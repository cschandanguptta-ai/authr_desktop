// Authr OS 3.0 — P3 Implementation Report (phase deliverable)
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

const OUT = "/home/z/my-project/download/Authr-OS-3.0-P3-Implementation-Report.docx";

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
        text: "Authr OS 3.0 · P3 Implementation Report · November 9, 2026",
        size: 16, color: P.secondary, font: FONT_BODY,
      })],
    })],
  });
}

// ── Content ────────────────────────────────────────────────────────────────
const body = [];

body.push(lib.h1("1. Executive Summary"));
body.push(lib.p(
  "Phase **P3 — Serialization is complete with its exit gate green**. Serialized fiction is now a first-class project format in both workspaces, specified end to end by the Serialized Fiction Blueprint 2.0 (plan §6). The deterministic validator suite encodes the blueprint's structural rules as local, testable code: the opening validators (hook, story promise, exposition runs, substantial developments) over the free tier (§6.2), the seven-beat coverage checker and balance meter (§6.3), the ending advisor with its six types plus QUIET and the soft fatigue flag that replaces the 1.0 mechanical rotation (§6.4), the micro-arc planner with the completion requirement (§6.5), the pre-draft worksheet (§6.8), the 14-point pre-publication scorecard with its interpretation bands and zero-triggers (§6.9), the buffer and cadence tooling (§6.10), and the six paste-ready platform export profiles (§6.13). The anti-withholding gate — Blueprint 2.0's replacement of the paywall-cliffhanger rule — runs before every serial export, composed with the P2 final-act recovery gate. A new Serial Dashboard workspace hosts the publication-side tooling: buffer gauge, cadence tracker, arc board, overdue-question widget, worksheet and scorecard editors, release actions, and the export profile picker."
));
body.push(lib.p(
  "The **exit-gate corpus is a fully deterministic, planted 15-chapter golden serial** — \u201CThe Estate of Fallingwater Dike\u201D — whose chapters carry the cue of every beat function, hook every free-tier opening inside the 200\u2013400-word span, exercise all seven ending types without consecutive same-type high-intensity repeats, sit inside the 1,000\u20132,000-word envelope (measured 1,093\u20131,223 words), and release on Mon/Wed/Fri dates with a 4-chapter amber buffer. The full suite reports **0 warnings and 0 hard blocks across 79 passing checks**. Two seeded negative variants prove the gates fire exactly where the plan says: a withheld-payoff project trips the anti-withholding gate on chapter 5 alone, and scorecard band variants walk all four interpretation bands plus both zero-triggers. The full suite stands at **130/130 tests green — the 63 P0/P1/P2 tests unchanged and passing, plus 67 new P3 tests** — and the 188 MB distributable app image rebuilds and launches."
));

body.push(lib.h1("2. P3 Exit Gate (plan §13, Table 33)"));
body.push(...lib.tbl(
  "P3 exit-gate evidence (plan §13, Table 33; acceptance metrics §15, Table 34)",
  ["Exit gate", "Acceptance metric", "Verdict", "Evidence"],
  [
    [
      "A 15-chapter golden serial passes the full validator suite with at most two dismissable warnings",
      "All checks pass; ≤ 2 dismissable warnings",
      "**PASS**",
      "SerializationGoldenSerialTest (13 tests): the suite over the planted corpus reports 0 hard blocks and 0 warnings (79 passing checks); every beat coverage check reads 7/7; hook validator passes on chapters 1\u20135 inside the 200\u2013400-word span; all five chapter-1 story-promise goals present; substantial developments in chapters 4\u20135; all seven ending classifications match the planted plan; arc 1 completes with its question answered inside the window and its contribution declared; cadence check passes with 11 on-schedule releases; non-serialized books short-circuit; the canon gate composes (a PROPOSED chapter hard-blocks export)"
    ],
    [
      "The anti-withholding gate fires correctly on a seeded free tier",
      "Fires exactly on the seeded withheld-payoff project",
      "**PASS**",
      "AntiWithholdingGateTest (7 tests): the gate binds to Book.freeTierChapters (a free tier of 3 moves the boundary to chapter 3), fires on the seeded variant that strips chapter 5's payoff, seed and payoff beat (both halves required \u2014 payoff alone fails), passes on the golden serial, does not gate retail-only books (free tier 0) or non-serialized projects, and the seeded variant differs from the golden serial in chapter 5 only"
    ],
    [
      "Scorecard gates behave per the interpretation bands",
      "Interpretation bands applied correctly; zero-triggers always block",
      "**PASS**",
      "ScorecardGateTest (8 tests): seeded band cases walk all four bands \u2014 12\u201314 STRONG releases, 9\u201311 REASONABLE releases with a writer note on the weak criteria, 6\u20138 REVISION blocks until revised or overrides with a note, 0\u20135 RECONSIDER blocks with a note attached (never overridable); band boundaries exact (12/11/7/6/4); a zero on criterion 2 (objective) or 6 (payoff) blocks a 12-total chapter and clears only with an explicit, disclosed writer note; blank notes never override; unscored chapters cannot enter the release queue; the L6 deterministic proposal zeroes exactly on the two hard criteria"
    ],
  ],
  [16, 14, 8, 62]
));

