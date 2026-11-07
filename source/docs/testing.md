# Testing Guide & Verification Suite

## Test Architecture & Organization

The project employs **Robolectric** for local JVM unit/integration testing alongside **Roborazzi** for Compose UI screenshot regression testing.

```
app/src/test/
├── java/com/example/
│   └── SettingsScreenScreenshotTest.kt  # Robolectric + Roborazzi UI tests
└── screenshots/
    ├── greeting.png
    ├── settings_compact_dark_obsidian_normal.png
    ├── settings_compact_dark_standard_huge.png
    ├── settings_compact_light_sepia_normal.png
    ├── settings_expanded_dark_charcoal_normal.png
    └── settings_medium_light_cream_large.png
```

---

## Test Execution Commands

```bash
# Run all JVM unit and Robolectric tests
gradle :app:testDebugUnitTest

# Record new Roborazzi baseline screenshots
gradle :app:recordRoborazziDebug

# Verify current UI matches baseline screenshots
gradle :app:verifyRoborazziDebug
```

---

## What Is and Isn't Covered

| Subsystem | Test Coverage | How Guarded | Status |
|---|---|---|---|
| **Theme & Settings UI** | **COVERED** | `SettingsScreenScreenshotTest.kt` verifies Compact, Medium, and Expanded window sizes with dark/light mode and custom font scales (1.0x, 1.5x, 2.0x). | FACT |
| **Room Database & DAOs** | **PARTIALLY COVERED** | In-memory Room database setup in `SettingsScreenScreenshotTest.setUp()`; lacks dedicated entity migration assertions (`MigrationTestHelper`). | FACT |
| **Workers & WorkManager Pipeline** | **NOT COVERED** | No automated tests for `WorldBuildingWorker`, `OutlineWorker`, `DraftingWorker`, `PolishingWorker`, `CompilationWorker`. | FACT |
| **LLM Orchestration & Fallback** | **NOT COVERED** | No mocked unit tests for `LlmOrchestrator` fallback sequence or `RequestQueueManager` rate limiter. | FACT |
| **EPUB Generation (`EpubManager`)**| **NOT COVERED** | No automated validation of generated zip containers or EPUB 3 metadata parsing. | FACT |
| **Audio Synthesis (`WavUtils`)** | **NOT COVERED** | No automated byte-level tests for PCM concatenation or WAV header validation. | FACT |

---

## Writing Tests Safely (Invariants)

1. **Always call `stopKoin()` in `@Before` and `@After`**:
   - `StudioApplication` and test runners share JVM context. Omitting `stopKoin()` causes `KoinAppAlreadyStartedException`. [FACT]
2. **Flag Mock Testing Mode in ViewModels**:
   - Set `StudioViewModel.skipInitForTesting = true` and `LocalKittenTtsManager.skipInitForTesting = true` before instantiating ViewModels to prevent network or hardware TTS initialization during tests (`SettingsScreenScreenshotTest.kt:45-46`). [FACT]
3. **Initialize WorkManager Test Executor**:
   - Test contexts must initialize WorkManager safely with a single-thread executor (`SettingsScreenScreenshotTest.kt:52-59`). [FACT]
