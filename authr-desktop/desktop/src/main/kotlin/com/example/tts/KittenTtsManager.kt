package com.example.tts

import android.media.MediaPlayer
import android.util.Log
import com.example.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object KittenTtsManager {
    private var mediaPlayer: MediaPlayer? = null
    private const val BASE_URL = "https://kingnish-kitten-tts.hf.space/gradio_api/"

    private fun generateSessionHash(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11)
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 6000L,
        factor: Double = 2.0,
        block: suspend () -> T?
    ): T? {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                if (result != null) return result
            } catch (e: Exception) {
                Log.w("KittenTtsManager", "SSE Gradio attempt ${attempt + 1} failed: ${e.message}")
            }
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return null
    }

    suspend fun synthesizeToFile(
        text: String,
        outputFile: File,
        voiceName: String = "expr-voice-5-m",
        speed: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val result = executeWithRetry {
            try {
                val sessionHash = generateSessionHash()
                
                // 1. Join the queue via POST
                val joinUrl = "${BASE_URL}queue/join"
                val joinPayload = JSONObject().apply {
                    put("fn_index", 2)
                    put("data", org.json.JSONArray(listOf(text, voiceName, speed.toDouble())))
                    put("session_hash", sessionHash)
                }
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = joinPayload.toString().toRequestBody(mediaType)
                val joinRequest = Request.Builder()
                    .url(joinUrl)
                    .post(requestBody)
                    .build()
                    
                ApiClient.okHttpClient.newCall(joinRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("KittenTtsManager", "Join queue failed: ${response.code}")
                        return@executeWithRetry null
                    }
                    val bodyStr = response.body?.string() ?: ""
                    Log.d("KittenTtsManager", "Join queue response: $bodyStr")
                }
                
                // 2. Stream queue/data via GET
                val dataUrl = "${BASE_URL}queue/data?session_hash=$sessionHash"
                val dataRequest = Request.Builder()
                    .url(dataUrl)
                    .get()
                    .build()
                    
                var audioUrl: String? = null
                
                ApiClient.okHttpClient.newCall(dataRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("KittenTtsManager", "SSE connection failed: ${response.code}")
                        return@executeWithRetry null
                    }
                    
                    val source = response.body?.source() ?: return@executeWithRetry null
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()?.trim() ?: break
                        if (line.startsWith("data:")) {
                            val jsonData = line.substring(5).trim()
                            if (jsonData.isEmpty()) continue
                            val json = JSONObject(jsonData)
                            val msg = json.optString("msg", "")
                            Log.d("KittenTtsManager", "SSE event: $msg")
                            
                            if (msg == "process_completed") {
                                val success = json.optBoolean("success", false)
                                if (success) {
                                    val output = json.optJSONObject("output")
                                    val dataArray = output?.optJSONArray("data")
                                    val firstOutput = dataArray?.optJSONObject(0)
                                    audioUrl = firstOutput?.optString("url")
                                }
                                break
                            } else if (msg == "close_stream") {
                                break
                            }
                        }
                    }
                }
                
                if (audioUrl == null) {
                    Log.e("KittenTtsManager", "Did not receive process_completed or audio URL")
                    return@executeWithRetry null
                }
                
                // 3. Download the audio file from the audioUrl
                val downloadRequest = Request.Builder()
                    .url(audioUrl!!)
                    .get()
                    .build()
                    
                ApiClient.okHttpClient.newCall(downloadRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("KittenTtsManager", "Download audio failed: ${response.code}")
                        return@executeWithRetry null
                    }
                    
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@executeWithRetry null
                }
                
                true
            } catch (e: Exception) {
                Log.e("KittenTtsManager", "Synthesize error", e)
                null
            }
        }
        result == true
    }

    suspend fun playAudioFile(file: File, volume: Float = 100f) = suspendCoroutine { continuation ->
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
                val v = (volume / 100f).coerceIn(0f, 1f)
                setVolume(v, v)
                start()
            }
        } catch (e: Exception) {
            Log.e("KittenTtsManager", "Playback error", e)
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

    suspend fun speakParagraphSuspend(
        text: String,
        voiceName: String,
        speed: Float,
        volume: Float,
        cacheDir: File
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rawKey = "${text.trim()}_${voiceName}_${speed}"
                val digest = java.security.MessageDigest.getInstance("MD5").digest(rawKey.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                val cacheFile = File(cacheDir, "kitten_tts_chunk_${hex}.mp3")

                val synthSuccess = if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.i("KittenTtsManager", "Cache hit for chunk: $hex. Skipping synthesis.")
                    true
                } else {
                    synthesizeToFile(text, cacheFile, voiceName, speed)
                }

                if (synthSuccess) {
                    playAudioFile(cacheFile, volume)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
