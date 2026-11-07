package com.example.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object AudioEncoder {

    fun encodeWavToM4a(wavFile: File, m4aFile: File) {
        // Basic MediaCodec AAC encoder
        val sampleRate = 44100
        val channelCount = 2
        val bitRate = 128000
        
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        val muxer = MediaMuxer(m4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        codec.start()
        
                var audioTrackIndex = -1
        var muxerStarted = false
        
        FileInputStream(wavFile).use { fis ->
            // Skip 44 bytes WAV header
            fis.skip(44)
            
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            
            while (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                    inputBuffer.clear()
                    
                    val buffer = ByteArray(inputBuffer.capacity())
                    val readBytes = fis.read(buffer)
                    
                    if (readBytes < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        inputBuffer.put(buffer, 0, readBytes)
                        codec.queueInputBuffer(inputBufferIndex, 0, readBytes, System.nanoTime() / 1000, 0)
                    }
                }
                
                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    
                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            val newFormat = codec.outputFormat
                            audioTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }
                    
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEOS = true
                        break
                    }
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                }
                
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw RuntimeException("Format changed twice")
                    }
                }
            }
        }
        
        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
            muxer.release()
        }
    }

    fun tagMp3WithId3v2(mp3File: File, title: String, author: String, album: String) {
        // A minimal ID3v2.3 tagger implementation
        val originalBytes = mp3File.readBytes()
        
        // Helper to encode strings to ID3 frames
        fun makeFrame(id: String, text: String): ByteArray {
            val textBytes = text.toByteArray(Charsets.ISO_8859_1)
            val frameSize = textBytes.size + 1
            val frame = ByteArray(10 + frameSize)
            System.arraycopy(id.toByteArray(), 0, frame, 0, 4)
            // Size is not synchsafe in ID3v2.3 frames
            frame[4] = (frameSize shr 24).toByte()
            frame[5] = (frameSize shr 16).toByte()
            frame[6] = (frameSize shr 8).toByte()
            frame[7] = frameSize.toByte()
            // Flags
            frame[8] = 0
            frame[9] = 0
            // Encoding byte (0 = ISO-8859-1)
            frame[10] = 0
            System.arraycopy(textBytes, 0, frame, 11, textBytes.size)
            return frame
        }
        
        val titleFrame = makeFrame("TIT2", title)
        val authorFrame = makeFrame("TPE1", author)
        val albumFrame = makeFrame("TALB", album)
        
        val totalTagSize = titleFrame.size + authorFrame.size + albumFrame.size
        
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3 // Version 2.3
        header[4] = 0 // Revision
        header[5] = 0 // Flags
        
        // Size is encoded as 4 synchsafe integers (7 bits each)
        header[6] = ((totalTagSize shr 21) and 0x7F).toByte()
        header[7] = ((totalTagSize shr 14) and 0x7F).toByte()
        header[8] = ((totalTagSize shr 7) and 0x7F).toByte()
        header[9] = (totalTagSize and 0x7F).toByte()
        
        val newBytes = ByteArray(10 + totalTagSize + originalBytes.size)
        System.arraycopy(header, 0, newBytes, 0, 10)
        
        var offset = 10
        System.arraycopy(titleFrame, 0, newBytes, offset, titleFrame.size)
        offset += titleFrame.size
        
        System.arraycopy(authorFrame, 0, newBytes, offset, authorFrame.size)
        offset += authorFrame.size
        
        System.arraycopy(albumFrame, 0, newBytes, offset, albumFrame.size)
        offset += albumFrame.size
        
        System.arraycopy(originalBytes, 0, newBytes, offset, originalBytes.size)
        
        mp3File.writeBytes(newBytes)
    }
}
