# Change Impact — "If I change X, what else breaks?"

Labeling: FACT = verified in code at the cited location; INFERRED = derived from structure. All paths relative to `desktop/src/main/kotlin/` unless noted.

## Shared code and shared types (touch these → re-verify everything that consumes them)

| Shared artifact | Where | Who consumes it | Impact if changed |
| --- | --- | --- | --- |
| `WriterGate.isCanon / canonChapters` | `engine/DraftState.kt` | WriterStudioViewModel, SerialValidatorSuite, CompilationWorker, SixLayerPipeline, BalanceMeter, EndingAdvisor, BufferCadence, tests (WriterGateTest, SixLayerPipelineTest, WriterStudioFlowTest, SerializationGoldenSerialTest, SerialDashboardTest) | Tightening blocks legit flows; loosening breaks the P1 exit-gate assertion (unconfirmed text reaching consumers). Any change → run the full suite. |
| `DraftState.PROPOSED/CONFIRMED/ARCHIVED` | `engine/DraftState.kt` | Chapter entity defaults, workers, VMs, tests | Renaming a constant changes persisted `draftStatus` strings — DB rows stop matching `WriterGate`. Never rename without a migration. |
| `LiveAstAnnotator.beatCues` / `classifyEnding` | `engine/LiveAstAnnotator.kt` | BalanceMeter (`beatCueList`), EndingAdvisor + suite + WriterStudioViewModel (`classifyEnding`), ChekhovLedgerTest golden corpus text, SerializationGoldenSerialTest planted corpus | Editing a beat cue regex or an ending cue regex silently re-classifies the golden serial and breaks planted assertions (ending types, coverage 7/7, balance shares). Golden serial texts in `engine/serialization/GoldenSerial.kt` are written against these exact cue sets. |
| `ClicheCleaner` pattern matrix | `engine/ClicheCleaner.kt` | LiveAstAnnotator.clicheHits, L4 generator prompt constraints, TokenBiasPolicy, ClicheCleanerGoldenCorpusTest (precision 1.0000 assertion) | Adding patterns can create false positives on the golden corpus text and drop precision below the pinned assertion. Adding a class changes the 6-class matrix parity test. |
| `ChapterBeat` enum | `engine/ManuscriptAst.kt` | annotator, BalanceMeter bands, golden serial, UI beat cards | Reordering/removing breaks persisted `beatCoverage` JSON semantics and band mapping. |
| `EndingTypes` constants | `engine/ManuscriptAst.kt` | annotator, advisor, Chapter.endingType persistence, suite, OpeningValidators.HIGH_INTENSITY_ENDINGS | Persisted `endingType` strings must keep matching; renames need migration. |
| `AppRepository` | `data/repository/AppRepository.kt` | All ViewModels + workers | Its ledger/arc DAOs are nullable-by-default (`chekhovGunDao`, `questionDao`, `serialArcDao` = null) — a new DAO parameter must default to null or every test fixture breaks. |
| `BuildConfig` keys | `com/example/BuildConfig.kt` | LlmOrchestrator, TTS managers | Adding a key = also update `docs/config-and-env.md` and the `.env` loader (already generic). |
| `RequestQueueManager` semaphore/queue | `data/api/RequestQueueManager.kt` | Every LLM call | Concurrency limits affect all providers; persisted-request format changes can strand queued work. |

## Feature-level change map

