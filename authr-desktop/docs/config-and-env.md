# Config & Env

Every env var, API key, preference key, and per-book config field. **Never include values** — names, purpose, and blast radius only. Labeling: FACT (seen in code) / INFERRED.

## Environment variables

| Name | Purpose | Where used | Required? | What breaks if missing/wrong |
| --- | --- | --- | --- | --- |
| `GEMINI_API_KEY` | Fallback Gemini key | `BuildConfig.resolveKey("GEMINI_API_KEY", placeholder)` → `LlmOrchestrator.getApiKeys` | No (secure-pref key wins) | Gemini route skipped / placeholder rejected with `Gemini API key is invalid or empty.` |
| `OLLAMA_API_KEY` | Fallback Ollama key (sent as `Bearer <key>` or `Bearer none`) | `BuildConfig` → Ollama block | No | Ollama still tried with `Bearer none` |
| `HF_API_KEY` | HuggingFace inference (translation features) | `BuildConfig.HF_API_KEY` → translation screen | No | Translation provider unavailable |
| `ELEVENLABS_API_KEY` | Cloud TTS | `BuildConfig` → `tts/CloudTtsManager` | No | That TTS engine unavailable |
| `GOOGLE_TTS_API_KEY` | Cloud TTS | `BuildConfig` → `tts/CloudTtsManager` | No | That TTS engine unavailable |
| `POCKET_TTS_BIN` | Explicit Pocket TTS binary override | `desktopbridge/PocketTtsBridge.locateBinary` | No | Bridge falls back to bundled/PATH/`~/.authr`; `Status.MISSING` if none |
| `FFMPEG_HOME` | ffmpeg install root (`$FFMPEG_HOME/bin/ffmpeg`) | `desktopbridge/FfmpegLocator` | No | Falls back to bundled binary then PATH |
| `ESPEAK_NG_BIN` | Explicit espeak-ng binary override (P4 phonemizer + local render engine) | `desktopbridge/EspeakLocator.espeakPath` | No | Falls back to bundled binary → PATH → `~/.authr/espeak-ng`; the IPA pass then finds no phonemizer (candidates drop out) and `EspeakSynthesizer` reports `espeak-ng not found` |
| `EPUBCHECK_JAR` | Explicit path to epubcheck.jar (P5 external EPUB 3 gate; lib/ siblings must sit beside it — official dist layout). Distribution decision (ADR-0013, 2026-11-11; bundle shipped 2026-11-12 per ADR-0014): locator-first — the APP IMAGE now bundles `lib/app/epubcheck/` (5.2.1 dist), so writers need NO env vars and NO install; env vars remain for gate/CI machines, `~/.authr/epubcheck` remains the manual-install path; no runtime auto-download (a gate that needs the network to pass is not a local gate) | `desktopbridge/EpubCheckLocator.epubCheckJar` | No | Falls back to `EPUBCHECK_HOME` → `<app dir>/epubcheck/` → `~/.authr/epubcheck`; with no tool the retail package still builds but the epubcheck verdict is UNVERIFIED and the ship report is NOT clean (disclosed) |
| `EPUBCHECK_HOME` | epubcheck dist directory (contains `epubcheck.jar` + `lib/`) | `desktopbridge/EpubCheckLocator.locate` | No | See `EPUBCHECK_JAR` fallback chain |
| `DISPLAY` (Linux) | Compose UI windowing | skiko/awt | Yes for UI runs | `HeadlessException` at startup (headless smoke uses Xvfb) |
| `LD_LIBRARY_PATH` | Native libs for software rendering in sandboxes | smoke harness only | No | GL fallback noise; skiko software rendering still works |

### `.env` file loading (FACT: `com/example/BuildConfig.loadDotEnv`)

Searched in order: `<working dir>/.env` → `~/.authr/.env` → `~/.env`. Simple `KEY=VALUE` lines. Values here are fallbacks behind the secure prefs.

## API keys in prefs (secure layer)

Stored via `EncryptedSharedPreferences` file `secure_studio_settings` (AES256_GCM/SIV master key); **falls back to plain prefs file `secure_studio_settings_fallback`** if the keystore fails (FACT: `LlmOrchestrator.getSecurePrefs` — see tech-debt D-03).

