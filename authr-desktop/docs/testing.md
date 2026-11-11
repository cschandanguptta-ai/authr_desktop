# Testing

How tests are organized, what is and isn't covered, which tests guard which areas, and how to verify a change safely. All paths relative to `authr-desktop/desktop/src/test/kotlin/com/example/` unless noted. Labeling: FACT / INFERRED.

## Organization

- **Framework**: `kotlin("test")` on JUnit4 runner + `kotlinx-coroutines-test` (FACT: `desktop/build.gradle.kts` test deps). Tests use `kotlin.test` assertions and backtick names.
- **JVM-pure, Room-free**: every test runs without Android or Room. DAOs are faked with in-memory `MutableStateFlow` maps implementing the DAO interfaces (FACT: `ChekhovLedgerTest` "In-memory DAO fakes" section; `SerialDashboardTest` fakes). The repository's nullable DAO parameters (`chekhovGunDao`, `questionDao`, `serialArcDao` default null) are what make this possible — keep new DAO params nullable with defaults.
- **ViewModels are testable** because they take injected scope + generators (`WriterStudioViewModel` takes `CoroutineScope`, `L4DraftGenerator`, optional `authrRoot`; `SerialDashboardViewModel` takes `CoroutineScope`, optional `exportDir`).
- **Engine code is pure**: `engine/` and `engine/serialization/` have no Context dependencies by design.

## Suite inventory (221 tests, all green as of the post-P5 maintenance slice)

