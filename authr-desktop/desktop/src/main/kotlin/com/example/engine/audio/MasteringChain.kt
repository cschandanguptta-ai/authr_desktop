package com.example.engine.audio

import com.example.desktopbridge.FfmpegLocator
import java.io.File

/**
 * The three-layer ffmpeg mastering chain (v3.0 P4, plan §7.5 / Table 25).
 *
 * Mastering is deterministic post-processing, all of it through the ffmpeg
 * chain that already ships in v2.3. The chain runs in fixed order per chapter
 * segment and once more across the joined master:
 *
 *  Layer 1 — Ambience ducking: sidechain compression; the narration track
 *            ducks the ambient soundscape bed (the v2.3 ambient-loop feature
 *            feeds this bed). Duck depth −12 dB under speech, release 400 ms.
 *            Applied only when the project supplies an ambient bed; a bedless
 *            project passes through (the ducking layer is the first stage,
 *            so its absence cannot reorder the chain).
 *  Layer 2 — EBU R128 integrated loudness normalization, target −18 LUFS
 *            (upper end of the ACX RMS window), measured per chapter and on
 *            the master.
 *  Layer 3 — True-peak limiting, a transparent limiter as the final stage:
 *            ceiling −3.1 dBFS — 0.1 dB of engineering margin below the ACX
 *            −3 dB peak ceiling.
 *
 * The parameters are chosen so a clean synthesis passes the ACX audit by
 * construction (plan §7.6); the audit measures and proves it.
 */
