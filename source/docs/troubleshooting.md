# Troubleshooting & Incident Guide

## Symptom Resolution Guide

| Symptom | Likely Cause | Where to Look | Fix | Status |
|---|---|---|---|---|
| **Autonomous Drafting gets stuck on "Outlining" or "Drafting"** | WorkManager constraints not satisfied (device on battery saver or disconnected from WiFi). | Logcat tag: `WM-WorkerWrapper`<br>`app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt:5432-5435` | Plug in device to charge, disable Battery Saver, or connect to active WiFi network. [FACT] |
| **All user novels / chapters disappear after app update** | Room DB schema changed without migration; triggered `fallbackToDestructiveMigration(true)`. | Logcat tag: `Room`<br>`app/src/main/java/com/example/data/local/AppDatabase.kt:48-52` | Write explicit `Migration(N-1, N)` in `AppDatabase.kt` and register in `addMigrations()`. [FACT] |
| **Worker crashes with `IllegalStateException: Could not instantiate Worker`** | Worker missing Koin DI registration in `AppModule.kt`. | `app/src/main/java/com/example/di/AppModule.kt:44-49`<br>`StudioApplication.kt:12-18` | Add `worker { MyWorker(get(), ...) }` to `appModule` in `AppModule.kt`. [FACT] |
| **LLM generation fails immediately with "All LLM providers failed"** | Missing or invalid API keys in Settings; fallback sequence exhausted. | Logcat tag: `LlmOrchestrator`<br>`app/src/main/java/com/example/data/api/LlmOrchestrator.kt:107-142` | Open Settings tab (Tab 3) in app, enter valid Gemini, Groq, or NVIDIA API key, and tap Save Keys. [FACT] |
| **429 Too Many Requests / Rate Limit Exceeded** | Provider quota exhausted or RPM threshold breached. | Logcat tag: `RequestQueueManager`<br>`app/src/main/java/com/example/data/api/RequestQueueManager.kt:68-79` | `RequestQueueManager` automatically applies exponential backoff up to 3 retries. In Settings, switch preferred agent provider or configure backup keys separated by commas. [FACT] |
| **TTS audio synthesis produces 0-byte file or crashes** | Selected TTS engine unavailable or network timed out (Edge TTS WebSocket dropped). | Logcat tag: `StudioViewModel`, `EdgeTtsManager`<br>`app/src/main/java/com/example/tts/` | Switch TTS Engine in Settings (Tab 3) to "Android" native engine. [FACT] |
| **EPUB export fails or generated file corrupted** | Invalid cover image format, unescaped XML characters in chapter titles, or missing metadata. | Logcat tag: `EpubManager`<br>`app/src/main/java/com/example/managers/EpubManager.kt:181-302` | Validate image with `EpubManager.validateCoverImage()` and ensure title text does not contain raw `<` or `&` characters. [FACT] |
| **Worker crash on Android 14+: `foregroundServiceType 0x40000000 is not a subset`** | Android 14+ strict FGS enforcement; `androidx.work.impl.foreground.SystemForegroundService` missing `android:foregroundServiceType="specialUse"` attribute in manifest. | Logcat: `AndroidRuntime`<br>`app/src/main/AndroidManifest.xml:42-50` | Declare `SystemForegroundService` with `android:foregroundServiceType="specialUse"`, `tools:node="merge"`, and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property in `AndroidManifest.xml`. [FACT] |
| **Crash on startup: `KoinAppAlreadyStartedException` in tests** | `startKoin` called multiple times across test cases. | `app/src/test/java/com/example/SettingsScreenScreenshotTest.kt:44, 78` | Call `stopKoin()` in test `@Before` and `@After` methods. [FACT] |
| **RxFFmpeg video generation fails with exit code != 0** | Invalid image resolution aspect ratio, missing audio track, or codec incompatibility. | Logcat tag: `StudioViewModel`<br>`StudioViewModel.kt:4350-4450` | Ensure source image exists on disk and audio track file exists with non-zero size. [FACT] |

---

## Log Reading & Diagnostics

### Key Logcat Tags

| Log Tag | Subsystem | Typical Log Messages |
|---|---|---|
| `StudioViewModel` | ViewModel / UI / Audio pipeline | `Autonomous Draft Started for Book ID: X`<br>`Starting TTS conversion for: Title`<br>`Failed to generate wizard outline` |
| `LlmOrchestrator` | Multi-agent LLM Router | `Skipping Provider: no API keys configured`<br>`All LLM providers failed for agent X` |
| `RequestQueueManager` | Queue & Rate Limiter | `Request enqueued: id=...`<br>`429 Rate limit hit, backing off...` |
| `WorldBuildingWorker` | Autonomous Stage 1 | `Starting WorldBuildingWorker for Book ID: X`<br>`World building completed successfully` |
| `DraftingWorker` | Autonomous Stage 3 | `Drafting chapter N of M...`<br>`Chapter N saved successfully` |
| `EpubManager` | Export Packager | `Validating cover image...`<br>`EPUB generation complete: path` |
| `StorageCleanupWorker` | Background Storage | `Deleted N oldest books to respect max projects limit`<br>`SQLite VACUUM executed successfully` |

### In-App Diagnostics
- The application provides a built-in **LogBook Dialog**:
  - Accessible via the **BugReport** icon in the top app bar (`MainActivity.kt:199-208`).
  - Implemented in `app/src/main/java/com/example/ui/components/LogBookDialog.kt`.
  - Captures real-time system logs, worker events, and LLM retry logs with a one-tap "Copy Logs" export. [FACT]

---

## How to Reproduce Locally & Roll Back

### Local Testing
```bash
# 1. Run full unit and Robolectric test suite
gradle :app:testDebugUnitTest

# 2. Compile debug APK
compile_applet # or gradle assembleDebug
```

### Roll Back Safe Procedures
- If a database migration causes runtime failures:
  1. Do NOT delete migration code from `AppDatabase.kt`.
  2. Increment database version by +1 (`version = 15`).
  3. Write a compensating migration script `MIGRATION_14_15` repairing broken table columns or indexes.
  4. Register `MIGRATION_14_15` in `addMigrations()`. [FACT]
