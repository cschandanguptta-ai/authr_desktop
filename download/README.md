# Deliverables

## Authr OS — Desktop App Release (November 12, 2026)
- authr-os-desktop-linux-x64-3.0.0.zip — the runnable Linux x64 desktop app
  (145 MB): self-contained jpackage image with bundled Java 21 runtime,
  epubcheck 5.2.1, print fonts, and the full v3.0 engine (Writer Studio,
  Serial Dashboard, audiobook engine, Publishing workbench). INSTALL.md is
  inside the zip. Verified: unzip integrity OK, launcher exec bits preserved,
  fresh-extraction launch under Xvfb stays alive with zero fatal lines.
- authr-os-desktop-linux-x64-3.0.0.zip.sha256 — release checksum
- authr-source-v2.3-original-upload.zip — the original v2.3 Android source
  project, exactly as uploaded (re-downloadable copy). Note: there was never
  a 2.3.0 *desktop* build — the desktop edition is new in 3.0; naming follows
  the platform-version pattern the release series uses.

Quick start: `unzip authr-os-desktop-linux-x64-3.0.0.zip -d ~/apps && ~/apps/AuthrOS/bin/AuthrOS`
(requires glibc >= 2.38 and a graphical session; no Java install needed).
The packaging script lives at `scripts/d16_package_zip.sh` (version read from
the image's own AuthrOS.cfg; rebuild is byte-size-stable).

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

## Authr OS 3.0 — P4 Implementation Report (November 11, 2026)
- Authr-OS-3.0-P4-Implementation-Report.docx — phase P4 deliverable report: Audiobook Engine —
  IPA pronunciation pass, continuity contract, three-layer mastering chain, ACX compliance
  audit, SMIL read-along export, the ten-chapter exit-gate render, docs/ADR updates
- Authr-OS-3.0-P4-Implementation-Report.pdf — PDF render of the same document
- Authr-OS-3.0-P4-Audiobook-Gate/ — the exit-gate evidence tree: 10 mastered chapter MP3s +
  whole master (7.9 min, MP3 CBR 192 kbps / 44.1 kHz), 10 SMIL overlays + paired XHTML content
  documents, pronunciation-rules.json (13 approved rules), gate-report.md (per-chapter ACX
  tables with measured values)
- Authr-OS-3.0-P4-Audiobook-Gate.gate-run.log — the full gate run log

P4 exit-gate status (all green, plan §13 Table 33, see the report for evidence):
1. A ten-chapter audiobook renders and passes the ACX audit all-green — 10/10 chapters + the
   joined master, every criterion green (RMS −18.9…−19.6 dB in the −23…−18 window, true peaks
   −3.3/−3.4 dBFS ≤ −3, noise floors ≤ −191 dB ≤ −60, 44.1 kHz, MP3 CBR 192 kbps)
2. Synthesis ≥ 2× real time — 587.6× measured on the gate render through the bundled espeak-ng
   route (Pocket TTS production engine: 7.3× from the P0 spike on the reference machine)
3. SMIL timing sanity — 10/10 chapters: measured per-fragment clip times, contiguous coverage,
   last clipEnd equals chapter duration

Implementation lives in authr-desktop/ — 159 unit tests green (130 P0–P3 unchanged + 29 new P4,
run via the direct kotlinc/JUnitCore route after Maven Central rate-limiting blocked Gradle).
New in P4: engine/audio package (PronunciationPass, MasteringChain, AcxAudit, SmilExporter,
AudiobookRenderer + EspeakSynthesizer), desktopbridge/EspeakLocator.kt, gate + toolchain
scripts, ADR-0010, tech-debt D-13/D-14, open questions OQ-14.

## Authr OS 3.0 — P5 Implementation Report (November 11, 2026)
- Authr-OS-3.0-P5-Implementation-Report.docx — phase P5 deliverable report: Publishing —
  EPUB 3 exporter, print interior with gutter ladder, metadata workbench, pricing
  simulator, volume bundling, ship report, one-action retail packager, docs/ADR updates,
  and the program-completion P0–P5 summary
- Authr-OS-3.0-P5-Implementation-Report.pdf — PDF render of the same document
- Authr-OS-3.0-P5-Retail-Package/ — the exit-gate evidence tree: novel/ and serial/
  retail packages (EPUBs, print/interior.pdf, metadata.json, ship-report.md each, plus
  the source .authr containers), probes/ (the four gutter-ladder band interiors),
  gate-report.md
- Authr-OS-3.0-P5-Retail-Package.gate-run.log — the full gate run log (suite + gate)

P5 exit-gate status (all green, plan §13 Table 33, see the report for evidence):
1. epubcheck zero errors — the real epubcheck 5.2.1 tool on the novel EPUB and both
   serial volume EPUBs (0 fatals / 0 errors / 0 warnings each); a deliberately broken
   EPUB confirmed failures block the clean verdict
2. KDP Print Previewer contract — 6×9 in trim on every page; gutter ladder verified on
   all four bands (85/206/389/571-page probes → 0.375/0.5/0.625/0.75 in); the Previewer
   run itself is an upload-time, Amazon-side step (OQ-15)
3. One action — both projects round-tripped through real .authr containers, then one
   RetailPackager call each produced the full retail package; ship reports CLEAN with
   disclosures (canon exclusions, workbench derivation, Chekhov waivers, pricing record)

Implementation lives in authr-desktop/ — 209 unit tests green (159 P0–P4 unchanged +
50 new P5, run via the direct kotlinc/JUnitCore route `scripts/p5_direct_test.sh`;
epubcheck + pdfbox jars via `scripts/p5_fetch_toolchain.sh`). New in P5:
engine/publishing (PublishingMetadata + RoyaltySimulator + WorkbenchStore, Epub3Exporter,
PrintInterior, VolumeBundler, ShipReport, RetailPackager), desktopbridge/EpubCheckLocator.kt,
structured KdpWorkbench in the .authr container (additive; legacy stub still readable),
ArcPayload.id for round-trip-safe arc joins, gate + toolchain scripts, ADR-0011,
tech-debt D-15/D-16, open questions OQ-9 (resolved) / OQ-15 / OQ-16.

PROGRAM STATUS: all six plan phases (P0–P5) are implemented with green exit gates.

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
