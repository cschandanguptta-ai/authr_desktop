# Invariants & Gotchas

## Load-Bearing System Invariants

### 1. Zero-Loss Database Upgrades
- **Invariant**: `fallbackToDestructiveMigration(true)` is configured in `AppDatabase.kt:51`. Every single schema change to Room entities (`Book`, `Chapter`, `Story`, `AudioTrack`, `Lorebook`) MUST include an explicit `Migration(OldVersion, NewVersion)` and be registered in `.addMigrations(...)`.
- **Consequence of Violation**: When the app launches on an existing user device with an unhandled version increment, Room deletes `audio_novel_studio_db` and recreates all tables, erasing all manuscripts, chapters, and lore. [FACT]

### 2. WorkManager Dependency Injection via Koin
- **Invariant**: Android default `WorkManagerInitializer` is stripped in `AndroidManifest.xml:47-51`, and `StudioApplication.kt:12-18` provides `KoinWorkerFactory()`.
- **Consequence of Violation**: Any worker without a registered binding `worker { MyWorker(get(), ...) }` in `AppModule.kt` throws `IllegalStateException: Could not instantiate com.example.worker.MyWorker` when scheduled by the system. [FACT]

### 3. Sequential Work Chain Execution Order
- **Invariant**: Autonomous book compilation requires strict sequential dependency:
  `WorldBuildingWorker` -> `OutlineWorker` -> `DraftingWorker` -> `PolishingWorker` -> `CompilationWorker` (`StudioViewModel.kt:5364-5369`).
- **Consequence of Violation**: `DraftingWorker` requires chapter outline rows created by `OutlineWorker`; `CompilationWorker` requires chapters drafted by `DraftingWorker`. Running out of order produces empty manuscripts or zero chapters. [FACT]

### 4. Background Execution Constraints
- **Invariant**: Background drafting workers require `NetworkType.CONNECTED` and `requiresBatteryNotLow(true)` (`StudioViewModel.kt:5350-5360, 5432-5435, 5522-5525`).
- **Consequence of Violation**: If the device enters battery saver or battery drops below 15-20%, the entire pipeline pauses indefinitely until plugged in or recharged. [FACT]

### 5. Foreign Key Cascading on Deletion
- **Invariant**: `Chapter` table declares foreign key constraint `onDelete = ForeignKey.CASCADE` against `Book(id)` with index on `bookId` (`Chapter.kt:10-18`).
- **Consequence of Violation**: Deleting a book in `BookDao` automatically cascades and deletes all associated chapters at SQLite level. Do not manually execute raw `DELETE FROM chapters` before deleting the book, as foreign key triggers manage this atomically. [FACT]

---

## Non-Obvious Behavior & Fragile Areas

### 1. Dual Story Models (`Story` vs `Book`)
- **Gotcha**: The codebase maintains two distinct entity systems:
  - `Story`: Legacy 5-part structure (`part1`, `part2`, `part3`, `part4`, `part5`) used exclusively by the **Interactive Wizard** (`Story.kt:16-20`).
  - `Book` + `Chapter`: Relational, arbitrary chapter count structure used by the **Autonomous Studio** (`Book.kt`, `Chapter.kt`).
- **File Reference**: `app/src/main/java/com/example/ui/screens/StoryDashboardScreen.kt:48`.
- **Rule**: Never attempt to query `StoryDao` when looking for autonomous chapters, and never look for `Book` entries in Interactive Wizard drafts. [FACT]

### 2. Monolithic ViewModel Concurrency & Coroutine Scopes
- **Gotcha**: `StudioViewModel.kt` (6,661 lines) contains hundreds of mutable `StateFlow` and `viewModelScope.launch` calls.
- **Fragility**: Long-running audio playback loops (`startPlaybackLoop()`, line 3090) and foreground services run within ViewModel or Service scopes. Modifying playback index state while TTS is actively streaming causes `IndexOutOfBoundsException` in paragraph traversal. [FACT]

### 3. Secret Management Fallback Behavior
- **Gotcha**: `LlmOrchestrator.getSecurePrefs(context)` attempts to create an `EncryptedSharedPreferences` backed by Android Keystore (`LlmOrchestrator.kt:30-46`). If Keystore initialization fails (common on custom ROMs or modified Android environments), it silently falls back to unencrypted `secure_studio_settings_fallback`.
- **Rule**: Never assume keys are encrypted at rest on all devices. [FACT]

### 4. LLM Response Parsing Resilience
- **Gotcha**: LLM responses often contain markdown code blocks (````json ... ````).
- **Rule**: Workers use `extractJsonArray()` and `extractJsonObject()` helper methods (`StudioViewModel.kt:5863-5885`) to sanitize markdown ticks before passing text to `org.json.JSONObject` or `JSONArray`. Omitting this sanitizer causes `JSONException: Value of type java.lang.String cannot be converted to JSONObject`. [FACT]

### 5. Rate Limiter Memory Reset
- **Gotcha**: `RequestQueueManager` stores request timestamps in a local in-memory list (`RequestQueueManager.kt:91`).
- **Rule**: If the app process dies while workers are queued in WorkManager, the in-memory rate limiter resets, allowing immediate burst calls upon process recreation. [FACT]

---

## Past Bug Patterns & Fix Evidence

1. **WorkManager Battery Constraint Missing**:
   - *Issue*: Background workers ran aggressively on low battery, leading to process kills midway through drafting.
   - *Fix*: Added `.setRequiresBatteryNotLow(true)` across all WorkRequest builders (`StudioViewModel.kt:5352, 5434, 5524, 5558`). [FACT]
2. **Koin App Already Started in Screenshot / Unit Tests**:
   - *Issue*: Tests failed with `KoinAppAlreadyStartedException`.
   - *Fix*: Explicit `stopKoin()` in `@Before` and `@After` in test fixtures (`SettingsScreenScreenshotTest.kt:44, 78`). [FACT]
3. **Android Q Storage Deprecation**:
   - *Issue*: `WRITE_EXTERNAL_STORAGE` permission denied on modern Android API levels.
   - *Fix*: Scoped permissions in `AndroidManifest.xml:7` with `android:maxSdkVersion="29"`. [FACT]
4. **Android 14 WorkManager SystemForegroundService Crash**:
   - *Issue*: `IllegalArgumentException: foregroundServiceType 0x40000000 is not a subset of foregroundServiceType attribute 0x00000000 in service element of manifest file` when autonomous drafting workers called `setForeground(createForegroundInfo(...))` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
   - *Fix*: Declared `androidx.work.impl.foreground.SystemForegroundService` with `android:foregroundServiceType="specialUse"`, `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property, and `tools:node="merge"` in `AndroidManifest.xml:42-50`. [FACT]
