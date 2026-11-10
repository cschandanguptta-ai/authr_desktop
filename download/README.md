# Deliverables

## Authr OS 3.0 — Improvement Plan (Rev 2)
- Authr-OS-3.0-Improvement-Plan-Rev2.docx — the v3.0 improvement plan, Revision 2 (42 pages, 35 content tables, 2 charts)
- Authr-OS-3.0-Improvement-Plan-Rev2.pdf — PDF render of the same document

## Authr OS 3.0 — P0 Implementation Report (November 9, 2026)
- Authr-OS-3.0-P0-Implementation-Report.docx — phase P0 deliverable report: scope executed, exit gates + evidence, build-repair log, test results, P1 handoff
- Authr-OS-3.0-P0-Implementation-Report.pdf — PDF render of the same document

P0 exit-gate status (all green, see the report for evidence):
1. v2.3 project round-trips through the .authr container losslessly — 5/5 container tests green
2. Zero video / Kitten TTS references in the codebase — repository grep count 0
3. Pocket TTS synthesizes a 10,010-word chapter on CPU at 7.3x real time (gate >= 2x)

## Authr OS 3.0 — P1 Implementation Report (November 9, 2026)
- Authr-OS-3.0-P1-Implementation-Report.docx — phase P1 deliverable report: Writer Engine Core — six-layer contract, Writer Studio, Live AST Annotator, PROPOSED/CONFIRMED/ARCHIVED flow, canon-gated pipeline
- Authr-OS-3.0-P1-Implementation-Report.pdf — PDF render of the same document

P1 exit-gate status (all green, see the report for evidence):
1. A five-chapter novella runs end to end with the confirm flow — WriterStudioFlowTest 3/3 green
2. No unconfirmed text reaches any ledger or export — zero occurrences across
   chekhov_ledger / timeline / epub_export / manuscript_compile (seeded-project assertion suite)

Implementation lives in authr-desktop/ — 34 unit tests green (14 P0 + 20 P1), 188 MB distributable
app image rebuilds and launches. New in P1: engine/ package (ManuscriptAst, LiveAstAnnotator,
SixLayerPipeline, DraftArchive, L4LlmDraftGenerator), WriterStudioViewModel + WriterStudioScreen
(Interactive + Autonomous modes, keyboard-first review queue), global workspace bar; the drafting,
polishing, compilation and EPUB workers are gated to canon chapters. Spike/benchmark scripts live
in scripts/.

## Authr OS 3.0 — P2 Implementation Report (November 9, 2026)
- Authr-OS-3.0-P2-Implementation-Report.docx — phase P2 deliverable report: Prose Quality Systems —
  cliché cleaner (154 patterns, 6 classes), provider token-bias matrix, Chekhov ledger with
  final-act recovery gate, question ledger foundations
- Authr-OS-3.0-P2-Implementation-Report.pdf — PDF render of the same document

P2 exit-gate status (all green, see the report for evidence):
1. Precision ≥ 90% on the 50-page golden corpus — measured 1.0000 (154 true positives,
   0 false positives across a 23,210-word corpus; matrix parity with plan Table 6 pinned by tests)
2. Planted guns in the regression corpus all detected — six-chapter seeded serial walked through
   the confirm flow: escalation on re-emphasis, distinct vows stay distinct, canon-only provenance
3. Zero false compile blocks — empty / resolved / resolved-plus-waived ledgers compile clean;
   only genuinely unfired guns block, waivers always disclosed

Implementation lives in authr-desktop/ — 63 unit tests green (34 P0/P1 unchanged + 29 P2), the
188 MB distributable app rebuilds and the smoke run survives the 40-second window. New in
P2: engine/ClicheCleaner + TokenBiasPolicy + ChekhovLedger + QuestionLedger, the Studio Continuity
Ledger panel (guns with lifecycle actions, questions with due/overdue chips, compile-gate chip),
prompt-level cliché constraints on the L4 route, logit_bias plumbing on the OpenAI-compatible
family, and the final-act recovery gate inside CompilationWorker.

## Authr OS 3.0 — P3 Implementation Report (November 9, 2026)
- Authr-OS-3.0-P3-Implementation-Report.docx — phase P3 deliverable report: Serialization —
  opening validators, seven-beat coverage and balance meter, ending advisor, micro-arc planner,
  worksheet, 14-point scorecard, buffer and cadence, platform export profiles, both modes
- Authr-OS-3.0-P3-Implementation-Report.pdf — PDF render of the same document

P3 exit-gate status (all green, see the report for evidence):
1. A 15-chapter golden serial passes the full validator suite — 0 warnings, 0 hard blocks
   (79 passing checks); at most two dismissable warnings allowed
2. The anti-withholding gate fires exactly on the seeded withheld-payoff project
   (free-tier-bound; both payoff and consequential-seed halves required)
3. Scorecard gates behave per the interpretation bands — all four bands, zero-triggers
   always block, note-only overrides disclosed

Implementation lives in authr-desktop/ — 130 unit tests green (63 P0/P1/P2 unchanged + 67 P3),
the 188 MB distributable app image rebuilds and launches (smoke window survived). New in P3:
engine/serialization (OpeningValidators + anti-withholding gate, BalanceMeter, EndingAdvisor,
MicroArcPlanner, PreDraftWorksheet, QcScorecard, BufferCadence, SerialExportProfiles,
SerialValidatorSuite, GoldenSerial), the Serial Dashboard workspace (buffer, cadence, arc board,
suite report with dismissal, worksheet/scorecard editors, paste-ready exports), serialization
artifacts persisted at confirm time, the Blueprint 2.0 drafting block replacing the 1.0
cliffhanger score, the serial outline gate, and the serial export gates in CompilationWorker.

## Authr OS 3.0 — AI Maintenance Documentation (November 10, 2026)
- Authr-OS-AI-Maintenance-Docs/ — the "Document a Production App for Future AI Maintenance"
  package for the authr-desktop production app: repo-root AGENTS.md (doc index, hard rules,
  commands, workflow) + docs/ (system-map with Mermaid component diagram, change-impact blast
  radii, invariants-and-gotchas, troubleshooting symptom tables, data-model with ER diagram +
  migration rules, api-contracts, config-and-env, testing, tech-debt D-01..D-12,
  open-questions, and 9 ADRs under docs/decisions/)
- Authr-OS-AI-Maintenance-Docs.zip — the same package zipped

Every claim is labeled FACT / INFERRED / UNKNOWN with file citations. The package passed the
3-module random self-check (ChekhovGun.kt, QcScorecard.kt, EdgeTtsManager.kt): five real gaps
were found and patched into change-impact.md / troubleshooting.md / invariants-and-gotchas.md
(ledger status strings duplicated in Kotlin constants AND ChekhovGunDao raw SQL; scorecard
null-card -1/14 symptom; qcScores persisted-JSON-key rename trap; Edge TTS reverse-engineered
handshake fragility shared by three narration engines; stale Edge TTS chunk cache).
Suite status re-verified from the recorded Nov 9 results: 130/130 green, 0 failures — no Kotlin
source has been modified since. Canonical copies live in authr-desktop/ (AGENTS.md + docs/);
that copy is the one maintainers must keep current.
