package android.speech.tts

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * DESKTOP TextToSpeech — replaces the Android system TTS engine.
 *
 * Per the v3.0 audit (Edge TTS reject, 2026-11-12), the desktop "device /
 * system TTS" engine maps to the LOCAL Pocket TTS engine (via
 * PocketTtsManager) — the same production engine the audiobook renderer uses
 * — instead of the removed reverse-engineered Microsoft Edge cloud engine.
 * A missing Pocket binary/voice pack fails LOUD (the utterance errors); it
 * never silently downgrades to a cloud dependency. The API surface (speak /
 * synthesizeToFile / playSilentUtterance / UtteranceProgressListener / Voice)
 * is preserved 1:1 so AndroidTtsManager compiles unchanged.
 *
 * Extras beyond Android (per user request): custom cloud TTS providers
 * (ElevenLabs, Google Cloud TTS) are wired in through
 * [com.example.tts.CustomProviderTtsManager] at the settings level.
 */
class TextToSpeech(
    val context: Context,
    private val engine: String? = null,
    private val initListener: ((Int) -> Unit)? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile private var initialized = false
    @Volatile private var stopped = false

    var language: Locale = Locale.US
    var voice: Voice? = null
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var utteranceListener: UtteranceProgressListener? = null

    private val queue = LinkedBlockingQueue<QueueItem>()
    private val worker = Thread({ drainQueue() }, "AuthrSystemTts").apply {
        isDaemon = true
        start()
    }

    private sealed class QueueItem {
        abstract val utteranceId: String?
        data class Speak(val text: String, val volume: Float, override val utteranceId: String) : QueueItem()
        class Silence(val durationMs: Long, override val utteranceId: String) : QueueItem()
        object Flush : QueueItem() { override val utteranceId: String? = null }
    }

    init {
        // Desktop engine "initializes" instantly and successfully.
        initialized = true
        initListener?.invoke(SUCCESS)
    }

    val voices: Set<Voice>?
        get() = if (initialized) NARRATOR_VOICES else null

    fun setSpeechRate(rate: Float) { speechRate = rate }

    fun setPitch(pitch: Float) { this.pitch = pitch }

    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?) {
        utteranceListener = listener
    }

    fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String): Int {
        if (!initialized) return ERROR
        if (queueMode == QUEUE_FLUSH) queue.add(QueueItem.Flush)
        val volume = params?.getFloat(Engine.KEY_PARAM_VOLUME, 1f) ?: 1f
        queue.add(QueueItem.Speak(text, volume, utteranceId))
        return SUCCESS
    }

    fun synthesizeToFile(text: String, params: Bundle?, outputFile: File, utteranceId: String): Int {
        if (!initialized) return ERROR
        scope.launch {
            try {
                val ok = synthesizeBlocking(text, outputFile, params?.getFloat(Engine.KEY_PARAM_VOLUME, 1f) ?: 1f)
                if (ok) utteranceListener?.onDone(utteranceId)
                else utteranceListener?.onError(utteranceId)
            } catch (e: Exception) {
                android.util.Log.e("TextToSpeech", "synthesizeToFile failed", e)
                utteranceListener?.onError(utteranceId)
            }
        }
        return SUCCESS
    }

    fun playSilentUtterance(durationInMs: Long, queueMode: Int, utteranceId: String?): Int {
        if (queueMode == QUEUE_FLUSH) queue.add(QueueItem.Flush)
        queue.add(QueueItem.Silence(durationInMs, utteranceId ?: "silence"))
        return SUCCESS
    }

    fun stop(): Int {
        queue.clear()
        queue.add(QueueItem.Flush)
        return SUCCESS
    }

    fun shutdown() {
        stopped = true
        queue.clear()
        queue.add(QueueItem.Flush)
        com.example.tts.PocketTtsManager.stopSpeaking()
    }

    fun isLanguageAvailable(loc: Locale?): Int = LANG_AVAILABLE

    // ------------------------------------------------------------------ //

    private fun drainQueue() {
        while (!stopped) {
            try {
                val item = queue.take()
                when (item) {
                    is QueueItem.Flush -> {
                        com.example.tts.PocketTtsManager.stopSpeaking()
                    }
                    is QueueItem.Silence -> {
                        utteranceListener?.onStart(item.utteranceId)
                        Thread.sleep(item.durationMs)
                        utteranceListener?.onDone(item.utteranceId)
                    }
                    is QueueItem.Speak -> {
                        utteranceListener?.onStart(item.utteranceId)
                        try {
                            val voiceName = voice?.name ?: defaultVoiceFor(language)
                            val tempWav = File(context.cacheDir, "system_tts_${UUID.randomUUID()}.wav")
                            val ok = runBlocking {
                                com.example.tts.PocketTtsManager.synthesizeToFile(
                                    context = context,
                                    text = item.text,
                                    outputFile = tempWav,
                                    voiceName = voiceName,
                                    speed = speechRate
                                )
                            }
                            if (ok && tempWav.exists()) {
                                runBlocking {
                                    com.example.tts.PocketTtsManager.playAudioFile(tempWav, item.volume * 100f)
                                }
                                tempWav.delete()
                                utteranceListener?.onDone(item.utteranceId)
                            } else {
                                android.util.Log.w("TextToSpeech", "Pocket TTS unavailable (binary/voice pack missing) — system-TTS utterance fails loud; install the Pocket TTS model pack.")
                                utteranceListener?.onError(item.utteranceId)
                            }
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        } catch (e: Exception) {
                            android.util.Log.e("TextToSpeech", "speak failed: ${e.message}")
                            utteranceListener?.onError(item.utteranceId)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                android.util.Log.e("TextToSpeech", "queue worker error", e)
            }
        }
    }

    private fun synthesizeBlocking(text: String, outputFile: File, volume: Float): Boolean {
        val voiceName = voice?.name ?: defaultVoiceFor(language)
        // Pocket writes the audio directly (WAV); the removed Edge path needed
        // an MP3->WAV conversion step that no longer applies.
        return runBlocking {
            com.example.tts.PocketTtsManager.synthesizeToFile(
                context = context,
                text = text,
                outputFile = outputFile,
                voiceName = voiceName,
                speed = speechRate
            )
        }
    }

    private fun defaultVoiceFor(locale: Locale): String =
        NARRATOR_VOICES.firstOrNull { it.locale.language == locale.language }?.name
            ?: "pocket-narrator-soft"

    object Engine {
        const val KEY_PARAM_VOLUME = "volume"
        const val KEY_PARAM_PAN = "pan"
        const val KEY_PARAM_UTTERANCE_ID = "utteranceId"
        const val KEY_PARAM_STREAM = "streamType"

        const val INTENT_ACTION_TTS_SERVICE = "android.intent.action.TTS_SERVICE"
    }

    companion object {
        const val SUCCESS = 0
        const val ERROR = -1
        const val QUEUE_FLUSH = 0
        const val QUEUE_ADD = 1
        const val QUEUE_DESTROY = 2
        const val LANG_AVAILABLE = 0
        const val LANG_MISSING_DATA = -1
        const val LANG_NOT_SUPPORTED = -2

        /**
         * The local narrator slots the desktop system-TTS engine exposes.
         * Locale coverage here mirrors the Android engine's allowed language
         * filter (AndroidTtsManager.getAvailableVoices); a slot without an
         * installed Pocket voice pack still selects — synthesis then fails
         * loud with the install hint.
         */
        val NARRATOR_VOICES: Set<Voice> = setOf(
            // English
            Voice("pocket-narrator-soft", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-clear", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-warm", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-deep", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-brisk", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-grave", Locale.US, 400, 200, false, setOf("localTts")),
            Voice("pocket-clone-custom", Locale.US, 400, 200, false, setOf("localTts")),
            // Indic slots (mapped by language; synthesized with the narrator packs)
            Voice("pocket-narrator-soft-hi", Locale("hi", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-te", Locale("te", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-ta", Locale("ta", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-kn", Locale("kn", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-ml", Locale("ml", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-bn", Locale("bn", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-mr", Locale("mr", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-gu", Locale("gu", "IN"), 400, 200, false, setOf("localTts")),
            Voice("pocket-narrator-soft-pa", Locale("pa", "IN"), 400, 200, false, setOf("localTts"))
        )
    }
}

abstract class UtteranceProgressListener {
    abstract fun onStart(utteranceId: String?)
    abstract fun onDone(utteranceId: String?)
    @Deprecated("Deprecated in Java")
    open fun onError(utteranceId: String?) {}
    open fun onError(utteranceId: String?, errorCode: Int) { onError(utteranceId) }
    open fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {}
    open fun onStop(utteranceId: String?, interrupted: Boolean) {}
}

class Voice(
    val name: String,
    val locale: Locale,
    val quality: Int,
    val latency: Int,
    val networkRequired: Boolean,
    val features: Set<String>
) {
    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = other is Voice && other.name == name
    override fun hashCode(): Int = name.hashCode()
}
