package com.example.utils

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

object WavUtils {

    data class WavInfo(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long
    )

    fun extractWavInfo(file: File): WavInfo? {
        file.inputStream().use { input ->
            val header = ByteArray(12)
            if (input.read(header) != 12) return null
            if (String(header, 0, 4) != "RIFF") return null
            if (String(header, 8, 4) != "WAVE") return null

            var channels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = -1L
            
            var offset = 12L
            val chunkHeader = ByteArray(8)
            while (input.read(chunkHeader) == 8) {
                val chunkId = String(chunkHeader, 0, 4)
                val chunkSize = getInt(chunkHeader, 4).toLong() and 0xFFFFFFFFL
                
                offset += 8
                
                if (chunkId == "fmt ") {
                    val fmtData = ByteArray(chunkSize.toInt())
                    input.read(fmtData)
                    channels = getShort(fmtData, 2).toInt()
                    sampleRate = getInt(fmtData, 4)
                    bitsPerSample = getShort(fmtData, 14).toInt()
                    
                    val bytesToSkip = if (chunkSize % 2L == 1L) 1L else 0L
                    if (bytesToSkip > 0) input.skip(1)
                    
                    offset += chunkSize + bytesToSkip
                } else if (chunkId == "data") {
                    dataOffset = offset
                    dataSize = chunkSize
                    break
                } else {
                    val skipBytes = chunkSize + (chunkSize % 2L)
                    input.skip(skipBytes)
                    offset += skipBytes
                }
            }
            
            if (channels > 0 && sampleRate > 0 && dataOffset > 0) {
                return WavInfo(channels, sampleRate, bitsPerSample, dataOffset, dataSize)
            }
            return null
        }
    }

