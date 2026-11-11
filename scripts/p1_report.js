// Authr OS 3.0 — P1 Implementation Report (phase deliverable)
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

const OUT = "/home/z/my-project/download/Authr-OS-3.0-P1-Implementation-Report.docx";

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
        text: "Authr OS 3.0 · P1 Implementation Report · November 9, 2026",
        size: 16, color: P.secondary, font: FONT_BODY,
      })],
    })],
  });
}

// ── Content ────────────────────────────────────────────────────────────────
const body = [];

body.push(lib.h1("1. Executive Summary"));
body.push(lib.p(
  "Phase **P1 — Writer Engine Core is complete with its exit gate green**. The desktop codebase now hosts the six-layer drafting engine's writer-sovereignty machinery end to end: L4 drafts land as PROPOSED, the Writer Studio's confirm flow freezes text into canon (writer_confirmed = 1), the Live AST Annotator annotates confirmed text only across all six node kinds, the Chekhov ledger receives its first entries on confirmation, and every downstream consumer — L5 polishing, compilation, EPUB export — is gated to canon chapters at the code level. The P1 exit gate from the plan (§13, Table 33) is discharged by the seeded-project assertion suite: a five-chapter novella runs end to end through the confirm flow, and zero unconfirmed text reaches any ledger or export (34/34 tests green, including the 20 new P1 tests)."
));
body.push(lib.p(
  "The implementation adds 2,229 lines across nine new files and modifies eight existing ones, deliberately without restructuring the ported Android surface: the Writer Studio is a new subsystem (engine package + dedicated ViewModel + dedicated screen) wired in through one new global workspace tab, so the 15,000+ lines of ported screens remain untouched."
));

body.push(lib.h1("2. P1 Exit Gate (plan §13, Table 33)"));
body.push(...lib.tbl(
  "Table 1. P1 exit gate and verification evidence",
  ["Gate", "Target", "Result", "Evidence"],
  [
    ["A five-chapter novella runs end to end with the confirm flow", "Full PROPOSED → CONFIRMED walk on a seeded project", "**PASS**", "WriterStudioFlowTest — 3/3 green: five seeded L4 drafts queue for review; ch. 1-3 accepted from the queue, ch. 4 edited then confirmed, ch. 5 archived; ledger entries written on confirmation trace exclusively to canon chapters"],
    ["No unconfirmed text reaches any ledger or export", "Zero occurrences across the consumer surface (seeded-project assertion suite)", "**PASS — zero occurrences**", "Gate assertion across chekhov_ledger / timeline / epub_export / manuscript_compile: canon-fed surface yields zero violations; the same primitive applied to an unfiltered feed still catches the archived chapter (the gate is real, not vacuous); Studio sovereignty report holds (34/34 tests, 0 failures)"],
  ],
  [27, 18, 15, 40]
));
body.push(lib.p(
  "The gate semantics were sharpened during implementation: the pipeline feeds canon only by construction, so the Studio's live report distinguishes **violations** (always zero unless the gate itself is broken) from **withheld** chapters (proposals and archives the gate keeps away from consumers). The withheld count is visible in the Studio header at all times, so the writer can see exactly what is being protected."
));

