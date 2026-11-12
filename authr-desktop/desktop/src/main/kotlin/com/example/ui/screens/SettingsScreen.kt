package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: com.example.ui.viewmodel.SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val geminiKey by settingsViewModel.geminiApiKey.collectAsState()
    val geminiModel by settingsViewModel.geminiModel.collectAsState()
    val mistralKey by settingsViewModel.mistralApiKey.collectAsState()
    val mistralModel by settingsViewModel.mistralModel.collectAsState()
    val openRouterKey by settingsViewModel.openRouterApiKey.collectAsState()
    val openRouterModel by settingsViewModel.openRouterModel.collectAsState()
    val groqKey by settingsViewModel.groqApiKey.collectAsState()
    val groqModel by settingsViewModel.groqModel.collectAsState()
    val nvidiaKey by settingsViewModel.nvidiaApiKey.collectAsState()
    val nvidiaModel by settingsViewModel.nvidiaModel.collectAsState()
    val customOpenAiKey by settingsViewModel.customOpenAiApiKey.collectAsState()
    val customOpenAiBaseUrl by settingsViewModel.customOpenAiBaseUrl.collectAsState()
    val customOpenAiModel by settingsViewModel.customOpenAiModel.collectAsState()
    val ollamaKey by settingsViewModel.ollamaApiKey.collectAsState()
    val ollamaModel by settingsViewModel.ollamaModel.collectAsState()
    val ollamaBaseUrl by settingsViewModel.ollamaBaseUrl.collectAsState()
    val hfKey by settingsViewModel.hfApiKey.collectAsState()
    val hfModel by settingsViewModel.hfModel.collectAsState()
    val ttsEng by settingsViewModel.ttsEngine.collectAsState()
    val provider by settingsViewModel.selectedProvider.collectAsState()
    val appTheme by settingsViewModel.appTheme.collectAsState()
    val fetchedModels by settingsViewModel.fetchedModels.collectAsState()
    val isFetchingModels by settingsViewModel.isFetchingModels.collectAsState()
    val modelFetchStatus by settingsViewModel.modelFetchStatus.collectAsState()

    var tempGeminiKey by remember { mutableStateOf(geminiKey) }
    var tempGeminiModel by remember { mutableStateOf(geminiModel) }
    var tempMistralKey by remember { mutableStateOf(mistralKey) }
    var tempMistralModel by remember { mutableStateOf(mistralModel) }
    var tempOpenRouterKey by remember { mutableStateOf(openRouterKey) }
    var tempOpenRouterModel by remember { mutableStateOf(openRouterModel) }
    var tempGroqKey by remember { mutableStateOf(groqKey) }
    var tempGroqModel by remember { mutableStateOf(groqModel) }
    var tempNvidiaKey by remember { mutableStateOf(nvidiaKey) }
    var tempNvidiaModel by remember { mutableStateOf(nvidiaModel) }
    var tempCustomOpenAiKey by remember { mutableStateOf(customOpenAiKey) }
    var tempCustomOpenAiBaseUrl by remember { mutableStateOf(customOpenAiBaseUrl) }
    var tempCustomOpenAiModel by remember { mutableStateOf(customOpenAiModel) }
    var tempOllamaKey by remember { mutableStateOf(ollamaKey) }
    var tempOllamaModel by remember { mutableStateOf(ollamaModel) }
    var tempOllamaBaseUrl by remember { mutableStateOf(ollamaBaseUrl) }
    var tempHfKey by remember { mutableStateOf(hfKey) }
    var tempHfModel by remember { mutableStateOf(hfModel) }
    var selectedTtsEngine by remember { mutableStateOf(ttsEng) }
    var selectedProv by remember { mutableStateOf(provider) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("studio_settings", android.content.Context.MODE_PRIVATE) }
    var executiveProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_executive", "Gemini") ?: "Gemini") }
    var plotProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_plot", "Gemini") ?: "Gemini") }
    var characterProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_character", "Gemini") ?: "Gemini") }
    var worldProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_world", "Gemini") ?: "Gemini") }
    var sceneProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_scene", "Gemini") ?: "Gemini") }
    var continuityProvider by remember { mutableStateOf(sharedPrefs.getString("agent_provider_continuity", "Gemini") ?: "Gemini") }

    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var isMistralKeyVisible by remember { mutableStateOf(false) }
    var isOpenRouterKeyVisible by remember { mutableStateOf(false) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }
    var isNvidiaKeyVisible by remember { mutableStateOf(false) }
    var isCustomOpenAiKeyVisible by remember { mutableStateOf(false) }
    var isOllamaKeyVisible by remember { mutableStateOf(false) }
    var isHfKeyVisible by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val cleanupEnabled by settingsViewModel.cleanupEnabled.collectAsState()
    val cleanupFrequency by settingsViewModel.cleanupFrequency.collectAsState()
    val deleteTempCache by settingsViewModel.deleteTempCache.collectAsState()
    val deleteFailedArtifacts by settingsViewModel.deleteFailedArtifacts.collectAsState()
    val deleteExpiredApi by settingsViewModel.deleteExpiredApi.collectAsState()
    val deleteUnusedCache by settingsViewModel.deleteUnusedCache.collectAsState()
    val maxCacheSizeMb by settingsViewModel.maxCacheSizeMb.collectAsState()
    val maxProjectsCount by settingsViewModel.maxProjectsCount.collectAsState()

    val databaseSize by settingsViewModel.databaseSize.collectAsState()
    val cacheSize by settingsViewModel.cacheSize.collectAsState()
    val audioCacheSize by settingsViewModel.audioCacheSize.collectAsState()
    val imageCacheSize by settingsViewModel.imageCacheSize.collectAsState()
    val totalStorageUsed by settingsViewModel.totalStorageUsed.collectAsState()

    val queueLength by settingsViewModel.queueLength.collectAsState()
    val runningRequests by settingsViewModel.runningRequests.collectAsState()
    val waitingRequests by settingsViewModel.waitingRequests.collectAsState()
    val failedRequests by settingsViewModel.failedRequests.collectAsState()
    val retryCountdown by settingsViewModel.retryCountdown.collectAsState()
    val averageRequestDurationMs by settingsViewModel.averageRequestDurationMs.collectAsState()

    val queueConcurrency by settingsViewModel.queueConcurrency.collectAsState()
    val queueRpm by settingsViewModel.queueRpm.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.refreshStorageUsage()
    }

    // Sync state when loaded
    LaunchedEffect(geminiKey, geminiModel, mistralKey, mistralModel, openRouterKey, openRouterModel, groqKey, groqModel, nvidiaKey, nvidiaModel, customOpenAiKey, customOpenAiBaseUrl, customOpenAiModel, ollamaKey, ollamaModel, ollamaBaseUrl, hfKey, hfModel, ttsEng, provider) {
        tempGeminiKey = geminiKey
        tempGeminiModel = geminiModel
        tempMistralKey = mistralKey
        tempMistralModel = mistralModel
        tempOpenRouterKey = openRouterKey
        tempOpenRouterModel = openRouterModel
        tempGroqKey = groqKey
        tempGroqModel = groqModel
        tempNvidiaKey = nvidiaKey
        tempNvidiaModel = nvidiaModel
        tempCustomOpenAiKey = customOpenAiKey
        tempCustomOpenAiBaseUrl = customOpenAiBaseUrl
        tempCustomOpenAiModel = customOpenAiModel
        tempOllamaKey = ollamaKey
        tempOllamaModel = ollamaModel
        tempOllamaBaseUrl = ollamaBaseUrl
        tempHfKey = hfKey
        tempHfModel = hfModel
        selectedTtsEngine = ttsEng
        selectedProv = provider
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "API Key Configurations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // v3.0 D-03: a broken keystore degrades to MEMORY-ONLY key storage —
        // say so where the keys are entered, instead of silently persisting
        // them unencrypted (the old plain-file fallback).
        if (com.example.data.api.LlmOrchestrator.secureStorageDegraded) {
            Text(
                text = "Secure storage unavailable — API keys are held in memory only for this session and will NOT be saved. Re-enter them after restarting with a working keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active AI Provider",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Select your default LLM engine to configure its credentials and models:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Groq", "NVIDIA", "Gemini", "Mistral", "OpenRouter", "Ollama", "Custom OpenAI").forEach { name ->
                        FilterChip(
                            selected = selectedProv == name,
                            onClick = { 
                                selectedProv = name
                                settingsViewModel.setSelectedProvider(name)
                            },
                            label = { Text(name, fontWeight = if (selectedProv == name) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (selectedProv == name) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("provider_chip_$name")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuring $selectedProv",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FilledTonalButton(
                        onClick = { settingsViewModel.autoFetchModels(selectedProv) },
                        enabled = !isFetchingModels,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("auto_fetch_models_button")
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetching...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Fetch Models", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (!modelFetchStatus.isNullOrEmpty()) {
                    Text(
                        text = modelFetchStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                val availableModelsForProv = fetchedModels[selectedProv] ?: com.example.data.api.ModelDiscoveryService.getFallbackModelsForProvider(selectedProv)

                when (selectedProv) {
                    "Groq" -> {
                        OutlinedTextField(
                            value = tempGroqKey,
                            onValueChange = { tempGroqKey = it },
                            label = { Text("Groq API Key") },
                            placeholder = { Text("Enter your Groq API Key (gsk_...)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("groq_key_input"),
                            visualTransformation = if (isGroqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isGroqKeyVisible = !isGroqKeyVisible }) {
                                    Icon(
                                        imageVector = if (isGroqKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempGroqModel,
                            onValueChange = { tempGroqModel = it },
                            label = { Text("Groq Model") },
                            placeholder = { Text("e.g. openai/gpt-oss-120b or llama-3.3-70b-versatile") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("groq_model_input")
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempGroqModel == model,
                                    onClick = { tempGroqModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempGroqModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    "NVIDIA" -> {
                        OutlinedTextField(
                            value = tempNvidiaKey,
                            onValueChange = { tempNvidiaKey = it },
                            label = { Text("NVIDIA NIM API Key") },
                            placeholder = { Text("Enter your NVIDIA API Key (nvapi-...)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nvidia_key_input"),
                            visualTransformation = if (isNvidiaKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isNvidiaKeyVisible = !isNvidiaKeyVisible }) {
                                    Icon(
                                        imageVector = if (isNvidiaKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempNvidiaModel,
                            onValueChange = { tempNvidiaModel = it },
                            label = { Text("NVIDIA Model") },
                            placeholder = { Text("e.g. mistralai/mistral-nemotron, google/gemma-4-31b-it") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nvidia_model_input")
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempNvidiaModel == model || tempNvidiaModel.endsWith(model),
                                    onClick = { tempNvidiaModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempNvidiaModel == model || tempNvidiaModel.endsWith(model)) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    "Custom OpenAI" -> {
                        OutlinedTextField(
                            value = tempCustomOpenAiBaseUrl,
                            onValueChange = { tempCustomOpenAiBaseUrl = it },
                            label = { Text("Custom OpenAI Base URL") },
                            placeholder = { Text("e.g. https://integrate.api.nvidia.com/v1") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_openai_base_url_input")
                        )
                        OutlinedTextField(
                            value = tempCustomOpenAiKey,
                            onValueChange = { tempCustomOpenAiKey = it },
                            label = { Text("Custom OpenAI API Key") },
                            placeholder = { Text("Enter API key for custom endpoint") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_openai_key_input"),
                            visualTransformation = if (isCustomOpenAiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCustomOpenAiKeyVisible = !isCustomOpenAiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isCustomOpenAiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempCustomOpenAiModel,
                            onValueChange = { tempCustomOpenAiModel = it },
                            label = { Text("Custom OpenAI Model") },
                            placeholder = { Text("e.g. deepseek-ai/deepseek-v4-flash") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_openai_model_input")
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempCustomOpenAiModel == model,
                                    onClick = { tempCustomOpenAiModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempCustomOpenAiModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    "Gemini" -> {
                        OutlinedTextField(
                            value = tempGeminiKey,
                            onValueChange = { tempGeminiKey = it },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("Enter your Gemini API Key") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_key_input"),
                            visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isGeminiKeyVisible = !isGeminiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isGeminiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempGeminiModel,
                            onValueChange = { tempGeminiModel = it },
                            label = { Text("Gemini Model") },
                            placeholder = { Text("e.g. gemini-1.5-flash or gemini-2.0-flash-exp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempGeminiModel == model,
                                    onClick = { tempGeminiModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempGeminiModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }

                        Text(
                            text = "💡 Tip: Leave key empty to fall back to the project's default built-in Gemini key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    "Mistral" -> {
                        OutlinedTextField(
                            value = tempMistralKey,
                            onValueChange = { tempMistralKey = it },
                            label = { Text("Mistral API Key") },
                            placeholder = { Text("Enter your Mistral API Key") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mistral_key_input"),
                            visualTransformation = if (isMistralKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isMistralKeyVisible = !isMistralKeyVisible }) {
                                    Icon(
                                        imageVector = if (isMistralKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempMistralModel,
                            onValueChange = { tempMistralModel = it },
                            label = { Text("Mistral Model") },
                            placeholder = { Text("e.g. mistral-small-latest or mistral-large-latest") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempMistralModel == model,
                                    onClick = { tempMistralModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempMistralModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    "OpenRouter" -> {
                        OutlinedTextField(
                            value = tempOpenRouterKey,
                            onValueChange = { tempOpenRouterKey = it },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("Enter your OpenRouter API Key") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openrouter_key_input"),
                            visualTransformation = if (isOpenRouterKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isOpenRouterKeyVisible = !isOpenRouterKeyVisible }) {
                                    Icon(
                                        imageVector = if (isOpenRouterKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempOpenRouterModel,
                            onValueChange = { tempOpenRouterModel = it },
                            label = { Text("OpenRouter Model") },
                            placeholder = { Text("e.g. google/gemini-2.0-flash-exp:free") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Discovered / Free Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempOpenRouterModel == model,
                                    onClick = { tempOpenRouterModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempOpenRouterModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    "Ollama" -> {
                        OutlinedTextField(
                            value = tempOllamaBaseUrl,
                            onValueChange = { tempOllamaBaseUrl = it },
                            label = { Text("Ollama Base URL") },
                            placeholder = { Text("e.g. https://api.ollama.com/ or local URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ollama_base_url_input")
                        )
                        OutlinedTextField(
                            value = tempOllamaKey,
                            onValueChange = { tempOllamaKey = it },
                            label = { Text("Ollama API Key (Optional)") },
                            placeholder = { Text("Enter your Ollama API Key if required") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ollama_key_input"),
                            visualTransformation = if (isOllamaKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isOllamaKeyVisible = !isOllamaKeyVisible }) {
                                    Icon(
                                        imageVector = if (isOllamaKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = tempOllamaModel,
                            onValueChange = { tempOllamaModel = it },
                            label = { Text("Ollama Model") },
                            placeholder = { Text("e.g. gemma4:31b") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Discovered / Recommended Models (${availableModelsForProv.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModelsForProv.forEach { model ->
                                FilterChip(
                                    selected = tempOllamaModel == model,
                                    onClick = { tempOllamaModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tempOllamaModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Hugging Face Settings
        // Mature Content Gate (2026-11-12 audit #4): adult personas and the
        // erotica genre browser stay hidden everywhere until acknowledged here.
        var showAdultGateDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var adultAcknowledged by remember {
            mutableStateOf(com.example.utils.PersonaResolver.adultAcknowledged(context))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("Mature Content (18+)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Unlocks the adult author personas (e.g. The Weaver, The NightKing) and the Special Erotica genre browser. Every generation path enforces this gate — not just the pickers.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (adultAcknowledged) "Enabled — adult presets visible" else "Disabled — adult presets hidden", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = adultAcknowledged,
                        onCheckedChange = { wanted ->
                            if (wanted) showAdultGateDialog = true
                            else {
                                com.example.utils.PersonaResolver.setAdultContentAcknowledged(context, false)
                                adultAcknowledged = false
                            }
                        },
                        modifier = Modifier.testTag("adult_content_gate_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showAdultGateDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAdultGateDialog = false },
                title = { Text("Enable mature content?") },
                text = {
                    Text(
                        "This unlocks personas and genres that generate explicit adult material (18+). " +
                            "You confirm you are of legal age and want this content enabled on this device. " +
                            "You can turn it off again at any time."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        com.example.utils.PersonaResolver.setAdultContentAcknowledged(context, true)
                        adultAcknowledged = true
                        showAdultGateDialog = false
                    }) { Text("I am 18+, enable") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdultGateDialog = false }) { Text("Cancel") }
                }
            )
        }

        // TTS Engine Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("Text-to-Speech Engine", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Android", "Pocket TTS", "Cloud HuggingFace TTS").forEach { engine ->
                        FilterChip(
                            selected = selectedTtsEngine == engine,
                            onClick = { 
                                selectedTtsEngine = engine
                                settingsViewModel.setTtsEngine(engine)
                            },
                            label = { Text(engine) },
                            leadingIcon = if (selectedTtsEngine == engine) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("tts_chip_$engine")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val humanizeAudio by settingsViewModel.humanizeAudio.collectAsState()
                val autoOptimize by settingsViewModel.autoOptimize.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Humanize Local TTS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Adds organic pauses and micro-jitter to speech for realism.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = humanizeAudio,
                        onCheckedChange = { settingsViewModel.setHumanizeAudio(it) },
                        modifier = Modifier.testTag("humanize_tts_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Optimize Speed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Prioritize synthesis speed over complex neural layering.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = autoOptimize,
                        onCheckedChange = { settingsViewModel.setAutoOptimize(it) },
                        modifier = Modifier.testTag("auto_optimize_switch")
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Info Section for selected TTS Engine
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val (infoIcon, infoText) = when (selectedTtsEngine) {
                        "Android" -> Icons.Default.Android to "Uses your device's built-in Text-to-Speech system. Works completely offline, fast, and light on resources."
                        "Pocket TTS" -> Icons.Default.PhonelinkRing to "Runs the local Pocket TTS engine on this machine: 100% offline, private, real-time on CPU, with narrator voice cloning from a short WAV reference. Optional on Android as a downloadable model pack."
                        else -> Icons.Default.SettingsApplications to "Uses Hugging Face inference endpoints. Highly customizable with cutting-edge neural speech models (requires internet and API key)."
                    }
                    Icon(
                        imageVector = infoIcon,
                        contentDescription = "Engine Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (selectedTtsEngine == "Cloud HuggingFace TTS") {
            OutlinedTextField(
                value = tempHfKey,
                onValueChange = { tempHfKey = it },
                label = { Text("Hugging Face Access Token (for Cloud TTS)") },
                placeholder = { Text("Enter your HF Token (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isHfKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isHfKeyVisible = !isHfKeyVisible }) {
                        Icon(
                            imageVector = if (isHfKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Key Visibility"
                        )
                    }
                }
            )

            OutlinedTextField(
                value = tempHfModel,
                onValueChange = { tempHfModel = it },
                label = { Text("Hugging Face Model ID (for Cloud TTS)") },
                placeholder = { Text("e.g. facebook/mms-tts-eng or espnet/kan-bayashi_ljspeech_vits") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    settingsViewModel.saveApiKeys(
                        gemini = tempGeminiKey,
                        geminiModel = tempGeminiModel,
                        mistral = tempMistralKey,
                        mistralModel = tempMistralModel,
                        hfKey = tempHfKey,
                        hfModel = tempHfModel,
                        provider = selectedProv,
                        ttsEng = selectedTtsEngine,
                        ollama = tempOllamaKey,
                        ollamaModel = tempOllamaModel,
                        ollamaBaseUrl = tempOllamaBaseUrl,
                        openrouter = tempOpenRouterKey,
                        openRouterModel = tempOpenRouterModel,
                        groqKey = tempGroqKey,
                        groqModel = tempGroqModel,
                        nvidiaKey = tempNvidiaKey,
                        nvidiaModel = tempNvidiaModel,
                        customOpenAiKey = tempCustomOpenAiKey,
                        customOpenAiBaseUrl = tempCustomOpenAiBaseUrl,
                        customOpenAiModel = tempCustomOpenAiModel
                    )
                    sharedPrefs.edit().apply {
                        putString("agent_provider_executive", executiveProvider)
                        putString("agent_provider_plot", plotProvider)
                        putString("agent_provider_character", characterProvider)
                        putString("agent_provider_world", worldProvider)
                        putString("agent_provider_scene", sceneProvider)
                        putString("agent_provider_continuity", continuityProvider)
                    }.apply()
                    testStatus = "Keys and Agent configurations saved!"
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_keys_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Keys")
            }

            Button(
                onClick = {
                    if (isTesting) return@Button
                    isTesting = true
                    testStatus = "Connecting to $selectedProv API..."
                    coroutineScope.launch {
                        try {
                            val prompt = "Reply with 'API Online' in 2 words."
                            settingsViewModel.saveApiKeys(
                                gemini = tempGeminiKey,
                                geminiModel = tempGeminiModel,
                                mistral = tempMistralKey,
                                mistralModel = tempMistralModel,
                                hfKey = tempHfKey,
                                hfModel = tempHfModel,
                                provider = selectedProv,
                                ttsEng = selectedTtsEngine,
                                ollama = tempOllamaKey,
                                ollamaModel = tempOllamaModel,
                                ollamaBaseUrl = tempOllamaBaseUrl,
                                openrouter = tempOpenRouterKey,
                                openRouterModel = tempOpenRouterModel,
                                groqKey = tempGroqKey,
                                groqModel = tempGroqModel,
                                nvidiaKey = tempNvidiaKey,
                                nvidiaModel = tempNvidiaModel,
                                customOpenAiKey = tempCustomOpenAiKey,
                                customOpenAiBaseUrl = tempCustomOpenAiBaseUrl,
                                customOpenAiModel = tempCustomOpenAiModel
                            )
                            
                            var finalStatus = ""
                            try {
                                val result = when (selectedProv) {
                                    "Gemini" -> {
                                        // 2026-11-12 audit (#9): an unset key is an unset key —
                                        // the old BuildConfig placeholder fallback sent
                                        // "MY_GEMINI_API_KEY" to the API as a real key.
                                        val effectiveKey = tempGeminiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
                                        if (effectiveKey.isBlank()) throw IllegalStateException("No Gemini API key configured — enter a key (or set GEMINI_API_KEY in .env) before testing.")
                                        com.example.data.api.ApiClient.geminiService.generateContent(
                                            "gemini-1.5-flash",
                                            effectiveKey,
                                            com.example.data.api.GeminiRequest(
                                                contents = listOf(com.example.data.api.GeminiContent(role = "user", parts = listOf(com.example.data.api.GeminiPart(text = prompt))))
                                            )
                                        ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                    }
                                    "Mistral" -> {
                                        com.example.data.api.ApiClient.mistralService.generateContent(
                                            "Bearer $tempMistralKey",
                                            com.example.data.api.MistralRequest(
                                                messages = listOf(com.example.data.api.MistralMessage(role = "user", content = prompt))
                                            )
                                        ).choices?.firstOrNull()?.message?.content
                                    }
                                    "Groq" -> {
                                        val req = com.example.data.api.OpenAiChatRequest(
                                            model = tempGroqModel,
                                            messages = listOf(com.example.data.api.OpenAiChatMessage(role = "user", content = prompt))
                                        )
                                        com.example.data.api.ApiClient.openAiCompatibleService.generateContent(
                                            "https://api.groq.com/openai/v1/chat/completions",
                                            "Bearer $tempGroqKey",
                                            req
                                        ).choices?.firstOrNull()?.message?.content
                                    }
                                    "NVIDIA" -> {
                                        val req = com.example.data.api.OpenAiChatRequest(
                                            model = tempNvidiaModel,
                                            messages = listOf(com.example.data.api.OpenAiChatMessage(role = "user", content = prompt))
                                        )
                                        com.example.data.api.ApiClient.openAiCompatibleService.generateContent(
                                            "https://integrate.api.nvidia.com/v1/chat/completions",
                                            "Bearer $tempNvidiaKey",
                                            req
                                        ).choices?.firstOrNull()?.message?.content
                                    }
                                    "Custom OpenAI" -> {
                                        val finalUrl = if (tempCustomOpenAiBaseUrl.endsWith("/")) "${tempCustomOpenAiBaseUrl}chat/completions" else "$tempCustomOpenAiBaseUrl/chat/completions"
                                        val req = com.example.data.api.OpenAiChatRequest(
                                            model = tempCustomOpenAiModel,
                                            messages = listOf(com.example.data.api.OpenAiChatMessage(role = "user", content = prompt))
                                        )
                                        com.example.data.api.ApiClient.openAiCompatibleService.generateContent(
                                            finalUrl,
                                            "Bearer $tempCustomOpenAiKey",
                                            req
                                        ).choices?.firstOrNull()?.message?.content
                                    }
                                    else -> {
                                        val finalOllamaUrl = if (tempOllamaBaseUrl.endsWith("/")) "${tempOllamaBaseUrl}api/chat" else "$tempOllamaBaseUrl/api/chat"
                                        com.example.data.api.ApiClient.ollamaService.generateContent(
                                            finalOllamaUrl,
                                            "Bearer $tempOllamaKey",
                                            com.example.data.api.OllamaRequest(
                                                model = tempOllamaModel,
                                                messages = listOf(com.example.data.api.OllamaMessage(role = "user", content = prompt))
                                            )
                                        ).message?.content
                                    }
                                }
                                finalStatus += if (result != null) "LLM: Succeeded ($result)\n" else "LLM: Failed\n"
                            } catch (e: Exception) {
                                finalStatus += "LLM Error: ${e.localizedMessage}\n"
                            }
                            
                            if (selectedTtsEngine == "Cloud TTS" || tempHfKey.isNotEmpty()) {
                                try {
                                    val keyToTest = tempHfKey.ifEmpty { BuildConfig.HF_API_KEY }
                                    com.example.data.api.ApiClient.huggingFaceService.whoami("Bearer $keyToTest")
                                    finalStatus += "HF API: Succeeded"
                                } catch (e: Exception) {
                                    finalStatus += "HF API Error: ${e.localizedMessage}"
                                }
                            }
                            
                            testStatus = finalStatus.trim()
                        } catch (e: Exception) {
                            testStatus = "Error: ${e.localizedMessage ?: "Failed to connect"}"
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("test_connection_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ElectricalServices, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test API")
            }
        }

        testStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("Succeeded") || status.contains("saved")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (status.contains("Succeeded") || status.contains("saved")) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        Text(
            text = "Studio Visual Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Select your preferred visual aesthetic for the entire studio interface.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Original", "Sepia", "Light", "Dark", "Obsidian", "Charcoal", "Midnight Blue", "Graphite", "Emerald Studio", "High Contrast").forEach { theme ->
                        val isSelected = appTheme == theme || (theme == "Original" && appTheme == "original")
                        FilterChip(
                            selected = isSelected,
                            onClick = { settingsViewModel.setAppTheme(if (theme == "Original") "original" else theme) },
                            label = {
                                Text(
                                    text = theme,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("theme_chip_$theme")
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        // AUTHOR DETAILS SECTION
        val authorName by settingsViewModel.authorName.collectAsState()
        val authorPenName by settingsViewModel.authorPenName.collectAsState()
        val authorBio by settingsViewModel.authorBio.collectAsState()
        val authorPublisher by settingsViewModel.authorPublisher.collectAsState()
        val authorWebsite by settingsViewModel.authorWebsite.collectAsState()
        val authorSocial by settingsViewModel.authorSocial.collectAsState()

        var tempAuthorName by remember { mutableStateOf(authorName) }
        var tempAuthorPenName by remember { mutableStateOf(authorPenName) }
        var tempAuthorBio by remember { mutableStateOf(authorBio) }
        var tempAuthorPublisher by remember { mutableStateOf(authorPublisher) }
        var tempAuthorWebsite by remember { mutableStateOf(authorWebsite) }
        var tempAuthorSocial by remember { mutableStateOf(authorSocial) }

        LaunchedEffect(authorName, authorPenName, authorBio, authorPublisher, authorWebsite, authorSocial) {
            tempAuthorName = authorName
            tempAuthorPenName = authorPenName
            tempAuthorBio = authorBio
            tempAuthorPublisher = authorPublisher
            tempAuthorWebsite = authorWebsite
            tempAuthorSocial = authorSocial
        }

        Text(
            text = "Author & Publisher Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "These details will be automatically embedded into generated EPUB books.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = tempAuthorName,
                    onValueChange = { tempAuthorName = it },
                    label = { Text("Author Real Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAuthorPenName,
                    onValueChange = { tempAuthorPenName = it },
                    label = { Text("Pen Name (Used in EPUB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAuthorPublisher,
                    onValueChange = { tempAuthorPublisher = it },
                    label = { Text("Publisher Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAuthorWebsite,
                    onValueChange = { tempAuthorWebsite = it },
                    label = { Text("Author Website") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAuthorSocial,
                    onValueChange = { tempAuthorSocial = it },
                    label = { Text("Social Media Links") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAuthorBio,
                    onValueChange = { tempAuthorBio = it },
                    label = { Text("Author Biography") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        settingsViewModel.saveAuthorDetails(
                            tempAuthorName, tempAuthorPenName, tempAuthorBio, tempAuthorPublisher, tempAuthorWebsite, tempAuthorSocial
                        )
                        testStatus = "Author details saved successfully!"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Author Details")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        Text(
            text = "Weaver Agent Configurations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Assign dynamic LLM providers to specialized pipeline sub-agents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val agentsList = listOf(
                    Triple("Executive Orchestrator", "Coordinates compilation, styling passes, cover designs, and marketing assets.", executiveProvider to { v: String -> executiveProvider = v }),
                    Triple("Plot Architect", "Constructs chapter-by-chapter story outlines and tracks timeline plot events.", plotProvider to { v: String -> plotProvider = v }),
                    Triple("Character Psychologist", "Profiles motivations, vulnerabilities, cognitive schemas, and dialogues.", characterProvider to { v: String -> characterProvider = v }),
                    Triple("World Builder", "Synthesizes Story Bible details, setting rules, and custom lorebook assets.", worldProvider to { v: String -> worldProvider = v }),
                    Triple("Scene Composer", "Drafts prose, acts scene goals, and integrates active persona styles.", sceneProvider to { v: String -> sceneProvider = v }),
                    Triple("Continuity Manager", "Compiles real-time narrative reports to track continuity constraints.", continuityProvider to { v: String -> continuityProvider = v })
                )

                agentsList.forEach { (name, desc, statePair) ->
                    val (currentValue, setValue) = statePair
                    val keyName = name.split(" ")[0].lowercase()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Gemini", "Mistral", "OpenRouter", "Groq", "NVIDIA", "Custom OpenAI", "Ollama").forEach { prov ->
                                FilterChip(
                                    selected = currentValue == prov,
                                    onClick = { 
                                        setValue(prov)
                                        sharedPrefs.edit().putString("agent_provider_$keyName", prov).apply()
                                    },
                                    label = { Text(prov, style = MaterialTheme.typography.bodySmall) },
                                    leadingIcon = if (currentValue == prov) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("agent_chip_${keyName}_$prov")
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        // Storage & Automated Cleanup
        Card(
            modifier = Modifier.fillMaxWidth().testTag("storage_cleanup_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Storage Cleanup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Prevent the database and cache from growing indefinitely",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cleanupEnabled,
                        onCheckedChange = { settingsViewModel.setCleanupEnabled(context, it) },
                        modifier = Modifier.testTag("cleanup_enabled_switch")
                    )
                }

                if (cleanupEnabled) {
                    Text(
                        text = "Cleanup Frequency",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Daily", "Weekly", "Monthly").forEach { freq ->
                            FilterChip(
                                selected = cleanupFrequency == freq,
                                onClick = { settingsViewModel.setCleanupFrequency(context, freq) },
                                label = { Text(freq) },
                                leadingIcon = if (cleanupFrequency == freq) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("frequency_chip_$freq")
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    text = "Cleanup Options",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setDeleteTempCache(!deleteTempCache) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteTempCache,
                        onCheckedChange = { settingsViewModel.setDeleteTempCache(it) },
                        modifier = Modifier.testTag("checkbox_temp_cache")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete temporary cache files", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setDeleteFailedArtifacts(!deleteFailedArtifacts) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteFailedArtifacts,
                        onCheckedChange = { settingsViewModel.setDeleteFailedArtifacts(it) },
                        modifier = Modifier.testTag("checkbox_failed_artifacts")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete failed generation artifacts", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setDeleteExpiredApi(!deleteExpiredApi) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteExpiredApi,
                        onCheckedChange = { settingsViewModel.setDeleteExpiredApi(it) },
                        modifier = Modifier.testTag("checkbox_expired_api")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete expired API responses", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setDeleteUnusedCache(!deleteUnusedCache) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteUnusedCache,
                        onCheckedChange = { settingsViewModel.setDeleteUnusedCache(it) },
                        modifier = Modifier.testTag("checkbox_unused_cache")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove unused audio/image cache", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Max Cache Size Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Maximum Cache Size", style = MaterialTheme.typography.bodyMedium)
                        Text("${maxCacheSizeMb} MB", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = maxCacheSizeMb.toFloat(),
                        onValueChange = { settingsViewModel.setMaxCacheSizeMb(it.toInt()) },
                        valueRange = 100f..10000f,
                        steps = 99,
                        modifier = Modifier.testTag("max_cache_size_slider")
                    )
                }

                // Max Projects Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Maximum Stored Projects", style = MaterialTheme.typography.bodyMedium)
                        Text("$maxProjectsCount projects", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = maxProjectsCount.toFloat(),
                        onValueChange = { settingsViewModel.setMaxProjectsCount(it.toInt()) },
                        valueRange = 5f..200f,
                        steps = 39,
                        modifier = Modifier.testTag("max_projects_slider")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Storage usage summary
                Text(
                    text = "Storage Usage Summary",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (audioCacheSize > 500 * 1024 * 1024L) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("audio_cache_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚠️ Audio Cache Threshold Exceeded (>500 MB)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cumulative voice cache has reached ${formatBytes(audioCacheSize)}. Prune voice cache to regain storage space.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Database size", style = MaterialTheme.typography.bodySmall)
                        Text(formatBytes(databaseSize), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cache size", style = MaterialTheme.typography.bodySmall)
                        Text(formatBytes(cacheSize), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Audio cache size", style = MaterialTheme.typography.bodySmall)
                        Text(formatBytes(audioCacheSize), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Image cache size", style = MaterialTheme.typography.bodySmall)
                        Text(formatBytes(imageCacheSize), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total storage used", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatBytes(totalStorageUsed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { settingsViewModel.pruneAudioCache(context) },
                        modifier = Modifier.weight(1f).testTag("prune_voice_cache_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prune Voice Cache")
                    }

                    Button(
                        onClick = { settingsViewModel.cleanNow(context) },
                        modifier = Modifier.weight(1f).testTag("clean_now_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clean All")
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        // Local API Rate Limit Queue
        Card(
            modifier = Modifier.fillMaxWidth().testTag("api_rate_limit_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local Client-Side API Rate Limit Queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "Controls concurrent requests and throttles calls to respect provider limits (FIFO, Auto-Retry with exponential backoff).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    text = "Queue Monitoring Diagnostics",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Queue Length (Active + Waiting)", style = MaterialTheme.typography.bodySmall)
                        Text("$queueLength requests", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Running / Concurrency State", style = MaterialTheme.typography.bodySmall)
                        Text("$runningRequests / $queueConcurrency active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Waiting in queue", style = MaterialTheme.typography.bodySmall)
                        Text("$waitingRequests requests", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Failed / Throttled requests", style = MaterialTheme.typography.bodySmall)
                        Text("$failedRequests requests", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    if (retryCountdown > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Retry Countdown (Backoff)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text("$retryCountdown seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Avg Request Duration", style = MaterialTheme.typography.bodySmall)
                        Text(if (averageRequestDurationMs > 0) "${averageRequestDurationMs} ms" else "N/A", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    text = "Queue Parameters Config",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Concurrency Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Thread Concurrency", style = MaterialTheme.typography.bodyMedium)
                        Text("$queueConcurrency req", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = queueConcurrency.toFloat(),
                        onValueChange = { settingsViewModel.setQueueConfig(it.toInt(), queueRpm) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("concurrency_slider")
                    )
                }

                // RPM Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Requests Per Minute (RPM)", style = MaterialTheme.typography.bodyMedium)
                        Text("$queueRpm RPM", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = queueRpm.toFloat(),
                        onValueChange = { settingsViewModel.setQueueConfig(queueConcurrency, it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 23,
                        modifier = Modifier.testTag("rpm_slider")
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

        // Security Notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security Protocol", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    text = "Security Warning: I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline)
        
        Text("About & Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        
        var showRatingDialog by remember { mutableStateOf(false) }
        var showPolicyDialog by remember { mutableStateOf(false) }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("Rate & Review") },
                    supportingContent = { Text("Help us improve the app") },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.clickable { showRatingDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Send Feedback") },
                    supportingContent = { Text("Report a bug or suggest a feature") },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            setData(android.net.Uri.parse("mailto:support@audionovelstudio.com"))
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Feedback for Authr OS")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    leadingContent = { Icon(Icons.Default.Policy, contentDescription = null) },
                    modifier = Modifier.clickable { showPolicyDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Terms of Service") },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                    modifier = Modifier.clickable { showPolicyDialog = true }
                )
            }
        }
        
        if (showRatingDialog) {
            AlertDialog(
                onDismissRequest = { showRatingDialog = false },
                title = { Text("Enjoying Authr OS?") },
                text = { Text("If you like generating audio novels, please take a moment to rate the app. It really helps!") },
                confirmButton = {
                    TextButton(onClick = { showRatingDialog = false }) {
                        Text("Rate on Play Store")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRatingDialog = false }) {
                        Text("Not Now")
                    }
                }
            )
        }
        
        if (showPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showPolicyDialog = false },
                title = { Text("Legal Information") },
                text = { Text("Privacy Policy\n\n1. Data Collection: Authr OS operates locally. Generative prompts and story data are stored entirely on your device via Room DB.\n2. API Key Security: Configured API keys (Gemini, Hugging Face, Mistral, Ollama) are stored in secure local SharedPreferences and only transmitted directly to the respective model providers during inference.\n3. Telemetry: We do not collect telemetry, analytics, or manuscript telemetry.\n4. IP Rights: You retain 100% intellectual property rights to all manuscripts generated through this studio.\n\nTerms of Service\n\nBy using Authr OS, you agree to abide by the terms of the LLM providers (e.g. Google Gemini, Mistral AI, Hugging Face). You are responsible for the content you generate. Authr OS makes no guarantees of commercial viability or literary quality.") },
                confirmButton = {
                    TextButton(onClick = { showPolicyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
}
