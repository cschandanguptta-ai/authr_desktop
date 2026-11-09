package com.example.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.data.api.ApiClient
import com.example.data.api.HuggingFaceTtsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CloudTtsManager {
    
    // Fallback to a commonly available HF VITS model
    private const val DEFAULT_MODEL = "facebook/mms-tts-eng"
    
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Synthesizes text using Hugging Face Inference API and saves it to a file.
     */
    suspend fun synthesizeToFile(
        text: String,
        hfToken: String,
        outputFile: File,
        modelName: String = DEFAULT_MODEL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val authHeader = if (hfToken.isNotEmpty()) "Bearer $hfToken" else "" // some models work without tokens
            val response = ApiClient.huggingFaceService.synthesizeText(
                model = modelName,
                authHeader = authHeader,
                request = HuggingFaceTtsRequest(inputs = text)
            )
            
            val inputStream: InputStream = response.byteStream()
            val outputStream = FileOutputStream(outputFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            true
        } catch (e: Exception) {
            Log.e("CloudTtsManager", "Failed to synthesize text: ${e.message}", e)
            false
        }
    }

    /**
     * Plays the audio from a file.
     */
    suspend fun playAudioFile(file: File, speed: Float = 1.0f, pitch: Float = 1.0f, volume: Float = 100f) = suspendCoroutine { continuation ->
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    continuation.resume(Unit)
                }
                setOnErrorListener { _, _, _ ->
                    continuation.resume(Unit)
                    true
                }
                prepare()
                try {
                    val params = android.media.PlaybackParams()
                    params.speed = speed
                    params.pitch = pitch
                    playbackParams = params
                } catch (e: Exception) {
                    Log.e("CloudTtsManager", "PlaybackParams error", e)
                }
                val v = (volume / 100f).coerceIn(0f, 1f)
                setVolume(v, v)
                start()
            }
        } catch (e: Exception) {
            Log.e("CloudTtsManager", "Failed to play audio: ${e.message}", e)
            continuation.resume(Unit)
        }
    }
    
    fun stopSpeaking() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    suspend fun speakParagraphSuspend(text: String, voiceName: String?, hfToken: String, cacheDir: File, speed: Float = 1.0f, pitch: Float = 1.0f, volume: Float = 100f): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val voiceKey = voiceName ?: "default"
                val rawKey = "${text.trim()}_${voiceKey}_${speed}_${pitch}"
                val digest = java.security.MessageDigest.getInstance("MD5").digest(rawKey.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                val cacheFile = File(cacheDir, "cloud_tts_chunk_${hex}.mp3")

                val synthSuccess = if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.i("CloudTtsManager", "Cache hit for chunk: $hex. Skipping synthesis.")
                    true
                } else {
                    synthesizeToFile(text, hfToken, cacheFile, voiceName ?: DEFAULT_MODEL)
                }

                if (synthSuccess) {
                    playAudioFile(cacheFile, speed, pitch, volume)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
