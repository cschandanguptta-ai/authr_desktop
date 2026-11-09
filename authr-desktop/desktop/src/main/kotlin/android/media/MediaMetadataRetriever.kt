package android.media

import android.util.Base64
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Header
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Desktop replacement for android.media.MediaMetadataRetriever.
 *
 * Supports the only metadata key the app uses: duration.
 *  - WAV: parsed directly from the RIFF header (exact)
 *  - MP3: computed from the first MPEG frame header + file size (CBR math,
 *    accurate for the TTS engine outputs)
 *  - anything else: ffmpeg/ffprobe fallback when available
 */
class MediaMetadataRetriever {

    private var dataSource: String? = null

    fun setDataSource(path: String) { dataSource = path }

    fun extractMetadata(key: Int): String? {
        if (key != METADATA_KEY_DURATION) return null
        val path = dataSource ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            when {
                isWav(file) -> wavDurationMs(file)
                isMp3(file) -> mp3DurationMs(file)
                else -> ffprobeDurationMs(file)
            }?.toString()
        } catch (e: Exception) {
            android.util.Log.w("MediaMetadataRetriever", "duration probe failed for $path: ${e.message}")
            null
        }
    }

    fun release() { dataSource = null }

    companion object {
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_TITLE = 7
        const val METADATA_KEY_ARTIST = 2
        const val METADATA_KEY_ALBUM = 1

        private fun isWav(file: File): Boolean {
            RandomAccessFile(file, "r").use { raf ->
                val h = ByteArray(12)
                raf.readFully(h)
                return String(h, 0, 4) == "RIFF" && String(h, 8, 4) == "WAVE"
            }
        }

        private fun isMp3(file: File): Boolean {
            RandomAccessFile(file, "r").use { raf ->
                val h = ByteArray(3)
                raf.readFully(h)
                if (h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()) return true
                return (h[0].toInt() and 0xFF) == 0xFF && (h[1].toInt() and 0xE0) == 0xE0
            }
        }

        private fun wavDurationMs(file: File): Long? {
            java.io.DataInputStream(FileInputStream(file)).use { dis ->
                fun ascii(n: Int) = ByteArray(n).also { dis.readFully(it) }.toString(Charsets.US_ASCII)
                fun intLE(): Int = (dis.read() and 0xFF) or ((dis.read() and 0xFF) shl 8) or
                        ((dis.read() and 0xFF) shl 16) or ((dis.read() and 0xFF) shl 24)
                fun shortLE(): Int = (dis.read() and 0xFF) or ((dis.read() and 0xFF) shl 8)
                ascii(4); intLE(); ascii(4)
                var byteRate = -1
                while (true) {
                    val id = try { ascii(4) } catch (e: Exception) { break }
                    val size = intLE()
                    if (id == "fmt ") {
                        shortLE()          // audio format
                        val channels = shortLE()
                        val sampleRate = intLE()
                        byteRate = intLE()
                        shortLE()          // block align
                        shortLE()          // bits per sample
                        if (size > 16) { var s = size - 16; while (s > 0) { dis.read(); s-- } }
                    } else if (id == "data") {
                        val dataBytes = if (size != 0) size.toLong() else (file.length() - 44)
                        if (byteRate > 0) return (dataBytes * 1000L / byteRate)
                        return -1L
                    } else {
                        var s = size; while (s > 0) { dis.read(); s-- }
                    }
                }
                return null
            }
        }

        private fun mp3DurationMs(file: File): Long? {
            return try {
                val bitstream = Bitstream(FileInputStream(file))
                val header: Header? = bitstream.readFrame()
                if (header == null) null
                else {
                    // trim is not perfect for VBR but exact for the CBR TTS outputs
                    val ms = file.length().toDouble() / (header.bitrate() / 8.0) * 1000.0
                    bitstream.close()
                    ms.toLong()
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun ffprobeDurationMs(file: File): Long? {
            return try {
                val ffprobe = com.example.desktopbridge.FfmpegLocator.ffprobePath() ?: return null
                val process = ProcessBuilder(ffprobe, "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", file.absolutePath)
                    .redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                out.toDoubleOrNull()?.let { (it * 1000).toLong() }
            } catch (e: Exception) {
                null
            }
        }
    }
}
