package com.example.desktopbridge

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.File
import java.io.FileInputStream

/**
 * Pure-Java MP3 -> 16-bit PCM WAV converter (JLayer decode + manual WAV
 * header). Used to keep Android's "synthesizeToFile produces WAV" contract
 * when the desktop engine (Edge TTS) returns MP3.
 */
object Mp3ToWav {

    fun convert(mp3File: File, wavFile: File, volume: Float = 1f): Boolean {
        return try {
            val bitstream = Bitstream(FileInputStream(mp3File))
            val decoder = Decoder()
            var header = bitstream.readFrame()
            var sampleRate = 44100
            var channels = 1
            val pcm = ArrayList<ShortArray>()
            var totalSamples = 0
            while (header != null) {
                val frame = decoder.decodeFrame(header, bitstream) as SampleBuffer
                sampleRate = frame.sampleFrequency
                channels = frame.channelCount
                val chunk = frame.buffer.copyOf(frame.bufferLength)
                if (volume != 1f) {
                    for (i in chunk.indices) {
                        chunk[i] = (chunk[i] * volume).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                pcm.add(chunk)
                totalSamples += chunk.size
                bitstream.closeFrame()
                header = bitstream.readFrame()
            }
            bitstream.close()

            wavFile.parentFile?.mkdirs()
            java.io.DataOutputStream(
                java.io.BufferedOutputStream(java.io.FileOutputStream(wavFile))
            ).use { out ->
                val byteRate = sampleRate * channels * 2
                val dataSize = totalSamples * 2
                // RIFF header
                out.writeBytes("RIFF")
                writeIntLE(out, 36 + dataSize)
                out.writeBytes("WAVE")
                out.writeBytes("fmt ")
                writeIntLE(out, 16)
                writeShortLE(out, 1)                 // PCM
                writeShortLE(out, channels)
                writeIntLE(out, sampleRate)
                writeIntLE(out, byteRate)
                writeShortLE(out, channels * 2)      // block align
                writeShortLE(out, 16)                // bits per sample
                out.writeBytes("data")
                writeIntLE(out, dataSize)
                for (chunk in pcm) {
                    for (s in chunk) {
                        out.write(s.toInt() and 0xFF)
                        out.write((s.toInt() shr 8) and 0xFF)
                    }
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("Mp3ToWav", "conversion failed: ${e.message}", e)
            false
        }
    }

    private fun writeIntLE(out: java.io.DataOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
    }

    private fun writeShortLE(out: java.io.DataOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
    }
}
