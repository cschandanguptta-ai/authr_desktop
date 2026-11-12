// Authr OS 3.0 — P0 Implementation Report (phase deliverable)
// Route: Create (docx-js), Scene: Report, Cover: Recipe R1 (GO-1 palette)
"use strict";
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Header, Footer, PageNumber,
  AlignmentType, NumberFormat, SectionType, TableOfContents,
} = require("docx");

const lib = require("./v3_plan_lib");
const P = lib.P;
const FONT_BODY = lib.FONT_BODY;
const FONT_HEAD = lib.FONT_HEAD;

const OUT = "/home/z/my-project/download/Authr-OS-3.0-P0-Implementation-Report.docx";

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
        text: "Authr OS 3.0 · P0 Implementation Report · November 9, 2026",
        size: 16, color: P.secondary, font: FONT_BODY,
      })],
    })],
  });
}

// ── Content ────────────────────────────────────────────────────────────────
const body = [];

body.push(lib.h1("1. Executive Summary"));
body.push(lib.p(
  "Implementation of the Authr OS 3.0 improvement plan (Rev 2) has started, and **Phase P0 — Foundation & Clean-up is complete with all three exit gates green**. The desktop codebase at authr-desktop now carries the Rev 2 scope corrections SCR-1 through SCR-4 at the code level: the Scene Automation & Video subsystem is fully removed, Kitten TTS is replaced by Pocket TTS behind the existing engine abstraction, and the web-serial data model (schema v2) is in place. The .authr project container (schema v2) is implemented with atomic saves, backup-before-migration, and a checksum audit; the lossless round-trip gate is proven by the unit test suite (14 tests, 0 failures). The Pocket TTS desktop spike measured **7.3x real time on CPU for a 10,010-word chapter** (gate: >= 2x). A runnable desktop distribution (188 MB app image) was produced and smoke-tested to full UI initialization."
));
body.push(lib.p(
  "One finding deserves the reader's attention: the port source as previously committed did not compile (4,050 errors), and its build was missing the Material 3 dependency, the serialization plugin, and the desktop entry point. P0 therefore also completed the **build-repair work** listed in section 4, which was a prerequisite for every gate in this phase."
));

body.push(lib.h1("2. P0 Exit Gates (plan §13, Table 33)"));
body.push(...lib.tbl(
  "Table 1. P0 exit gates and verification evidence",
  ["Gate", "Target", "Result", "Evidence"],
  [
    ["v2.3 project round-trips through the container losslessly", "Lossless; manifest records the migration", "**PASS**", "AuthrContainerRoundTripTest — 5/5 green: full-field book parity, chapter parity incl. writer state and unicode, ledger/arc/lore parity, atomic re-save, v1->v2 forward migration with backup, checksum audit flags tampering"],
    ["Zero video or Kitten references in the codebase", "Zero after P0", "**PASS**", "Repository-wide grep: 0 video-feature tokens, 0 Kitten tokens across .kt/.kts; jniLibs dir absent"],
    ["Pocket TTS standalone binary synthesizes a 10,000-word chapter on the reference laptop", "Synthesis >= 2x real time (main or small variant)", "**PASS — 7.3x real time**", "10,010 words -> 47.5 min audio in 6.5 min wall time; pocket-tts 4.0.1, english_2m_2026-10 variant, CPU-only (scripts/p0_pocket_tts_spike.py)"],
  ],
  [34, 22, 10, 34]
));
body.push(lib.p(
  "The spike also validated the exact CLI contract the process bridge wraps: pocket-tts generate --text T --voice V --output-path O, and export-voice AUDIO_PATH EXPORT_PATH for narrator cloning. Both are wired into PocketTtsBridge verbatim."
));

