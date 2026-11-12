package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import com.example.data.api.LlmOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    // v3.0 D-03: the single secure-prefs construction site is
    // LlmOrchestrator.openSecurePrefs — on keystore failure the fallback is
    // IN-MEMORY ONLY (never the old plain `secure_studio_settings_fallback`
    // file), the corrupt-store delete is gone (a transient failure must not
    // nuke the store; a broken store stays for the fixed keystore to reopen)
    // and the Settings screen surfaces the degraded state.
    private val sharedPrefs = LlmOrchestrator.openSecurePrefs(application)

    private val _geminiApiKey = MutableStateFlow(sharedPrefs.getString("gemini_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow(sharedPrefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash")
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _mistralApiKey = MutableStateFlow(sharedPrefs.getString("mistral_key", "") ?: "")
    val mistralApiKey: StateFlow<String> = _mistralApiKey.asStateFlow()

    private val _mistralModel = MutableStateFlow(sharedPrefs.getString("mistral_model", "mistral-small-latest") ?: "mistral-small-latest")
    val mistralModel: StateFlow<String> = _mistralModel.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(sharedPrefs.getString("openrouter_key", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModel = MutableStateFlow(sharedPrefs.getString("openrouter_model", "google/gemini-2.0-flash-exp:free") ?: "google/gemini-2.0-flash-exp:free")
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    private val _groqApiKey = MutableStateFlow(sharedPrefs.getString("groq_key", "") ?: "")
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
    val ollamaApiKey: StateFlow<String> = _ollamaApiKey.asStateFlow()

    private val _ollamaModel = MutableStateFlow(sharedPrefs.getString("ollama_model", "gemma4:31b") ?: "gemma4:31b")
    val ollamaModel: StateFlow<String> = _ollamaModel.asStateFlow()

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

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "Sepia") ?: "Sepia")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _authorName = MutableStateFlow(sharedPrefs.getString("author_name", "") ?: "")
    val authorName: StateFlow<String> = _authorName.asStateFlow()

    private val _authorPenName = MutableStateFlow(sharedPrefs.getString("author_pen_name", "") ?: "")
    val authorPenName: StateFlow<String> = _authorPenName.asStateFlow()

    private val _authorBio = MutableStateFlow(sharedPrefs.getString("author_bio", "") ?: "")
    val authorBio: StateFlow<String> = _authorBio.asStateFlow()

    private val _authorPublisher = MutableStateFlow(sharedPrefs.getString("author_publisher", "") ?: "")
    val authorPublisher: StateFlow<String> = _authorPublisher.asStateFlow()

    private val _authorWebsite = MutableStateFlow(sharedPrefs.getString("author_website", "") ?: "")
    val authorWebsite: StateFlow<String> = _authorWebsite.asStateFlow()

    private val _authorSocial = MutableStateFlow(sharedPrefs.getString("author_social", "") ?: "")
    val authorSocial: StateFlow<String> = _authorSocial.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "tts_engine" -> _ttsEngine.value = prefs.getString(key, "Cloud HuggingFace TTS") ?: "Cloud HuggingFace TTS"
            "humanize_audio" -> _humanizeAudio.value = prefs.getBoolean(key, true)
            "auto_optimize" -> _autoOptimize.value = prefs.getBoolean(key, false)
            "queue_concurrency" -> _queueConcurrency.value = prefs.getInt(key, 2)
            "queue_rpm" -> _queueRpm.value = prefs.getInt(key, 15)
            "gemini_key" -> _geminiApiKey.value = prefs.getString(key, "") ?: ""
            "gemini_model" -> _geminiModel.value = prefs.getString(key, "gemini-1.5-flash") ?: "gemini-1.5-flash"
            "mistral_key" -> _mistralApiKey.value = prefs.getString(key, "") ?: ""
            "mistral_model" -> _mistralModel.value = prefs.getString(key, "mistral-small-latest") ?: "mistral-small-latest"
            "openrouter_key" -> _openRouterApiKey.value = prefs.getString(key, "") ?: ""
            "openrouter_model" -> _openRouterModel.value = prefs.getString(key, "google/gemini-2.0-flash-exp:free") ?: "google/gemini-2.0-flash-exp:free"
            "groq_key" -> _groqApiKey.value = prefs.getString(key, "") ?: ""
            "groq_model" -> _groqModel.value = prefs.getString(key, "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b"
            "nvidia_key" -> _nvidiaApiKey.value = prefs.getString(key, "") ?: ""
            "nvidia_model" -> _nvidiaModel.value = prefs.getString(key, "mistralai/mistral-nemotron") ?: "mistralai/mistral-nemotron"
            "custom_openai_key" -> _customOpenAiApiKey.value = prefs.getString(key, "") ?: ""
            "custom_openai_base_url" -> _customOpenAiBaseUrl.value = prefs.getString(key, "https://integrate.api.nvidia.com/v1") ?: "https://integrate.api.nvidia.com/v1"
            "custom_openai_model" -> _customOpenAiModel.value = prefs.getString(key, "deepseek-ai/deepseek-v4-flash") ?: "deepseek-ai/deepseek-v4-flash"
            "hf_key" -> _hfApiKey.value = prefs.getString(key, "") ?: ""
            "hf_model" -> _hfModel.value = prefs.getString(key, "facebook/mms-tts-eng") ?: "facebook/mms-tts-eng"
            "provider" -> _selectedProvider.value = prefs.getString(key, "Gemini") ?: "Gemini"
            "app_theme" -> _appTheme.value = prefs.getString(key, "Sepia") ?: "Sepia"
            "author_name" -> _authorName.value = prefs.getString(key, "") ?: ""
            "author_pen_name" -> _authorPenName.value = prefs.getString(key, "") ?: ""
            "author_bio" -> _authorBio.value = prefs.getString(key, "") ?: ""
            "author_publisher" -> _authorPublisher.value = prefs.getString(key, "") ?: ""
            "author_website" -> _authorWebsite.value = prefs.getString(key, "") ?: ""
            "author_social" -> _authorSocial.value = prefs.getString(key, "") ?: ""
            "cleanup_enabled" -> _cleanupEnabled.value = prefs.getBoolean(key, true)
            "cleanup_frequency" -> _cleanupFrequency.value = prefs.getString(key, "Weekly") ?: "Weekly"
            "delete_temp_cache" -> _deleteTempCache.value = prefs.getBoolean(key, true)
            "delete_failed_artifacts" -> _deleteFailedArtifacts.value = prefs.getBoolean(key, true)
            "delete_expired_api" -> _deleteExpiredApi.value = prefs.getBoolean(key, false)
            "delete_unused_cache" -> _deleteUnusedCache.value = prefs.getBoolean(key, true)
            "max_cache_size_mb" -> _maxCacheSizeMb.value = prefs.getInt(key, 500)
            "max_projects_count" -> _maxProjectsCount.value = prefs.getInt(key, 10)
        }
    }

    private val _fetchedModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val fetchedModels: StateFlow<Map<String, List<String>>> = _fetchedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _modelFetchStatus = MutableStateFlow<String?>(null)
    val modelFetchStatus: StateFlow<String?> = _modelFetchStatus.asStateFlow()

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        val cached = mutableMapOf<String, List<String>>()
        listOf("Groq", "NVIDIA", "Gemini", "Mistral", "OpenRouter", "Ollama", "Custom OpenAI").forEach { p ->
            val saved = sharedPrefs.getString("cached_models_$p", "") ?: ""
            if (saved.isNotEmpty()) {
                cached[p] = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                cached[p] = com.example.data.api.ModelDiscoveryService.getFallbackModelsForProvider(p)
            }
        }
        _fetchedModels.value = cached
        autoFetchModels()
    }

    fun autoFetchModels(targetProvider: String? = null) {
        val prov = targetProvider ?: _selectedProvider.value
        val key = when (prov) {
            "Gemini" -> _geminiApiKey.value.ifEmpty { com.example.BuildConfig.GEMINI_API_KEY }
            "Mistral" -> _mistralApiKey.value
            "OpenRouter" -> _openRouterApiKey.value
            "Groq" -> _groqApiKey.value
            "NVIDIA" -> _nvidiaApiKey.value
            "Custom OpenAI" -> _customOpenAiApiKey.value
            "Ollama" -> _ollamaApiKey.value.ifEmpty { com.example.BuildConfig.OLLAMA_API_KEY }
            else -> ""
        }
        val baseUrl = when (prov) {
            "Ollama" -> _ollamaBaseUrl.value
            "Custom OpenAI" -> _customOpenAiBaseUrl.value
            else -> ""
        }

        viewModelScope.launch {
            // 2026-11-12 audit (#9): with no key configured, skip the live fetch —
            // the old path sent the BuildConfig placeholder to the provider and
            // surfaced its HTTP 400 as a mystery failure.
            if (key.isBlank()) {
                _fetchedModels.value = _fetchedModels.value.toMutableMap().apply {
                    put(prov, com.example.data.api.ModelDiscoveryService.getFallbackModelsForProvider(prov))
                }
                _modelFetchStatus.value = "No API key configured for $prov — showing the built-in model list."
                return@launch
            }
            _isFetchingModels.value = true
            _modelFetchStatus.value = "Fetching live models for $prov..."
            val result = com.example.data.api.ModelDiscoveryService.fetchModelsForProvider(prov, key, baseUrl)
            val models = result.getOrDefault(com.example.data.api.ModelDiscoveryService.getFallbackModelsForProvider(prov))
            val currentMap = _fetchedModels.value.toMutableMap()
            currentMap[prov] = models
            _fetchedModels.value = currentMap
            _isFetchingModels.value = false
            _modelFetchStatus.value = "Discovered ${models.size} models for $prov"
            sharedPrefs.edit().putString("cached_models_$prov", models.joinToString(",")).apply()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }


    private val _waitingRequests = MutableStateFlow(0)
    val waitingRequests: StateFlow<Int> = _waitingRequests.asStateFlow()

    private val _failedRequests = MutableStateFlow(0)
    val failedRequests: StateFlow<Int> = _failedRequests.asStateFlow()

    private val _retryCountdown = MutableStateFlow(0)
    val retryCountdown: StateFlow<Int> = _retryCountdown.asStateFlow()

    private val _averageRequestDurationMs = MutableStateFlow(0L)
    val averageRequestDurationMs: StateFlow<Long> = _averageRequestDurationMs.asStateFlow()

    private val _queueConcurrency = MutableStateFlow(sharedPrefs.getInt("queue_concurrency", 2))
    val queueConcurrency: StateFlow<Int> = _queueConcurrency.asStateFlow()

    private val _queueRpm = MutableStateFlow(sharedPrefs.getInt("queue_rpm", 15))
    val queueRpm: StateFlow<Int> = _queueRpm.asStateFlow()

    fun refreshStorageUsage() {}

    fun pruneAudioCache(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var freedBytes = 0L
                val cacheDir = context.cacheDir
                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.extension.equals("wav", ignoreCase = true) || file.name.startsWith("tts_") || file.name.startsWith("pockettts_"))) {
                        freedBytes += file.length()
                        file.delete()
                    }
                }
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val weaverDir = java.io.File(downloadsDir, "Weaver_Studio/AudioCache")
                if (weaverDir.exists()) {
                    weaverDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            freedBytes += file.length()
                            file.delete()
                        }
                    }
                }
                _audioCacheSize.value = 0L
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "🧹 Voice Cache Pruned: Freed ${freedBytes / 1024} KB of storage", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to prune audio cache", e)
            }
        }
    }









    fun setQueueConfig(concurrency: Int, rpm: Int) {
        _queueConcurrency.value = concurrency
        _queueRpm.value = rpm
        sharedPrefs.edit()
            .putInt("queue_concurrency", concurrency)
            .putInt("queue_rpm", rpm)
            .apply()
    }


    private val _cleanupEnabled = MutableStateFlow(sharedPrefs.getBoolean("cleanup_enabled", true))
    val cleanupEnabled: StateFlow<Boolean> = _cleanupEnabled.asStateFlow()

    private val _cleanupFrequency = MutableStateFlow(sharedPrefs.getString("cleanup_frequency", "Weekly") ?: "Weekly")
    val cleanupFrequency: StateFlow<String> = _cleanupFrequency.asStateFlow()

    private val _deleteTempCache = MutableStateFlow(sharedPrefs.getBoolean("delete_temp_cache", true))
    val deleteTempCache: StateFlow<Boolean> = _deleteTempCache.asStateFlow()

    private val _deleteFailedArtifacts = MutableStateFlow(sharedPrefs.getBoolean("delete_failed_artifacts", true))
    val deleteFailedArtifacts: StateFlow<Boolean> = _deleteFailedArtifacts.asStateFlow()

    private val _deleteExpiredApi = MutableStateFlow(sharedPrefs.getBoolean("delete_expired_api", false))
    val deleteExpiredApi: StateFlow<Boolean> = _deleteExpiredApi.asStateFlow()
    
    private val _deleteUnusedCache = MutableStateFlow(sharedPrefs.getBoolean("delete_unused_cache", true))
    val deleteUnusedCache: StateFlow<Boolean> = _deleteUnusedCache.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(sharedPrefs.getInt("max_cache_size_mb", 500))
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb.asStateFlow()

    private val _maxProjectsCount = MutableStateFlow(sharedPrefs.getInt("max_projects_count", 10))
    val maxProjectsCount: StateFlow<Int> = _maxProjectsCount.asStateFlow()

    private val _databaseSize = MutableStateFlow(0L)
    val databaseSize: StateFlow<Long> = _databaseSize.asStateFlow()

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    private val _audioCacheSize = MutableStateFlow(0L)
    val audioCacheSize: StateFlow<Long> = _audioCacheSize.asStateFlow()

    private val _imageCacheSize = MutableStateFlow(0L)
    val imageCacheSize: StateFlow<Long> = _imageCacheSize.asStateFlow()
    
    private val _totalStorageUsed = MutableStateFlow(0L)
    val totalStorageUsed: StateFlow<Long> = _totalStorageUsed.asStateFlow()

    private val _queueLength = MutableStateFlow(0)
    val queueLength: StateFlow<Int> = _queueLength.asStateFlow()

    private val _runningRequests = MutableStateFlow(0)
    val runningRequests: StateFlow<Int> = _runningRequests.asStateFlow()

    fun updateCleanupSchedule(context: Context) {
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val enabled = _cleanupEnabled.value
            val frequency = _cleanupFrequency.value

            workManager.cancelAllWorkByTag("storage_cleanup")

            if (enabled) {
                val repeatInterval = when (frequency) {
                    "Daily" -> 1L
                    "Weekly" -> 7L
                    "Monthly" -> 30L
                    else -> 7L
                }
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val cleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.worker.StorageCleanupWorker>(
                    repeatInterval, java.util.concurrent.TimeUnit.DAYS
                )
                    .setConstraints(constraints)
                    .addTag("storage_cleanup")
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    "storage_cleanup_work",
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    cleanupRequest
                )
                android.util.Log.d("SettingsViewModel", "Scheduled storage cleanup: $frequency")
            } else {
                android.util.Log.d("SettingsViewModel", "Automatic storage cleanup disabled.")
            }
        } catch (e: Throwable) {
            android.util.Log.e("SettingsViewModel", "Failed to schedule storage cleanup", e)
        }
    }

    fun setCleanupEnabled(context: Context, enabled: Boolean) { 
        _cleanupEnabled.value = enabled 
        sharedPrefs.edit().putBoolean("cleanup_enabled", enabled).apply()
        updateCleanupSchedule(context)
    }
    fun setCleanupFrequency(context: Context, freq: String) { 
        _cleanupFrequency.value = freq 
        sharedPrefs.edit().putString("cleanup_frequency", freq).apply()
        updateCleanupSchedule(context)
    }
    fun setDeleteTempCache(v: Boolean) { 
        _deleteTempCache.value = v 
        sharedPrefs.edit().putBoolean("delete_temp_cache", v).apply()
    }
    fun setDeleteFailedArtifacts(v: Boolean) { 
        _deleteFailedArtifacts.value = v 
        sharedPrefs.edit().putBoolean("delete_failed_artifacts", v).apply()
    }
    fun setDeleteExpiredApi(v: Boolean) { 
        _deleteExpiredApi.value = v 
        sharedPrefs.edit().putBoolean("delete_expired_api", v).apply()
    }
    fun setDeleteUnusedCache(v: Boolean) { 
        _deleteUnusedCache.value = v 
        sharedPrefs.edit().putBoolean("delete_unused_cache", v).apply()
    }
    fun setMaxCacheSizeMb(v: Int) { 
        _maxCacheSizeMb.value = v 
        sharedPrefs.edit().putInt("max_cache_size_mb", v).apply()
    }
    fun setMaxProjectsCount(v: Int) { 
        _maxProjectsCount.value = v 
        sharedPrefs.edit().putInt("max_projects_count", v).apply()
    }
    fun cleanNow(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val cleanRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.StorageCleanupWorker>()
                .setConstraints(constraints)
                .addTag("storage_cleanup_manual")
                .build()
            workManager.enqueue(cleanRequest)
        }
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }
    
    fun setTtsEngine(engine: String) {
        _ttsEngine.value = engine
        sharedPrefs.edit().putString("tts_engine", engine).apply()
    }

    fun setSelectedProvider(provider: String) {
        _selectedProvider.value = provider
        sharedPrefs.edit().putString("provider", provider).apply()
    }

    fun setHumanizeAudio(enabled: Boolean) {
        _humanizeAudio.value = enabled
        sharedPrefs.edit().putBoolean("humanize_audio", enabled).apply()
    }

    fun setAutoOptimize(enabled: Boolean) {
        _autoOptimize.value = enabled
        sharedPrefs.edit().putBoolean("auto_optimize", enabled).apply()
    }

    fun saveApiKeys(
        gemini: String,
        geminiModel: String = "gemini-1.5-flash",
        mistral: String,
        mistralModel: String = "mistral-small-latest",
        hfKey: String,
        hfModel: String,
        provider: String,
        ttsEng: String = "Android",
        ollama: String = "",
        ollamaModel: String = "gemma4:31b",
        ollamaBaseUrl: String = "https://api.ollama.com/",
        openrouter: String = "",
        openRouterModel: String = "google/gemini-2.0-flash-exp:free",
        groqKey: String = "",
        groqModel: String = "openai/gpt-oss-120b",
        nvidiaKey: String = "",
        nvidiaModel: String = "mistralai/mistral-nemotron",
        customOpenAiKey: String = "",
        customOpenAiBaseUrl: String = "https://integrate.api.nvidia.com/v1",
        customOpenAiModel: String = "deepseek-ai/deepseek-v4-flash"
    ) {
        _geminiApiKey.value = gemini
        _geminiModel.value = geminiModel
        _mistralApiKey.value = mistral
        _mistralModel.value = mistralModel
        _openRouterApiKey.value = openrouter
        _openRouterModel.value = openRouterModel
        _groqApiKey.value = groqKey
        _groqModel.value = groqModel
        _nvidiaApiKey.value = nvidiaKey
        _nvidiaModel.value = nvidiaModel
        _customOpenAiApiKey.value = customOpenAiKey
        _customOpenAiBaseUrl.value = customOpenAiBaseUrl
        _customOpenAiModel.value = customOpenAiModel
        _hfApiKey.value = hfKey
        _hfModel.value = hfModel
        _ollamaApiKey.value = ollama
        _ollamaModel.value = ollamaModel
        _ollamaBaseUrl.value = ollamaBaseUrl
        _selectedProvider.value = provider
        _ttsEngine.value = ttsEng

        sharedPrefs.edit()
            .putString("tts_engine", ttsEng)
            .putString("gemini_key", gemini)
            .putString("gemini_model", geminiModel)
            .putString("mistral_key", mistral)
            .putString("mistral_model", mistralModel)
            .putString("openrouter_key", openrouter)
            .putString("openrouter_model", openRouterModel)
            .putString("groq_key", groqKey)
            .putString("groq_model", groqModel)
            .putString("nvidia_key", nvidiaKey)
            .putString("nvidia_model", nvidiaModel)
            .putString("custom_openai_key", customOpenAiKey)
            .putString("custom_openai_base_url", customOpenAiBaseUrl)
            .putString("custom_openai_model", customOpenAiModel)
            .putString("ollama_key", ollama)
            .putString("ollama_model", ollamaModel)
            .putString("ollama_base_url", ollamaBaseUrl)
            .putString("hf_key", hfKey)
            .putString("hf_model", hfModel)
            .putString("provider", provider)
            .apply()
        autoFetchModels(provider)
    }

    fun saveAuthorDetails(name: String, penName: String, bio: String, publisher: String, website: String, social: String) {
        _authorName.value = name
        _authorPenName.value = penName
        _authorBio.value = bio
        _authorPublisher.value = publisher
        _authorWebsite.value = website
        _authorSocial.value = social

        sharedPrefs.edit()
            .putString("author_name", name)
            .putString("author_pen_name", penName)
            .putString("author_bio", bio)
            .putString("author_publisher", publisher)
            .putString("author_website", website)
            .putString("author_social", social)
            .apply()
    }
}
