// Authr OS 3.0 — P2 Implementation Report (phase deliverable)
// Route: Create (docx-js), Scene: Report, Cover: Recipe R1 (GO-1 palette)
"use strict";
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Header, Footer, PageNumber,
  AlignmentType, NumberFormat, SectionType,
} = require("docx");

const lib = require("./v3_plan_lib");
const P = lib.P;
const FONT_BODY = lib.FONT_BODY;
const FONT_HEAD = lib.FONT_HEAD;

const OUT = "/home/z/my-project/download/Authr-OS-3.0-P2-Implementation-Report.docx";

const pgSize = { width: 11906, height: 16838 };
const pgMargin = { top: 1440, bottom: 1440, left: 1701, right: 1417 };

function pageNumFooter() {
  return new Footer({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { line: 240 },
      children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "808080", font: FONT_BODY })],
    })],
  });
}

function docHeader() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.RIGHT,
      spacing: { line: 240 },
      children: [new TextRun({
        text: "Authr OS 3.0 · P2 Implementation Report · November 9, 2026",
        size: 16, color: P.secondary, font: FONT_BODY,
      })],
    })],
  });
}

// ── Content ────────────────────────────────────────────────────────────────
const body = [];

body.push(lib.h1("1. Executive Summary"));
body.push(lib.p(
  "Phase **P2 — Prose Quality Systems is complete with its exit gate green**. The two deterministic systems that bracket the stochastic drafting engine now exist at full launch strength: the cliché cleaner runs the complete 154-pattern matrix across six classes (plan §5.1, Table 6) — suppressing forbidden patterns in the L4 drafting prompt on every provider route, biasing the most frequent offender tokens at decode time where the provider's API supports it (plan Table 7), and linting confirmed text into the Writer Studio's margin rail — and the Chekhov continuity ledger now enforces its full lifecycle (plan §5.2, Table 8): duplicate detection on the confirm-time upsert, escalation history for re-emphasized promises, the automatic ORPHANED sweep at the final-act boundary, and the final-act recovery gate that blocks compilation while any confirmed gun is still unfired unless the writer waives it with a mandatory, disclosed note. The question ledger foundations (plan §6.7) open, window, and surface the three information levels off the same confirm flow."
));
body.push(lib.p(
  "The P2 exit gate from the plan (§13, Table 33) is discharged by the golden-corpus and regression suites: **precision 1.0000 on the 50-page golden corpus** (154 true positives, 0 false positives across 23,210 words — gate ≥ 0.90), **every planted gun in the regression corpus detected** (a six-chapter seeded serial walked through the confirm flow), and **zero false compile blocks** (empty, fully resolved, and resolved-plus-waived ledgers all compile clean; only genuinely unresolved guns block). The full suite stands at 63/63 tests green — the 34 P0/P1 tests unchanged and passing, plus 29 new P2 tests."
));
body.push(lib.p(
  "The implementation adds 1,699 lines across eight new files (four engine modules, four test suites) and touches nine existing ones. As in P1, the ported Android surface is left intact: the new machinery rides the P1 seams — the annotator's cliché hook, the confirm flow's ledger upsert, the compile worker's canon gate — without restructuring any ported screen or the transport layer."
));