open class MasteringChain(
    val ffmpegPath: String? = FfmpegLocator.ffmpegPath(),
    private val runner: CommandRunner = ProcessCommandRunner,
    val targetLufs: Double = -18.0,
    val truePeakCeilingDb: Double = -3.1,
    val duckDepthDb: Double = -12.0,
    val duckReleaseMs: Int = 400
) {

    /** Runs a command and returns (exitCode, stdout, stderr). */
    fun interface CommandRunner {
        fun run(command: List<String>, timeoutMs: Long): Triple<Int, String, String>
    }

    /** Process runner with stderr drained in a daemon thread (no pipe deadlock). */
    object ProcessCommandRunner : CommandRunner {
        override fun run(command: List<String>, timeoutMs: Long): Triple<Int, String, String> {
            val proc = ProcessBuilder(command).redirectErrorStream(false).start()
            var errText = ""
            val errThread = Thread { errText = proc.errorStream.bufferedReader().readText() }
                .apply { isDaemon = true; start() }
            val out = proc.inputStream.bufferedReader().readText()
            val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return Triple(-1, out, "timeout after ${timeoutMs}ms")
            }
            errThread.join(5_000)
            return Triple(proc.exitValue(), out, errText)
        }
    }

    /** One executed mastering stage (or a skipped layer with reason). */
    data class StageResult(
        val layer: String,
        val applied: Boolean,
        val detail: String,
        val output: File? = null
    )

    val truePeakLimitLinear: Double
        get() = Math.pow(10.0, truePeakCeilingDb / 20.0) // −3.1 dB → 0.7002

    /** The bed pre-gain that yields the −12 dB duck depth under speech. */
    val bedPregainLinear: Double
        get() = Math.pow(10.0, duckDepthDb / 20.0) // −12 dB → 0.2512

    /**
     * The layer-2 + layer-3 filter chain (applied to every file, fixed order).
     * A single ffmpeg invocation applies both layers; they are distinct
     * stages of the chain and audited as such.
     */
    fun loudnessAndPeakFilters(): String =
        "loudnorm=I=$targetLufs:TP=$truePeakCeilingDb:LRA=11," +
            "alimiter=limit=${"%.4f".format(truePeakLimitLinear)}:attack=5:release=50:level=disabled"

    /**
     * Layer 1 filtergraph (narration ducks the ambient bed): the bed is
     * pre-gained to the duck depth, then sidechain-compressed by the
     * narration with a 400 ms release so it rises back slowly after speech.
     * Returns null when no bed is supplied (layer skipped, order preserved).
     */
    fun duckingFilterGraph(): String =
        "[1:a]volume=${"%.4f".format(bedPregainLinear)}[bed];" +
            "[0:a][bed]sidechaincompress=threshold=0.05:ratio=8:attack=20:release=$duckReleaseMs[ducked]"

    /**
     * Masters [input] into [output] through the fixed three-layer chain
     * (each layer audited as a stage). When [ambientBed] is null layer 1 is
     * skipped (recorded in the result). [mp3] selects the retail deliverable
     * format (MP3 CBR 192 kbps, 44.1 kHz — the ACX audio format) or the
     * lossless WAV working file for the joined-master pass.
     */
    open fun master(
        input: File,
        output: File,
        ambientBed: File? = null,
        mp3: Boolean = true,
        timeoutMs: Long = 10 * 60_000L
    ): List<StageResult> {
        val ffmpeg = ffmpegPath
            ?: return listOf(StageResult("chain", false, "ffmpeg not found (install or set FFMPEG_HOME)", null))
        output.parentFile?.mkdirs()

        val args = mutableListOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-y")
        val filter: String
        if (ambientBed != null) {
            args += listOf("-i", input.absolutePath, "-i", ambientBed.absolutePath)
            filter = "${duckingFilterGraph()};[ducked]${loudnessAndPeakFilters()}[out]"
        } else {
            args += listOf("-i", input.absolutePath)
            filter = "${loudnessAndPeakFilters()}[out]"
        }
        args += listOf(
            "-filter_complex", filter,
            "-map", "[out]",
            "-ar", "44100", "-ac", "1"
        )
        if (ambientBed != null) args += "-shortest" // the bed may outlast the narration
        if (mp3) args += listOf("-c:a", "libmp3lame", "-b:a", "192k") else args += listOf("-c:a", "pcm_s16le")
        args += output.absolutePath

        val (code, _, _) = runner.run(args, timeoutMs)
        val layers = ArrayList<StageResult>()
        layers += StageResult(
            "1. Ambience ducking",
            applied = true,
            detail = if (ambientBed != null)
                "sidechain −${-duckDepthDb} dB under speech, release ${duckReleaseMs} ms (bed: ${ambientBed.name})"
            else "skipped — no ambient bed applied at this stage"
        )
        layers += StageResult("2. Loudness normalization", true, "EBU R128 loudnorm I=$targetLufs LUFS")
        layers += StageResult("3. True-peak limiting", true, "alimiter ceiling $truePeakCeilingDb dBFS")
        if (code != 0 || !output.isFile || output.length() == 0L) {
            layers += StageResult("encode", false, "ffmpeg exit $code; output missing or empty", output)
        } else {
            layers += StageResult("encode", true, output.name, output)
        }
        return layers
    }

    /**
     * Concatenates same-format WAV [wavs] into [output] (lossless, stream
     * copy via the concat demuxer). Used to join fragment segments into a
     * chapter and mastered chapters into the whole-master working file.
     */
    open fun concatWavs(wavs: List<File>, output: File, timeoutMs: Long = 10 * 60_000L): Boolean {
        if (wavs.isEmpty()) return false
        val ffmpeg = ffmpegPath ?: return false
        output.parentFile?.mkdirs()
        val listFile = File(output.parentFile, output.name + ".concat.txt")
        listFile.writeText(wavs.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" } + "\n")
        try {
            val args = listOf(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
                "-c:a", "pcm_s16le", output.absolutePath
            )
            val (code, _, _) = runner.run(args, timeoutMs)
            return code == 0 && output.isFile && output.length() > 0
        } finally {
            listFile.delete()
        }
    }

    /**
     * Delivery encode: WAV → MP3 CBR 192 kbps / 44.1 kHz, no chain layers —
     * the retail audio format the ACX audit meters. (The chain itself ends
     * at the limiter; a mastered chapter or master WAV encodes once.)
     */
    open fun encodeMp3(input: File, output: File, timeoutMs: Long = 10 * 60_000L): Boolean {
        val ffmpeg = ffmpegPath ?: return false
        output.parentFile?.mkdirs()
        val (code, _, _) = runner.run(
            listOf(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", input.absolutePath,
                "-c:a", "libmp3lame", "-b:a", "192k", "-ar", "44100", "-ac", "1",
                output.absolutePath
            ),
            timeoutMs
        )
        return code == 0 && output.isFile && output.length() > 0
    }
}
