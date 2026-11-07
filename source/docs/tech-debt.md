# Technical Debt & Architecture Scaling Limits

## Known Technical Debt & Vulnerabilities

### 1. Monolithic God-Class ViewModel (`StudioViewModel.kt`)
- **Location**: `app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt` (6,661 lines). [FACT]
- **Issue**: Combines UI state management, background thread dispatching, WorkManager request construction, audio playback loops, file I/O operations, video rendering orchestration via ffmpeg, and JSON extraction.
- **Risk**: Extremely high blast radius. Unintended recompositions; difficult to write unit tests; high likelihood of race conditions.
- **Remediation Plan**: Split into domain-specific viewmodels:
  - `AutonomousPipelineViewModel`: WorkManager job dispatch, drafting progress.
  - `AudioPlayerViewModel`: TTS streaming, playback loops, audio tracking.
  - `VideoGenerationViewModel`: Scene automation and RxFFmpeg processing.
  - `PronunciationViewModel`: Custom phonetic dictionary. [INFERRED]

### 2. Orphaned / Dead Code Screens
- **Location**: `app/src/main/java/com/example/ui/screens/`
  - `AudioScreen.kt` (2,493 lines)
  - `TranslateScreen.kt` (683 lines)
  - `SceneAutomationScreen.kt` (555 lines)
  - `MetadataScreen.kt` (472 lines)
  - `StoryEditorScreen.kt` (412 lines) [FACT]
- **Issue**: These screens are compiled into the APK, contain extensive business logic, and bind to `StudioViewModel`, but are not accessible from any navigation tab in `MainActivity.kt:295-325`.
- **Risk**: Bloats APK size; confuses developers; accumulates stale code that does not undergo regression testing. [FACT]

### 3. Destructive Room Fallback Enabled
- **Location**: `app/src/main/java/com/example/data/local/AppDatabase.kt:51`. [FACT]
- **Issue**: `.fallbackToDestructiveMigration(true)` is left enabled in production database builder.
- **Risk**: Any forgotten migration step will drop the SQLite database and delete all user stories without warning.
- **Remediation**: Remove `fallbackToDestructiveMigration(true)` and establish automated migration tests using `androidx.room.testing.MigrationTestHelper`. [INFERRED]

### 4. WorkManager Battery Constraint Starvation
- **Location**: `app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt:5352, 5434, 5524, 5558`. [FACT]
- **Issue**: `.setRequiresBatteryNotLow(true)` prevents novel generation from starting if battery is below ~15-20%.
- **Risk**: Users on mobile devices without chargers experience silent job queuing with no UI feedback explaining why generation has paused.
- **Remediation**: Expose battery/constraint status explicitly on the dashboard card or provide user override. [INFERRED]

### 5. In-Memory Rate Limiting Vulnerability
- **Location**: `app/src/main/java/com/example/data/api/RequestQueueManager.kt:43, 90-100`. [FACT]
- **Issue**: Rate limiter timestamps and active queue sizes are held in memory (`queueScope`).
- **Risk**: Process death during background execution resets the rate limiter, allowing burst requests upon worker restart. [FACT]

### 6. Legacy Script Files in Repository Root (RESOLVED)
- **Status**: **RESOLVED** (Cleaned 120+ obsolete `.py`, `.sh`, and `.txt` scripts and duplicate backup files from repository root). [FACT]

### 7. Hardcoded Model Lists (RESOLVED)
- **Status**: **RESOLVED** (`ModelDiscoveryService` now queries live `/models` and `/api/tags` dynamically across Gemini, Groq, NVIDIA, Mistral, OpenRouter, Ollama, and Custom OpenAI, eliminating manual model maintenance). [FACT]

### 8. Heavy I/O Foreground Exports (RESOLVED)
- **Status**: **RESOLVED** (`EpubExportWorker` enqueues EPUB generation onto WorkManager in background, eliminating UI thread block and ANR risks on large manuscripts). [FACT]

### 9. RAG Vectorization Latency (RESOLVED)
- **Status**: **RESOLVED** (`LocalVectorRag.kt` precompiled regex patterns and added an in-memory TF vector cache, eliminating redundant tokenization overhead in multi-chapter drafting loops). [FACT]