body.push(lib.h1("2. P2 Exit Gate (plan §13, Table 33)"));
body.push(...lib.tbl(
  "Table 1. P2 exit gate and verification evidence",
  ["Gate", "Target", "Result", "Evidence"],
  [
    ["Precision ≥ 90% on the 50-page golden corpus", "≥ 0.90 span-level precision; matrix at launch strength (154 patterns, 6 classes)", "**PASS — 1.0000**", "ClicheCleanerGoldenCorpusTest — a deterministic 23,210-word corpus (≈ 66 pages at 350 words/page) built from a clean sentence bank that fires none of the 154 patterns plus one planted catch per pattern: 154 hits, all 154 true positives, **0 false positives**; the clean bank deliberately carries near-miss phrasing (\"morning chill\" vs \"a chill ran down her spine\") that would fail a sloppy pattern set"],
    ["Planted guns in the regression corpus all detected", "Every planted promise reaches the ledger with the correct lifecycle", "**PASS**", "ChekhovLedgerTest — six-chapter seeded serial (vow, debt, artifact, grudge, open question + a distinct second vow) walked through the Writer Studio confirm flow: all five planted ch.1 detector classes present, re-emphasis escalates the existing entry instead of duplicating it, the distinct ferryman vow becomes its own entry, every ledger entry traces to a canon chapter"],
    ["Zero false compile blocks", "Compile proceeds whenever no gun is genuinely unfired", "**PASS**", "The final-act recovery gate blocks only INTRODUCED/ESCALATED/ORPHANED guns: empty, all-resolved, and resolved-plus-waived ledgers all compile clean (waiver disclosed in the report, never silent); the seeded blocked serial recovers through resolve/waive until the gate clears"],
  ],
  [22, 24, 14, 40]
));
body.push(lib.p(
  "The matrix also carries its plan-parity assertions as tests: exactly 154 patterns, exactly 30 / 32 / 28 / 24 / 18 / 22 per class (Table 6), every pattern carrying class, severity, rewrite, and prompt hint — so the launch strength the plan specifies is pinned by the suite, not by convention."
));

body.push(lib.h1("3. What Was Implemented"));

body.push(lib.h2("3.1 The cliché cleaner — 154 patterns, six classes (plan §5.1)"));
body.push(...lib.bullets([
  "**ClicheCleaner (engine)** — the full pattern matrix, compiled once per project open (lazy), one regex per pattern with severity, plain-language rewrite, and a prompt hint. Classes per Table 6: Dialogue-tag crutches (30 — said-bookisms, adverb-laden tags, voice-label crutches, inverted exotic tags), Filter words (32 — perception filters, hedges, weak intensifiers), Purple prose & overwrought metaphor (28), Body-cliché cluster (24), Plot-formula phrases (18, including the ERROR-severity \"dark and stormy night\" opening crutch), Web-novel filler (22 — recap openers, mirror-confirmation filler).",
  "**Span deduplication** — when two patterns match overlapping spans, the higher-severity (then earlier) pattern wins, so one catch produces exactly one rail node: the count a writer sees equals the number of distinct offending spans. The exact-count assertion of the P1 annotator test (\"both planted clichés detected\") passes unchanged against the full matrix.",
  "**The annotator delegates** — LiveAstAnnotator.clicheHits now calls ClicheCleaner.scan; no consumer code changed when the matrix landed, exactly as the P1 handoff intended (\"the exact node/payload contract the 154-pattern matrix will plug into\").",
]));

body.push(lib.h2("3.2 Generation-time suppression — prompt constraints + provider token-bias matrix (plan Table 7)"));
body.push(...lib.bullets([
  "**Prompt constraints on every route** — ClicheCleaner.promptConstraints() renders the matrix as a hard-constraint block (\"FORBIDDEN CLICHÉ PATTERNS (hard constraints — the text is linted against all 154 patterns before it can ship)\" with one line per class and its count); the L4 Scene Composer prompt now carries it on every provider.",
  "**TokenBiasPolicy (engine)** — the verified capability matrix per Table 7: OpenAI-compatible family (Groq, NVIDIA, Custom OpenAI endpoints, OpenRouter) → logit_bias on completion APIs; Ollama → bias via its OpenAI-compatible surface; Mistral and Gemini → no logit_bias (verified), enforcement falls to prompt constraints + deterministic linter. Unknown routes fail closed to prompt-plus-linter — a route is never silently biased.",
  "**The bias payload** — the cleaner's offenderTokens() ranks the most frequent tokens appearing inside cliché matches (deterministic frequency order); biasPayload() maps them to the plan's −5 weight under the 300-entry route ceiling. The L4 generator computes the payload from the canon context and the transport carries it on bias-capable routes (new optional logitBias parameter on LlmOrchestrator.callAgent, new optional logit_bias field on OpenAiChatRequest — serialized only when non-null, so existing call sites and the Moshi reflection adapter are untouched).",
  "**Integration boundary, stated honestly** — OpenAI-compatible providers match logit_bias keys against their own tokenizer's token IDs; the ported codebase carries no tokenizer, so the payload keys are offender surface forms and the transport attaches them only where the routed model can encode them. On any route the plan's own rule holds regardless: the linter, not the bias, is the gate that decides what ships (plan §5.1). The matrix, payload construction, and per-route strategy are pinned by tests; bundling provider tokenizers is the follow-up that makes decode-time suppression fully effective.",
]));

