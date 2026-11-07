package com.example.utils

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.regex.Pattern

object JsonStructuralEnforcer {

    private const val TAG = "JsonStructuralEnforcer"

    /**
     * Enforces that the output string is a valid JSON matching the metadata schema.
     * If the string is malformed, has invalid syntax, or is missing keys, this method
     * repairs it or extracts values using regex fallbacks to build a guaranteed valid JSON.
     */
    fun enforceMetadataJson(rawInput: String): String {
        val trimmed = rawInput.trim()
        val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter<Map<String, Any>>(type)

        // Try direct parsing first
        try {
            val startIdx = trimmed.indexOf("{")
            val endIdx = trimmed.lastIndexOf("}")
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val candidateJson = trimmed.substring(startIdx, endIdx + 1)
                val parsed = adapter.fromJson(candidateJson)
                if (parsed != null && hasAllKeys(parsed)) {
                    Log.d(TAG, "Direct JSON parse successful and complete.")
                    return candidateJson
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct JSON parse failed or incomplete. Falling back to repair logic: ${e.message}")
        }

        // Regex/String-matching fallback loop
        Log.d(TAG, "Executing regex extraction repair loop...")
        val title = extractValue(trimmed, "title", "Title", "High-Quality Audio Novel Production")
        val description = extractValue(trimmed, "description", "Description", "Full audio novel generated using Authr OS.")
        val tags = extractValue(trimmed, "tags", "Tags", "audio novel, audiobook, web novel, voice acting")
        val thumbnailPrompt = extractValue(trimmed, "thumbnail_prompt", "Thumbnail Prompt", "A dramatic, high-contrast comic book illustration of the hero.")

        // Build guaranteed clean JSON
        return buildSafeJson(title, description, tags, thumbnailPrompt)
    }

    private fun hasAllKeys(map: Map<*, *>): Boolean {
        return map.containsKey("title") &&
               map.containsKey("description") &&
               map.containsKey("tags") &&
               map.containsKey("thumbnail_prompt")
    }

    private fun extractValue(text: String, jsonKey: String, humanKey: String, fallback: String): String {
        // Pattern 1: JSON-style key-value with double quotes: "key" : "value"
        val jsonPattern = Pattern.compile(
            "\"$jsonKey\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = jsonPattern.matcher(text)
        if (matcher.find()) {
            val matched = matcher.group(1)?.trim()
            if (!matched.isNullOrEmpty()) return matched
        }

        // Pattern 2: JSON-style key-value without closing quote or line-broken: "key" : "value...
        val jsonSoftPattern = Pattern.compile(
            "\"$jsonKey\"\\s*:\\s*\"(.*)",
            Pattern.CASE_INSENSITIVE
        )
        matcher = jsonSoftPattern.matcher(text)
        if (matcher.find()) {
            val line = matcher.group(1)?.trim() ?: ""
            val cleanLine = line.substringBefore("\"").trim()
            if (cleanLine.isNotEmpty()) return cleanLine
        }

        // Pattern 3: Human-style key-value like "Title: some title" or "**Title**: some title"
        val humanPattern = Pattern.compile(
            "(?:\\*\\*)?$humanKey(?:\\*\\*)?\\s*:\\s*([^\\n]*)",
            Pattern.CASE_INSENSITIVE
        )
        matcher = humanPattern.matcher(text)
        if (matcher.find()) {
            var matched = matcher.group(1)?.trim() ?: ""
            // Clean markdown quotes/styles
            matched = matched.removePrefix("\"").removeSuffix("\"")
                .removePrefix("'").removeSuffix("'").trim()
            if (matched.isNotEmpty()) return matched
        }

        // Pattern 4: Broad list format (e.g., "1. Title: some title")
        val listPattern = Pattern.compile(
            "\\d+\\.\\s*$humanKey\\s*:\\s*([^\\n]*)",
            Pattern.CASE_INSENSITIVE
        )
        matcher = listPattern.matcher(text)
        if (matcher.find()) {
            var matched = matcher.group(1)?.trim() ?: ""
            matched = matched.removePrefix("\"").removeSuffix("\"")
                .removePrefix("'").removeSuffix("'").trim()
            if (matched.isNotEmpty()) return matched
        }

        return fallback
    }

    private fun buildSafeJson(title: String, description: String, tags: String, thumbnailPrompt: String): String {
        // Escape strings for valid JSON
        val escTitle = escapeJson(title)
        val escDesc = escapeJson(description)
        val escTags = escapeJson(tags)
        val escThumb = escapeJson(thumbnailPrompt)

        return """
        {
          "title": "$escTitle",
          "description": "$escDesc",
          "tags": "$escTags",
          "thumbnail_prompt": "$escThumb"
        }
        """.trimIndent()
    }

    private fun escapeJson(str: String): String {
        val builder = StringBuilder()
        for (ch in str) {
            when (ch) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }
}