| Test class | Tests | Guards (exit gates in bold) |
| --- | --- | --- |
| `AuthrContainerRoundTripTest` | 5 | **P0 gate: v2.3 project round-trips the .authr container losslessly**; plugin schema |
| `PluginRegistryTest` | 6 | shipped plugin scaffold + schema |
| `WriterGateTest` | 3 | canon predicate, violations list |
| `SixLayerPipelineTest` | 6 | **P1 gate primitives** (assertGate/gateHolds), layer contract |
| `WriterStudioFlowTest` | 3 | **P1 gate: five-chapter novella end-to-end through the confirm flow; no unconfirmed text reaches ledgers/exports** |
| `LiveAstAnnotatorTest` | 11 | annotator determinism, refusal of non-canon text, per-detector behavior |
| `AnnotatorPerfTest` | 9 | **D-08 guards (ADR-0012)**: memoization contract (same-instance hits, text/lorebook misses, LRU bound, refusal non-caching), echo punctuation equivalence, O(1) span-dedupe overlap invariant, 20k-word latency ceilings (cold <5 s, warm <100 ms) |
| `ClicheCleanerGoldenCorpusTest` | 8 | **P2 gate: precision 1.0000 on the 50-page golden corpus; 154-pattern matrix parity with plan Table 6** |
| `ChekhovLedgerTest` | 7 | **P2 gates: planted guns detected; zero false compile blocks**; lifecycle (escalate/resolve/waive/orphan sweep/recovery gate) |
| `QuestionLedgerTest` | 6 | windows, overdue, revelation outcomes |
| `TokenBiasPolicyTest` | 8 | bias payload construction (−5 weight, 300-entry ceiling) |
| `SerializationGoldenSerialTest` | 13 | **P3 gate 1: 15-chapter golden serial passes the full validator suite, ≤2 dismissable warnings**; corpus properties; canon-gate composition |
| `AntiWithholdingGateTest` | 7 | **P3 gate 2: anti-withholding fires exactly on the seeded withheld-payoff project** |
| `ScorecardGateTest` | 8 | **P3 gate 3: interpretation bands + zero-triggers** |
| `MicroArcPlannerTest` | 8 | arc layout, planner checks, completion requirement |
| `BufferCadenceTest` | 7 | buffer levels, release markers, cadence slips |
| `SerialExportProfilesTest` | 7 | per-platform paste-ready behaviors |
| `EndingAdvisorBalanceWorksheetTest` | 10 | advisor, balance meter, worksheet |
| `SerialDashboardTest` | 7 | dashboard VM over fakes (recompute, release, dismissal, persistence) |
| `PronunciationPassTest` | 8 | **P4 primitives**: scan (lorebook/mid-sentence capitals/coined; possessives & contractions excluded), respell table incl. espeak ɹ/ɾ/ɚ symbols, verbatim normalization, container rules round-trip |
| `MasteringChainTest` | 6 | **P4 gate primitives**: Table-25 parameter pinning (I=−18, TP=−3.1, limit 0.6998, duck −12 dB/400 ms), fixed layer order, bedless skip, real-ffmpeg smoke |
| `AcxAuditTest` | 6 | **P4 gate primitives**: parser unit tests (astats/ebur128/ametadata/ffprobe), red/green boundaries on every criterion, real mastered-speech audit green by construction |
| `SmilExporterTest` | 7 | **P4 gate primitives**: fragment split, word-weighted timings, SMIL/XHTML pairing, timing sanity accepts coverage & rejects gaps/overlaps/mismatched last clip |
| `AudiobookRendererTest` | 8+ | **P4 gate: canon-only render, normalized synthesis vs canon display surface, measured clip times, continuity mode recorded, synthesis-speed factor, real espeak+ffmpeg 2-chapter integration (skipped when binaries absent)** |
| `PublishingMetadataTest` | 13 | **P5 primitives**: workbench field caps (7 keywords / 3 categories), fromBook seeding, royalty bands ($2.99–$12.99 boundaries), per-MB delivery rounding, print cost/royalty/breakeven formulas, workbench JSON round-trip + legacy string-envelope compat |
| `Epub3ExporterTest` | 9 | **P5 primitives**: mimetype first+STORED, package doc (identifier/subject/dcterms:modified/collection), nav ↔ chapter links, spine, XML escaping, cover slot, manifest-href resolution |
| `PrintInteriorTest` | 10 | **P5 primitives**: gutter ladder table exactness (plan §11.1), 6×9 in MediaBox, ladder-compliant gutters, pagination convergence, embedded-font text extraction; **D-16 font resolver** (auto-resolve lands on a real face, strict explicit path fails loudly, total miss lists the search locations — assumes-skips where system dejavu exists) |
| `VolumeBundlerTest` | 5 | arc grouping + reading order, size fallback, shared-exporter volume EPUBs |
| `ShipReportTest` | 7 | **P5 primitives**: clean = every verdict passed (UNVERIFIED ≠ clean), artifact/verdict tables, waiver disclosure with mandatory notes |
| `RetailPackagerTest` | 9 | **P5 gate: one-action retail package (novel + serial volumes), canon exclusions disclosed, workbench authority, pricing record; real epubcheck 5.2.1 integration — zero errors verified, failures block the clean verdict (skipped when the tool is absent)** |

## What is NOT covered (be honest before shipping a change)

- **Network**: no test touches Retrofit/OkHttp — provider request shapes, failover, and the `logit_bias` payload on the wire are compile-only + runtime-verified (INFERRED from the test list; no MockWebServer dependency exists in the build file).
- **The autonomous worker chain**: the WorkManager shim has no registered factories (tech-debt D-01), so `WorldBuildingWorker → … → CompilationWorker` logic beyond the engine functions is exercised only by reading + manual runs. The gates those workers *would* enforce are tested at the engine level.
- **Compose UI**: no UI tests / screenshot assertions. Visual verification is a workstation task (Xvfb + Robot smoke harness in `scripts/p3_smoke.sh` captures screenshots but asserts nothing).
- **TTS binaries**: `PocketTtsBridge` is tested only for absent-binary behavior (status MISSING — INFERRED); real synthesis was benchmarked manually (P0 spike: 7.3× real time on CPU).
- **Android-only paths in shims** (MediaStore, StaticLayout, etc.).

## Which tests guard which areas (quick map)

