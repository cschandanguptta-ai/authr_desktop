# Invariants & Gotchas

Things that must ALWAYS stay true, non-obvious behavior, and desktop-port traps. Labeling: FACT (seen in code), INFERRED, UNKNOWN. File refs are relative to `desktop/src/main/kotlin/`.

## Hard invariants (breaking any of these breaks a test or loses writer data)

1. **Writer sovereignty** — no text enters the manuscript canon without `writerConfirmed = true && draftStatus == CONFIRMED` (FACT: `engine/DraftState.kt`, `WriterGate.isCanon`). Every consumer (ledgers, exports, compile, downstream layers) must filter through `WriterGate.canonChapters()`. Enforced by tests: `WriterGateTest`, `SixLayerPipelineTest`, `WriterStudioFlowTest`, `SerializationGoldenSerialTest.canon gate` test.
2. **The confirm flow is the only promotion path** — L4 drafts always land PROPOSED (FACT: `worker/DraftingWorker.kt` sets `writerConfirmed=false, draftStatus=PROPOSED`); `WriterStudioViewModel.confirmChapter` is the sole writer of canon state (FACT).
3. **Additive migrations only** — every shipped `Migration` in `data/local/AppDatabase.kt` uses ALTER TABLE ADD COLUMN / CREATE TABLE only. Never rewrite history; never rely on `fallbackToDestructiveMigration(true)` (it exists but no shipped path depends on it — FACT).
4. **Entity/DB default convention** — serialization columns declare Kotlin defaults but NO `@ColumnInfo defaultValue`; the migration supplies SQL DEFAULTs, which Room tolerates (FACT: comment in `data/model/Book.kt` and `data/model/Chapter.kt`). Adding a column with an entity-declared default that mismatches the migration breaks schema validation.
5. **Waivers are never silent** — `ChekhovLedger.waive` requires a non-blank note (FACT: `engine/ChekhovLedger.kt`); compilation discloses waiver notes in the agent chat (FACT: `worker/CompilationWorker.kt`).
6. **The advisor never auto-assigns; the writer always sets scores** — `EndingAdvisor` only classifies + flags (FACT); `QcScorecard.propose` is a proposal, `gate()` enforces bands but the dashboard is the writer's edit surface (FACT: `engine/serialization/QcScorecard.kt`, `ui/viewmodel/SerialDashboardViewModel.saveScorecard`).
7. **Zero-triggers always block** — a 0 on scorecard criterion 2 (objective) or 6 (payoff) blocks export regardless of total; only an explicit writer note overrides, and 0–5 is never overridable (FACT: `QcScorecard.gate`).
8. **Anti-withholding, not cliffhangers** — the final free chapter must resolve part of the opening conflict AND seed a bigger development (FACT: `OpeningValidators.antiWithholdingGate`). The 1.0 `[CLIFFHANGER SCORE]` prompt instruction was removed in P3 (FACT: `worker/DraftingWorker.kt`).
9. **Audiobook renders consume canon only** — `AudiobookRenderer.render` filters through `WriterGate.canonChapters` (FACT: `engine/audio/AudiobookRenderer.kt`); skipped non-canon chapters are reported, never silently dropped.
10. **Audiobook mastering parameters are pinned** — loudnorm I=−18 LUFS, alimiter ceiling −3.1 dBFS (limit 0.6998 linear), bed duck −12 dB with 400 ms release, MP3 CBR 192 kbps / 44.1 kHz (FACT: `MasteringChain` defaults, asserted in `MasteringChainTest`). Changing a parameter means changing the plan Table 25 AND the test together.
11. **SMIL clip times are measured, not estimated** — clip times derive from measured fragment-synthesis durations; the synthesized segment sum is the canonical chapter duration, the MP3 probe is a 0.25 s cross-check only (ADR-0010). Do not swap the duration source.
12. **The read-along surface shows the manuscript, not the respellings** — SMIL/XHTML fragments carry canon text; only the synthesizer input is normalized through approved pronunciation rules (FACT: `canonTexts` vs `texts` in `AudiobookRenderer`, asserted in `AudiobookRendererTest`).
13. **Nothing enters synthesis without writer approval** — `PronunciationPass.normalize` applies only rules with `approvedByWriter = true` (FACT: `approvedFromContainer` filter); the respelling table is a proposal source, the writer's approval (or correction) is recorded per project in the container rules.
14. **Paste-ready exports only** — no platform API calls for serial publishing (FACT: `SerialExportProfiles` builds text packages; no HTTP in the module).
15. **Container migrations are forward-only and backed up** — `AuthrContainer.migrateForward` stamps a migration note and the import path backs up originals (FACT: `container/AuthrContainer.kt`; test: `AuthrContainerRoundTripTest`).
16. **Lore/mystery prompts are canon-locked** — L4 prompt context assembles from confirmed material only (FACT: `ui/viewmodel/WriterStudioViewModel.L4DraftGenerator` contract + `engine/L4LlmDraftGenerator.kt`).
17. **Polls never feed the engine** — reader polls are annotations on the manuscript; the drafting chain never reads them (plan §6.10; FACT: no poll field is read by any engine module).

