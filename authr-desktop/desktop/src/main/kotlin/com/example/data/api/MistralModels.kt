package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class MistralRequest(
    val model: String = "mistral-small-latest",
    val messages: List<MistralMessage>,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = false)
data class MistralMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = false)
data class MistralResponse(
    val choices: List<MistralChoice>?
)

@JsonClass(generateAdapter = false)
data class MistralChoice(
    val message: MistralMessage?
)