| If you change… | Also check/update | Tests to run |
| --- | --- | --- |
| **The confirm flow** (`WriterStudioViewModel.confirmChapter`) | The persisted serialization artifacts (endingType, beatCoverage, balanceShares, hookCheck JSON written here, P3); ledger upsert order (ChekhovLedger.planUpsert → questions → orphan sweep); `WriterStudioScreen` status line | `WriterStudioFlowTest`, `ChekhovLedgerTest`, `SerializationGoldenSerialTest`, `QuestionLedgerTest` |
| **Any serialization validator** (`engine/serialization/*`) | The golden corpus cues in `GoldenSerial.kt` (planted sentences match validator regexes exactly); `SerialValidatorSuite` severity mapping; dashboard UI strings | `SerializationGoldenSerialTest`, `AntiWithholdingGateTest`, `ScorecardGateTest`, `MicroArcPlannerTest`, `BufferCadenceTest`, `EndingAdvisorBalanceWorksheetTest` |
| **The anti-withholding gate cue regexes** (`OpeningValidators.openingConflictPayoffCues` / `consequentialSeedCues`) | `GoldenSerial.WITHHOLDING_PAYOFF/_SEED` planted sentences and the withheld variant replacements — the gate must keep firing exactly on the variant | `AntiWithholdingGateTest` |
| **Scorecard bands or zero-triggers** (`QcScorecard.Band`, `ZERO_TRIGGERS`, `gate()`) | `CompilationWorker` serial gate (calls `SerialValidatorSuite.run`), dashboard scorecard editor chip, `GoldenSerial.scorecardBandVariants`. The `qcScores` JSON keys (`c1..c7`, `r1..r7`) are **persisted per chapter** and parsed with `optInt(..., 0)` — renaming a key silently scores old chapters as all-zeros (false zero-triggers, hard-block) | `ScorecardGateTest` |
| **Arc phase defaults** (`MicroArcPlanner.phaseBoundsJson`, `Phase`) | `GoldenSerial.arc()` phaseBounds (expects default 15-chapter layout), dashboard arc board chips, `phaseBounds` JSON parsing | `MicroArcPlannerTest` |
| **Buffer thresholds or release marker** (`BufferCadence.RELEASED_PREFIX`, `releaseMarker`) | Dashboard `ChapterRow` chip logic and `releaseChapter`; old chapters whose status already says "Completed (N words)" | `BufferCadenceTest`, `SerialDashboardTest` |
| **Export profiles** (`SerialExportProfiles.PROFILES`) | `Book.exportProfile` values persisted in DB; dashboard profile picker (takes `PROFILES.take(4)`); `byId` fallback | `SerialExportProfilesTest` |
| **`Book` entity / `BookDao`** | `AppDatabase` (no migration needed for same-shape changes; new columns need `MIGRATION_15_16`), `AuthrContainer` BookPayload mapping, `StudioViewModel` + `SerialDashboardViewModel` copy() call sites, plan Table 21 field parity comments in `Book.kt` | `AuthrContainerRoundTripTest`, `SerialDashboardTest` |
| **`Chapter` entity** | Same as Book + `WriterGate` defaults (`writerConfirmed`, `draftStatus`), the P3 serialization columns, `AuthrContainer` ChapterPayload | `AuthrContainerRoundTripTest` + suite |
| **Ledger/arc entities** (`ChekhovGun`, `QuestionEntry`, `SerialArc`) | `AuthrContainer` payload mirrors (`GunPayload`/`QuestionPayload`/`ArcPayload`, `ledger/*.json` + `arcs/` in the zip — round-trip test pins them); **lifecycle status strings live in TWO places** — Kotlin constants (`ChekhovLedger.INTRODUCED/…`) AND hardcoded raw SQL in `ChekhovGunDao.getUnresolvedGuns` (`'INTRODUCED','ESCALATED','ORPHANED'`) — rename one side only and the recovery gate silently finds nothing; `QuestionLedger`/`MicroArcPlanner` matching; dashboard ledger/arc panels; the per-test fake DAOs | `ChekhovLedgerTest`, `QuestionLedgerTest`, `MicroArcPlannerTest`, `AuthrContainerRoundTripTest` |
| **`AppDatabase` version/migrations** | `docs/data-model.md`; the no-`@ColumnInfo`-defaultValue convention (schema validation tolerates DB-side defaults — see comment in `Book.kt`); `fallbackToDestructiveMigration` must NOT gain new users | `AuthrContainerRoundTripTest` (migration path) |
| **`LlmOrchestrator.callAgent` signature** | All call sites: 6 workers, StudioViewModel wizard calls, `L4LlmDraftGenerator`; the P2 `logitBias` optional param must stay optional (Moshi adapter + call sites) | `TokenBiasPolicyTest` + full compile |
| **Provider models/URLs** (`LlmOrchestrator` provider blocks) | `docs/api-contracts.md`, `SettingsViewModel` model pickers, `ModelDiscoveryService` endpoints | manual + `docs` update |
| **Worker construction** (adding a worker) | **Register a factory** in `DesktopWorkManager.registerWorker(...)` — the shim runs ONLY registered workers (`runSingle` returns false otherwise). Today zero workers are registered (see tech-debt D-01). | manual chain run |
| **`CompilationWorker` gates** | Order matters: canon gate → Chekhov recovery gate → serial suite (anti-withholding + scorecard + arc completion). Hard blocks must `return Result.failure()` AFTER logging each block to the agent chat. | `SerializationGoldenSerialTest` (suite semantics), engine tests |
| **`DraftingWorker` serialized prompt block** | `OpeningValidators.L4_OPENING_RULE`, `PreDraftWorksheet.promptBlock`, the four ultimate rules wording (quoted verbatim per plan §6.9) | `PreDraftWorksheet`-related tests |
| **`OutlineWorker` serial gate** | `OpeningValidators.outlineCarriesIncitingBeat` cues; gate failure sets phase "Failed" and returns Result.failure() | manual |
| **Plugins scaffold** (`PluginRegistry.scaffoldDefaults`, `ShippedPlugins`) | `Main.kt` LaunchedEffect; container plugin dir layout (`<authr>/plugins/{structures,personas}`); `PluginRegistryTest` | `PluginRegistryTest` |
| **`.authr` container schema** (`AuthrContainer.SCHEMA_VERSION`, payloads) | Migration `migrateForward` (additive only), `AuthrContainerRoundTripTest` 5 tests, plan Appendix A | `AuthrContainerRoundTripTest` |
| **TTS managers / bridge binary resolution** | `POCKET_TTS_BIN` / `FFMPEG_HOME` env docs; AudioScreen UI; `Mp3ToWav`. **`EdgeTtsManager` extra blast radius**: it borrows the shared `ApiClient.okHttpClient` (client config changes affect the synthesis WebSocket); its pinned handshake constants (`TRUSTED_CLIENT_TOKEN`, `Sec-MS-GEC` token, Edge UA/Origin headers) are a reverse-engineered Microsoft contract — do not "clean them up"; its callers are `StudioViewModel` (direct `speakParagraphSuspend`/`synthesizeToFile`), the desktop `TextToSpeech` shim (`android/speech/tts/TextToSpeech.kt` routes the whole Android-TTS engine through it), and `PocketTtsManager` (offline fallback, engine marker `edge-fallback`) | manual smoke |
| **`StudioViewModel` tab indices** | `Main.kt` `GlobalWorkspaceBar` (0/1/2/3/5/6/7) and `when(currentTab)` branches | manual UI smoke |