| You changed… | Minimum test set | Full set |
| --- | --- | --- |
| `engine/serialization/*` | the 6 P3 classes | full suite |
| `engine/DraftState.kt` / confirm flow | WriterGateTest, SixLayerPipelineTest, WriterStudioFlowTest | full suite |
| ledgers | ChekhovLedgerTest, QuestionLedgerTest | full suite |
| cliché matrix | ClicheCleanerGoldenCorpusTest, TokenBiasPolicyTest | full suite |
| annotator | LiveAstAnnotatorTest + the 6 P3 classes (cue-locked) | full suite |
| entities/migrations/container | AuthrContainerRoundTripTest | full suite |
| ViewModels | SerialDashboardTest (pattern) | full suite |
| workers | engine classes behind them + compile | full suite + manual chain review |
| `engine/audio/*` | the 5 P4 classes + `AudiobookRendererTest` (integration auto-skips without espeak/ffmpeg) | full suite, then `scripts/p4_gate.sh` (10-chapter gate render) |
| `engine/publishing/*` or `container/AuthrContainer.kt` | the 6 P5 classes + `AuthrContainerRoundTripTest` | full suite, then `scripts/p5_gate.sh` (two projects → retail packages + real epubcheck + all four ladder bands) |

## How to verify a change safely

1. `./gradlew :desktop:compileKotlin :desktop:compileTestKotlin` — fast signal.
2. `./gradlew :desktop:test` — 221/221 required. A red exit-gate test means the CODE moved; re-read `docs/change-impact.md` before touching either side of a cue-locked corpus.
3. In CI-less sandboxes where Gradle dependency resolution is blocked, the P4+P5 suites run without Gradle: `scripts/p4_direct_test.sh` and `scripts/p5_direct_test.sh` (direct kotlinc + JUnitCore; jars via `scripts/p4_fetch_toolchain.sh` / `scripts/p5_fetch_toolchain.sh` into `.build-extras/`; P5 adds the kotlinx-serialization compiler plugin jar for the container/workbench code).
4. The annotator/print latency slice (D-08/D-16) has its own route: `scripts/p6_direct_test.sh` compiles the modified engine files + `AnnotatorPerfTest`, the annotator/cliché/token-bias tests and `PrintInteriorTest`; `scripts/p6_probe.sh` re-runs the D-08 latency probe (`scripts/latency_probe.kt`) — pass `EXTRA_CP=$HOME/../my-project/.build-extras/p6-classes` to measure the fixed classes against the stock build. The toolchain in `.build-extras/` is NOT in git (restorable via the fetch scripts — P6 workspace hygiene).
5. For serialization changes, re-run the corpus probe (`scripts/CorpusProbe.java` via jshell) and eyeball the suite verdict line (`passed=true exitGateGreen=true warnings=0`).
6. For UI-affecting changes, run the Xvfb smoke (`scripts/p3_smoke.sh`) on a workstation and look at the screenshots in `/tmp/render-p3/`.
7. For audiobook changes, re-run the ten-chapter gate (`scripts/p4_gate.sh`) and check the five gate lines plus the per-chapter ACX tables in the output (evidence tree lands in `download/Authr-OS-3.0-P4-Audiobook-Gate/`).
8. For publishing changes, re-run the retail gate (`scripts/p5_gate.sh`) and check the four gate lines plus the band-probe lines (evidence tree lands in `download/Authr-OS-3.0-P5-Retail-Package/`). epubcheck must be present (the fetch script puts the dist under `.build-extras/epubcheck-dist/`).
9. If you added a planted corpus sentence, add or update the matching planted-cue assertion in the same commit (ADR-0004/0007).

## Adding tests (house rules)

- Fakes live inline in each test class (no shared fixture module yet — acceptable duplication, keeps classes self-contained).
- Test names are the spec: backticked sentences quoting the plan's acceptance language (e.g. `` `the 15-chapter golden serial passes the full validator suite with at most two dismissable warnings` ``).
- Deterministic only: no time-sensitive logic (`java.time.LocalDate.parse` over `now` in fixtures; `GoldenSerial.releaseDate` derives from a fixed 2025-01-06 anchor).
- Coroutines: `runTest` + injected `Dispatchers.Unconfined` scopes for VMs; no `Thread.sleep`.