body.push(lib.h1("3. What Was Implemented"));

body.push(lib.h2("3.1 Opening validators and the free-tier ladder (plan §6.2, Table 9)"));
body.push(lib.p(
  "**OpeningValidators** (engine/serialization) encodes Blueprint 2.0 Part 1 as deterministic code. The hook validator scans the first 200\u2013400 words for any of the **seven acceptable sources of interest** \u2014 a disturbing or unusual event; an urgent problem; a character pursuing something important; an unexpected confrontation; a mystery that challenges the reader's assumptions; a powerful emotional situation; an intriguing voice or unusual narrative perspective \u2014 and flags pure-description openings. The chapter-1 story-promise check verifies the five goals (protagonist, immediate situation, conflict, reason to care, unanswered question). Chapters 2\u20133 get the exposition-run check (120+-word narration stretches without dialogue or action); chapters 4\u20135 get the substantial-development check against the six development categories. The **outline gate** refuses a serial outline whose chapter 1 does not carry the inciting beat (wired into OutlineWorker). The L4 opening rule \u2014 begin with a character experiencing, pursuing, or confronting something that matters \u2014 is encoded for the drafting prompt."
));
body.push(lib.h2("3.2 The anti-withholding gate (plan §6.2, Table 9, last row)"));
body.push(lib.p(
  "The gate is chapter-count based and binds to Book.freeTierChapters: the final free chapter must contain a confirmed payoff beat resolving part of the opening conflict PLUS the seed of a more consequential development \u2014 the reader must feel the story rewarded their investment and that the next chapter offers genuinely valuable continuation. This is the **inverse of the Rev 1 paywall-cliffhanger rule, which Blueprint 2.0 replaces**. Retail-only projects (free tier 0) are not gated. The gate composes with the P2 final-act recovery gate in the export path: the recovery gate is ledger-only, the withholding gate is chapter-count-based, and both run before any serial export."
));
body.push(lib.h2("3.3 Seven-beat coverage and the balance meter (plan §6.3, Tables 10\u201311)"));
body.push(lib.p(
  "The coverage checker reuses the Live AST Annotator's beat cues as a single source of truth (the annotator now exposes its cue lists so the rail anchors and the measured shares can never diverge). The **BalanceMeter** measures each chapter's segment shares against the advisory bands \u2014 opening/continuity 10\u201315%, objective/conflict/escalation 50\u201360%, turning/payoff 20\u201325%, forward pull 10\u201315% \u2014 from the first cue span of each present beat; the first beat owns the lead-in and the last owns the run to the end, so shares always cover 100%. Deviations are **informational, never blocking**; the word envelope (wordMin/wordMax, default 1,000\u20132,000) is advisory, and the meter's own copy says what the plan says: do not pad or split a scene to hit a number."
));
body.push(lib.h2("3.4 The ending advisor (plan §6.4, Table 12)"));
body.push(lib.p(
  "The advisor classifies every canon chapter's closing passage against the six Blueprint 2.0 types plus QUIET (one classifier shared with the rail's ending signal), records type history, and raises the **soft fatigue flag only when the same high-intensity type repeats in consecutive chapters**. The 1.0 rotation wheel and its least-recently-used suggestion are absent from the codebase; quiet, reflective endings are a first-class classification, and the advisor never auto-assigns a type \u2014 the writer confirms or picks another, and a stored type that differs from the classification is informational only."
));
body.push(lib.h2("3.5 The micro-arc planner (plan §6.5, Table 13)"));
body.push(lib.p(
  "The planner lays 15-chapter arcs across the book's targetChapters with the four default phases \u2014 Objective & Setup (1\u20133), Escalation (4\u20137), Reversal & Pivot (8\u201311), Climax & Resolution (12\u201315) \u2014 compressing the final arc's phases proportionally when the chapter count does not divide by 15, so every arc can still close. Six deterministic checks per arc: the conflict statement recorded and the conflict introduced by chapter 3 of the arc; at least two escalation beats across the escalation span; a reversal beat in the reversal phase; climax and payoff beats confirmed; the next-arc objective seeded before close; and the **completion requirement** \u2014 the arc's question answered in the ledger plus one of the five contribution kinds declared. In-flight arcs report progress; the suite hard-blocks only a closed arc with an unanswered arc-level question. The ship report lists every arc's contribution."
));
body.push(lib.h2("3.6 The pre-draft worksheet (plan §6.8, Table 16)"));
body.push(lib.p(
  "The worksheet is a five-section card (A narrative objective, B character development, C plot development, D reader experience, E chapter ending) with the D payoff link mandatory. It persists as JSON on the chapter (§6.12 field), validates with section-level messages, auto-fills from the arc plan and the open questions of the ledger for the Autonomous run (writer edits before the run), and feeds the L4 drafter as prompt context \u2014 the chapter is drafted toward an explicit purpose instead of a word count."
));
body.push(lib.h2("3.7 The 14-point scorecard (plan §6.9, Tables 17\u201318)"));
body.push(lib.p(
  "Seven criteria, 0\u20132 each, maximum 14, with the deterministic-contribution proposal anchored on the validator results (hook, worksheet sections, beat coverage, payoff link, ending signal) so the L6 proposal is reviewable rather than invented \u2014 and the writer always sets the final scores. The interpretation bands apply exactly per Table 18: 12\u201314 release queue; 9\u201311 release queue with a writer note on the weak criteria; 6\u20138 blocked until revised or overridden with a note; 0\u20135 blocked, revision required, never overridable. The two zero-triggers \u2014 criterion 2 (narrative objective clarity) and criterion 6 (satisfying payoff) \u2014 block regardless of total and clear only with an explicit, disclosed writer note. The export pre-flight ships the ten final drafting principles and the four ultimate rules verbatim."
));
body.push(lib.h2("3.8 Buffer and cadence (plan §6.10, Table 19)"));
body.push(lib.p(
  "The buffer meter counts confirmed-but-unreleased chapters \u2014 green at 8+, amber 4\u20137, red below 4, target per book (4\u20138 range). The cadence tracker validates released chapters against the book's releaseSchedule (v2.3 field, default Mon/Wed/Fri) with a streak count and slip warnings for off-schedule dates and weekly gaps. Release state rides the v2.3 free-text chapter status as a \u201CReleased (ISO date)\u201D marker \u2014 the serialization field set adds no released column, so the ported schema stays untouched. None of it depends on platform APIs; the tooling is local and pure."
));
body.push(lib.h2("3.9 Platform export profiles (plan §6.13, Table 20)"));
body.push(lib.p(
  "Six shipped profiles \u2014 Royal Road, Wattpad, GoodNovel, Dreame, Webnovel, Tapas \u2014 each producing a paste-ready chapter package (title, optional recap header from the recap tool, body paragraphs in the platform's spacing conventions, author note with CTA, poll payload where supported) plus a plain-text clipboard bundle. The contract is the plan's: **the application formats, the writer pastes** \u2014 no platform APIs. Per-profile behaviors verified by test: Royal Road no recap by default; Wattpad looser paragraph spacing; pay-per-chapter storefronts carry the recap header and strip external links from the note; Tapas exports poll payloads and carries the episode-length guidance against the envelope. Profiles are versioned data, adjustable without a release (the plan's ToS-change mitigation)."
));
body.push(lib.h2("3.10 The Serial Dashboard and pipeline integration (plan §6.11, both modes)"));
body.push(lib.p(
  "A new **Serial Dashboard workspace** (global bar, tab 7) hosts the publication surface: buffer gauge, cadence strip, the arc board (lay arcs, edit conflict and contribution), the overdue-question widget, the validator-suite report with dismissal, per-chapter worksheet and scorecard editors, the release action, the export profile picker, and the paste-ready package preview. The same engine code serves both workspaces per §6.11 \u2014 the Writer Studio's confirm flow now persists the serialization artifacts on the frozen text (advised ending type, beat coverage, balance shares, and the free-tier hook check), and the Autonomous run report (draftReport) runs coverage, hook, and ending classification over PROPOSED chapters before they enter the review queue. In the drafting path, the 1.0-era [CLIFFHANGER SCORE] instruction is **removed and replaced** by the Blueprint 2.0 contract: the three chapter duties, the seven beat functions, the natural-ending rule, the four ultimate rules, and the word envelope, plus the worksheet block and the chapter-1 opening rule. In the compile path, serial projects run the full validator suite before the manuscript is assembled; hard blocks fail the compile with the blocking messages logged in the agent chat, and warnings and arc ship-report lines are disclosed."
));