## Blast-radius quick reference

| Change area | Blast radius | Files to re-verify | Tests |
| --- | --- | --- | --- |
| Writer-sovereignty gate | **Very large** — every consumer | DraftState.kt + all consumers above | full suite |
| Serialization validators | Large (golden corpus is cue-locked) | GoldenSerial.kt, SerialValidatorSuite.kt, dashboard | 6 P3 test classes |
| Chekhov/question ledgers | Medium-large | VM confirm flow, CompilationWorker recovery gate, dashboard ledger panel | ChekhovLedgerTest, QuestionLedgerTest |
| DB schema | Large, migration-sensitive | AppDatabase, entities, container payloads | AuthrContainerRoundTripTest + full suite |
| LLM orchestration | Medium (runtime only, no tests cover network) | workers, VMs, api-contracts docs | compile + manual |
| WorkManager shim | Medium (chains currently no-op) | all workers, tech-debt docs | manual |
| UI screens | Low (ViewModel contracts) | corresponding VMs | manual smoke |
| Docs | None — but stale docs are treated as bugs (AGENTS.md workflow) | — | — |

## DB schema coupling

FACT (`data/local/AppDatabase.kt`):
- `MIGRATION_14_15` adds the whole v3.0 serialization profile; entities declare **no** `@ColumnInfo` defaults for migrated columns (Kotlin defaults govern writes; Room tolerates DB-side defaults — see the comment block in `data/model/Book.kt`).
- `books` ⇐ `chapters` (FK, CASCADE), `books` ⇐ `serial_arcs` (FK CASCADE, unique `(bookId, arcIndex)`), `books` ⇐ `chekhov_guns` / `questions` (FK CASCADE).
- Changing FK/indices requires table rebuild migrations; additive ALTERs only elsewhere (hard rule #3 in `AGENTS.md`).

## API contracts coupling

- `OpenAiChatRequest.logit_bias` is serialized **only when non-null** (P2 decision, ADR-0005); removing the null-guard changes every OpenAI-compatible request body.
- Gemini request shape (`GeminiRequest` in `data/api/GeminiModels.kt`) is pinned by `ApiClient.geminiService.generateContent(model, apiKey, request)` — model name comes from prefs `gemini_model`.
- `logitBias` map keys are token IDs produced by `TokenBiasPolicy` — changing the payload format breaks Groq/NVIDIA/Custom-OpenAI requests silently (HTTP 400s at runtime, no test coverage — see testing.md "not covered").

## Env vars / flags coupling

See `docs/config-and-env.md` (single source of truth). Adding a key: `BuildConfig.resolveKey` + `.env` loader are generic; document it there and in the provider block that reads it.
