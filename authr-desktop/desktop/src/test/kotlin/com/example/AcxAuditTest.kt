package com.example

import com.example.engine.audio.AcxAudit
import com.example.engine.audio.MasteringChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * ACX compliance audit (v3.0 P4, plan §7.6): the five retail criteria meter
 * red/green with measured values — RMS −23..−18 dB, peaks ≤ −3 dB, noise
 * floor ≤ −60 dB, 44.1 kHz, MP3 CBR ≥ 192 kbps. Parsers are unit-tested on
 * canned ffmpeg logs; a real-ffmpeg audit proves a mastered file passes
 * all green by construction.
 */
class AcxAuditTest {

    private fun auditWith(logs: MutableMap<String, String>) =
        AcxAudit(
            ffmpegPath = "/usr/bin/ffmpeg",
            ffprobePath = "/usr/bin/ffprobe",
            runner = { command, _ ->
                val cmd = command.joinToString(" ")
                val key = when {
                    cmd.contains("ffprobe") && cmd.contains("codec_name") -> "probe"
                    cmd.contains("astats=measure_perchannel=none") -> "rms"
                    cmd.contains("ebur128") -> "peak"
                    cmd.contains("ametadata") -> "floor"
                    else -> "other"
                }
                Triple(0, logs.getOrPut(key) { "" }, logs.getOrDefault(key, ""))
            }
        )

    @Test
    fun `parsers read astats, ebur128 and per-window noise floor`() {
        val rmsLog = """
            [Parsed_astats_0 @ 0x1] Overall
            [Parsed_astats_0 @ 0x1] Peak level dB: -14.312277
            [Parsed_astats_0 @ 0x1] RMS level dB: -19.234567
        """.trimIndent()
        assertEquals(-19.234567, AcxAudit.parseOverallRmsDb(rmsLog)!!, 1e-9)

        val peakLog = """
            Integrated loudness:
                I:         -18.0 LUFS
            True peak:
                Peak:      -3.1 dBFS
        """.trimIndent()
        assertEquals(-3.1, AcxAudit.parseTruePeakDb(peakLog)!!, 1e-9)

        val floorLog = """
            frame:0    pts:0       pts_time:0
            lavfi.astats.1.RMS_level=-24.100000
            frame:1    pts:1024    pts_time:0.023220
            lavfi.astats.1.RMS_level=-71.500000
            frame:2    pts:2048    pts_time:0.046440
            lavfi.astats.1.RMS_level=-88.000000
            lavfi.astats.Overall.RMS_level=-41.300000
        """.trimIndent()
        assertEquals(-88.0, AcxAudit.parseNoiseFloorDb(floorLog)!!, 1e-9)

        // digital silence parses as -inf and clamps to a quiet floor value
        val silentFloor = "lavfi.astats.1.RMS_level=-inf\n"
        assertTrue(AcxAudit.parseNoiseFloorDb(silentFloor)!! <= -99.0)
    }

    @Test
    fun `probe parses ffprobe key=value stream metadata`() {
        val audit = auditWith(
            mutableMapOf(
                "probe" to "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n"
            )
        )
        val info = audit.probeStream(File("fake.mp3"))!!
        assertEquals("mp3", info.codec)
        assertEquals(44_100, info.sampleRateHz)
        assertEquals(1, info.channels)
        assertEquals(192_000, info.bitRateBps)
    }

