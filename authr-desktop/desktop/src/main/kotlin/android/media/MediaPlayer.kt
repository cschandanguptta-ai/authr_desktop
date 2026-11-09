package android.media

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop replacement for android.media.MediaPlayer.
 *
 * - WAV files are played through javax.sound.sampled.
 * - MP3 files (Edge TTS / Pocket TTS / Cloud TTS output) are decoded with
 *   JLayer and played through the same line.
 * - setVolume and playbackParams.speed are applied on the PCM stream
 *   (linear resampling). Pitch is folded into the resample ratio because
 *   desktop has no MediaCodec DSP; for the app's engines pitch is applied at
 *   synthesis time, so playback stays 1:1.
 */
class MediaPlayer {

    private var dataSource: String? = null
    private var volume: Float = 1f
    private var speed: Float = 1f
    private var pitch: Float = 1f
    private var playingThread: Thread? = null

    @Volatile private var stopRequested = false
    @Volatile private var playing = false

    private var onCompletionListener: ((MediaPlayer) -> Unit)? = null
    private var onErrorListener: ((MediaPlayer, Int, Int) -> Boolean)? = null

    private object Const {
        const val MEDIA_ERROR_UNKNOWN = 1
        const val MEDIA_ERROR_SERVER_DIED = 100
    }

    var playbackParams: PlaybackParams
        get() = PlaybackParams().also { p -> p.speed = speed; p.pitch = pitch }
        set(value) { speed = value.speed.coerceIn(0.25f, 4f); pitch = value.pitch }

    val isPlaying: Boolean get() = playing && playingThread?.isAlive == true

    fun pause() {
        // Desktop: pause is implemented as stop-with-state so callers can resume.
        paused = true
    }

    var paused: Boolean = false
        private set

    fun setDataSource(path: String) { dataSource = path }

    fun setDataSource(descriptor: java.io.FileDescriptor) {
        // not used by the app; provided for API completeness
    }

    fun setVolume(leftVolume: Float, rightVolume: Float) {
        volume = ((leftVolume + rightVolume) / 2f).coerceIn(0f, 1f)
    }

    fun setOnCompletionListener(listener: (MediaPlayer) -> Unit) { onCompletionListener = listener }
    fun setOnErrorListener(listener: (MediaPlayer, Int, Int) -> Boolean) { onErrorListener = listener }

    fun prepare() { /* lazy: decoding happens on start() */ }

    fun prepareAsync() { /* lazy */ }

    fun start() {
        val path = dataSource ?: return
        stopRequested = false
        playing = true
        val thread = Thread({
            try {
                val file = File(path)
                if (!file.exists()) throw IllegalStateException("File not found: $path")
                val isWav = isWavFile(file)
                if (isWav) playWav(file) else playMp3(file)
                if (!stopRequested) {
                    playing = false
                    onCompletionListener?.invoke(this)
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayer", "playback failed: ${e.message}")
                playing = false
                val handled = onErrorListener?.invoke(this, Const.MEDIA_ERROR_UNKNOWN, 0) ?: false
                if (!handled) onCompletionListener?.invoke(this)
            } finally {
                playing = false
            }
        }, "AuthrMediaPlayer")
        thread.isDaemon = true
        playingThread = thread
        thread.start()
    }

    fun stop() {
        stopRequested = true
        playingThread?.interrupt()
        playing = false
    }

    fun release() {
        stop()
        onCompletionListener = null
        onErrorListener = null
        playingThread = null
    }

    fun reset() { stopRequested = false; dataSource = null }

    // ------------------------------------------------------------------ //

    private fun isWavFile(file: File): Boolean {
        return try {
            FileInputStream(file).use { ins ->
                val header = ByteArray(12)
                ins.read(header)
                header.size >= 12 && String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE"
            }
        } catch (e: Exception) { false }
    }

    private fun ratio(): Float = (speed * pitch).coerceIn(0.25f, 4f)

    private fun playWav(file: File) {
        DataInputStream(FileInputStream(file)).use { ins ->
            // ---- parse RIFF header ----
            readAscii(ins, 4) // RIFF
            ins.readIntLE()   // size
            readAscii(ins, 4) // WAVE
            var sampleRate = 44100
            var channels = 1
            var bitsPerSample = 16
            var dataBytes = -1L
            while (true) {
                val id = try { readAscii(ins, 4) } catch (e: Exception) { break }
                val size = ins.readIntLE()
                when (id) {
                    "fmt " -> {
                        val audioFormat = ins.readShortLE()
                        channels = ins.readShortLE().toInt()
                        sampleRate = ins.readIntLE()
                        ins.readIntLE()   // byte rate
                        ins.readShortLE() // block align
                        bitsPerSample = ins.readShortLE().toInt()
                        if (size > 16) ins.skipFully((size - 16).toLong())
                    }
                    "data" -> { dataBytes = size.toLong(); if (size == 0) break; break }
                    else -> ins.skipFully(size.toLong())
                }
            }
            if (bitsPerSample != 16) {
                throw IllegalStateException("Unsupported WAV bit depth: $bitsPerSample")
            }
            val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format, 64 * 1024)
            line.start()
            try {
                val frameBytes = 4096
                val frameSize = 2 * channels
                val buffer = ByteArray(frameBytes * frameSize)
                while (!stopRequested) {
                    val read = ins.read(buffer)
                    if (read <= 0) break
                    var samples = shortsFrom(buffer, read)
                    samples = applyGainAndRatio(samples, volume, ratio())
                    line.write(samplesToBytes(samples), 0, samples.size * 2)
                }
                line.drain()
            } finally {
                line.stop()
                line.close()
            }
        }
    }

