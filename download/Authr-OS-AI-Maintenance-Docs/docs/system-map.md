# System Map

All paths are relative to `authr-desktop/desktop/src/main/kotlin/` unless noted. Labeling per repo convention: FACT = seen in code, INFERRED = derived, UNKNOWN = needs human confirmation.

## Mermaid — components and data flow

```mermaid
flowchart LR
    subgraph UI["Compose UI (desktop)"]
        Main["Main.kt<br/>AuthrDesktopApp + GlobalWorkspaceBar"]
        Story["StoryScreen (4,446 lines)<br/>StoryEditorScreen, OutlineBibleTab"]
        Studio["WriterStudioScreen<br/>PROPOSED/CONFIRMED/ARCHIVED"]
        Serial["SerialDashboardScreen<br/>buffer, cadence, arcs, suite"]
    end

    subgraph VM["ViewModels"]
        SVM["StudioViewModel (5,496 lines)"]
        WVM["WriterStudioViewModel<br/>confirm flow + ledgers"]
        SDVM["SerialDashboardViewModel"]
        SEVM["StoryEditorViewModel"]
        SetVM["SettingsViewModel"]
    end

    subgraph Engine["engine/ (deterministic, JVM-pure)"]
        Gate["WriterGate + DraftState<br/>canon gate"]
        AST["LiveAstAnnotator<br/>+ ManuscriptAst"]
        Pipe["SixLayerPipeline"]
        Ledgers["ChekhovLedger + QuestionLedger"]
        Cliche["ClicheCleaner + TokenBiasPolicy"]
        SerialE["engine/serialization/*<br/>validators, scorecard, arcs, exports"]
    end

    subgraph Data["data/"]
        Repo["AppRepository"]
        DB[("AppDatabase (Room KMP)<br/>v15, 11 entities")]
        Orch["LlmOrchestrator<br/>+ RequestQueueManager + ApiClient"]
        Container["AuthrContainer (.authr zip)<br/>schema v2"]
        Plugins["PluginRegistry<br/>structures + personas"]
    end

    subgraph Workers["worker/ (WorkManager shim)"]
        Chain["WorldBuilding → Outline → Drafting →<br/>Polishing → Compilation chain"]
        SerialGates["CompilationWorker: canon gate +<br/>recovery gate + serial export gates"]
    end

    subgraph Media["tts/ + desktopbridge/"]
        TTS["PocketTtsManager / AndroidTts /<br/>EdgeTts / CloudTts"]
        PBridge["PocketTtsBridge (child process)"]
        FF["FfmpegLocator + Mp3ToWav +<br/>RxFFmpegInvoke (audio only)"]
    end

    Main --> Story & Studio & Serial
    Story --> SVM & SEVM
    Studio --> WVM
    Serial --> SDVM
    SVM & SEVM --> Orch
    WVM --> Gate & AST & Pipe & Ledgers & Repo
    SDVM --> SerialE & Repo
    Orch --> Ledgers
    Cliche --> AST
    SerialE --> Gate
    SVM --> Chain
    Chain --> Orch
    SerialGates --> SerialE
    Repo --> DB
    Container --> Repo
    Plugins --> Main
    TTS --> PBridge & FF
```

## Modules

