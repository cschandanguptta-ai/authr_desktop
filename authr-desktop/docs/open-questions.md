# Open Questions

Every UNKNOWN in one checklist for a human to answer. If you resolve one, move the fact into the relevant doc (and cite the source) or delete the row.

| # | Question | Why it matters | Where the uncertainty lives | Blocks |
| --- | --- | --- | --- | --- |
| 1 | Is the Autonomous Studio pipeline (WorldBuilding→Outline→Drafting→Polish→Compilation workers) expected to run on the desktop edition for v3.0, or is engine-level enforcement + interactive (Wizard/Writer Studio) flows the shipped scope? | Decides whether D-01 (no registered worker factories) is a bug to fix now or an accepted port limitation. | `androidx/work/WorkManager.kt runSingle`, absence of any `registerWorker` call site, P1–P3 report notes | P4 audiobook batch rendering design |
| 2 | The P2 report records a "reference laptop" for performance gates (TTS ≥ 2× real time). Which hardware is that, and is the 40–50 s smoke window (P2/P3 reports) the agreed proxy in CI-less environments? | Performance gates need a pinned baseline. | P2/P3 report build-evidence sections | P4 exit gate |
| 3 | Should `fallbackToDestructiveMigration(true)` be removed now (fail-fast) or kept until the Android edition ships? (D-02) | Data-loss policy. | `data/local/AppDatabase.kt` | next schema change |
| 4 | Is plain-prefs fallback for API keys acceptable on machines with broken keystores, or should keys stay memory-only there? (D-03) | Confidentiality policy. | `LlmOrchestrator.getSecurePrefs` | next settings change |
| 5 | Which `.env`/pref key names are contractual for the Android↔desktop parity (e.g. `agent_provider_*`, secure-pref file names)? The desktop keys are FACT, but the Android side's key inventory was not re-verified in this port. | Parity of settings migration. | `docs/config-and-env.md`, original Android source under `source/` (not scanned this pass) | cross-edition settings sync |
| 6 | `StudioViewModel` non-encrypted prefs contain theme/TTS/onboarding keys whose exact inventory we marked INFERRED — can a human confirm the full list (or accept it as internal detail)? | Avoids breaking saved prefs in refactor D-04. | `SettingsViewModel`, `StudioViewModel` | D-04 refactor |
| 7 | Are the six serial platform profiles (Royal Road, Wattpad, GoodNovel, Dreame, Webnovel, Tapas) the launch set, and is the recap-block default for GoodNovel/Dreame/Webnovel correct for their current submission UIs? | Profiles are versioned data but shipped defaults matter. | `engine/serialization/SerialExportProfiles.kt` | P5 publishing |
| 8 | Should the dashboard's profile picker expose all six profiles (D-09), and is `take(4)` row truncation acceptable for beta? | Beta usability. | `SerialDashboardScreen.kt` | next UI pass |
| 9 | Does the compiled-manuscript ISBN placeholder (D-05) need to survive into P5's retail pipeline, or is P5's metadata workbench its replacement? | Avoids double-booking metadata. | `CompilationWorker.kt`, plan §12 | P5 |
| 10 | Git history contains UUID-named commits (e.g. `fac08d1 1b4a9c79-…`) alongside phase commits — is there a canonical history elsewhere (the original Android repo) that this port's history must be reconciled with? | Provenance for audits/ADR evidence. | `git log` of `authr-desktop/` | docs integrity |
| 11 | The plan's P4 exit gate needs a Pocket TTS voice-pack distribution story (packs dir, model variants). Is the pack format/binary invocation protocol frozen yet? | P4 depends on a stable bridge. | `desktopbridge/PocketTtsBridge.kt` resolution order only; pack format UNKNOWN beyond `listVoicePacks` | P4 kickoff |
| 12 | Beta program: the plan's success metric requires "three beta authors each ship one serial and one retail title end to end" — is beta staffing/scheduling decided? | Program-level acceptance. | plan §15 | release planning |

## Documented assumptions (INFERRED — confirm or refute)

- Data root on Linux is `~/.local/share/authr` (observed in smoke logs; derived from the shim's `filesDir` wiring).
- RESOLVED AS FACT (self-check): `RequestQueueManager` persists failed requests to prefs (`offline_queue_data`) and restores their COUNT on launch, but never re-executes them — `serializedRequests` has no replay consumer (`data/api/RequestQueueManager.kt`). Treat the queue length as accounting, not backlog.
- The `source/` directory in the workspace is the original Android app used for parity reference only; it is not part of the desktop deliverable.
