# AGENTS.md

## App Summary
**Authr: The Autonomous Author Operating System v2.3** (`com.aistudio.audionovelstudio.nxkpt`) is an Android creative writing and audiobook generation workstation built with Kotlin, Jetpack Compose (M3), Koin DI, Room DB (SQLite schema v14 with FTS4), and AndroidX WorkManager. It orchestrates a 5-stage background multi-agent novel writing pipeline (WorldBuilding -> Outline -> Drafting -> Polishing -> Compilation) across multiple LLM providers (Gemini, Groq, NVIDIA, Mistral, OpenRouter, Ollama, Custom OpenAI) and synthesizes audiobooks via Android TTS, Edge TTS, and KittenTTS.

---

## Documentation Index
- `docs/system-map.md`: Read when understanding architecture, data flow, component dependencies, and entry points.
- `docs/change-impact.md`: Read BEFORE modifying any file to determine blast radius, coupled schemas, and verification steps.
- `docs/invariants-and-gotchas.md`: Read before touching state, Room DB, WorkManager chains, or LLM queues to avoid regressions.
- `docs/troubleshooting.md`: Read when builds fail, workers halt, TTS fails, DB errors occur, or API limits trigger.
- `docs/data-model.md`: Read when modifying Room entities (`Book`, `Chapter`, `Story`, `AudioTrack`, `Lorebook`) or creating migrations.
- `docs/api-contracts.md`: Read when integrating or modifying external LLM or TTS network APIs.
- `docs/config-and-env.md`: Read when managing environment variables, `BuildConfig` keys, or SharedPreferences.
- `docs/decisions/`: Read before refactoring major architectural decisions to see load-bearing constraints (`Do not change unless`).
- `docs/testing.md`: Read when running or authoring tests (Robolectric, Compose tests, Roborazzi screenshot verification).
- `docs/tech-debt.md`: Read when refactoring code smells, oversized ViewModels, or orphaned screens.
- `docs/open-questions.md`: Read to review unresolved system ambiguities needing human confirmation.

---

## Hard Rules
1. **NEVER bump Room DB version in `AppDatabase.kt` without writing an explicit migration (`Migration(N, N+1)`)**: `fallbackToDestructiveMigration(true)` is enabled (`AppDatabase.kt:51`); missing migrations wipe user databases. [FACT]
2. **NEVER remove `requiresBatteryNotLow(true)` or `CONNECTED` constraints on background Workers without reviewing battery and data policies**: Workers run long multi-stage LLM chains (`StudioViewModel.kt:5351-5361`). [FACT]
3. **NEVER modify `debug.keystore`, `debug.keystore.base64`, or Gradle `signingConfigs`**: Modifying these breaks signing compatibility (`app/build.gradle.kts:27-53`). [FACT]
4. **ALWAYS use Koin worker injection**: Register all new workers in `AppModule.kt` via `worker { ... }` and maintain `StudioApplication.kt` `KoinWorkerFactory` provider (`StudioApplication.kt:12-18`). [FACT]
5. **ALWAYS route LLM calls through `LlmOrchestrator.callAgent()`**: Do not call `ApiService` directly from UI or workers; bypasses rate limiting, priority queues, and fallback sequence (`LlmOrchestrator.kt:86-120`). [FACT]
6. **NEVER hardcode API keys in code**: Inject via Secrets Gradle plugin from `.env` / BuildConfig or retrieve from `EncryptedSharedPreferences` (`LlmOrchestrator.kt:48-79`). [FACT]

---

## Commands
- **Build Applet**: AI Studio tool `compile_applet` (or `gradle assembleDebug`)
- **Run Unit / Robolectric Tests**: `gradle :app:testDebugUnitTest`
- **Verify Lint**: `lint_applet` (or `gradle lintDebug`)
- **Roborazzi Screenshot Tests**: `gradle :app:recordRoborazziDebug` / `verifyRoborazziDebug`

---

## Workflow
1. **Before modifying code**: Read `docs/change-impact.md` and `docs/invariants-and-gotchas.md` for target files.
2. **Implement changes**: Follow architecture patterns and preserve thread safety (`Dispatchers.IO` for file/DB/network).
3. **Verify**: Run `compile_applet` and `gradle :app:testDebugUnitTest`.
4. **After modifying code**: Update affected tables in `docs/change-impact.md`, `docs/data-model.md`, or `docs/api-contracts.md`.
