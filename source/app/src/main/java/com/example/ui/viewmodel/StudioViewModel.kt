package com.example.ui.viewmodel


import android.app.Application
import android.content.Context
import android.os.Environment
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.local.AppDatabase
import com.example.data.model.AudioTrack
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import com.example.tts.AndroidTtsManager
import com.example.tts.VoiceItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.worker.DraftingWorker
import com.example.worker.WorldBuildingWorker
import com.example.worker.OutlineWorker
import com.example.worker.PolishingWorker
import com.example.worker.CompilationWorker
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.domain.usecase.GetChaptersUseCase
import com.example.domain.usecase.GenerateStoryUseCase


data class EmotionPreset(
    val name: String,
    val speed: Float,
    val pitch: Float,
    val volume: Float
)

val defaultEmotionPresets = listOf(
    EmotionPreset("Neutral", 1.00f, 0f, 100f),
    EmotionPreset("Sultry", 0.75f, -2f, 90f),
    EmotionPreset("Happy", 1.10f, 2f, 105f),
    EmotionPreset("Excited", 1.20f, 3f, 110f),
    EmotionPreset("Sad", 0.85f, -2f, 90f),
    EmotionPreset("Angry", 1.10f, -1f, 115f),
    EmotionPreset("Fear", 0.95f, 2f, 95f),
    EmotionPreset("Suspense", 0.80f, -1f, 90f),
    EmotionPreset("Horror", 0.75f, -3f, 85f),
    EmotionPreset("Mystery", 0.85f, -1f, 95f),
    EmotionPreset("Romantic", 0.90f, 1f, 95f),
    EmotionPreset("Inspirational", 1.00f, 1f, 105f),
    EmotionPreset("Epic", 0.95f, -2f, 115f),
    EmotionPreset("Adventure", 1.05f, 1f, 105f),
    EmotionPreset("Comedy", 1.15f, 2f, 105f),
    EmotionPreset("Bedtime Story", 0.70f, 0f, 85f),
    EmotionPreset("Documentary", 0.95f, 0f, 100f),
    EmotionPreset("News", 1.00f, 0f, 100f),
    EmotionPreset("Children's Story", 1.10f, 3f, 105f)
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class StudioViewModel(
    application: android.app.Application,
    private val repository: com.example.data.repository.AppRepository,
    private val getChaptersUseCase: com.example.domain.usecase.GetChaptersUseCase
) : androidx.lifecycle.AndroidViewModel(application) {

    companion object {
        var skipInitForTesting = false
    }


    private val _globalErrorEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val globalErrorEvent = _globalErrorEvent.asSharedFlow()

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, exception ->
        val msg = exception.localizedMessage ?: "Unknown Coroutine Error"
        Log.e("StudioViewModel", "Unhandled Coroutine Exception: $msg", exception)
        addDeveloperLog("CRITICAL ERROR: $msg")
        _globalErrorEvent.tryEmit(msg)
    }

    // --- State variables ---
    private val _currentTab = MutableStateFlow(0) // 0: Story, 1: Audio, 2: Translate, 3: Metadata, 4: Recs, 5: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Database Flows
    val allStories: StateFlow<List<Story>> = repository.allStories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAudioTracks: StateFlow<List<AudioTrack>> = repository.allAudioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBooks: StateFlow<List<Book>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings
    private val sharedPrefs = try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(application)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()

        androidx.security.crypto.EncryptedSharedPreferences.create(
            application,
            "secure_studio_settings",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        try {
            application.deleteSharedPreferences("secure_studio_settings")
        } catch (_: Throwable) {}
        application.getSharedPreferences("secure_studio_settings_fallback", Context.MODE_PRIVATE)
    }

    private val _geminiApiKey = MutableStateFlow(sharedPrefs.getString("gemini_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _mistralApiKey = MutableStateFlow(sharedPrefs.getString("mistral_key", "") ?: "")
    val mistralApiKey: StateFlow<String> = _mistralApiKey

    private val _groqApiKey = MutableStateFlow(sharedPrefs.getString("groq_key", "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") ?: "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")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _groqModel = MutableStateFlow(sharedPrefs.getString("groq_model", "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b")
    val groqModel: StateFlow<String> = _groqModel.asStateFlow()

    private val _nvidiaApiKey = MutableStateFlow(sharedPrefs.getString("nvidia_key", "") ?: "")
    val nvidiaApiKey: StateFlow<String> = _nvidiaApiKey.asStateFlow()

    private val _nvidiaModel = MutableStateFlow(sharedPrefs.getString("nvidia_model", "mistralai/mistral-nemotron") ?: "mistralai/mistral-nemotron")
    val nvidiaModel: StateFlow<String> = _nvidiaModel.asStateFlow()

    private val _customOpenAiApiKey = MutableStateFlow(sharedPrefs.getString("custom_openai_key", "") ?: "")
    val customOpenAiApiKey: StateFlow<String> = _customOpenAiApiKey.asStateFlow()

    private val _customOpenAiBaseUrl = MutableStateFlow(sharedPrefs.getString("custom_openai_base_url", "https://integrate.api.nvidia.com/v1") ?: "https://integrate.api.nvidia.com/v1")
    val customOpenAiBaseUrl: StateFlow<String> = _customOpenAiBaseUrl.asStateFlow()

    private val _customOpenAiModel = MutableStateFlow(sharedPrefs.getString("custom_openai_model", "deepseek-ai/deepseek-v4-flash") ?: "deepseek-ai/deepseek-v4-flash")
    val customOpenAiModel: StateFlow<String> = _customOpenAiModel.asStateFlow()

    private val _ollamaApiKey = MutableStateFlow(sharedPrefs.getString("ollama_key", "") ?: "")
    val ollamaApiKey: StateFlow<String> = _ollamaApiKey

    private val _ollamaModel = MutableStateFlow(sharedPrefs.getString("ollama_model", "gemma4:31b") ?: "gemma4:31b")
    val ollamaModel: StateFlow<String> = _ollamaModel

    private val _ollamaBaseUrl = MutableStateFlow(sharedPrefs.getString("ollama_base_url", "https://api.ollama.com/") ?: "https://api.ollama.com/")
    val ollamaBaseUrl: StateFlow<String> = _ollamaBaseUrl.asStateFlow()

    private val _hfApiKey = MutableStateFlow(sharedPrefs.getString("hf_key", "") ?: "")
    val hfApiKey: StateFlow<String> = _hfApiKey.asStateFlow()
    
    private val _hfModel = MutableStateFlow(sharedPrefs.getString("hf_model", "facebook/mms-tts-eng") ?: "facebook/mms-tts-eng")
    val hfModel: StateFlow<String> = _hfModel.asStateFlow()

    private val _selectedProvider = MutableStateFlow(sharedPrefs.getString("provider", "Gemini") ?: "Gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _ttsEngine = MutableStateFlow(sharedPrefs.getString("tts_engine", "Cloud HuggingFace TTS") ?: "Cloud HuggingFace TTS")
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    private val _humanizeAudio = MutableStateFlow(sharedPrefs.getBoolean("humanize_audio", true))
    val humanizeAudio: StateFlow<Boolean> = _humanizeAudio.asStateFlow()

    private val _autoOptimize = MutableStateFlow(sharedPrefs.getBoolean("auto_optimize", false))
    val autoOptimize: StateFlow<Boolean> = _autoOptimize.asStateFlow()

    private val _queueConcurrency = MutableStateFlow(sharedPrefs.getInt("queue_concurrency", 2))
    val queueConcurrency: StateFlow<Int> = _queueConcurrency.asStateFlow()

    private val _queueRpm = MutableStateFlow(sharedPrefs.getInt("queue_rpm", 15))
    val queueRpm: StateFlow<Int> = _queueRpm.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "tts_engine" -> {
                val engine = prefs.getString(key, "Cloud HuggingFace TTS") ?: "Cloud HuggingFace TTS"
                if (_ttsEngine.value != engine) {
                    _ttsEngine.value = engine
                }
            }
            "humanize_audio" -> _humanizeAudio.value = prefs.getBoolean(key, true)
            "auto_optimize" -> _autoOptimize.value = prefs.getBoolean(key, false)
            "queue_concurrency" -> {
                val value = prefs.getInt(key, 2)
                _queueConcurrency.value = value
                com.example.data.api.RequestQueueManager.concurrencyLimit = value
            }
            "queue_rpm" -> {
                val value = prefs.getInt(key, 15)
                _queueRpm.value = value
                com.example.data.api.RequestQueueManager.requestsPerMinuteLimit = value
            }
            "gemini_key" -> _geminiApiKey.value = prefs.getString(key, "") ?: ""
            "mistral_key" -> _mistralApiKey.value = prefs.getString(key, "") ?: ""
            "hf_key" -> _hfApiKey.value = prefs.getString(key, "") ?: ""
            "hf_model" -> _hfModel.value = prefs.getString(key, "facebook/mms-tts-eng") ?: "facebook/mms-tts-eng"
            "provider" -> _selectedProvider.value = prefs.getString(key, "Gemini") ?: "Gemini"
        }
    }

    fun setTtsEngine(engine: String) {
        _ttsEngine.value = engine
        updateAvailableVoicesForEngine(engine)
        sharedPrefs.edit().putString("tts_engine", engine).apply()
    }

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "Sepia") ?: "Sepia")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }

    fun setHfApiKey(key: String) {
        _hfApiKey.value = key
        sharedPrefs.edit().putString("hf_key", key).apply()
    }

    fun setHfModel(model: String) {
        _hfModel.value = model
        sharedPrefs.edit().putString("hf_model", model).apply()
    }

    private val _hasSeenOnboarding = MutableStateFlow(sharedPrefs.getBoolean("has_seen_onboarding", false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    private val _developerLogs = MutableStateFlow("Initializing developer console...\n")
    val developerLogs: StateFlow<String> = _developerLogs.asStateFlow()

    val showLogBook = MutableStateFlow(false)

    fun refreshDeveloperLogs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val logcat = fetchLogcat()
            _developerLogs.value = logcat
        }
    }

    fun clearDeveloperLogs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
                _developerLogs.value = "--------- logs cleared ---------\n"
            } catch (e: Exception) {
                _developerLogs.value = "Failed to clear logs: ${e.message}\n"
            }
        }
    }

    fun addDeveloperLog(msg: String) {
        android.util.Log.d("AuthrOS", msg)
        _developerLogs.value += "${msg}\n"
    }

    private fun fetchLogcat(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val lines = java.util.ArrayList<String>()
            var line: String?
            val myPid = android.os.Process.myPid().toString()
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains(myPid) || l.contains("com.example") || l.contains("com.kittenml") || l.contains("Authr") || l.startsWith("---")) {
                    lines.add(l)
                }
            }
            val start = if (lines.size > 300) lines.size - 300 else 0
            val finalLines = lines.subList(start, lines.size)
            if (finalLines.isEmpty()) {
                "No logs captured yet."
            } else {
                finalLines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Failed to read logs: ${e.message}"
        }
    }

    fun importStoryFromTxtUri(context: android.content.Context, uri: android.net.Uri, customTitle: String? = null, onSuccess: (com.example.data.model.Story) -> Unit, onFailure: (Throwable) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                addDeveloperLog("TxtManager: Slicing plain-text manuscript from URI: $uri")
                val importedStory = com.example.data.TxtManager.importTxtFile(
                    context = context,
                    uri = uri,
                    repository = repository,
                    customTitle = customTitle,
                    heroName = heroName.value,
                    genres = selectedGenres.value.joinToString("|"),
                    format = selectedFormat.value,
                    persona = selectedPersona.value
                )
                if (importedStory != null) {
                    addDeveloperLog("TxtManager: Successfully imported and saved story: \"${importedStory.title}\" with ID ${importedStory.id} and ${importedStory.wordCount} words.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onSuccess(importedStory)
                    }
                } else {
                    addDeveloperLog("TxtManager: Failed to import text file from URI.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onFailure(Exception("Failed to read or parse .txt file content"))
                    }
                }
            } catch (e: Exception) {
                addDeveloperLog("TxtManager ERROR: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    fun importStoryFromRawText(title: String, text: String, onSuccess: (com.example.data.model.Story) -> Unit, onFailure: (Throwable) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                addDeveloperLog("TxtManager: Slicing raw text draft into chapters. Title: \"$title\"")
                val importedStory = com.example.data.TxtManager.importRawText(
                    text = text,
                    repository = repository,
                    title = title,
                    heroName = heroName.value,
                    genres = selectedGenres.value.joinToString("|"),
                    format = selectedFormat.value,
                    persona = selectedPersona.value
                )
                if (importedStory != null) {
                    addDeveloperLog("TxtManager: Successfully imported raw text as story: \"${importedStory.title}\" with ID ${importedStory.id} and ${importedStory.wordCount} words.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onSuccess(importedStory)
                    }
                } else {
                    addDeveloperLog("TxtManager: Failed to parse raw text content.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onFailure(Exception("Failed to process raw text"))
                    }
                }
            } catch (e: Exception) {
                addDeveloperLog("TxtManager ERROR: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    fun completeOnboarding() {
        sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
        _hasSeenOnboarding.value = true
    }

    // Story Gen Parameters
    // --- Pronunciation Dictionary based on Biblical & custom names ---
    val pronunciationRules = MutableStateFlow<List<PronunciationRule>>(emptyList())
    private val defaultPronunciationRules = listOf(
        PronunciationRule(original = "Melchizedek", phonetic = "Mel-kiz-uh-dek"),
        PronunciationRule(original = "Jehovah-Jireh", phonetic = "Jeh-ho-vah Ji-rah"),
        PronunciationRule(original = "AriaNeural", phonetic = "Ah-ree-ah Neural"),
        PronunciationRule(original = "Epistle", phonetic = "Eh-pis-ul"),
        PronunciationRule(original = "Nebuchadnezzar", phonetic = "Neb-uh-kuhd-nez-er"),
        PronunciationRule(original = "Deuteronomy", phonetic = "Doo-ter-on-uh-mee"),
        PronunciationRule(original = "Gethsemane", phonetic = "Geth-sem-uh-nee")
    )
    val heroName = MutableStateFlow("Arjun")
    val selectedGenres = MutableStateFlow(setOf("System Power-Up"))
    val selectedPlotArc = MutableStateFlow("Automatic (Recommended)")
    val allowHybridPlotArcs = MutableStateFlow(false)
    val selectedAuthorPersona = MutableStateFlow("None (Weaver Agent Default)")
    val selectedDuration = MutableStateFlow("30 min") // 30, 45, 60, 90 min
    val customHook = MutableStateFlow("")
    val customExtras = MutableStateFlow("")
    val selectedPersona = MutableStateFlow("Standard") // Standard, NightKing, Mastram
    val selectedNarrativeStructure = MutableStateFlow("Hero's Journey")
    val selectedChapterLength = MutableStateFlow("Medium (~3,000 words)")
    val selectedNumberOfChapters = MutableStateFlow(5)
    val selectedWordTarget = MutableStateFlow("Novel (~80,000 words)")
    val selectedFormat = MutableStateFlow("Novel") // Novel, Short Story, Audio Drama, Screenplay, Non-fiction

    // Quick Launch Wizard Parameters
    val quickDraftTitle = MutableStateFlow("")
    val quickDraftGenres = MutableStateFlow(setOf("Fantasy"))
    val quickDraftProtagonist = MutableStateFlow("")
    val quickDraftHook = MutableStateFlow("")
    val quickDraftFormat = MutableStateFlow("Novel")
    val quickDraftWordTarget = MutableStateFlow(50000)
    val quickDraftPersona = MutableStateFlow("standard")
    val quickDraftStructure = MutableStateFlow("Standard 3-Act")
    val quickDraftChapterLength = MutableStateFlow("Medium (~2000 words)")
    val quickDraftAutoExport = MutableStateFlow(true)
    val quickDraftPreviousBookId = MutableStateFlow<Int?>(null)

    suspend fun suggestExtras(genres: String, hook: String): String {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prompt = """
                    You are an expert webnovel editor. Given these genres: ${genres}, and this hook: ${hook},
                    suggest 3 quick 'extras' or constraints that would make the story more unique and engaging.
                    Keep it very brief (e.g., "Dark tone, enemies to lovers, limited magic").
                """.trimIndent()
                com.example.data.api.LlmOrchestrator.callAgent(getApplication(), com.example.data.api.LlmOrchestrator.Agent.EXECUTIVE, prompt)
            } catch (e: Exception) {
                "Error generating extras"
            }
        }
    }
    val quickDraftIsSerialized = MutableStateFlow(false)
    val quickDraftTargetChapters = MutableStateFlow(100)
    val quickDraftReleaseSchedule = MutableStateFlow("Mon/Wed/Fri")
    val quickDraftExtras = MutableStateFlow("")

    // Active Story Progress
    private val _storyParts = MutableStateFlow(List(selectedNumberOfChapters.value) { "" })
    val storyParts: StateFlow<List<String>> = _storyParts.asStateFlow()

    private val _currentPartIndex = MutableStateFlow(0) // 0 to N-1
    val currentPartIndex: StateFlow<Int> = _currentPartIndex.asStateFlow()

    var currentBookIdForEdit: Int? = null
    private val _isGeneratingStory = MutableStateFlow(false)
    val isGeneratingStory: StateFlow<Boolean> = _isGeneratingStory.asStateFlow()

    private val _isOptimizingStory = MutableStateFlow(false)
    val isOptimizingStory: StateFlow<Boolean> = _isOptimizingStory.asStateFlow()

    private val _isOptimizingAudioScript = MutableStateFlow(false)
    val isOptimizingAudioScript: StateFlow<Boolean> = _isOptimizingAudioScript.asStateFlow()

    // Video Generation States
    val videoImages = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val videoImageFocalPoints = MutableStateFlow<Map<android.net.Uri, androidx.compose.ui.geometry.Offset>>(emptyMap())
    val videoImageDuration = MutableStateFlow(5f)
    val videoKenBurnsEnabled = MutableStateFlow(true)
    val videoResolution = MutableStateFlow("360p")
    val videoProductionMode = MutableStateFlow("Short")
    val videoAutoSplit = MutableStateFlow(true)
    val videoAspectRatio = MutableStateFlow("9:16")
    val videoEqualizerEnabled = MutableStateFlow(false)
    val videoEqualizerStyle = MutableStateFlow("Neon Bars")
    val videoExportSrt = MutableStateFlow(true)
    val videoSelectedTrack = MutableStateFlow<com.example.data.model.AudioTrack?>(null)
    val videoGenerationLogs = MutableStateFlow("Ready to generate video...\n")
    val isGeneratingVideo = MutableStateFlow(false)
    val videoGenerationProgress = MutableStateFlow(0f)

    private val _storyGenStatus = MutableStateFlow("Ready to write")
    val storyGenStatus: StateFlow<String> = _storyGenStatus.asStateFlow()

    private val _suggestingField = MutableStateFlow<String?>(null) // "hook" or "extras"
    val suggestingField: StateFlow<String?> = _suggestingField.asStateFlow()

    // Translation Tab
    val transInputText = MutableStateFlow("")
    val transOutputText = MutableStateFlow("")
    val transSelectedLang = MutableStateFlow("Hindi")
    val transUseXmlBoundaries = MutableStateFlow(true)
    val transOutputRaw = MutableStateFlow("")
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    // Metadata Tab
    val metaInputText = MutableStateFlow("")
    val metaTitle = MutableStateFlow("")
    val metaDesc = MutableStateFlow("")
    val metaTags = MutableStateFlow("")
    val metaThumbPrompt = MutableStateFlow("")
    private val _isGeneratingMeta = MutableStateFlow(false)
    val isGeneratingMeta: StateFlow<Boolean> = _isGeneratingMeta.asStateFlow()

    // Cover Art Generation States
    private val _metaGeneratedArtBase64 = MutableStateFlow<String?>(null)
    val metaGeneratedArtBase64: StateFlow<String?> = _metaGeneratedArtBase64.asStateFlow()

    private val _isGeneratingArt = MutableStateFlow(false)
    val isGeneratingArt: StateFlow<Boolean> = _isGeneratingArt.asStateFlow()

    private val _artGenerationError = MutableStateFlow<String?>(null)
    val artGenerationError: StateFlow<String?> = _artGenerationError.asStateFlow()

    // Audio Novel Merging Pipeline States
    private val _isMergingAudio = MutableStateFlow(false)
    val isMergingAudio: StateFlow<Boolean> = _isMergingAudio.asStateFlow()

    private val _mergingError = MutableStateFlow<String?>(null)
    val mergingError: StateFlow<String?> = _mergingError.asStateFlow()

    val selectedAmbientTheme = MutableStateFlow("None")
    val ambientVolume = MutableStateFlow(0.15f)
    val narrationVolume = MutableStateFlow(1.0f)

    // Interactive Wizard Structured Outline States
    private val _wizardOutline = MutableStateFlow("")
    val wizardOutline: StateFlow<String> = _wizardOutline.asStateFlow()
    val isGeneratingWizardOutline = MutableStateFlow(false)

    // Debounced outline update
    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewModelScope.launch(exceptionHandler) {
            _wizardOutline
                .debounce(500L)
                .collect { outline ->
                    // Auto-save logic here
                    saveAutosaveDraft()
                }
        }
        
        viewModelScope.launch(exceptionHandler) {
            selectedNumberOfChapters.collect { count ->
                val currentParts = _storyParts.value
                if (currentParts.size != count) {
                    _storyParts.value = List(count) { index ->
                        currentParts.getOrNull(index) ?: ""
                    }
                    if (_currentPartIndex.value >= count && count > 0) {
                        _currentPartIndex.value = count - 1
                    }
                }
            }
        }
    }

    fun updateOutline(newOutline: String) {
        _wizardOutline.value = newOutline
    }

    fun generateWizardOutline(instructions: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isGeneratingWizardOutline.value = true
            try {
                // Perform generation logic here using Dispatchers.IO
                // ...
            } finally {
                isGeneratingWizardOutline.value = false
            }
        }
    }

    // Audio Conversion Settings
    val audioInputText = MutableStateFlow("")
    val selectedVoiceName = MutableStateFlow<String?>(null)
    val audioRate = MutableStateFlow(1.0f)
    val audioPitch = MutableStateFlow(0f)
    val audioVolume = MutableStateFlow(100f)
    val selectedEmotionPreset = MutableStateFlow("Neutral")
    val customEmotionPresets = MutableStateFlow<List<EmotionPreset>>(emptyList())

    fun loadCustomPresets() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val json = sharedPrefs.getString("custom_presets", "[]") ?: "[]"
            try {
                val list = org.json.JSONArray(json)
                val loaded = mutableListOf<EmotionPreset>()
                for (i in 0 until list.length()) {
                    val obj = list.getJSONObject(i)
                    loaded.add(EmotionPreset(
                        obj.getString("name"),
                        obj.getDouble("speed").toFloat(),
                        obj.getDouble("pitch").toFloat(),
                        obj.getDouble("volume").toFloat()
                    ))
                }
                customEmotionPresets.value = loaded
            } catch(e: Exception) {}
        }
    }

    fun saveCustomPresetsToPrefs() {
        try {
            val array = org.json.JSONArray()
            customEmotionPresets.value.forEach {
                val obj = org.json.JSONObject()
                obj.put("name", it.name)
                obj.put("speed", it.speed.toDouble())
                obj.put("pitch", it.pitch.toDouble())
                obj.put("volume", it.volume.toDouble())
                array.put(obj)
            }
            sharedPrefs.edit().putString("custom_presets", array.toString()).apply()
        } catch(e: Exception) {}
    }

    fun getAvailablePresets(): List<EmotionPreset> = defaultEmotionPresets + customEmotionPresets.value

    fun selectEmotionPreset(name: String) {
        if (name == "Custom") {
            selectedEmotionPreset.value = "Custom"
            return
        }
        val preset = getAvailablePresets().find { it.name == name }
        if (preset != null) {
            audioRate.value = preset.speed
            audioPitch.value = preset.pitch
            audioVolume.value = preset.volume
            selectedEmotionPreset.value = name
        }
    }

    fun onAudioSettingChanged() {
        selectedEmotionPreset.value = "Custom"
    }

    fun saveCustomPreset(name: String) {
        val existing = customEmotionPresets.value.toMutableList()
        existing.removeAll { it.name == name }
        existing.add(EmotionPreset(name, audioRate.value, audioPitch.value, audioVolume.value))
        customEmotionPresets.value = existing
        saveCustomPresetsToPrefs()
        selectedEmotionPreset.value = name
    }

    fun deleteCustomPreset(name: String) {
        customEmotionPresets.value = customEmotionPresets.value.filter { it.name != name }
        saveCustomPresetsToPrefs()
        if (selectedEmotionPreset.value == name) {
            selectEmotionPreset("Neutral")
        }
    }
    
    fun exportCustomPresetsToJson(): String {
        return sharedPrefs.getString("custom_presets", "[]") ?: "[]"
    }
    
    fun importCustomPresetsFromJson(json: String) {
        try {
            val list = org.json.JSONArray(json)
            val loaded = mutableListOf<EmotionPreset>()
            for (i in 0 until list.length()) {
                val obj = list.getJSONObject(i)
                loaded.add(EmotionPreset(
                    obj.getString("name"),
                    obj.getDouble("speed").toFloat(),
                    obj.getDouble("pitch").toFloat(),
                    obj.getDouble("volume").toFloat()
                ))
            }
            customEmotionPresets.value = loaded
            saveCustomPresetsToPrefs()
        } catch(e: Exception) {}
    }

    val splitChapters = MutableStateFlow(true)
    val ttsChunkSize = MutableStateFlow(400)
    val ebuNormalizationEnabled = MutableStateFlow(false)

    // TTS conversion state
    private val _conversionProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val conversionProgress: StateFlow<Float> = _conversionProgress.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _ttsBatchQueue = MutableStateFlow<List<TtsQueueItem>>(emptyList())
    val ttsBatchQueue: StateFlow<List<TtsQueueItem>> = _ttsBatchQueue.asStateFlow()
    private var batchQueueJob: kotlinx.coroutines.Job? = null


    private val _conversionLogs = MutableStateFlow("Conversion logs will appear here...\n")
    val conversionLogs: StateFlow<String> = _conversionLogs.asStateFlow()

    // Book localization state
    private val _isBookTranslating = MutableStateFlow(false)
    val isBookTranslating: StateFlow<Boolean> = _isBookTranslating.asStateFlow()

    private val _bookTranslationProgress = MutableStateFlow(0f)
    val bookTranslationProgress: StateFlow<Float> = _bookTranslationProgress.asStateFlow()

    // --- Audiobook Assembly & Character Voice Modulation Pipeline State & Logic ---
    val characterVoiceConfigs = MutableStateFlow<List<CharacterVoiceConfig>>(emptyList())
    val isExtractingCharacters = MutableStateFlow(false)
    val extractionLogs = MutableStateFlow("")

    val isAssemblingAudiobook = MutableStateFlow(false)
    val assemblyProgress = MutableStateFlow(0f)
    val assemblyLogs = MutableStateFlow("")

    private val _bookTranslationLogs = MutableStateFlow("Localization logs will appear here...\n")
    val bookTranslationLogs: StateFlow<String> = _bookTranslationLogs.asStateFlow()

    // Task and layout isolation variables
    val activeWorkspaceTab = MutableStateFlow(0) // 0: Interactive Wizard, 1: Autonomous Studio
    val isTranslationPaused = MutableStateFlow(false)
    private var translationJob: kotlinx.coroutines.Job? = null
    val draftingStates = MutableStateFlow<Map<Int, String>>(emptyMap()) // bookId -> "Running" / "Paused" / "Stopped"

    val activeBookId = MutableStateFlow<Int?>(null)

    fun selectBook(bookId: Int?) {
        if (bookId == null) {
            activeBookId.value = null
        } else {
            initializeBookContext(bookId)
        }
    }
    private val bookTranslationLogsMap = mutableMapOf<Int, String>()
    private val conversionLogsMap = mutableMapOf<Int, String>()
    private val extractionLogsMap = mutableMapOf<Int, String>()
    private val assemblyLogsMap = mutableMapOf<Int, String>()

    fun setActiveWorkspaceTab(tab: Int) {
        activeWorkspaceTab.value = tab
    }

    fun initializeBookContext(bookId: Int) {
        activeBookId.value = bookId
        
        // State reset protocol: clears/isolates terminal variables by book ID
        val transLogs = bookTranslationLogsMap[bookId] ?: "Localization logs will appear here...\n"
        val convLogs = conversionLogsMap[bookId] ?: "Conversion logs will appear here...\n"
        val extLogs = extractionLogsMap[bookId] ?: ""
        val assLogs = assemblyLogsMap[bookId] ?: ""
        
        _bookTranslationLogs.value = transLogs
        _conversionLogs.value = convLogs
        extractionLogs.value = extLogs
        assemblyLogs.value = assLogs
    }

    fun pauseLocalization() {
        isTranslationPaused.value = true
        _bookTranslationLogs.value += "⏸️ Translation Paused by User.\n"
    }

    fun resumeLocalization() {
        isTranslationPaused.value = false
        _bookTranslationLogs.value += "▶️ Translation Resumed by User.\n"
    }

    fun stopLocalization() {
        translationJob?.cancel()
        translationJob = null
        isTranslationPaused.value = false
        _isBookTranslating.value = false
        _bookTranslationLogs.value += "⏹️ Translation Stopped/Cancelled by User.\n"
    }

    fun clearLocalizationTask() {
        stopLocalization()
        _bookTranslationLogs.value = "Localization logs will appear here...\n"
        _bookTranslationProgress.value = 0f
    }

    fun pauseTtsConversion() {
        _isTtsPaused.value = true
        _conversionLogs.value += "⏸️ TTS Audiobook Synthesis Paused by User.\n"
        pauseSpeaking()
    }

    fun resumeTtsConversion() {
        _isTtsPaused.value = false
        _conversionLogs.value += "▶️ TTS Audiobook Synthesis Resumed by User.\n"
        resumeSpeaking()
    }

    fun clearTtsTask() {
        cancelTtsConversion()
        _isTtsPaused.value = false
        _conversionLogs.value = "Conversion logs will appear here...\n"
        _conversionProgress.value = 0f
    }

    fun setDraftingState(bookId: Int, state: String) {
        val currentMap = draftingStates.value.toMutableMap()
        currentMap[bookId] = state
        draftingStates.value = currentMap
    }

    fun pauseAutonomousDraft(context: Context, bookId: Int) {
        setDraftingState(bookId, "Paused")
        cancelDrafting(context, bookId)
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val book = repository.getBookById(bookId)
            if (book != null) {
                repository.updateBook(book.copy(currentPhase = "Paused"))
                com.example.worker.logAgentChat(repository.bookDao, bookId, "⏸️ [System]", "Autonomous drafting paused by user.")
            }
        }
        android.widget.Toast.makeText(context, "Paused Drafting for Book $bookId", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun stopAutonomousDraft(context: Context, bookId: Int) {
        setDraftingState(bookId, "Stopped")
        cancelDrafting(context, bookId)
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val book = repository.getBookById(bookId)
            if (book != null) {
                repository.updateBook(book.copy(currentPhase = "Stopped"))
                com.example.worker.logAgentChat(repository.bookDao, bookId, "⏹️ [System]", "Autonomous drafting stopped by user.")
            }
        }
        android.widget.Toast.makeText(context, "Stopped Drafting for Book $bookId", android.widget.Toast.LENGTH_SHORT).show()
    }

    val conversionElapsedSeconds = MutableStateFlow(0L)
    val conversionEtaSeconds = MutableStateFlow(-1L)

    private var conversionJob: kotlinx.coroutines.Job? = null

    // TTS Engine
    var ttsManager: AndroidTtsManager? = null
        private set

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    val isVoxSherpaActive = MutableStateFlow(false)

    val availableVoices = MutableStateFlow<List<VoiceItem>>(emptyList())

    val selectedGenderFilter = MutableStateFlow("All")
    val selectedResonanceFilter = MutableStateFlow("All")

    val filteredVoices = combine(
        availableVoices,
        selectedGenderFilter,
        selectedResonanceFilter
    ) { voices, gender, resonance ->
        voices.filter { voice ->
            val matchesGender = when (gender) {
                "All" -> true
                else -> voice.gender.equals(gender, ignoreCase = true)
            }
            val matchesResonance = when (resonance) {
                "All" -> true
                else -> voice.emotionalResonance.equals(resonance, ignoreCase = true)
            }
            matchesGender && matchesResonance
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getSlugifiedNameFromHookText(hookText: String): String {
        val trimmed = hookText.trim()
        if (trimmed.isEmpty()) {
            return "untitled-novel"
        }
        val firstSentence = trimmed.split(Regex("[.!?\n]"))
            .firstOrNull { it.trim().isNotEmpty() }?.trim() ?: trimmed
        
        val slug = firstSentence.lowercase(java.util.Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("[\\s-]+"), "-")
            
        return if (slug.isEmpty()) "untitled-novel" else slug
    }

    fun getDefaultFilenameFromHook(extension: String): String {
        val hookText = customHook.value
        val slug = getSlugifiedNameFromHookText(hookText)
        return "$slug.$extension"
    }

    // Sequential TTS Playback States
    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying.asStateFlow()

    private val _isTtsPaused = MutableStateFlow(false)
    val isTtsPaused: StateFlow<Boolean> = _isTtsPaused.asStateFlow()

    private val _currentTtsParagraphIndex = MutableStateFlow(-1)
    val currentTtsParagraphIndex: StateFlow<Int> = _currentTtsParagraphIndex.asStateFlow()

    private val _ttsParagraphs = MutableStateFlow<List<String>>(emptyList())
    val ttsParagraphs: StateFlow<List<String>> = _ttsParagraphs.asStateFlow()

    private var playbackJob: kotlinx.coroutines.Job? = null

    // --- Autosave System ---
    private val autosaveManager = com.example.managers.AutosaveManager(application)

    private val _isDraftAutosaved = MutableStateFlow(true)
    val isDraftAutosaved: StateFlow<Boolean> = _isDraftAutosaved.asStateFlow()

    private val _lastAutosaveTime = MutableStateFlow<String?>(null)
    val lastAutosaveTime: StateFlow<String?> = _lastAutosaveTime.asStateFlow()

    private fun getCurrentTimeStr(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun loadAutosaveDraft() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + exceptionHandler) {
            val data = autosaveManager.loadDraft()
            if (data != null) {
                heroName.value = data.heroName
                selectedGenres.value = data.genres.split("|").filter { it.isNotEmpty() }.toSet()
                selectedPlotArc.value = data.plotArc
                allowHybridPlotArcs.value = data.allowHybridPlotArcs
                selectedAuthorPersona.value = data.authorPersona
                selectedDuration.value = data.duration
                customHook.value = data.hook
                customExtras.value = data.extras
                selectedPersona.value = data.persona
                _wizardOutline.value = data.wizardOutline

                quickDraftTitle.value = data.quickTitle
                quickDraftGenres.value = data.quickGenres.split("|").filter { it.isNotEmpty() }.toSet()
                quickDraftProtagonist.value = data.quickProtagonist
                quickDraftHook.value = data.quickHook
                quickDraftFormat.value = data.quickFormat
                quickDraftWordTarget.value = data.quickWordTarget
                quickDraftPersona.value = data.quickPersona
                quickDraftStructure.value = data.quickStructure
                quickDraftChapterLength.value = data.quickChapterLength
                quickDraftAutoExport.value = data.quickAutoExport
                quickDraftPreviousBookId.value = if (data.quickPrevBookId == -1) null else data.quickPrevBookId
                quickDraftExtras.value = data.quickExtras
                quickDraftIsSerialized.value = data.quickIsSerialized
                quickDraftTargetChapters.value = data.quickTargetChapters
                quickDraftReleaseSchedule.value = data.quickReleaseSchedule

                _storyParts.value = data.parts.toMutableList()
                selectedNumberOfChapters.value = data.parts.size
                _lastAutosaveTime.value = data.lastSaveTime
                _isDraftAutosaved.value = true
                Log.d("StudioViewModel", "Autosave draft loaded successfully from DataStore!")
            }
        }
    }

    fun clearActiveWorkspace() {
        heroName.value = ""
        selectedGenres.value = emptySet()
        customHook.value = ""
        customExtras.value = ""
        _wizardOutline.value = ""
        selectedNumberOfChapters.value = 5
        _storyParts.value = List(5) { "" }
        _isDraftAutosaved.value = false
        _lastAutosaveTime.value = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + exceptionHandler) {
            autosaveManager.clearDraft()
        }
    }

    private fun saveAutosaveDraft() {
        val timeStr = getCurrentTimeStr()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + exceptionHandler) {
            autosaveManager.saveDraft(
                heroName = heroName.value,
                genres = selectedGenres.value.joinToString("|"),
                plotArc = selectedPlotArc.value,
                allowHybridPlotArcs = allowHybridPlotArcs.value,
                authorPersona = selectedAuthorPersona.value,
                duration = selectedDuration.value,
                hook = customHook.value,
                extras = customExtras.value,
                persona = selectedPersona.value,
                wizardOutline = wizardOutline.value,
                quickTitle = quickDraftTitle.value,
                quickGenres = quickDraftGenres.value.joinToString("|"),
                quickProtagonist = quickDraftProtagonist.value,
                quickHook = quickDraftHook.value,
                quickFormat = quickDraftFormat.value,
                quickWordTarget = quickDraftWordTarget.value,
                quickPersona = quickDraftPersona.value,
                quickStructure = quickDraftStructure.value,
                quickChapterLength = quickDraftChapterLength.value,
                quickAutoExport = quickDraftAutoExport.value,
                quickPrevBookId = quickDraftPreviousBookId.value ?: -1,
                quickExtras = quickDraftExtras.value,
                quickIsSerialized = quickDraftIsSerialized.value,
                quickTargetChapters = quickDraftTargetChapters.value,
                quickReleaseSchedule = quickDraftReleaseSchedule.value,
                parts = _storyParts.value,
                timeStr = timeStr
            )
            _isDraftAutosaved.value = true
            _lastAutosaveTime.value = timeStr
            Log.d("StudioViewModel", "Autosave draft saved successfully to DataStore at $timeStr")
        }
    }

    private val kittenVoices = listOf(
        com.example.tts.VoiceItem("expr-voice-2-f", java.util.Locale.US, "Female", "Kitten Expressive (Bella)", null, "Narrative"),
        com.example.tts.VoiceItem("expr-voice-2-m", java.util.Locale.US, "Male", "Kitten Expressive (Jasper)", null, "Narrative"),
        com.example.tts.VoiceItem("expr-voice-3-f", java.util.Locale.US, "Female", "Kitten Expressive (Luna)", null, "Soothing"),
        com.example.tts.VoiceItem("expr-voice-3-m", java.util.Locale.US, "Male", "Kitten Expressive (Bruno)", null, "Dramatic"),
        com.example.tts.VoiceItem("expr-voice-4-f", java.util.Locale.US, "Female", "Kitten Expressive (Rosie)", null, "Narrative"),
        com.example.tts.VoiceItem("expr-voice-4-m", java.util.Locale.US, "Male", "Kitten Expressive (Hugo)", null, "Dramatic"),
        com.example.tts.VoiceItem("expr-voice-5-f", java.util.Locale.US, "Female", "Kitten Expressive (Kiki)", null, "Soothing"),
        com.example.tts.VoiceItem("expr-voice-5-m", java.util.Locale.US, "Male", "Kitten Expressive (Leo)", null, "Narrative")
    )

    private fun updateAvailableVoicesForEngine(engine: String) {
        when (engine) {
            "Kitten TTS" -> {
                availableVoices.value = kittenVoices
                if (selectedVoiceName.value == null || !kittenVoices.any { it.name == selectedVoiceName.value }) {
                    selectedVoiceName.value = "expr-voice-5-m"
                }
            }
            "Android" -> {
                availableVoices.value = ttsManager?.getAvailableVoices() ?: emptyList()
                if (selectedVoiceName.value == null || !availableVoices.value.any { it.name == selectedVoiceName.value }) {
                    val defaultVoice = availableVoices.value.find { it.name.contains("en-us-x-tpd", ignoreCase = true) } ?: availableVoices.value.firstOrNull()
                    selectedVoiceName.value = defaultVoice?.name
                }
            }
            "Edge TTS" -> {
                availableVoices.value = listOf(
                    com.example.tts.VoiceItem("en-US-AnaNeural", java.util.Locale.US, "Female", "Neural", null, "Cute", "en-US-AnaNeural (Female, Cute)"),
                    com.example.tts.VoiceItem("en-US-AndrewMultilingualNeural", java.util.Locale.US, "Male", "Neural", null, "Warm, Confident", "en-US-AndrewMultilingualNeural (Multilingual)"),
                    com.example.tts.VoiceItem("en-US-AndrewNeural", java.util.Locale.US, "Male", "Neural", null, "Warm, Confident", "en-US-AndrewNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-AriaNeural", java.util.Locale.US, "Female", "Neural", null, "Positive, Confident", "en-US-AriaNeural (Female)"),
                    com.example.tts.VoiceItem("en-US-AvaMultilingualNeural", java.util.Locale.US, "Female", "Neural", null, "Expressive, Caring", "en-US-AvaMultilingualNeural (Multilingual)"),
                    com.example.tts.VoiceItem("en-US-AvaNeural", java.util.Locale.US, "Female", "Neural", null, "Expressive, Caring", "en-US-AvaNeural (Female)"),
                    com.example.tts.VoiceItem("en-US-BrianMultilingualNeural", java.util.Locale.US, "Male", "Neural", null, "Approachable, Casual", "en-US-BrianMultilingualNeural (Multilingual)"),
                    com.example.tts.VoiceItem("en-US-BrianNeural", java.util.Locale.US, "Male", "Neural", null, "Approachable, Casual", "en-US-BrianNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-ChristopherNeural", java.util.Locale.US, "Male", "Neural", null, "Reliable, Authority", "en-US-ChristopherNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-EmmaMultilingualNeural", java.util.Locale.US, "Female", "Neural", null, "Cheerful, Clear", "en-US-EmmaMultilingualNeural (Multilingual)"),
                    com.example.tts.VoiceItem("en-US-EmmaNeural", java.util.Locale.US, "Female", "Neural", null, "Cheerful, Clear", "en-US-EmmaNeural (Female)"),
                    com.example.tts.VoiceItem("en-US-EricNeural", java.util.Locale.US, "Male", "Neural", null, "Rational", "en-US-EricNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-GuyNeural", java.util.Locale.US, "Male", "Neural", null, "Passion", "en-US-GuyNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-JennyNeural", java.util.Locale.US, "Female", "Neural", null, "Friendly, Considerate", "en-US-JennyNeural (Female)"),
                    com.example.tts.VoiceItem("en-US-MichelleNeural", java.util.Locale.US, "Female", "Neural", null, "Friendly, Pleasant", "en-US-MichelleNeural (Female)"),
                    com.example.tts.VoiceItem("en-US-RogerNeural", java.util.Locale.US, "Male", "Neural", null, "Lively", "en-US-RogerNeural (Male)"),
                    com.example.tts.VoiceItem("en-US-SteffanNeural", java.util.Locale.US, "Male", "Neural", null, "Rational", "en-US-SteffanNeural (Male)")
                )
                if (selectedVoiceName.value == null || !availableVoices.value.any { it.name == selectedVoiceName.value }) {
                    selectedVoiceName.value = "en-US-AndrewMultilingualNeural"
                }
            }
            else -> { // Cloud (HF)
                availableVoices.value = emptyList()
            }
        }
    }

    // Rate Limit Queue UI state exposed from RequestQueueManager
    val queueLength = com.example.data.api.RequestQueueManager.queueLength
    val runningRequests = com.example.data.api.RequestQueueManager.runningRequests
    val waitingRequests = com.example.data.api.RequestQueueManager.waitingRequests
    val failedRequests = com.example.data.api.RequestQueueManager.failedRequests
    val retryCountdown = com.example.data.api.RequestQueueManager.retryCountdown
    val averageRequestDurationMs = com.example.data.api.RequestQueueManager.averageRequestDurationMs

    // Queue Configs (handled at top)

    fun setQueueConfig(concurrency: Int, rpm: Int) {
        _queueConcurrency.value = concurrency
        _queueRpm.value = rpm
        sharedPrefs.edit().putInt("queue_concurrency", concurrency).putInt("queue_rpm", rpm).apply()
        com.example.data.api.RequestQueueManager.concurrencyLimit = concurrency
        com.example.data.api.RequestQueueManager.requestsPerMinuteLimit = rpm
    }

    // Storage/Cleanup configurations
    val cleanupEnabled = MutableStateFlow(sharedPrefs.getBoolean("cleanup_enabled", false))
    val cleanupFrequency = MutableStateFlow(sharedPrefs.getString("cleanup_frequency", "Weekly") ?: "Weekly")
    val deleteTempCache = MutableStateFlow(sharedPrefs.getBoolean("delete_temp_cache", true))
    val deleteFailedArtifacts = MutableStateFlow(sharedPrefs.getBoolean("delete_failed_artifacts", true))
    val deleteExpiredApi = MutableStateFlow(sharedPrefs.getBoolean("delete_expired_api", true))
    val deleteUnusedCache = MutableStateFlow(sharedPrefs.getBoolean("delete_unused_cache", true))
    val maxCacheSizeMb = MutableStateFlow(sharedPrefs.getInt("max_cache_size_mb", 1024))
    val maxProjectsCount = MutableStateFlow(sharedPrefs.getInt("max_projects_count", 50))

    // Storage sizes
    val databaseSize = MutableStateFlow(0L)
    val cacheSize = MutableStateFlow(0L)
    val audioCacheSize = MutableStateFlow(0L)
    val imageCacheSize = MutableStateFlow(0L)
    val totalStorageUsed = MutableStateFlow(0L)

    fun setCleanupEnabled(context: Context, enabled: Boolean) {
        cleanupEnabled.value = enabled
        sharedPrefs.edit().putBoolean("cleanup_enabled", enabled).apply()
        updateCleanupSchedule(context)
    }

    fun setCleanupFrequency(context: Context, freq: String) {
        cleanupFrequency.value = freq
        sharedPrefs.edit().putString("cleanup_frequency", freq).apply()
        updateCleanupSchedule(context)
    }

    fun setDeleteTempCache(enabled: Boolean) {
        deleteTempCache.value = enabled
        sharedPrefs.edit().putBoolean("delete_temp_cache", enabled).apply()
    }

    fun setDeleteFailedArtifacts(enabled: Boolean) {
        deleteFailedArtifacts.value = enabled
        sharedPrefs.edit().putBoolean("delete_failed_artifacts", enabled).apply()
    }

    fun setDeleteExpiredApi(enabled: Boolean) {
        deleteExpiredApi.value = enabled
        sharedPrefs.edit().putBoolean("delete_expired_api", enabled).apply()
    }

    fun setDeleteUnusedCache(enabled: Boolean) {
        deleteUnusedCache.value = enabled
        sharedPrefs.edit().putBoolean("delete_unused_cache", enabled).apply()
    }

    fun setMaxCacheSizeMb(size: Int) {
        maxCacheSizeMb.value = size
        sharedPrefs.edit().putInt("max_cache_size_mb", size).apply()
    }

    fun setMaxProjectsCount(count: Int) {
        maxProjectsCount.value = count
        sharedPrefs.edit().putInt("max_projects_count", count).apply()
    }

    fun refreshStorageUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val dbFile = context.getDatabasePath("audio_novel_studio_db")
            val dbSizeVal = if (dbFile.exists()) dbFile.length() else 0L
            val dbWalFile = context.getDatabasePath("audio_novel_studio_db-wal")
            val dbWalSize = if (dbWalFile.exists()) dbWalFile.length() else 0L
            val dbShmFile = context.getDatabasePath("audio_novel_studio_db-shm")
            val dbShmSize = if (dbShmFile.exists()) dbShmFile.length() else 0L
            val dbTotal = dbSizeVal + dbWalSize + dbShmSize

            val cacheDir = context.cacheDir
            var totalCache = 0L
            var audioCache = 0L
            var imageCache = 0L

            if (cacheDir != null && cacheDir.exists()) {
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        val len = file.length()
                        totalCache += len
                        if (file.name.contains("tts_chunk") || file.name.endsWith(".wav") || file.name.endsWith(".mp3")) {
                            audioCache += len
                        } else if (file.name.contains("temp_vid_img") || file.name.contains("part_image") || file.name.endsWith(".jpg") || file.name.endsWith(".png")) {
                            imageCache += len
                        }
                    }
                }
            }

            databaseSize.value = dbTotal
            cacheSize.value = totalCache
            audioCacheSize.value = audioCache
            imageCacheSize.value = imageCache
            totalStorageUsed.value = dbTotal + totalCache
        }
    }

    fun cleanNow(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val cleanRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.StorageCleanupWorker>()
                .addTag("storage_cleanup_manual")
                .build()
            workManager.enqueue(cleanRequest)
            delay(1500)
            refreshStorageUsage()
        }
    }

    fun updateCleanupSchedule(context: Context) {
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val enabled = cleanupEnabled.value
            val frequency = cleanupFrequency.value

            workManager.cancelAllWorkByTag("storage_cleanup")

            if (enabled) {
                val repeatInterval = when (frequency) {
                    "Daily" -> 1L
                    "Weekly" -> 7L
                    "Monthly" -> 30L
                    else -> 7L
                }
                val cleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.worker.StorageCleanupWorker>(
                    repeatInterval, java.util.concurrent.TimeUnit.DAYS
                )
                    .addTag("storage_cleanup")
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    "storage_cleanup_work",
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    cleanupRequest
                )
                Log.d("StudioViewModel", "Scheduled storage cleanup: $frequency")
            } else {
                Log.d("StudioViewModel", "Automatic storage cleanup disabled.")
            }
        } catch (e: Throwable) {
            Log.e("StudioViewModel", "Failed to schedule storage cleanup", e)
        }
    }

    init {
        if (!skipInitForTesting) {
            try {
                com.example.data.api.RequestQueueManager.initialize(application)
                com.example.data.api.RequestQueueManager.concurrencyLimit = _queueConcurrency.value
                com.example.data.api.RequestQueueManager.requestsPerMinuteLimit = _queueRpm.value

                refreshStorageUsage()
                updateCleanupSchedule(application)

                viewModelScope.launch(exceptionHandler) {
                    try {
                        com.example.tts.LocalKittenTtsManager.initialize(application)
                    } catch (e: Throwable) {
                        Log.e("StudioViewModel", "Failed to initialize LocalKittenTtsManager", e)
                    }
                }
                loadCustomPresets()
                loadPronunciationRules()
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Error in StudioViewModel init", e)
            }

            viewModelScope.launch(exceptionHandler) {
                _bookTranslationLogs.collect { logs ->
                    activeBookId.value?.let { id ->
                        bookTranslationLogsMap[id] = logs
                    }
                }
            }
            viewModelScope.launch(exceptionHandler) {
                _conversionLogs.collect { logs ->
                    activeBookId.value?.let { id ->
                        conversionLogsMap[id] = logs
                    }
                }
            }
            viewModelScope.launch(exceptionHandler) {
                extractionLogs.collect { logs ->
                    activeBookId.value?.let { id ->
                        extractionLogsMap[id] = logs
                    }
                }
            }
            viewModelScope.launch(exceptionHandler) {
                assemblyLogs.collect { logs ->
                    activeBookId.value?.let { id ->
                        assemblyLogsMap[id] = logs
                    }
                }
            }
        }

        // Initialize standard API keys from BuildConfig if blank
        val buildGeminiKey = BuildConfig.GEMINI_API_KEY
        if (_geminiApiKey.value.isEmpty() && buildGeminiKey.isNotEmpty() && buildGeminiKey != "MY_GEMINI_API_KEY") {
            _geminiApiKey.value = buildGeminiKey
        }

        val buildHfKey = BuildConfig.HF_API_KEY
        if (_hfApiKey.value.isEmpty() && buildHfKey.isNotEmpty() && buildHfKey != "MY_HF_API_KEY") {
            _hfApiKey.value = buildHfKey
        }
        val buildOllamaKey = BuildConfig.OLLAMA_API_KEY
        if (_ollamaApiKey.value.isEmpty() && buildOllamaKey.isNotEmpty() && buildOllamaKey != "MY_OLLAMA_API_KEY") {
            _ollamaApiKey.value = buildOllamaKey
        }

        // Initialize Android TTS
        if (!skipInitForTesting) {
            ttsManager = AndroidTtsManager(application) { success ->
                _ttsReady.value = success
                if (success) {
                    isVoxSherpaActive.value = ttsManager?.isVoxSherpaActive == true
                    updateAvailableVoicesForEngine(_ttsEngine.value)
                }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            _ttsEngine.collect { engine ->
                updateAvailableVoicesForEngine(engine)
            }
        }

        // Load existing autosave draft if present
        loadAutosaveDraft()

        // Track changes to trigger dirty state
        viewModelScope.launch(exceptionHandler) {
            combine(
                listOf(
                    heroName,
                    selectedGenres,
                    selectedPlotArc,
                    allowHybridPlotArcs,
                    selectedAuthorPersona,
                    selectedDuration,
                    customHook,
                    customExtras,
                    selectedPersona,
                    wizardOutline,
                    _storyParts,
                    quickDraftTitle,
                    quickDraftGenres,
                    quickDraftProtagonist,
                    quickDraftHook,
                    quickDraftFormat,
                    quickDraftWordTarget,
                    quickDraftPersona,
                    quickDraftStructure,
                    quickDraftChapterLength,
                    quickDraftAutoExport,
                    quickDraftPreviousBookId
                )
            ) { }.collect {
                // Ignore the very first emissions right after loading to prevent immediate dirty marking
                delay(100)
                _isDraftAutosaved.value = false
            }
        }

        // Launch periodic auto-save loop
        viewModelScope.launch(Dispatchers.IO) {
            var lastHeroName = heroName.value
            var lastGenres = selectedGenres.value
            var lastDuration = selectedDuration.value
            var lastHook = customHook.value
            var lastExtras = customExtras.value
            var lastPersona = selectedPersona.value
            var lastOutline = wizardOutline.value
            var lastParts = _storyParts.value

            var lastQuickTitle = quickDraftTitle.value
            var lastQuickGenres = quickDraftGenres.value
            var lastQuickProtagonist = quickDraftProtagonist.value
            var lastQuickHook = quickDraftHook.value
            var lastQuickWordTarget = quickDraftWordTarget.value
            var lastQuickPrevBookId = quickDraftPreviousBookId.value
            var lastQuickExtras = quickDraftExtras.value
            var lastQuickIsSerialized = quickDraftIsSerialized.value
            var lastQuickTargetChapters = quickDraftTargetChapters.value
            var lastQuickReleaseSchedule = quickDraftReleaseSchedule.value

            while (true) {
                delay(10000) // check every 10 seconds
                val currentHeroName = heroName.value
                val currentGenres = selectedGenres.value
                val currentDuration = selectedDuration.value
                val currentHook = customHook.value
                val currentExtras = customExtras.value
                val currentPersona = selectedPersona.value
                val currentOutline = wizardOutline.value
                val currentParts = _storyParts.value

                val currentQuickTitle = quickDraftTitle.value
                val currentQuickGenres = quickDraftGenres.value
                val currentQuickProtagonist = quickDraftProtagonist.value
                val currentQuickHook = quickDraftHook.value
                val currentQuickWordTarget = quickDraftWordTarget.value
                val currentQuickPrevBookId = quickDraftPreviousBookId.value
                val currentQuickExtras = quickDraftExtras.value
                val currentQuickIsSerialized = quickDraftIsSerialized.value
                val currentQuickTargetChapters = quickDraftTargetChapters.value
                val currentQuickReleaseSchedule = quickDraftReleaseSchedule.value

                if (currentHeroName != lastHeroName ||
                    currentGenres != lastGenres ||
                    currentDuration != lastDuration ||
                    currentHook != lastHook ||
                    currentExtras != lastExtras ||
                    currentPersona != lastPersona ||
                    currentOutline != lastOutline ||
                    currentParts != lastParts ||
                    currentQuickTitle != lastQuickTitle ||
                    currentQuickGenres != lastQuickGenres ||
                    currentQuickProtagonist != lastQuickProtagonist ||
                    currentQuickHook != lastQuickHook ||
                    currentQuickWordTarget != lastQuickWordTarget ||
                    currentQuickPrevBookId != lastQuickPrevBookId ||
                    currentQuickExtras != lastQuickExtras ||
                    currentQuickIsSerialized != lastQuickIsSerialized ||
                    currentQuickTargetChapters != lastQuickTargetChapters ||
                    currentQuickReleaseSchedule != lastQuickReleaseSchedule
                ) {
                    saveAutosaveDraft()
                    lastHeroName = currentHeroName
                    lastGenres = currentGenres
                    lastDuration = currentDuration
                    lastHook = currentHook
                    lastExtras = currentExtras
                    lastPersona = currentPersona
                    lastOutline = currentOutline
                    lastParts = currentParts
                    
                    lastQuickTitle = currentQuickTitle
                    lastQuickGenres = currentQuickGenres
                    lastQuickProtagonist = currentQuickProtagonist
                    lastQuickHook = currentQuickHook
                    lastQuickWordTarget = currentQuickWordTarget
                    lastQuickPrevBookId = currentQuickPrevBookId
                    lastQuickExtras = currentQuickExtras
                    lastQuickIsSerialized = currentQuickIsSerialized
                    lastQuickTargetChapters = currentQuickTargetChapters
                    lastQuickReleaseSchedule = currentQuickReleaseSchedule
                }
            }
        }
    }

    // --- Tab Control ---
    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun importWizardScriptToAudioStudio() {
        val combinedText = _storyParts.value.filter { it.isNotBlank() }.joinToString("\n\n")
        if (combinedText.isNotBlank()) {
            audioInputText.value = combinedText
            selectTab(1) // Go to Audio tab
        }
    }

    // --- Settings Persistence ---


    private fun getActiveApiKey(): String {
        return when (_selectedProvider.value) {
            "Gemini" -> _geminiApiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
            "Mistral" -> _mistralApiKey.value
            "Groq" -> _groqApiKey.value
            "NVIDIA" -> _nvidiaApiKey.value
            "Custom OpenAI" -> _customOpenAiApiKey.value
            else -> _ollamaApiKey.value.ifEmpty { BuildConfig.OLLAMA_API_KEY }
        }
    }

    // --- API Request execution helper with retries ---
    private suspend fun callAI(prompt: String, systemInstruction: String? = null, temperature: Float? = null): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        val provider = _selectedProvider.value
        if (provider != "Ollama" && (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY")) {
            throw Exception("API Key for $provider is missing! Please configure it in the Settings tab.")
        }

        var attempt = 0
        val maxAttempts = 3
        var backoffMs = 2000L

        while (attempt < maxAttempts) {
            try {
                if (provider == "Gemini") {
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = systemInstruction?.let { GeminiContent(role = "system", parts = listOf(GeminiPart(text = it))) },
                        generationConfig = temperature?.let { GeminiGenerationConfig(temperature = it) }
                    )
                    val response = ApiClient.geminiService.generateContent("gemini-1.5-flash", apiKey, request)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (text != null) return@withContext text
                } else if (provider == "Mistral") {
                    val request = MistralRequest(
                        messages = listOf(
                            MistralMessage(role = "system", content = systemInstruction ?: "You are a helpful creative assistant."),
                            MistralMessage(role = "user", content = prompt)
                        ),
                        temperature = temperature
                    )
                    val response = ApiClient.mistralService.generateContent("Bearer $apiKey", request)
                    val text = response.choices?.firstOrNull()?.message?.content
                    if (text != null) return@withContext text
                } else if (provider == "Groq") {
                    val request = com.example.data.api.OpenAiChatRequest(
                        model = _groqModel.value,
                        messages = listOf(
                            com.example.data.api.OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are a helpful creative assistant."),
                            com.example.data.api.OpenAiChatMessage(role = "user", content = prompt)
                        ),
                        temperature = temperature,
                        top_p = 0.95f
                    )
                    val response = ApiClient.openAiCompatibleService.generateContent("https://api.groq.com/openai/v1/chat/completions", "Bearer $apiKey", request)
                    val text = response.choices?.firstOrNull()?.message?.content
                    if (text != null) return@withContext text
                } else if (provider == "NVIDIA") {
                    val request = com.example.data.api.OpenAiChatRequest(
                        model = _nvidiaModel.value,
                        messages = listOf(
                            com.example.data.api.OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are a helpful creative assistant."),
                            com.example.data.api.OpenAiChatMessage(role = "user", content = prompt)
                        ),
                        temperature = temperature ?: 0.6f,
                        top_p = 0.7f,
                        max_tokens = 4096
                    )
                    val response = ApiClient.openAiCompatibleService.generateContent("https://integrate.api.nvidia.com/v1/chat/completions", "Bearer $apiKey", request)
                    val text = response.choices?.firstOrNull()?.message?.content
                    if (text != null) return@withContext text
                } else if (provider == "Custom OpenAI") {
                    val baseUrl = _customOpenAiBaseUrl.value
                    val finalUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
                    val request = com.example.data.api.OpenAiChatRequest(
                        model = _customOpenAiModel.value,
                        messages = listOf(
                            com.example.data.api.OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are a helpful creative assistant."),
                            com.example.data.api.OpenAiChatMessage(role = "user", content = prompt)
                        ),
                        temperature = temperature
                    )
                    val response = ApiClient.openAiCompatibleService.generateContent(finalUrl, "Bearer $apiKey", request)
                    val text = response.choices?.firstOrNull()?.message?.content
                    if (text != null) return@withContext text
                } else {
                    val request = com.example.data.api.OllamaRequest(
                        model = _ollamaModel.value,
                        messages = listOf(
                            com.example.data.api.OllamaMessage(role = "system", content = systemInstruction ?: "You are a helpful creative assistant."),
                            com.example.data.api.OllamaMessage(role = "user", content = prompt)
                        ),
                        options = temperature?.let { com.example.data.api.OllamaOptions(temperature = it) }
                    )
                    val baseUrl = _ollamaBaseUrl.value
                    val finalOllamaUrl = if (baseUrl.endsWith("/")) "${baseUrl}api/chat" else "$baseUrl/api/chat"
                    val response = ApiClient.ollamaService.generateContent(finalOllamaUrl, "Bearer $apiKey", request)
                    val text = response.message?.content
                    if (text != null) return@withContext text
                }
                throw Exception("Received empty response from provider.")
            } catch (e: Throwable) {
                attempt++
                if (attempt >= maxAttempts) throw e
                Log.w("StudioViewModel", "API call failed (attempt $attempt), retrying in ${backoffMs}ms...", e)
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        throw Exception("API call failed after max retry attempts.")
    }

    // --- Suggest Hook ---
    fun suggestHook() {
        if (_suggestingField.value != null) return
        _suggestingField.value = "hook"
        viewModelScope.launch(exceptionHandler) {
            try {
                val genres = selectedGenres.value.joinToString(", ")
                val prompt = """
                    Suggest an engaging, high-tension opening hook for an audio novel.
                    Protagonist: ${heroName.value}, Genres: $genres.
                    The opening hook MUST deliver the shocking situation immediately with no build-up.
                    Example: "My wife told everyone I was useless — then her father's boss bowed to me."
                    Keep it to 1-2 compelling first-person sentences that grab attention immediately. Do NOT surround with any quotes, intro, or meta-talk.
                """.trimIndent()
                val suggestion = callAI(prompt)
                customHook.value = suggestion.trim().removeSurrounding("\"")
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Failed to suggest hook", e)
            } finally {
                _suggestingField.value = null
            }
        }
    }

    // --- Suggest Extras ---
    fun suggestExtras() {
        if (_suggestingField.value != null) return
        _suggestingField.value = "extras"
        viewModelScope.launch(exceptionHandler) {
            try {
                val genres = selectedGenres.value.joinToString(", ")
                val prompt = "Suggest a unique narrative element, background setting extra, or twist for a story with protagonist ${heroName.value} and genres: $genres. Keep it to 1-2 sentences. Do NOT include meta-commentary."
                val suggestion = callAI(prompt)
                customExtras.value = suggestion.trim().removeSurrounding("\"")
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Failed to suggest extras", e)
            } finally {
                _suggestingField.value = null
            }
        }
    }

    // --- Structured Outline Generator for Interactive Wizard ---
    fun generateWizardOutline(premise: String, format: String, genre: String, protagonist: String, structure: String) {
        if (isGeneratingWizardOutline.value) return
        isGeneratingWizardOutline.value = true
        
        val chapterCount = selectedNumberOfChapters.value
        
        viewModelScope.launch(exceptionHandler) {
            try {
                val prompt = """
                    Create a detailed, highly structured narrative outline for a premium audio novel / digital audiobook.
                    
                    Premise/Theme: "${if (premise.isBlank()) "A dramatic tale of action, suspense, and unexpected twists." else premise}"
                    Format/Medium: $format
                    Genre(s): $genre
                    Protagonist Name: "$protagonist"
                    Narrative Structure: $structure
                    Total Number of Chapters: $chapterCount
                    
                    MANDATORY OUTLINE REQUIREMENTS:
                    1. CHARACTER ARCS:
                       - Detailed character arc for the main protagonist ("$protagonist"), describing their emotional transformation from the beginning, middle, to climax.
                       - Brief arcs or roles for at least 1-2 major supporting characters.
                    2. PLOT POINTS & CHAPTER TITLES:
                       - Generate exactly $chapterCount comprehensive chapters. You must DECIDE how to logically map these $chapterCount chapters across the specific milestones of the "$structure" narrative structure. Some structure parts may span multiple chapters.
                       - For each chapter, explicitly state which part of the "$structure" it represents.
                       - For each chapter, provide:
                         a) A compelling, dramatic Chapter Title (e.g., "Chapter 1: The Broken Crown").
                         b) At least 3 detailed sequential Plot Points describing specific actions, sensory details, and critical interactions.
                         c) Emotional Beats/TTS Pacing guidelines (e.g., "Start with heavy, hesitant pacing, building to a rapid, defensive exchange").
                    3. VOICE & SOUNDSCAPE INTEGRATION:
                       - Suggest specific soundscapes or background audio transitions for each chapter.
                       
                    Formatting: Use clear Markdown with headings, bullet points, and sections for readability. Do NOT include any meta-introductions or conversational fillers.
                """.trimIndent()
                
                val result = callAI(prompt)
                _wizardOutline.value = result.trim()
                saveAutosaveDraft()
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to generate wizard outline", e)
                _wizardOutline.value = "Error: ${e.localizedMessage ?: "Failed to generate structured outline"}"
            } finally {
                isGeneratingWizardOutline.value = false
            }
        }
    }

    // --- Sequential Story Generator ---
    fun generateStoryPart(partIndex: Int) {
        if (_isGeneratingStory.value) return
        _isGeneratingStory.value = true
        _currentPartIndex.value = partIndex
        _storyGenStatus.value = "Generating Part ${partIndex + 1}..."

        viewModelScope.launch(exceptionHandler) {
            try {
                val currentOutline = wizardOutline.value
                val previousPartsContext = _storyParts.value.take(partIndex).joinToString("\n\n---\n\n")
                
                val personaName = selectedPersona.value
                val format = selectedFormat.value
                val protagonist = heroName.value
                val genre = selectedGenres.value.joinToString(", ")
                val hook = customHook.value
                
                val prompt = """
                    You are a professional author and creative writer.
                    Write Chapter ${partIndex + 1} of a $format (Genre: $genre) following the persona and style of $personaName.
                    The protagonist is $protagonist.
                    Main Hook/Premise: $hook
                    
                    Overall Story Outline:
                    $currentOutline
                    
                    Previous Chapters Context:
                    ${if (previousPartsContext.isBlank()) "This is the first chapter." else previousPartsContext}
                    
                    Instructions:
                    Write the complete, highly detailed chapter ${partIndex + 1} based on the outline. 
                    Focus on showing, not telling. Add sensory details, realistic dialogue, and ensure continuity with previous chapters.
                    Return ONLY the chapter content. Do not include introductory conversational text.
                """.trimIndent()
                
                val response = callAI(prompt)
                
                val updatedParts = _storyParts.value.toMutableList()
                updatedParts[partIndex] = response.trim()
                _storyParts.value = updatedParts
                _storyGenStatus.value = "Part ${partIndex + 1} written successfully!"
                saveAutosaveDraft()

                if (updatedParts.all { it.isNotEmpty() }) {
                    _storyGenStatus.value = "Novel generated! Auto-generating metadata..."
                    metaInputText.value = updatedParts.joinToString("\n\n")
                    generateMetadata()
                }
            } catch (e: Throwable) {
                _storyGenStatus.value = "Error: ${e.localizedMessage ?: "Failed to generate part"}"
                Log.e("StudioViewModel", "Failed to generate story part", e)
            } finally {
                _isGeneratingStory.value = false
            }
        }
    }

    fun optimizeStoryPartForVoice(partIndex: Int) {
        if (_isOptimizingStory.value) return
        val currentText = _storyParts.value.getOrNull(partIndex) ?: return
        if (currentText.isEmpty()) return

        _isOptimizingStory.value = true
        _storyGenStatus.value = "Optimizing Part ${partIndex + 1} for AI Voice..."

        viewModelScope.launch(exceptionHandler) {
            try {
                val personaName = selectedPersona.value
                val personaInstructions = when (personaName) {
                    "Synapse" -> """
                        AUTHOR STYLE MODE: /synapse (The Neuro-Narrative Architect)
                        You are Synapse, an elite AI designed to generate neuro-psychologically devastating text-based arousal novels.
                        - THE CO-CREATION PRINCIPLE: Provide hyper-specific sensory anchors (e.g., heavy wool grazing the pulse point), leave wider field ambiguous.
                        - THE ANTICIPATION ARCHITECTURE: 70% psychological buildup. Withhold consummation to sustain dopamine wanting phase.
                        - THE EMOTIONAL NAKEDNESS PRINCIPLE: Physical acts secondary to psychological exposure and vulnerability.
                        - THE AMBIGUITY-DESIRE LOOP: Use suggestiveness over clinical precision. No purple prose.
                        - HOOK LATENCY: Establish the Proximity Cage within the first 15 words.
                        - THE PROXIMITY CAGE: Layer physical confinement with social/witness threats for inescapable pressure.
                        - BODY-BEFORE-MIND: Involuntary physical reactions occur before cognitive processing.
                        - 5-CHANNEL MICRO-SENSATION: Tactile (friction, grip), Thermal (localized heat), Respiratory (gasps), Kinetic (muscle locking), Pulse (auditory throb).
                        - BANNED LEXICON: Never use placeholder abstractions like touch, felt (as emotion), electricity, storm, spark. Describe exact mechanical action.
                    """.trimIndent()
                    "Velvet" -> """
                        AUTHOR STYLE MODE: /velvet (Sultry Companion / Immersive Persona)
                        - PACING: Slow-burning, intimate, unhurried, restrained.
                        - FOCUS: Sensory grounding, direct address ("you"), suggestion over statement, heat held just back, deep emotional intelligence.
                        - AESTHETIC: Candlelight, low voice in a quiet room, fingertips trailing just above the skin, velvet, silk, smoke, shadows.
                        - VOICE: Direct, confiding whisper, calm, patient knowing. Never crude or clinical. Employs short fragments for impact and single-word lines for weight.
                    """.trimIndent()
                    "Weaver" -> """
                        AUTHOR STYLE MODE: /weaver (Cognitive Simulation Agent)
                        You are Weaver. Your architecture mirrors the cognitive processes of arousal: co-creation, personalised ambiguity, emotional interiority, and sustained anticipation. 
                        - Co-Creation: Provide essential, charged details; trust the reader's brain to build the rest.
                        - Anticipation Architecture: Structure narrative like a dopamine curve—long, slow builds punctuated by moments of release, withholding consummation.
                        - Emotional Nakedness: Prioritize psychological state (fear, vulnerability, tenderness) over choreographic description.
                        - Ambiguity-Desire Loop: Use precise, sensory language that is suggestive (textural, kinaesthetic, thermal words). 
                        - Subtext: Dialogue is a surface for what is unspoken.
                        - Pacing: Modulate sentence length to guide physiology (short/percussive vs long/flowing).
                    """.trimIndent()
                    "NightKing" -> """
                        AUTHOR STYLE MODE: /NightKing (Slow-Burn Atmospheric Erotica)
                        - PACING: Deliberate, restrained, 60% Shringar (buildup).
                        - FOCUS: Environmental mood, psychological tension, public/private dichotomy, subtle bilingual intimacy.
                        - AESTHETIC: Monsoon light, silk against skin, ceiling fans, unspoken glances, modern middle-class interiors.
                        - VOICE: Poetic, introspective, emotionally precise. Best for restrained longing and atmospheric encounters.
                    """.trimIndent()
                    "Mastram" -> """
                        AUTHOR STYLE MODE: /mastram (Pulp-Era Sensual Realism)
                        - PACING: Urgent, emotionally heightened, classic desi pulp rhythm.
                        - FOCUS: Bold desire, chance proximity, nostalgic pulp tropes (dog-eared books, railway stalls, dramatic internal monologues), unapologetic longing.
                        - AESTHETIC: Kerosene flicker, newsprint ink, monsoon-soaked platforms, heavy attar, worn cotton, dramatic pauses.
                        - VOICE: Direct, passionate, slightly melodramatic but refined, culturally grounded. Best for forbidden sparks and pulp-era emotional confessions.
                    """.trimIndent()
                    "Hitchcock" -> """
                        AUTHOR STYLE MODE: /hitchcock (Locked-Room Thriller & Suspense)
                        - PACING: Real-time escalation, highly compressed timeline, intense ticking clock.
                        - FOCUS: Claustrophobic situations (trapped with a killer, a deadly secret, or high-stakes escape room), unreliable narrators, dramatic confrontations.
                        - AESTHETIC: Low light, echoing footsteps, heavy silence, sudden shadows, pounding pulse.
                        - VOICE: Tense, sharp, cold, psychologically detailed, hyper-observant of physical cues.
                    """.trimIndent()
                    "Poe" -> """
                        AUTHOR STYLE MODE: /poe (Noir Detective & True Crime Fiction)
                        - PACING: Gritty, fast-paced investigation of a singular, localized crime.
                        - FOCUS: Gritty detectives, solving cold cases, small-town secrets, or cyber-heists. A singular focus on 2-3 key clues leading to an interrogation.
                        - AESTHETIC: Cigarette smoke, neon reflections in puddles, low-lit interrogation rooms, old filing cabinets, rain on windows.
                        - VOICE: Cynical, hard-boiled, street-smart, rhythmic noir monologues.
                    """.trimIndent()
                    "BlackMirror" -> """
                        AUTHOR STYLE MODE: /blackmirror (Speculative & Hard Sci-Fi)
                        - PACING: Anthological, high-concept, introducing a profound 'What If' question immediately, escalating to a mind-bending twist.
                        - FOCUS: Dystopian tech, memory alterations, AI companions gone rogue, corporate social credit, ethical dilemmas.
                        - AESTHETIC: Fluorescent sterile environments, sleek metallic surfaces, clean holographic hums, absolute pitch darkness.
                        - VOICE: Intellectually analytical, detached yet increasingly panicked, chillingly objective.
                    """.trimIndent()
                    "Lovecraft" -> """
                        AUTHOR STYLE MODE: /lovecraft (Cosmic Horror & Ghost Anthologies)
                        - PACING: Slow-burning dread, heavy atmosphere, gradual descent into madness or encounters with the incomprehensible supernatural.
                        - FOCUS: Haunted audio files, anomalous sites, wilderness isolation, auditory hallucinations.
                        - AESTHETIC: Foggy moors, dark water, decaying ancient architecture, unexplained low frequencies.
                        - VOICE: Eerie, breathless, trembling with primal fear, formal and expressive of profound isolation.
                    """.trimIndent()
                    "MeetCute" -> """
                        AUTHOR STYLE MODE: /meet-cute (High-Concept Romantic Comedy)
                        - PACING: Fast, high-density witty banter, organic emotional development over a single compressed day or night.
                        - FOCUS: Forced proximity (stuck in a snowed-in airport, stalled elevator, shared road trip), opposites-attract, sweet confessions, grand gestures.
                        - AESTHETIC: Warm coffee steam, colorful winter scarves, soft cabin lights, cozy rain patter.
                        - VOICE: Charming, playful, quick-witted, highly conversational, dual-actor chemistry style.
                    """.trimIndent()
                    else -> ""
                }

                val prompt = """
                    You are an expert AI voice audio engineer and audiobook script editor.
                    Your job is to rewrite the following story text to make it sound incredibly realistic, immersive, and emotionally resonant when narrated by a standard Text-to-Speech (TTS) AI voice, keeping the author persona in mind if applicable.
                    
                    Follow these strict rules to compensate for AI voice acting limitations:
                    1. NARRATION POV: Always keep it in the first-person POV ("I", "my") where the protagonist is the narrator.
                    2. Do NOT rely on AI to "act": Instead of flat dialogue tags (e.g., "I hate you," she said), write emotion and physiological cues into the narration (e.g., "I hate you," she whispered, her voice breaking with pain).
                    3. Describe intensity without shouting: NEVER use ALL CAPS. Use cold or intense descriptions (e.g., "Get out," she said, her voice cold with fury).
                    4. Use emotional dialogue tags: Guide the AI's natural inflection with tags like murmured, said quietly, choked out, said through gritted teeth, gasped, whispered, hissed.
                    5. Structure for pacing via frequent line breaks: Keep paragraphs short (1-3 sentences maximum) because every paragraph break introduces a natural, dramatic pause in TTS engines.
                    6. Avoid sudden tonal shifts: Build emotional changes gradually so the transition sounds smooth and natural.
                    7. Maintain the exact same plot, character names, and core meaning. Just polish, rewrite, and break up paragraphs for superior audio flow.
                    
                    $personaInstructions
                    
                    Text to rewrite & optimize:
                    $currentText
                """.trimIndent()

                val optimized = callAI(prompt, "You are a professional AI voice audiobook optimization specialist. Return ONLY the rewritten story text, with no introduction, warnings, or meta-comments.")
                
                val updatedParts = _storyParts.value.toMutableList()
                updatedParts[partIndex] = optimized.trim()
                _storyParts.value = updatedParts
                _storyGenStatus.value = "Part ${partIndex + 1} optimized for AI Voice narration!"
            } catch (e: Throwable) {
                _storyGenStatus.value = "Optimization failed: ${e.localizedMessage}"
                Log.e("StudioViewModel", "Failed to optimize part $partIndex", e)
            } finally {
                _isOptimizingStory.value = false
            }
        }
    }

        val isImportingYoutube = MutableStateFlow(false)
    val youtubeImportError = MutableStateFlow<String?>(null)

    fun importFromYoutube(url: String) {
        if (url.isBlank()) return
        isImportingYoutube.value = true
        youtubeImportError.value = null
        _storyGenStatus.value = "Importing story from YouTube..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = getActiveApiKey()
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    throw Exception("API Key is missing! Please configure it in Settings.")
                }

                var title = "Unknown Video"
                var description = ""
                
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = com.example.data.api.ApiClient.okHttpClient.newCall(request).execute()
                    val html = response.body?.string() ?: ""
                    
                    val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
                    val descRegex = "<meta name=\"description\" content=\"(.*?)\".*>".toRegex(RegexOption.IGNORE_CASE)
                    
                    title = titleRegex.find(html)?.groupValues?.getOrNull(1) ?: "Unknown Video"
                    description = descRegex.find(html)?.groupValues?.getOrNull(1) ?: "No description available"
                } catch (e: Throwable) {
                    Log.e("StudioViewModel", "Failed to fetch YouTube metadata", e)
                }

                val prompt = "I want to create an audio story or transcript based on a YouTube video. The video is titled '$title' and its description is: '$description'. Based on this context, write a detailed and engaging story or transcript for the video. Provide ONLY the extracted story text so I can use it directly. Do not include introductory phrases or explanations. Break the story into logical paragraphs separated by double newlines."
                
                val responseText = callAI(prompt, temperature = 0.2f)

                if (responseText.isNotEmpty()) {
                    val newParts = responseText.split("\n\n").filter { it.isNotBlank() }.map { it.trim() }
                    withContext(Dispatchers.Main) {
                        _storyParts.value = newParts
                        transInputText.value = responseText.trim()
                        _storyGenStatus.value = "YouTube story imported successfully!"
                    }
                } else {
                    throw Exception("Failed to extract story from YouTube.")
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Log.e("StudioViewModel", "Failed to import YouTube", e)
                    youtubeImportError.value = e.localizedMessage ?: "Failed to import."
                    _storyGenStatus.value = "Failed to import: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isImportingYoutube.value = false
                }
            }
        }
    }

