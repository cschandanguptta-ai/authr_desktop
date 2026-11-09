package com.example.utils

import android.util.Log
import com.example.desktopbridge.FfmpegLocator
import java.io.File

/**
 * Desktop replacement for com.example.utils.AudioEncoder.
 *
 * The Android implementation uses MediaCodec + MediaMuxer (not available on
 * the JVM). Desktop encodes through the bundled ffmpeg binary instead — the
 * same audio-only ffmpeg surface the mastering chain uses (v3.0 SCR-2 kept
 * the audio DSP path; the video muxing path was removed).
 *
 * Contract (identical to the Android version):
 *   - input : 16-bit PCM WAV
 *   - output: AAC-in-MP4 (.m4a), 44.1 kHz stereo, 128 kbps
 */
object AudioEncoder {

    fun encodeWavToM4a(wavFile: File, m4aFile: File) {
        val ffmpeg = FfmpegLocator.ffmpegPath()
            ?: throw IllegalStateException("ffmpeg not found; cannot encode $wavFile to M4A")

        m4aFile.parentFile?.mkdirs()
        val cmd = arrayOf(
            ffmpeg, "-y",
            "-i", wavFile.absolutePath,
            "-vn",
            "-c:a", "aac",
            "-b:a", "128k",
            "-ar", "44100",
            "-ac", "2",
            "-movflags", "+faststart",
            m4aFile.absolutePath
        )
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        proc.inputStream.use { it.readBytes() } // drain output
        val finished = proc.waitFor(10 * 60_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            throw IllegalStateException("ffmpeg M4A encoding timed out for $wavFile")
        }
        if (proc.exitValue() != 0 || !m4aFile.exists() || m4aFile.length() == 0L) {
            throw IllegalStateException("ffmpeg M4A encoding failed with code ${proc.exitValue()}")
        }
        Log.i("AudioEncoder", "Encoded ${wavFile.name} -> ${m4aFile.name} (${m4aFile.length()} bytes)")
    }
}
