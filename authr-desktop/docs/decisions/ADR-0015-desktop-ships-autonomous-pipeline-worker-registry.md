# ADR-0015: D-01 — the desktop edition ships the autonomous pipeline; a Koin-free worker registry wires it

Date: 2026-11-12 · Status: accepted · Deciders: program agent (delegated decision — "USE YOUR INTELLIGENCE AND MAKE DECISIONS"), no human in the loop — this ADR records the decision for audit

## Context

OQ-1 asked whether the Autonomous Studio pipeline (WorldBuilding→Outline→Drafting→Polishing→Compilation) is expected to RUN on the desktop edition for v3.0, or whether engine-level enforcement + interactive flows are the shipped scope. The question decided D-01's identity: bug-to-fix-now vs accepted port limitation. The shim was complete — `DesktopWorkManager.runSingle` executes chains in order with retry/backoff, `enqueueChain`/`enqueuePeriodic` work, unique names and tags work — but ZERO call sites registered a factory, so every enqueue from `StudioViewModel` (Autonomous drafting ×3, storage cleanup ×2) and `StoryEditorViewModel` logged "No factory for …" and stopped. The workers themselves and their DAO-backed constructors were complete and gate-proven at engine level; only the DI registration (Koin on Android) had no desktop replacement.

## Decision

**The desktop edition SHIPS the pipeline as a running feature (OQ-1 → RESOLVED).** Rationale: the v3.0 program's value proposition is the autonomous chain; every constituent part was already built, gate-proven, and merely un-wired; the missing piece is a registration table, not new engine work. An accepted-limitation decision would have shipped dead code behind live UI buttons.

1. **A Koin-free registry** (`worker/WorkerRegistry.kt`) registers a factory for every worker class the app enqueues (7 classes, grep-verified call sites: the five pipeline workers + `EpubExportWorker` + `StorageCleanupWorker`). Called once from `main()` — before the UI exists — from the app's own database + repository graph; idempotent (re-registration overwrites with equivalent factories).
2. **The shim gained a public registration + probe API**: `WorkManager.Companion.registerWorker(...)` (the desktop replacement for the Android Koin `worker { … }` module) and `WorkManager.registeredWorkerClasses()` (diagnostic). Production registers through `WorkerRegistry`; tests register their own workers through the same public API.
3. **Testability shape**: the registry takes DAOs directly (not the `AppDatabase` singleton), so `WorkerWiringTest` proves coverage with JDK-proxy DAOs — no Room, no Context shim filesystem — plus a chain-execution integration test (in order, failure halts the chain) through the same `beginUniqueWork(...).then(...).enqueue()` path `StudioViewModel` drives.
4. **Consequence — D-05 is now live**: wiring the chain activates `CompilationWorker`'s hardcoded-ISBN compile path on desktop. That placeholder's retail-side authority is already resolved (OQ-9: the workbench, not the placeholder, feeds retail exports); the legacy compiled-manuscript artifact keeps the placeholder by documented backward compatibility. No code change needed — the interaction is recorded in the D-05 row.

## Evidence

- `WorkerWiringTest`: 2/2 green — the registry covers all 7 enqueued classes; a registered chain runs in order and a failed step halts it (log: "Chain wiring-test-fail halted after StepWorker failure").
- Full suite: d13 route 68/68, p5 route 59/59, p6 route 47/47 (golden corpus precision 1.0000 unchanged) — 238 tests total (226 + 12 new across this slice).
- App smoke under Xvfb after the rebuild: alive-until-kill, zero exceptions, no "No factory" lines.

## Consequences

- The autonomous drafting buttons, storage cleanup scheduling, and EPUB export now actually execute on desktop; failures are the workers' own (e.g. missing API keys → loud retry/backoff logs), not a silent registry miss.
- The engine-level gate tests remain the compile-path proof; `WorkerWiringTest` is the integration seam guard (registry coverage + chain semantics), not a re-run of the gates.
- OQ-1 resolves to FACT-with-decision; the row in `docs/open-questions.md` carries it.