| Path | Responsibility | Depends on | Depended on by |
| --- | --- | --- | --- |
| `Main.kt` | App entry (`MainKt`), tab routing (0 Story, 1 Audio, 2 Translate, 3 Metadata, 5 Settings, 6 Writer Studio, 7 Serial), `GlobalWorkspaceBar`, plugin scaffold on launch | all screens + ViewModels | — (entry point) |
| `ui/screens/*` | Compose screens; `StoryScreen` is the wizard/library hub, `WriterStudioScreen` the review surface, `SerialDashboardScreen` the publication surface | ViewModels | `Main.kt` |
| `ui/viewmodel/StudioViewModel.kt` | Master VM: books/stories CRUD, wizard LLM calls, autonomous pipeline enqueue, TTS settings, logs (5,496 lines) | AppRepository, LlmOrchestrator, WorkManager shim | `Main.kt`, screens |
| `ui/viewmodel/WriterStudioViewModel.kt` | Confirm flow: freeze text → persist serialization artifacts → L6 annotate → ledger upserts → orphan sweep; queue actions; archive | AppRepository, engine/* | `WriterStudioScreen` |
| `ui/viewmodel/SerialDashboardViewModel.kt` | Publication dashboard state: recompute suite/buffer/cadence, arcs, worksheet/scorecard saves, release actions, exports | AppRepository, engine/serialization/* | `SerialDashboardScreen` |
| `engine/DraftState.kt` | Writer-sovereignty contract: `DraftState` states, `EngineLayer`, `WriterGate` (canon filter + violations) | `data/model/Chapter` | every consumer of chapters |
| `engine/ManuscriptAst.kt` | AST types: `AnnotationKind`, `Severity`, `AstAnnotation`, `ManuscriptAst`, `ChapterBeat`, `EndingTypes` | — | annotator, VMs, ledgers |
| `engine/LiveAstAnnotator.kt` | Deterministic annotator of confirmed text: beat coverage, cliché hits, promises, lore, ending signal, continuity flags; `classifyEnding` + `beatCueList` public accessors (P3) | `ClicheCleaner`, ManuscriptAst | VMs, `SixLayerPipeline` |
| `engine/SixLayerPipeline.kt` | Layer contract L1–L6, ledger upsert builder, `assertGate`, `DraftArchive` (archived-draft files) | WriterGate, ManuscriptAst | tests, VMs |
| `engine/ClicheCleaner.kt` | 154-pattern cliché matrix, 6 classes; `scan`, `offenderTokens`, `biasPayload` | — | annotator, L4 generator |
| `engine/TokenBiasPolicy.kt` | Maps offender tokens to `logit_bias` payload (−5 weight, 300-entry ceiling) | ClicheCleaner | `L4LlmDraftGenerator` |
| `engine/ChekhovLedger.kt` | Gun lifecycle (INTRODUCED/ESCALATED/RESOLVED/ORPHANED/WAIVED), upsert planning, orphan sweep, final-act `recoveryGate` | model/ChekhovGun | VMs, CompilationWorker |
| `engine/QuestionLedger.kt` | 3 information levels, expected-answer windows, overdue surfacing, revelation outcomes | model/QuestionEntry | VMs, suite |
| `engine/serialization/OpeningValidators.kt` | Hook validator (7 sources of interest), story promise, exposition runs, substantial developments, **anti-withholding gate**, outline gate, L4 opening rule | WriterGate, EndingTypes | suite, workers, VMs |
| `engine/serialization/BalanceMeter.kt` | Seven-beat segment shares vs advisory bands; word envelope; `balanceSharesJson` | LiveAstAnnotator cues | suite, VMs |
| `engine/serialization/EndingAdvisor.kt` | Ending history + consecutive high-intensity fatigue flags (never auto-assigns) | LiveAstAnnotator, OpeningValidators | suite |
| `engine/serialization/MicroArcPlanner.kt` | Arc layout (15-chapter cycles, proportional truncation), 6 planner checks, completion requirement, ship report | model/SerialArc, QuestionEntry | suite, dashboard, workers |
| `engine/serialization/PreDraftWorksheet.kt` | Worksheet A–E model, JSON round-trip, validation, auto-fill, prompt block | org.json | workers, dashboard, exports |
| `engine/serialization/QcScorecard.kt` | 7 criteria × 0–2, bands + zero-triggers + note-only overrides; ten principles + four ultimate rules | org.json | suite, dashboard, workers |
| `engine/serialization/BufferCadence.kt` | Buffer meter (8/4 thresholds), release markers, cadence tracker (schedule parse, slips, streak) | model/Book, Chapter | suite, dashboard |
| `engine/serialization/SerialExportProfiles.kt` | 6 paste-ready platform profiles, package builder, batch export | PreDraftWorksheet, OpeningValidators | dashboard |
| `engine/serialization/SerialValidatorSuite.kt` | Orchestrator: PASS/INFO/WARN(dismissable)/HARD checks over a serial project; exit-gate predicate; Autonomous draft report | all serialization engines | dashboard, CompilationWorker |
| `engine/serialization/GoldenSerial.kt` | Deterministic 15-chapter planted corpus + negative variants (withheld, beat-gap, scorecard bands) | all serialization engines | tests, probes |
| `data/repository/AppRepository.kt` | Single data facade over all DAOs (nullable ledger/arc DAOs keep JVM tests Room-free) | all DAOs | ViewModels, workers |
| `data/local/AppDatabase.kt` | Room KMP DB, v15, 11 entities, migrations 12→15, `fallbackToDestructiveMigration(true)` | entities | repository |
| `data/local/*Dao.kt` | DAOs: Story, AudioTrack, Book, Chapter, Lorebook, SerialArc, ChekhovGun, Question, Plugin | models | repository |
| `data/model/*.kt` | Room entities + FTS: Book (serial profile fields), Chapter (engine state + serialization fields), SerialArc, ChekhovGun, QuestionEntry, PluginRecord… | — | DB, container, engine |
| `data/api/LlmOrchestrator.kt` | 6 agents × 7 providers, per-agent provider prefs, failover sequence, `logitBias` passthrough (P2) | ApiService, BuildConfig, RequestQueueManager | workers, VMs, L4 generator |
| `data/api/RequestQueueManager.kt` | Global request queue: priority + `Semaphore` concurrency limit, persisted request accounting (failed requests are saved and counted but NEVER replayed — no replay path exists) | ApiService | LlmOrchestrator |
| `data/api/ApiService.kt` | Retrofit interfaces + models for Gemini/Ollama/Mistral/HF/OpenRouter/OpenAI-compatible | Retrofit/Moshi | orchestrator, discovery |
| `data/api/ModelDiscoveryService.kt` | Live model listing per provider (settings UI) | ApiService | SettingsViewModel |
| `container/AuthrContainer.kt` | `.authr` zip interchange (schema v2): export/import, v1→v2 migration, backup | models, org.json | tests (`AuthrContainerRoundTripTest`) |
| `plugins/PluginRegistry.kt` | Structure/persona JSON plugins under `<authr>/plugins/{structures,personas}`, scaffold of shipped defaults | ShippedPlugins, PluginSchema | `Main.kt`, drafting prompts |
| `worker/*.kt` | CoroutineWorkers: WorldBuilding, Outline (serial outline gate), Drafting (Blueprint 2.0 block), Polishing, Compilation (canon + recovery + serial gates), EpubExport, StorageCleanup, WorkerUtils (`logAgentChat`, `retryWithBackoff`) | DAOs, engine, orchestrator | WorkManager shim chains |
| `androidx/work/WorkManager.kt` | Ported WorkManager shim: chains, periodic jobs, `runSingle` via registered factories; **no factories registered anywhere → chains no-op (see tech-debt)** | — | ViewModels/workers |
| `tts/*.kt` | PocketTtsManager (local), AndroidTtsManager, EdgeTtsManager, CloudTtsManager | desktopbridge, BuildConfig | AudioScreen/VMs |
| `desktopbridge/PocketTtsBridge.kt` | Pocket TTS child-process bridge; binary resolution: bundled → `POCKET_TTS_BIN` → PATH → `~/.authr/pocket-tts` | ProcessBuilder | PocketTtsManager |
| `desktopbridge/FfmpegLocator.kt` | ffmpeg resolution: bundled → `FFMPEG_HOME` → PATH | — | audio workers |
| `utils/*` | EpubUtils/EpubManager, AudioEncoder/Mixer, WavUtils, ZipUtils, LocalVectorRag, JsonStructuralEnforcer, TxtManager | — | workers, VMs |

## Entry points

- **App**: `Main.kt` → `fun main()` → `com.example.MainKt` (FACT: `compose.desktop.application.mainClass = "com.example.MainKt"` in `desktop/build.gradle.kts`).
- **Test**: `gradle :desktop:test` over `desktop/src/test/kotlin/com/example/`.
- **Distribution**: `gradle :desktop:createDistributable` → `desktop/build/compose/binaries/main/app/AuthrOS/` (FACT).

## Background jobs

FACT: the ViewModels enqueue a 5-worker chain — `WorldBuildingWorker → OutlineWorker → DraftingWorker → PolishingWorker → CompilationWorker` (e.g. `StudioViewModel.kt` ≈ line 4180) — plus `EpubExportWorker` and periodic `StorageCleanupWorker`, all through the ported WorkManager shim.
INFERRED/CRITICAL: the shim's `runSingle` (`androidx/work/WorkManager.kt` ≈ line 480) requires a factory registered via `DesktopWorkManager.registerWorker(...)`, and **no call site registers any worker** — so these chains log `"No factory for …; is it registered in the DI module?"` and stop. The workers' *logic* is still the enforcement seam spec (gates at the same points as tests) but does not execute on desktop. See `docs/tech-debt.md` D-01 and `docs/troubleshooting.md` T-09.

## External services

FACT (`data/api/ApiService.kt` base URLs + `LlmOrchestrator.kt`):
- Gemini `https://generativelanguage.googleapis.com/`
- Ollama `https://api.ollama.com/` (configurable base URL)
- Mistral `https://api.mistral.ai/`
- HuggingFace inference `https://api-inference.huggingface.co/` (translation features)
- OpenRouter `https://openrouter.ai/`
- OpenAI-compatible (Groq `https://api.groq.com/openai/v1/…`, NVIDIA `https://integrate.api.nvidia.com/v1/…`, Custom base URL)
- Pocket TTS local binary (no network), Edge TTS (network, `tts/EdgeTtsManager.kt`), ElevenLabs/Google TTS keys via BuildConfig (FACT: `BuildConfig.kt`).