body.push(lib.h1("4. The Golden Serial (exit-gate corpus)"));
body.push(lib.p(
  "\u201CThe Estate of Fallingwater Dike\u201D is fully deterministic and planted, in the P2 golden-corpus pattern. Every chapter carries the cue of all seven beat functions; every free-tier opening holds a source of interest inside the 200\u2013400-word span; chapters 4\u20135 carry substantial developments; the final free chapter satisfies the anti-withholding gate; each closing passage contains exactly one ending type's signature cues with all other cue families absent by construction; chapter lengths sit inside the envelope; chapters 1\u201311 release on consecutive Mon/Wed/Fri dates leaving a 4-chapter amber buffer; the single micro-arc declares its conflict, its question (opened in chapter 1, answered in chapter 4 inside the expected-answer window), and its contribution. Two seeded variants drive the negative gates, and a 25-template neutral filler bank (no ending-type, question-ledger, or promise cues) keeps the planted signals unambiguous."
));
body.push(...lib.tbl(
  "Golden serial measured profile (CorpusProbe over the built jar)",
  ["Measure", "Value"],
  [
    ["Chapters", "15 (target 15, free tier 5, envelope 1,000\u20132,000)"],
    ["Chapter length range", "1,093\u20131,223 words"],
    ["Ending types exercised", "All seven (MYSTERY_DISCOVERY \u00D73, DANGER \u00D72, QUIET \u00D72, REVERSAL \u00D72, EMOTIONAL_REVELATION \u00D72, IRREVERSIBLE_CHOICE \u00D72, ANTICIPATION \u00D72)"],
    ["Scorecard", "13/14 STRONG on every chapter (criterion 4 at 1; no zero-triggers)"],
    ["Release schedule", "Chapters 1\u201311 on Mon/Wed/Fri (2025-01-06 anchor); buffer 4 (amber)"],
    ["Validator suite verdict", "passed = true, exit-gate green; 0 warnings, 0 hard blocks; 79 passing checks of 140 total (the rest are informational balance notes and per-arc lines)"],
  ],
  [26, 74]
));

