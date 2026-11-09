# Configuration & Environment Variables

## Environment Variables & Secrets (Build / Secret Panel)

Environment variables are managed through the Secrets Gradle Plugin (`app/build.gradle.kts:10, 74-77`) reading from `.env` and `.env.example` to inject constants into `BuildConfig`.

| Variable Name | Purpose | Where Used | Required? | What Breaks If Missing or Wrong | Status |
|---|---|---|---|---|---|
| `GEMINI_API_KEY` | Default fallback API key for Google Gemini model inference. | `app/src/main/java/com/example/data/api/LlmOrchestrator.kt:53`, `StudioViewModel.kt:1448` | Optional (if user configures key in Settings UI). | Gemini requests fail with HTTP 400/403 or throw missing key error unless another key is in Settings. [FACT] |
| `HF_API_KEY` | Hugging Face Access Token for Cloud TTS model synthesis. | `app/src/main/java/com/example/ui/screens/SettingsScreen.kt:823`, `StudioViewModel.kt:1279` | Optional | Cloud HuggingFace TTS fails with HTTP 401 Unauthorized; falls back to Android native TTS. [FACT] |
| `OLLAMA_API_KEY` | API Key for Ollama Cloud endpoints or authenticated Ollama reverse proxies. | `app/src/main/java/com/example/data/api/LlmOrchestrator.kt:72`, `StudioViewModel.kt:1453` | Optional | Ollama calls fail if endpoint requires Bearer token auth. [FACT] |
| `KEYSTORE_PATH` | Path to release signing keystore file for production APK/AAB builds. | `app/build.gradle.kts:29` | Optional (defaults to `${rootDir}/my-upload-key.jks`) | Release build fails signing if keystore is missing and release build is invoked. [FACT] |
| `STORE_PASSWORD` | Keystore password for release signing. | `app/build.gradle.kts:31` | Required for Release APK | Release packaging task fails signing verification. [FACT] |
| `KEY_PASSWORD` | Key alias password for release signing. | `app/build.gradle.kts:33` | Required for Release APK | Release packaging task fails signing verification. [FACT] |

---

## SharedPreferences & Feature Flags (`secure_studio_settings`)

Stored via `EncryptedSharedPreferences` (falling back to standard SharedPreferences if Keystore is unavailable) (`LlmOrchestrator.kt:30-45`, `SettingsViewModel.kt:22-120`).

| Key Name | Default Value | Purpose & Impact | Where Used | Status |
|---|---|---|---|---|
| `gemini_key` | `""` | User-provided Google Gemini API key. Overrides `BuildConfig.GEMINI_API_KEY`. | `LlmOrchestrator.kt:52`, `SettingsViewModel.kt:33` | FACT |
| `gemini_model` | `"gemini-1.5-flash"` | Active model identifier for Gemini calls. | `SettingsViewModel.kt:36` | FACT |
| `mistral_key` | `""` | API key for Mistral AI. | `LlmOrchestrator.kt:56`, `SettingsViewModel.kt:42` | FACT |
| `mistral_model` | `"mistral-small-latest"` | Active model identifier for Mistral completions. | `SettingsViewModel.kt:45` | FACT |
| `openrouter_key`| `""` | API key for OpenRouter aggregator. | `LlmOrchestrator.kt:59`, `SettingsViewModel.kt:48` | FACT |
| `openrouter_model`| `"google/gemini-2.0-flash-exp:free"` | Target model identifier on OpenRouter. | `SettingsViewModel.kt:51` | FACT |
| `groq_key` | `""` | API key for Groq Cloud inference. | `LlmOrchestrator.kt:62`, `SettingsViewModel.kt:54` | FACT |
| `groq_model` | `"openai/gpt-oss-120b"` | Target model identifier on Groq. | `SettingsViewModel.kt:57` | FACT |
| `nvidia_key` | `""` | API key for NVIDIA NIM API. | `LlmOrchestrator.kt:65`, `SettingsViewModel.kt:60` | FACT |
| `nvidia_model` | `"mistralai/mistral-nemotron"` | Target model identifier on NVIDIA NIM. | `SettingsViewModel.kt:63` | FACT |
| `custom_openai_key` | `""` | API key for custom OpenAI-compatible endpoint (e.g. OmniRoute, vLLM). | `LlmOrchestrator.kt:68`, `SettingsViewModel.kt:66` | FACT |
| `custom_openai_base_url` | `"https://integrate.api.nvidia.com/v1"` | Base URL for custom OpenAI-compatible endpoint. | `SettingsViewModel.kt:69` | FACT |
| `custom_openai_model` | `"deepseek-ai/deepseek-v4-flash"` | Target model name on custom endpoint. | `SettingsViewModel.kt:72` | FACT |
| `ollama_key` | `""` | API key for Ollama instance. | `LlmOrchestrator.kt:71`, `SettingsViewModel.kt:75` | FACT |
| `ollama_base_url` | `"https://api.ollama.com/"` | URL to reachable Ollama server. | `SettingsViewModel.kt:81` | FACT |
| `ollama_model` | `"gemma4:31b"` | Model tag to load in Ollama. | `SettingsViewModel.kt:78` | FACT |
| `provider` | `"Gemini"` | Default preferred provider across the studio. | `SettingsViewModel.kt:90` | FACT |
| `agent_provider_{key}` | `"Gemini"` | Per-agent provider override (`executive`, `plot`, `character`, `world`, `scene`, `continuity`). | `LlmOrchestrator.kt:20-28` | FACT |
| `tts_engine` | `"Cloud HuggingFace TTS"` | Active speech engine: `"Android"`, `"Cloud HuggingFace TTS"`, or `"Edge TTS"`. | `SettingsViewModel.kt:93` | FACT |
| `queue_concurrency` | `2` | Max parallel network LLM calls allowed in `RequestQueueManager`. | `RequestQueueManager.kt:68`, `SettingsViewModel.kt:186` | FACT |
| `queue_rpm` | `15` | Requests-per-minute throttle ceiling. | `RequestQueueManager.kt:74`, `SettingsViewModel.kt:189` | FACT |
| `app_theme` | `"Sepia"` | UI theme: `"Sepia"`, `"Default"`, `"Obsidian"`, `"Cream"`, `"Charcoal"`. | `MainActivity.kt:69`, `SettingsViewModel.kt:102` | FACT |
| `cleanup_frequency` | `"Weekly"` | Schedule frequency for periodic `StorageCleanupWorker`. | `SettingsViewModel.kt:246` | FACT |
| `max_projects_count` | `10` | Project quota; oldest books purged if exceeded during cleanup. | `StorageCleanupWorker.kt:115-125`, `SettingsViewModel.kt:264` | FACT |