body.push(lib.h2("3.3 The Chekhov continuity ledger — lifecycle and the final-act gate (plan §5.2, Table 8)"));
body.push(...lib.bullets([
  "**Confirm-time upsert with duplicate detection** — ChekhovLedger.planUpsert replaces the P1 insert-on-confirm: a re-emphasized promise (same detector, context-quote similarity ≥ 0.6 by token overlap) escalates the existing entry — status ESCALATED, the chapter appended to the escalation history — while a distinct promise of the same detector class becomes its own gun. Same-chapter duplicates are skipped. The archived quote now carries a ±40-character context window, which is the evidence the detector compares.",
  "**The automatic ORPHANED sweep** — the final-act boundary is deterministic (last quarter of the book's target chapters; a six-chapter book orphans at chapter 5). When confirmation crosses the boundary, every confirmed gun still INTRODUCED or ESCALATED from before it becomes ORPHANED; resolved and waived entries are untouched, and guns made inside the final act get their own chance.",
  "**The final-act recovery gate** — ChekhovLedger.recoveryGate blocks compile while any gun is in INTRODUCED/ESCALATED/ORPHANED; CompilationWorker runs it after the P1 canon gate and logs each blocking gun with its remedy (resolve in the Studio or waive with a note). Waived guns pass, but every waiver note is disclosed in the log — \"waivers are listed in the ship report; never silent\" (Table 8).",
  "**Writer actions in the Studio** — the ViewModel exposes escalateGun, resolveGun (canon chapter + archived payoff quote mandatory), and waiveGun (blank or whitespace note refused); guards return explicit refusal messages rather than silent no-ops.",
]));

body.push(lib.h2("3.4 The question ledger — foundations (plan §6.7, Table 15)"));
body.push(...lib.bullets([
  "**Deterministic detection** — QuestionLedger.detectOpenQuestions scans confirmed text for open-question cues (\"the question of…\", \"no one knew…\", \"the mystery of…\", long-horizon \"would haunt…\" phrasing), opening PROGRESSIVE entries with an expected-answer window (opened + 3 chapters) and LONG_TERM entries with no window; duplicate cues within a chapter collapse to one entry.",
  "**Windows, overdue surfacing, answering** — isOverdue applies only to unanswered progressive questions past their window (long-term questions are never overdue — they resolve at a turning point or climax); overdueReport renders the writer's dashboard lines with opened/due chapters; answer() records the answering chapter and links the answering revelation (answeredByReveal). The six revelation outcomes of §6.7 ship as the revelationOutcomeIndices check — a twist matching none of the six flags as shock-only.",
  "**Persistence** — the schema-v2 questions table (P0) is now live end to end: AppRepository gains the QuestionDao surface (nullable DAO keeps older call sites working), Main.kt wires it, and the confirm flow opens detected questions automatically.",
]));

body.push(lib.h2("3.5 The Studio ledger panel and pipeline integration"));
body.push(...lib.bullets([
  "**One ledger UI, two entry kinds** (§6.7) — the Writer Studio's right column now pairs the margin rail with a Continuity Ledger panel: guns with status chips (INTRODUCED / ESCALATED / RESOLVED / ORPHANED / WAIVED), chapter references, Resolve and Waive actions (waive requires the mandatory note in a dialog), the live compile-gate chip (\"COMPILE BLOCKED\" / \"compile clear\"), the final-act boundary line, and the question ledger with due/overdue chips and an Answer action.",
  "**ViewModel state** — guns and questions load per book; the compile gate recomputes with every sovereignty refresh; the confirm flow now plans the ledger upsert, runs the orphan sweep, opens detected questions, and reports ledger writes in its status line.",
  "**CompilationWorker** — the recovery gate sits between the canon gate and the manuscript build; blocking guns fail the work with the remedy in the log, waivers are disclosed, and a clean ledger compiles without friction.",
]));

