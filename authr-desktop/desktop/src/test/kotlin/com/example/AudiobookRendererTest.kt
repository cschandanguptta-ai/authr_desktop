package com.example

import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.AudiobookRenderer.ContinuityMode
import com.example.engine.audio.AcxAudit
import com.example.engine.audio.MasteringChain
import com.example.engine.audio.PronunciationPass
import com.example.engine.audio.SmilExporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

/** Minimal 16-bit mono 22050 Hz WAV with a mid-amplitude square tone. */
private fun writeToneWav(file: File, samples: Int) {
    val dataLen = samples * 2
    val out = java.io.DataOutputStream(java.io.FileOutputStream(file).buffered())
    out.writeBytes("RIFF"); out.writeIntLe(36 + dataLen); out.writeBytes("WAVE")
    out.writeBytes("fmt "); out.writeIntLe(16); out.writeShortLe(1); out.writeShortLe(1)
    out.writeIntLe(22_050); out.writeIntLe(44_100); out.writeShortLe(2); out.writeShortLe(16)
    out.writeBytes("data"); out.writeIntLe(dataLen)
    repeat(samples) { out.writeShortLe(if (it % 64 < 32) 9000 else -9000) }
    out.close()
}

private fun java.io.DataOutputStream.writeIntLe(v: Int) {
    write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
}

private fun java.io.DataOutputStream.writeShortLe(v: Int) {
    write(v and 0xFF); write((v shr 8) and 0xFF)
}

/**
 * The audiobook renderer (v3.0 P4, plan §7): canon-only rendering (the
 * writer gate holds), pre-synthesis normalization, per-fragment synthesis
 * with measured clip times, per-chapter + whole-master mastering, ACX audits,
 * SMIL export, and the synthesis-speed factor. Fakes cover the pipeline
 * logic; a real espeak-ng + ffmpeg integration renders two mini-chapters
 * end to end when the binaries are present.
 */
class AudiobookRendererTest {

    /** Fake synthesizer: real WAV files of word-proportional duration. */
    private class FakeSynthesizer(
        val texts: MutableList<String> = mutableListOf()
    ) : AudiobookRenderer.Synthesizer {
        override val engineLabel = "FakeTTS"
        override val continuityMode = ContinuityMode.POCKET_STRUCTURAL
        override val temperature = 0.6
        var call = 0
        override fun synthesize(text: String, outWav: File): AudiobookRenderer.SynthesisResult {
            val start = System.currentTimeMillis()
            texts += text
            outWav.parentFile?.mkdirs()
            val words = text.split(Regex("\\s+")).size
            // ~0.4 s per word at 22050 Hz mono 16-bit — a real, parseable WAV
            val samples = (words * 0.4 * 22_050).toInt()
            writeToneWav(outWav, samples)
            call++
            return AudiobookRenderer.SynthesisResult(true, samples / 22_050.0, System.currentTimeMillis() - start)
        }
    }

    /** Fake mastering: copies the input, records every invocation. */
    private class FakeMastering(val calls: MutableList<String> = mutableListOf()) : MasteringChain() {
        override fun master(
            input: File, output: File, ambientBed: File?, mp3: Boolean, timeoutMs: Long
        ): List<MasteringChain.StageResult> {
            calls += "${input.name}->${output.name}(mp3=$mp3)"
            output.parentFile?.mkdirs()
            input.copyTo(output, overwrite = true)
            return listOf(MasteringChain.StageResult("encode", true, output.name, output))
        }

        override fun concatWavs(wavs: List<File>, output: File, timeoutMs: Long): Boolean {
            calls += "concat(${wavs.size})->${output.name}"
            output.parentFile?.mkdirs()
            java.io.FileOutputStream(output).use { fos -> wavs.forEach { it.inputStream().use { it.copyTo(fos) } } }
            return true
        }

