package com.example.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import com.squareup.moshi.JsonClass

class RetryInterceptor(private val maxRetries: Int = 3, private val initialDelayMillis: Long = 1000L) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response: Response? = null
        var exception: IOException? = null
        var tryCount = 0

        while (tryCount < maxRetries) {
            try {
                response = chain.proceed(chain.request())
                if (response.isSuccessful || (response.code in 400..499 && response.code != 429)) {
                    return response
                }
            } catch (e: IOException) {
                exception = e
            }

            tryCount++
            if (tryCount < maxRetries) {
                response?.close()
                try {
                    val delay = initialDelayMillis * (1L shl (tryCount - 1))
                    Thread.sleep(delay)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        return response ?: throw exception ?: IOException("Failed after $maxRetries retries")
    }
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): okhttp3.ResponseBody
}

interface OllamaApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OllamaRequest
    ): OllamaResponse

    @GET
    suspend fun listTags(
        @Url url: String,
        @Header("Authorization") authHeader: String = ""
    ): okhttp3.ResponseBody
}

interface MistralApiService {
    @POST("v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: MistralRequest
    ): MistralResponse

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authHeader: String
    ): okhttp3.ResponseBody
}

interface HuggingFaceApiService {
    @GET("api/whoami-v2")
    suspend fun whoami(
        @Header("Authorization") authHeader: String
    ): okhttp3.ResponseBody

    @POST("models/{model}")
    @Streaming
    suspend fun synthesizeText(
        @Path("model") model: String,
        @Header("Authorization") authHeader: String,
        @Body request: HuggingFaceTtsRequest
    ): okhttp3.ResponseBody
}

interface OpenRouterApiService {
    @POST("api/v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: OpenRouterRequest
    ): OpenRouterResponse

    @GET("api/v1/models")
    suspend fun listModels(
        @Header("Authorization") authHeader: String = ""
    ): okhttp3.ResponseBody
}

interface OpenAiCompatibleApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): okhttp3.ResponseBody
}

@JsonClass(generateAdapter = false)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null,
    /** v3.0 P2 (plan §5.1, Table 7): decode-time cliché suppression on the
     *  OpenAI-compatible family. Keys are offender token encodings, values
     *  the additive bias (−5). Null on routes without bias support. */
    val logit_bias: Map<String, Int>? = null
)

@JsonClass(generateAdapter = false)
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = false)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>?
)

@JsonClass(generateAdapter = false)
data class OpenAiChoice(
    val message: OpenAiChatMessage?
)

@JsonClass(generateAdapter = false)
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = false)
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = false)
data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>?
)

@JsonClass(generateAdapter = false)
data class OpenRouterChoice(
    val message: OpenRouterMessage?
)

@JsonClass(generateAdapter = false)
data class HuggingFaceTtsRequest(val inputs: String)

object ApiClient {

    /** Desktop port: Kotlin reflection adapter so @JsonClass data classes
     * with default values serialize identically to the codegen path. */
    val moshi: com.squareup.moshi.Moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (com.example.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .addInterceptor(RetryInterceptor())
            .build()
    }

    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val ollamaService: OllamaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.ollama.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaApiService::class.java)
    }

    val mistralService: MistralApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mistral.ai/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MistralApiService::class.java)
    }

    val huggingFaceService: HuggingFaceApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HuggingFaceApiService::class.java)
    }

    val openRouterService: OpenRouterApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenRouterApiService::class.java)
    }

    val openAiCompatibleService: OpenAiCompatibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiCompatibleApiService::class.java)
    }
}
