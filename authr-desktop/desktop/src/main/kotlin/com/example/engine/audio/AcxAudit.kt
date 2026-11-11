package com.example.engine.audio

import com.example.desktopbridge.FfmpegLocator
import java.io.File

/**
 * ACX compliance audit (v3.0 P4, plan §7.6).
 *
 * Meters every chapter and the whole master against the retail audio
 * standard and produces a red/green report per criterion with the measured
 * values:
 *
 *  | Criterion | Requirement |
 *  | RMS level | between −23 and −18 dB |
 *  | Peaks | at or below −3 dB |
 *  | Noise floor | at or below −60 dB |
 *  | Sample rate | 44.1 kHz |
 *  | Format | MP3 CBR at 192 kbps or higher |
 *
 * The audit is deterministic: the mastering chain parameters (plan §7.5) are
 * chosen so a clean synthesis passes by construction, and this audit
 * measures and proves it. All measurements run through the ffmpeg/ffprobe
 * binary chain that ships with the desktop distribution.
 */
class AcxAudit(
    val ffmpegPath: String? = FfmpegLocator.ffmpegPath(),
    val ffprobePath: String? = FfmpegLocator.ffprobePath(),
    private val runner: MasteringChain.CommandRunner = MasteringChain.ProcessCommandRunner,
    val rmsMinDb: Double = -23.0,
    val rmsMaxDb: Double = -18.0,
    val peakMaxDb: Double = -3.0,
    val noiseFloorMaxDb: Double = -60.0,
    val requiredSampleRateHz: Int = 44_100,
    val requiredMinBitrateBps: Int = 192_000,
    val requiredCodec: String = "mp3"
) {

    /** One audited criterion with its requirement, measured value and verdict. */
    data class Criterion(
        val name: String,
        val requirement: String,
        val measured: String,
        val pass: Boolean
    )

    /** The audit report for one audio file (a chapter or the joined master). */
    data class AuditReport(
        val label: String,
        val file: File,
        val criteria: List<Criterion>
    ) {
        val allGreen: Boolean get() = criteria.isNotEmpty() && criteria.all { it.pass }
        fun asTable(): String = buildString {
            appendLine("ACX audit — $label (${file.name})")
            criteria.forEach {
                appendLine("  [${if (it.pass) "GREEN" else "RED "}] ${it.name.padEnd(28)} requirement: ${it.requirement.padEnd(30)} measured: ${it.measured}")
            }
            append(if (allGreen) "  ALL GREEN" else "  FAILED")
        }
    }

    /** Stream metadata as reported by ffprobe. */
    data class StreamInfo(
        val codec: String,
        val sampleRateHz: Int,
        val channels: Int,
        val bitRateBps: Int
    )

    fun probeStream(file: File, timeoutMs: Long = 60_000): StreamInfo? {
        val ffprobe = ffprobePath ?: return null
        val (code, out, _) = runner.run(
            listOf(
                ffprobe, "-v", "error", "-show_entries",
                "stream=codec_name,sample_rate,channels,bit_rate",
                "-of", "default=noprint_wrappers=1", file.absolutePath
            ),
            timeoutMs
        )
        if (code != 0) return null
        var codec = ""; var rate = 0; var ch = 0; var br = 0
        out.lineSequence().forEach { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            when (line.substring(0, idx)) {
                "codec_name" -> codec = line.substring(idx + 1).trim()
                "sample_rate" -> rate = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                "channels" -> ch = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                "bit_rate" -> br = line.substring(idx + 1).trim().toIntOrNull() ?: 0
            }
        }
        return if (codec.isBlank()) null else StreamInfo(codec, rate, ch, br)
    }

    /** Overall RMS level in dB (astats). Returns null when measurement fails. */
    fun overallRmsDb(file: File, timeoutMs: Long = 120_000): Double? {
        val out = runFfmpegLog(file, "astats=measure_perchannel=none:measure_overall=RMS_level") ?: return null
        return parseOverallRmsDb(out)
    }

    /** True peak in dBFS (ebur128 with peak measurement). */
    fun truePeakDb(file: File, timeoutMs: Long = 120_000): Double? {
        val out = runFfmpegLog(file, "ebur128=peak=true") ?: return null
        return parseTruePeakDb(out)
    }

    /**
     * Noise floor in dB: the minimum per-window RMS level across the file
     * (astats metadata reset per filter frame). Digital silence parses as
     * −inf and clamps to −99.9 dB — at or below every realistic threshold.
     */
    fun noiseFloorDb(file: File, timeoutMs: Long = 120_000): Double? {
        val out = runFfmpegLog(
            file,
            "astats=metadata=1:reset=1,ametadata=print:file=-"
        ) ?: return null
        return parseNoiseFloorDb(out)
    }

    /**
     * Runs the full five-criterion audit on one delivered audio file.
     * Returns null only when the measurement toolchain itself is missing.
     */
    fun audit(file: File, label: String = file.nameWithoutExtension, timeoutMs: Long = 120_000): AuditReport? {
        if (ffmpegPath == null || ffprobePath == null) return null
        val criteria = ArrayList<Criterion>()

        val info = probeStream(file, timeoutMs)
        criteria += if (info == null) {
            Criterion("Sample rate", "$requiredSampleRateHz Hz", "probe failed", false)
        } else {
            Criterion(
                "Sample rate", "$requiredSampleRateHz Hz",
                "${info.sampleRateHz} Hz", info.sampleRateHz == requiredSampleRateHz
            )
        }
        criteria += if (info == null) {
            Criterion("Format / bitrate", "MP3 CBR ≥ ${requiredMinBitrateBps / 1000} kbps", "probe failed", false)
        } else {
            Criterion(
                "Format / bitrate", "MP3 CBR ≥ ${requiredMinBitrateBps / 1000} kbps",
                "${info.codec} ${info.bitRateBps / 1000} kbps",
                info.codec == requiredCodec && info.bitRateBps >= requiredMinBitrateBps
            )
        }

        val rms = overallRmsDb(file, timeoutMs)
        criteria += if (rms == null) {
            Criterion("RMS level", "$rmsMinDb to $rmsMaxDb dB", "measurement failed", false)
        } else {
            Criterion(
                "RMS level", "$rmsMinDb to $rmsMaxDb dB",
                "%.1f dB".format(rms), rms in rmsMinDb..rmsMaxDb
            )
        }

        val peak = truePeakDb(file, timeoutMs)
        criteria += if (peak == null) {
            Criterion("Peaks", "at or below $peakMaxDb dB", "measurement failed", false)
        } else {
            Criterion(
                "Peaks", "at or below $peakMaxDb dB",
                "%.1f dBFS".format(peak), peak <= peakMaxDb
            )
        }

        val floor = noiseFloorDb(file, timeoutMs)
        criteria += if (floor == null) {
            Criterion("Noise floor", "at or below $noiseFloorMaxDb dB", "measurement failed", false)
        } else {
            Criterion(
                "Noise floor", "at or below $noiseFloorMaxDb dB",
                "%.1f dB".format(floor), floor <= noiseFloorMaxDb
            )
        }

        return AuditReport(label, file, criteria)
    }

    private fun runFfmpegLog(file: File, filter: String, timeoutMs: Long = 120_000): String? {
        val ffmpeg = ffmpegPath ?: return null
        val (code, out, err) = runner.run(
            listOf(ffmpeg, "-hide_banner", "-nostats", "-i", file.absolutePath, "-af", filter, "-f", "null", "-"),
            timeoutMs
        )
        if (code != 0) return null
        return out + "\n" + err
    }

    companion object {
        /** Parses "RMS level dB: -19.2" from an astats overall section. */
        fun parseOverallRmsDb(log: String): Double? =
            Regex("Overall RMS level dB:\\s*(-?[\\d.]+|-inf)").findAll(log)
                .lastOrNull()?.groupValues?.get(1)
                ?.let(::clampDb)
            ?: Regex("RMS level dB:\\s*(-?[\\d.]+|-inf)").find(log)?.groupValues?.get(1)?.let(::clampDb)

        /** Parses the ebur128 summary true peak: "Peak:      -3.1 dBFS". */
        fun parseTruePeakDb(log: String): Double? =
            Regex("True peak:\\s*\\n\\s*Peak:\\s*(-?[\\d.]+|-inf)\\s*dBFS", RegexOption.MULTILINE)
                .findAll(log).lastOrNull()?.groupValues?.get(1)?.let(::clampDb)

        /** Parses per-window RMS levels; returns the minimum (the noise floor). */
        fun parseNoiseFloorDb(log: String): Double? {
            val values = Regex("lavfi\\.astats\\.(?:Overall|\\d+)\\.RMS_level=(-?[\\d.]+|-inf)")
                .findAll(log).mapNotNull { it.groupValues[1].let(::parseDb) }.toList()
            return values.minOrNull()
        }

        fun parseDb(raw: String): Double? = when {
            raw.equals("-inf", true) || raw.equals("-Infinity", true) -> -99.9
            raw.equals("inf", true) || raw.equals("Infinity", true) -> null
            else -> raw.toDoubleOrNull()
        }

        private fun clampDb(raw: String): Double? = parseDb(raw)
    }
}
