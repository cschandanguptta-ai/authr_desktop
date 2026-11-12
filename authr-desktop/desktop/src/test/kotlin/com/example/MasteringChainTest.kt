package com.example

import com.example.engine.audio.MasteringChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * The three-layer ffmpeg mastering chain (v3.0 P4, plan §7.5 / Table 25):
 * layers run in fixed order (ducking → loudnorm −18 LUFS → true-peak
 * −3.1 dBFS), the ducking layer is skipped (and recorded) when no ambient
 * bed is supplied, the retail encode is MP3 CBR 192 kbps / 44.1 kHz, and a
 * real ffmpeg smoke run masters a synthesized tone end to end.
 */
class MasteringChainTest {

    /** Fake runner capturing commands; writes the output file (last arg). */
    private class CapturingRunner : MasteringChain.CommandRunner {
        val commands = ArrayList<List<String>>()
        var exitCode = 0
        override fun run(command: List<String>, timeoutMs: Long): Triple<Int, String, String> {
            commands += command
            command.lastOrNull()?.let { File(it).writeBytes(ByteArray(2048)) }
            return Triple(exitCode, "", "")
        }
    }

    private fun chainWithRunner(runner: MasteringChain.CommandRunner) =
        MasteringChain(ffmpegPath = "/usr/bin/ffmpeg", runner = runner)

    @Test
    fun `layer parameters match the Table 25 specification`() {
        val chain = MasteringChain(ffmpegPath = "/usr/bin/ffmpeg")
        assertEquals(-18.0, chain.targetLufs, "EBU R128 target −18 LUFS")
        assertEquals(-3.1, chain.truePeakCeilingDb, "true-peak ceiling −3.1 dBFS (0.1 dB ACX margin)")
        assertEquals(-12.0, chain.duckDepthDb, "duck depth −12 dB under speech")
        assertEquals(400, chain.duckReleaseMs, "duck release 400 ms")
        assertTrue(chain.truePeakLimitLinear in 0.6997..0.6999, "−3.1 dBFS → 0.6998 linear")
        val filters = chain.loudnessAndPeakFilters()
        val loudIdx = filters.indexOf("loudnorm=")
        val limitIdx = filters.indexOf("alimiter=")
        assertTrue(loudIdx in filters.indices, "layer 2 present: $filters")
        assertTrue(limitIdx > loudIdx, "layer 3 runs after layer 2: $filters")
        assertTrue(filters.contains("I=-18.0"), "loudnorm integrated target: $filters")
        assertTrue(filters.contains("TP=-3.1"), "loudnorm true-peak parameter: $filters")
        assertTrue(filters.contains("limit=0.6998"), "alimiter ceiling linear: $filters")
        val duck = chain.duckingFilterGraph()
        assertTrue(duck.contains("sidechaincompress"), "layer 1 is sidechain compression: $duck")
        assertTrue(duck.contains("release=400"), "duck release 400 ms: $duck")
        assertTrue(duck.startsWith("[1:a]volume=0.2512"), "bed pre-gained to the −12 dB duck depth: $duck")
    }

    @Test
    fun `mastering runs the layers in fixed order and skips ducking without a bed`() {
        val runner = CapturingRunner()
        val chain = chainWithRunner(runner)
        val dir = File(System.getProperty("java.io.tmpdir"), "mc-test-${System.nanoTime()}")
        dir.mkdirs()
        val input = File(dir, "in.wav").apply { writeBytes(ByteArray(1024)) }
        val output = File(dir, "out.mp3")
        try {
            val stages = chain.master(input, output, ambientBed = null, mp3 = true)
            assertEquals(4, stages.size, "ducking, loudnorm, limiter, encode")
            assertEquals("1. Ambience ducking", stages[0].layer)
            assertFalse(stages[0].detail.startsWith("sidechain"), "layer 1 skipped without a bed")
            assertEquals("2. Loudness normalization", stages[1].layer)
            assertEquals("3. True-peak limiting", stages[2].layer)
            assertTrue(stages.all { it.applied })
            val cmd = runner.commands.single().joinToString(" ")
            assertFalse(cmd.contains("sidechaincompress"), "no bed → no sidechain stage")
            assertTrue(cmd.contains("loudnorm=I=-18.0"))
            assertTrue(cmd.contains("alimiter=limit=0.6998"))
            assertTrue(cmd.contains("-b:a 192k"), "MP3 CBR 192 kbps: $cmd")
            assertTrue(cmd.contains("-ar 44100"), "44.1 kHz sample rate: $cmd")
            assertTrue(cmd.contains("libmp3lame"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ambient bed activates the ducking layer as stage one`() {
        val runner = CapturingRunner()
        val chain = chainWithRunner(runner)
        val dir = File(System.getProperty("java.io.tmpdir"), "mc-test-${System.nanoTime()}")
        dir.mkdirs()
        val input = File(dir, "in.wav").apply { writeBytes(ByteArray(1024)) }
        val bed = File(dir, "bed.wav").apply { writeBytes(ByteArray(1024)) }
        val output = File(dir, "out.wav")
        try {
            val stages = chain.master(input, output, ambientBed = bed, mp3 = false)
            assertTrue(stages[0].detail.startsWith("sidechain"), "layer 1 active: ${stages[0].detail}")
            assertTrue(stages[0].detail.contains("bed.wav"))
            val cmd = runner.commands.single().joinToString(" ")
            val duckIdx = cmd.indexOf("sidechaincompress")
            val loudIdx = cmd.indexOf("loudnorm=")
            assertTrue(duckIdx in cmd.indices && loudIdx > duckIdx, "ducking before loudnorm: $cmd")
            assertTrue(cmd.contains("pcm_s16le"), "lossless WAV intermediate: $cmd")
            assertFalse(cmd.contains("libmp3lame"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing ffmpeg is reported, never thrown`() {
        val chain = MasteringChain(ffmpegPath = null, runner = CapturingRunner())
        val stages = chain.master(File("/tmp/does-not-exist.wav"), File("/tmp/out.mp3"))
        assertFalse(stages.all { it.applied })
        assertTrue(stages.any { !it.applied && it.detail.contains("ffmpeg not found") })
    }

    @Test
    fun `real ffmpeg smoke - mastering a clean tone passes`() {
        val ffmpeg = com.example.desktopbridge.FfmpegLocator.ffmpegPath()
        assumeTrue("ffmpeg not installed; skipping real smoke", ffmpeg != null)
        val chain = MasteringChain(ffmpegPath = ffmpeg)
        val dir = File(System.getProperty("java.io.tmpdir"), "mc-smoke-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val raw = File(dir, "raw.wav")
            val synth = ProcessBuilder(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2:sample_rate=44100",
                "-ac", "1", "-c:a", "pcm_s16le", raw.absolutePath
            ).start().apply { waitFor(30_000, java.util.concurrent.TimeUnit.MILLISECONDS) }
            assumeTrue("tone synthesis failed; skipping", synth.exitValue() == 0 && raw.isFile)
            val out = File(dir, "mastered.mp3")
            val stages = chain.master(raw, out, mp3 = true)
            assertTrue(stages.all { it.applied }, stages.filter { !it.applied }.joinToString { it.detail })
            assertTrue(out.isFile && out.length() > 10_000, "MP3 written: ${out.length()} bytes")
        } finally {
            dir.deleteRecursively()
        }
    }
}