        override fun encodeMp3(input: File, output: File, timeoutMs: Long): Boolean {
            calls += "encodeMp3(${input.name})->${output.name}"
            output.parentFile?.mkdirs()
            input.copyTo(output, overwrite = true)
            return true
        }
    }

    /** Green audit fake: answers each measurement command with a passing value. */
    private fun fakeAudit() = AcxAudit(
        ffmpegPath = "/fake/ffmpeg", ffprobePath = "/fake/ffprobe",
        runner = { command, _ ->
            val cmd = command.joinToString(" ")
            val out = when {
                cmd.contains("ffprobe") && cmd.contains("codec_name") ->
                    "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n"
                cmd.contains("ffprobe") && cmd.contains("format=duration") -> ""
                cmd.contains("astats=measure_perchannel=none") -> "Overall\nRMS level dB: -18.5\n"
                cmd.contains("ebur128") -> "True peak:\n    Peak:      -3.1 dBFS\n"
                cmd.contains("ametadata") -> "lavfi.astats.1.RMS_level=-72.0\n"
                else -> ""
            }
            Triple(0, out, "")
        }
    )

    private fun chapter(n: Int, content: String, canon: Boolean = true) = Chapter(
        bookId = 7, chapterNumber = n, title = "Chapter $n", content = content, summary = "s",
        writerConfirmed = canon,
        draftStatus = if (canon) DraftState.CONFIRMED else DraftState.PROPOSED
    )