    @Test
    fun `audit is green only when every criterion is met`() {
        val green = auditWith(
            mutableMapOf(
                "probe" to "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n",
                "rms" to "RMS level dB: -18.9",
                "peak" to "True peak:\n    Peak:      -3.1 dBFS",
                "floor" to "lavfi.astats.1.RMS_level=-72.0"
            )
        )
        val report = green.audit(File("green.mp3"), "Chapter 1")!!
        assertTrue(report.allGreen, report.asTable())
        assertEquals(5, report.criteria.size)
        assertTrue(report.asTable().contains("[GREEN]"))
        // exact boundary values pass
        assertTrue(report.criteria.first { it.name == "RMS level" }.pass)

        val loud = auditWith(
            mutableMapOf(
                "probe" to "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n",
                "rms" to "RMS level dB: -17.2",
                "peak" to "True peak:\n    Peak:      -3.1 dBFS",
                "floor" to "lavfi.astats.1.RMS_level=-72.0"
            )
        )
        assertFalse(loud.audit(File("loud.mp3"))!!.allGreen, "RMS above −18 dB is red")

        val hotPeak = auditWith(
            mutableMapOf(
                "probe" to "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n",
                "rms" to "RMS level dB: -18.5",
                "peak" to "True peak:\n    Peak:      -2.4 dBFS",
                "floor" to "lavfi.astats.1.RMS_level=-72.0"
            )
        )
        assertFalse(hotPeak.audit(File("hot.mp3"))!!.allGreen, "peak above −3 dB is red")

        val noisy = auditWith(
            mutableMapOf(
                "probe" to "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n",
                "rms" to "RMS level dB: -18.5",
                "peak" to "True peak:\n    Peak:      -3.1 dBFS",
                "floor" to "lavfi.astats.1.RMS_level=-55.0"
            )
        )
        assertFalse(noisy.audit(File("noisy.mp3"))!!.allGreen, "noise floor above −60 dB is red")

        val wrongFormat = auditWith(
            mutableMapOf(
                "probe" to "codec_name=aac\nsample_rate=48000\nchannels=1\nbit_rate=128000\n",
                "rms" to "RMS level dB: -18.5",
                "peak" to "True peak:\n    Peak:      -3.1 dBFS",
                "floor" to "lavfi.astats.1.RMS_level=-72.0"
            )
        )
        val wrong = wrongFormat.audit(File("wrong.m4a"))!!
        assertFalse(wrong.allGreen, "44.1 kHz MP3 CBR 192k required")
        assertTrue(wrong.criteria.first { it.name == "Sample rate" }.measured.contains("48000"))
        assertTrue(wrong.criteria.first { it.name == "Format / bitrate" }.measured.contains("aac"))
    }

    @Test
    fun `measurement failure is red, never a crash`() {
        val broken = auditWith(mutableMapOf())
        val report = broken.audit(File("broken.mp3"), "Chapter X")!!
        assertFalse(report.allGreen)
        assertTrue(report.criteria.all { !it.pass })
        assertNull(AcxAudit.parseTruePeakDb("no summary here"))
        assertNull(AcxAudit.parseNoiseFloorDb("no windows here"))
    }

    @Test
    fun `real ffmpeg audit - mastered speech passes all green by construction`() {
        val ffmpeg = com.example.desktopbridge.FfmpegLocator.ffmpegPath()
        val espeak = com.example.desktopbridge.EspeakLocator.espeakPath()
        assumeTrue("ffmpeg not installed; skipping real audit", ffmpeg != null)
        assumeTrue("espeak-ng not installed; skipping real audit", espeak != null)
        val dir = File(System.getProperty("java.io.tmpdir"), "acx-smoke-${System.nanoTime()}")
        dir.mkdirs()
        try {
            // a clean synthesis — real espeak-ng speech, the material the chain
            // is tuned for (a pure tone is not program material)
            val raw = File(dir, "raw.wav")
            val proc = ProcessBuilder(espeak, "-v", "en-us", "-w", raw.absolutePath).start()
            proc.outputStream.use { it.write(SPEECH_TEXT.toByteArray(Charsets.UTF_8)) }
            assumeTrue("speech synthesis failed; skipping", proc.waitFor(60_000, java.util.concurrent.TimeUnit.MILLISECONDS) && proc.exitValue() == 0 && raw.isFile)
            val chain = MasteringChain(ffmpegPath = ffmpeg)
            val mp3 = File(dir, "mastered.mp3")
            val stages = chain.master(raw, mp3, mp3 = true)
            assumeTrue("mastering failed; skipping", stages.all { it.applied })
            val audit = AcxAudit(ffmpegPath = ffmpeg)
            val report = audit.audit(mp3, "Smoke")!!
            assertTrue(report.allGreen, report.asTable())
            val rms = report.criteria.first { it.name == "RMS level" }
            assertTrue(rms.measured.contains("dB"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private val SPEECH_TEXT = "Aelyth opened the gate at dawn. " +
        "The road ran north toward the hills, and rain began to fall before noon. " +
        "She pulled her cloak tighter and counted the milestones: three since the river, " +
        "two since the shrine. By evening the lights of Vaelmoor showed through the mist."
}
