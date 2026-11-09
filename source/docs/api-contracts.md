# External API Contracts & Integrations

## Overview
All external network communications are managed through `com.example.data.api.ApiClient` using OkHttp 4.12.0 and Retrofit 2.11.0 with automatic exponential backoff retries via `RetryInterceptor` (3 retries, initial delay 1000ms) (`ApiService.kt:14-44`). [FACT]

---

## API Contracts Table

| Provider | Endpoint | Auth Mechanism | Request Model | Response Model | Consumers / Callers | Status |
|---|---|---|---|---|---|---|
| **Google Gemini** | `POST v1beta/models/{model}:generateContent` | Query param `?key={apiKey}` | `GeminiRequest` (`contents`, `generationConfig`, `systemInstruction`) | `GeminiResponse` (`candidates[0].content.parts[0].text`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **Mistral AI** | `POST v1/chat/completions` | Header `Authorization: Bearer {apiKey}` | `MistralRequest` (`model`, `messages`, `temperature`, `max_tokens`) | `MistralResponse` (`choices[0].message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **OpenRouter** | `POST api/v1/chat/completions` | Header `Authorization: Bearer {apiKey}` | `OpenRouterRequest` (`model`, `messages`, `temperature`) | `OpenRouterResponse` (`choices[0].message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **Groq (OpenAI Compatible)** | `POST {url}/chat/completions` (default `https://api.groq.com/openai/v1/`) | Header `Authorization: Bearer {apiKey}` | `OpenAiChatRequest` (`model`, `messages`, `temperature`, `max_tokens`) | `OpenAiChatResponse` (`choices[0].message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **NVIDIA NIM (OpenAI Compatible)** | `POST {url}/chat/completions` (default `https://integrate.api.nvidia.com/v1/`) | Header `Authorization: Bearer {apiKey}` | `OpenAiChatRequest` (`model`, `messages`, `temperature`, `max_tokens`) | `OpenAiChatResponse` (`choices[0].message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **Custom OpenAI / OmniRoute Gateway** | Dynamic `@Url url` | Header `Authorization: Bearer {apiKey}` | `OpenAiChatRequest` (`model`, `messages`, `temperature`, `max_tokens`) | `OpenAiChatResponse` (`choices[0].message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **Ollama** | Dynamic `@Url url` | Header `Authorization: Bearer {apiKey}` | `OllamaRequest` (`model`, `messages`, `stream=false`) | `OllamaResponse` (`message.content`) | `LlmOrchestrator`, `StudioViewModel` | FACT |
| **Model Discovery (OpenAI/Groq/NVIDIA/Mistral/OpenRouter/Custom)** | `GET {url}/models` | Header `Authorization: Bearer {apiKey}` (or none for public/Ollama) | None | `ModelListResponse` (`data[].id`) | `ModelDiscoveryService`, `SettingsViewModel` | FACT |
| **Model Discovery (Ollama)** | `GET {url}/api/tags` | None / Header | None | `OllamaTagsResponse` (`models[].name`) | `ModelDiscoveryService`, `SettingsViewModel` | FACT |
| **Model Discovery (Gemini)** | `GET v1beta/models` | Query param `?key={apiKey}` | None | `GeminiModelListResponse` (`models[].name`) | `ModelDiscoveryService`, `SettingsViewModel` | FACT |
| **Hugging Face Inference** | `POST models/{model}` | Header `Authorization: Bearer {token}` | `HuggingFaceTtsRequest` (`inputs`) | Raw Audio Stream (`ResponseBody`) | `CloudTtsManager`, `SettingsViewModel` | FACT |
| **KittenTTS (Hugging Face Space)** | `POST https://kingnish-kitten-tts.hf.space/gradio_api/call/predict` | None (Public Gradio) | JSON: `{"data": [text, voiceName, speed]}` | JSON with event stream / WAV path | `KittenTtsManager` | FACT |
| **Edge TTS** | `wss://speech.platform.bing.com/consumer/speech/synthesize/readaheadedge/v1` | Binary Sec-MS-GEC token | WSS text message SSML `<speak>...</speak>` | Binary Audio chunks (`audio/mpeg`) | `EdgeTtsManager` | FACT |

---

## Request & Response Formats

### 1. OpenAI Compatible (Groq, NVIDIA, Custom, OmniRoute)
- **Request Body**:
  ```json
  {
    "model": "openai/gpt-oss-120b",
    "messages": [
      { "role": "system", "content": "System prompt instructions" },
      { "role": "user", "content": "User prompt text" }
    ],
    "temperature": 0.7,
    "max_tokens": 4096
  }
  ```
- **Response Body**:
  ```json
  {
    "choices": [
      {
        "message": {
          "role": "assistant",
          "content": "Generated completion text..."
        }
      }
    ]
  }
  ```

### 2. Google Gemini
- **Request Body**:
  ```json
  {
    "contents": [
      { "parts": [{ "text": "Prompt text" }] }
    ],
    "generationConfig": {
      "temperature": 0.7,
      "maxOutputTokens": 4096
    },
    "systemInstruction": {
      "parts": [{ "text": "System instruction text" }]
    }
  }
  ```
- **Response Body**:
  ```json
  {
    "candidates": [
      {
        "content": {
          "parts": [
            { "text": "Generated candidate text..." }
          ]
        }
      }
    ]
  }
  ```

---

## Error Format & Handling Contracts

- **HTTP 429 (Rate Limit)**:
  - Caught by `RetryInterceptor` (`ApiService.kt:23`) and `RequestQueueManager` (`RequestQueueManager.kt:180-210`).
  - Exponential backoff wait (`1s -> 2s -> 4s`) before failing over to the next provider in `LlmOrchestrator.fallbackSequence`. [FACT]
- **HTTP 401 / 403 (Invalid Key)**:
  - `LlmOrchestrator` logs a warning and immediately tries the next configured key or next provider in the fallback chain (`LlmOrchestrator.kt:113-145`). [FACT]
- **Timeouts**:
  - Global OkHttpClient timeouts: `connectTimeout = 60s`, `readTimeout = 60s`, `writeTimeout = 60s` (`ApiService.kt:158-160`). [FACT]
