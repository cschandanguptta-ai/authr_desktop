package com.example

import java.io.File

/**
 * Desktop replacement for the AGP-generated BuildConfig.
 *
 * Mirrors the Android build: secrets are read from (in order)
 *   1. a `.env` file next to the app / in the working directory
 *      (same format as .env.example: KEY=VALUE)
 *   2. real environment variables
 * so CI and local runs behave identically to the Android secrets plugin.
 */
object BuildConfig {

    const val DEBUG: Boolean = true
    const val APPLICATION_ID: String = "com.aistudio.audionovelstudio.nxkpt"
    const val BUILD_TYPE: String = "debug"
    const val VERSION_NAME: String = "3.0.0-alpha"
    const val VERSION_CODE: Int = 30

    val GEMINI_API_KEY: String by lazy { resolveKey("GEMINI_API_KEY", "MY_GEMINI_API_KEY") }
    val HF_API_KEY: String by lazy { resolveKey("HF_API_KEY", "MY_HF_API_KEY") }
    val OLLAMA_API_KEY: String by lazy { resolveKey("OLLAMA_API_KEY", "MY_OLLAMA_API_KEY") }
    val ELEVENLABS_API_KEY: String by lazy { resolveKey("ELEVENLABS_API_KEY", "") }
    val GOOGLE_TTS_API_KEY: String by lazy { resolveKey("GOOGLE_TTS_API_KEY", "") }

    private val envFileCache: Map<String, String> by lazy { loadDotEnv() }

    private fun loadDotEnv(): Map<String, String> {
        val candidates = listOf(
            File(System.getProperty("user.dir"), ".env"),
            File(System.getProperty("user.home"), ".authr/.env"),
            File(System.getProperty("user.home"), ".env")
        )
        for (f in candidates) {
            if (!f.exists()) continue
            try {
                val map = HashMap<String, String>()
                f.readLines().forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val k = line.substring(0, idx).trim()
                        val v = line.substring(idx + 1).trim().trim('"')
                        map[k] = v
                    }
                }
                return map
            } catch (e: Exception) {
                android.util.Log.w("BuildConfig", "failed reading ${f.path}: ${e.message}")
            }
        }
        return emptyMap()
    }

    private fun resolveKey(name: String, default: String): String {
        System.getenv(name)?.let { return it }
        envFileCache[name]?.let { return it }
        return default
    }
}