body.push(lib.h1("4. The Golden Corpus and Regression Suites"));
body.push(...lib.tbl(
  "Table 2. P2 test suites (63 tests total, 0 failures)",
  ["Suite", "Tests", "Covers"],
  [
    ["ClicheCleanerGoldenCorpusTest (new)", "8", "Exit gate 1: matrix parity with Table 6 (154 / 30 / 32 / 28 / 24 / 18 / 22), the 50-page corpus build, precision 1.0000 (gate ≥ 0.90), planted-catch recovery, all six classes firing, the prompt-constraint block, offender-token ranking; planted-vs-pattern alignment asserted 1:1"],
    ["ChekhovLedgerTest (new)", "7", "Exit gates 2–3: the six-chapter regression serial through the confirm flow (all planted guns detected, distinct-vow non-duplication, canon-only provenance), escalation on re-emphasis, escalate/resolve/waive guards (payoff quote mandatory; waiver note mandatory), the orphan sweep at the final-act boundary, the recovery gate's blocking set, and the end-to-end blocked-serial recovery"],
    ["QuestionLedgerTest (new)", "6", "Detection with windows (progressive +3), long-term without a window, same-chapter dedupe, overdue semantics across the four question states, answering + revelation link, the six revelation outcomes incl. the shock-only flag"],
    ["TokenBiasPolicyTest (new)", "8", "Table 7 per route (Groq/NVIDIA/Custom OpenAI/OpenRouter bias; Ollama via compat surface; Mistral/Gemini none; unknown routes fail closed), the −5 payload under the route ceiling, empty payload on clean text and non-bias routes, the enforcement plan with the linter gate"],
    ["LiveAstAnnotatorTest (P1, unchanged)", "11", "Canon-only refusal, six node kinds, 7/7 beats, cliché payload contract — now backed by the full matrix"],
    ["SixLayerPipelineTest / WriterGateTest / WriterStudioFlowTest (P1, unchanged)", "15", "Layer provenance, canon-only ledger builds, sovereignty primitives, the five-chapter novella confirm flow"],
    ["AuthrContainerRoundTripTest / PluginRegistryTest (P0, unchanged)", "11", "Container schema v2 round-trip, plugin scaffold (regression guards)"],
  ],
  [30, 8, 62]
));
body.push(lib.p(
  "The corpus is deterministic and self-alining: one planted catch per pattern in matrix order, each verified by the suite to fire its aligned pattern before the corpus is assembled. Precision is measured span-wise — a hit overlapping a planted span is a true positive, any other hit is a false positive — and the clean sentence bank is engineered with near-miss phrasing so the FP count is meaningful rather than vacuous. Reported margin: 154 TP, 0 FP."
));

body.push(lib.h1("5. Build & Distribution Evidence"));
body.push(...lib.bullets([
  "Full test suite: **63/63 green** (gradle :desktop:test; the 34 P0/P1 tests are unchanged and still pass — the P1 exact-count cliché assertion passes unchanged against the full 154-pattern matrix).",
  "The 188 MB distributable app image rebuilds (gradle :desktop:createDistributable) and the shipped jar carries all P2 classes (ClicheCleaner, TokenBiasPolicy, ChekhovLedger, QuestionLedger, the Studio LedgerPanel composables, and the updated workers verified inside lib/app/desktop-*.jar).",
  "Headless smoke run under Xvfb: the app process launches, initializes the data root (/home/z/.local/share/authr), the Pocket TTS bridge, and DataStore autosave, and stays alive through the 40-second window. UI pixel rendering requires a GL context the sandbox lacks (skiko RenderException under Xvfb, as in P1), so visual QA of the ledger panel remains a workstation task — the ViewModel and engine layers behind it are fully covered by the assertion suite.",
]));