body.push(lib.h1("5. Test Suite Evidence"));
body.push(...lib.tbl(
  "The P3 test classes (67 new tests on top of the 63 unchanged P0/P1/P2 tests; 130/130 green)",
  ["Test class (new)", "Tests", "Guards"],
  [
    ["SerializationGoldenSerialTest", "13", "Exit gate 1: the full suite over the planted corpus; beat coverage 7/7 per chapter; hooks on chapters 1\u20135; story promise; developments; envelope; ending plan match; arc completion; cadence/buffer; overdue questions; warning dismissal mechanics; the canon gate composing"],
    ["AntiWithholdingGateTest", "7", "Exit gate 2: fires exactly on the seeded withheld-payoff project; both halves required; free-tier binding; retail-only and non-serialized exemptions; variant isolation"],
    ["ScorecardGateTest", "8", "Exit gate 3: all four interpretation bands; exact boundaries; zero-triggers block regardless of total; note-only override; blank-note refusal; unscored refusal; the L6 proposal's zero anchoring"],
    ["MicroArcPlannerTest", "8", "Phase bounds; arc layout and final-arc truncation; all six planner checks on the golden arc; the completion requirement; in-flight progress reporting; seeds parsing"],
    ["BufferCadenceTest", "7", "Buffer levels 8/4; canon-only counting; release markers; Mon/Wed/Fri dates; slip and gap detection; schedule parsing"],
    ["SerialExportProfilesTest", "7", "The six profiles' per-platform behaviors; package structure; recap gating; poll gating; link stripping; episode-length guidance; batch export"],
    ["EndingAdvisorBalanceWorksheetTest", "10", "Advisor classifications; the fatigue flag (consecutive-only); QUIET support; balance shares summing to 100; advisory envelope; worksheet validation, JSON round-trip, prompt block, auto-fill"],
    ["SerialDashboardTest", "7", "The dashboard state machine over Room-free fakes: recompute, arc planning, worksheet/scorecard persistence, release actions (canon-gated), dismissal disclosure, export-profile persistence"],
  ],
  [22, 7, 71]
));
body.push(lib.p(
  "The suite runs with `gradle :desktop:test`: **130 tests, 0 failures**. The 63 P0/P1/P2 tests are unchanged and passing \u2014 the P1 writer-sovereignty flows, the P2 clich\u00E9 matrix and ledger machinery, and the container round-trip all hold against the P3 code with no regression fixes needed."
));

