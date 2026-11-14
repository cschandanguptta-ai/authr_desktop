# ADR-0018: The Commercial Authoring Pipeline and Editorial Intelligence Layer

**Status:** Accepted (2026-11-14)
**Context:** v3.1 has a strong *manuscript-side* engine (writer sovereignty, continuity ledgers, serial validators, audiobook, retail packaging) but no *book-side* pipeline: nothing researches the market before writing, develops a positioned concept, runs editorial passes, measures reader-retention signals, or QA-audits the full manuscript. Quality control exists only for the serial format; a retail novel reaches Publishing with no gate between "chapters confirmed" and "package for stores."

## Decision

1. **A nine-stage commercial pipeline** (`engine/pipeline/PipelineStages.kt`): Market Research → Concept → Story Architecture → Worldbuilding → Manuscript → Editorial → Engagement → QA → Publication Prep. Stages 5 reuses the existing writer-gated flow (no duplication); stages 7 and 8 are **local deterministic analyzers**, not LLM calls.

2. **A persistent editorial intelligence layer** (`editorial_memory` table, schema v16): per-book slots for market findings, positioning, reader personas, decisions, issues, versions, stage states, engagement analytics, and the publication package. Every stage assembles its context FROM this store (`StageContext`), so two stages cannot silently contradict each other. Payloads are `@Serializable` DTOs; drift fails loud.

3. **Deterministic stage gates** (`StageGates.kt`): each stage has measurable criteria (findings completeness, differentiator presence, bible depth, canon existence, retention floor ≥55, weak-hook cap ≤2, QA hard-finding zero, KDP package shape). A red gate sets the stage BLOCKED; **weak content is never auto-advanced**. `approveStage` is the only path to APPROVED and refuses while the gate is red — the writer checkpoint, aligned with ADR-0001.

4. **Retention analytics with an honesty contract** (`RetentionAnalyzer.kt`): per-chapter hook / pacing / tension / curiosity / ending scores and a composite retention index, all local code (ADR-0004). The index is an *editorial measurement of craft signals* — the same disclaimer QcScorecard carries — never a claim of measured reader behavior.

5. **Manuscript QA suite** (`ManuscriptQaSuite.kt`): Chekhov orphans (reusing ledger semantics), cross-chapter 5-gram repetition, POV/echo continuity flags (reusing the annotator, ADR-0006 single source of truth), cliché density caps, ending fatigue. HARD findings gate stage 8; an optional LLM audit runs *beside* it as a writer-visible proposal only.

6. **LLM stages return STRICT JSON** parsed defensively (fail-soft to raw text for the UI). All stage output is PROPOSED until approved.

## Consequences

- Room schema v15→v16, additive only (one new table + unique index per book+slot). `AppDatabase` version bumped; `MigrationFailFastTest` updated to target v16 (its contract is "current schema", not "15").
- The Pipeline Workbench (tab 10, "Pipeline" in the Create rail section) renders the stage tracker, gate verdicts, the editorial memory (decisions / issues / versions), and retention analytics.
- `AppRepository` takes an optional `editorialMemoryDao` (nullable keeps JVM tests Room-free).
- The direct CI suite gains the `adr0018_direct_test.sh` route (18 tests): memory round-trips, gate blocking, planted-corpus analyzer behavior, QA defect detection.
- Invariant additions: stage outputs never bypass gates; the memory store never calls an LLM; analyzers stay local.

## Alternatives rejected

- *LLM-as-judge gates* — violates ADR-0004 (guardrails must be deterministic local code) and makes advancement nondeterministic.
- *Auto-advance on green gates* — rejected; the writer approval checkpoint is a hard requirement (writer sovereignty).
- *New per-stage tables* — rejected; the slot-per-payload design keeps migrations additive and cheap.

## References

`engine/pipeline/*`, `data/model/EditorialMemory.kt`, `data/local/EditorialMemoryDao.kt`, `AppDatabase` (MIGRATION_15_16), `ui/screens/PipelineWorkbenchScreen.kt`, `ui/viewmodel/PipelineViewModel.kt`, `Main.kt` (tab 10), `scripts/adr0018_direct_test.sh`