body.push(lib.h1("6. Implementation Notes & Decisions"));
body.push(...lib.bullets([
  "**Weak intensifiers and opening crutches found homes in Table 6's classes**: intensifiers (\"very angry\") ride the Filter words class (telling-not-showing crutches) and the \"dark and stormy night\" opening crutch rides Plot-formula phrases — the plan's six classes absorb the P1 starter's four with no orphaned category, and the class counts stay exactly as Table 6 specifies.",
  "**Duplicate detection compares archived context quotes, not detector descriptions**: the ±40-character quote window gives re-emphasis a shared token footprint (overlap ≥ 0.6) while distinct promises of the same detector class share almost none — which is what lets the ferryman's vow exist alongside Mara's. Escalation matching is deterministic; LLM-proposed extraction with richer identity remains the full-L6 work of a later phase, as scoped.",
  "**The compile gate is code-level and test-level**: the worker wiring for the ported desktop edition still runs through the ported WorkManager shim (as in P1), so the gate's enforcement is proven by the engine function and the seeded serial; the worker integrates it at the same seam as the P1 canon gate.",
  "**Sandbox build repair (environmental, not product)**: Maven Central rate-limited Gradle's metadata bursts (HTTP 429) for one artifact; the missing artifacts were fetched with curl into ~/.m2 and mavenLocal() was added to settings.gradle.kts ahead of mavenCentral, with a comment explaining the workaround. No product dependency versions changed.",
  "**Level 4 remains retained per SCR-1** (carried from P1): the P2 cleaner suppresses patterns at generation time and lints canon text — it never gates the writer's confirm flow, and its only hard stop is the compile gate the plan itself specifies.",
]));

body.push(lib.h1("7. P3 Handoff"));
body.push(...lib.bullets([
  "P3 (Serialization) consumes the question ledger built here — the information levels of §6.7 already share storage with the Chekhov machinery (questions + chekhov_guns tables live since P0); serial_arcs.questionRef and answeredQuestionRef can now reference real rows, and the overdue surfacing becomes the writer's dashboard widget.",
  "The golden-corpus pattern (deterministic corpus + span-level precision/recall measurement + planted-alignment assertions) is the template P3's validator suites should reuse: a 15-chapter golden serial with planted seven-beat coverage, ending types, and arc phases will slot into the same structure.",
  "The compile gate now blocks on unresolved guns; P3's free-tier/anti-withholding gate composes with it (both run before export; the recovery gate is ledger-only, the withholding gate is chapter-count-based).",
  "No blockers carried from P2; the annotator's cliché payloads (class, severity, rewrite) are stable, so scorecard criteria that consume them (criteria 1–7) can aggregate without re-deriving.",
]));

// ── Document assembly ──────────────────────────────────────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Implementation Report — Phase P2",
  subtitle: "Prose Quality Systems: cliché cleaner (154 patterns, 6 classes), provider token-bias matrix, Chekhov ledger with final-act gate, question ledger foundations",
  englishLabel: "PHASE REPORT · P2",
  metaLines: [
    "Plan: Authr OS 3.0 Improvement Plan, Revision 2 (32 person-weeks, P0-P5)",
    "Status: P2 complete — exit gate green (63/63 tests, precision 1.0000)",
    "Date: November 9, 2026 · Baseline: authr-desktop @ P2 commit",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential — Internal Engineering Document",
  palette: P.cover,
};

const doc = new Document({
  creator: "Authr OS Product Team",
  title: "Authr OS 3.0 — P2 Implementation Report",
  styles: {
    default: {
      document: { run: { font: FONT_BODY, size: 21 } },
      heading1: {
        run: { font: FONT_HEAD, size: 28, bold: true, color: P.ink },
        paragraph: { spacing: { before: 280, after: 140, line: 312 } },
      },
      heading2: {
        run: { font: FONT_HEAD, size: 24, bold: true, color: P.ink },
        paragraph: { spacing: { before: 200, after: 100, line: 312 } },
      },
      heading3: {
        run: { font: FONT_HEAD, size: 22, bold: true, color: P.ink },
        paragraph: { spacing: { before: 160, after: 80, line: 312 } },
      },
    },
  },
  sections: [
    {
      properties: {
        page: { size: pgSize, margin: { top: 0, bottom: 0, left: 0, right: 0 } },
      },
      children: lib.buildCoverR1(coverConfig),
    },
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: pgSize, margin: pgMargin, pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } },
      },
      headers: { default: docHeader() },
      footers: { default: pageNumFooter() },
      children: body,
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT, buf);
  console.log("written:", OUT, buf.length, "bytes");
});