body.push(lib.h1("3. What Was Implemented"));
body.push(lib.h2("3.1 Six-layer engine core (engine package)"));
body.push(...lib.bullets([
  "ManuscriptAst — the AST node model (plan §4.1, Table 5): six annotation kinds (Beat, Cliché hit, Chekhov gun, Lore reference, Ending signal, Continuity flag), three severities, thirteen writer actions, and span-pinned nodes; the AST carries a stable text hash pinning it to the frozen text.",
  "SixLayerPipeline — the layer contract (plan §4, Table 4): every artifact names its producing layer, owning agent, and confirmation state; L4 results are PROPOSED by construction; L6 builds ledger entries from canon text only; assertGate() is the reusable sovereignty primitive.",
  "DraftArchive — container-side storage for archived and regenerated drafts (<authr>/<book>/draft-archive/chapter-N-<stamp>.txt), honoring \"archived drafts are kept in the container for diffing but never feed downstream layers\".",
  "L4LlmDraftGenerator — the production Scene Composer prompt, assembled from confirmed material only: beat sheet, character sheets, lorebook keys, continuity summaries of confirmed chapters, persona, and the format's word envelope.",
]));
body.push(lib.h2("3.2 Live AST Annotator — deterministic, confirmed-text-only"));
body.push(...lib.bullets([
  "Refuses PROPOSED/ARCHIVED chapters with an explicit reason — the rail and statistics always reflect canon rather than proposals (plan §4.1).",
  "Beat coverage (plan §6.3, Table 10): all seven Blueprint 2.0 beat functions checked against deterministic cues — opening continuity, immediate objective, resistance, escalation, turning point, chapter payoff, forward pull; missing functions raise a WARN the writer can address or waive, and present beats emit per-beat rail anchors.",
  "Cliché scan (plan §5.1): starter pattern classes (dialogue-tag crutches, purple prose staples, weak intensifiers, opening crutches) each with class, severity, and suggested rewrite; the full 154-pattern matrix and provider token-biasing remain P2 scope, and the annotator hook ships here.",
  "Chekhov promises (plan §5.2): five deterministic detectors (detailed artifact, debt, vow, named grudge, open question) marking INTRODUCED nodes with archived quotes; on confirm, these upsert into the Chekhov ledger.",
  "Lore references: lorebook keys matched word-boundary/case-insensitive; recurring capitalized terms without entries are proposed inline as new definitions.",
  "Ending signal (plan §6.4): the closing passage is classified against the six Blueprint 2.0 ending types plus QUIET — quiet endings are explicitly permitted.",
  "Continuity flags: deterministic echo detection (repeated 5+ word phrases) and POV drift (mixed first/third person density).",
]));
body.push(lib.h2("3.3 Writer Studio — the editing surface (plan §4.1)"));
body.push(...lib.bullets([
  "Interactive Mode: chapter list with status chips, editor with word-envelope indicator (advisory wordMin-wordMax), Save / Regenerate / Archive / Confirm Chapter actions, and the live margin rail rendering the AST with severity colors, excerpts, messages, and suggested actions.",
  "Autonomous Mode: the batch review queue of PROPOSED chapters with keyboard-first navigation — ↑/↓ move the cursor, ⏎ confirm, E edit, A archive, R regenerate — with cursor-following scroll and an empty-queue clear state.",
  "The sovereignty gate chip in the header shows canon / proposed / archived / withheld counts live and turns red on any violation.",
]));
body.push(lib.h2("3.4 The confirm flow state machine (WriterStudioViewModel)"));
body.push(...lib.bullets([
  "confirmChapter: freezes the text, sets writerConfirmed = 1 and draftStatus = CONFIRMED, runs the annotator on the frozen text, persists the AST JSON into the container's annotations directory, and upserts Chekhov ledger entries (L6).",
  "regenerateDraft: archives the previous draft into the container for diffing, then produces a fresh PROPOSAL from confirmed material only; the L4 generator is injected as an interface, which is what makes the whole flow JVM-testable without network.",
  "archiveDraft: sets ARCHIVED, writes the diffable copy into the container, and removes the chapter from every consumer surface.",
  "Edits to confirmed chapters keep canon status and re-annotate in place; proposals never auto-annotate.",
]));
body.push(lib.h2("3.5 Pipeline gating — the four workers"));
body.push(...lib.bullets([
  "DraftingWorker (L4): fresh drafts now land as PROPOSED with writerConfirmed = false; a pending proposal is never overwritten by a re-run; continuity memory and plot-history updates are deferred (they consumed proposal text before — a layer-contract breach this phase closes); the next chapter's context stays anchored to canon.",
  "PolishingWorker (L5): polishes canon chapters only, and the styled revision lands as a NEW PROPOSAL (accept/edit/reject in the Studio) instead of silently overwriting canon — the side-by-side diff gate from plan Table 4.",
  "CompilationWorker and EpubExportWorker: consume WriterGate.canonChapters() only, log excluded unconfirmed chapters into the agent log, and produce exports from confirmed text exclusively.",
]));
body.push(lib.h2("3.6 Workspace integration"));
body.push(...lib.bullets([
  "A slim global workspace bar (Story · Audio · Translate · Metadata · Writer Studio · Settings) was added above the ported screens — the first top-level navigation the desktop edition has had, and the only change to the app shell.",
  "AppRepository gained the ChekhovGunDao surface (nullable DAO keeps older call sites working); ChapterDao gained the PROPOSED review-queue query; Main.kt routes tab 6 to the Studio.",
]));

