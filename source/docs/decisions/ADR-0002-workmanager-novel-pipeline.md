# ADR-0002: Chained AndroidX WorkManager for Novel Drafting Pipeline

## Status
Accepted

## Context
Generating a 50,000-word novel takes 15 to 45 minutes of continuous processing. Coroutine scopes bound to UI ViewModels terminate when the Android OS reclaims memory or the user navigates away or closes the app.

## Decision
Orchestrate autonomous novel production via AndroidX `WorkManager` using a strictly chained sequence:
`WorldBuildingWorker` -> `OutlineWorker` -> `DraftingWorker` -> `PolishingWorker` -> `CompilationWorker`
1. Input data is passed via `workDataOf("BOOK_ID" to bookId, "AUTO_EXPORT" to autoExport)`.
2. All jobs are tagged with `drafting_work_$bookId`.
3. Work requests require network connection (`NetworkType.CONNECTED`) and battery constraint (`setRequiresBatteryNotLow(true)`).
4. Custom worker instantiation is handled via Koin `KoinWorkerFactory` with default AndroidX startup provider removed.

## Alternatives (Evidenced in Code)
- *Foreground Service Only*: Code includes `StudioForegroundService` for audio/video synthesis (`MainActivity.kt:35-40`). Foreground services can still be killed on aggressive OEM power management if battery drops.

## Consequences
- **Positive**: Guaranteed execution across app reboots; state persistence in Room database between stages; exponential backoff on transient network drops.
- **Negative**: Workers pause silently when battery saver is activated or battery falls below threshold.

## Do Not Change Unless
- Novel generation is offloaded entirely to a remote backend server with websocket progress streaming to the client.

## Evidence
- `app/src/main/java/com/example/ui/viewmodel/StudioViewModel.kt:5363-5369` [FACT]
- `app/src/main/AndroidManifest.xml:42-51` [FACT]
- `app/src/main/java/com/example/StudioApplication.kt:12-18` [FACT]