| Pref key | Purpose | What breaks if wrong |
| --- | --- | --- |
| `gemini_key` | Gemini key (comma-separated list allowed → round-robin) | failover to next provider |
| `mistral_key`, `openrouter_key`, `groq_key`, `nvidia_key`, `custom_openai_key` | per-provider keys | those routes skipped |
| `ollama_key` | optional Ollama auth | `Bearer none` used |
| `ollama_model`, `ollama_base_url` | model + endpoint overrides | HTTP 404 / connection refused |
| `gemini_model`, `mistral_model`, `openrouter_model`, `groq_model`, `nvidia_model`, `custom_openai_model`, `custom_openai_base_url` | model/endpoint selection | HTTP 404 (`model does not exist`) |

## Non-encrypted preference files

| File | Keys | Purpose |
| --- | --- | --- |
| `studio_settings` | `agent_provider_<executive|plot|character|world|scene|continuity>` | per-agent provider selection (FACT: `LlmOrchestrator.getProviderForAgent`) |
| other app prefs | theme, TTS engine choice, onboarding flag, HF settings | `SettingsViewModel` / `StudioViewModel` (exact keys INFERRED from usage; enumerate before changing a pref name) |

Desktop prefs live under `<dataRoot>/files/shared_prefs` (FACT: `android/content/Context.kt` prefsDir). Data root: `~/.local/share/authr` on Linux (observed).

## Database config

- DB file: `<dataRoot>/files/databases/audio_novel_studio_db` (FACT: `Context.getDatabasePath` + `AppDatabase.getDatabase`).
- Driver: Room KMP `BundledSQLiteDriver` (no OS SQLite dependency — FACT: `AppDatabase.kt`).
- Version: 15; migrations registered explicitly (see `docs/data-model.md`).

## Per-book serialization config (the "feature flags" of the serial format)

All on the `books` row; defaults apply only when `isSerialized = true` (FACT: `data/model/Book.kt`, plan Table 21):

| Field | Default | Purpose | What breaks if wrong |
| --- | --- | --- | --- |
| `isSerialized` | false | switches the serial profile on | dashboard hides the book; suite short-circuits |
| `targetChapters` | 100 (entity default 0 = unset → planner falls back to 15) | arc layout + final-act boundary math (`ChekhovLedger.finalActStartChapter`) | wrong final-act sweep point |
| `releaseSchedule` | "Mon/Wed/Fri" | cadence tracker day tokens | false slip warnings |
| `freeTierChapters` | 5 | anti-withholding + hook-validator span + free-tier phase checks | gate binds to the wrong chapter |
| `wordMin` / `wordMax` | 1000 / 2000 | advisory envelope (L4 prompt + warnings) | advisory noise only |
| `bufferTarget` | 8 (4–8) | buffer gauge target | gauge message only |
| `exportProfile` | "royal_road" | default paste-ready profile | unexpected platform formatting |

## Feature flags

There is no flag system (FACT: no flag/toggle infrastructure in the repo). Behavior switches are the per-book fields above, per-agent provider prefs, and plugin enablement (`plugins.enabled`, `plugins/PluginRegistry.kt`). Plugin library scaffolds on first launch into `<dataRoot>/authr/plugins/{structures,personas}` (FACT: `Main.kt` LaunchedEffect + `PluginRegistry.scaffoldDefaults`).

## Build-time config

- `gradle.properties`: pinned JDK path (`org.gradle.java.installations.paths`, `org.gradle.java.home`), heap caps, `parallel=false`, `workers.max=2` (sandbox-tuned; safe to relax on workstations).
- `desktop/build.gradle.kts`: `packageVersion = "3.0.0"`, targets Deb/Msi/Dmg/AppImage/Exe, main class `com.example.MainKt`.
- `com/example/BuildConfig.kt`: `VERSION_NAME = "3.0.0-alpha"`, `VERSION_CODE = 30`, `DEBUG = true` — a hand-maintained shim, not generated.