body.push(lib.h1("4. Seeded-Project Assertion Suite"));
body.push(...lib.tbl(
  "Table 2. P1 test suites (34 tests total, 0 failures)",
  ["Suite", "Tests", "Covers"],
  [
    ["WriterStudioFlowTest (new)", "3", "The exit gate itself: five-chapter novella end-to-end confirm flow incl. an edit-then-confirm chapter and an archived chapter; regenerate-then-archive flow with injected fake L4 generator; keyboard queue order and cursor behavior"],
    ["LiveAstAnnotatorTest (new)", "11", "Canon-only refusal (PROPOSED and ARCHIVED), six node kinds present on seeded text, 7/7 beat coverage, missing-beat WARN, cliché class/severity/rewrite payloads, INTRODUCED promises with quotes, lore key matching, ANTICIPATION and QUIET ending classification, text-hash stability"],
    ["SixLayerPipelineTest (new)", "6", "Layer provenance on all six layers, agent map parity with plan Table 4, canon-only ledger builds, gate assertion catching unconfirmed feeds, empty-consumer behavior, L6 state semantics"],
    ["WriterGateTest (P0, unchanged)", "3", "The sovereignty primitives the phase builds on"],
    ["AuthrContainerRoundTripTest (P0, unchanged)", "5", "Container schema v2 round-trip (regression guard)"],
    ["PluginRegistryTest (P0, unchanged)", "6", "Plugin scaffold (regression guard)"],
  ],
  [30, 10, 60]
));
body.push(lib.p(
  "The novella fixture is deterministic: five seeded PROPOSED drafts with planted beat cues, two clichés, a vow and a debt promise, a lorebook key, an ANTICIPATION ending, and an exact-phrase echo. All detectors fire on the seeded text, which keeps the annotator's behavior pinned by example."
));

body.push(lib.h1("5. Build & Distribution Evidence"));
body.push(...lib.bullets([
  "Full test suite: 34/34 green (gradle :desktop:test; the 14 P0 tests are unchanged and still pass).",
  "The 188 MB distributable app image rebuilds (gradle :desktop:createDistributable) and the shipped jar carries all 45 new P1 classes.",
  "Headless smoke run under Xvfb: the app process launches, initializes the data root (/home/z/.local/share/authr), Room databases, DataStore autosave, request queue, and the Pocket TTS bridge, and stays alive through the 35-second window. UI pixel rendering requires a GL context the sandbox lacks (skiko RenderException under Xvfb), so visual QA of the new screen remains a workstation task — the ViewModel layer behind it is fully covered by the assertion suite.",
]));

body.push(lib.h1("6. Implementation Notes & Decisions"));
body.push(...lib.bullets([
  "Level 4 is retained as the productivity core per SCR-1 (plan §4): the six-layer contract, the PROPOSED landing state, and the confirm flow all reinforce rather than remove the Scene Auto-Drafter.",
  "DraftingWorker's memory and plot-history updates were deliberately deferred to confirmation: they previously consumed proposal text, which contradicted the layer contract (\"each consuming the previous layer's confirmed output\"). The Continuity Manager's ledger work now happens where the plan puts it — on confirm, on the frozen text.",
  "L5 styled revisions land as proposals rather than in-place canon edits; a future phase can add the true side-by-side diff UI on top of the state machine that now makes it representable.",
  "The annotator's cliché patterns are a starter set (9 patterns, 4 classes) with the exact node/payload contract the 154-pattern matrix will plug into in P2; no consumer code will need to change when the matrix lands.",
  "The annotator hash (textHash) pins ASTs to frozen text; the stored annotation JSON in the container is keyed by chapter number and includes the hash, so stale rails are detectable.",
]));

body.push(lib.h1("7. P2 Handoff"));
body.push(...lib.bullets([
  "P2 (Prose Quality Systems) consumes the annotator hooks shipped here: the 154-pattern cliché matrix plugs into LiveAstAnnotator.clicheHits and the generation-time suppression into the L4 prompt path; the provider token-bias matrix is a new concern.",
  "The Chekhov ledger now receives INTRODUCED entries on confirm; P2 adds escalation/resolution tracking, the final-act recovery gate at compile, and waiver flows (the ChekhovGunDao already carries the status lifecycle and waive-note column).",
  "The question ledger foundations (plan §5.3) share storage with the Chekhov machinery — QuestionDao from schema v2 is ready.",
  "No blockers carried from P1; the seeded assertion suite pattern established here is the template for P2's golden-corpus gates.",
]));

// ── Document assembly ──────────────────────────────────────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Implementation Report — Phase P1",
  subtitle: "Writer Engine Core: six-layer contract, Writer Studio, Live AST Annotator, PROPOSED/CONFIRMED/ARCHIVED flow, canon-gated pipeline",
  englishLabel: "PHASE REPORT · P1",
  metaLines: [
    "Plan: Authr OS 3.0 Improvement Plan, Revision 2 (32 person-weeks, P0-P5)",
    "Status: P1 complete — exit gate green (34/34 tests, zero gate violations)",
    "Date: November 9, 2026 · Baseline: authr-desktop @ P1 commit",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential — Internal Engineering Document",
  palette: P.cover,
};

const doc = new Document({
  creator: "Authr OS Product Team",
  title: "Authr OS 3.0 — P1 Implementation Report",
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
