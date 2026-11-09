package com.example.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object EdgeTtsManager {

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"

    private var mediaPlayer: MediaPlayer? = null

    private fun generateGecToken(): String {
        val unixEpochMs = System.currentTimeMillis()
        val fileTimeMs = unixEpochMs + 11644473600000L
        val fileTime100Ns = fileTimeMs * 10000L
        val quantizedTime = (fileTime100Ns / 3000000000L) * 3000000000L
        val rawString = "${quantizedTime}$TRUSTED_CLIENT_TOKEN"
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = sha256.digest(rawString.toByteArray(Charsets.UTF_8))
        val sb = java.lang.StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format(java.util.Locale.US, "%02X", b))
        }
        return sb.toString()
    }

    suspend fun synthesizeToFile(
        text: String,
        outputFile: File,
        voiceName: String = "en-US-AriaNeural",
        pitch: Float = 1.0f,
        rate: Float = 1.0f
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val uuid = UUID.randomUUID().toString().replace("-", "")
            val gecToken = generateGecToken()
            val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$uuid&Sec-MS-GEC=$gecToken&Sec-MS-GEC-Version=1-143.0.3650.75"

            val muid = UUID.randomUUID().toString().replace("-", "").uppercase()
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Origin", ORIGIN)
                .addHeader("Pragma", "no-cache")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Cookie", "muid=$muid;")
                .build()

            val outputStream = FileOutputStream(outputFile)

            val client = ApiClient.okHttpClient
            var isFinished = false
            var hasResumed = false

            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Send config
                    val configMsg = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    // Format pitch and rate to string
                    // Edge TTS expects pitch like '+0Hz', rate like '+0%'
                    // Let's map Float (e.g. 1.0f is normal, 1.5f is +50%)
                    val ratePercentage = ((rate - 1.0f) * 100).toInt()
                    val rateString = if (ratePercentage >= 0) "+${ratePercentage}%" else "${ratePercentage}%"
                    
                    // SSML
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='$voiceName'><prosody pitch='+0Hz' rate='$rateString'>$text</prosody></voice></speak>"
                    val ssmlMsg = "X-RequestId:$uuid\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        isFinished = true
                        webSocket.close(1000, "Normal closure")
                        outputStream.flush()
                        outputStream.close()
                        if (!hasResumed) {
                            hasResumed = true
                            continuation.resume(true)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        val byteArray = bytes.toByteArray()
                        if (byteArray.size > 2) {
                            val headerLen = ByteBuffer.wrap(byteArray, 0, 2).short.toInt()
                            if (byteArray.size > 2 + headerLen) {
                                val audioData = byteArray.copyOfRange(2 + headerLen, byteArray.size)
                                outputStream.write(audioData)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EdgeTtsManager", "Error parsing binary msg", e)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!hasResumed) {
                        hasResumed = true
                        outputStream.flush()
                        outputStream.close()
                        if (isFinished) {
                            continuation.resume(true)
                        } else {
                            continuation.resumeWithException(Exception("WebSocket closed before synthesis finished (Reason: $reason)"))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("EdgeTtsManager", "WebSocket Failure", t)
                    if (!hasResumed) {
                        hasResumed = true
                        try {
                            outputStream.close()
                        } catch (e: Exception) {}
                        continuation.resumeWithException(t)
                    }
                }
            })

            continuation.invokeOnCancellation {
                webSocket.cancel()
                try {
                    outputStream.close()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("EdgeTtsManager", "Edge TTS synthesis error", e)
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

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
                    Log.e("EdgeTtsManager", "PlaybackParams error", e)
                }
                val v = (volume / 100f).coerceIn(0f, 1f)
                setVolume(v, v)
                start()
            }
        } catch (e: Exception) {
            Log.e("EdgeTtsManager", "Failed to play audio: ${e.message}", e)
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

    suspend fun speakParagraphSuspend(text: String, voiceName: String, cacheDir: File, speed: Float = 1.0f, pitch: Float = 1.0f, volume: Float = 100f): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rawKey = "${text.trim()}_${voiceName}_${speed}_${pitch}"
                val digest = java.security.MessageDigest.getInstance("MD5").digest(rawKey.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                val cacheFile = File(cacheDir, "edge_tts_chunk_${hex}.mp3")

                val synthSuccess = if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.i("EdgeTtsManager", "Cache hit for chunk: $hex. Skipping synthesis.")
                    true
                } else {
                    synthesizeToFile(text, cacheFile, voiceName, pitch, speed)
                }

                if (synthSuccess) {
                    playAudioFile(cacheFile, 1.0f, 1.0f, volume) // the speed and pitch are already applied in synth
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
