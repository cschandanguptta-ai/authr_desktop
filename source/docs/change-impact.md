# Change Impact & Blast Radius Guide

## Change Impact Matrix

| Change Area | Blast Radius | Files to Re-verify | Tests to Run | Status |
|---|---|---|---|---|
| **Room Database Schema** (`AppDatabase`, `Book`, `Chapter`, `Story`, `AudioTrack`, `Lorebook`) | **CRITICAL**: Any schema modification without an explicit `Migration(N, N+1)` triggers destructive migration wiping all local user books and chapters. | `app/src/main/java/com/example/data/local/AppDatabase.kt`<br>`app/src/main/java/com/example/data/local/*Dao.kt`<br>`app/src/main/java/com/example/data/repository/AppRepository.kt`<br>`app/src/main/java/com/example/worker/*Worker.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **WorkManager Pipeline & Constraints** | **HIGH**: Changing Worker input/output data keys (`BOOK_ID`, `AUTO_EXPORT`) or constraint parameters breaks the 5-stage chain execution. | `app/src/main/java/com/example/worker/WorldBuildingWorker.kt`<br>`app/src/main/java/com/example/worker/OutlineWorker.kt`<br>`app/src/main/java/com/example/worker/DraftingWorker.kt`<br>`app/src/main/java/com/example/worker/PolishingWorker.kt`<br>`app/src/main/java/com/example/worker/CompilationWorker.kt`<br>`app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **`LlmOrchestrator` & `RequestQueueManager`** | **CRITICAL**: Affects every AI generation call in the entire application. Changes to fallback order, queue priority, or error handling can cause infinite retries or premature worker failure. | `app/src/main/java/com/example/data/api/LlmOrchestrator.kt`<br>`app/src/main/java/com/example/data/api/RequestQueueManager.kt`<br>`app/src/main/java/com/example/data/api/ApiService.kt`<br>`app/src/main/java/com/example/worker/*Worker.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **`StudioViewModel` (God Class)** | **CRITICAL**: 6,661 lines. Merging or touching state flows impacts both UI tabs (Wizard, Autonomous), TTS playback loops, video generation, and dialog controllers. | `app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt`<br>`app/src/main/java/com/example/ui/screens/StoryScreen.kt`<br>`app/src/main/java/com/example/ui/screens/StoryDashboardScreen.kt`<br>`app/src/main/java/com/example/MainActivity.kt` | `compile_applet`<br>`gradle :app:testDebugUnitTest` | FACT |
| **Koin Dependency Injection (`AppModule.kt`)** | **HIGH**: Modifying constructor parameters for ViewModels, DAOs, or Workers without updating `AppModule.kt` causes runtime crash on startup or background worker launch. | `app/src/main/java/com/example/di/AppModule.kt`<br>`app/src/main/java/com/example/StudioApplication.kt` | `compile_applet`<br>`gradle :app:testDebugUnitTest` | FACT |
| **TTS Engines & Audio Pipelines** | **MEDIUM**: Changes to WAV header parsing or audio mixing affect duration calculations, SRT generation, and playback synchronization. | `app/src/main/java/com/example/utils/WavUtils.kt`<br>`app/src/main/java/com/example/tts/AndroidTtsManager.kt`<br>`app/src/main/java/com/example/tts/EdgeTtsManager.kt`<br>`app/src/main/java/com/example/tts/KittenTtsManager.kt`<br>`app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **EPUB Generator & Worker (`EpubManager`, `EpubExportWorker`)** | **MEDIUM**: Affects export validity and background queueing. Worker expects either `BOOK_ID` or `STORY_ID` input data. Must remain registered in `AppModule.kt`. | `app/src/main/java/com/example/managers/EpubManager.kt`<br>`app/src/main/java/com/example/worker/EpubExportWorker.kt`<br>`app/src/main/java/com/example/worker/CompilationWorker.kt`<br>`app/src/main/java/com/example/ui/screens/StoryDashboardScreen.kt`<br>`app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **Model Discovery (`ModelDiscoveryService`)** | **LOW-MEDIUM**: Affects dynamic model lists in Settings. Must handle provider JSON format discrepancies gracefully without blocking UI or falling back prematurely. | `app/src/main/java/com/example/data/api/ModelDiscoveryService.kt`<br>`app/src/main/java/com/example/data/api/ApiService.kt`<br>`app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt`<br>`app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | `gradle :app:testDebugUnitTest` | FACT |
| **Settings & Security Storage** | **MEDIUM**: Changing preference keys (`gemini_key`, `mistral_key`, `agent_provider_*`) desynchronizes user credentials between UI, ViewModel, and LLM Orchestrator. | `app/src/main/java/com/example/data/api/LlmOrchestrator.kt`<br>`app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt`<br>`app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | `gradle :app:testDebugUnitTest` | FACT |

---

## Detailed Blast Radius by Subsystem

### 1. Modifying Data Models (`com.example.data.model.*`)

#### If you change `Book.kt`:
- **Check/Update**:
  - `AppDatabase.kt:17, 29-41`: Add a new `Migration(M, N)` and register in `addMigrations()`.
  - `BookDao.kt`: Check queries (`getAllBooks`, `getBookById`, `updateBook`).
  - `CompilationWorker.kt:35-120`: Check metadata extraction (`title`, `genre`, `synopsis`, `storyBible`, `currentPhase`).
  - `WorldBuildingWorker.kt`, `OutlineWorker.kt`, `DraftingWorker.kt`, `PolishingWorker.kt`: All deserialize and update `Book`.
  - `StoryDashboardScreen.kt:35-700`: Displays `Book` cards, status badges, and chapter details.
- **Breaking Condition**: Renaming columns without migration throws `SQLiteException: no such column` on upgrade. [FACT]

#### If you change `Chapter.kt`:
- **Check/Update**:
  - `AppDatabase.kt`: Update schema version and migration. Foreign key `bookId` cascades on delete (`Chapter.kt:15`). [FACT]
  - `ChapterDao.kt`: Check `getChaptersForBookFlow(bookId)` and `getChapterByNumber(bookId, chapterNumber)`.
  - `DraftingWorker.kt:110-160`: Inserts or updates chapters sequentially.
  - `EpubManager.kt`: Builds EPUB table of contents and XHTML content from chapters.

---

### 2. Modifying WorkManager Workers (`com.example.worker.*`)

#### If you change worker parameters or constructors:
- **Check/Update**:
  - `AppModule.kt:44-49`: Every worker must be bound using `worker { ... }` with matching injected dependencies.
  - `StudioViewModel.kt:5350-5570`: Verify `workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to autoExport)` matches what `Worker.doWork()` reads via `inputData.getInt("BOOK_ID", -1)`.
- **Breaking Condition**: Adding a non-injected parameter to worker constructor causes `KoinWorkerFactory` to fail with `NoBeanDefFoundException` at background runtime. [FACT]

---

### 3. Modifying LLM API Layer (`com.example.data.api.*`)

#### If you change `LlmOrchestrator.kt`:
- **Check/Update**:
  - `Agent` enum keys (`executive`, `plot`, `character`, `world`, `scene`, `continuity`): Used as SharedPreferences keys (`agent_provider_${agent.key}`). [FACT]
  - Fallback sequence (`LlmOrchestrator.kt:107`): Changing order changes token costs and failure tolerance.
  - `RequestQueueManager.kt`: Enqueues and gates requests. Verify timeout handling and retry policies.
- **Breaking Condition**: Throwing uncaught exceptions from `callAgent` will terminate background workers and mark drafting state as `Failed`. [FACT]

---

### 4. Modifying Screen Navigation & Tabs (`MainActivity.kt`)

#### If you change tabs or routes:
- **Check/Update**:
  - `MainActivity.kt:174-180, 295-325`: Matches `currentTab` indices `(0: Library, 1: Workspace, 2: Guide, 3: Settings)`.
  - `StoryDashboardScreen.kt:298-323`: Switches `viewModel.selectTab(1)` and `viewModel.activeWorkspaceTab.value = 1` when opening a book.
  - Wide-screen `NavigationRail` (`MainActivity.kt:100-153`) must mirror bottom `NavigationBar` indices.
- **Breaking Condition**: Off-by-one in tab index renders blank container or displays unintended screen. [FACT]
