package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class OllamaRequest(
    val model: String = "llama3",
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@JsonClass(generateAdapter = false)
data class OllamaOptions(
    val temperature: Float? = null
)

@JsonClass(generateAdapter = false)
data class OllamaMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = false)
data class OllamaResponse(
    val model: String?,
    val created_at: String?,
    val message: OllamaMessage?,
    val done: Boolean?
)