## Non-obvious behavior & ordering requirements

- **The annotator memoizes (D-08, ADR-0012)**: `LiveAstAnnotator.annotate` caches the annotation list per (text hash, lorebook fingerprint) in a bounded LRU-32 and returns the SAME immutable list instance on a hit. Consequences: (1) the rail's recomposition on unchanged text is free (<1 ms measured vs 484 ms cold at 20k words); (2) a hit list must be treated as frozen — never mutate it, sort it in place, or store it in a mutable collection; (3) if a detector ever starts consuming MORE than the chapter content + lorebook (e.g. another Chapter field), its input must join the cache key or callers see stale annotations (change-impact row added); (4) refusals are never cached — a refused chapter that later gets confirmed annotates fresh. `clearAnnotationCache()` exists for tests; production code has no reason to call it.
- **Confirm order matters**: annotation → Chekhov upsert (insert/escalate/skip-duplicate) → question detection → orphan sweep **only at/after the final-act boundary** (FACT: `WriterStudioViewModel.confirmChapter`; sweep condition `frozen.chapterNumber >= finalActStart`).
- **Final-act boundary math**: `ChekhovLedger.finalActStartChapter(total)` — the P2 regression serial with `targetChapters = 6` has its final act at chapter 5 (FACT: `ChekhovLedgerTest` corpus comment). Changing the formula silently changes when the recovery gate starts blocking.
- **Duplicate detection compares archived context quotes** (±40 chars, overlap ≥ 0.6), not detector names — a re-emphasized promise escalates; a distinct promise of the same class stays separate (FACT: `LiveAstAnnotator.chekhovPromises` quote window + `ChekhovLedger.planUpsert`; asserted by `ChekhovLedgerTest.distinct vows`).
- **Golden corpora are cue-locked**: the planted texts in `GoldenSerial.kt` / `ChekhovLedgerTest.chapterTexts` are written to exactly match validator regexes. Editing a regex OR a planted sentence can flip a classification (this bit during P3: filler containing the word "sealed" scored an unintended IRREVERSIBLE_CHOICE ending). When touching either side, run the whole suite (FACT: P3 build-repair log).
- **Suite severity is positional**: `SerialValidatorSuite` HARD checks = canon gate + anti-withholding + scorecard + closed-arc completion; WARNs are dismissable and don't count toward the exit-gate budget once dismissed; INFO (balance) never blocks (FACT: `engine/serialization/SerialValidatorSuite.kt`).
- **The balance meter folds absent beats** into the previous present beat's band; the first beat owns the lead-in and the last owns the run to the end, so shares sum to 100% (FACT: `BalanceMeter.measure`).
- **Arc phase bounds compress proportionally** for truncated final arcs (`phaseBoundsJson(start, length)`), so a 100-chapter book's 7th arc can still close (FACT: `MicroArcPlanner.planArcs`).
- **Release state lives in `Chapter.status` text** as `Released (ISO date)` (FACT: `BufferCadence.RELEASED_PREFIX`). Status is also "Completed (N words)" / "Proposed (N words)" — don't parse it loosely.
- **The Writer Studio re-derives endingType at confirm time** even for non-serialized books (harmless extra field writes; FACT: confirm flow sets `endingType`/`beatCoverage`/`balanceShares` unconditionally, `hookCheck` only for free-tier chapters).
- **Ollama is tried with a key or "Bearer none"** and its base URL is user-pref-configurable (FACT: `LlmOrchestrator` Ollama block) — an empty-key run against Ollama is normal, not a bug.
- **`RequestQueueManager` persistence is accounting, not retry** — failed requests are saved to prefs (`offline_queue_data`) and their count is restored on launch, but nothing ever re-executes them (FACT: `loadQueueFromPersistence` fills `serializedRequests`, which no replay path consumes). Do not promise users that queued work replays.
- **EncryptedSharedPreferences falls back to plain prefs on failure** (FACT: `LlmOrchestrator.getSecurePrefs` catch block → `secure_studio_settings_fallback`). API keys may silently live unencrypted if the keystore fails — see tech-debt D-03.
- **Per-agent provider prefs** live in non-encrypted `studio_settings` prefs under `agent_provider_<key>` (FACT: `LlmOrchestrator.getProviderForAgent`).
- **Ledger status strings live in TWO places** — `ChekhovLedger` Kotlin constants (`INTRODUCED/ESCALATED/RESOLVED/ORPHANED/WAIVED`, `ChekhovLedger.kt` lines 26–30) AND hardcoded raw SQL in `ChekhovGunDao.getUnresolvedGuns` (`'INTRODUCED','ESCALATED','ORPHANED'`). Rename one side only and the recovery gate silently finds nothing unresolved — change both atomically (FACT, found in the P3-doc self-check).
- **QcScorecard null-card gate shape** — a chapter with blank `qcScores` gets `GateResult(total=-1, band=STRONG, releasable=false)`; the STRONG band is a placeholder, not a classification (FACT: `QcScorecard.gate` null branch). Don't "fix" it into a real band without a migration path for unscored chapters.
- **Edge TTS is GONE** (2026-11-12 audit, supersedes OQ-13's old risk-acceptance): `tts/EdgeTtsManager.kt` is deleted; synthesis degrades LOUD everywhere — `PocketTtsManager` fails with marker `pocket-failed` when the binary/pack is missing, and the desktop `TextToSpeech` shim routes through Pocket (local narrator slots). Never reintroduce a silent cloud fallback into the local synthesis path (ADR-0017's no-mixing contract now holds for every engine, not just the renderer).
- **The persona registry is the single persona source** (2026-11-12 audit, D-18): every prompt site renders personas through `engine/PersonaRegistry.cardBody`; pickers list through `utils/PersonaResolver` (built-ins + container plugin personas, built-ins winning id AND name collisions). Adding a persona = add it to `defaultPersonas` (or drop a validated plugin JSON into `plugins/personas/`) — never inline persona text into a prompt again. The 18+ gate is enforced in `PersonaResolver.resolve` on EVERY generation path (adult → standard with the gate closed), not only in the UI — bypassing it for a "quick call" reintroduces the ungated-presets hole.
- **API keys are either configured or absent** (2026-11-12 audit #9): `BuildConfig` resolves env/.env only and defaults to EMPTY — no placeholder fallback. A provider with no key is skipped loudly ("no API keys configured"); debug HTTP logging ships off unless `AUTHR_DEBUG=1`.

## Race conditions / concurrency

- `RequestQueueManager` gates concurrency with a `Semaphore(concurrencyLimit)` (FACT: `data/api/RequestQueueManager.kt` ≈ line 80) — priority affects dequeue order, not fairness (INFERRED).
- The desktop WorkManager shim runs chains on its own `SupervisorJob + Dispatchers.IO` scope (FACT: `DesktopWorkManager.scope`); ViewModel state flows are updated on `Dispatchers.Main.immediate` (FACT: VM constructors) — worker-written DB rows do NOT push into open ViewModels (no DB flow re-emission wired for chapter lists) — INFERRED from the DAO returning `Flow` but VMs re-fetching manually on actions.
- `AppDatabase.getDatabase` is a plain double-checked singleton with no thread-safe closing — hot-swap of the DB file while running is not supported (FACT).

## Hidden coupling

- `engine/serialization/GoldenSerial.kt` ↔ every validator regex: the corpus is the tests' fixture AND the exit-gate gatekeeper. Change one side only and the suite tells you which side moved.
- `LiveAstAnnotator.beatCueList` / `classifyEnding` were made public in P3 precisely so BalanceMeter/EndingAdvisor share the annotator's cue lists — do NOT fork new cue sets in the serialization package (FACT: P3 decision).
- `CompilationWorker` gate ORDER: canon exclusion → Chekhov recovery gate → serial suite (FACT: `worker/CompilationWorker.kt`). Reordering changes which gate a blocked writer sees first.
- Tab indices are load-bearing across `Main.kt` (bar + `when`) and the smoke harness — adding a tab means touching both (FACT).

## Fragile areas — do not touch without reading first

| Area | Why fragile | Read first |
| --- | --- | --- |
| `androidx/work/WorkManager.kt` shim | Reimplements WorkManager semantics (chains, backoff, periodic); no factories registered → chains no-op on desktop | `docs/tech-debt.md` D-01, `docs/system-map.md` background jobs |
| `LiveAstAnnotator` cue maps | Locked by golden corpora; public accessors feed BalanceMeter/EndingAdvisor | ADR-0006, `docs/testing.md` |
| `AppDatabase` migrations + entity defaults convention | Room schema validation is strict | `docs/data-model.md`, comments in `Book.kt` |
| `AuthrContainer` payload mappings | Round-trip guarantee is a P0 exit gate | ADR-0002, `AuthrContainerRoundTripTest` |
| `ClicheCleaner` 154-pattern matrix | Precision pinned at 1.0000; adding patterns risks false positives | ADR-0004, `ClicheCleanerGoldenCorpusTest` |
| `StudioViewModel` / `StoryScreen` god-classes | 5,496 / 4,446 lines; touch one feature, compile everything | `docs/tech-debt.md` D-04 |

## Past bugs and fixes evident from history (git log + build-repair notes)

- **P3 (2026-11-09)**: `classifyEnding` returned the wrong shape (`if (a) x else y to z` parsed as `(if (a) x else y) to z` in Kotlin) — fixed with an explicit local (`val type = … ?: QUIET`) in `LiveAstAnnotator.classifyEnding` (FACT: git commit "v3.0 P3").
- **P3**: golden serial filler leaked ending-cue words ("sealed") into closings, flipping classifications — filler rewritten to avoid all cue families (FACT: `GoldenSerial` FILLER doc comment).
- **P3**: BalanceMeter shares didn't sum to 100 (misaligned segment starts) — fixed by attributing lead-in to the first beat and the tail to the last (FACT: `BalanceMeter.measure`).
- **P2**: Maven Central 429s against Gradle's parallel metadata bursts — workaround: `mavenLocal()` first in `settings.gradle.kts` + curl-seeded artifacts (FACT: comment in `settings.gradle.kts`).
- **P1/P2**: worker gates enforced at engine level because the desktop WorkManager shim can't construct DAO-backed workers without factories (FACT: P2 report §6; shim `runSingle`).