body.push(lib.h1("6. Build & Distribution Evidence"));
body.push(...lib.bullets([
  "Full test suite: **130/130 green** (gradle :desktop:test; 63 P0/P1/P2 tests unchanged plus 67 new P3 tests).",
  "The 188 MB distributable app image rebuilds (gradle :desktop:createDistributable, 73 jars in lib/app) and launches under Xvfb with software rendering; the process stays alive through the 50-second smoke window and the first composition renders (onboarding screenshot captured by the Robot walkthrough harness).",
  "The Serial Dashboard wiring (tab 7 in the global workspace bar, SerialDashboardViewModel + SerialDashboardScreen, repository serial-arc methods, CompilationWorker serial export gates) is compiled into the shipped jar; UI pixel rendering of the dashboard remains a workstation task as in P1/P2, while the ViewModel and engine layers behind it are fully covered by the assertion suite.",
]));

body.push(lib.h1("7. Implementation Notes & Decisions"));
body.push(...lib.bullets([
  "**Release state rides the v2.3 chapter status field** as a \u201CReleased (ISO date)\u201D marker rather than a new column: the serialization field set (plan Table 21) defines no released field, so the ported schema v2 stays untouched and additive \u2014 the buffer meter and cadence tracker are pure functions over canon chapters and their status markers.",
  "**One classifier, one cue list**: the balance meter and the ending advisor consume the Live AST Annotator's beat cues and ending classifier through two new public accessors, so the rail's anchors, the persisted endingType, and the measured shares can never diverge from each other.",
  "**The 1.0 cliffhanger score is gone from the drafting prompt**: Blueprint 2.0 prohibits mechanical cliffhanger rotation and scores nothing; the L4 webnovel block now carries the three chapter duties, the seven beat functions, the natural-ending rule with quiet endings permitted, the four ultimate rules verbatim, and the advisory envelope \u2014 plus the worksheet and the chapter-1 opening rule.",
  "**The anti-withholding gate composes with the recovery gate, not replaces it**: the P2 final-act recovery gate stays ledger-only; the withholding gate is chapter-count based; the scorecard bands and zero-triggers gate the release queue; all run before export inside CompilationWorker for serialized books, with every hard block logged to the agent chat.",
  "**Dismissals are writer notes, not bypasses**: a dismissed warning leaves the exit-gate budget but stays disclosed in the report; hard gates are never dismissable \u2014 mirroring the plan's risk-register commitment that every serialization check is tunable per project and dismissable with a note.",
  "**The build ran against a recycled sandbox**: the Maven-local dependency cache from P2 was gone, and Central's burst rate limits re-appeared; repeated Gradle resolutions (each caching its successes) rebuilt the dependency set without any product dependency change \u2014 the settings.gradle.kts mavenLocal() workaround from P2 remained in place and sufficient.",
]));

body.push(lib.h1("8. P4 Handoff"));
body.push(...lib.bullets([
  "P4 (Audiobook Engine) consumes the confirmed-canon pipeline exactly as P2/P3 left it: WriterGate.canonChapters is the audio source set, the Pocket TTS bridge and the audio-only ffmpeg reduction from P0 are the transport, and the Serial Dashboard's release queue is the natural chapter source for narration batches.",
  "The ACX audit depends on P0's audio-only ffmpeg reduction staying complete \u2014 the P3 phase touched no audio path, so the mastering chain's binary set is unchanged.",
  "The serialization data model is final for v3.0: P4 needs no schema migration (chapters carry writer-confirmed text, worksheet context for narration tone, and the release markers for batch ordering).",
  "The validator suite's severity model (PASS / INFO / WARN dismissable / HARD) is the template for the ACX audit report: red/green per criterion with measured values, advisory notes never blocking a clean master.",
]));

// ── Document assembly ──────────────────────────────────────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Implementation Report — Phase P3",
  subtitle: "Serialization: opening validators, seven-beat coverage and balance meter, ending advisor, micro-arc planner, worksheet, 14-point scorecard, buffer and cadence, platform export profiles — both modes",
  englishLabel: "PHASE REPORT · P3",
  metaLines: [
    "Plan: Authr OS 3.0 Improvement Plan, Revision 2 (32 person-weeks, P0-P5)",
    "Status: P3 complete — exit gate green (130/130 tests; golden serial suite 0 warnings, 0 hard blocks)",
    "Date: November 9, 2026 · Baseline: authr-desktop @ P3 build",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential — Internal Engineering Document",
  palette: P.cover,
};

const doc = new Document({
  creator: "Authr OS Product Team",
  title: "Authr OS 3.0 — P3 Implementation Report",
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
