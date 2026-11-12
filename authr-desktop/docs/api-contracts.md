# API Contracts

This app's "APIs" are (a) the outbound LLM/TTS provider calls and (b) the inbound consumption contract of its own engine layer. There is no server-side API (FACT: no HTTP-serving code in the repo).

## LLM orchestrator — `LlmOrchestrator.callAgent`

```kotlin
suspend fun callAgent(
    context: Context,
    agent: Agent,                       // EXECUTIVE | PLOT | CHARACTER | WORLD | SCENE | CONTINUITY
    prompt: String,
    systemInstruction: String? = null,
    temperature: Float? = null,
    logitBias: Map<String, Int>? = null // P2: OpenAI-compatible routes only
): String
```
FACT: `data/api/LlmOrchestrator.kt`. Returns the provider's text; throws the last provider exception after the full failover sequence. Calls are queued by `RequestQueueManager` (priority HIGH for EXECUTIVE/PLOT, else NORMAL).

### Agents → priority (FACT)

| Agent | Key | Queue priority | Default provider |
| --- | --- | --- | --- |
| Executive Orchestrator | `executive` | HIGH | Gemini |
| Plot Architect | `plot` | HIGH | Gemini |
| Character Psychologist | `character` | NORMAL | Gemini |
| World Builder | `world` | NORMAL | Gemini |
| Scene Composer | `scene` | NORMAL | Gemini |
| Continuity Manager | `continuity` | NORMAL | Gemini |

### Provider routes (FACT; order = failover sequence: preferred, then Groq, NVIDIA, Gemini, Mistral, OpenRouter, Ollama, Custom OpenAI — `distinct()`)

| Provider | Endpoint | Auth | Model source | Notes |
| --- | --- | --- | --- | --- |
| Gemini | `POST https://generativelanguage.googleapis.com/…generateContent` | `?key=` query param | pref `gemini_model` (default `gemini-1.5-flash`) | Rejects placeholder key `MY_GEMINI_API_KEY` |
| Mistral | `POST https://api.mistral.ai/…chat/completions` | `Authorization: Bearer` | pref `mistral_model` (`mistral-small-latest`) | |
| OpenRouter | `POST https://openrouter.ai/…chat/completions` | Bearer | pref `openrouter_model` (`google/gemini-2.0-flash-exp:free`) | |
| Groq | `POST https://api.groq.com/openai/v1/chat/completions` | Bearer | pref `groq_model` (`openai/gpt-oss-120b`) | `top_p=0.95`; supports `logit_bias` |
| NVIDIA | `POST https://integrate.api.nvidia.com/v1/chat/completions` | Bearer | pref `nvidia_model` (`mistralai/mistral-nemotron`) | `temperature ?: 0.6`, `top_p=0.7`, `max_tokens=4096`; supports `logit_bias` |
| Custom OpenAI | `<baseUrl>/chat/completions` (user-set base URL) | Bearer | prefs `custom_openai_model` + `custom_openai_base_url` | Supports `logit_bias` |
| Ollama | `<baseUrl>/api/chat` (default `https://api.ollama.com/`) | `Bearer <key>` or `Bearer none` | pref `ollama_model` (`llama3`) | No key required |

FACT: `logit_bias` is included in the JSON **only when non-null** (`OpenAiChatRequest`, `data/api/…Models.kt` — Moshi adapter untouched). Gemini/Mistral/OpenRouter/Ollama ignore `logitBias` (prompt constraints + deterministic linter are the enforcement there — plan Table 7).

### Error format (normalized to messages, FACT: HTTP-exception mapping in `callAgent`)

`HTTP <code>: <meaning>` — 401 invalid key, 403 denied, 404 bad model/endpoint, 413 payload too large, 429 rate limit, 5xx server error. Everything else: `HTTP <code>: <provider> request failed.` Non-HTTP failures surface their exception message. All failures are WARN-logged with the `🔄 Multi-Provider Failover Orchestrator` prefix.

### Which consumers depend on each route (FACT: call sites)

| Consumer | Call | Depends on |
| --- | --- | --- |
| `worker/OutlineWorker` | PLOT (serial outline prompt when `book.isSerialized`) | Gemini-class text quality; outline parse regex `(\d+)\.\s*(.*?)(?:[:\-])\s*(.*)` |
| `worker/DraftingWorker` | SCENE (chapter prose; Blueprint 2.0 block for serials) | long-context window |
| `worker/PolishingWorker` | SCENE/L5 | |
| `worker/WorldBuildingWorker` | WORLD | |
| `worker/CompilationWorker` | EXECUTIVE (cover prompt, marketing) | |
| `ui/viewmodel/StudioViewModel` | EXECUTIVE/PLOT/… wizard calls | |
| `engine/L4LlmDraftGenerator` | SCENE with `logitBias` payload (P2) | bias-capable routes degrade gracefully |
| `ui/viewmodel/SettingsViewModel` + `data/api/ModelDiscoveryService` | GET model lists per provider | settings UI only |

## TTS contracts (FACT: `tts/*`, `desktopbridge/*`)

| Engine | Contract |
| --- | --- |
| Pocket TTS (local) | Child-process invocation of the `pocket-tts` binary; resolution: bundled `<app>/pocket-tts/<os>/` → env `POCKET_TTS_BIN` → PATH → `~/.authr/pocket-tts/`; voice packs from a packs dir; `Status.MISSING` when absent (no crash). |
| AndroidTtsManager | desktop shim of the Android TTS API (`android/speech/tts/TextToSpeech.kt` shim — routes synthesis through PocketTtsManager since the Edge removal, 2026-11-12). |
| CloudTtsManager | ElevenLabs / Google TTS via `BuildConfig` keys (`ELEVENLABS_API_KEY`, `GOOGLE_TTS_API_KEY`). |
| ffmpeg | `FfmpegLocator`: bundled → env `FFMPEG_HOME` → PATH; audio-only surface (video path removed in P0, SCR-2). |

## The engine-side "API" — what every consumer must honor

| Contract | Definition | Consumers |
| --- | --- | --- |
| Canon gate | `WriterGate.isCanon(chapter) == writerConfirmed && draftStatus == CONFIRMED` | ledgers, exports, compile, annotator, advisor, meters, buffers |
| Suite report | `SerialValidatorSuite.run(Input): Report` with `checks[Check(id, chapter, severity PASS/INFO/WARN/HARD, message, key)]`, `warnings`, `hardBlocks`, `exitGateGreen` | SerialDashboardViewModel, CompilationWorker, tests |
| Scorecard JSON | `qcScores = {"c1".."c7"}` + `qcTotal` 0–14 | dashboard editor, compile gates |
| Worksheet JSON | `{"A_objective","B_character","C_plot","D_reader","E_ending"}` | DraftingWorker prompt block, dashboard editor, export packages |
| Arc phaseBounds JSON | `{"setup":[a,b],…}` | dashboard arc board, planner checks |
| Release marker | `Chapter.status` starts with `"Released"` + `(ISO date)` | BufferCadence, dashboard chapter chips |
| `.authr` container | schema v2 zip (manifest + metadata/book.json + manuscripts + bible + lorebook + arcs + guns + questions + plugins/voice-pack dirs copied verbatim) | import/export UI + tests |

## Clients/consumers of each outbound call

Everything outbound is called only from the desktop app itself (workers + ViewModels). INFERRED: no other process consumes this app's data; the `.authr` container is the interchange format with the (future) Android edition (plan §12).
