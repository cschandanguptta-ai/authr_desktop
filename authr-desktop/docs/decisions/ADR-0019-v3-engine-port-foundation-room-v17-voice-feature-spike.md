# ADR-0019: The v3.0 Engine Port Foundation — Room v17 Engine Tables and the Deterministic Voice Feature Spike

**Status:** Accepted (2026-11-14)
**Context:** The v3.0 specification set (master architecture, engine enhancements, schema v2.2, roadmap) defines six enhancement tracks — cumulative voice drift, hierarchical context with token budgets, expanded prose feature extraction, research vault, compliance gate, dialogue sub-engine — for the Python/Node lineage. The desktop port shares the doctrine but has none of the engine internals: nothing measures voice fidelity (TextMetrics stops at word/sentence/syllable counts), nothing manages context economics, nothing verifies research facts, nothing gates publication on compliance. The cross-check analysis (download/authr-os-v3-spec-crosscheck-analysis.docx) recommended porting them in five sprints; this is Sprint 0.

## Decision

1. **Room v17, additive only** (`MIGRATION_16_17` in `AppDatabase.kt`): five new tables adapted from spec schema v2.2 — `voice_drift_log` (per-chapter drift measurements with cumulative EMA), `summary_cache` (4-level hierarchical summaries keyed by SHA-256 source hash), `research_sources` + `research_facts` (the research vault with source→fact cascade), `compliance_checks` (pre-publication gate results). No column changes to any v16 table. Desktop adaptations: epoch-millis timestamps (codebase convention) instead of `datetime('now')`; CHECK constraints enforced at the Kotlin API surface, not in DDL.

2. **Voice evolution and token spend ride editorial-memory slots** (`voice_evolution`, `token_spend_ledger`): low-volume, per-book, JSON-shaped payloads — the ADR-0018 slot pattern absorbs both without new tables. Slots append to `ALL_SLOTS` (never reorder).

3. **A deterministic 24-feature voice extractor** (`engine/voice/VoiceFeatureExtractor.kt`): pure JVM regex + token statistics, no spaCy, no LLM, no network. Structural (sentence length mean/SD, paragraph length, opener entropy, consecutive openers, punctuation rhythm, contraction ratio), lexical (windowed TTR, hapax ratio, average word length, adverb/passive/filter-word rates, figurative density, sensory frequency per sense), and composition (dialogue ratio) features — all normalized per-1k words so chapters of different lengths compare directly. spaCy-derived features (clause depth, narrative distance, show/tell) are deferred to LLM-assisted passes in later sprints.

4. **The drift math lands with the schema** (`engine/voice/VoiceDriftTracker.kt`): per-feature drift = |current − baseline| / max(baseline, ε), weighted composite (burstiness and sentence-length carry the heaviest weights), cumulative EMA with α = 0.1, and the spec flag rule — composite > 0.30 or cumulative > 0.20 — with voice-evolution suppression (the alert quiets, the measurement is still recorded; writer sovereignty per ADR-0001).

## Consequences

- `AppDatabase` version 16→17; `MigrationFailFastTest` updated: the "current schema" contract now derives the target version from the Impl on the classpath (the no-Gradle direct route runs against the prebuilt v16 Impl until KSP regenerates it via Gradle), and a new test drives the real `MIGRATION_16_17` over a raw KMP connection, proving v16 rows survive and the five tables appear with the exact entity-declared columns.
- **Spike measured**: 24 features × 15 golden-serial chapters at **3.7 ms/chapter mean** (budget: 50 ms — 13× under). Vectors are deterministic (same input → identical output, the precondition for drift comparison).
- The `s0_direct_test.sh` route (12 tests) joins the CI direct suite: migration data preservation, feature sanity across the golden envelope, timing budget, planted voice-collapse flagging, evolution suppression, rolling baseline.
- **Follow-up (Gradle route)**: `./gradlew :desktop:test` must regenerate `AppDatabase_Impl` at v17 via KSP; until Maven Central's 429 rate-limit clears, the direct route is authoritative for the new slices.
- Sprint 1 (ADR-0020, voice engine wiring) and Sprint 2 (ADR-0021, context orchestrator) build directly on these tables and this extractor.

## Alternatives rejected

- *One migration per sprint* — rejected; all five tables are settled spec DDL. Landing them together means S1–S3 wire logic onto a stable schema with zero further migrations (the roadmap's own "schema first, then code").
- *A dedicated `voice_evolution` table* — rejected for now; the slot DTO pattern is cheaper and additive. Promote to a table if range queries become needed.
- *spaCy-equivalent NLP on the JVM* — rejected; the deterministic core is what the drift math needs, and anything heavier violates the 50 ms budget and ADR-0004's local-only guardrail doctrine.

## References

`data/model/VoiceDriftLog.kt`, `data/model/SummaryCache.kt`, `data/model/ResearchVault.kt`, `data/model/ComplianceCheck.kt`, `data/model/VoiceEvolutionEntry.kt`, `data/local/VoiceEngineDaos.kt`, `data/local/ResearchComplianceDaos.kt`, `AppDatabase` (MIGRATION_16_17), `engine/voice/VoiceFeatureExtractor.kt`, `engine/voice/VoiceDriftTracker.kt`, `scripts/s0_direct_test.sh`, spec `upload/authr-os-v3-schema-v2.2.md`, `upload/authr-os-v3-engine-enhancements.md`
