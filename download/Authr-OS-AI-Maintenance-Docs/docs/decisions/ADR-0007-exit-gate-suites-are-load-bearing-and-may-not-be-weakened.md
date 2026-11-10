# ADR-0007 — Exit-gate suites are load-bearing and may not be weakened

**Status:** Accepted (project convention since P0)
**Context:** Each phase closes on a measurable exit gate (plan §13 Table 33); the temptation is to adjust tests to make a gate pass.
**Decision:** Phase exit-gate tests assert plan acceptance metrics exactly (P0 container round-trip; P1 no-unconfirmed-text; P2 precision/planted-guns/zero-false-blocks; P3 golden-serial suite ≤2 dismissable warnings, anti-withholding firing exactly, scorecard bands). If a gate fails, fix the code (or, for cue-locked corpora, move corpus and checker together); never weaken an assertion to pass.
**Alternatives considered:** soft assertions / TODO-skip (rejected — the gate is the deliverable).
**Consequences:** The suite count is a delivery metric (P3: 130/130); occasional real fixes (e.g., P3's `classifyEnding` return-shape bug) come out of it.
**Do not change unless:** the plan's gate definition itself changes (then update the test to the new plan text, citing it).
**Evidence:** test classes in `desktop/src/test/kotlin/com/example/`; P0–P3 reports' exit-gate tables.
