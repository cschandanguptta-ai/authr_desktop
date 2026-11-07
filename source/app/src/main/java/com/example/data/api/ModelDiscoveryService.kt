package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ModelDiscoveryService {

    private const val TAG = "ModelDiscoveryService"

    suspend fun fetchModelsForProvider(
        provider: String,
        apiKey: String,
        baseUrl: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val models = when (provider) {
                "Groq" -> fetchGroqModels(apiKey)
                "NVIDIA" -> fetchNvidiaModels(apiKey)
                "Gemini" -> fetchGeminiModels(apiKey)
                "Mistral" -> fetchMistralModels(apiKey)
                "OpenRouter" -> fetchOpenRouterModels(apiKey)
                "Ollama" -> fetchOllamaModels(apiKey, baseUrl)
                "Custom OpenAI" -> fetchCustomOpenAiModels(apiKey, baseUrl)
                else -> emptyList()
            }

            if (models.isNotEmpty()) {
                Result.success(models.distinct())
            } else {
                val fallbacks = getFallbackModelsForProvider(provider)
                Result.success(fallbacks)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to auto-fetch models for $provider: ${e.message}")
            val fallbacks = getFallbackModelsForProvider(provider)
            Result.success(fallbacks)
        }
    }

    private suspend fun fetchGroqModels(apiKey: String): List<String> {
        val auth = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        val responseBody = ApiClient.openAiCompatibleService.listModels("https://api.groq.com/openai/v1/models", auth)
        val json = JSONObject(responseBody.string())
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotEmpty() && !id.contains("whisper") && !id.contains("audio")) {
                list.add(id)
            }
        }
        return list.sorted()
    }

    private suspend fun fetchNvidiaModels(apiKey: String): List<String> {
        val auth = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        val responseBody = ApiClient.openAiCompatibleService.listModels("https://integrate.api.nvidia.com/v1/models", auth)
        val json = JSONObject(responseBody.string())
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotEmpty()) {
                list.add(id)
            }
        }
        return list.sorted()
    }

    private suspend fun fetchGeminiModels(apiKey: String): List<String> {
        val responseBody = ApiClient.geminiService.listModels(apiKey)
        val json = JSONObject(responseBody.string())
        val models = json.optJSONArray("models") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val cleanName = name.removePrefix("models/")
            if (cleanName.startsWith("gemini") && !cleanName.contains("vision") && !cleanName.contains("embedding")) {
                list.add(cleanName)
            }
        }
        return list.sorted()
    }

    private suspend fun fetchMistralModels(apiKey: String): List<String> {
        val auth = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        val responseBody = ApiClient.mistralService.listModels(auth)
        val json = JSONObject(responseBody.string())
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotEmpty() && !id.contains("embed")) {
                list.add(id)
            }
        }
        return list.sorted()
    }

    private suspend fun fetchOpenRouterModels(apiKey: String): List<String> {
        val auth = if (apiKey.isNotEmpty()) {
            if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        } else ""
        val responseBody = ApiClient.openRouterService.listModels(auth)
        val json = JSONObject(responseBody.string())
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotEmpty()) {
                list.add(id)
            }
        }
        // Prioritize free models first
        val freeModels = list.filter { it.endsWith(":free") }.sorted()
        val standardModels = list.filter { !it.endsWith(":free") }.sorted().take(40)
        return freeModels + standardModels
    }

    private suspend fun fetchOllamaModels(apiKey: String, baseUrl: String): List<String> {
        val cleanBaseUrl = baseUrl.ifBlank { "https://api.ollama.com/" }.trimEnd('/')
        val url = if (cleanBaseUrl.endsWith("/api/tags")) cleanBaseUrl else "$cleanBaseUrl/api/tags"
        val auth = if (apiKey.isNotBlank()) {
            if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        } else ""
        val responseBody = ApiClient.ollamaService.listTags(url, auth)
        val json = JSONObject(responseBody.string())
        val models = json.optJSONArray("models") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val name = item.optString("name", item.optString("model", ""))
            if (name.isNotEmpty()) {
                list.add(name)
            }
        }
        return list.sorted()
    }

    private suspend fun fetchCustomOpenAiModels(apiKey: String, baseUrl: String): List<String> {
        val cleanBaseUrl = baseUrl.ifBlank { "https://integrate.api.nvidia.com/v1" }.trimEnd('/')
        val url = if (cleanBaseUrl.endsWith("/models")) cleanBaseUrl else "$cleanBaseUrl/models"
        val auth = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        val responseBody = ApiClient.openAiCompatibleService.listModels(url, auth)
        val json = JSONObject(responseBody.string())
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotEmpty()) {
                list.add(id)
            }
        }
        return list.sorted()
    }

    fun getFallbackModelsForProvider(provider: String): List<String> {
        return when (provider) {
            "Groq" -> listOf(
                "llama-3.3-70b-versatile",
                "openai/gpt-oss-120b",
                "llama-3.1-8b-instant",
                "mixtral-8x7b-32768",
                "deepseek-r1-distill-llama-70b"
            )
            "NVIDIA" -> listOf(
                "mistralai/mistral-nemotron",
                "meta/llama-3.3-70b-instruct",
                "google/gemma-2-27b-it",
                "deepseek-ai/deepseek-r1",
                "nvidia/llama-3.1-nemotron-70b-instruct"
            )
            "Gemini" -> listOf(
                "gemini-1.5-flash",
                "gemini-1.5-pro",
                "gemini-2.0-flash-exp",
                "gemini-2.5-flash"
            )
            "Mistral" -> listOf(
                "mistral-small-latest",
                "mistral-large-latest",
                "codestral-latest",
                "open-mixtral-8x22b"
            )
            "OpenRouter" -> listOf(
                "google/gemini-2.0-flash-exp:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "deepseek/deepseek-chat:free",
                "mistralai/mistral-7b-instruct:free"
            )
            "Ollama" -> listOf(
                "gemma4:31b",
                "llama3:latest",
                "mistral:latest",
                "qwen2.5:latest",
                "deepseek-r1:latest"
            )
            "Custom OpenAI" -> listOf(
                "deepseek-ai/deepseek-v4-flash",
                "gpt-4o",
                "gpt-4o-mini",
                "claude-3-5-sonnet"
            )
            else -> emptyList()
        }
    }
}