    fun humanizeWavFile(file: File) {
        val info = extractWavInfo(file) ?: return
        if (info.bitsPerSample != 16) return // We process 16-bit PCM (standard for local TTS)

        val tempFile = File(file.parent, "${file.name}.humanized.wav")
        try {
            file.inputStream().use { input ->
                FileOutputStream(tempFile).use { out ->
                    // Copy the original header first (we don't change sample rate, channels, or size)
                    val header = ByteArray(info.dataOffset.toInt())
                    var headerRead = 0
                    while (headerRead < header.size) {
                        val read = input.read(header, headerRead, header.size - headerRead)
                        if (read <= 0) break
                        headerRead += read
                    }
                    out.write(header, 0, headerRead)

                    // Set up DSP parameters based on sampleRate
                    val sampleRate = info.sampleRate
                    val channels = info.channels

                    // Multi-tap Studio Reverb Delays (approx. 18ms, 31ms, 45ms)
                    val delay1 = maxOf(1, (sampleRate * 0.018).toInt())
                    val delay2 = maxOf(1, (sampleRate * 0.031).toInt())
                    val delay3 = maxOf(1, (sampleRate * 0.045).toInt())

                    // Size circular buffers for all channels
                    val bufferSize1 = delay1 * channels
                    val bufferSize2 = delay2 * channels
                    val bufferSize3 = delay3 * channels

                    val delayLine1 = FloatArray(bufferSize1)
                    val delayLine2 = FloatArray(bufferSize2)
                    val delayLine3 = FloatArray(bufferSize3)

                    var idx1 = 0
                    var idx2 = 0
                    var idx3 = 0

                    // Warmth low-pass filter states (one per channel)
                    val lastLpf = FloatArray(channels) { 0.0f }

                    val buffer = ByteArray(4096)
                    var remaining = info.dataSize
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead <= 0) break

                        // Process sample bytes
                        var offset = 0
                        while (offset + 1 < bytesRead) {
                            val channelIdx = (offset / 2) % channels

                            // Read 16-bit PCM sample
                            val low = buffer[offset].toInt() and 0xFF
                            val high = buffer[offset + 1].toInt()
                            val originalShort = ((high shl 8) or low).toShort()
                            val sampleVal = originalShort.toFloat() / 32768.0f

                            // 1. Retrieve delay taps
                            val tap1 = delayLine1[idx1]
                            val tap2 = delayLine2[idx2]
                            val tap3 = delayLine3[idx3]

                            // Combined reverb values with acoustic dispersion weights
                            val reverbVal = tap1 * 0.16f + tap2 * 0.10f + tap3 * 0.06f

                            // 2. Feed current dry value into delay lines with decay feedback
                            delayLine1[idx1] = sampleVal + tap1 * 0.22f
                            delayLine2[idx2] = sampleVal + tap2 * 0.16f
                            delayLine3[idx3] = sampleVal + tap3 * 0.10f

                            // Advance circular buffer indices (multiplied by channels)
                            if (channelIdx == channels - 1) {
                                idx1 = (idx1 + 1) % bufferSize1
                                idx2 = (idx2 + 1) % bufferSize2
                                idx3 = (idx3 + 1) % bufferSize3
                            }

                            // 3. Mix dry signal (80%) and room reverb (20%)
                            val mixedVal = sampleVal * 0.80f + reverbVal * 0.20f

                            // 4. Apply warmth filter (gentle Low-Pass with alpha = 0.42)
                            val alpha = 0.42f
                            val lpfVal = lastLpf[channelIdx] + alpha * (mixedVal - lastLpf[channelIdx])
                            lastLpf[channelIdx] = lpfVal

                            // Combine low-passed warm signal (70%) with direct mixed signal (30%)
                            val finalVal = mixedVal * 0.30f + lpfVal * 0.70f

                            // Clamp sample to prevent clipping
                            val clampedVal = finalVal.coerceIn(-1.0f, 1.0f)
                            val outShort = (clampedVal * 32767.0f).toInt().toShort()

                            // Write back 16-bit PCM sample
                            buffer[offset] = (outShort.toInt() and 0xFF).toByte()
                            buffer[offset + 1] = ((outShort.toInt() ushr 8) and 0xFF).toByte()

                            offset += 2
                        }

                        out.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                }
            }

            // Replace the original file with the humanized temp file
            if (file.delete()) {
                tempFile.renameTo(file)
            } else {
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun generateAmbientSample(theme: String, t: Double): Double {
        return when (theme) {
            "Cinematic Tension" -> {
                val subBass = kotlin.math.sin(2.0 * kotlin.math.PI * 45.0 * t) * 0.45
                val highDrone = kotlin.math.sin(2.0 * kotlin.math.PI * 850.0 * t) * 0.15
                val lfo = 0.5 + 0.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.05 * t)
                (subBass + highDrone * lfo) * 0.25
            }
            "Ethereal Space" -> {
                val pad1 = kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t)
                val pad2 = kotlin.math.sin(2.0 * kotlin.math.PI * 330.0 * t)
                val shimmer = kotlin.math.sin(2.0 * kotlin.math.PI * 1200.0 * t) * kotlin.math.sin(2.0 * kotlin.math.PI * 0.1 * t)
                (pad1 * 0.3 + pad2 * 0.2 + shimmer * 0.1) * 0.25
            }
            "Cyberpunk Haze" -> {
                val drone = kotlin.math.sin(2.0 * kotlin.math.PI * 110.0 * t)
                val lfo = 0.5 + 0.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.8 * t)
                val terminalBeep = if ((t % 4.0) < 0.12) {
                    val beepFreq = if ((t % 4.0) < 0.06) 1300.0 else 1950.0
                    kotlin.math.sin(2.0 * kotlin.math.PI * beepFreq * t) * 0.03
                } else 0.0
                (drone * lfo * 0.3) + terminalBeep
            }
            "Busy Cafe Atmosphere" -> {
                val randomNoise = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * 0.07
                val murmur1 = kotlin.math.sin(2.0 * kotlin.math.PI * 175.0 * t) * 0.04 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.4 * t)
                val murmur2 = kotlin.math.sin(2.0 * kotlin.math.PI * 330.0 * t) * 0.03 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.25 * t)
                val cupClink = if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.0012) {
                    val clinkFreq = 2300.0 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 800.0
                    kotlin.math.sin(2.0 * kotlin.math.PI * clinkFreq * t) * kotlin.math.exp(-((t % 0.07) * 110.0)) * 0.07
                } else 0.0
                randomNoise + murmur1 + murmur2 + cupClink
            }
            "Deep Forest Wind" -> {
                val foliageNoise = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0)
                val windGustLfo = 0.35 + 0.65 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.045 * t)
                val rustle = foliageNoise * windGustLfo * 0.12
                val windWhistle = kotlin.math.sin(2.0 * kotlin.math.PI * (580.0 + 120.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.12 * t)) * t) * windGustLfo * 0.025
                rustle + windWhistle
            }
            "Dark Dungeon Drone" -> {
                val subBass = kotlin.math.sin(2.0 * kotlin.math.PI * 41.2 * t) * 0.55
                val tensionPad = kotlin.math.sin(2.0 * kotlin.math.PI * 61.7 * t) * 0.25
                val echoPulse = if ((t % 6.0) < 0.35) {
                    val pulseFactor = kotlin.math.sin((t % 6.0) / 0.35 * kotlin.math.PI)
                    kotlin.math.sin(2.0 * kotlin.math.PI * 120.0 * t) * pulseFactor * 0.05
                } else 0.0
                (subBass + tensionPad) * 0.22 + echoPulse
            }
            else -> 0.0
        }
    }

    private fun mixBuffer(
        buffer: ByteArray,
        bytesRead: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        theme: String,
        ambientVolume: Float,
        narrationVolume: Float,
        startSampleFrame: Long,
        envState: DoubleArray
    ): Long {
        val bytesPerSample = bitsPerSample / 8
        var currentFrame = startSampleFrame

        if (bytesPerSample != 2) return currentFrame

        val narrVol = narrationVolume
        val hasAmbient = theme != "None" && ambientVolume > 0.0f

        var offset = 0
        while (offset + 1 < bytesRead) {
            val t = currentFrame.toDouble() / sampleRate

            val low = buffer[offset].toInt() and 0xFF
            val high = buffer[offset + 1].toInt()
            val originalShort = ((high shl 8) or low).toShort()

            val narrationSample = (originalShort.toDouble() / 32768.0) * narrVol
            val ambientSample = if (hasAmbient) generateAmbientSample(theme, t) else 0.0
            val mixedSample = narrationSample + ambientSample * ambientVolume
            val clampedSample = kotlin.math.tanh(mixedSample)

            val mixedShort = (clampedSample * 32767.0).toInt().toShort()

            buffer[offset] = (mixedShort.toInt() and 0xFF).toByte()
            buffer[offset + 1] = ((mixedShort.toInt() ushr 8) and 0xFF).toByte()

            offset += 2

            if (channels > 0 && (offset / 2) % channels == 0) {
                currentFrame++
            }
        }
        return currentFrame
    }


    private fun buildId3Tag(title: String, artist: String, album: String, plotPointMarkers: List<com.example.data.model.PlotPointMarker>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun writeFrame(id: String, text: String) {
            val textBytes = text.toByteArray(Charsets.ISO_8859_1)
            val frameSize = textBytes.size + 1
            out.write(id.toByteArray(Charsets.ISO_8859_1))
            out.write(byteArrayOf((frameSize shr 24).toByte(), (frameSize shr 16).toByte(), (frameSize shr 8).toByte(), frameSize.toByte()))
            out.write(byteArrayOf(0, 0))
            out.write(0)
            out.write(textBytes)
        }
        fun writeTxxxFrame(description: String, text: String) {
            val descBytes = description.toByteArray(Charsets.ISO_8859_1)
            val textBytes = text.toByteArray(Charsets.ISO_8859_1)
            val frameSize = descBytes.size + 1 + textBytes.size + 1
            out.write("TXXX".toByteArray(Charsets.ISO_8859_1))
            out.write(byteArrayOf((frameSize shr 24).toByte(), (frameSize shr 16).toByte(), (frameSize shr 8).toByte(), frameSize.toByte()))
            out.write(byteArrayOf(0, 0))
            out.write(0)
            out.write(descBytes)
            out.write(0)
            out.write(textBytes)
        }
        fun writeChapFrame(marker: com.example.data.model.PlotPointMarker, index: Int) {
            val elementId = "chap$index".toByteArray(Charsets.ISO_8859_1)
            val startTimeMs = marker.timestampMs
            val endTimeMs = marker.timestampMs + marker.durationMs
            
            val chapOut = java.io.ByteArrayOutputStream()
            chapOut.write(elementId)
            chapOut.write(0)
            chapOut.write(byteArrayOf((startTimeMs shr 24).toByte(), (startTimeMs shr 16).toByte(), (startTimeMs shr 8).toByte(), startTimeMs.toByte()))
            chapOut.write(byteArrayOf((endTimeMs shr 24).toByte(), (endTimeMs shr 16).toByte(), (endTimeMs shr 8).toByte(), endTimeMs.toByte()))
            chapOut.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
            chapOut.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
            
            // Sub-frame TIT2
            val titleBytes = marker.titleLabel.toByteArray(Charsets.ISO_8859_1)
            val tit2Size = titleBytes.size + 1
            chapOut.write("TIT2".toByteArray(Charsets.ISO_8859_1))
            chapOut.write(byteArrayOf((tit2Size shr 24).toByte(), (tit2Size shr 16).toByte(), (tit2Size shr 8).toByte(), tit2Size.toByte()))
            chapOut.write(byteArrayOf(0, 0))
            chapOut.write(0)
            chapOut.write(titleBytes)
            
            val chapBytes = chapOut.toByteArray()
            val frameSize = chapBytes.size
            out.write("CHAP".toByteArray(Charsets.ISO_8859_1))
            out.write(byteArrayOf((frameSize shr 24).toByte(), (frameSize shr 16).toByte(), (frameSize shr 8).toByte(), frameSize.toByte()))
            out.write(byteArrayOf(0, 0))
            out.write(chapBytes)
        }

        writeFrame("TIT2", title)
        writeFrame("TPE1", artist)
        writeFrame("TALB", album)
        
        val markersJson = "[" + plotPointMarkers.joinToString(",") { 
            "{\"chapterIndex\":${it.chapterIndex},\"titleLabel\":\"${it.titleLabel.replace('"', '\'')}\",\"plotSummary\":\"${it.plotSummary.replace('"', '\'')}\",\"timestampMs\":${it.timestampMs},\"durationMs\":${it.durationMs}}"
        } + "]"
        writeTxxxFrame("PLOT_POINT_MARKERS", markersJson)
        
        plotPointMarkers.forEachIndexed { index, marker ->
            writeChapFrame(marker, index)
        }

        val framesBytes = out.toByteArray()
        val id3Header = ByteArray(10)
        id3Header[0] = 73.toByte(); id3Header[1] = 68.toByte(); id3Header[2] = 51.toByte()
        id3Header[3] = 3; id3Header[4] = 0; id3Header[5] = 0
        val size = framesBytes.size
        id3Header[6] = ((size shr 21) and 0x7F).toByte(); id3Header[7] = ((size shr 14) and 0x7F).toByte()
        id3Header[8] = ((size shr 7) and 0x7F).toByte(); id3Header[9] = (size and 0x7F).toByte()
        return id3Header + framesBytes
    }


    private fun generateCueAndTxtFiles(wavFile: java.io.File, title: String, artist: String, markers: List<com.example.data.model.PlotPointMarker>) {
        val parent = wavFile.parentFile ?: return
        val baseName = wavFile.nameWithoutExtension
        val cueFile = java.io.File(parent, "$baseName.cue")
        val txtFile = java.io.File(parent, "${baseName}_Plot_Points.txt")
        
        val cueSb = StringBuilder()
        cueSb.append("TITLE \"$title\"\n")
        cueSb.append("PERFORMER \"$artist\"\n")
        cueSb.append("FILE \"${wavFile.name}\" WAVE\n")
        
        val txtSb = StringBuilder()
        txtSb.append("Sync Manifest: $title\n")
        txtSb.append("==============================\n")
        
        for ((i, marker) in markers.withIndex()) {
            val trackNum = String.format("%02d", i + 1)
            val timeStr = formatCueTime(marker.timestampMs)
            cueSb.append("  TRACK $trackNum AUDIO\n")
            cueSb.append("    TITLE \"${marker.titleLabel}\"\n")
            cueSb.append("    INDEX 01 $timeStr\n")
            
            val humanTime = formatHumanTime(marker.timestampMs)
            txtSb.append("[$humanTime] Chapter ${marker.chapterIndex}: ${marker.titleLabel}\n")
            txtSb.append("Summary: ${marker.plotSummary}\n\n")
        }
        
        cueFile.writeText(cueSb.toString())
        txtFile.writeText(txtSb.toString())
    }
    
    private fun formatCueTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val frames = ((ms % 1000) * 75) / 1000
        return String.format("%02d:%02d:%02d", mins, secs, frames)
    }
    
    private fun formatHumanTime(ms: Long): String {
        val totalSecs = ms / 1000
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)
    }

    fun mergeWavFiles(files: List<File>, outputFile: File, silenceSeconds: Float, theme: String, ambientVolume: Float, narrationVolume: Float, title: String = "", artist: String = "", album: String = "", plotPointMarkers: List<com.example.data.model.PlotPointMarker> = emptyList()) {
        if (files.isEmpty()) return

        val firstInfo = extractWavInfo(files.first()) ?: return

        val channels = firstInfo.channels
        val sampleRate = firstInfo.sampleRate
        val bitsPerSample = firstInfo.bitsPerSample

        FileOutputStream(outputFile).use { out ->
            // Write placeholder header (44 bytes)
            out.write(ByteArray(44))

            var totalDataSize = 0L
            var currentFrame = 0L
            val envState = DoubleArray(1) { 0.0 }

            for ((index, file) in files.withIndex()) {
                val info = extractWavInfo(file) ?: continue
                file.inputStream().use { input ->
                    input.skip(info.dataOffset)
                    
                    val fadeFrames = (sampleRate * 0.02).toLong() // 20ms fade
                    val totalFrames = info.dataSize / (channels * (bitsPerSample / 8))
                    var localFrame = 0L
                    
                    var remainingData = info.dataSize
                    val buffer = ByteArray(4096)
                    while (remainingData > 0) {
                        val toRead = minOf(remainingData, buffer.size.toLong()).toInt()
                        val bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead <= 0) break
                        
                        // Apply fade in/out
                        var offset = 0
                        while (offset + 1 < bytesRead) {
                            val low = buffer[offset].toInt() and 0xFF
                            val high = buffer[offset + 1].toInt()
                            var sample = ((high shl 8) or low).toShort()
                            
                            val localFrameIndex = localFrame + (offset / (channels * 2))
                            
                            var multiplier = 1.0f
                            if (localFrameIndex < fadeFrames) {
                                multiplier = localFrameIndex.toFloat() / fadeFrames
                            } else if (localFrameIndex > totalFrames - fadeFrames) {
                                multiplier = (totalFrames - localFrameIndex).toFloat() / fadeFrames
                            }
                            
                            if (multiplier < 1.0f) {
                                sample = (sample * multiplier).toInt().toShort()
                                buffer[offset] = (sample.toInt() and 0xFF).toByte()
                                buffer[offset + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
                            }
                            offset += 2
                        }
                        localFrame += bytesRead / (channels * 2)
                        
                        currentFrame = mixBuffer(buffer, bytesRead, sampleRate, channels, bitsPerSample, theme, ambientVolume, narrationVolume, currentFrame, envState)
                        out.write(buffer, 0, bytesRead)
                        totalDataSize += bytesRead
                        remainingData -= bytesRead
                    }
                }

                // If not the last file, insert silence
                if (index < files.lastIndex && silenceSeconds > 0) {
                    val bytesPerFrame = channels * (bitsPerSample / 8)
                    // Ensure padding is at least 1.5 seconds if silence padding is enabled
                    val actualSilence = maxOf(silenceSeconds, 1.5f)
                    val rawSilenceBytes = (sampleRate * bytesPerFrame * actualSilence).toLong()
                    val silenceBytesCount = rawSilenceBytes - (rawSilenceBytes % bytesPerFrame)
                    
                    val silenceBuffer = ByteArray(4096)
                    var bytesToWrite = silenceBytesCount
                    while (bytesToWrite > 0) {
                        val writeSize = minOf(bytesToWrite, silenceBuffer.size.toLong()).toInt()
                        
                        val mixedSilenceBuffer = ByteArray(writeSize)
                        currentFrame = mixBuffer(mixedSilenceBuffer, writeSize, sampleRate, channels, bitsPerSample, theme, ambientVolume, narrationVolume, currentFrame, envState)
                        
                        out.write(mixedSilenceBuffer, 0, writeSize)
                        totalDataSize += writeSize
                        bytesToWrite -= writeSize
                    }
                }
            }


            // ID3 tag embedding
            val id3Tag = buildId3Tag(title, artist, album, plotPointMarkers)
            out.write(id3Tag)
            totalDataSize += id3Tag.size

            // Generate .cue and .txt files
            if (plotPointMarkers.isNotEmpty()) {
                generateCueAndTxtFiles(outputFile, title, artist, plotPointMarkers)
            }


            // Update WAV header
            RandomAccessFile(outputFile, "rw").use { raf ->
                val headerBuffer = ByteArray(44)
                
                // Size of file minus 8
                val totalFileSize = totalDataSize + 36
                setInt(headerBuffer, 4, totalFileSize.toInt())
                // WAVE
                headerBuffer[8] = 'W'.code.toByte()
                headerBuffer[9] = 'A'.code.toByte()
                headerBuffer[10] = 'V'.code.toByte()
                headerBuffer[11] = 'E'.code.toByte()
                // fmt 
                headerBuffer[12] = 'f'.code.toByte()
                headerBuffer[13] = 'm'.code.toByte()
                headerBuffer[14] = 't'.code.toByte()
                headerBuffer[15] = ' '.code.toByte()
                // Length of format chunk
                setInt(headerBuffer, 16, 16)
                // PCM format (1)
                setShort(headerBuffer, 20, 1)
                // Channels
                setShort(headerBuffer, 22, channels.toShort())
                // Sample Rate
                setInt(headerBuffer, 24, sampleRate)
                // Byte Rate
                val blockAlign = channels * (bitsPerSample / 8)
                setInt(headerBuffer, 28, sampleRate * blockAlign)
                // Block Align
                setShort(headerBuffer, 32, blockAlign.toShort())
                // Bits Per Sample
                setShort(headerBuffer, 34, bitsPerSample.toShort())
                // data
                headerBuffer[36] = 'd'.code.toByte()
                headerBuffer[37] = 'a'.code.toByte()
                headerBuffer[38] = 't'.code.toByte()
                headerBuffer[39] = 'a'.code.toByte()
                // Size of data section
                setInt(headerBuffer, 40, totalDataSize.toInt())

                raf.seek(0)
                // RIFF
                headerBuffer[0] = 'R'.code.toByte()
                headerBuffer[1] = 'I'.code.toByte()
                headerBuffer[2] = 'F'.code.toByte()
                headerBuffer[3] = 'F'.code.toByte()

                raf.write(headerBuffer)
            }
        }
    }

    fun generateSilenceWav(outputFile: File, durationSeconds: Float, sampleRate: Int = 44100, channels: Int = 2, bitsPerSample: Int = 16) {
        val bytesPerFrame = channels * (bitsPerSample / 8)
        val totalDataSize = (sampleRate * bytesPerFrame * durationSeconds).toLong()
        val silenceBytesCount = totalDataSize - (totalDataSize % bytesPerFrame)

        FileOutputStream(outputFile).use { out ->
            // Header
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            setInt(header, 4, (silenceBytesCount + 36).toInt())
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            setInt(header, 16, 16)
            setShort(header, 20, 1)
            setShort(header, 22, channels.toShort())
            setInt(header, 24, sampleRate)
            setInt(header, 28, sampleRate * bytesPerFrame)
            setShort(header, 32, bytesPerFrame.toShort())
            setShort(header, 34, bitsPerSample.toShort())
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            setInt(header, 40, silenceBytesCount.toInt())
            out.write(header)

            // Data
            val buffer = ByteArray(4096)
            var bytesRemaining = silenceBytesCount
            while (bytesRemaining > 0) {
                val toWrite = if (bytesRemaining > buffer.size) buffer.size else bytesRemaining.toInt()
                out.write(buffer, 0, toWrite)
                bytesRemaining -= toWrite
            }
        }
    }

    private fun getShort(bytes: ByteArray, offset: Int): Short {
        return ((bytes[offset + 1].toInt() and 0xFF shl 8) or (bytes[offset].toInt() and 0xFF)).toShort()
    }

    private fun getInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset + 3].toInt() and 0xFF shl 24) or
               (bytes[offset + 2].toInt() and 0xFF shl 16) or
               (bytes[offset + 1].toInt() and 0xFF shl 8) or
               (bytes[offset].toInt() and 0xFF)
    }

    private fun setShort(bytes: ByteArray, offset: Int, value: Short) {
        bytes[offset] = (value.toInt() and 0xFF).toByte()
        bytes[offset + 1] = (value.toInt() ushr 8 and 0xFF).toByte()
    }

    private fun setInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value.toInt() and 0xFF).toByte()
        bytes[offset + 1] = (value.toInt() ushr 8 and 0xFF).toByte()
        bytes[offset + 2] = (value.toInt() ushr 16 and 0xFF).toByte()
        bytes[offset + 3] = (value.toInt() ushr 24 and 0xFF).toByte()
    }
}