    @Test
    fun `render gates canon, normalizes and records continuity`() {
        val synth = FakeSynthesizer()
        val mastering = FakeMastering()
        val renderer = AudiobookRenderer(synth, mastering, fakeAudit())
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val rules = listOf(PronunciationPass.ApprovedRule("Aelyth", "ˈeɪlɪθ", "AY-lith"))
            val chapters = listOf(
                chapter(1, "Aelyth opened the gate.\n\nThe road ran north."),
                chapter(2, "Rain fell on the road.\n\nAelyth walked on."),
                chapter(3, "A draft proposal chapter.", canon = false)
            )
            val report = renderer.render("The Iron Vale", chapters, dir, rules)
            assertEquals(2, report.chapters.size, "canon chapters only: ${report.chapters.map { it.chapterNumber }}")
            assertEquals(listOf(3), report.skippedChapters, "proposed chapter skipped and reported")
            assertTrue(report.allGreen, report.summary())
            assertTrue(synth.texts.none { it.contains("Aelyth") }, "normalization applied pre-synthesis")
            assertTrue(synth.texts.any { it.contains("AY-lith") })
            // the read-along surface shows the manuscript, not the respelling
            val xhtml = report.chapters[0].xhtmlFile!!.readText()
            assertTrue(xhtml.contains("Aelyth"), "read-along text is the canon manuscript")
            assertFalse(xhtml.contains("AY-lith"), "respellings never enter the display surface")
            assertEquals(ContinuityMode.POCKET_STRUCTURAL, report.continuityMode)
            assertEquals(0.6, report.temperature)
            assertTrue(report.summary().contains("Pocket TTS structural"), report.summary())
            // per-chapter MP3 + SMIL + XHTML, whole master once
            assertEquals(2, report.chapters.count { it.mp3?.isFile == true })
            assertEquals(2, report.chapters.count { it.smilFile?.isFile == true })
            assertEquals(2, report.chapters.count { it.xhtmlFile?.isFile == true })
            assertTrue(report.masterMp3?.isFile == true)
            assertTrue(report.masterAudit?.allGreen == true)
            // mastering ran per chapter and once more across the joined master
            assertTrue(mastering.calls.any { it.contains("master_raw.wav") }, "whole-master pass: ${mastering.calls}")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `measured fragment durations give contiguous SMIL timings`() {
        val synth = FakeSynthesizer()
        val renderer = AudiobookRenderer(synth, FakeMastering(), fakeAudit())
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-timing-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val content = "One two three four five.\n\nSix seven.\n\nEight nine ten eleven twelve."
            val report = renderer.render("Timing", listOf(chapter(1, content)), dir)
            val ch = report.chapters.single()
            assertEquals(3, ch.fragments.size)
            assertTrue(ch.timing.ok, ch.timing.summary())
            assertEquals(0.0, ch.fragments.first().clipBeginSec, 1e-9)
            // every fragment got synthesized text (not just chapter words)
            assertEquals(3, synth.texts.size)
            val smil = ch.smilFile!!.readText()
            assertTrue(smil.contains("""clipBegin="0.000s""""))
            assertTrue(smil.contains("clipEnd="))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `synthesis speed factor divides audio seconds by wall seconds`() {
        val synth = FakeSynthesizer()
        val renderer = AudiobookRenderer(synth, FakeMastering(), fakeAudit())
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-rtf-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val content = (1..6).joinToString("\n\n") { "Word group number $it here with more words to say aloud." }
            val report = renderer.render("Speed", listOf(chapter(1, content)), dir)
            assertTrue(report.synthesisSpeedFactor > 1.0, "fake synth is effectively instant: ${report.synthesisSpeedFactor}")
            assertTrue(report.totalAudioSec > 0)
            assertTrue(report.summary().contains("speed"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `synthesis failure fails the report, never crashes`() {
        val broken = object : AudiobookRenderer.Synthesizer {
            override val engineLabel = "BrokenTTS"
            override val continuityMode = ContinuityMode.EDGE_FALLBACK
            override val temperature = 0.0
            override fun synthesize(text: String, outWav: File) =
                AudiobookRenderer.SynthesisResult(false, null, 5, "engine exploded")
        }
        val renderer = AudiobookRenderer(broken, FakeMastering(), fakeAudit())
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-broken-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val report = renderer.render("Broken", listOf(chapter(1, "Some words here.")), dir)
            assertFalse(report.allGreen)
            assertTrue(report.failureNotes.any { it.contains("synthesis failed") && it.contains("engine exploded") })
            assertTrue(report.chapters.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `wav duration parser reads the canonical header`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-wav-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "t.wav")
            writeToneWav(f, 22_050)
            assertEquals(1.0, com.example.engine.audio.EspeakSynthesizer.wavDurationSec(f)!!, 1e-6)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `real espeak-ng and ffmpeg render two chapters end to end`() {
        val espeak = com.example.desktopbridge.EspeakLocator.espeakPath()
        val ffmpeg = com.example.desktopbridge.FfmpegLocator.ffmpegPath()
        assumeTrue("espeak-ng not installed; skipping integration", espeak != null)
        assumeTrue("ffmpeg not installed; skipping integration", ffmpeg != null)
        val dir = File(System.getProperty("java.io.tmpdir"), "abr-real-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val synth = com.example.engine.audio.EspeakSynthesizer(espeak)
            val mastering = MasteringChain(ffmpegPath = ffmpeg)
            val audit = AcxAudit(ffmpegPath = ffmpeg, ffprobePath = com.example.desktopbridge.FfmpegLocator.ffprobePath())
            val renderer = AudiobookRenderer(synth, mastering, audit)
            val content = "Aelyth opened the gate at dawn.\n\nThe road ran north toward the hills.\n\nRain began to fall."
            val report = renderer.render(
                "Integration", listOf(chapter(1, content), chapter(2, content)), dir
            )
            assertTrue(report.allGreen, report.summary())
            assertTrue(report.synthesisSpeedFactor > 2.0, "espeak-ng must exceed 2x real time: ${report.synthesisSpeedFactor}")
            report.chapters.forEach { ch ->
                assertTrue(ch.audit!!.allGreen, ch.audit.asTable())
                assertTrue(ch.timing.ok, ch.timing.summary())
                assertTrue(ch.smilFile!!.readText().contains("<smil"))
            }
            assertTrue(report.masterAudit!!.allGreen, report.masterAudit.asTable())
        } finally {
            dir.deleteRecursively()
        }
    }
}
