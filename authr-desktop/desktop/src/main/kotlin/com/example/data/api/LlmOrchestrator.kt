package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LlmOrchestrator {
    
    enum class Agent(val key: String, val displayName: String) {
        EXECUTIVE("executive", "Executive Orchestrator"),
        PLOT("plot", "Plot Architect"),
        CHARACTER("character", "Character Psychologist"),
        WORLD("world", "World Builder"),
        SCENE("scene", "Scene Composer"),
        CONTINUITY("continuity", "Continuity Manager")
    }

    fun getProviderForAgent(context: Context, agent: Agent): String {
        val sharedPrefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
        return sharedPrefs.getString("agent_provider_${agent.key}", "Gemini") ?: "Gemini"
    }

    fun setProviderForAgent(context: Context, agent: Agent, provider: String) {
        val sharedPrefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("agent_provider_${agent.key}", provider).apply()
    }

    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "secure_studio_settings",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("secure_studio_settings_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun getApiKeys(context: Context, provider: String): List<String> {
        val sharedPrefs = getSecurePrefs(context)
        val rawKeys = when (provider) {
            "Gemini" -> {
                val key = sharedPrefs.getString("gemini_key", "") ?: ""
                if (key.isNotEmpty()) key else BuildConfig.GEMINI_API_KEY
            }
            "Mistral" -> {
                sharedPrefs.getString("mistral_key", "") ?: ""
            }
            "OpenRouter" -> {
                sharedPrefs.getString("openrouter_key", "") ?: ""
            }
            "Groq" -> {
                sharedPrefs.getString("groq_key", "") ?: ""
            }
            "NVIDIA" -> {
                sharedPrefs.getString("nvidia_key", "") ?: ""
            }
            "Custom OpenAI" -> {
                sharedPrefs.getString("custom_openai_key", "") ?: ""
            }
            "Ollama" -> {
                val key = sharedPrefs.getString("ollama_key", "") ?: ""
                if (key.isNotEmpty()) key else BuildConfig.OLLAMA_API_KEY
            }
            else -> ""
        }
        return rawKeys.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun getOllamaModel(context: Context): String {
        val sharedPrefs = getSecurePrefs(context)
        return sharedPrefs.getString("ollama_model", "llama3") ?: "llama3"
    }

    suspend fun callAgent(
        context: Context,
        agent: Agent,
        prompt: String,
        systemInstruction: String? = null,
        temperature: Float? = null,
        /** P2 (plan §5.1, Table 7): decode-time cliché suppression for
         *  logit_bias-capable routes (OpenAI-compatible family). Routes
         *  without bias support ignore it — the prompt constraints and the
         *  deterministic linter remain the enforcement layers there. */
        logitBias: Map<String, Int>? = null
    ): String {
        val priority = when (agent) {
            Agent.EXECUTIVE, Agent.PLOT -> RequestQueueManager.Priority.HIGH
            else -> RequestQueueManager.Priority.NORMAL
        }
        return RequestQueueManager.enqueueRequest(
            context = context,
            agentKey = agent.key,
            prompt = prompt,
            systemInstruction = systemInstruction,
            temperature = temperature,
            priority = priority
        ) {
            withContext(Dispatchers.IO) {
                val preferredProvider = getProviderForAgent(context, agent)
                val fallbackSequence = listOf(preferredProvider, "Groq", "NVIDIA", "Gemini", "Mistral", "OpenRouter", "Ollama", "Custom OpenAI").distinct()
                
                var lastException: Exception? = null

                for (provider in fallbackSequence) {
                    val keys = getApiKeys(context, provider)
                    if (provider != "Ollama" && keys.isEmpty()) {
                        Log.w("LlmOrchestrator", "Skipping $provider: no API keys configured.")
                        continue
                    }
                    
                    val keysToTry = if (keys.isEmpty()) listOf("") else keys
                    for (apiKey in keysToTry) {
                        try {
                            Log.d("LlmOrchestrator", "Attempting request via $provider with key: ${if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "default/empty"}")
                            
                            val responseText = when (provider) {
                                "Gemini" -> {
                                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                                        throw Exception("Gemini API key is invalid or empty.")
                                    }
                                    val request = GeminiRequest(
                                        contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))),
                                        systemInstruction = systemInstruction?.let { GeminiContent(role = "system", parts = listOf(GeminiPart(text = it))) },
                                        generationConfig = temperature?.let { GeminiGenerationConfig(temperature = it) }
                                    )
                                    val sharedPrefs = getSecurePrefs(context)
                                    val model = sharedPrefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
                                    val response = ApiClient.geminiService.generateContent(model, apiKey, request)
                                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                    text ?: throw Exception("Gemini returned empty text.")
                                }
                                "Mistral" -> {
                                    if (apiKey.isEmpty()) {
                                        throw Exception("Mistral API key is empty.")
                                    }
                                    val sharedPrefs = getSecurePrefs(context)
                                    val model = sharedPrefs.getString("mistral_model", "mistral-small-latest") ?: "mistral-small-latest"
                                    val request = MistralRequest(
                                        model = model,
                                        messages = listOf(
                                            MistralMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            MistralMessage(role = "user", content = prompt)
                                        ),
                                        temperature = temperature
                                    )
                                    val response = ApiClient.mistralService.generateContent("Bearer $apiKey", request)
                                    val text = response.choices?.firstOrNull()?.message?.content
                                    text ?: throw Exception("Mistral returned empty text.")
                                }
                                "OpenRouter" -> {
                                    if (apiKey.isEmpty()) {
                                        throw Exception("OpenRouter API key is empty.")
                                    }
                                    val sharedPrefs = getSecurePrefs(context)
                                    val model = sharedPrefs.getString("openrouter_model", "google/gemini-2.0-flash-exp:free") ?: "google/gemini-2.0-flash-exp:free"
                                    val request = OpenRouterRequest(
                                        model = model,
                                        messages = listOf(
                                            OpenRouterMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            OpenRouterMessage(role = "user", content = prompt)
                                        ),
                                        temperature = temperature
                                    )
                                    val response = ApiClient.openRouterService.generateContent("Bearer $apiKey", request)
                                    val text = response.choices?.firstOrNull()?.message?.content
                                    text ?: throw Exception("OpenRouter returned empty text.")
                                }
                                "Groq" -> {
                                    if (apiKey.isEmpty()) {
                                        throw Exception("Groq API key is empty.")
                                    }
                                    val sharedPrefs = getSecurePrefs(context)
                                    val model = sharedPrefs.getString("groq_model", "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b"
                                    val request = OpenAiChatRequest(
                                        model = model,
                                        messages = listOf(
                                            OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            OpenAiChatMessage(role = "user", content = prompt)
                                        ),
                                        temperature = temperature,
                                        top_p = 0.95f,
                                        logit_bias = logitBias?.takeIf { it.isNotEmpty() }
                                    )
                                    val response = ApiClient.openAiCompatibleService.generateContent(
                                        "https://api.groq.com/openai/v1/chat/completions",
                                        "Bearer $apiKey",
                                        request
                                    )
                                    val text = response.choices?.firstOrNull()?.message?.content
                                    text ?: throw Exception("Groq returned empty text.")
                                }
                                "NVIDIA" -> {
                                    if (apiKey.isEmpty()) {
                                        throw Exception("NVIDIA API key is empty.")
                                    }
                                    val sharedPrefs = getSecurePrefs(context)
                                    val model = sharedPrefs.getString("nvidia_model", "mistralai/mistral-nemotron") ?: "mistralai/mistral-nemotron"
                                    val request = OpenAiChatRequest(
                                        model = model,
                                        messages = listOf(
                                            OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            OpenAiChatMessage(role = "user", content = prompt)
                                        ),
                                        temperature = temperature ?: 0.6f,
                                        top_p = 0.7f,
                                        max_tokens = 4096,
                                        logit_bias = logitBias?.takeIf { it.isNotEmpty() }
                                    )
                                    val response = ApiClient.openAiCompatibleService.generateContent(
                                        "https://integrate.api.nvidia.com/v1/chat/completions",
                                        "Bearer $apiKey",
                                        request
                                    )
                                    val text = response.choices?.firstOrNull()?.message?.content
                                    text ?: throw Exception("NVIDIA returned empty text.")
                                }
                                "Custom OpenAI" -> {
                                    if (apiKey.isEmpty()) {
                                        throw Exception("Custom OpenAI API key is empty.")
                                    }
                                    val sharedPrefs = getSecurePrefs(context)
                                    val baseUrl = sharedPrefs.getString("custom_openai_base_url", "https://integrate.api.nvidia.com/v1") ?: "https://integrate.api.nvidia.com/v1"
                                    val model = sharedPrefs.getString("custom_openai_model", "deepseek-ai/deepseek-v4-flash") ?: "deepseek-ai/deepseek-v4-flash"
                                    val finalUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
                                    val request = OpenAiChatRequest(
                                        model = model,
                                        messages = listOf(
                                            OpenAiChatMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            OpenAiChatMessage(role = "user", content = prompt)
                                        ),
                                        temperature = temperature,
                                        logit_bias = logitBias?.takeIf { it.isNotEmpty() }
                                    )
                                    val response = ApiClient.openAiCompatibleService.generateContent(
                                        finalUrl,
                                        "Bearer $apiKey",
                                        request
                                    )
                                    val text = response.choices?.firstOrNull()?.message?.content
                                    text ?: throw Exception("Custom OpenAI provider returned empty text.")
                                }
                                else -> {
                                    // Ollama
                                    val actualModel = getOllamaModel(context)
                                    val request = OllamaRequest(
                                        model = actualModel,
                                        messages = listOf(
                                            OllamaMessage(role = "system", content = systemInstruction ?: "You are ${agent.displayName}."),
                                            OllamaMessage(role = "user", content = prompt)
                                        ),
                                        options = temperature?.let { OllamaOptions(temperature = it) }
                                    )
                                    val authHeader = if (apiKey.isNotEmpty()) "Bearer $apiKey" else "Bearer none"
                                    val sharedPrefs = getSecurePrefs(context)
                                    val baseUrl = sharedPrefs.getString("ollama_base_url", "https://api.ollama.com/") ?: "https://api.ollama.com/"
                                    val finalOllamaUrl = if (baseUrl.endsWith("/")) "${baseUrl}api/chat" else "$baseUrl/api/chat"
                                    val response = ApiClient.ollamaService.generateContent(finalOllamaUrl, authHeader, request)
                                    val text = response.message?.content
                                    text ?: throw Exception("Ollama returned empty text.")
                                }
                            }
                            return@withContext responseText
                        } catch (e: retrofit2.HttpException) {
                            val msg = when (e.code()) {
                                401 -> "HTTP 401 Unauthorized: The API key for $provider is invalid or expired."
                                403 -> "HTTP 403 Forbidden: Access denied for $provider."
                                404 -> "HTTP 404 Not Found: The specified model for $provider does not exist or endpoint is incorrect."
                                413 -> "HTTP 413 Payload Too Large: The context or prompt exceeded $provider's token limit."
                                429 -> "HTTP 429 Too Many Requests: Rate limit exceeded for $provider."
                                500, 502, 503, 504 -> "HTTP ${e.code()}: Server error on $provider's side."
                                else -> "HTTP ${e.code()}: $provider request failed."
                            }
                            Log.w("LlmOrchestrator", "🔄 Multi-Provider Failover Orchestrator: $msg. Transitioning to next available provider...")
                            lastException = Exception(msg, e)
                        } catch (e: Exception) {
                            Log.w("LlmOrchestrator", "🔄 Multi-Provider Failover Orchestrator: Provider $provider failed (${e.message}). Transitioning to next available provider...")
                            lastException = e
                        }
                    }
                }
                
                throw lastException ?: Exception("All configured AI providers and keys failed to reply.")
            }
        }
    }
}
