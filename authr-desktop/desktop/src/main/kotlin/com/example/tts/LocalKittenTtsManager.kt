package com.example.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LocalKittenTtsManager now delegates directly to KittenTtsManager (Cloud Gradio API)
 * or EdgeTtsManager as ONNX TTS runtime has been completely removed.
 */
object LocalKittenTtsManager {
    var skipInitForTesting = false

    suspend fun initialize(context: Context) {
        Log.d("LocalKittenTtsManager", "ONNX TTS removed; using cloud/edge TTS pipeline.")
    }

    suspend fun synthesizeToFile(
        context: Context,
        text: String,
        outputFile: File,
        voiceName: String = "expr-voice-5-m",
        speed: Float = 1.0f,
        chunkSize: Int = 400,
        onChunkProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onChunkProgress(1, 1)
            val success = KittenTtsManager.synthesizeToFile(text, outputFile, voiceName, speed)
            if (!success) {
                EdgeTtsManager.synthesizeToFile(text = text, outputFile = outputFile, voiceName = "en-US-AriaNeural", pitch = 1.0f, rate = speed)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w("LocalKittenTtsManager", "Fallback to Edge TTS due to error", e)
            EdgeTtsManager.synthesizeToFile(text = text, outputFile = outputFile, voiceName = "en-US-AriaNeural", pitch = 1.0f, rate = speed)
        }
    }

    suspend fun playAudioFile(file: File, volume: Float = 100f) {
        KittenTtsManager.playAudioFile(file, volume)
    }

    fun stopSpeaking() {
        KittenTtsManager.stopSpeaking()
        EdgeTtsManager.stopSpeaking()
    }

    suspend fun speakParagraphSuspend(
        context: Context,
        text: String,
        voiceName: String,
        speed: Float,
        volume: Float,
        cacheDir: File
    ): Boolean {
        return KittenTtsManager.speakParagraphSuspend(
            text = text,
            voiceName = voiceName,
            speed = speed,
            volume = volume,
            cacheDir = cacheDir
        )
    }
}