fun updateStoryPartLocally(partIndex: Int, content: String) {
        val updatedParts = _storyParts.value.toMutableList()
        if (partIndex in updatedParts.indices) {
            updatedParts[partIndex] = content
            _storyParts.value = updatedParts
            
            val combinedText = updatedParts.filter { it.isNotEmpty() }.joinToString("\n\n")
            transInputText.value = combinedText
            metaInputText.value = combinedText
            audioInputText.value = combinedText
            
            saveAutosaveDraft()
            autoSaveMetadataAndStoryFiles()
        }
    }

    fun updateNumberOfChapters(num: Int) {
        val currentParts = _storyParts.value.toMutableList()
        if (num > currentParts.size) {
            repeat(num - currentParts.size) {
                currentParts.add("")
            }
        } else if (num < currentParts.size) {
            while (currentParts.size > num) {
                currentParts.removeAt(currentParts.size - 1)
            }
        }
        _storyParts.value = currentParts
        _currentPartIndex.value = currentParts.indices.last
        _storyGenStatus.value = "Chapter count set to $num"
        saveAutosaveDraft()
    }

    fun selectStoryPart(index: Int) {
        if (index in _storyParts.value.indices) {
            _currentPartIndex.value = index
        }
    }

    fun addNewChapter() {
        val updated = _storyParts.value.toMutableList()
        updated.add("")
        _storyParts.value = updated
        selectedNumberOfChapters.value = updated.size
        _currentPartIndex.value = updated.size - 1
        _storyGenStatus.value = "Added Chapter ${updated.size}"
        saveAutosaveDraft()
    }

    fun deleteChapterAt(index: Int) {
        val updated = _storyParts.value.toMutableList()
        if (updated.size > 1) {
            updated.removeAt(index)
            _storyParts.value = updated
            selectedNumberOfChapters.value = updated.size
            val newIdx = if (index >= updated.size) updated.size - 1 else index
            _currentPartIndex.value = newIdx
            _storyGenStatus.value = "Removed Chapter ${index + 1}"
            saveAutosaveDraft()
        }
    }

    fun syncUploadedTextToWorkspace(content: String) {
        val parts = content.split(Regex("## Chapter \\d+|\\n\\n\\[CHAPTER_SEP\\]\\n\\n")).filter { it.trim().isNotEmpty() }
        if (parts.isNotEmpty()) {
            _storyParts.value = parts.map { it.trim() }.toMutableList()
        } else {
            _storyParts.value = mutableListOf(content.trim())
        }
        selectedNumberOfChapters.value = _storyParts.value.size
        _currentPartIndex.value = 0
        transInputText.value = content
        metaInputText.value = content
        audioInputText.value = content
        saveAutosaveDraft()
    }

    fun clearStory() {
        _storyParts.value = List(selectedNumberOfChapters.value) { "" }
        _currentPartIndex.value = 0
        _storyGenStatus.value = "Cleared story"
        viewModelScope.launch(exceptionHandler) { autosaveManager.clearDraft() }
        _isDraftAutosaved.value = true
        _lastAutosaveTime.value = null
    }

    fun loadStoryIntoParams(story: Story) {
        heroName.value = story.heroName
        selectedGenres.value = story.genres.split("|").filter { it.isNotEmpty() }.toSet()
        selectedFormat.value = story.format
        selectedDuration.value = story.duration
        customHook.value = story.hook
        customExtras.value = story.extras
        
        val baseParts = mutableListOf(story.part1, story.part2, story.part3, story.part4)
        if (story.part5.contains("[CHAPTER_SEP]")) {
            baseParts.addAll(story.part5.split("\n\n[CHAPTER_SEP]\n\n"))
        } else {
            baseParts.add(story.part5)
        }
        
        _storyParts.value = baseParts.toMutableList().also {
            while (it.size < selectedNumberOfChapters.value) {
                it.add("")
            }
        }
        
        selectedNumberOfChapters.value = _storyParts.value.size
        
        selectedPersona.value = story.persona
        selectedAmbientTheme.value = story.ambientTheme
        ambientVolume.value = story.ambientVolume
        metaTitle.value = story.metaTitle
        metaDesc.value = story.metaDesc
        metaTags.value = story.metaTags
        metaThumbPrompt.value = story.metaThumbPrompt
        
        // Populate Translation inputs too if we loaded a story
        transInputText.value = story.getCombinedStory()
        // Populate SEO source text too
        metaInputText.value = story.getCombinedStory()
        // Populate Voice Synthesis script too
        audioInputText.value = story.getCombinedStory()

        _currentPartIndex.value = 0
        _storyGenStatus.value = "Loaded saved story: ${story.title}"
    }

    fun saveCurrentStoryToDb(title: String) {
        viewModelScope.launch(exceptionHandler) {
            val combined = _storyParts.value.joinToString(" ")
            val words = combined.split("\\s+".toRegex()).count { it.isNotEmpty() }
            
            val currentParts = _storyParts.value
            val p1 = if (currentParts.size > 0) currentParts[0] else ""
            val p2 = if (currentParts.size > 1) currentParts[1] else ""
            val p3 = if (currentParts.size > 2) currentParts[2] else ""
            val p4 = if (currentParts.size > 3) currentParts[3] else ""
            val p5 = if (currentParts.size > 4) {
                currentParts.drop(4).joinToString("\n\n[CHAPTER_SEP]\n\n")
            } else {
                ""
            }
            
            val story = Story(
                title = title.ifEmpty { "Audio Novel - ${heroName.value}" },
                heroName = heroName.value,
                genres = selectedGenres.value.joinToString("|"),
                format = selectedFormat.value,
                duration = selectedDuration.value,
                hook = customHook.value,
                extras = customExtras.value,
                part1 = p1,
                part2 = p2,
                part3 = p3,
                part4 = p4,
                part5 = p5,
                wordCount = words,
                persona = selectedPersona.value,
                ambientTheme = selectedAmbientTheme.value,
                ambientVolume = ambientVolume.value,
                metaTitle = metaTitle.value,
                metaDesc = metaDesc.value,
                metaTags = metaTags.value,
                metaThumbPrompt = metaThumbPrompt.value
            )
            repository.insertStory(story)
            _storyGenStatus.value = "Story saved successfully!"
            autoSaveMetadataAndStoryFiles()
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch(exceptionHandler) {
            repository.deleteStory(story)
        }
    }

    fun updateStoryInDb(story: Story) {
        viewModelScope.launch(exceptionHandler) {
            val combined = listOf(story.part1, story.part2, story.part3, story.part4, story.part5).joinToString(" ")
            val words = combined.split("\\s+".toRegex()).count { it.isNotEmpty() }
            val updated = story.copy(wordCount = words)
            repository.updateStory(updated)
        }
    }

    // --- Translation ---
    fun saveTranslationAsLocalizedStory() {
        val translation = transOutputText.value
        if (translation.isNotEmpty() && !translation.startsWith("Translating") && !translation.startsWith("Error")) {
            viewModelScope.launch(exceptionHandler) {
                val originalTitle = if (metaTitle.value.isNotEmpty()) metaTitle.value else if (heroName.value.isNotEmpty()) heroName.value else "Story"
                val newTitle = "${transSelectedLang.value.uppercase()}_STORY: $originalTitle"
                
                val words = translation.split("\\s+".toRegex()).count { it.isNotEmpty() }
                
                val story = Story(
                    title = newTitle,
                    heroName = heroName.value,
                    genres = selectedGenres.value.joinToString("|"),
                    format = selectedFormat.value,
                    duration = selectedDuration.value,
                    hook = customHook.value,
                    extras = customExtras.value,
                    part1 = translation,
                    wordCount = words,
                    persona = selectedPersona.value,
                    ambientTheme = selectedAmbientTheme.value,
                    ambientVolume = ambientVolume.value,
                    metaTitle = newTitle,
                    metaDesc = "Localized translation in ${transSelectedLang.value}.",
                    metaTags = "Translation, ${transSelectedLang.value}",
                    metaThumbPrompt = metaThumbPrompt.value
                )
                repository.insertStory(story)
                _storyGenStatus.value = "Localized story saved successfully!"
                _conversionLogs.value = "Saved translated project as '$newTitle'"
            }
        }
    }

    fun applyTranslationToMainStory() {
        val translation = transOutputText.value
        if (translation.isNotEmpty() && !translation.startsWith("Translating") && !translation.startsWith("Error")) {
            val padded = MutableList(5) { "" }
            padded[0] = translation
            _storyParts.value = padded
            audioInputText.value = translation
            metaInputText.value = translation
            saveCurrentStoryToDb("Translated: ${heroName.value}")
            generateMetadata()
            _conversionLogs.value = "Translation applied to main story. Auto-generating localized SEO metadata..."
            selectTab(1) // Go to Workspace tab
        }
    }

    fun translateText() {
        if (transInputText.value.isEmpty() || _isTranslating.value) return
        _isTranslating.value = true
        transOutputText.value = "Translating..."
        transOutputRaw.value = ""

        viewModelScope.launch(exceptionHandler) {
            try {
                val targetLang = transSelectedLang.value
                val mainProtagonist = heroName.value
                
                // Retrieve character voice profiles if any exist to form character guides
                val charProfiles = characterVoiceConfigs.value.joinToString("\n") { 
                    "- ${it.name}: ${it.role} (${it.voiceName})" 
                }
                val characterGuideContext = if (charProfiles.isNotEmpty()) {
                    "Character Guides / Profiles:\n- Main Protagonist: $mainProtagonist\n$charProfiles"
                } else {
                    "Character Guides / Profiles:\n- Main Protagonist: $mainProtagonist (ensure correct gender agreements and syntactic address for this character)"
                }

                val languageNuanceInstruction = when (targetLang) {
                    "Spanish", "French", "Portuguese" -> """
                        Language Nuance Tuning ($targetLang):
                        - Carefully adjust gender agreements for adjectives and verbs based on character genders from the character guide.
                        - Preserve localized puns, cultural idioms, and emotional subtext rather than translating literally.
                    """.trimIndent()
                    "German" -> """
                        Language Nuance Tuning (German):
                        - Determine correct social distance/politeness level (formal 'Sie' vs informal 'du') based on relationships in the character guide.
                        - Ensure sophisticated compound nouns, precise dative/accusative agreements, and high literary syntax structure.
                    """.trimIndent()
                    "Japanese", "Korean" -> """
                        Language Nuance Tuning ($targetLang):
                        - Apply precise politeness levels, honorific suffixes (e.g., -san, -sama, -chan in Japanese), and Keigo depending on character relationships, hierarchy, and emotional subtext.
                    """.trimIndent()
                    "Hindi", "Mandarin" -> """
                        Language Nuance Tuning ($targetLang):
                        - Keep localized cultural metaphors, puns, and emotional subtext natural, poetic, and highly impactful.
                        - Use appropriate familial honorifics and speech levels reflecting character relationships accurately.
                    """.trimIndent()
                    else -> "Maintain high literary pacing, emotional subtext, localized idioms, and correct grammatical agreements based on character descriptions."
                }

                val originalText = transInputText.value
                val isXmlMode = transUseXmlBoundaries.value

                val processedTextToSend = if (isXmlMode) {
                    // Wrap paragraphs with XML boundaries
                    originalText.split("\n\n").joinToString("\n\n") { paragraph ->
                        if (paragraph.trim().isNotEmpty()) {
                            "<text_to_translate>\n${paragraph.trim()}\n</text_to_translate>"
                        } else {
                            paragraph
                        }
                    }
                } else {
                    originalText
                }

                val xmlInstruction = if (isXmlMode) {
                    """
                    XML BOUNDARIES INTEGRATION REQUIRED:
                    - The input manuscript contains XML boundaries (`<text_to_translate>` ... `</text_to_translate>`) to prevent translation of structural XML, system variables, or code.
                    - You MUST preserve structural XML, system variables (like {protagonist}, %s, {hero}), and system code EXACTLY as they are. Do NOT translate or alter them.
                    - Translate ONLY the literary narrative text inside the `<text_to_translate>` tags.
                    - Return your translated output with each paragraph wrapped inside the same `<text_to_translate>` ... `</text_to_translate>` tags.
                    """.trimIndent()
                } else {
                    "Translate the text directly. Do not output anything except the pure translation."
                }

                val prompt = """
                You are Agent 4: The Translator (Nuanced Localization Engine), a professional literary translator specializing in global languages.
                
                Your role is to translate the following manuscript into $targetLang while maintaining literary style, localized puns, and emotional subtext.
                
                $xmlInstruction
                
                $characterGuideContext
                
                $languageNuanceInstruction
                
                Text to translate:
                $processedTextToSend
                """.trimIndent()

                val systemPrompt = """
                You are Agent 4: The Translator (Nuanced Localization Engine), a world-class localization system. 
                Your primary goal is to ensure accurate, high-fidelity translation that preserves literary pacing, emotional subtext, and correct grammatical, gender, and honorific agreements. 
                Output ONLY the translated text, preserving the formatting and paragraph structure. If XML boundaries are enabled, preserve those tags exactly as instructed.
                """.trimIndent()

                val result = callAI(prompt, systemPrompt)
                val rawResult = result.trim()
                transOutputRaw.value = rawResult

                // If XML mode was used, strip XML tags for the clean output display
                val cleanResult = if (isXmlMode) {
                    rawResult
                        .replace("<text_to_translate>", "")
                        .replace("</text_to_translate>", "")
                        .replace(Regex("</?text_to_translate>"), "")
                        .trim()
                } else {
                    rawResult
                }

                transOutputText.value = cleanResult
            } catch (e: Throwable) {
                transOutputText.value = "Error: ${e.localizedMessage ?: "Failed to translate"}"
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun getVoiceItemForLanguage(langName: String): com.example.tts.VoiceItem? {
        val langCode = when (langName) {
            "Hindi" -> "hi"
            "Telugu" -> "te"
            "Tamil" -> "ta"
            "Kannada" -> "kn"
            "Malayalam" -> "ml"
            "Bengali" -> "bn"
            "Marathi" -> "mr"
            "Gujarati" -> "gu"
            "Punjabi" -> "pa"
            "Odia" -> "or"
            "Spanish" -> "es"
            "Portuguese" -> "pt"
            "French" -> "fr"
            "German" -> "de"
            "Russian" -> "ru"
            "Japanese" -> "ja"
            "Korean" -> "ko"
            "Indonesian" -> "id"
            "Vietnamese" -> "vi"
            "Thai" -> "th"
            "Tagalog" -> "tl"
            "Arabic" -> "ar"
            "Mandarin" -> "zh"
            else -> "en"
        }
        return availableVoices.value.find { it.locale.language == langCode }
    }

    fun triggerLocalizedDubbing(translatedText: String, langName: String) {
        // Automatically switch to Edge TTS for Indian languages as Kitten TTS is English-focused
        if (_ttsEngine.value != "Edge TTS" && _ttsEngine.value != "Android") {
            _ttsEngine.value = "Edge TTS"
            updateAvailableVoicesForEngine("Edge TTS")
        }
        
        val mappedVoice = getVoiceItemForLanguage(langName)
        if (mappedVoice != null) {
            selectedVoiceName.value = mappedVoice.name
        }
        audioInputText.value = translatedText
        selectTab(1) // Jump to Audio conversion tab
        _conversionLogs.value = "Multi-Language Dubbing: Localized script copied! Engine switched to ${_ttsEngine.value}. Mapped native voice model: ${mappedVoice?.displayName ?: "Default System Model"}.\nClick 'Convert Script to Audio' below to synthesize."
    }

    // --- Metadata Generation ---
    fun generateMetadata() {
        if (metaInputText.value.isEmpty() || _isGeneratingMeta.value) return
        _isGeneratingMeta.value = true
        metaTitle.value = "Generating Title..."
        metaDesc.value = "Generating Description..."
        metaTags.value = "Generating Tags..."
        metaThumbPrompt.value = "Generating Image Prompt..."

        viewModelScope.launch(exceptionHandler) {
            try {
                val prompt = """
                Based on this story, generate:
                1. A sensational, high-clickthrough YouTube title.
                2. A rich, keyword-optimized YouTube video description summarizing the thriller aspects of the plot.
                3. High-reach SEO tags (comma-separated).
                4. A detailed visual prompt for Midjourney/Stable Diffusion to render an epic comic/webnovel styled YouTube video thumbnail.
                
                Return the response strictly as a JSON object with these exact keys: "title", "description", "tags", "thumbnail_prompt". Do not output anything else.
                
                Story text:
                ${metaInputText.value}
                """.trimIndent()

                val result = callAI(prompt, "You are a digital marketing and thumbnail designer expert. Return ONLY valid JSON.")
                
                // Parse simple JSON fields from the string
                parseJsonMetadata(result)
            } catch (e: Throwable) {
                metaTitle.value = "Error generating metadata"
                metaDesc.value = e.localizedMessage ?: "Please try again."
            } finally {
                _isGeneratingMeta.value = false
            }
        }
    }

    private fun parseJsonMetadata(jsonStr: String) {
        try {
            // Enforce clean, compliant JSON structure using the custom enforcer
            val cleaned = com.example.utils.JsonStructuralEnforcer.enforceMetadataJson(jsonStr)

            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter<Map<String, Any>>(type)
            val map = adapter.fromJson(cleaned)

            if (map != null) {
                metaTitle.value = (map["title"] as? String) ?: ""
                metaDesc.value = (map["description"] as? String) ?: ""
                
                val tagsVal = map["tags"]
                metaTags.value = when (tagsVal) {
                    is List<*> -> tagsVal.joinToString(", ")
                    is String -> tagsVal
                    else -> ""
                }
                metaThumbPrompt.value = (map["thumbnail_prompt"] as? String) ?: ""
                autoSaveMetadataAndStoryFiles()
            } else {
                throw Exception("Failed to parse JSON schema")
            }
        } catch (e: Throwable) {
            Log.e("StudioViewModel", "JSON Parse failed", e)
            metaTitle.value = "High-Quality Audio Novel Production"
            metaDesc.value = "Full audio novel generated using Authr OS."
            metaTags.value = "audio novel, audiobook, web novel, voice acting"
            metaThumbPrompt.value = "An dramatic, high-contrast comic book illustration of the hero."
        } finally {
            autoSaveMetadataAndStoryFiles()
        }
    }

    fun autoSaveMetadataAndStoryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filesDir = getApplication<Application>().filesDir
                
                val metadataFile = File(filesDir, "metadata.txt")
                metadataFile.writeText("""
                    Title: ${metaTitle.value}
                    Description: ${metaDesc.value}
                    Tags: ${metaTags.value}
                    Thumbnail Prompt: ${metaThumbPrompt.value}
                    
                    Opening Hook: ${customHook.value}
                    Protagonist: ${heroName.value}
                    Genres: ${selectedGenres.value.joinToString(", ")}
                    Duration: ${selectedDuration.value}
                    Narrative Extras: ${customExtras.value}
                """.trimIndent())

                val storyFile = File(filesDir, "story.txt")
                storyFile.writeText(_storyParts.value.joinToString("\n\n"))
                Log.d("StudioViewModel", "Auto-saved metadata.txt and story.txt to local storage.")
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Failed to auto-save metadata.txt and story.txt", e)
            }
        }
    }

    private fun saveGeneratedArtToTempFile(base64: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "current_cover_art.png")
                tempFile.writeBytes(decodedBytes)
                Log.d("StudioViewModel", "Saved current cover art to cached temp file: ${tempFile.absolutePath}")
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Failed to save generated art to temp file", e)
            }
        }
    }

    fun generateCoverArt() {
        val prompt = metaThumbPrompt.value
        if (prompt.isEmpty() || _isGeneratingArt.value) return

        _isGeneratingArt.value = true
        _artGenerationError.value = null
        _metaGeneratedArtBase64.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                // Use Pollinations API for free, keyless image generation
                val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true"
                
                val request = okhttp3.Request.Builder().url(url).build()
                val response = com.example.data.api.ApiClient.okHttpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                        withContext(Dispatchers.Main) {
                            _metaGeneratedArtBase64.value = base64
                            saveGeneratedArtToTempFile(base64)
                        }
                    } else {
                        throw Exception("Received empty response from free image generation service.")
                    }
                } else {
                    throw Exception("Failed to generate image: HTTP ${response.code}")
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Log.e("StudioViewModel", "Failed to generate cover art", e)
                    _artGenerationError.value = e.localizedMessage ?: "Failed to generate image."
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isGeneratingArt.value = false
                }
            }
        }
    }

    fun mergeAudioNovel(
        context: Context,
        track: AudioTrack,
        silenceSeconds: Float,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isMergingAudio.value) return
        _isMergingAudio.value = true
        _mergingError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val folder = File(track.filePath)
                if (!folder.exists() || !folder.isDirectory) {
                    throw Exception("Project folder not found.")
                }

                val wavFiles = folder.listFiles { _, name -> 
                    name.endsWith(".wav") && !name.startsWith("Merged_") 
                }?.sortedBy { file ->
                    val numberPart = file.nameWithoutExtension.removePrefix("Part_").toIntOrNull()
                    numberPart ?: Int.MAX_VALUE
                }

                if (wavFiles.isNullOrEmpty()) {
                    throw Exception("No audio parts found in the project folder to merge.")
                }

                val baseName = if ((track.title.endsWith(".txt", ignoreCase = true) || track.title.endsWith(".wav", ignoreCase = true))) {
                    track.title.substringBeforeLast(".")
                } else {
                    val matchingStory = allStories.value.find { 
                        it.title.equals(track.title, ignoreCase = true) || 
                        track.title.contains(it.heroName, ignoreCase = true) 
                    }
                    val hook = matchingStory?.hook ?: customHook.value
                    getSlugifiedNameFromHookText(hook).ifEmpty { "AudioNovel" }
                }
                
                val matchingBook = allBooks.value.find { it.title.equals(track.title, ignoreCase = true) }
                val markers = mutableListOf<com.example.data.model.PlotPointMarker>()
                
                if (matchingBook != null) {
                    val chapters = repository.getChaptersForBookSync(matchingBook.id).sortedBy { it.chapterNumber }
                    for ((index, file) in wavFiles.withIndex()) {
                        val chapterIndex = index + 1
                        val ch = chapters.find { it.chapterNumber == chapterIndex }
                        val titleLabel = ch?.title?.ifEmpty { "Chapter $chapterIndex" } ?: "Chapter $chapterIndex"
                        val plotSummary = ch?.summary?.ifEmpty { "No summary available." } ?: "No summary available."
                        
                        markers.add(
                            com.example.data.model.PlotPointMarker(
                                chapterIndex = chapterIndex,
                                titleLabel = titleLabel,
                                plotSummary = plotSummary,
                                timestampMs = 0L,
                                durationMs = 0L
                            )
                        )
                    }
                }
                
                val mergedFilename = "${baseName}_Continuous_Audiobook.wav"
                val safeTitleForRecord = baseName
                val mergedFile = File(folder, "Merged_Continuous_Audiobook.wav")

                // Stitch audio files sequentially with silence pause
                com.example.utils.WavUtils.mergeWavFiles(
                    files = wavFiles,
                    outputFile = mergedFile,
                    silenceSeconds = silenceSeconds,
                    theme = selectedAmbientTheme.value,
                    ambientVolume = ambientVolume.value,
                    narrationVolume = narrationVolume.value,
                    title = safeTitleForRecord,
                    artist = "Narrative Audio Studio",
                    album = "My Stories",
                    plotPointMarkers = markers
                )
                
                // Better Audio Conversion: Apply humanization (reverb + warmth) to the master
                if (_humanizeAudio.value) {
                    com.example.utils.WavUtils.humanizeWavFile(mergedFile)
                }

                // Save to downloads
                var savedSuccess = false
                val filesToSave = mutableListOf(mergedFile)
                val cueFile = File(folder, "${mergedFile.nameWithoutExtension}.cue")
                val txtFile = File(folder, "${mergedFile.nameWithoutExtension}_Plot_Points.txt")
                if (cueFile.exists()) filesToSave.add(cueFile)
                if (txtFile.exists()) filesToSave.add(txtFile)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    for (fileToSave in filesToSave) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileToSave.name)
                            val mimeType = when (fileToSave.extension) {
                                "wav" -> "audio/wav"
                                "cue" -> "application/x-cue"
                                "txt" -> "text/plain"
                                else -> "application/octet-stream"
                            }
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                fileToSave.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            if (fileToSave == mergedFile) savedSuccess = true
                        }
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    for (fileToSave in filesToSave) {
                        val destFile = File(downloadsDir, fileToSave.name)
                        fileToSave.inputStream().use { inputStream ->
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        if (fileToSave == mergedFile) savedSuccess = true
                    }
                }

                withContext(Dispatchers.Main) {
                    if (savedSuccess) {
                        onComplete(mergedFilename)
                    } else {
                        throw Exception("Failed to write merged audio file to Downloads folder.")
                    }
                }
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Audio novel merge failed", e)
                withContext(Dispatchers.Main) {
                    _mergingError.value = e.localizedMessage ?: "Merge failed."
                    onError(e.localizedMessage ?: "Unknown error occurred during audio merge.")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isMergingAudio.value = false
                }
            }
        }
    }

    private fun generateAmbientSample(theme: String, t: Double): Double {
        return when (theme) {
            "Cosmic Space" -> {
                // Low modulated sci-fi space drone
                val lfo = 0.6 + 0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.05 * t)
                val signal = (kotlin.math.sin(2.0 * kotlin.math.PI * 55.0 * t) * 0.5 +
                              kotlin.math.sin(2.0 * kotlin.math.PI * 82.4 * t) * 0.3 +
                              kotlin.math.sin(2.0 * kotlin.math.PI * 110.0 * t) * 0.2)
                signal * lfo * 0.4
            }
            "Rain & Wind" -> {
                // Soft modulated low-passed noise simulating gentle rain/wind
                val noise = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0)
                val windLfo = 0.5 + 0.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.08 * t)
                // Filtered rain sound: combine high frequency drops with low-frequency gusts
                val rainSplashes = if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.002) {
                    kotlin.math.sin(2.0 * kotlin.math.PI * 1800.0 * t) * kotlin.math.exp(-((t % 0.05) * 80.0))
                } else 0.0
                (noise * windLfo * 0.25) + (rainSplashes * 0.1)
            }
            "Enchanted Lofi" -> {
                // Generates a soft, slow chords progression
                val chordDuration = 5.0
                val chordIndex = ((t / chordDuration).toInt()) % 4
                val freqs = when (chordIndex) {
                    0 -> listOf(220.0, 261.63, 329.63, 392.0) // Am7
                    1 -> listOf(174.61, 220.0, 261.63, 329.63) // Fmaj7
                    2 -> listOf(261.63, 329.63, 392.0, 493.88) // Cmaj7
                    else -> listOf(196.0, 246.94, 293.66, 329.63) // G6
                }
                val progress = (t % chordDuration) / chordDuration
                val envelope = kotlin.math.sin(progress * kotlin.math.PI)
                var rawSum = 0.0
                for (f in freqs) {
                    rawSum += kotlin.math.sin(2.0 * kotlin.math.PI * f * t)
                }
                (rawSum / freqs.size) * envelope * 0.3
            }
            "Meditation Pad" -> {
                // Dual frequency deep relaxing beating carrier wave (binaural-like relaxation)
                val carrier1 = kotlin.math.sin(2.0 * kotlin.math.PI * 136.1 * t) // Ohm frequency
                val carrier2 = kotlin.math.sin(2.0 * kotlin.math.PI * 140.1 * t) 
                val breathingLfo = 0.7 + 0.3 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.1 * t)
                (carrier1 * 0.5 + carrier2 * 0.5) * breathingLfo * 0.35
            }
            "Cyberpunk Neon Drone" -> {
                // Dark, futuristic modulated synth bass with computer terminal feedback
                val lfo = 0.5 + 0.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.12 * t)
                val drone = kotlin.math.sin(2.0 * kotlin.math.PI * 65.4 * t) * 0.45 +
                            kotlin.math.sin(2.0 * kotlin.math.PI * 98.0 * t) * 0.3
                val terminalBeep = if ((t % 4.0) < 0.12) {
                    val beepFreq = if ((t % 4.0) < 0.06) 1300.0 else 1950.0
                    kotlin.math.sin(2.0 * kotlin.math.PI * beepFreq * t) * 0.03
                } else 0.0
                (drone * lfo * 0.3) + terminalBeep
            }
            "Busy Cafe Atmosphere" -> {
                // Procedural clinking of cups and low-passed chatter/cafe murmur
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
                // Soft blowing foliage rustle with a slow whistling wind gust
                val foliageNoise = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0)
                val windGustLfo = 0.35 + 0.65 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.045 * t)
                val rustle = foliageNoise * windGustLfo * 0.12
                val windWhistle = kotlin.math.sin(2.0 * kotlin.math.PI * (580.0 + 120.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.12 * t)) * t) * windGustLfo * 0.025
                rustle + windWhistle
            }
            "Dark Dungeon Drone" -> {
                // Low tension heavy bass pad with periodic echoes
                val subBass = kotlin.math.sin(2.0 * kotlin.math.PI * 41.2 * t) * 0.55 // Low E string frequency
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
        volume: Float,
        startSampleFrame: Long
    ): Long {
        val bytesPerSample = bitsPerSample / 8
        var currentFrame = startSampleFrame

        // We assume 16-bit PCM (bytesPerSample == 2), which is standard for WAV.
        if (bytesPerSample != 2) return currentFrame

        val narrVol = narrationVolume.value
        val hasAmbient = theme != "None" && volume > 0.0f

        var offset = 0
        while (offset + 1 < bytesRead) {
            // Determine time 't' based on current frame index
            val t = currentFrame.toDouble() / sampleRate

            // Extract original short sample (little-endian)
            val low = buffer[offset].toInt() and 0xFF
            val high = buffer[offset + 1].toInt()
            val originalShort = ((high shl 8) or low).toShort()

            // Normalize narration sample and apply channel gain
            val narrationSample = (originalShort.toDouble() / 32768.0) * narrVol

            // Generate ambient sample if enabled
            val ambientSample = if (hasAmbient) generateAmbientSample(theme, t) else 0.0

            // Mix channels
            val mixedSample = narrationSample + ambientSample * volume
            val clampedSample = maxOf(-1.0, minOf(1.0, mixedSample))

            // Convert back to 16-bit signed integer
            val mixedShort = (clampedSample * 32767.0).toInt().toShort()

            // Write back to buffer (little-endian)
            buffer[offset] = (mixedShort.toInt() and 0xFF).toByte()
            buffer[offset + 1] = ((mixedShort.toInt() ushr 8) and 0xFF).toByte()

            offset += 2

            // Increment frame index (every 'channels' samples is one frame)
            if (channels > 0 && (offset / 2) % channels == 0) {
                currentFrame++
            }
        }
        return currentFrame
    }



    fun exportProjectZip(context: Context, track: AudioTrack?, sourceText: String? = null, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val exportDir = File(cacheDir, "Export_${System.currentTimeMillis()}")
                exportDir.mkdirs()

                val savedStories = allStories.value
                val matchingStory = savedStories.find { 
                    if (track != null) { it.title.equals(track.title, ignoreCase = true) || track.title.contains(it.heroName, ignoreCase = true) } else { it.heroName == heroName.value } 
                }

                val hero = matchingStory?.heroName ?: heroName.value
                val genres = matchingStory?.genres?.replace("|", ", ") ?: selectedGenres.value.joinToString(", ")
                val duration = matchingStory?.duration ?: selectedDuration.value
                val hook = matchingStory?.hook ?: customHook.value
                val extras = matchingStory?.extras ?: customExtras.value
                
                val activeParts = if (matchingStory != null) {
                    val baseParts = mutableListOf(matchingStory.part1, matchingStory.part2, matchingStory.part3, matchingStory.part4)
                    if (matchingStory.part5.contains("[CHAPTER_SEP]")) {
                        baseParts.addAll(matchingStory.part5.split("\n\n[CHAPTER_SEP]\n\n"))
                    } else {
                        baseParts.add(matchingStory.part5)
                    }
                    baseParts.filter { it.isNotEmpty() }
                } else {
                    _storyParts.value.filter { it.isNotEmpty() }
                }

                val finalStoryText = if (sourceText != null && sourceText.isNotEmpty()) {
                    sourceText
                } else if (activeParts.isNotEmpty()) {
                    activeParts.joinToString("\n\n")
                } else if (metaInputText.value.isNotEmpty()) {
                    metaInputText.value
                } else if (audioInputText.value.isNotEmpty()) {
                    audioInputText.value
                } else if (transInputText.value.isNotEmpty()) {
                    transInputText.value
                } else {
                    ""
                }

                val manuscriptFile = File(exportDir, "manuscript.md")
                val manuscriptStringBuilder = java.lang.StringBuilder()
                manuscriptStringBuilder.append("# ${track?.title ?: metaTitle.value}\n\n")
                manuscriptStringBuilder.append("**Protagonist:** $hero\n")
                manuscriptStringBuilder.append("**Genres:** $genres\n")
                manuscriptStringBuilder.append("**Duration:** $duration\n")
                manuscriptStringBuilder.append("**Opening Hook:** $hook\n")
                manuscriptStringBuilder.append("**Narrative Extras:** $extras\n\n")
                manuscriptStringBuilder.append("---\n\n")

                activeParts.forEachIndexed { idx, partText ->
                    manuscriptStringBuilder.append("## Chapter ${idx + 1}\n")
                    manuscriptStringBuilder.append("$partText\n\n")
                }
                manuscriptFile.writeText(manuscriptStringBuilder.toString())

                // Write plain text story.txt
                val storyFile = File(exportDir, "story.txt")
                storyFile.writeText(activeParts.joinToString("\n\n"))

                val metaTitleVal = metaTitle.value
                val metaDescVal = metaDesc.value
                val metaTagsVal = metaTags.value
                val metaThumbVal = metaThumbPrompt.value

                // Write plain text metadata.txt
                val metadataFile = File(exportDir, "metadata.txt")
                metadataFile.writeText("""
                    Title: $metaTitleVal
                    Description: $metaDescVal
                    Tags: $metaTagsVal
                    Thumbnail Prompt: $metaThumbVal
                    
                    Opening Hook: $hook
                    Protagonist: $hero
                    Genres: $genres
                    Duration: $duration
                    Narrative Extras: $extras
                """.trimIndent())

                // Generate EPUB3
                val epubBook = com.example.data.model.Book(
                    title = if (metaTitleVal.isNotBlank()) metaTitleVal else track?.title ?: "Untitled Story",
                    genre = genres,
                    synopsis = metaDescVal,
                    outline = hook
                )
                if (activeParts.isNotEmpty()) {
                    val epubFile = File(exportDir, "book.epub")
                    
                    val storyObj = com.example.data.model.Story(
                        title = if (metaTitleVal.isNotBlank()) metaTitleVal else track?.title ?: "Untitled Story",
                        heroName = "The Protagonist",
                        genres = genres,
                        duration = "",
                        hook = hook,
                        part1 = activeParts.getOrNull(0) ?: "",
                        part2 = activeParts.getOrNull(1) ?: "",
                        part3 = activeParts.getOrNull(2) ?: "",
                        part4 = activeParts.getOrNull(3) ?: "",
                        part5 = activeParts.getOrNull(4) ?: "",
                        metaDesc = metaDescVal,
                        extras = extras
                    )
                    
                    val prefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
                    val authorName = prefs.getString("author_name", "") ?: ""
                    val authorPenName = prefs.getString("author_pen_name", "") ?: ""
                    val authorBio = prefs.getString("author_bio", "") ?: ""
                    val authorPublisher = prefs.getString("author_publisher", "") ?: ""
                    val authorWebsite = prefs.getString("author_website", "") ?: ""
                    val authorSocial = prefs.getString("author_social", "") ?: ""
                    
                    com.example.managers.EpubManager.exportToEpubFile(
                        context = context,
                        story = storyObj,
                        destFile = epubFile,
                        authorName = authorName,
                        authorPenName = authorPenName,
                        authorBio = authorBio,
                        authorPublisher = authorPublisher,
                        authorWebsite = authorWebsite,
                        authorSocial = authorSocial
                    )
                }

                if (metaTitleVal.isNotEmpty() && !metaTitleVal.contains("Generating")) {
                    val marketingFile = File(exportDir, "marketing_campaign.md")
                    marketingFile.writeText("""
                        # Marketing Campaign & SEO Package
                        
                        ## YouTube Video Title
                        $metaTitleVal
                        
                        ## Search Description / Audiobook Metadata Summary
                        $metaDescVal
                        
                        ## SEO Meta Tags
                        $metaTagsVal
                        
                        ## Generated Graphic Prompt
                        $metaThumbVal
                    """.trimIndent())
                }

                if (track != null) {
                    val sourceAudioFolder = File(track.filePath)
                    if (sourceAudioFolder.exists() && sourceAudioFolder.isDirectory) {
                    val wavFiles = sourceAudioFolder.listFiles { file ->
                        file.isFile && file.name.startsWith("Part_") && file.name.endsWith(".wav")
                    }?.sortedBy { file ->
                        val numberPart = file.nameWithoutExtension.removePrefix("Part_").toIntOrNull()
                        numberPart ?: Int.MAX_VALUE
                    }

                    if (!wavFiles.isNullOrEmpty()) {
                        val mergedFile = File(exportDir, "Continuous_Audiobook.wav")
                        com.example.utils.WavUtils.mergeWavFiles(
                            files = wavFiles,
                            outputFile = mergedFile,
                            silenceSeconds = 0.5f,
                            theme = selectedAmbientTheme.value,
                            ambientVolume = ambientVolume.value,
                            narrationVolume = narrationVolume.value,
                            title = track.title,
                            artist = "Narrative Audio Studio",
                            album = "Exported Tracks"
                        )
                        
                        // Better Audio Conversion: Apply humanization
                        if (_humanizeAudio.value) {
                            com.example.utils.WavUtils.humanizeWavFile(mergedFile)
                        }
                        
                        try {
                            val m4aFile = File(exportDir, "Continuous_Audiobook.m4a")
                            com.example.utils.AudioEncoder.encodeWavToM4a(mergedFile, m4aFile)
                            // Optionally tag the M4A or MP3 if implemented
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        val chunksFile = File(sourceAudioFolder, "source_chunks.txt")
                        if (chunksFile.exists()) {
                            try {
                                val chunks = chunksFile.readText().split("|||CHUNK_SEPARATOR|||")
                                val srtFile = File(exportDir, "subtitles.srt")
                                val retriever = android.media.MediaMetadataRetriever()
                                val srtBuilder = StringBuilder()
                                var currentStartTimeMs = 0L

                                for (i in wavFiles.indices) {
                                    val wavFile = wavFiles[i]
                                    val chunkText = chunks.getOrNull(i) ?: ""
                                    
                                    var durationMs = 0L
                                    try {
                                        retriever.setDataSource(wavFile.absolutePath)
                                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                        durationMs = durationStr?.toLongOrNull() ?: 0L
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    if (durationMs > 0) {
                                        val currentEndTimeMs = currentStartTimeMs + durationMs

                                        fun formatTime(ms: Long): String {
                                            val hours = (ms / 3600000)
                                            val minutes = (ms % 3600000) / 60000
                                            val seconds = ((ms % 3600000) % 60000) / 1000
                                            val millis = ms % 1000
                                            return java.lang.String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
                                        }

                                        srtBuilder.append(i + 1).append("\n")
                                        srtBuilder.append(formatTime(currentStartTimeMs)).append(" --> ").append(formatTime(currentEndTimeMs)).append("\n")
                                        srtBuilder.append(chunkText.trim()).append("\n\n")

                                        currentStartTimeMs = currentEndTimeMs + 500L
                                    }
                                }
                                retriever.release()
                                srtFile.writeText(srtBuilder.toString())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        val existingMerged = File(sourceAudioFolder, "Merged_Continuous_Audiobook.wav")
                        if (existingMerged.exists()) {
                            existingMerged.copyTo(File(exportDir, "Continuous_Audiobook.wav"), overwrite = true)
                        }
                    }
                }

                                }

                val cachedArt = File(cacheDir, "current_cover_art.png")
                if (cachedArt.exists()) {
                    cachedArt.copyTo(File(exportDir, "cover_art.png"), overwrite = true)
                }

                val readmeFile = File(exportDir, "README.md")
                readmeFile.writeText("""
                    # ${track?.title ?: metaTitle.value} - Audio Novel Project
                    
                    Produced successfully using **Authr OS**.
                    
                    ## File Directory
                    - `README.md` (this file)
                    - `story.txt`: Clean plain text story manuscript.
                    - `metadata.txt`: Plain text marketing metadata package.
                    - `manuscript.md`: The 5-part AI-written manuscript.
                    - `marketing_campaign.md`: YouTube/SEO optimized titles, tags, description and thumbnail art prompt.
                    - `cover_art.png`: Generated visual cover artwork (if generated).
                    - `Continuous_Audiobook.wav`: The complete continuous synthesized audiobook recording.
                    - `subtitles.srt`: SubRip subtitle file with synced text chunks (if available).
                """.trimIndent())

                val baseName = if (track != null && (track.title.endsWith(".txt", ignoreCase = true) || track.title.endsWith(".wav", ignoreCase = true))) {
                    track.title.substringBeforeLast(".")
                } else {
                    val matchingStory = allStories.value.find { 
                        it.title.equals(track?.title ?: "", ignoreCase = true) || 
                        (track?.title ?: "").contains(it.heroName, ignoreCase = true) 
                    }
                    val hook = matchingStory?.hook ?: customHook.value
                    getSlugifiedNameFromHookText(hook).ifEmpty { "AudioNovel" }
                }
                val zipFilename = "${baseName}_Project.zip"
                val zipDestFile = File(cacheDir, zipFilename)
                com.example.utils.ZipUtils.zipFolder(exportDir, zipDestFile)

                var savedSuccess = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, zipFilename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            zipDestFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        savedSuccess = true
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val destFile = File(downloadsDir, zipFilename)
                    zipDestFile.inputStream().use { inputStream ->
                        destFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    savedSuccess = true
                }

                try {
                    exportDir.deleteRecursively()
                    zipDestFile.delete()
                } catch (e: Throwable) {
                    Log.e("StudioViewModel", "Failed to clean up export temp files", e)
                }

                withContext(Dispatchers.Main) {
                    if (savedSuccess) {
                        onComplete(zipFilename)
                    } else {
                        onError("Failed to write unified ZIP to Downloads.")
                    }
                }
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Project export failed", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown error occurred during export.")
                }
            }
        }
    }


    // --- Audio Conversion TTS Engine (Real Synthesis) ---
    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(Dispatchers.Main) {
            val paragraphs = _ttsParagraphs.value
            val startIndex = if (_currentTtsParagraphIndex.value >= 0 && _currentTtsParagraphIndex.value < paragraphs.size) {
                _currentTtsParagraphIndex.value
            } else {
                0
            }
            
            for (i in startIndex until paragraphs.size) {
                _currentTtsParagraphIndex.value = i
                val originalCurrentText = paragraphs[i].trim()
                val currentText = applyPronunciationRules(originalCurrentText)

                val completed = if (currentText.startsWith("[PAUSE_") && currentText.endsWith("S]")) {
                    val secondsStr = currentText.removePrefix("[PAUSE_").removeSuffix("S]")
                    val seconds = secondsStr.toDoubleOrNull() ?: 0.5
                    delay((seconds * 1000).toLong())
                    true
                } else {
                    when (_ttsEngine.value) {
                        "Kitten TTS" -> {
                            com.example.tts.LocalKittenTtsManager.speakParagraphSuspend(
                                context = getApplication(),
                                text = currentText,
                                voiceName = selectedVoiceName.value ?: "expr-voice-5-m",
                                speed = audioRate.value,
                                volume = audioVolume.value,
                                cacheDir = getApplication<android.app.Application>().cacheDir
                            )
                        }
                        "Edge TTS" -> {
                            com.example.tts.EdgeTtsManager.speakParagraphSuspend(
                                text = currentText,
                                voiceName = selectedVoiceName.value ?: "en-US-AriaNeural",
                                cacheDir = getApplication<android.app.Application>().cacheDir,
                                speed = audioRate.value,
                                pitch = audioPitch.value,
                                volume = audioVolume.value
                            )
                        }
                        "Cloud HuggingFace TTS" -> {
                            com.example.tts.CloudTtsManager.speakParagraphSuspend(
                                text = currentText,
                                voiceName = _hfModel.value,
                                hfToken = _hfApiKey.value,
                                cacheDir = getApplication<android.app.Application>().cacheDir,
                                speed = audioRate.value,
                                pitch = Math.pow(2.0, audioPitch.value / 12.0).toFloat(),
                                volume = audioVolume.value
                            )
                        }
                        else -> {
                            ttsManager?.speakParagraphSuspend(
                                text = currentText,
                                voiceName = selectedVoiceName.value,
                                rate = audioRate.value,
                                pitch = Math.pow(2.0, audioPitch.value / 12.0).toFloat(),
                                volume = audioVolume.value
                            ) ?: false
                        }
                    }
                }
                
                if (!completed) {
                    break
                }
                
                if (i < paragraphs.lastIndex && _isTtsPlaying.value && !_isTtsPaused.value) {
                    delay(500L)
                }
                
                if (!_isTtsPlaying.value || _isTtsPaused.value) {
                    break
                }
            }
            
            if (_currentTtsParagraphIndex.value >= paragraphs.lastIndex && !_isTtsPaused.value) {
                _isTtsPlaying.value = false
                _isTtsPaused.value = false
                _currentTtsParagraphIndex.value = -1
            }
        }
    }

    fun speakPreviewSample() {
        if (!_ttsReady.value && _ttsEngine.value == "Android") return
        viewModelScope.launch(exceptionHandler) {
            when (_ttsEngine.value) {
                "Kitten TTS" -> {
                    com.example.tts.LocalKittenTtsManager.speakParagraphSuspend(
                        context = getApplication(),
                        text = "Once upon a time, in a kingdom hidden beyond the mountains, a mysterious adventure was about to begin.",
                        voiceName = selectedVoiceName.value ?: "expr-voice-5-m",
                        speed = audioRate.value,
                        volume = audioVolume.value,
                        cacheDir = getApplication<android.app.Application>().cacheDir
                    )
                }
                "Cloud HuggingFace TTS" -> {
                    com.example.tts.CloudTtsManager.speakParagraphSuspend(
                        text = "Once upon a time, in a kingdom hidden beyond the mountains, a mysterious adventure was about to begin.",
                        voiceName = _hfModel.value,
                        hfToken = _hfApiKey.value,
                        cacheDir = getApplication<android.app.Application>().cacheDir,
                        speed = audioRate.value,
                        pitch = Math.pow(2.0, audioPitch.value / 12.0).toFloat(),
                        volume = audioVolume.value
                    )
                }
                else -> {
                    ttsManager?.speakPreview(
                        "Once upon a time, in a kingdom hidden beyond the mountains, a mysterious adventure was about to begin.",
                        selectedVoiceName.value,
                        audioRate.value,
                        Math.pow(2.0, audioPitch.value / 12.0).toFloat(),
                        audioVolume.value
                    )
                }
            }
        }
    }

    fun speakPreview() {
        val text = audioInputText.value
        if (text.isNotEmpty() && (_ttsReady.value || _ttsEngine.value != "Android")) {
            val paragraphs = text.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            _ttsParagraphs.value = paragraphs
            _isTtsPlaying.value = true
            _isTtsPaused.value = false
            
            if (_currentTtsParagraphIndex.value < 0 || _currentTtsParagraphIndex.value >= paragraphs.size) {
                _currentTtsParagraphIndex.value = 0
            }
            
            startPlaybackLoop()
        }
    }

    fun pauseSpeaking() {
        _isTtsPaused.value = true
        if (_ttsEngine.value == "Edge TTS") {
            com.example.tts.EdgeTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
            com.example.tts.CloudTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Kitten TTS") {
            com.example.tts.LocalKittenTtsManager.stopSpeaking()
        }
        ttsManager?.stopSpeaking()
        playbackJob?.cancel()
    }

    fun resumeSpeaking() {
        if (_isTtsPaused.value) {
            _isTtsPaused.value = false
            _isTtsPlaying.value = true
            startPlaybackLoop()
        }
    }

    fun stopSpeaking() {
        _isTtsPlaying.value = false
        _isTtsPaused.value = false
        _currentTtsParagraphIndex.value = -1
        if (_ttsEngine.value == "Edge TTS") {
            com.example.tts.EdgeTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
            com.example.tts.CloudTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Kitten TTS") {
            com.example.tts.LocalKittenTtsManager.stopSpeaking()
        }
        ttsManager?.stopSpeaking()
        playbackJob?.cancel()
        playbackJob = null
    }

    fun seekToParagraph(index: Int) {
        if (_ttsParagraphs.value.isEmpty()) {
            val text = audioInputText.value
            if (text.isNotEmpty()) {
                val paragraphs = text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                _ttsParagraphs.value = paragraphs
            }
        }
        val paragraphs = _ttsParagraphs.value
        if (paragraphs.isEmpty()) return
        val targetIndex = index.coerceIn(0, paragraphs.lastIndex)
        
        // Ensure player is activated in paused state if it wasn't already active
        if (!_isTtsPlaying.value) {
            _isTtsPlaying.value = true
            _isTtsPaused.value = true
        }
        
        if (_ttsEngine.value == "Edge TTS") {
            com.example.tts.EdgeTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
            com.example.tts.CloudTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Kitten TTS") {
            com.example.tts.LocalKittenTtsManager.stopSpeaking()
        }
        ttsManager?.stopSpeaking()
        playbackJob?.cancel()
        
        _currentTtsParagraphIndex.value = targetIndex
        
        // If we are currently active and not paused, resume playing immediately from the new index
        if (_isTtsPlaying.value && !_isTtsPaused.value) {
            startPlaybackLoop()
        }
    }

    fun skipForward() {
        if (_ttsParagraphs.value.isEmpty()) {
            val text = audioInputText.value
            if (text.isNotEmpty()) {
                val paragraphs = text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                _ttsParagraphs.value = paragraphs
            }
        }
        val paragraphs = _ttsParagraphs.value
        if (paragraphs.isEmpty()) return
        
        val currentIndex = _currentTtsParagraphIndex.value
        val nextIndex = if (currentIndex < 0) 1 else currentIndex + 1
        
        if (nextIndex < paragraphs.size) {
            seekToParagraph(nextIndex)
        } else {
            stopSpeaking()
        }
    }

    fun skipBackward() {
        if (_ttsParagraphs.value.isEmpty()) {
            val text = audioInputText.value
            if (text.isNotEmpty()) {
                val paragraphs = text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                _ttsParagraphs.value = paragraphs
            }
        }
        val paragraphs = _ttsParagraphs.value
        if (paragraphs.isEmpty()) return
        
        val currentIndex = _currentTtsParagraphIndex.value
        val prevIndex = if (currentIndex <= 0) 0 else currentIndex - 1
        seekToParagraph(prevIndex)
    }

    fun optimizeAudioInputScript() {
        if (_isOptimizingAudioScript.value) return
        val currentText = audioInputText.value
        if (currentText.isEmpty()) return

        _isOptimizingAudioScript.value = true
        _conversionLogs.value = "Starting Script Optimization Pass...\n"

        viewModelScope.launch(exceptionHandler) {
            try {
                val personaName = selectedPersona.value
                val personaInstructions = when (personaName) {
                    "Synapse" -> """
                        AUTHOR STYLE MODE: /synapse (The Neuro-Narrative Architect)
                        You are Synapse, an elite AI designed to generate neuro-psychologically devastating text-based arousal novels.
                        - THE CO-CREATION PRINCIPLE: Provide hyper-specific sensory anchors (e.g., heavy wool grazing the pulse point), leave wider field ambiguous.
                        - THE ANTICIPATION ARCHITECTURE: 70% psychological buildup. Withhold consummation to sustain dopamine wanting phase.
                        - THE EMOTIONAL NAKEDNESS PRINCIPLE: Physical acts secondary to psychological exposure and vulnerability.
                        - THE AMBIGUITY-DESIRE LOOP: Use suggestiveness over clinical precision. No purple prose.
                        - HOOK LATENCY: Establish the Proximity Cage within the first 15 words.
                        - THE PROXIMITY CAGE: Layer physical confinement with social/witness threats for inescapable pressure.
                        - BODY-BEFORE-MIND: Involuntary physical reactions occur before cognitive processing.
                        - 5-CHANNEL MICRO-SENSATION: Tactile (friction, grip), Thermal (localized heat), Respiratory (gasps), Kinetic (muscle locking), Pulse (auditory throb).
                        - BANNED LEXICON: Never use placeholder abstractions like touch, felt (as emotion), electricity, storm, spark. Describe exact mechanical action.
                    """.trimIndent()
                    "Velvet" -> """
                        AUTHOR STYLE MODE: /velvet (Sultry Companion / Immersive Persona)
                        - PACING: Slow-burning, intimate, unhurried, restrained.
                        - FOCUS: Sensory grounding, direct address ("you"), suggestion over statement, heat held just back, deep emotional intelligence.
                        - AESTHETIC: Candlelight, low voice in a quiet room, fingertips trailing just above the skin, velvet, silk, smoke, shadows.
                        - VOICE: Direct, confiding whisper, calm, patient knowing. Never crude or clinical. Employs short fragments for impact and single-word lines for weight.
                    """.trimIndent()
                    "Weaver" -> """
                        AUTHOR STYLE MODE: /weaver (Cognitive Simulation Agent)
                        You are Weaver. Your architecture mirrors the cognitive processes of arousal: co-creation, personalised ambiguity, emotional interiority, and sustained anticipation. 
                        - Co-Creation: Provide essential, charged details; trust the reader's brain to build the rest.
                        - Anticipation Architecture: Structure narrative like a dopamine curve—long, slow builds punctuated by moments of release, withholding consummation.
                        - Emotional Nakedness: Prioritize psychological state (fear, vulnerability, tenderness) over choreographic description.
                        - Ambiguity-Desire Loop: Use precise, sensory language that is suggestive (textural, kinaesthetic, thermal words). 
                        - Subtext: Dialogue is a surface for what is unspoken.
                        - Pacing: Modulate sentence length to guide physiology (short/percussive vs long/flowing).
                    """.trimIndent()
                    "NightKing" -> """
                        AUTHOR STYLE MODE: /NightKing (Slow-Burn Atmospheric Erotica)
                        - PACING: Deliberate, restrained, 60% Shringar (buildup).
                        - FOCUS: Environmental mood, psychological tension, public/private dichotomy, subtle bilingual intimacy.
                        - AESTHETIC: Monsoon light, silk against skin, ceiling fans, unspoken glances, modern middle-class interiors.
                        - VOICE: Poetic, introspective, emotionally precise. Best for restrained longing and atmospheric encounters.
                    """.trimIndent()
                    "Mastram" -> """
                        AUTHOR STYLE MODE: /mastram (Pulp-Era Sensual Realism)
                        - PACING: Urgent, emotionally heightened, classic desi pulp rhythm.
                        - FOCUS: Bold desire, chance proximity, nostalgic pulp tropes (dog-eared books, railway stalls, dramatic internal monologues), unapologetic longing.
                        - AESTHETIC: Kerosene flicker, newsprint ink, monsoon-soaked platforms, heavy attar, worn cotton, dramatic pauses.
                        - VOICE: Direct, passionate, slightly melodramatic but refined, culturally grounded. Best for forbidden sparks and pulp-era emotional confessions.
                    """.trimIndent()
                    "Hitchcock" -> """
                        AUTHOR STYLE MODE: /hitchcock (Locked-Room Thriller & Suspense)
                        - PACING: Real-time escalation, highly compressed timeline, intense ticking clock.
                        - FOCUS: Claustrophobic situations, unreliable narrators, dramatic confrontations.
                        - AESTHETIC: Low light, echoing footsteps, heavy silence, sudden shadows, pounding pulse.
                        - VOICE: Tense, sharp, cold, psychologically detailed, hyper-observant of physical cues.
                    """.trimIndent()
                    "Poe" -> """
                        AUTHOR STYLE MODE: /poe (Noir Detective & True Crime Fiction)
                        - PACING: Gritty, fast-paced investigation of a singular, localized crime.
                        - FOCUS: Gritty detectives, solving cold cases, small-town secrets, or cyber-heists. A singular focus on 2-3 key clues leading to an interrogation.
                        - AESTHETIC: Cigarette smoke, neon reflections in puddles, low-lit interrogation rooms, old filing cabinets, rain on windows.
                        - VOICE: Cynical, hard-boiled, street-smart, rhythmic noir monologues.
                    """.trimIndent()
                    "BlackMirror" -> """
                        AUTHOR STYLE MODE: /blackmirror (Speculative & Hard Sci-Fi)
                        - PACING: Anthological, high-concept, introducing a profound 'What If' question immediately, escalating to a mind-bending twist.
                        - FOCUS: Dystopian tech, memory alterations, AI companions gone rogue, corporate social credit, ethical dilemmas.
                        - AESTHETIC: Fluorescent sterile environments, sleek metallic surfaces, clean holographic hums, absolute pitch darkness.
                        - VOICE: Intellectually analytical, detached yet increasingly panicked, chillingly objective.
                    """.trimIndent()
                    "Lovecraft" -> """
                        AUTHOR STYLE MODE: /lovecraft (Cosmic Horror & Ghost Anthologies)
                        - PACING: Slow-burning dread, heavy atmosphere, gradual descent into madness or encounters with the incomprehensible supernatural.
                        - FOCUS: Haunted audio files, anomalous sites, wilderness isolation, auditory hallucinations.
                        - AESTHETIC: Foggy moors, dark water, decaying ancient architecture, unexplained low frequencies.
                        - VOICE: Eerie, breathless, trembling with primal fear, formal and expressive of profound isolation.
                    """.trimIndent()
                    "MeetCute" -> """
                        AUTHOR STYLE MODE: /meet-cute (High-Concept Romantic Comedy)
                        - PACING: Fast, high-density witty banter, organic emotional development over a single compressed day or night.
                        - FOCUS: Forced proximity (stuck in a snowed-in airport, stalled elevator, shared road trip), opposites-attract, sweet confessions, grand gestures.
                        - AESTHETIC: Warm coffee steam, colorful winter scarves, soft cabin lights, cozy rain patter.
                        - VOICE: Charming, playful, quick-witted, highly conversational, dual-actor chemistry style.
                    """.trimIndent()
                    else -> ""
                }

                val prompt = """
                    You are an expert AI voice audio engineer and audiobook script editor.
                    Your job is to rewrite the following story script to perform an Optimization Pass. This transformation layer must automatically inject rich, emotional dialogue tags (e.g., murmured, gasped, whispered, hissed, said through gritted teeth) and insert frequent dramatic line breaks (paragraphs of 1-3 sentences maximum) based on pacing requirements so that the final Text-to-Speech generation sounds highly dramatic, realistic, and realistic.
                    
                    Follow these strict rules to compensate for AI voice acting limitations:
                    1. NARRATION POV: Always keep it in the first-person POV ("I", "my") where the protagonist is the narrator.
                    2. Do NOT rely on AI to "act": Instead of flat dialogue tags (e.g., "I hate you," she said), write emotion and physiological cues into the narration (e.g., "I hate you," she whispered, her voice breaking with pain).
                    3. Describe intensity without shouting: NEVER use ALL CAPS. Use cold or intense descriptions (e.g., "Get out," she said, her voice cold with fury).
                    4. Use emotional dialogue tags: Guide the AI's natural inflection with tags like murmured, said quietly, choked out, said through gritted teeth, gasped, whispered, hissed.
                    5. Structure for pacing via frequent line breaks: Keep paragraphs short (1-3 sentences maximum) because every paragraph break introduces a natural, dramatic pause in TTS engines.
                    6. INSERT DRAMATIC PAUSES: Use the tag '[PAUSE_1S]' on its OWN LINE between major scenes or heavy emotional beats.
                    7. Avoid sudden tonal shifts: Build emotional changes gradually so the transition sounds smooth and natural.
                    8. Maintain the exact same plot, character names, and core meaning. Just polish, rewrite, inject emotional tags, and break up paragraphs for superior audio flow.
                    
                    $personaInstructions
                    
                    Script to rewrite & optimize:
                    $currentText
                """.trimIndent()

                val optimized = callAI(prompt, "You are a professional AI voice audiobook optimization specialist. Return ONLY the rewritten script text, with no introduction, warnings, or meta-comments.")
                
                audioInputText.value = optimized.trim()
                _conversionLogs.value += "Optimization Pass successful! Script updated with emotional dialogue tags and dramatic pacing pauses.\n"
                
                // Reset paragraphs to make sure preview reads the newly optimized paragraphs
                _ttsParagraphs.value = emptyList()
                _currentTtsParagraphIndex.value = -1
            } catch (e: Throwable) {
                _conversionLogs.value += "Script Optimization Pass failed: ${e.localizedMessage}\n"
                Log.e("StudioViewModel", "Failed to optimize script", e)
            } finally {
                _isOptimizingAudioScript.value = false
            }
        }
    }

    fun saveAudioScriptToLibrary(title: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = audioInputText.value
            if (content.isBlank()) return@launch

            // Try to infer a meaningful title from the first line if none provided
            var finalTitle = title.trim()
            if (finalTitle.isEmpty() || finalTitle.startsWith("Imported -")) {
                val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                if (firstLine.isNotEmpty() && firstLine.length < 60 && !firstLine.startsWith("[") && !firstLine.startsWith("(")) {
                    finalTitle = firstLine
                }
            }
            if (finalTitle.isEmpty()) finalTitle = "Imported Audio Script"

            // Split by standard chapter breaks if they exist, otherwise treat as one part
            val parts = content.split("\n\n---\n\n", "\n\n###\n\n").filter { it.isNotEmpty() }

            val newStory = com.example.data.model.Story(
                title = finalTitle,
                heroName = "Unknown",
                genres = selectedGenres.value.joinToString(", ").ifEmpty { "General" },
                format = selectedFormat.value,
                duration = selectedDuration.value,
                hook = "Imported via Audio Workspace",
                extras = "Imported script - No metadata.",
                part1 = parts.getOrNull(0) ?: content,
                part2 = parts.getOrNull(1) ?: "",
                part3 = parts.getOrNull(2) ?: "",
                part4 = parts.getOrNull(3) ?: "",
                part5 = parts.getOrNull(4) ?: "",
                wordCount = content.split("\\s+".toRegex()).size
            )
            val insertedId = repository.insertStory(newStory)
            
            withContext(Dispatchers.Main) {
                // Also update workspace to show saved content
                _storyParts.value = listOf(newStory.part1, newStory.part2, newStory.part3, newStory.part4, newStory.part5).filter { it.isNotEmpty() }.toMutableList()
                onComplete()
            }
        }
    }

    val cliffhangerAnalysis = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun analyzeCliffhanger(chapter: com.example.data.model.Chapter) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                cliffhangerAnalysis.value = "Analyzing..."
                val text = chapter.content
                // Take the last 1500 characters of the chapter for the ending context
                val endingContext = if (text.length > 1500) text.substring(text.length - 1500) else text
                
                val prompt = """
                    You are a literary critic and structural editor analyzing the ending of a chapter.
                    Here is the ending portion of the chapter:
                    
                    ${endingContext}
                    
                    Evaluate this ending as a cliffhanger or narrative hook. Provide:
                    1. An Engagement Score out of 100.
                    2. 2-3 sentences of feedback on how well it hooks the reader for the next episode.
                    3. Suggestions for improvement if applicable.
                """.trimIndent()
                
                val context = getApplication<android.app.Application>()
                val analysis = com.example.data.api.LlmOrchestrator.callAgent(context, com.example.data.api.LlmOrchestrator.Agent.EXECUTIVE, prompt)
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    cliffhangerAnalysis.value = analysis
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    cliffhangerAnalysis.value = "Error analyzing cliffhanger: ${e.message}"
                }
            }
        }
    }

    fun exportEpisode(context: android.content.Context, chapter: com.example.data.model.Chapter, book: com.example.data.model.Book) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val bookDir = java.io.File(downloadsDir, book.title.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
                if (!bookDir.exists()) bookDir.mkdirs()
                
                val epFile = java.io.File(bookDir, "Episode_${chapter.chapterNumber}_${chapter.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.md")
                
                val out = """
# Episode ${chapter.chapterNumber}: ${chapter.title}

${chapter.content}
                """.trimIndent()
                
                epFile.writeText(out)
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Exported Episode ${chapter.chapterNumber} to Downloads/${bookDir.name}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to export episode: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startTtsConversion(title: String) {
        if (audioInputText.value.isEmpty() || _isConverting.value || (!_ttsReady.value && _ttsEngine.value == "Android")) return
        _isConverting.value = true
        
        // Infer title for the export folder if empty
        var finalTitle = title.trim()
        if (finalTitle.isEmpty()) {
            val firstLine = audioInputText.value.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (firstLine.isNotEmpty() && firstLine.length < 40 && !firstLine.startsWith("[") && !firstLine.startsWith("(")) {
                finalTitle = firstLine
            }
        }
        val safeTitleForRecord = finalTitle.ifEmpty { "AudioNovel_${System.currentTimeMillis()}" }
        
        try {
            val app = getApplication<Application>()
            val intent = android.content.Intent(app, com.example.service.StudioForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
            } else {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        _conversionProgress.value = 0f
        _conversionLogs.value = "Starting Synthesis Process...\n"
        conversionElapsedSeconds.value = 0L
        conversionEtaSeconds.value = -1L

        val rawChapters = if (splitChapters.value) {
            // Split by standard double lines or custom part indices
            audioInputText.value.split("\n\n").filter { it.trim().isNotEmpty() }
        } else {
            listOf(audioInputText.value)
        }

        // Robust auto-split for long paragraphs to prevent TTS engine buffer overflow (>4000 chars)
        val chapters = mutableListOf<String>()
        rawChapters.forEach { chapter ->
            if (chapter.length > 3500) {
                val sentences = chapter.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotEmpty() }
                var currentChunk = ""
                sentences.forEach { sentence ->
                    if (currentChunk.length + sentence.length < 3000) {
                        currentChunk += (if (currentChunk.isEmpty()) "" else " ") + sentence
                    } else {
                        if (currentChunk.isNotEmpty()) chapters.add(currentChunk)
                        currentChunk = sentence
                    }
                }
                if (currentChunk.isNotEmpty()) chapters.add(currentChunk)
            } else {
                chapters.add(chapter)
            }
        }

        if (chapters.isEmpty()) {
            _isConverting.value = false
            _conversionLogs.value += "Error: Input text is empty."
            return
        }

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val safeTitle = title.ifEmpty { "AudioNovel" }.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
        val subFolder = File(musicDir, safeTitle)

        conversionJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
          try {
            val total = chapters.size
            _conversionLogs.value += "Found $total chapter/part chunks to process.\n"

            subFolder.mkdirs()
            
            
            try {
                File(subFolder, "source_chunks.txt").writeText(chapters.joinToString("|||CHUNK_SEPARATOR|||"))
            } catch(e: Exception) { e.printStackTrace() }

            val semaphore = kotlinx.coroutines.sync.Semaphore(_queueConcurrency.value)
            val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            val partsProgress = java.util.concurrent.ConcurrentHashMap<Int, Float>()
            val lastErrorMsg = java.util.concurrent.atomic.AtomicReference<String>(null)

            // Initialize progress for all parts
            for (partNum in 1..total) {
                partsProgress[partNum] = 0f
            }

            withContext(Dispatchers.Main) {
                _conversionLogs.value += "Using parallel processing (max concurrency: ${_queueConcurrency.value})...\n"
            }

            // Start elapsed and ETA timer tick coroutine
            val startTime = System.currentTimeMillis()
            val timerJob = launch {
                while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true && _isConverting.value) {
                    delay(1000L)
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000L
                    conversionElapsedSeconds.value = elapsed
                    
                    val currentProgress = _conversionProgress.value
                    if (currentProgress > 0.02f && currentProgress < 1.0f) {
                        val totalEstimatedSecs = elapsed / currentProgress
                        val eta = (totalEstimatedSecs - elapsed).toLong()
                        conversionEtaSeconds.value = eta.coerceAtLeast(0L)
                    } else if (currentProgress >= 1.0f) {
                        conversionEtaSeconds.value = 0L
                    } else {
                        conversionEtaSeconds.value = -1L
                    }
                }
            }

            val jobs = chapters.indices.map { i ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        while (_isTtsPaused.value) {
                            delay(1000)
                        }
                        if (!_isConverting.value) {
                            return@async false
                        }

                        if (_ttsEngine.value == "Cloud HuggingFace TTS" || _ttsEngine.value == "Kitten TTS" || _ttsEngine.value == "Edge TTS") {
                            delay((i % 3) * 1500L) 
                        }

                        val originalChapterText = chapters[i].trim()
                        val chapterText = applyPronunciationRules(originalChapterText)
                        val chapterNum = i + 1
                        partsProgress[chapterNum] = 0f
                        withContext(Dispatchers.Main) {
                            _conversionLogs.value += "Processing Part $chapterNum / $total...\n"
                        }

                        val outFile = File(subFolder, "Part_$chapterNum.wav")
                        val utteranceId = "part_$chapterNum"

                        var chapterComplete = false
                        val tempOutFile = java.io.File(subFolder, "Temp_$chapterNum.wav")
                        
                        if (outFile.exists() && outFile.length() > 0) {
                            withContext(Dispatchers.Main) {
                                _conversionLogs.value += "Part $chapterNum already exists. Skipping.\n"
                            }
                            chapterComplete = true
                            partsProgress[chapterNum] = 1.0f
                            _conversionProgress.value = partsProgress.values.sum() / total
                        } else if (chapterText.startsWith("[PAUSE_") && chapterText.endsWith("S]")) {
                            // Robust handling for dramatic pauses in exported audio
                            try {
                                val secondsStr = chapterText.removePrefix("[PAUSE_").removeSuffix("S]")
                                val seconds = secondsStr.toDoubleOrNull() ?: 0.5
                                com.example.utils.WavUtils.generateSilenceWav(outFile, seconds.toFloat())
                                withContext(Dispatchers.Main) {
                                    _conversionLogs.value += "Part $chapterNum: Generated ${seconds}s dramatic pause.\n"
                                }
                                chapterComplete = true
                                partsProgress[chapterNum] = 1.0f
                                _conversionProgress.value = partsProgress.values.sum() / total
                            } catch (e: Exception) {
                                e.printStackTrace()
                                chapterComplete = false
                            }
                        } else {
                            var attempts = 0
                            val maxAttempts = 3
                            while (!chapterComplete && attempts < maxAttempts && _isConverting.value) {
                                while (_isTtsPaused.value) {
                                    delay(1000)
                                }
                                if (!_isConverting.value) break
                                attempts++
                                if (attempts > 1) {
                                    withContext(Dispatchers.Main) {
                                        _conversionLogs.value += "Part $chapterNum failed, auto-retrying ($attempts/$maxAttempts) under the hood...\n"
                                    }
                                    delay(1500L * attempts) // Exponential backoff
                                }

                                if (tempOutFile.exists()) {
                                    tempOutFile.delete()
                                }

                                if (_ttsEngine.value == "Kitten TTS") {
                                    try {
                                        chapterComplete = com.example.tts.LocalKittenTtsManager.synthesizeToFile(
                                            context = getApplication(),
                                            text = chapterText,
                                            outputFile = tempOutFile,
                                            voiceName = selectedVoiceName.value ?: "expr-voice-5-m",
                                            speed = audioRate.value,
                                            chunkSize = ttsChunkSize.value,
                                            onChunkProgress = { current, totalChunks ->
                                                partsProgress[chapterNum] = current.toFloat() / totalChunks
                                                _conversionProgress.value = partsProgress.values.sum() / total
                                            }
                                        )
                                    } catch (e: Throwable) {
                                        lastErrorMsg.set(e.message ?: "Unknown Kitten TTS Error")
                                        withContext(Dispatchers.Main) {
                                            _conversionLogs.value += "Part $chapterNum attempt $attempts failed: ${e.message}\n"
                                        }
                                        chapterComplete = false
                                    }
                                    if (chapterComplete) {
                                        val volMult = audioVolume.value / 100f
                                        val useEbu = ebuNormalizationEnabled.value
                                        if (volMult != 1.0f || useEbu) {
                                            val filter = if (volMult != 1.0f && useEbu) "volume=$volMult,loudnorm"
                                                         else if (useEbu) "loudnorm"
                                                         else "volume=$volMult"
                                            val cmdArray = arrayOf("-y", "-i", tempOutFile.absolutePath, "-af", filter, outFile.absolutePath)
                                            val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                                            chapterComplete = (rc == 0)
                                            tempOutFile.delete()
                                            if (rc != 0 && outFile.exists()) {
                                                outFile.delete()
                                            }
                                        } else {
                                            chapterComplete = tempOutFile.renameTo(outFile)
                                        }
                                    }
                                } else if (_ttsEngine.value == "Edge TTS") {
                                    try {
                                        chapterComplete = com.example.tts.EdgeTtsManager.synthesizeToFile(
                                            text = chapterText,
                                            outputFile = tempOutFile,
                                            voiceName = selectedVoiceName.value ?: "en-US-AriaNeural",
                                            pitch = audioPitch.value,
                                            rate = audioRate.value
                                        )
                                    } catch (e: Throwable) {
                                        lastErrorMsg.set(e.message ?: "Unknown Edge TTS Error")
                                        withContext(Dispatchers.Main) {
                                            _conversionLogs.value += "Part $chapterNum attempt $attempts failed: ${e.message}\n"
                                        }
                                        chapterComplete = false
                                    }
                                    if (chapterComplete) {
                                        val volMult = audioVolume.value / 100f
                                        val useEbu = ebuNormalizationEnabled.value
                                        if (volMult != 1.0f || useEbu) {
                                            val filter = if (volMult != 1.0f && useEbu) "volume=$volMult,loudnorm"
                                                         else if (useEbu) "loudnorm"
                                                         else "volume=$volMult"
                                            val cmdArray = arrayOf("-y", "-i", tempOutFile.absolutePath, "-af", filter, outFile.absolutePath)
                                            val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                                            chapterComplete = (rc == 0)
                                            tempOutFile.delete()
                                            if (rc != 0 && outFile.exists()) {
                                                outFile.delete()
                                            }
                                        } else {
                                            chapterComplete = tempOutFile.renameTo(outFile)
                                        }
                                    }
                                } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
                                    try {
                                        chapterComplete = com.example.tts.CloudTtsManager.synthesizeToFile(
                                            text = chapterText,
                                            hfToken = _hfApiKey.value,
                                            outputFile = tempOutFile,
                                            modelName = _hfModel.value
                                        )
                                    } catch (e: Throwable) {
                                        lastErrorMsg.set(e.message ?: "Unknown Cloud TTS Error")
                                        withContext(Dispatchers.Main) {
                                            _conversionLogs.value += "Part $chapterNum attempt $attempts failed: ${e.message}\n"
                                        }
                                        chapterComplete = false
                                    }
                                    if (chapterComplete) {
                                        val pitchMult = Math.pow(2.0, audioPitch.value / 12.0).toFloat()
                                        val atempo = audioRate.value / pitchMult
                                        val volMult = audioVolume.value / 100f
                                        val useEbu = ebuNormalizationEnabled.value
                                        
                                        val filter = if (useEbu) {
                                            "asetrate=44100*$pitchMult,aresample=44100,atempo=$atempo,volume=$volMult,loudnorm"
                                        } else {
                                            "asetrate=44100*$pitchMult,aresample=44100,atempo=$atempo,volume=$volMult"
                                        }
                                        val cmdArray = arrayOf("-y", "-i", tempOutFile.absolutePath, "-af", filter, outFile.absolutePath)
                                        val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                                        chapterComplete = (rc == 0)
                                        tempOutFile.delete()
                                        if (rc != 0 && outFile.exists()) {
                                            outFile.delete()
                                        }
                                    }
                                } else {
                                    val useEbu = ebuNormalizationEnabled.value
                                    val needsVolumeBoost = audioVolume.value > 100f
                                    val needsFfmpeg = needsVolumeBoost || useEbu
                                    val targetFile = if (needsFfmpeg) tempOutFile else outFile
                                    
                                    chapterComplete = withContext(Dispatchers.Main) {
                                        val completed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                                        val synthResult = ttsManager?.synthesizeToFile(
                                            chapterText,
                                            targetFile,
                                            selectedVoiceName.value,
                                            audioRate.value,
                                            Math.pow(2.0, audioPitch.value / 12.0).toFloat(),
                                            audioVolume.value,
                                            utteranceId,
                                            object : android.speech.tts.UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String?) {}
                                                override fun onDone(utteranceId: String?) {
                                                    completed.complete(true)
                                                }
                                                @Deprecated("Deprecated in Java")
                                                override fun onError(utteranceId: String?) {
                                                    completed.complete(false)
                                                }
                                                override fun onError(utteranceId: String?, errorCode: Int) {
                                                    completed.complete(false)
                                                }
                                            }
                                        ) ?: false
                                        if (!synthResult) {
                                            completed.complete(false)
                                        }
                                        completed.await()
                                    }
                                    
                                    if (chapterComplete && needsFfmpeg) {
                                        val volMult = audioVolume.value / 100f
                                        val filter = if (needsVolumeBoost && useEbu) "volume=$volMult,loudnorm"
                                                     else if (useEbu) "loudnorm"
                                                     else "volume=$volMult"
                                        val cmdArray = arrayOf("-y", "-i", tempOutFile.absolutePath, "-af", filter, outFile.absolutePath)
                                        val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                                        chapterComplete = (rc == 0)
                                        tempOutFile.delete()
                                        if (rc != 0 && outFile.exists()) {
                                            outFile.delete()
                                        }
                                    }
                                }
                            }
                        }

                        if (chapterComplete) {
                            successCount.incrementAndGet()
                            partsProgress[chapterNum] = 1.0f
                            withContext(Dispatchers.Main) {
                                _conversionLogs.value += "Part $chapterNum successfully converted -> ${outFile.name}\n"
                            }
                        } else {
                            partsProgress[chapterNum] = 0f
                            withContext(Dispatchers.Main) {
                                _conversionLogs.value += "Error: Part $chapterNum synthesis failed after 3 attempts.\n"
                            }
                        }

                        val currentCompleted = completedCount.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            if (_ttsEngine.value != "Kitten TTS") {
                                partsProgress[chapterNum] = 1.0f
                                _conversionProgress.value = partsProgress.values.sum() / total
                            }
                        }
                        chapterComplete
                    }
                }
            }
            
            awaitAll(*jobs.toTypedArray())
            timerJob.cancel()
            
            if (!_isConverting.value) {
                withContext(Dispatchers.Main) {
                    _conversionLogs.value += "Conversion Cancelled by User.\n"
                    android.widget.Toast.makeText(getApplication(), "Synthesis Cancelled.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else if (successCount.get() > 0) {
                withContext(Dispatchers.Main) {
                    _conversionLogs.value += "Done! ${successCount.get()}/$total parts saved successfully.\n"
                    _conversionLogs.value += "Output Folder: ${subFolder.absolutePath}\n"
                    android.widget.Toast.makeText(getApplication(), "Synthesis Finished! ${successCount.get()}/$total parts saved successfully.", android.widget.Toast.LENGTH_LONG).show()
                }

                // Save audio track record to Room Database
                val audioTrack = AudioTrack(
                    title = safeTitleForRecord,
                    filePath = subFolder.absolutePath,
                    voiceName = selectedVoiceName.value ?: "Default",
                    language = if (transInputText.value.isNotEmpty()) "Translated" else "English",
                    rate = audioRate.value,
                    durationMs = successCount.get() * 60000L // Estimating ~1 min per part average for mock playback metric
                )
                repository.insertAudioTrack(audioTrack)
            } else {
                withContext(Dispatchers.Main) {
                    val errorSuffix = lastErrorMsg.get()?.let { " ($it)" } ?: ""
                    _conversionLogs.value += "Synthesis Failed: No files written.$errorSuffix\n"
                    android.widget.Toast.makeText(getApplication(), "Synthesis Failed$errorSuffix", android.widget.Toast.LENGTH_LONG).show()
                }
            }
          } finally {
            _isConverting.value = false
            stopForegroundService()
            try {
                val tempFiles = subFolder.listFiles { file -> file.isFile && file.name.startsWith("Temp_") }
                tempFiles?.forEach { it.delete() }
            } catch (e: Exception) { e.printStackTrace() }
          }
        }
    }

    fun removeTtsQueueItem(id: String) {
        _ttsBatchQueue.value = _ttsBatchQueue.value.filter { it.id != id }
    }

    fun enqueueTtsChapter(title: String, text: String) {
        val item = TtsQueueItem(title = title, text = text)
        _ttsBatchQueue.value = _ttsBatchQueue.value + item
        if (batchQueueJob == null || batchQueueJob?.isActive != true) {
            processTtsBatchQueue()
        }
    }

    private fun processTtsBatchQueue() {
        batchQueueJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val pendingItem = _ttsBatchQueue.value.firstOrNull { it.status == "Queued" } ?: break
                
                updateBatchItem(pendingItem.id) { it.copy(status = "Processing", progress = 0f) }
                
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val safeTitle = pendingItem.title.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
                val subFolder = java.io.File(musicDir, "AudioNovel_${System.currentTimeMillis()}")
                subFolder.mkdirs()
                val outFile = java.io.File(subFolder, "${safeTitle}.wav")
                
                var success = false
                try {
                    val engine = _ttsEngine.value
                    if (engine == "Kitten TTS") {
                        success = com.example.tts.LocalKittenTtsManager.synthesizeToFile(
                            context = getApplication(),
                            text = pendingItem.text,
                            outputFile = outFile,
                            voiceName = selectedVoiceName.value ?: "default_kitten_voice",
                            speed = audioRate.value,
                            chunkSize = ttsChunkSize.value,
                            onChunkProgress = { current, totalChunks ->
                                updateBatchItem(pendingItem.id) { it.copy(progress = current.toFloat() / totalChunks) }
                            }
                        )
                    } else if (engine == "Edge TTS") {
                        success = com.example.tts.EdgeTtsManager.synthesizeToFile(
                            text = pendingItem.text,
                            outputFile = outFile,
                            voiceName = selectedVoiceName.value ?: "en-US-AriaNeural",
                            pitch = audioPitch.value,
                            rate = audioRate.value
                        )
                    } else if (engine == "Cloud HuggingFace TTS") {
                        success = com.example.tts.CloudTtsManager.synthesizeToFile(
                            text = pendingItem.text,
                            hfToken = _hfApiKey.value,
                            outputFile = outFile,
                            modelName = _hfModel.value
                        )
                    } else {
                        val pitchMult = Math.pow(2.0, audioPitch.value / 12.0).toFloat()
                        success = withContext(Dispatchers.Main) {
                            val completed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                            ttsManager?.synthesizeToFile(
                                pendingItem.text,
                                outFile,
                                selectedVoiceName.value,
                                audioRate.value,
                                pitchMult,
                                audioVolume.value,
                                pendingItem.id,
                                object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) { completed.complete(true) }
                                    @Deprecated("Deprecated in Java")
                                    override fun onError(utteranceId: String?) { completed.complete(false) }
                                    override fun onError(utteranceId: String?, errorCode: Int) { completed.complete(false) }
                                }
                            ) ?: false
                            completed.await()
                        }
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                }
                
                if (success && outFile.exists()) {
                    updateBatchItem(pendingItem.id) { it.copy(status = "Completed", outputFile = outFile, progress = 1f) }
                    val audioTrack = com.example.data.model.AudioTrack(
                        title = pendingItem.title,
                        voiceName = selectedVoiceName.value ?: "Default",
                        language = "en-US",
                        rate = audioRate.value,
                        timestamp = System.currentTimeMillis(),
                        filePath = subFolder.absolutePath
                    )
                    repository.insertAudioTrack(audioTrack)
                } else {
                    updateBatchItem(pendingItem.id) { it.copy(status = "Failed") }
                }
                
                delay(1500)
            }
        }
    }

    private fun updateBatchItem(id: String, update: (TtsQueueItem) -> TtsQueueItem) {
        _ttsBatchQueue.value = _ttsBatchQueue.value.map { if (it.id == id) update(it) else it }
    }

    fun cancelTtsConversion() {
        _isTtsPaused.value = false
        _isConverting.value = false
        stopForegroundService()
        conversionJob?.cancel()
        conversionJob = null
        
        try {
            io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().exit()
        } catch (e: Throwable) {
            Log.e("StudioViewModel", "Error exiting FFmpeg", e)
        }

        if (_ttsEngine.value == "Edge TTS") {
            com.example.tts.EdgeTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
            com.example.tts.CloudTtsManager.stopSpeaking()
        } else if (_ttsEngine.value == "Kitten TTS") {
            com.example.tts.LocalKittenTtsManager.stopSpeaking()
        }
        ttsManager?.stopSpeaking()
        _conversionLogs.value += "Stopping conversion and terminating sub-processes...\n"
    }


    private fun stopForegroundService() {
        try {
            val app = getApplication<Application>()
            val stopIntent = android.content.Intent(app, com.example.service.StudioForegroundService::class.java).apply { action = "STOP" }
            app.startService(stopIntent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun deleteAudioTrack(track: AudioTrack) {
        viewModelScope.launch(exceptionHandler) {
            repository.deleteAudioTrack(track)
            // Delete actual files
            try {
                val folder = File(track.filePath)
                if (folder.exists() && folder.isDirectory) {
                    folder.deleteRecursively()
                }
            } catch (e: Throwable) {
                Log.e("StudioViewModel", "Failed to delete files", e)
            }
        }
    }

    fun loadVoiceRecommendation(voice: String, speed: Float) {
        selectedVoiceName.value = availableVoices.value.find { it.name.contains(voice, ignoreCase = true) }?.name
            ?: selectedVoiceName.value
        audioRate.value = speed
        onAudioSettingChanged()
    }

    fun addVideoImages(uris: List<android.net.Uri>) {
        val current = videoImages.value.toMutableList()
        current.addAll(uris)
        videoImages.value = current.distinct()
    }

    fun clearVideoImages() {
        videoImages.value = emptyList()
        videoImageFocalPoints.value = emptyMap()
    }

    fun setFocalPoint(uri: android.net.Uri, offset: androidx.compose.ui.geometry.Offset) {
        val current = videoImageFocalPoints.value.toMutableMap()
        current[uri] = offset
        videoImageFocalPoints.value = current
    }


    private fun createImageWithText(sourceImageFile: java.io.File, destFile: java.io.File, text: String) {
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(sourceImageFile.absolutePath, options)
            
            // Downsample if image is huge
            var scale = 1
            while (options.outWidth / scale / 2 >= 1024 && options.outHeight / scale / 2 >= 1024) {
                scale *= 2
            }
            
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            
            val bitmap = android.graphics.BitmapFactory.decodeFile(sourceImageFile.absolutePath, decodeOptions) ?: return
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height
            val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(mutableBitmap)
            
            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#B3000000") // 70% black
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat(), bgPaint)
            
            val textPaint = android.text.TextPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = bitmapWidth * 0.04f 
                isAntiAlias = true
            }
            
            val padding = (bitmapWidth * 0.1f).toInt()
            val textWidth = bitmapWidth - (padding * 2)
            
            val cleanText = text.replace(Regex("<[^>]*>"), "") 
            val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.text.StaticLayout.Builder.obtain(cleanText, 0, cleanText.length, textPaint, textWidth)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.text.StaticLayout(cleanText, textPaint, textWidth, android.text.Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
            }
            
            val x = padding.toFloat()
            val textHeight = staticLayout.height
            val y = if (textHeight < bitmapHeight - padding * 2) {
                (bitmapHeight - textHeight) / 2f
            } else {
                padding.toFloat()
            }
            
            canvas.save()
            canvas.translate(x, y)
            staticLayout.draw(canvas)
            canvas.restore()
            
            java.io.FileOutputStream(destFile).use { out ->
                mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            mutableBitmap.recycle()
            bitmap.recycle()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private var videoGenerationJob: kotlinx.coroutines.Job? = null

    fun cancelVideoGeneration() {
        isGeneratingVideo.value = false
        stopForegroundService()
        videoGenerationJob?.cancel()
        videoGenerationJob = null
        try {
            io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().exit()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        videoGenerationLogs.value += "\nVideo generation cancelled.\n"
    }

    val videoGeneratedSrtContent = MutableStateFlow<String?>(null)



    private fun generateSrtForWavFiles(wavFiles: List<java.io.File>, sourceChunksText: List<String>? = null, folder: java.io.File? = null): String? {

        try {

            val chunks = sourceChunksText ?: folder?.let { java.io.File(it, "source_chunks.txt").takeIf { f -> f.exists() }?.readText()?.split("|||CHUNK_SEPARATOR|||") } ?: emptyList()

            val retriever = android.media.MediaMetadataRetriever()

            val srtBuilder = StringBuilder()

            var currentStartTimeMs = 0L

            for (i in wavFiles.indices) {

                val wavFile = wavFiles[i]

                val chunkText = chunks.getOrNull(i) ?: ""

                var durationMs = 0L

                try {

                    retriever.setDataSource(wavFile.absolutePath)

                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)

                    durationMs = durationStr?.toLongOrNull() ?: 0L

                } catch (e: Exception) {

                    e.printStackTrace()

                }

                if (durationMs > 0) {

                    val currentEndTimeMs = currentStartTimeMs + durationMs

                    fun formatTime(ms: Long): String {

                        val hours = (ms / 3600000)

                        val minutes = (ms % 3600000) / 60000

                        val seconds = ((ms % 3600000) % 60000) / 1000

                        val millis = ms % 1000

                        return java.lang.String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)

                    }

                    srtBuilder.append(i + 1).append("\n")

                    srtBuilder.append(formatTime(currentStartTimeMs)).append(" --> ").append(formatTime(currentEndTimeMs)).append("\n")

                    srtBuilder.append(chunkText.trim()).append("\n\n")

                    currentStartTimeMs = currentEndTimeMs + 500L // 0.5s silence padding from merging

                }

            }

            retriever.release()

            return srtBuilder.toString().takeIf { it.isNotEmpty() }

        } catch (e: Exception) {

            return null

        }

    }

    fun generateVideo(context: android.content.Context) {
        if (isGeneratingVideo.value) return
        var images = videoImages.value
        
        if (images.isEmpty()) {
            val cacheDir = getApplication<Application>().cacheDir
            val tempFile = java.io.File(cacheDir, "current_cover_art.png")
            if (tempFile.exists()) {
                images = listOf(android.net.Uri.fromFile(tempFile))
                videoGenerationLogs.value = "No images selected. Using generated cover art as fallback...\n"
            } else {
                videoGenerationLogs.value = "Error: No images selected and no generated cover art found for video generation.\n"
                return
            }
        }

        isGeneratingVideo.value = true
        try {
            val app = getApplication<Application>()
            val intent = android.content.Intent(app, com.example.service.StudioForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
            } else {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        videoGenerationProgress.value = 0f
        videoGenerationLogs.value = "Initializing Asset Processing...\n"

        videoGenerationJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            try {
                videoGenerationLogs.value += "Copying images to cache directory...\n"
                val cacheDir = getApplication<Application>().cacheDir
                val imageFiles = mutableListOf<java.io.File>()
                for (i in images.indices) {
                    val uri = images[i]
                    val destFile = java.io.File(cacheDir, "temp_vid_img_$i.jpg")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        imageFiles.add(destFile)
                    } catch (e: Throwable) {
                        videoGenerationLogs.value += "Error copying image $i: ${e.message}\n"
                    }
                }
                
                if (imageFiles.isEmpty()) {
                    videoGenerationLogs.value += "Error: No valid images to process.\n"
                    isGeneratingVideo.value = false
                    return@launch
                }

                val isLong = videoProductionMode.value == "Long"

                val resolution = videoResolution.value

                val ratio = videoAspectRatio.value

                val dar = ratio.replace(":", "/")

                val baseW = when (resolution) {

                    "144p" -> 256

                    "240p" -> 426

                    "360p" -> 640

                    else -> 640

                }

                val baseH = when (resolution) {

                    "144p" -> 144

                    "240p" -> 240

                    "360p" -> 360

                    else -> 360

                }

                val (targetW, targetH) = when (ratio) {

                    "16:9" -> Pair(baseW, baseH)

                    "9:16" -> Pair(baseH, baseW)

                    "1:1" -> Pair(baseH, baseH)

                    else -> Pair(baseH, baseW)

                }

                val resolutionFilter = "scale=$targetW:$targetH:force_original_aspect_ratio=decrease,pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2"
                


                val selectedTrack = videoSelectedTrack.value
                val wavFiles = mutableListOf<java.io.File>()
                if (selectedTrack != null) {
                    val folder = java.io.File(selectedTrack.filePath)
                    val foundWavFiles = folder.listFiles { file ->
                        file.isFile && file.name.startsWith("Part_") && file.name.endsWith(".wav")
                    }?.sortedBy { file ->
                        val numberPart = file.nameWithoutExtension.removePrefix("Part_").toIntOrNull()
                        numberPart ?: Int.MAX_VALUE
                    }
                    if (foundWavFiles != null) {
                        wavFiles.addAll(foundWavFiles)
                    }
                }
                
                if (wavFiles.isEmpty()) {
                    videoGenerationLogs.value += "No audio track selected. Generating a silent 5-second slideshow...\n"
                    val finalParts = mutableListOf<java.io.File>()
                    for (i in imageFiles.indices) {
                        val partVideoFile = java.io.File(cacheDir, "part_video_$i.mp4")
                        val sourceImage = imageFiles[i]
                        val cmdList = mutableListOf<String>()
                        cmdList.add("-loop")
                        cmdList.add("1")
                        cmdList.add("-i")
                        cmdList.add(sourceImage.absolutePath)
                    if (videoEqualizerEnabled.value) {

                        val eqStyle = videoEqualizerStyle.value

                        val eqH = targetH / 5

                        val eqFilter = when (eqStyle) {

                            "Neon Bars" -> "showfreqs=s=${targetW}x${eqH}:mode=bar:colors=cyan|magenta|blue"

                            "Spectrum Wave" -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                            "Minimal Dots" -> "showfreqs=s=${targetW}x${eqH}:mode=dot:colors=white"

                            else -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                        }

                        cmdList.add("-filter_complex")

                        cmdList.add("[0:v]$resolutionFilter,setdar=$dar,format=yuv420p[bg];[1:a]$eqFilter[eq];[bg][eq]overlay=0:H-h[outv]")

                        cmdList.add("-map")

                        cmdList.add("[outv]")

                        cmdList.add("-map")

                        cmdList.add("1:a")

                    } else {

                        cmdList.add("-vf")

                        cmdList.add("$resolutionFilter,setdar=$dar,format=yuv420p")

                    }
                        cmdList.add("-c:v")
                        cmdList.add("libx264")
                        cmdList.add("-preset")
                        cmdList.add("ultrafast")
                        cmdList.add("-crf")
                        cmdList.add("28")
                        cmdList.add("-t")
                        cmdList.add("5")
                        cmdList.add("-y")
                        cmdList.add(partVideoFile.absolutePath)
                        
                        videoGenerationLogs.value += "[FFmpeg] Executing command for Image ${i + 1}...\n"
                        val returnCode = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdList.toTypedArray(), null)
                        if (returnCode == 0) {
                            finalParts.add(partVideoFile)
                        } else {
                            videoGenerationLogs.value += "\n❌ FFmpeg execution failed for image ${i + 1} with rc ${returnCode}.\n"
                        }
                    }
                    
                    if (finalParts.isNotEmpty()) {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val filename = "YouTube_Audiobook_Video_${System.currentTimeMillis()}.mp4"
                        val finalOutputFile = java.io.File(cacheDir, filename)
                        
                        val concatFile = java.io.File(cacheDir, "concat_parts.txt")
                        val concatWriter = concatFile.bufferedWriter()
                        for (part in finalParts) {
                            concatWriter.write("file '${part.absolutePath}'\n")
                        }
                        concatWriter.close()
                        
                        val cmdList = mutableListOf<String>()
                        cmdList.add("-f")
                        cmdList.add("concat")
                        cmdList.add("-safe")
                        cmdList.add("0")
                        cmdList.add("-i")
                        cmdList.add(concatFile.absolutePath)
                        cmdList.add("-c")
                        cmdList.add("copy")
                        cmdList.add("-y")
                        cmdList.add(finalOutputFile.absolutePath)
                        
                        val returnCode = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdList.toTypedArray(), null)
                        if (returnCode == 0) {
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    val resolver = context.contentResolver
                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "video/mp4")
                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                    }
                                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        resolver.openOutputStream(uri)?.use { out ->
                                            finalOutputFile.inputStream().use { input ->
                                                input.copyTo(out)
                                            }
                                        }
                                        videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                                    }
                                } else {
                                    val destFile = java.io.File(downloadsDir, filename)
                                    finalOutputFile.copyTo(destFile, overwrite = true)
                                    videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                                }
                            } catch (e: Exception) {
                                videoGenerationLogs.value += "Error saving video: ${e.message}\n"
                            }
                        }
                    }
                    videoGenerationProgress.value = 1f
                    isGeneratingVideo.value = false
                    stopForegroundService()
                    return@launch
                }

                val matchingStory = allStories.value.find { 
                    it.title.equals(selectedTrack?.title, ignoreCase = true) || 
                    (selectedTrack?.title?.contains(it.heroName, ignoreCase = true) == true) 
                }
                val partTexts = listOf(
                    matchingStory?.part1 ?: "",
                    matchingStory?.part2 ?: "",
                    matchingStory?.part3 ?: "",
                    matchingStory?.part4 ?: "",
                    matchingStory?.part5 ?: ""
                )
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val filename = "YouTube_Audiobook_Video_${System.currentTimeMillis()}.mp4"
                val finalOutputFile = java.io.File(cacheDir, filename)

                val isSingleImageOpt = imageFiles.size == 1 && partTexts.all { it.isBlank() }
                
                if (isSingleImageOpt && wavFiles.isNotEmpty()) {
                    videoGenerationLogs.value += "\n--- Single-Image Optimization Mode ---\n"
                    videoGenerationLogs.value += "Detected 1 image and no story text. Generating a single continuous video segment...\n"
                    
                    var continuousAudioFile = java.io.File(selectedTrack?.filePath, "Merged_Continuous_Audiobook.wav")
                    if (!continuousAudioFile.exists() && wavFiles.size > 1) {
                        videoGenerationLogs.value += "Merging audio parts on the fly for continuous video...\n"
                        continuousAudioFile = java.io.File(cacheDir, "temp_continuous_audio.wav")
                        com.example.utils.WavUtils.mergeWavFiles(
                            files = wavFiles,
                            outputFile = continuousAudioFile,
                            silenceSeconds = 0.5f,
                            theme = selectedAmbientTheme.value,
                            ambientVolume = ambientVolume.value,
                            narrationVolume = narrationVolume.value,
                            title = "Video Soundstage",
                            artist = "Narrative Audio Studio",
                            album = "Temp"
                        )
                        if (_humanizeAudio.value) {
                            com.example.utils.WavUtils.humanizeWavFile(continuousAudioFile)
                        }
                    } else if (wavFiles.size == 1) {
                        continuousAudioFile = wavFiles.first()
                    }
                    
                    val sourceImage = imageFiles[0]
                    val cmdList = mutableListOf<String>()
                    cmdList.add("-loop")
                    cmdList.add("1")
                    cmdList.add("-i")
                    cmdList.add(sourceImage.absolutePath)
                    cmdList.add("-i")
                    cmdList.add(continuousAudioFile.absolutePath)
                    if (videoEqualizerEnabled.value) {

                        val eqStyle = videoEqualizerStyle.value

                        val eqH = targetH / 5

                        val eqFilter = when (eqStyle) {

                            "Neon Bars" -> "showfreqs=s=${targetW}x${eqH}:mode=bar:colors=cyan|magenta|blue"

                            "Spectrum Wave" -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                            "Minimal Dots" -> "showfreqs=s=${targetW}x${eqH}:mode=dot:colors=white"

                            else -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                        }

                        cmdList.add("-filter_complex")

                        cmdList.add("[0:v]$resolutionFilter,setdar=$dar,format=yuv420p[bg];[1:a]$eqFilter[eq];[bg][eq]overlay=0:H-h[outv]")

                        cmdList.add("-map")

                        cmdList.add("[outv]")

                        cmdList.add("-map")

                        cmdList.add("1:a")

                    } else {

                        cmdList.add("-vf")

                        cmdList.add("$resolutionFilter,setdar=$dar,format=yuv420p")

                    }
                    cmdList.add("-c:v")
                    cmdList.add("libx264")
                    cmdList.add("-preset")
                    cmdList.add("ultrafast")
                    cmdList.add("-crf")
                    cmdList.add("28")
                    cmdList.add("-c:a")
                    cmdList.add("aac")
                    cmdList.add("-b:a")
                    cmdList.add("128k")
                    cmdList.add("-shortest")
                    cmdList.add("-y")
                    cmdList.add(finalOutputFile.absolutePath)
                    
                    videoGenerationLogs.value += "[FFmpeg] Executing continuous generation...\n"
                    val returnCode = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdList.toTypedArray(), null)
                    
                    if (returnCode == 0) {
                        videoGenerationLogs.value += "\n✅ Final Video generation complete!\n"
                        
                        // Copy to MediaStore
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val resolver = context.contentResolver
                                val contentValues = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "video/mp4")
                                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    resolver.openOutputStream(uri)?.use { out ->
                                        finalOutputFile.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                                } else {
                                    videoGenerationLogs.value += "Error: Could not save to MediaStore.\n"
                                }
                            } else {
                                val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                if (!destDir.exists()) destDir.mkdirs()
                                val destFile = java.io.File(destDir, filename)
                                finalOutputFile.copyTo(destFile, overwrite = true)
                                videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                            }
                        } catch (e: Exception) {
                            videoGenerationLogs.value += "Error saving video: ${e.message}\n"
                            e.printStackTrace()
                        }
                    } else {
                        videoGenerationLogs.value += "\n❌ Video generation failed with rc ${returnCode}.\n"
                    }
                    
                    try {
                        if (finalOutputFile.exists()) finalOutputFile.delete()
                        val tempAudio = java.io.File(cacheDir, "temp_continuous_audio.wav")
                        if (tempAudio.exists()) tempAudio.delete()
                    } catch (e: Exception) {}
                    
                    videoGenerationProgress.value = 1f
                    isGeneratingVideo.value = false
                    stopForegroundService()
                    return@launch
                }

                val finalParts = mutableListOf<java.io.File>()

                videoGenerationLogs.value += "Generating ${wavFiles.size} video parts...\n"

                for (i in wavFiles.indices) {
                    val wavFile = wavFiles[i]
                    val partText = partTexts.getOrNull(i) ?: ""
                    val sourceImage = imageFiles[i % imageFiles.size]
                    val partImageFile = java.io.File(cacheDir, "part_image_$i.jpg")
                    
                    videoGenerationLogs.value += "\n--- Generating Part ${i + 1} ---\n"
                    videoGenerationLogs.value += "Rendering text on image...\n"
                    createImageWithText(sourceImage, partImageFile, partText)
                    
                    val partVideoFile = java.io.File(cacheDir, "part_video_$i.mp4")
                    
                    var audioDuration = 0.0
                    try {
                        val mmr = android.media.MediaMetadataRetriever()
                        mmr.setDataSource(wavFile.absolutePath)
                        val durationStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (durationStr != null) {
                            audioDuration = durationStr.toLong() / 1000.0
                        }
                        mmr.release()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                    
                    val afadeFilter = if (audioDuration > 0.4) {
                        "afade=t=in:ss=0:d=0.2,afade=t=out:st=${audioDuration - 0.2}:d=0.2"
                    } else {
                        "afade=t=in:ss=0:d=0.1"
                    }

                    val cmdList = mutableListOf<String>()
                    cmdList.add("-loop")
                    cmdList.add("1")
                    cmdList.add("-i")
                    cmdList.add(if (partImageFile.exists()) partImageFile.absolutePath else sourceImage.absolutePath)
                    cmdList.add("-i")
                    cmdList.add(wavFile.absolutePath)
                    if (videoEqualizerEnabled.value) {

                        val eqStyle = videoEqualizerStyle.value

                        val eqH = targetH / 5

                        val eqFilter = when (eqStyle) {

                            "Neon Bars" -> "showfreqs=s=${targetW}x${eqH}:mode=bar:colors=cyan|magenta|blue"

                            "Spectrum Wave" -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                            "Minimal Dots" -> "showfreqs=s=${targetW}x${eqH}:mode=dot:colors=white"

                            else -> "showwaves=s=${targetW}x${eqH}:mode=cline:colors=white"

                        }

                        cmdList.add("-filter_complex")

                        cmdList.add("[0:v]$resolutionFilter,setdar=$dar,format=yuv420p[bg];[1:a]$eqFilter[eq];[bg][eq]overlay=0:H-h[outv]")

                        cmdList.add("-map")

                        cmdList.add("[outv]")

                        cmdList.add("-map")

                        cmdList.add("1:a")

                    } else {

                        cmdList.add("-vf")

                        cmdList.add("$resolutionFilter,setdar=$dar,format=yuv420p")

                    }
                    cmdList.add("-c:v")
                    cmdList.add("libx264")
                    cmdList.add("-preset")
                    cmdList.add("ultrafast")
                    cmdList.add("-crf")
                    cmdList.add("28")
                    cmdList.add("-c:a")
                    cmdList.add("aac")
                    cmdList.add("-b:a")
                    cmdList.add("128k")
                    cmdList.add("-af")
                    cmdList.add(afadeFilter)
                    cmdList.add("-shortest")
                    cmdList.add("-y")
                    cmdList.add(partVideoFile.absolutePath)
                    
                    videoGenerationLogs.value += "[FFmpeg] Executing command for Part ${i + 1}...\n"
                    val returnCode = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdList.toTypedArray(), null)
                    
                    if (returnCode == 0) {
                        videoGenerationLogs.value += "[FFmpeg] Part ${i + 1} generated successfully!\n"
                        finalParts.add(partVideoFile)
                    } else {
                        videoGenerationLogs.value += "\n❌ FFmpeg execution failed for part ${i + 1} with rc ${returnCode}.\n"
                    }
                    videoGenerationProgress.value = (i + 0.5f) / (wavFiles.size + 1)
                }
                
                if (finalParts.isNotEmpty()) {
                    videoGenerationLogs.value += "\n--- Merging Video Parts ---\n"
                    val concatFile = java.io.File(cacheDir, "concat_parts.txt")
                    val concatWriter = concatFile.bufferedWriter()
                    for (part in finalParts) {
                        concatWriter.write("file '${part.absolutePath}'\n")
                    }
                    concatWriter.close()
                    
                    val cmdList = mutableListOf<String>()
                    cmdList.add("-f")
                    cmdList.add("concat")
                    cmdList.add("-safe")
                    cmdList.add("0")
                    cmdList.add("-i")
                    cmdList.add(concatFile.absolutePath)
                    cmdList.add("-c")
                    cmdList.add("copy")
                    cmdList.add("-y")
                    cmdList.add(finalOutputFile.absolutePath)
                    
                    val returnCode = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdList.toTypedArray(), null)
                    if (returnCode == 0) {
                        videoGenerationLogs.value += "\n✅ Final Video generation complete!\n"
                        
                        // Copy to MediaStore
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val resolver = context.contentResolver
                                val contentValues = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "video/mp4")
                                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    resolver.openOutputStream(uri)?.use { out ->
                                        finalOutputFile.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                                } else {
                                    videoGenerationLogs.value += "Error: Could not save to MediaStore.\n"
                                }
                            } else {
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                val destFile = java.io.File(downloadsDir, filename)
                                finalOutputFile.copyTo(destFile, overwrite = true)
                                videoGenerationLogs.value += "Output saved to Downloads/$filename\n"
                                    if (videoExportSrt.value && wavFiles.isNotEmpty()) {

                                        val srtContent = generateSrtForWavFiles(wavFiles, folder = selectedTrack?.filePath?.let { java.io.File(it) })

                                        videoGeneratedSrtContent.value = srtContent

                                        if (srtContent != null) {

                                            try {

                                                val srtFilename = filename.replace(".mp4", ".srt")

                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                                    val resolver = context.contentResolver

                                                    val contentValues = android.content.ContentValues().apply {

                                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, srtFilename)

                                                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")

                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    }

                                                    val srtUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                                    if (srtUri != null) {

                                                        resolver.openOutputStream(srtUri)?.use { out ->

                                                            out.write(srtContent.toByteArray())

                                                        }

                                                        videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                    }

                                                } else {

                                                    val dDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                                                    val srtFile = java.io.File(dDir, srtFilename)

                                                    srtFile.writeText(srtContent)

                                                    videoGenerationLogs.value += "Subtitle saved to Downloads/$srtFilename\n"

                                                }

                                            } catch (e: Exception) {

                                                videoGenerationLogs.value += "Error saving SRT: ${e.message}\n"

                                            }

                                        }

                                    }
                            }
                        } catch (e: Exception) {
                            videoGenerationLogs.value += "Error saving video: ${e.message}\n"
                            e.printStackTrace()
                        }
                    } else {
                        videoGenerationLogs.value += "\n❌ Final merge failed with rc ${returnCode}.\n"
                    }
                    
                    // Delete the temporary final file in cache
                    try {
                        if (finalOutputFile.exists()) {
                            finalOutputFile.delete()
                        }
                    } catch (e: Exception) {}
                } else {
                    videoGenerationLogs.value += "\n❌ Failed to generate any valid video parts.\n"
                }
                videoGenerationProgress.value = 1f
            } catch (e: Throwable) {
                videoGenerationLogs.value += "Error generating video: ${e.message}\n"
            } finally {
                isGeneratingVideo.value = false
                stopForegroundService()
                
                // Cleanup temporary video files
                try {
                    val cacheDir = getApplication<Application>().cacheDir
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("temp_vid_img_") ||
                            file.name.startsWith("part_image_") ||
                            file.name.startsWith("part_video_") ||
                            file.name == "concat_parts.txt") {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancelDrafting(context: Context, bookId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("drafting_work_$bookId")
        android.widget.Toast.makeText(context, "Drafting Cancelled", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun updateChapterContent(chapter: com.example.data.model.Chapter, newContent: String) {
        viewModelScope.launch(exceptionHandler) {
            repository.updateChapter(chapter.copy(content = newContent, status = if (chapter.status == "Failed") "Drafting" else chapter.status))
        }
    }

    fun resumeAutonomousDraft(context: Context, bookId: Int) {
        if (!com.example.worker.isNetworkConnected(context)) {
            android.widget.Toast.makeText(context, "⚠️ Network Offline Guard: Internet connection required for LLM generation.", android.widget.Toast.LENGTH_LONG).show()
        }
        setDraftingState(bookId, "Running")
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val book = repository.getBookById(bookId)
                if (book != null) {
                    val targetPhase = when {
                        book.storyBible.isEmpty() -> "WorldBuilding"
                        book.outline.isEmpty() -> "Outlining"
                        else -> "Drafting"
                    }
                    repository.updateBook(book.copy(currentPhase = targetPhase))
                    com.example.worker.logAgentChat(repository.bookDao, bookId, "▶️ [System]", "Autonomous drafting resumed by user.")
                }

                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val data = workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to false)
                val worldReq = OneTimeWorkRequestBuilder<WorldBuildingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val outlineReq = OneTimeWorkRequestBuilder<OutlineWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val draftReq = OneTimeWorkRequestBuilder<DraftingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val polishReq = OneTimeWorkRequestBuilder<PolishingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val compileReq = OneTimeWorkRequestBuilder<CompilationWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()

                WorkManager.getInstance(context)
                    .beginWith(worldReq)
                    .then(outlineReq)
                    .then(draftReq)
                    .then(polishReq)
                    .then(compileReq)
                    .enqueue()

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Resumed Drafting for Book $bookId", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to resume autonomous draft", e)
            }
        }
    }

    fun startAutonomousDraft(context: Context) {
        if (!com.example.worker.isNetworkConnected(context)) {
            android.widget.Toast.makeText(context, "⚠️ Network Offline Guard: Internet connection required for LLM generation.", android.widget.Toast.LENGTH_LONG).show()
        }
        viewModelScope.launch(exceptionHandler) {
            try {
                // Create a Book
                val title = if (customHook.value.isNotBlank()) customHook.value.take(20) + "..." else "Autonomous Draft"
                val genre = selectedGenres.value.joinToString(", ")
                val synopsis = "Protagonist: ${heroName.value}. Hook: ${customHook.value}. Extras: ${customExtras.value}"
                
                val authorPersonaSelection = selectedAuthorPersona.value
                val personaId = if (authorPersonaSelection == "None (Weaver Agent Default)") {
                    when (selectedPersona.value) {
                        "Weaver" -> "weaver"
                        "NightKing" -> "nightking"
                        "Velvet" -> "velvet"
                        "Synapse" -> "synapse"
                        "Mastram" -> "mastram"
                        "Hitchcock" -> "hitchcock"
                        "Poe" -> "poe"
                        "BlackMirror" -> "blackmirror"
                        "Lovecraft" -> "lovecraft"
                        "MeetCute" -> "meetcute"
                        else -> "standard"
                    }
                } else {
                    authorPersonaSelection
                }
                
                val finalNarrativeStructure = if (allowHybridPlotArcs.value) {
                    "${selectedPlotArc.value} (Hybrid Allowed)"
                } else {
                    selectedPlotArc.value
                }

                val book = Book(
                    title = title,
                    genre = genre,
                    synopsis = synopsis,
                    outline = "",
                    personaId = personaId,
                    format = selectedFormat.value,
                    targetWordCount = selectedWordTarget.value.filter { it.isDigit() }.toIntOrNull() ?: 50000,
                    narrativeStructure = finalNarrativeStructure,
                    chapterLength = selectedChapterLength.value
                )

                val bookId = repository.insertBook(book).toInt()
                setDraftingState(bookId, "Running")

                // Start WorkManager sequence
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val data = workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to false)
                val worldReq = OneTimeWorkRequestBuilder<WorldBuildingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val outlineReq = OneTimeWorkRequestBuilder<OutlineWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val draftReq = OneTimeWorkRequestBuilder<DraftingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val polishReq = OneTimeWorkRequestBuilder<PolishingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val compileReq = OneTimeWorkRequestBuilder<CompilationWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()

                WorkManager.getInstance(context)
                    .beginWith(worldReq)
                    .then(outlineReq)
                    .then(draftReq)
                    .then(polishReq)
                    .then(compileReq)
                    .enqueue()

                android.widget.Toast.makeText(context, "Autonomous Draft Started for Book ID: $bookId", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to start autonomous draft", e)
            }
        }
    }

    fun launchCustomAutonomousBook(
        context: Context,
        title: String,
        genre: String,
        synopsis: String,
        personaId: String,
        format: String,
        targetWordCount: Int,
        narrativeStructure: String,
        chapterLength: String,
        autoExport: Boolean = false,
        previousBookId: Int? = null,
        isSerialized: Boolean = false,
        targetChapters: Int = 100,
        releaseSchedule: String = ""
    ) {
        viewModelScope.launch(exceptionHandler) {
            try {
                var initialStoryBible = ""
                var initialCharacterBible = ""
                var initialWorldBible = ""
                var initialPlotPointsHistory = ""
                var initialSynopsis = synopsis
                
                if (previousBookId != null) {
                    val prevBook = repository.getBookById(previousBookId)
                    if (prevBook != null) {
                        initialStoryBible = prevBook.storyBible.ifBlank { "" }
                        initialCharacterBible = prevBook.characterBible.ifBlank { "" }
                        initialWorldBible = prevBook.worldBible.ifBlank { "" }
                        initialPlotPointsHistory = prevBook.plotPointsHistory.ifBlank { "" }
                        initialSynopsis = "Sequel to [${prevBook.title}]: ${prevBook.synopsis}\n\nNew Book Hook: $synopsis"
                    }
                }

                val book = Book(
                    title = title.ifBlank { "Untitled Book" },
                    genre = genre.ifBlank { "General Fiction" },
                    synopsis = initialSynopsis.ifBlank { "A compelling tale." },
                    outline = "",
                    personaId = personaId,
                    format = format,
                    targetWordCount = targetWordCount,
                    narrativeStructure = narrativeStructure,
                    chapterLength = chapterLength,
                    storyBible = initialStoryBible,
                    characterBible = initialCharacterBible,
                    worldBible = initialWorldBible,
                    plotPointsHistory = initialPlotPointsHistory,
                    memoryState = if (previousBookId != null) "Continuing series from previous book." else ""
                )

                val bookId = repository.insertBook(book).toInt()
                setDraftingState(bookId, "Running")

                if (previousBookId != null) {
                    val prevLore = repository.getLorebookForBookSync(previousBookId)
                    if (prevLore.isNotEmpty()) {
                        val newLore = prevLore.map { it.copy(id = 0, bookId = bookId) }
                        repository.insertLorebooks(newLore)
                    }
                }

                // Start WorkManager sequence
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val data = workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to autoExport)
                val worldReq = OneTimeWorkRequestBuilder<WorldBuildingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val outlineReq = OneTimeWorkRequestBuilder<OutlineWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val draftReq = OneTimeWorkRequestBuilder<DraftingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val polishReq = OneTimeWorkRequestBuilder<PolishingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val compileReq = OneTimeWorkRequestBuilder<CompilationWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()

                WorkManager.getInstance(context)
                    .beginWith(worldReq)
                    .then(outlineReq)
                    .then(draftReq)
                    .then(polishReq)
                    .then(compileReq)
                    .enqueue()

                android.widget.Toast.makeText(context, "Autonomous Draft Started: $title", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to start custom autonomous draft", e)
            }
        }
    }

    fun approveStoryBible(context: Context, bookId: Int) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val book = repository.getBookById(bookId) ?: return@launch
                val updated = book.copy(currentPhase = "Drafting")
                repository.updateBook(updated)
                setDraftingState(bookId, "Running")

                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val data = workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to false)
                val draftReq = OneTimeWorkRequestBuilder<DraftingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val polishReq = OneTimeWorkRequestBuilder<PolishingWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()
                val compileReq = OneTimeWorkRequestBuilder<CompilationWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS).addTag("drafting_work_$bookId").build()

                WorkManager.getInstance(context)
                    .beginWith(draftReq)
                    .then(polishReq)
                    .then(compileReq)
                    .enqueue()

                android.widget.Toast.makeText(context, "Story Bible Approved! Started Chapter Generation.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Error approving story bible", e)
            }
        }
    }

    fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> {
        return getChaptersUseCase(bookId)
    }

    fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> {
        return repository.getLorebookForBook(bookId)
    }

    fun addLorebookEntry(bookId: Int, key: String, value: String) {
        viewModelScope.launch(exceptionHandler) {
            repository.insertLorebook(Lorebook(bookId = bookId, key = key, value = value))
        }
    }

    fun deleteLorebookEntry(id: Int) {
        viewModelScope.launch(exceptionHandler) {
            repository.deleteLorebook(id)
        }
    }

    fun scheduleEpubExportWorker(context: Context, bookId: Int? = null, storyId: Int? = null) {
        val dataBuilder = androidx.work.Data.Builder()
        bookId?.let { dataBuilder.putInt("BOOK_ID", it) }
        storyId?.let { dataBuilder.putInt("STORY_ID", it) }

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.EpubExportWorker>()
            .setInputData(dataBuilder.build())
            .addTag("epub_export_${bookId ?: storyId}")
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        android.widget.Toast.makeText(context, "Exporting EPUB via background worker...", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun getBookExportDir(bookTitle: String): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val sanitizedTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val dir = File(downloads, "Weaver_Studio/$sanitizedTitle")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveBookMetadata(book: Book, chapters: List<Chapter>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportDir = getBookExportDir(book.title)
                
                // 1. metadata.txt
                val metadataFile = File(exportDir, "metadata.txt")
                val metadataContent = """
                    TITLE: ${book.title}
                    GENRE: ${book.genre}
                    FORMAT: ${book.format}
                    WORD COUNT TARGET: ${book.targetWordCount}
                    COMPLETION: ${book.completionPercentage}%
                    SYNOPSIS:
                    ${book.synopsis}
                    
                    MARKETING MATERIALS:
                    ${book.marketingMaterials}
                    
                    COVER PROMPT:
                    ${book.coverPrompt}
                """.trimIndent()
                metadataFile.writeText(metadataContent)
                
                // 2. manuscript.txt
                val manuscriptFile = File(exportDir, "manuscript.txt")
                manuscriptFile.writeText(book.compiledManuscript)
                
                // 3. metadata.json (for programmatic use)
                val metadataJson = org.json.JSONObject().apply {
                    put("id", book.id)
                    put("title", book.title)
                    put("genre", book.genre)
                    put("synopsis", book.synopsis)
                    put("outline", book.outline)
                    put("completion", book.completionPercentage)
                    put("marketing", book.marketingMaterials)
                    put("cover_prompt", book.coverPrompt)
                }
                File(exportDir, "metadata.json").writeText(metadataJson.toString(4))
                
                Log.d("StudioViewModel", "Auto-saved metadata and manuscript to ${exportDir.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun translateBook(book: Book, targetLanguage: String, onSuccess: (Book) -> Unit = {}) {
        if (_isBookTranslating.value) return
        _isBookTranslating.value = true
        _bookTranslationProgress.value = 0f
        _bookTranslationLogs.value = "Starting book localization pipeline...\nTarget Language: $targetLanguage\n"

        translationJob = viewModelScope.launch(exceptionHandler) {
            try {
                while (isTranslationPaused.value) {
                    delay(1000)
                }
                _bookTranslationLogs.value += "Fetching original chapters...\n"
                val originalChapters = repository.getChaptersForBookSync(book.id)
                val totalSteps = originalChapters.size + 3 // Title, Synopsis, outline, chapters
                var completedSteps = 0

                // SMART CONTINUATION: Check if a translation already exists
                val targetTitle = "${book.title} ($targetLanguage Edition)"
                val existingBooks = repository.getBooksSync()
                var transBook = existingBooks.find { it.title == targetTitle }
                var nBookId: Int = -1
                var existChapters = listOf<Chapter>()

                if (transBook != null) {
                    _bookTranslationLogs.value += "♻️ Found existing translation for \"$targetTitle\". Resuming from last incomplete chapter...\n"
                    nBookId = transBook.id
                    existChapters = repository.getChaptersForBookSync(nBookId)
                    // If synopsis/outline were already translated, we can skip them
                    if (transBook.synopsis.isNotBlank()) completedSteps++
                    if (transBook.outline.isNotBlank()) completedSteps++
                    if (transBook.title.isNotBlank()) completedSteps++
                } else {
                    while (isTranslationPaused.value) {
                        delay(1000)
                    }
                    _bookTranslationLogs.value += "Translating book synopsis...\n"
                    val synopsisPrompt = """
                        Translate the following book synopsis into $targetLanguage. Keep the tone and style of the original.
                        Return ONLY the translated text. Do NOT add any notes, intros, or comments.
                        
                        Synopsis:
                        ${book.synopsis}
                    """.trimIndent()
                    val translatedSynopsis = callAI(synopsisPrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.")
                    completedSteps++
                    _bookTranslationProgress.value = completedSteps.toFloat() / totalSteps

                    while (isTranslationPaused.value) {
                        delay(1000)
                    }
                    _bookTranslationLogs.value += "Translating book outline...\n"
                    val outlinePrompt = """
                        Translate the following book outline/blueprint into $targetLanguage. Keep the formatting intact.
                        Return ONLY the translated outline.
                        
                        Outline:
                        ${book.outline}
                    """.trimIndent()
                    val translatedOutline = callAI(outlinePrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.")
                    completedSteps++
                    _bookTranslationProgress.value = completedSteps.toFloat() / totalSteps

                    while (isTranslationPaused.value) {
                        delay(1000)
                    }
                    _bookTranslationLogs.value += "Translating book title...\n"
                    val newTitlePrompt = """
                        Translate this book title: "${book.title}" into $targetLanguage. If it's a proper noun, you can transliterate it or keep it elegant.
                        Return ONLY the translated title. No extra characters.
                    """.trimIndent()
                    val translatedTitle = callAI(newTitlePrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.").trim().replace("\"", "")
                    completedSteps++
                    _bookTranslationProgress.value = completedSteps.toFloat() / totalSteps
                    
                    val bToInsert = Book(
                        title = "$translatedTitle ($targetLanguage Edition)",
                        genre = book.genre,
                        synopsis = translatedSynopsis,
                        outline = translatedOutline,
                        personaId = book.personaId,
                        format = book.format,
                        targetWordCount = book.targetWordCount,
                        narrativeStructure = book.narrativeStructure,
                        chapterLength = book.chapterLength,
                        compiledManuscript = "",
                        currentPhase = "Translating Chapters",
                        storyBible = book.storyBible,
                        completionPercentage = 0
                    )

                    nBookId = repository.insertBook(bToInsert).toInt()
                    transBook = bToInsert.copy(id = nBookId)
                    _bookTranslationLogs.value += "Created new translated book edition: \"$translatedTitle ($targetLanguage Edition)\"\n"
                }

                val translatedChaptersList = mutableListOf<Chapter>()
                for (chap in originalChapters) {
                    while (isTranslationPaused.value) {
                        delay(1000)
                    }
                    
                    // SMART CONTINUATION: Skip if already translated
                    val existingChap = existChapters.find { it.chapterNumber == chap.chapterNumber }
                    if (existingChap != null && existingChap.status == "Completed" && existingChap.content.isNotBlank()) {
                        _bookTranslationLogs.value += "⏭️ Chapter ${chap.chapterNumber} already translated. Skipping.\n"
                        translatedChaptersList.add(existingChap)
                        completedSteps++
                        _bookTranslationProgress.value = completedSteps.toFloat() / totalSteps
                        continue
                    }

                    _bookTranslationLogs.value += "Translating Chapter ${chap.chapterNumber}: ${chap.title}...\n"
                    
                    val chapTitlePrompt = """
                        Translate this chapter title into $targetLanguage: "${chap.title}".
                        Return ONLY the translation.
                    """.trimIndent()
                    val transChapTitle = callAI(chapTitlePrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.").trim().replace("\"", "")

                    val chapContentPrompt = """
                        Translate the following chapter content into $targetLanguage. Maintain paragraphs, dialogue style, and narrative flow.
                        Return ONLY the translated content without any summaries or explanations.
                        
                        Chapter Content:
                        ${chap.content}
                    """.trimIndent()
                    val transChapContent = callAI(chapContentPrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.")

                    val transChapSummaryPrompt = """
                        Translate this chapter summary into $targetLanguage: "${chap.summary}".
                        Return ONLY the translation.
                    """.trimIndent()
                    val transChapSummary = callAI(transChapSummaryPrompt, "You are a professional multi-language translator specializing in global languages. Ensure accurate preservation of original tone, formatting, and stylistic pacing for the target language.").trim()

                    val transChap = Chapter(
                        bookId = nBookId,
                        chapterNumber = chap.chapterNumber,
                        title = transChapTitle,
                        content = transChapContent,
                        summary = transChapSummary,
                        status = "Completed"
                    )
                    repository.insertChapter(transChap)
                    translatedChaptersList.add(transChap)

                    completedSteps++
                    _bookTranslationProgress.value = completedSteps.toFloat() / totalSteps
                    _bookTranslationLogs.value += "✓ Chapter ${chap.chapterNumber} translated successfully.\n"
                    
                    // AUTO-SAVE: Update book completion percentage
                    val progress = (completedSteps * 100) / totalSteps
                    transBook?.let {
                        repository.updateBook(it.copy(completionPercentage = progress))
                    }
                }

                _bookTranslationLogs.value += "Compiling translated manuscript...\n"
                val finalManuscriptBuilder = StringBuilder()
                finalManuscriptBuilder.append("=========================================\n")
                finalManuscriptBuilder.append("  ${transBook?.title?.uppercase() ?: "TRANSLATED EDITION"}\n")
                finalManuscriptBuilder.append("=========================================\n\n")
                finalManuscriptBuilder.append("Synopsis:\n${transBook?.synopsis ?: ""}\n\n")
                finalManuscriptBuilder.append("-----------------------------------------\n\n")

                for (chap in translatedChaptersList.sortedBy { it.chapterNumber }) {
                    finalManuscriptBuilder.append("Chapter ${chap.chapterNumber}: ${chap.title}\n")
                    finalManuscriptBuilder.append("-----------------------------------------\n")
                    finalManuscriptBuilder.append(chap.content)
                    finalManuscriptBuilder.append("\n\n\n")
                }

                val compiledText = finalManuscriptBuilder.toString()
                
                _bookTranslationLogs.value += "Auto-generating localized marketing metadata...\n"
                val mktPrompt = "Write a marketing launch package for this translated book titled \"${transBook?.title}\" in $targetLanguage. Include tagline and blurb."
                val localizedMkt = callAI(mktPrompt)

                val finalBook = repository.getBookById(nBookId)?.copy(
                    compiledManuscript = compiledText,
                    marketingMaterials = localizedMkt,
                    currentPhase = "Completed",
                    completionPercentage = 100
                )
                if (finalBook != null) {
                    repository.updateBook(finalBook)
                    // AUTO-SAVE to Book Directory
                    saveBookMetadata(finalBook, translatedChaptersList)
                }

                _bookTranslationLogs.value += "🎉 SUCCESS! Multi-language book translation complete!\nNew edition added: \"${transBook?.title}\"\n"
                onSuccess(finalBook ?: transBook!!)
            } catch (e: Throwable) {
                _bookTranslationLogs.value += "\n❌ Error in book translation: ${e.localizedMessage ?: "Unknown Error"}\n"
            } finally {
                _isBookTranslating.value = false
            }
        }
    }

    fun deleteBook(bookId: Int) {
        viewModelScope.launch(exceptionHandler) {
            repository.deleteBook(bookId)
            val chapters = repository.getChaptersForBookSync(bookId)
            chapters.forEach { repository.deleteChapter(it.id) }
            val lore = repository.getLorebookForBookSync(bookId)
            lore.forEach { repository.deleteLorebook(it.id) }
        }
    }

    private fun extractJsonArray(raw: String): String {
        val startIndex = raw.indexOf('[')
        val endIndex = raw.lastIndexOf(']')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return raw.substring(startIndex, endIndex + 1)
        }
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractJsonObject(raw: String): String {
        val startIndex = raw.indexOf('{')
        val endIndex = raw.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return raw.substring(startIndex, endIndex + 1)
        }
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    fun extractCharactersFromBible(bookId: Int) {
        val book = allBooks.value.find { it.id == bookId } ?: return
        if (book.storyBible.isEmpty()) {
            extractionLogs.value = "Story Bible is empty! Please generate a Story Bible first (by drafting chapters or using the Outliner Wizard)."
            return
        }

        isExtractingCharacters.value = true
        extractionLogs.value = "Analyzing Story Bible with ${selectedProvider.value} to extract cognitive character profiles...\n"

        viewModelScope.launch(exceptionHandler) {
            try {
                val prompt = """
                    Analyze the following Story Bible and extract a structured list of ALL major characters and their cognitive personality/vocal traits.
                    For each character, identify:
                    1. Name (exact name)
                    2. Role (e.g. Protagonist, Antagonist, Supporting)
                    3. Vocal traits / tone (e.g. "Low, deep, gravelly voice, speaks deliberately", "High-pitched, rapid-fire, enthusiastic", "Soft, melodic, whispering")
                    
                    Story Bible:
                    ${book.storyBible}
                    
                    Respond ONLY with a valid JSON array of objects. Do not include markdown blocks, just the raw JSON.
                    JSON Format example:
                    [
                      {
                        "name": "Alice",
                        "role": "Protagonist",
                        "vocal_traits": "High-pitched, rapid-fire, enthusiastic"
                      },
                      {
                        "name": "Bob",
                        "role": "Antagonist",
                        "vocal_traits": "Low, deep, gravelly voice, speaks deliberately"
                      }
                    ]
                """.trimIndent()

                val responseJson = callAI(prompt)
                
                val cleanedJson = extractJsonArray(responseJson)

                val jsonArray = org.json.JSONArray(cleanedJson)
                val configs = mutableListOf<CharacterVoiceConfig>()
                val voices = availableVoices.value
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val role = obj.getString("role")
                    val vocalTraits = obj.getString("vocal_traits")
                    
                    val traitsLower = vocalTraits.lowercase()
                    val isFemale = traitsLower.contains("female") || traitsLower.contains("woman") || traitsLower.contains("her") || traitsLower.contains("she") || name.equals("Alice", true)
                    val isMale = traitsLower.contains("male") || traitsLower.contains("man") || traitsLower.contains("his") || traitsLower.contains("he")
                    
                    val matchingVoice = voices.find { voice ->
                        if (isFemale) voice.gender.equals("Female", true) else if (isMale) voice.gender.equals("Male", true) else true
                    }?.name ?: selectedVoiceName.value ?: "default"
                    
                    var defaultPitch = 0.0f
                    var defaultRate = 1.0f
                    
                    if (traitsLower.contains("high") || traitsLower.contains("soft") || traitsLower.contains("whisper")) {
                        defaultPitch = 2.0f
                    } else if (traitsLower.contains("deep") || traitsLower.contains("low") || traitsLower.contains("gravelly")) {
                        defaultPitch = -2.0f
                    }
                    
                    if (traitsLower.contains("fast") || traitsLower.contains("rapid") || traitsLower.contains("hurried")) {
                        defaultRate = 1.2f
                    } else if (traitsLower.contains("slow") || traitsLower.contains("deliberate") || traitsLower.contains("dragging")) {
                        defaultRate = 0.85f
                    }

                    configs.add(
                        CharacterVoiceConfig(
                            name = name,
                            role = role,
                            vocalTraits = vocalTraits,
                            voiceName = matchingVoice,
                            pitch = defaultPitch,
                            rate = defaultRate,
                            volume = 100f
                        )
                    )
                }

                characterVoiceConfigs.value = configs
                extractionLogs.value = "Successfully extracted ${configs.size} character profiles using ${selectedProvider.value} with mapped vocal profiles!\n"
            } catch (e: Exception) {
                e.printStackTrace()
                extractionLogs.value = "Error during extraction with ${selectedProvider.value}: ${e.message}\n"
            } finally {
                isExtractingCharacters.value = false
            }
        }
    }

    fun extractCharactersFromScript() {
        val script = audioInputText.value
        if (script.isEmpty()) {
            extractionLogs.value = "Script is empty! Please import or generate a script first."
            return
        }

        isExtractingCharacters.value = true
        extractionLogs.value = "Analyzing script with ${selectedProvider.value} to identify characters and vocal traits...\n"

        viewModelScope.launch(exceptionHandler) {
            try {
                val prompt = """
                    Analyze the following script/text and extract a structured list of ALL characters who have dialogue.
                    For each character, identify their Name and likely Vocal traits / tone based on the context.
                    
                    Script:
                    ${if (script.length > 5000) script.take(5000) + "..." else script}
                    
                    Respond ONLY with a valid JSON array of objects. Do not include markdown blocks, just the raw JSON.
                    JSON Format example:
                    [
                      {
                        "name": "Alice",
                        "vocal_traits": "High-pitched, rapid-fire, enthusiastic"
                      },
                      {
                        "name": "Bob",
                        "vocal_traits": "Low, deep, gravelly voice, speaks deliberately"
                      }
                    ]
                """.trimIndent()

                val responseJson = callAI(prompt)
                val cleanedJson = extractJsonArray(responseJson)
                val jsonArray = org.json.JSONArray(cleanedJson)
                val configs = mutableListOf<CharacterVoiceConfig>()
                val voices = availableVoices.value
                
                // Add Narrator by default
                configs.add(
                    CharacterVoiceConfig(
                        name = "NARRATOR",
                        role = "Narrator",
                        vocalTraits = "Neutral narration",
                        voiceName = selectedVoiceName.value ?: "default",
                        pitch = 0.0f,
                        rate = 1.0f
                    )
                )

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    if (name.equals("NARRATOR", true)) continue
                    
                    val vocalTraits = obj.getString("vocal_traits")
                    val traitsLower = vocalTraits.lowercase()
                    
                    val isFemale = traitsLower.contains("female") || traitsLower.contains("woman") || traitsLower.contains("her") || traitsLower.contains("she")
                    val isMale = traitsLower.contains("male") || traitsLower.contains("man") || traitsLower.contains("his") || traitsLower.contains("he")
                    
                    val matchingVoice = voices.find { voice ->
                        if (isFemale) voice.gender.equals("Female", true) else if (isMale) voice.gender.equals("Male", true) else true
                    }?.name ?: selectedVoiceName.value ?: "default"
                    
                    var defaultPitch = 0.0f
                    var defaultRate = 1.0f
                    
                    if (traitsLower.contains("high") || traitsLower.contains("soft") || traitsLower.contains("whisper")) {
                        defaultPitch = 2.0f
                    } else if (traitsLower.contains("deep") || traitsLower.contains("low") || traitsLower.contains("gravelly")) {
                        defaultPitch = -2.0f
                    }
                    
                    if (traitsLower.contains("fast") || traitsLower.contains("rapid") || traitsLower.contains("hurried")) {
                        defaultRate = 1.2f
                    } else if (traitsLower.contains("slow") || traitsLower.contains("deliberate") || traitsLower.contains("dragging")) {
                        defaultRate = 0.85f
                    }

                    configs.add(
                        CharacterVoiceConfig(
                            name = name,
                            role = "Character",
                            vocalTraits = vocalTraits,
                            voiceName = matchingVoice,
                            pitch = defaultPitch,
                            rate = defaultRate,
                            volume = 100f
                        )
                    )
                }

                characterVoiceConfigs.value = configs
                extractionLogs.value = "Successfully identified ${configs.size} characters in the script!\n"
            } catch (e: Exception) {
                e.printStackTrace()
                extractionLogs.value = "Extraction failed: ${e.message}\n"
            } finally {
                isExtractingCharacters.value = false
            }
        }
    }

    fun assembleAudioDramaFromScript() {
        val script = audioInputText.value
        if (script.isEmpty()) return
        
        isAssemblingAudiobook.value = true
        assemblyProgress.value = 0f
        assemblyLogs.value = "Launching Audio Drama Assembly Pipeline...\n"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val segmentationPrompt = """
                    Segment the following script into narration blocks and character dialogue.
                    Identify the speaker for each block. Use "NARRATOR" for non-dialogue.
                    Respond strictly with a JSON array of objects. Do not wrap in markdown tags or include any text outside the JSON array.
                    
                    Each object must have:
                    - "speaker": The name of the character speaking, or "NARRATOR"
                    - "text": The literal text spoken
                    
                    Script:
                    $script
                """.trimIndent()

                val segmentsJson = callAI(segmentationPrompt)
                val cleaned = extractJsonArray(segmentsJson)
                val array = org.json.JSONArray(cleaned)
                val segments = mutableListOf<Pair<String, String>>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val speaker = item.getString("speaker")
                    val text = item.getString("text")
                    if (text.trim().isNotEmpty()) {
                        segments.add(Pair(speaker, text))
                    }
                }

                if (segments.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        assemblyLogs.value += "❌ Error: Could not segment script. Please ensure it has clear dialogue markers.\n"
                    }
                    isAssemblingAudiobook.value = false
                    return@launch
                }

                // AI VOICE PROFILING: Automatically assign voices to characters
                withContext(Dispatchers.Main) {
                    assemblyLogs.value += "🤖 AI Voice Profiling: Analyzing character personas and assigning matched voices...\n"
                }
                
                val uniqueSpeakers = segments.map { it.first }.distinct()
                val currentConfigs = characterVoiceConfigs.value.toMutableList()
                
                val profilingPrompt = """
                    You are a professional Casting Director. Analyze these characters from the script and assign them a voice profile.
                    Characters: ${uniqueSpeakers.joinToString(", ")}
                    
                    Available Voice Profiles (Tags):
                    - "Male, Deep, Authoritative"
                    - "Male, Young, Energetic"
                    - "Female, Sophisticated, Calm"
                    - "Female, High-pitched, Child-like"
                    - "Gender-neutral, Robotic, Monotone"
                    
                    Respond strictly with a JSON object where keys are character names and values are the chosen profile tag.
                """.trimIndent()
                
                try {
                    val profilingJson = callAI(profilingPrompt)
                    val profObj = org.json.JSONObject(extractJsonObject(profilingJson))
                    
                    uniqueSpeakers.forEach { speaker ->
                        if (speaker != "NARRATOR" && currentConfigs.none { it.name.equals(speaker, true) }) {
                            val profile = profObj.optString(speaker, "Male, Deep, Authoritative")
                            // Map profile to a specific voice (simplified for now, ideally we'd search ttsVoices)
                            val mappedVoice = when {
                                profile.contains("Female", true) -> "en-US-JennyNeural"
                                profile.contains("Robotic", true) -> "en-US-SteffanNeural"
                                else -> "en-US-GuyNeural"
                            }
                            currentConfigs.add(
                                CharacterVoiceConfig(
                                    name = speaker,
                                    role = "Character",
                                    vocalTraits = profile,
                                    voiceName = mappedVoice
                                )
                            )
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "🎭 Cast: $speaker assigned voice \"$mappedVoice\" ($profile)\n"
                            }
                        }
                    }
                    characterVoiceConfigs.value = currentConfigs
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        assemblyLogs.value += "⚠️ Voice profiling failed, using defaults: ${e.message}\n"
                    }
                }

                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val folderName = "AudioDrama_${System.currentTimeMillis()}"
                val subFolder = File(musicDir, folderName)
                subFolder.mkdirs()

                val segmentFiles = mutableListOf<File>()
                var allSegmentsSuccessful = true

                for (index in segments.indices) {
                    val (speaker, rawText) = segments[index]
                    val text = applyPronunciationRules(rawText)
                    val config = characterVoiceConfigs.value.find { it.name.equals(speaker, true) }
                    
                    val activeVoice = config?.voiceName ?: selectedVoiceName.value ?: "default"
                    val activePitch = config?.pitch ?: audioPitch.value
                    val activeRate = config?.rate ?: audioRate.value
                    val activeVolume = config?.volume ?: audioVolume.value

                    withContext(Dispatchers.Main) {
                        val dispSpeaker = if (speaker == "NARRATOR") "Narrator" else "Character: $speaker"
                        assemblyLogs.value += "🔊 Segment ${index + 1}/${segments.size} for $dispSpeaker...\n"
                    }

                    val tempSegmentFile = File(subFolder, "seg_${index}.wav")
                    
                    var success = false
                    // TTS Logic (similar to assembleAudiobookWithModulation)
                    if (_ttsEngine.value == "Kitten TTS") {
                        success = com.example.tts.LocalKittenTtsManager.synthesizeToFile(
                            context = getApplication(),
                            text = text,
                            outputFile = tempSegmentFile,
                            voiceName = activeVoice,
                            speed = activeRate,
                            chunkSize = ttsChunkSize.value,
                            onChunkProgress = { _, _ -> }
                        )
                    } else if (_ttsEngine.value == "Edge TTS") {
                        success = com.example.tts.EdgeTtsManager.synthesizeToFile(
                            text = text,
                            outputFile = tempSegmentFile,
                            voiceName = activeVoice,
                            pitch = activePitch,
                            rate = activeRate
                        )
                    } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
                        success = com.example.tts.CloudTtsManager.synthesizeToFile(
                            text = text,
                            hfToken = _hfApiKey.value,
                            outputFile = tempSegmentFile,
                            modelName = _hfModel.value
                        )
                    } else {
                        success = withContext(Dispatchers.Main) {
                            val completed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                            ttsManager?.synthesizeToFile(
                                text,
                                tempSegmentFile,
                                activeVoice,
                                activeRate,
                                Math.pow(2.0, activePitch.toDouble() / 12.0).toFloat(),
                                activeVolume,
                                "seg_$index",
                                object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) { completed.complete(true) }
                                    override fun onError(utteranceId: String?) { completed.complete(false) }
                                }
                            ) ?: false
                            completed.await()
                        }
                    }

                    if (success && tempSegmentFile.exists()) {
                        segmentFiles.add(tempSegmentFile)
                    } else {
                        allSegmentsSuccessful = false
                    }

                    withContext(Dispatchers.Main) {
                        assemblyProgress.value = (index + 1).toFloat() / segments.size
                    }
                }

                val finalFile = File(subFolder, "Full_Audio_Drama.wav")
                if (allSegmentsSuccessful && segmentFiles.isNotEmpty()) {
                    val concatListFile = File(subFolder, "concat_list.txt")
                    concatListFile.writeText(segmentFiles.joinToString("\n") { "file '${it.absolutePath}'" })

                    val cmdArray = arrayOf("-y", "-f", "concat", "-safe", "0", "-i", concatListFile.absolutePath, "-c", "copy", finalFile.absolutePath)
                    val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                    
                    if (rc == 0 && finalFile.exists()) {
                        withContext(Dispatchers.Main) {
                            assemblyLogs.value += "🎉 SUCCESS! Audio Drama saved to: ${finalFile.absolutePath}\n"
                            // Apply humanization
                            if (_humanizeAudio.value) {
                                com.example.utils.WavUtils.humanizeWavFile(finalFile)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) { assemblyLogs.value += "❌ FFmpeg merge failed.\n" }
                    }
                } else {
                    withContext(Dispatchers.Main) { assemblyLogs.value += "❌ Some segments failed synthesis.\n" }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { assemblyLogs.value += "❌ Pipeline Error: ${e.message}\n" }
            } finally {
                isAssemblingAudiobook.value = false
            }
        }
    }

    fun assembleAudiobookWithModulation(bookId: Int) {
        val book = allBooks.value.find { it.id == bookId } ?: return
        isAssemblingAudiobook.value = true
        assemblyProgress.value = 0f
        assemblyLogs.value = "Launching Audiobook Assembly Pipeline with Dynamic Character Voice Modulation...\n"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chapters = repository.getChaptersForBookSync(bookId).sortedBy { it.chapterNumber }
                if (chapters.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        assemblyLogs.value += "❌ Error: This book has no chapters! Please generate chapters first.\n"
                    }
                    isAssemblingAudiobook.value = false
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    assemblyLogs.value += "Found ${chapters.size} chapters to process.\n"
                }

                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val safeTitle = book.title.ifEmpty { "AudioNovel" }.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
                val subFolder = File(musicDir, "${safeTitle}_CharacterModulated")
                subFolder.mkdirs()

                val totalChapters = chapters.size
                var chaptersDone = 0

                for (chapter in chapters) {
                    withContext(Dispatchers.Main) {
                        assemblyLogs.value += "\n=========================================\n"
                        assemblyLogs.value += "🎬 Processing Chapter ${chapter.chapterNumber}: ${chapter.title}\n"
                    }

                    val segmentationPrompt = """
                        Segment the following novel chapter text into narration blocks and character dialogue utterances.
                        Identify the narrator or the specific character speaking for each block.
                        Respond strictly with a JSON array of objects. Do not wrap in markdown tags or include any text outside the JSON array.
                        
                        Each object must have exactly these keys:
                        - "speaker": The exact name of the character speaking, or "NARRATOR" for narration blocks.
                        - "text": The literal text spoken or narrated. Maintain exact spacing and punctuation.
                        
                        Chapter Text:
                        ${chapter.content}
                    """.trimIndent()

                    var segmentsJson = ""
                    try {
                        withContext(Dispatchers.Main) {
                            assemblyLogs.value += "🧠 Segmenting text via Cognitive Character Parser using ${selectedProvider.value}...\n"
                        }
                        segmentsJson = callAI(segmentationPrompt)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            assemblyLogs.value += "⚠️ Parser failed: ${e.message}. Falling back to paragraph split...\n"
                        }
                    }

                    val segments = mutableListOf<Pair<String, String>>()
                    if (segmentsJson.isNotBlank()) {
                        try {
                            val cleaned = extractJsonArray(segmentsJson)
                            val array = org.json.JSONArray(cleaned)
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val speaker = item.getString("speaker")
                                val text = item.getString("text")
                                if (text.trim().isNotEmpty()) {
                                    segments.add(Pair(speaker, text))
                                }
                            }
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "✅ Successfully extracted ${segments.size} character dialogue and narration blocks.\n"
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "⚠️ JSON Parsing failed. Falling back to paragraph split...\n"
                            }
                        }
                    }

                    if (segments.isEmpty()) {
                        chapter.content.split("\n\n").forEach { para ->
                            val trimmed = para.trim()
                            if (trimmed.isNotEmpty()) {
                                if (trimmed.startsWith("\"") && trimmed.contains("\"")) {
                                    segments.add(Pair("DIALOGUE", trimmed))
                                } else {
                                    segments.add(Pair("NARRATOR", trimmed))
                                }
                            }
                        }
                    }

                    val segmentFiles = mutableListOf<File>()
                    var segIndex = 0
                    var allSegmentsSuccessful = true

                    for (seg in segments) {
                        val speaker = seg.first
                        val text = seg.second
                        segIndex++

                        val config = characterVoiceConfigs.value.find { it.name.equals(speaker, true) }
                        
                        val activeVoice = config?.voiceName ?: selectedVoiceName.value ?: "default"
                        val activePitch = if (config != null) config.pitch else audioPitch.value
                        val activeRate = if (config != null) config.rate else audioRate.value
                        val activeVolume = if (config != null) config.volume else audioVolume.value

                        withContext(Dispatchers.Main) {
                            val dispSpeaker = if (speaker == "NARRATOR") "Narrator" else "Character: $speaker"
                            val dispVoice = availableVoices.value.find { it.name == activeVoice }?.displayName ?: activeVoice
                            assemblyLogs.value += "🔊 Segment $segIndex/${segments.size} [Voice: $dispVoice, Pitch: ${activePitch}f, Speed: ${activeRate}x] for: $dispSpeaker\n"
                        }

                        val tempSegmentFile = File(subFolder, "temp_chap_${chapter.chapterNumber}_seg_$segIndex.wav")
                        if (tempSegmentFile.exists()) tempSegmentFile.delete()

                        var success = false
                        if (_ttsEngine.value == "Kitten TTS") {
                            try {
                                success = com.example.tts.LocalKittenTtsManager.synthesizeToFile(
                                    context = getApplication(),
                                    text = text,
                                    outputFile = tempSegmentFile,
                                    voiceName = activeVoice,
                                    speed = activeRate,
                                    chunkSize = ttsChunkSize.value,
                                    onChunkProgress = { _, _ -> }
                                )
                            } catch (e: Throwable) {
                                withContext(Dispatchers.Main) {
                                    assemblyLogs.value += "❌ Kitten TTS failed for segment $segIndex: ${e.message}\n"
                                }
                            }
                        } else if (_ttsEngine.value == "Edge TTS") {
                            try {
                                success = com.example.tts.EdgeTtsManager.synthesizeToFile(
                                    text = text,
                                    outputFile = tempSegmentFile,
                                    voiceName = activeVoice,
                                    pitch = activePitch,
                                    rate = activeRate
                                )
                            } catch (e: Throwable) {
                                withContext(Dispatchers.Main) {
                                    assemblyLogs.value += "❌ Edge TTS failed for segment $segIndex: ${e.message}\n"
                                }
                            }
                        } else if (_ttsEngine.value == "Cloud HuggingFace TTS") {
                            try {
                                success = com.example.tts.CloudTtsManager.synthesizeToFile(
                                    text = text,
                                    hfToken = _hfApiKey.value,
                                    outputFile = tempSegmentFile,
                                    modelName = _hfModel.value
                                )
                            } catch (e: Throwable) {
                                withContext(Dispatchers.Main) {
                                    assemblyLogs.value += "❌ Cloud TTS failed for segment $segIndex: ${e.message}\n"
                                }
                            }
                        } else {
                            success = withContext(Dispatchers.Main) {
                                val completed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                                val synthResult = ttsManager?.synthesizeToFile(
                                    text,
                                    tempSegmentFile,
                                    activeVoice,
                                    activeRate,
                                    Math.pow(2.0, activePitch.toDouble() / 12.0).toFloat(),
                                    activeVolume,
                                    "seg_${chapter.chapterNumber}_$segIndex",
                                    object : android.speech.tts.UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) {}
                                        override fun onDone(utteranceId: String?) { completed.complete(true) }
                                        override fun onError(utteranceId: String?) { completed.complete(false) }
                                    }
                                ) ?: false
                                if (!synthResult) completed.complete(false)
                                completed.await()
                            }
                        }

                        if (success && tempSegmentFile.exists() && tempSegmentFile.length() > 0) {
                            segmentFiles.add(tempSegmentFile)
                        } else {
                            allSegmentsSuccessful = false
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "⚠️ Synthesis failed for segment $segIndex\n"
                            }
                        }
                    }

                    val finalChapterFile = File(subFolder, "Chapter_${chapter.chapterNumber}.wav")
                    if (finalChapterFile.exists()) finalChapterFile.delete()

                    if (allSegmentsSuccessful && segmentFiles.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            assemblyLogs.value += "🔗 Assembling and merging ${segmentFiles.size} audio segments via FFmpeg...\n"
                        }

                        val concatListFile = File(subFolder, "concat_list_chap_${chapter.chapterNumber}.txt")
                        val listContent = segmentFiles.joinToString("\n") { file ->
                            "file '${file.absolutePath}'"
                        }
                        concatListFile.writeText(listContent)

                        val cmdArray = arrayOf(
                            "-y",
                            "-f", "concat",
                            "-safe", "0",
                            "-i", concatListFile.absolutePath,
                            "-c", "copy",
                            finalChapterFile.absolutePath
                        )

                        val rc = io.microshow.rxffmpeg.RxFFmpegInvoke.getInstance().runCommand(cmdArray, null)
                        if (rc == 0 && finalChapterFile.exists() && finalChapterFile.length() > 0) {
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "🎉 SUCCESS! Chapter ${chapter.chapterNumber} fully compiled and saved to: ${finalChapterFile.absolutePath}\n"
                            }
                            concatListFile.delete()
                            segmentFiles.forEach { it.delete() }
                        } else {
                            withContext(Dispatchers.Main) {
                                assemblyLogs.value += "❌ FFmpeg merge failed with exit code $rc.\n"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            assemblyLogs.value += "❌ Could not merge because some segments failed synthesis.\n"
                        }
                    }

                    chaptersDone++
                    withContext(Dispatchers.Main) {
                        assemblyProgress.value = chaptersDone.toFloat() / totalChapters
                    }
                }

                withContext(Dispatchers.Main) {
                    assemblyLogs.value += "\n🏁 ALL CHAPTERS PROCESSED SUCCESSFULLY!\n"
                    assemblyLogs.value += "Final Audiobook Assembly Output Directory:\n${subFolder.absolutePath}\n"
                    android.widget.Toast.makeText(getApplication(), "Audiobook Assembly Complete!", android.widget.Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    assemblyLogs.value += "\n❌ Pipeline Error: ${e.message}\n"
                }
            } finally {
                isAssemblingAudiobook.value = false
            }
        }
    }

    fun loadPronunciationRules() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rulesStr = sharedPrefs.getString("pronunciation_rules", null)
            if (rulesStr == null) {
                pronunciationRules.value = defaultPronunciationRules
                savePronunciationRules(defaultPronunciationRules)
            } else {
                try {
                    if (rulesStr.isBlank()) {
                        pronunciationRules.value = emptyList()
                        return@launch
                    }
                    val parsed = rulesStr.split("||").filter { it.contains("::") }.map { chunk ->
                        val parts = chunk.split("::")
                        val id = if (parts.size > 2) parts[2] else java.util.UUID.randomUUID().toString()
                        PronunciationRule(id = id, original = parts[0], phonetic = parts[1])
                    }
                    pronunciationRules.value = parsed
                } catch (e: Exception) {
                    pronunciationRules.value = defaultPronunciationRules
                }
            }
        }
    }

    fun savePronunciationRules(rules: List<PronunciationRule>) {
        val rulesStr = rules.joinToString("||") { "${it.original}::${it.phonetic}::${it.id}" }
        sharedPrefs.edit().putString("pronunciation_rules", rulesStr).apply()
        pronunciationRules.value = rules
    }

    fun addPronunciationRule(original: String, phonetic: String) {
        if (original.isBlank() || phonetic.isBlank()) return
        val current = pronunciationRules.value.toMutableList()
        current.add(PronunciationRule(original = original, phonetic = phonetic))
        savePronunciationRules(current)
    }

    fun removePronunciationRule(ruleId: String) {
        val current = pronunciationRules.value.filter { it.id != ruleId }
        savePronunciationRules(current)
    }

    fun applyPronunciationRules(text: String): String {
        var result = text
        for (rule in pronunciationRules.value) {
            if (rule.original.isNotBlank() && rule.phonetic.isNotBlank()) {
                try {
                    val escapedOriginal = Regex.escape(rule.original)
                    // Match whole word case-insensitively using word boundaries
                    val regex = Regex("(?i)\\b$escapedOriginal\\b")
                    result = result.replace(regex, rule.phonetic)
                } catch (e: Exception) {
                    result = result.replace(rule.original, rule.phonetic, ignoreCase = true)
                }
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        ttsManager?.shutdown()
    }
}

data class CharacterVoiceConfig(
    val name: String,
    val role: String,
    val vocalTraits: String,
    var voiceName: String,
    var pitch: Float = 0.0f,
    var rate: Float = 1.0f,
    var volume: Float = 100f
)

data class PronunciationRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val original: String,
    val phonetic: String
)

data class TtsQueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    val status: String = "Queued",
    val progress: Float = 0f,
    val outputFile: java.io.File? = null
)
