package android.speech.tts

import android.content.Context
import android.os.Bundle
import com.example.desktopbridge.Mp3ToWav
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
 * Per the desktop porting decision, the "device / system TTS" engine maps to
 * Microsoft Edge TTS (identical to the app's built-in Edge engine) so no OS
 * dependency is required. The API surface (speak / synthesizeToFile /
 * playSilentUtterance / UtteranceProgressListener / Voice) is preserved 1:1
 * so AndroidTtsManager compiles unchanged.
 *
 * Extras beyond Android (per user request): custom cloud TTS providers
 * (ElevenLabs, Google Cloud TTS) are wired in through
 * [com.example.tts.CustomProviderTtsManager] at the settings level.
 */
class TextToSpeech(
    val context: Context,
    private val initListener: ((Int) -> Unit)?,
    private val engine: String? = null
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
        get() = if (initialized) EDGE_VOICES else null

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
        com.example.tts.EdgeTtsManager.stopSpeaking()
    }

    fun isLanguageAvailable(loc: Locale?): Int = LANG_AVAILABLE

    // ------------------------------------------------------------------ //

    private fun drainQueue() {
        while (!stopped) {
            try {
                val item = queue.take()
                when (item) {
                    is QueueItem.Flush -> {
                        com.example.tts.EdgeTtsManager.stopSpeaking()
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
                            val tempMp3 = File(context.cacheDir, "system_tts_${UUID.randomUUID()}.mp3")
                            val ok = runBlocking {
                                com.example.tts.EdgeTtsManager.synthesizeToFile(
                                    text = item.text,
                                    outputFile = tempMp3,
                                    voiceName = voiceName,
                                    pitch = pitch,
                                    rate = speechRate
                                )
                            }
                            if (ok && tempMp3.exists()) {
                                runBlocking {
                                    com.example.tts.EdgeTtsManager.playAudioFile(tempMp3, 1.0f, 1.0f, item.volume * 100f)
                                }
                                tempMp3.delete()
                                utteranceListener?.onDone(item.utteranceId)
                            } else {
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
        val tempMp3 = File(context.cacheDir, "system_tts_${UUID.randomUUID()}.mp3")
        val ok = runBlocking {
            com.example.tts.EdgeTtsManager.synthesizeToFile(
                text = text, outputFile = tempMp3, voiceName = voiceName, pitch = pitch, rate = speechRate
            )
        }
        if (!ok) return false
        val converted = Mp3ToWav.convert(tempMp3, outputFile, volume)
        tempMp3.delete()
        return converted
    }

    private fun defaultVoiceFor(locale: Locale): String {
        val lang = locale.language
        val country = locale.country
        EDGE_VOICES.firstOrNull { it.locale.language == lang && it.locale.country.equals(country, ignoreCase = true) }
            ?.let { return it.name }
        EDGE_VOICES.firstOrNull { it.locale.language == lang }?.let { return it.name }
        return "en-US-AriaNeural"
    }

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

        /** Curated Edge voice catalogue for the app's supported languages. */
        val EDGE_VOICES: Set<Voice> = setOf(
            // English
            Voice("en-US-AriaNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-US-GuyNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-US-JennyNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-US-MichelleNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-US-RogerNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-US-SteffanNeural", Locale.US, 400, 200, true, setOf("networkTts")),
            Voice("en-GB-SoniaNeural", Locale.UK, 400, 200, true, setOf("networkTts")),
            Voice("en-GB-RyanNeural", Locale.UK, 400, 200, true, setOf("networkTts")),
            Voice("en-GB-LibbyNeural", Locale.UK, 400, 200, true, setOf("networkTts")),
            Voice("en-IN-NeerjaNeural", Locale("en", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("en-IN-PrabhatNeural", Locale("en", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("en-AU-NatashaNeural", Locale("en", "AU"), 400, 200, true, setOf("networkTts")),
            Voice("en-AU-WilliamNeural", Locale("en", "AU"), 400, 200, true, setOf("networkTts")),
            // Hindi
            Voice("hi-IN-MadhurNeural", Locale("hi", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("hi-IN-SwaraNeural", Locale("hi", "IN"), 400, 200, true, setOf("networkTts")),
            // Telugu
            Voice("te-IN-ShrutiNeural", Locale("te", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("te-IN-MohanNeural", Locale("te", "IN"), 400, 200, true, setOf("networkTts")),
            // Tamil
            Voice("ta-IN-PriyaNeural", Locale("ta", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("ta-IN-ValluvarNeural", Locale("ta", "IN"), 400, 200, true, setOf("networkTts")),
            // Kannada
            Voice("kn-IN-SapnaNeural", Locale("kn", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("kn-IN-GaganNeural", Locale("kn", "IN"), 400, 200, true, setOf("networkTts")),
            // Malayalam
            Voice("ml-IN-SobhanaNeural", Locale("ml", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("ml-IN-MidhunNeural", Locale("ml", "IN"), 400, 200, true, setOf("networkTts")),
            // Bengali
            Voice("bn-IN-TanishaaNeural", Locale("bn", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("bn-IN-BashkarNeural", Locale("bn", "IN"), 400, 200, true, setOf("networkTts")),
            // Marathi
            Voice("mr-IN-AarohiNeural", Locale("mr", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("mr-IN-ManoharNeural", Locale("mr", "IN"), 400, 200, true, setOf("networkTts")),
            // Gujarati
            Voice("gu-IN-DhwaniNeural", Locale("gu", "IN"), 400, 200, true, setOf("networkTts")),
            Voice("gu-IN-NiranjanNeural", Locale("gu", "IN"), 400, 200, true, setOf("networkTts")),
            // Punjabi
            Voice("pa-IN-NeerjaNeuralP", Locale("pa", "IN"), 400, 200, true, setOf("networkTts"))
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
