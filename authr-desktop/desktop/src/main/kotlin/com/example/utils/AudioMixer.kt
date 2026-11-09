package com.example.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioMixer {

    // Mixes two WAV files (speech and background music) with ducking
    fun mixWithDucking(speechWav: File, bgmWav: File, outputWav: File, normalBgmVolume: Float = 0.5f, duckedBgmVolume: Float = 0.1f) {
        // This is a naive implementation that expects both files to be 44.1kHz, 16-bit, mono/stereo
        // Real implementation would parse WavInfo and convert if necessary
        
        val speechInfo = WavUtils.extractWavInfo(speechWav) ?: return
        val bgmInfo = WavUtils.extractWavInfo(bgmWav) ?: return
        
        if (speechInfo.bitsPerSample != 16 || bgmInfo.bitsPerSample != 16) return
        
        FileInputStream(speechWav).use { speechIn ->
            FileInputStream(bgmWav).use { bgmIn ->
                FileOutputStream(outputWav).use { out ->
                    
                    speechIn.skip(speechInfo.dataOffset)
                    bgmIn.skip(bgmInfo.dataOffset)
                    
                    // Write dummy header
                    val header = ByteArray(44)
                    out.write(header)
                    
                    val bufferSize = 4096
                    val speechBuffer = ByteArray(bufferSize)
                    val bgmBuffer = ByteArray(bufferSize)
                    
                    var totalDataSize = 0
                    
                    while (true) {
                        val speechRead = speechIn.read(speechBuffer)
                        if (speechRead <= 0) break
                        
                        val bgmRead = bgmIn.read(bgmBuffer, 0, speechRead)
                        // If BGM is shorter, we could loop it, but let's just pad with zero for now
                        if (bgmRead < speechRead && bgmRead > 0) {
                            for (i in bgmRead until speechRead) bgmBuffer[i] = 0
                        }
                        
                        // Detect speech presence to trigger ducking
                        var hasSpeech = false
                        val speechShorts = ShortArray(speechRead / 2)
                        ByteBuffer.wrap(speechBuffer, 0, speechRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(speechShorts)
                        
                        for (sample in speechShorts) {
                            if (Math.abs(sample.toInt()) > 500) { // Threshold for speech detection
                                hasSpeech = true
                                break
                            }
                        }
                        
                        val currentBgmVol = if (hasSpeech) duckedBgmVolume else normalBgmVolume
                        
                        val outBuffer = ByteBuffer.allocate(speechRead).order(ByteOrder.LITTLE_ENDIAN)
                        val bgmShorts = ShortArray(speechRead / 2)
                        
                        if (bgmRead > 0) {
                            ByteBuffer.wrap(bgmBuffer, 0, speechRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(bgmShorts)
                        }
                        
                        for (i in 0 until speechRead / 2) {
                            val speechSample = speechShorts[i]
                            val bgmSample = if (bgmRead > 0) (bgmShorts[i] * currentBgmVol).toInt() else 0
                            
                            var mixed = speechSample + bgmSample
                            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE.toInt()
                            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE.toInt()
                            
                            outBuffer.putShort(mixed.toShort())
                        }
                        
                        out.write(outBuffer.array())
                        totalDataSize += speechRead
                    }
                    
                    // Fix header
                    out.channel.position(0)
                    out.write(createWavHeader(totalDataSize, speechInfo.channels, speechInfo.sampleRate))
                }
            }
        }
    }
    
    private fun createWavHeader(dataSize: Int, channels: Int, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val byteRate = sampleRate * channels * 2
        
        // RIFF chunk descriptor
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, 36 + dataSize)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt sub-chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // Subchunk1Size
        writeShort(header, 20, 1) // AudioFormat (PCM)
        writeShort(header, 22, channels) // NumChannels
        writeInt(header, 24, sampleRate) // SampleRate
        writeInt(header, 28, byteRate) // ByteRate
        writeShort(header, 32, channels * 2) // BlockAlign
        writeShort(header, 34, 16) // BitsPerSample
        
        // data sub-chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, dataSize)
        
        return header
    }
    
    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
    
    private fun writeShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
