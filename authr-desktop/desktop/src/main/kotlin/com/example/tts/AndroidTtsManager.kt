package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale

class AndroidTtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isVoxSherpaActive = false
        private set

    init {
        val hasVoxSherpa = try {
            context.packageManager.getPackageInfo("com.CodeBySonu.VoxSherpa", 0)
            true
        } catch (e: Exception) {
            false
        }

        if (hasVoxSherpa) {
            Log.i("AndroidTtsManager", "VoxSherpa engine detected. Initializing with com.CodeBySonu.VoxSherpa.")
            tts = TextToSpeech(context, "com.CodeBySonu.VoxSherpa") { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    isVoxSherpaActive = true
                    tts?.language = Locale.US
                    onInitComplete(true)
                } else {
                    Log.e("AndroidTtsManager", "VoxSherpa TTS Initialization failed! Falling back to default.")
                    initializeDefaultTts()
                }
            }
        } else {
            initializeDefaultTts()
        }
    }

    private fun initializeDefaultTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.US
                onInitComplete(true)
            } else {
                Log.e("AndroidTtsManager", "TTS Initialization failed!")
                onInitComplete(false)
            }
        }
    }

    private fun determineVoiceGender(voiceName: String): String {
        val nameLower = voiceName.lowercase(Locale.US)
        
        // Direct explicit checks
        if (nameLower.contains("female")) return "Female"
        if (nameLower.contains("male")) return "Male"
        
        // Let's check common Google TTS codes
        val isFemale = nameLower.contains("sfg") || 
                       nameLower.contains("tpf") || 
                       nameLower.contains("iob") || 
                       nameLower.contains("iol") || 
                       nameLower.contains("iog") || 
                       nameLower.contains("ioj") || 
                       nameLower.contains("iof") || 
                       nameLower.contains("ava") || 
                       nameLower.contains("jen") || 
                       nameLower.contains("gbe") || 
                       nameLower.contains("gbc") || 
                       nameLower.contains("gba") || 
                       nameLower.contains("ene") || 
                       nameLower.contains("enc") || 
                       nameLower.contains("ena") || 
                       nameLower.contains("hia") || 
                       nameLower.contains("hid") || 
                       nameLower.contains("hie") || 
                       nameLower.contains("-fem-") ||
                       nameLower.contains("-f-") ||
                       nameLower.endsWith("f")
                       
        val isMale = nameLower.contains("iom") || 
                     nameLower.contains("rgf") || 
                     nameLower.contains("adm") || 
                     nameLower.contains("rjs") || 
                     nameLower.contains("tpd") ||
                     nameLower.contains("gbd") || 
                     nameLower.contains("gbb") || 
                     nameLower.contains("end") || 
                     nameLower.contains("enb") || 
                     nameLower.contains("hib") || 
                     nameLower.contains("hic") || 
                     nameLower.contains("-male-") ||
                     nameLower.contains("-m-") ||
                     nameLower.endsWith("m")
                     
        return when {
            isFemale -> "Female"
            isMale -> "Male"
            else -> "Neutral"
        }
    }

    private fun determineEmotionalResonance(voiceName: String): String {
        val nameLower = voiceName.lowercase(Locale.US)
        return when {
            nameLower.contains("sfg") || nameLower.contains("rjs") || nameLower.contains("adm") || nameLower.contains("tpf") -> "Dramatic"
            nameLower.contains("ava") || nameLower.contains("jen") || nameLower.contains("hid") || nameLower.contains("hie") -> "Soothing"
            else -> "Narrative"
        }
    }

    fun getAvailableVoices(): List<VoiceItem> {
        if (!isInitialized) return emptyList()
        val voices = tts?.voices ?: return emptyList()
        val allowedLangs = setOf("en", "hi", "te", "ta", "kn", "ml", "bn", "mr", "gu", "pa", "or")
        return voices.filter { allowedLangs.contains(it.locale.language) }
            .map { voice ->
                val type = if (voice.features.contains("networkTts")) "Natural (Online)" else "Standard (Offline)"
                val gender = determineVoiceGender(voice.name)
                val resonance = determineEmotionalResonance(voice.name)
                VoiceItem(
                    name = voice.name,
                    locale = voice.locale,
                    gender = gender,
                    type = type,
                    voiceObj = voice,
                    emotionalResonance = resonance
                )
            }
            .sortedWith(
                compareByDescending<VoiceItem> { it.type.contains("Natural") }
                    .thenBy { it.locale.language }
                    .thenBy { it.locale.country }
                    .thenBy { it.gender }
                    .thenBy { it.name }
            )
    }

    fun speakPreview(text: String, voiceName: String?, rate: Float, pitch: Float = 1.0f, volume: Float = 100f) {
        if (!isInitialized) return
        tts?.apply {
            stop()
            setSpeechRate(rate)
            setPitch(pitch)
            voiceName?.let { name ->
                val matchingVoice = voices?.find { it.name == name }
                if (matchingVoice != null) {
                    voice = matchingVoice
                }
            }
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (volume / 100f).coerceIn(0f, 1f))
            }
            speak(text, TextToSpeech.QUEUE_FLUSH, params, "preview")
        }
    }

    suspend fun speakParagraphSuspend(
        text: String,
        voiceName: String?,
        rate: Float, pitch: Float = 1.0f, volume: Float = 100f
    ): Boolean {
        if (!isInitialized || tts == null) return false
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        
        tts?.apply {
            setSpeechRate(rate)
            setPitch(pitch)
            voiceName?.let { name ->
                val matchingVoice = voices?.find { it.name == name }
                if (matchingVoice != null) {
                    voice = matchingVoice
                }
            }
            
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    deferred.complete(true)
                }
                
                override fun onError(utteranceId: String?) {
                    deferred.complete(false)
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    deferred.complete(false)
                }
            })
            
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (volume / 100f).coerceIn(0f, 1f))
            }
            speak(text, TextToSpeech.QUEUE_FLUSH, params, "p_single")
        } ?: return false
        
        try {
            return deferred.await()
        } catch (e: Exception) {
            return false
        }
    }

    fun speakSequential(
        paragraphs: List<String>,
        voiceName: String?,
        rate: Float, pitch: Float = 1.0f, volume: Float = 100f,
        pauseMs: Long,
        onParagraphStart: (Int) -> Unit,
        onPlaybackComplete: () -> Unit
    ) {
        if (!isInitialized || tts == null) return
        tts?.apply {
            stop()
            setSpeechRate(rate)
            setPitch(pitch)
            voiceName?.let { name ->
                val matchingVoice = voices?.find { it.name == name }
                if (matchingVoice != null) {
                    voice = matchingVoice
                }
            }
            
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != null && utteranceId.startsWith("p_")) {
                        val index = utteranceId.substringAfter("p_").toIntOrNull() ?: -1
                        onParagraphStart(index)
                    }
                }
                
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "done" || utteranceId == "preview") {
                        onPlaybackComplete()
                    }
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == "done" || utteranceId == "preview") {
                        onPlaybackComplete()
                    }
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == "done" || utteranceId == "preview") {
                        onPlaybackComplete()
                    }
                }
            })
            
            if (paragraphs.isEmpty()) {
                onPlaybackComplete()
                return
            }

            for (i in paragraphs.indices) {
                val text = paragraphs[i]
                val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (volume / 100f).coerceIn(0f, 1f))
            }
            speak(text, TextToSpeech.QUEUE_ADD, params, "p_$i")
                if (i < paragraphs.lastIndex && pauseMs > 0) {
                    playSilentUtterance(pauseMs, TextToSpeech.QUEUE_ADD, "silence_$i")
                }
            }
            
            // Queue a final "done" utterance so we get a callback when finished
            playSilentUtterance(100L, TextToSpeech.QUEUE_ADD, "done")
        }
    }

    fun stopSpeaking() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun synthesizeToFile(
        text: String,
        outputFile: File,
        voiceName: String?,
        rate: Float, pitch: Float = 1.0f, volume: Float = 100f,
        utteranceId: String,
        listener: UtteranceProgressListener
    ): Boolean {
        if (!isInitialized) return false
        tts?.apply {
            setOnUtteranceProgressListener(listener)
            setSpeechRate(rate)
            setPitch(pitch)
            voiceName?.let { name ->
                val matchingVoice = voices?.find { it.name == name }
                if (matchingVoice != null) {
                    voice = matchingVoice
                }
            }
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (volume / 100f).coerceIn(0f, 1f))
            }
            val result = synthesizeToFile(text, params, outputFile, utteranceId)
            return result == TextToSpeech.SUCCESS
        }
        return false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

data class VoiceItem(
    val name: String,
    val locale: Locale,
    val gender: String,
    val type: String,
    val voiceObj: Voice? = null,
    val emotionalResonance: String,
    val customDisplayName: String? = null
) {
    val displayName: String
        get() = customDisplayName ?: "${locale.displayLanguage} (${locale.displayCountry}) - $gender ($type) [$emotionalResonance]"
}
