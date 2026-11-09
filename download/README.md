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