body.push(lib.h1("3. What Was Implemented"));
body.push(lib.h2("3.1 SCR-2 — Scene Automation & Video removed"));
body.push(...lib.bullets([
  "Deleted SceneAutomationScreen.kt (555 lines) and the 16 video MutableStateFlow fields in StudioViewModel (videoImages, focal points, Ken Burns, resolution/production mode, aspect ratio, equalizer styles, SRT export, selected track, logs, progress and companions).",
  "Deleted generateVideo() (~970 lines: ffmpeg muxing, zoompan/focal filters, equalizer overlays, split/merge logic), the video-cancel path, and the SRT sidecar writer.",
  "Removed the video file-dialog shims from the port surface; RxFFmpegInvoke remains for the audio mastering chain only (its seven audio call sites are untouched).",
  "MediaStore Downloads shim retained for EPUB/audio exports; video-only usages left with the deleted path.",
]));
body.push(lib.h2("3.2 SCR-3 — Kitten TTS replaced by Pocket TTS"));
body.push(...lib.bullets([
  "KittenTtsManager (shared HF Space queue client) and LocalKittenTtsManager deleted.",
  "New PocketTtsBridge: locates the pocket-tts binary (bundled dir / POCKET_TTS_BIN / PATH / ~/.authr), manages safetensors voice packs in <data>/audio/voice_packs, invokes generate and export-voice through the process bridge, and reports status/error flows.",
  "New PocketTtsManager with the same call surface the engine used: initialize, synthesizeToFile, speakParagraphSuspend, stopSpeaking, playAudioFile, plus cloneNarratorVoice for author-voice cloning. Degrades gracefully to Edge TTS with a logged route marker when the binary or pack is absent.",
  "Engine voice slots re-seeded as Pocket narrator packs (soft/clear/warm/deep/brisk/grave + author-clone); Settings and Global Release engine pickers now offer Pocket TTS with accurate capability copy.",
]));
body.push(lib.h2("3.3 The .authr container (schema v2, plan §10)"));
body.push(...lib.bullets([
  "Full Table 29 layout: manifest.json (schema version, format, checksums, feature flags), manuscript/ (per-chapter JSON payloads + human-readable canon of confirmed text only), bibles/, lorebook/, ledger/ (chekhov.json, questions.json, timeline.json), structure/ (outline, beats, arcs), audio/ (pronunciation rules + voice-pack/segment/master dirs), plugins/, exports/, metadata/ (lossless book.json record).",
  "Atomic save (zip to .part sibling + rename), backup-before-migration for any container older than schema v2, additive v1->v2 migration that creates ledger/structure/plugins/exports without rewriting payloads, and a sha256 checksum audit on open that reports — never silently ignores — mismatches.",
]));
body.push(lib.h2("3.4 Room schema v15 (schema v2, plan Table 32 + Appendix A)"));
body.push(...lib.bullets([
  "books: freeTierChapters (5), wordMin (1000), wordMax (2000), bufferTarget (8), exportProfile (royal_road).",
  "chapters: writerConfirmed + draftStatus (PROPOSED/CONFIRMED/ARCHIVED — the writer-sovereignty state), arcId, endingType, beatCoverage, balanceShares, worksheet, qcScores, qcTotal, hookCheck, ctaNote, pollJson.",
  "New tables: serial_arcs, chekhov_guns, questions, plugins — with DAOs (SerialArcDao, ChekhovGunDao incl. final-act unresolved-gate query, QuestionDao incl. overdue-progressive query, PluginDao) and additive MIGRATION_14_15 only; legacy chapters default to confirmed canon.",
  "Migration defaults validated against Room 2.8.5's TableInfo.equalsCommon semantics (entity-declared defaults are enforced, database-side defaults tolerated) — no destructive fallback on the v14->v15 path.",
]));
body.push(lib.h2("3.5 Plugin scaffold (plan §8)"));
body.push(...lib.bullets([
  "PluginSchema: the complete persona contract (prompt_directive, sentence_length, sensory_focus, pacing_curve, cliffhanger_lean, banned_patterns) and structure contract, with semver/id validation.",
  "PluginRegistry: scaffolds the shipped library into the container, loads and validates every JSON before registration, reports invalid dropped-in files, never overwrites user edits.",
  "Shipped library as versioned data: 16 plot structures (14 classics + Micro-Arc Cascade + Serial Seven-Beat) and 22 author personas (7 built-ins + 15 new), exactly per Tables 26-27.",
]));
body.push(lib.h2("3.6 Writer-sovereignty engine contract (P1 head start)"));
body.push(...lib.bullets([
  "engine/DraftState.kt: the six-layer map (L1-L6 with owning agents) and the DraftState constants; WriterGate implements isCanon/canonChapters/proposedChapters/violations — the P1 exit-gate assertion (no unconfirmed text reaches any ledger or export) is executable now and covered by WriterGateTest (3/3 green).",
]));

