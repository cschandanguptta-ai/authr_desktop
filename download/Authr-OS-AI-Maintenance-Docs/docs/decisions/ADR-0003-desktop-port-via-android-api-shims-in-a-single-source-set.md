# ADR-0003 — Desktop port via Android API shims in a single source set

**Status:** Accepted (P0 port; consequences noted P1–P3)
**Context:** The product is a port of the Android app; keeping the ported code 1:1 makes parity auditable but requires desktop implementations of Android APIs (WorkManager, DataStore, EncryptedSharedPreferences, Compose runtime glue, MediaStore, etc.).
**Decision:** One JVM source set holds `com.example.*` (ported app code) and `android.*`/`androidx.*` shim packages implementing exactly the surface the app uses (see `desktop/src/main/kotlin/android/…`, `androidx/work/WorkManager.kt`). Room runs on the KMP `BundledSQLiteDriver`; WorkManager runs on a desktop shim with chain/backoff/periodic semantics.
**Alternatives considered:** rewriting the UI for desktop (rejected — parity cost); running an emulator (rejected — distribution).
**Consequences:** Known gaps: the shim's `runSingle` requires workers to be registered via `DesktopWorkManager.registerWorker`, and no call site registers any — the autonomous chain therefore does not execute on desktop (engine-level enforcement covers the gates; see tech-debt D-01). EncryptedSharedPreferences falls back to plain prefs on keystore failure (tech-debt D-03).
**Do not change unless:** you are completing the shim (registering worker factories) or the plan says the Android parity contract changed.
**Evidence:** `androidx/work/WorkManager.kt` (`runSingle`, `DefaultWorkerFactory`), P1/P2/P3 report build-repair notes.
