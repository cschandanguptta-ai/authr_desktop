# ADR-0004 — Deterministic guardrails around the stochastic engine, proven by golden corpora

**Status:** Accepted (P2, reused as the P3 pattern)
**Context:** LLM output quality cannot be asserted directly; the plan requires auditable, versioned, local checkers (Principle 4) with measurable precision.
**Decision:** All quality rules are deterministic local code: the 154-pattern cliché matrix (`engine/ClicheCleaner.kt`), the ledger engines, and the serialization validators. Each is pinned by a deterministic planted corpus — the 50-page cliché golden corpus (precision 1.0000 asserted), the six-chapter Chekhov regression serial, and the P3 15-chapter golden serial — where the texts are written to exactly match the validators' cue regexes.
**Alternatives considered:** LLM-as-judge (rejected — non-deterministic, unauditable); no gating (rejected — plan requirement).
**Consequences:** The corpora are cue-locked: editing a regex or a planted sentence can flip classifications; both sides must move together (this caused real test churn during P3 — see invariants-and-gotchas "Past bugs"). Precision/recall assertions are load-bearing tests.
**Do not change unless:** the plan's blueprint revision changes; corpus and checker move in the same commit, never separately.
**Evidence:** `engine/serialization/GoldenSerial.kt` (FILLER comment), `ClicheCleanerGoldenCorpusTest`, `ChekhovLedgerTest`, `SerializationGoldenSerialTest`.
