package com.example

import java.io.File

/**
 * Desktop replacement for the AGP-generated BuildConfig.
 *
 * Mirrors the Android build: secrets are read from (in order)
 *   1. real environment variables
 *   2. a `.env` file next to the app / in the working directory
 *      (same format as .env.example: KEY=VALUE)
 * so CI and local runs behave identically.
 *
 * 2026-11-12 audit fix (#9): the defaults are EMPTY — the old build fell back
 * to the literal placeholder "MY_GEMINI_API_KEY", which the orchestrator then
 * sent to providers as if it were a real key (guaranteed HTTP 400s and a
 * misleading "configured" state). No key configured now means NO key: the
 * orchestrator skips the provider loudly ("no API keys configured").
 */
object BuildConfig {

    /** Dev flag: opt in with AUTHR_DEBUG=1 (release builds must not ship debug HTTP logging). */
    val DEBUG: Boolean = System.getenv("AUTHR_DEBUG") == "1"

    const val APPLICATION_ID: String = "com.aistudio.audionovelstudio.nxkpt"
    val BUILD_TYPE: String = if (DEBUG) "debug" else "release"
    const val VERSION_NAME: String = "3.0.0-alpha"
    const val VERSION_CODE: Int = 30

    val GEMINI_API_KEY: String by lazy { resolveKey("GEMINI_API_KEY") }
    val HF_API_KEY: String by lazy { resolveKey("HF_API_KEY") }
    val OLLAMA_API_KEY: String by lazy { resolveKey("OLLAMA_API_KEY") }
    val ELEVENLABS_API_KEY: String by lazy { resolveKey("ELEVENLABS_API_KEY") }
    val GOOGLE_TTS_API_KEY: String by lazy { resolveKey("GOOGLE_TTS_API_KEY") }

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

    /** Env var first, then the .env file — an unset key resolves to "" (never a placeholder). */
    private fun resolveKey(name: String): String {
        System.getenv(name)?.let { if (it.isNotBlank()) return it }
        envFileCache[name]?.let { if (it.isNotBlank()) return it }
        return ""
    }
}