body.push(lib.h1("4. Build-Repair Work (pre-existing debt)"));
body.push(lib.p(
  "The committed desktop port did not compile: 4,050 errors, no Main.kt entry point, a repositories block that conflicted with settings-level FAIL_ON_PROJECT_REPOS, and a toolchain pinned to a JRE without javac. All were repaired during P0 so the gates could run. The principal repairs: Material 3 + icons wired through the compose plugin DSL (compose.desktop.currentOs does not include material3); kotlinx-serialization compiler plugin added; @ConstructedBy removed in favour of Room's documented JVM reflection path; the WorkManager/Data/Result/Continuation shim rewritten (public success/failure/retry factories, vararg syntax, builder fixes); migrations rewritten to Room 2.8.5's SQLiteConnection KMP API (execSQL/usePrepared/useWriterConnection); Context/Intent/Uri/Bundle/Paint/MediaPlayer/StaticLayout/MediaMetadataRetriever shim defects fixed; Theme.kt dynamic-color and status-bar code replaced with the desktop palette path; StorageCleanupWorker moved to the KMP connection API; AndroidViewModel/Paint platform clashes resolved; android.R, AudioEncoder (ffmpeg-based), and the com.example.MainKt entry point added. Final state: compileKotlin green, 14/14 tests green, 188 MB createDistributable app image builds and launches (verified under Xvfb to full UI initialization)."
));

body.push(lib.h1("5. Test Results"));
body.push(...lib.tbl(
  "Table 2. Unit test suites and results (desktop:test)",
  ["Suite", "Tests", "Focus", "Result"],
  [
    ["AuthrContainerRoundTripTest", "5", "Lossless round-trip; atomic save; v1 migration + backup; checksum audit; non-container rejection", "5/5 green"],
    ["PluginRegistryTest", "6", "16+22 shipped library; schema validation; scaffold idempotence; invalid-file reporting", "6/6 green"],
    ["WriterGateTest", "3", "PROPOSED never canon; review queue; zero unconfirmed text to consumers", "3/3 green"],
  ],
  [30, 10, 42, 18]
));

body.push(lib.h1("6. Handoff to P1"));
body.push(lib.p(
  "P1 (Writer Engine Core) begins from a compiles-and-tests green baseline. Already in place for it: the writer_confirmed/draftStatus columns and their migration, the WriterGate assertion suite, the six-layer map, and the Writer Studio data contract carried by the container's manuscript payloads. P1 scope per the plan: wire the six-layer engine with confirmation gates end to end, the Writer Studio surface over PROPOSED drafts, and the Live AST Annotator on confirmed text; exit gate is the five-chapter novella run with the seeded-project sovereignty assertions."
));

// ── Document assembly ──────────────────────────────────────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Implementation Report — Phase P0",
  subtitle: "Foundation & Clean-up: SCR-2/SCR-3 removals, .authr container schema v2, serialization data model, plugin scaffold, Pocket TTS spike",
  englishLabel: "PHASE REPORT · P0",
  metaLines: [
    "Plan: Authr OS 3.0 Improvement Plan, Revision 2 (32 person-weeks, P0-P5)",
    "Status: P0 complete — all three exit gates green",
    "Date: November 9, 2026 · Baseline: authr-desktop @ P0 commit",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential — Internal Engineering Document",
  palette: P.cover,
};

const doc = new Document({
  creator: "Authr OS Product Team",
  title: "Authr OS 3.0 — P0 Implementation Report",
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
    { // Section 1: cover — margin 0 (R1 full-page table), no header/footer
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
