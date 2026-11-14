# ADR-0018 Delivery Report — Commercial Authoring Pipeline
Commit `ac0f73e` on `main` · All 298 direct-route CI tests green · Gradle `:desktop:test` green

## What was built (per the 7 execution priorities)

### Priority 1 — Audit (done first)
Full audit of the 214-file codebase: engine layer, data layer (Room v15, 11 entities), container, workers, ViewModels, 17 ADRs, 280-test CI suite. Baseline verified green before any change. Gap list: no market research, no concept/positioning, no editorial passes, no engagement analytics, no retail QA, no persistent editorial memory, no cross-stage gates.

### Priority 2 — The 9-stage pipeline engine (`engine/pipeline/`)
1. **Market & Audience Research** — LLM stage, strict-JSON findings (expectations, demographics, comps, gaps, opportunities)
2. **Concept Development** — 3 positioned concepts w/ premise, differentiator, selling proposition, promise, avoid-list
3. **Story Architecture** — logline, acts, character arcs, chapter plan, continuity seeds → written to `Book.outline`
4. **Worldbuilding** — story/character/world bibles + relationships + timeline + narrative rules → `Book` columns
5. **Manuscript** — reuses the existing writer-gated L4 flow unchanged (no duplication)
6. **Editorial Intelligence** — five passes: developmental / structural / line / copyedit / proofread; findings persisted as issues
7. **Reader Engagement** — LOCAL deterministic analyzer (no LLM)
8. **Quality Assurance** — LOCAL deterministic QA suite (no LLM)
9. **Publication Preparation** — titles, blurbs, 7 keywords, 3 categories, promo materials → `publication_package` slot

### Priority 3 — Persistent editorial intelligence layer
New `editorial_memory` table (Room **v16**, additive migration): per-book slots for market findings, positioning, personas, decisions, issues, versions, stage states, engagement analytics, publication package. Every stage assembles context FROM this store — stages cannot contradict each other. `@Serializable` DTOs; payload drift fails loud.

### Priority 4 — Reader retention + quality gates
- `RetentionAnalyzer`: per-chapter hook / pacing / tension / curiosity / ending scores + composite retention index; final-chapter quiet-ending exemption; ranked recommendations. Honesty contract: editorial measurement of craft signals, never claims of measured reader behavior.
- `ManuscriptQaSuite`: Chekhov orphans, cross-chapter 5-gram repetition, POV/echo continuity (reuses the annotator — ADR-0006), cliché density caps, ending fatigue.
- `StageGates`: per-stage deterministic criteria (retention floor 55, weak-hook cap 2, QA hard-zero, KDP package shape). **Weak content is never auto-advanced**; `approveStage` is the only path to APPROVED and refuses red gates (writer sovereignty, ADR-0001).

### Priority 5 — UI
Pipeline Workbench (tab 10, "Pipeline" in the Create rail): stage tracker with statuses + gate findings, run/approve buttons, editorial memory panel (decisions / issues with resolve / versions), retention analytics panel, gate verdict card.

### Priority 6 — End-to-end verification
- `PipelineEngineTest` (18 tests): memory round-trips, fail-loud drift, gate blocking per stage, planted-corpus retention measurement, QA defect detection (orphans, repetition, healthy-canon green), version/engagement persistence.
- New CI route `scripts/adr0018_direct_test.sh` wired into `ci_direct_suite.sh` → **298 tests, all green**.
- Gradle `:desktop:test` green; `createDistributable` rebuilt with the new code.

## Key invariants preserved
LLM output always PROPOSED until writer approves; gates are local code, never LLM (ADR-0004); DB migration additive-only (ADR-0002); canon-only consumption (ADR-0001); existing 255 tests untouched except `MigrationFailFastTest` (v15→v16 — its contract is "current schema").

## Not finished (follow-ups, honestly)
1. Pixel walkthrough of the Pipeline screen under a real display (sandbox has none; smoke-compiled only).
2. `publication_package` slot → `KdpWorkbench` seeding (Publishing tab still reads the workbench file).
3. Version-list rollback UI renders but restoring a snapshot needs a confirm flow.
4. D-04 god-class split (`StudioViewModel`/`StoryScreen`) still deferred by decision.
5. CI will also run on GitHub Actions on push (installers.yml builds MSI+APKs).

## Files
- Engine: `desktop/src/main/kotlin/com/example/engine/pipeline/` (7 files)
- Data: `data/model/EditorialMemory.kt`, `data/local/EditorialMemoryDao.kt`, `AppDatabase.kt` (v16), `AppRepository.kt`
- UI: `ui/screens/PipelineWorkbenchScreen.kt`, `ui/viewmodel/PipelineViewModel.kt`, `Main.kt`
- Tests: `desktop/src/test/kotlin/com/example/PipelineEngineTest.kt`
- Scripts: `scripts/adr0018_direct_test.sh`, `scripts/adr0018_compile.sh` (+ CI suite updated)
- Docs: `docs/decisions/ADR-0018-*.md`, system-map, data-model, change-impact, invariants, tech-debt, AGENTS.md
