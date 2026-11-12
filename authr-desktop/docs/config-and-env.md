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
| `POCKET_TTS_BIN` | Explicit Pocket TTS binary override | `desktopbridge/PocketTtsBridge.locateBinary` | No | Bridge falls back to bundled/PATH/`~/.authr/pocket-tts`; `Status.MISSING` if none — the Audiobook workbench then offers espeak only, and `PocketSynthesizer` fails LOUD on the Pocket option (ADR-0017: never a silent Edge mix) |
| `FFMPEG_HOME` | ffmpeg install root (`$FFMPEG_HOME/bin/ffmpeg`) | `desktopbridge/FfmpegLocator` | No | Falls back to bundled binary then PATH |
| `ESPEAK_NG_BIN` | Explicit espeak-ng binary override (P4 phonemizer + local render engine) | `desktopbridge/EspeakLocator.espeakPath` | No | Falls back to bundled binary → PATH → `~/.authr/espeak-ng`; the IPA pass then finds no phonemizer (candidates drop out) and `EspeakSynthesizer` reports `espeak-ng not found` |
| `EPUBCHECK_JAR` | Explicit path to epubcheck.jar (P5 external EPUB 3 gate; lib/ siblings must sit beside it — official dist layout). Distribution decision (ADR-0013, 2026-11-11; bundle shipped 2026-11-12 per ADR-0014): locator-first — the APP IMAGE now bundles `lib/app/epubcheck/` (5.2.1 dist), so writers need NO env vars and NO install; env vars remain for gate/CI machines, `~/.authr/epubcheck` remains the manual-install path; no runtime auto-download (a gate that needs the network to pass is not a local gate) | `desktopbridge/EpubCheckLocator.epubCheckJar` | No | Falls back to `EPUBCHECK_HOME` → `<app dir>/epubcheck/` → `~/.authr/epubcheck`; with no tool the retail package still builds but the epubcheck verdict is UNVERIFIED and the ship report is NOT clean (disclosed) |
| `EPUBCHECK_HOME` | epubcheck dist directory (contains `epubcheck.jar` + `lib/`) | `desktopbridge/EpubCheckLocator.locate` | No | See `EPUBCHECK_JAR` fallback chain |
| `DISPLAY` (Linux) | Compose UI windowing | skiko/awt | Yes for UI runs | `HeadlessException` at startup (headless smoke uses Xvfb) |
| `LD_LIBRARY_PATH` | Native libs for software rendering in sandboxes | smoke harness only | No | GL fallback noise; skiko software rendering still works |
| `AUTHR_BASE`, `AUTHR_JAVA`, `AUTHR_EXTRAS` | Direct-route script overrides for CI/other machines (D-07): repo root, JDK 21 `java` binary, toolchain jar dir — sandbox defaults unchanged | every `scripts/*_direct_test.sh`, `scripts/ci_direct_suite.sh`, `scripts/p5_fetch_toolchain.sh` | No (CI sets them) | Scripts use the hardcoded sandbox defaults |

### `.env` file loading (FACT: `com/example/BuildConfig.loadDotEnv`)

Searched in order: `<working dir>/.env` → `~/.authr/.env` → `~/.env`. Simple `KEY=VALUE` lines. Values here are fallbacks behind the secure prefs.

## Voice packs (Pocket TTS, OQ-11 — ADR-0017 distribution decision)

Narrator voice packs install per-user into `<dataRoot>/files/audio/voice_packs/<name>.safetensors` (FACT: `PocketTtsManager.voicePacksDir`; the app creates the dir on first use). No runtime auto-download — a render that needs the network is not a deterministic local render. The desktop-side contract is frozen as code (`PocketTtsBridge`: the verified `pocket-tts` CLI invocation + `<name>.safetensors` resolution); the pack format beyond the file-name contract is Kyutai's external contract. Without a pack the Audiobook workbench's Pocket engine shows its reason and espeak (ESPEAK_LOCAL) renders instead.

## Worker registry (D-01, ADR-0015)

Worker factories register ONCE from `main()` via `WorkerRegistry.register(app, database, repository)` BEFORE the UI exists — no env vars, no user action. The public `WorkManager.registerWorker`/`registeredWorkerClasses` API is the desktop replacement for the Android Koin `worker { … }` module.

## API keys in prefs (secure layer)

Stored via `EncryptedSharedPreferences` file `secure_studio_settings` (AES256_GCM/SIV master key); **on keystore failure the fallback is IN-MEMORY ONLY — nothing is ever written to disk unencrypted** (v3.0 D-03, ADR-0016; the old plain `secure_studio_settings_fallback` file is a legacy name no desktop code writes anymore). The degraded state is disclosed: `LlmOrchestrator.secureStorageDegraded` + a red banner in the Settings key section — keys must be re-entered each session until the keystore works. Both construction sites route through the single `LlmOrchestrator.openSecurePrefs(context)`.

Parity (OQ-5, verified 2026-11-12 against the original Android app in `source/`): same pref files, same key names, same crypto schemes — FACT. One deliberate divergence: the Android source still carries the plain-file fallback; the desktop is stricter.

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
| `studio_settings` | `provider`, `tts_engine`, `humanize_audio`, `auto_optimize`, `app_theme`, `has_seen_onboarding`, `cleanup_enabled`, `cleanup_frequency`, `max_cache_size_mb`, `max_projects_count`, `queue_rpm`, `queue_concurrency`, `delete_unused_cache`, `delete_temp_cache`, `delete_failed_artifacts`, `delete_expired_api` | Settings/Studio app prefs (FACT: code-derived inventory, OQ-6, 2026-11-12 — `SettingsViewModel`/`StudioViewModel` string literals; the `custom_presets`/`pronunciation_rules`/`vocal_traits` literals are JSON payload field names inside single prefs values, not prefs keys) |
| `studio_settings` | `author_name`, `author_pen_name`, `author_bio`, `author_website`, `author_social`, `author_publisher` | author profile card |

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