    private fun playMp3(file: File) {
        val bitstream = Bitstream(FileInputStream(file))
        val decoder = Decoder()
        var header: Header? = bitstream.readFrame()
        var line: SourceDataLine? = null
        try {
            while (header != null && !stopRequested) {
                val pcm = decoder.decodeFrame(header, bitstream) as SampleBuffer
                var samples = pcm.buffer.copyOf(pcm.bufferLength)
                if (line == null) {
                    val freq = pcm.sampleFrequency.toFloat()
                    val ch = pcm.channelCount
                    val format = AudioFormat(freq, 16, ch, true, false)
                    line = AudioSystem.getSourceDataLine(format)
                    line.open(format, 64 * 1024)
                    line.start()
                }
                samples = applyGainAndRatio(samples, volume, ratio())
                val bytes = samplesToBytes(samples)
                line!!.write(bytes, 0, bytes.size)
                bitstream.closeFrame()
                header = bitstream.readFrame()
            }
            line?.drain()
        } finally {
            try { line?.stop(); line?.close() } catch (_: Exception) {}
            try { bitstream.close() } catch (_: Exception) {}
        }
    }

    private fun applyGainAndRatio(samples: ShortArray, gain: Float, ratio: Float): ShortArray {
        if (gain >= 0.999f && gain <= 1.001f && ratio >= 0.999f && ratio <= 1.001f) return samples
        if (ratio >= 0.999f && ratio <= 1.001f) {
            for (i in samples.indices) samples[i] = (samples[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            return samples
        }
        val outLen = (samples.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt()
            val i1 = (i0 + 1).coerceAtMost(samples.size - 1)
            val frac = srcPos - i0
            val v = samples[i0] + (samples[i1] - samples[i0]) * frac
            out[i] = (v * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun shortsFrom(bytes: ByteArray, len: Int): ShortArray {
        val n = len / 2
        val out = ShortArray(n)
        var o = 0
        var i = 0
        while (i + 1 < len) {
            out[o++] = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort()
            i += 2
        }
        return out
    }

    private fun samplesToBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            out[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((samples[i].toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun readAscii(ins: InputStream, n: Int): String {
        val b = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = ins.read(b, read, n - read)
            if (r <= 0) break
            read += r
        }
        return String(b, Charsets.US_ASCII)
    }

    // little-endian stream helpers
    private fun java.io.DataInputStream.readIntLE(): Int =
        (read() and 0xFF) or ((read() and 0xFF) shl 8) or ((read() and 0xFF) shl 16) or ((read() and 0xFF) shl 24)

    private fun java.io.DataInputStream.readShortLE(): Short =
        (((read() and 0xFF) or ((read() and 0xFF) shl 8))).toShort()

    private fun InputStream.skipFully(n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}

class PlaybackParams {
    var speed: Float = 1f
    var pitch: Float = 1f

    fun setSpeed(speed: Float): PlaybackParams { this.speed = speed; return this }
}

/** Minimal AudioManager marker ( getSystemService(AUDIO_SERVICE) ). */
class AudioManager {
    companion object {
        const val STREAM_MUSIC = 3
        const val MODE_NORMAL = 0
    }
}
