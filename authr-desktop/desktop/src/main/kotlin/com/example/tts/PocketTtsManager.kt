package com.example.tts

import android.content.Context
import android.util.Log
import com.example.desktopbridge.PocketTtsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pocket TTS manager (v3.0, SCR-3) — the primary LOCAL synthesis engine.
 *
 * Replaces the removed v2.3 cloud-queue + native-lib local TTS stack.
 * Pocket TTS (Kyutai Labs, MIT) is a 100M-parameter
 * local engine that:
 *   - runs faster than real time on ordinary CPUs,
 *   - streams unbounded text (whole chapters synthesize without chunk-stitching),
 *   - clones the narrator voice from a short WAV reference into a fixed
 *     safetensors voice pack, so narrator identity is identical across
 *     chapters by construction (plan §7.4).
 *
 * Degradation contract: when the Pocket TTS binary or the requested voice pack
 * is unavailable (first run, model pack not installed, constrained Android
 * device), synthesis falls back to Edge TTS so the audio pipeline stays
 * functional. The fallback is logged, never silent.
 */
object PocketTtsManager {

    const val ENGINE_LABEL = "Pocket TTS"

    /** Narrator voice packs directory: <data>/audio/voice_packs. */
    fun voicePacksDir(context: Context): File =
        File(File(context.filesDir, "audio"), "voice_packs").apply { mkdirs() }

    /** Project-level synthesis temperature, pinned per project for reproducible renders (plan §7.4). */
    @Volatile var synthesisTemperature: Double = 0.6

    private val _activeEngine = MutableStateFlowCompat()
    val lastRouteUsed: String get() = _activeEngine.value

    private class MutableStateFlowCompat {
        @Volatile var value: String = "none"
    }

    suspend fun initialize(context: Context) {
        Log.d("PocketTtsManager", "Initializing Pocket TTS bridge (binary + voice pack scan)…")
        val packs = PocketTtsBridge.listVoicePacks(voicePacksDir(context))
        if (packs.isEmpty()) {
            Log.i("PocketTtsManager", "No narrator voice packs installed yet; the default narrator pack is created on first synthesis or voice import.")
        } else {
            Log.i("PocketTtsManager", "Found ${packs.size} narrator voice packs: $packs")
        }
    }

    /**
     * Synthesizes [text] to [outputFile]. Returns true when Pocket TTS (or the
     * Edge fallback) produced a non-empty audio file.
     */
    suspend fun synthesizeToFile(
        context: Context,
        text: String,
        outputFile: File,
        voiceName: String = "pocket-narrator-soft",
        speed: Float = 1.0f,
        @Suppress("UNUSED_PARAMETER") chunkSize: Int = 400,
        onChunkProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        onChunkProgress(1, 1)
        val packName = normalizePackName(voiceName)
        val packsDir = voicePacksDir(context)
        val packReady = PocketTtsBridge.resolveVoicePack(packName, packsDir) != null
        if (packReady) {
            val ok = PocketTtsBridge.synthesizeToFile(
                text = text,
                outputFile = outputFile,
                packName = packName,
                packsDir = packsDir,
                temperature = synthesisTemperature
            )
            if (ok) {
                _activeEngine.value = "pocket-tts"
                return@withContext true
            }
            Log.w("PocketTtsManager", "Pocket TTS synthesis failed (${PocketTtsBridge.lastError.value}); falling back to Edge TTS.")
        } else {
            Log.w("PocketTtsManager", "Voice pack '$packName' not installed; falling back to Edge TTS. Install the Pocket TTS model pack for fully offline narration.")
        }
        val edgeOk = runCatching {
            EdgeTtsManager.synthesizeToFile(
                text = text,
                outputFile = outputFile,
                voiceName = "en-US-AriaNeural",
                pitch = 1.0f,
                rate = speed
            )
        }.getOrElse { false }
        if (edgeOk) _activeEngine.value = "edge-fallback"
        edgeOk
    }

    suspend fun playAudioFile(file: File, volume: Float = 100f) {
        // Playback routes through the shared desktop MediaPlayer shim
        // (WAV via javax.sound.sampled, MP3 via JLayer).
        CloudTtsManager.playAudioFile(file, speed = 1.0f, pitch = 1.0f, volume = volume)
    }

    fun stopSpeaking() {
        EdgeTtsManager.stopSpeaking()
        CloudTtsManager.stopSpeaking()
    }

    suspend fun speakParagraphSuspend(
        context: Context,
        text: String,
        voiceName: String,
        speed: Float,
        volume: Float,
        cacheDir: File
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rawKey = "${text.trim()}_pocket_${voiceName}_$speed"
                val digest = java.security.MessageDigest.getInstance("MD5").digest(rawKey.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                val cacheFile = File(cacheDir, "pockettts_chunk_${hex}.mp3")

                val synthSuccess = if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.i("PocketTtsManager", "Cache hit for chunk: $hex. Skipping synthesis.")
                    true
                } else {
                    synthesizeToFile(context, text, cacheFile, voiceName, speed)
                }

                if (synthSuccess) {
                    playAudioFile(cacheFile, volume)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.w("PocketTtsManager", "speakParagraphSuspend failed", e)
                false
            }
        }
    }

    /**
     * Clones the author's narrator voice from a short WAV reference into a
     * named pack (plan §7.1 voice cloning; used by the audiobook workbench).
     */
    suspend fun cloneNarratorVoice(context: Context, referenceWav: File, packName: String): Boolean {
        val ok = PocketTtsBridge.cloneVoice(referenceWav, normalizePackName(packName), voicePacksDir(context))
        if (!ok) Log.w("PocketTtsManager", "Voice clone failed: ${PocketTtsBridge.lastError.value}")
        return ok
    }

    /** Maps legacy engine voice ids onto the Pocket narrator slots. */
    private fun normalizePackName(voiceName: String): String =
        when {
            voiceName.startsWith("pocket-") -> voiceName
            voiceName.isBlank() -> "pocket-narrator-soft"
            else -> "pocket-narrator-soft"
        }
}
