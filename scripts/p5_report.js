// Authr OS 3.0 — P5 Implementation Report (phase deliverable)
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

const OUT = "/home/z/my-project/download/Authr-OS-3.0-P5-Implementation-Report.docx";

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
        text: "Authr OS 3.0 · P5 Implementation Report · November 11, 2026",
        size: 16, color: P.secondary, font: FONT_BODY,
      })],
    })],
  });
}

// ── Content ────────────────────────────────────────────────────────────────
const body = [];

body.push(lib.h1("1. Executive Summary"));
body.push(lib.p(
  "Phase **P5 — Publishing is complete with its exit gate green, closing the Rev 2 program: all six phases (P0–P5) are now implemented with green exit gates**. The publishing pillar (plan §11) takes a confirmed manuscript to retail-ready artifacts as local, deterministic code: a structured KDP metadata workbench with the seven keywords, categories, per-platform blurb variants, and a pricing simulator that quotes the 70% and 35% royalty brackets against the estimated print cost (§11.2); an EPUB 3 exporter that extends the v2.3 manager's intent as JVM-pure engine code — navigable TOC, semantic chapter markup, embedded cover slot, workbench metadata (§11.1); a print interior rendered to the 6×9 in trim with the inside-margin gutter ladder applied by iterative pagination and mirrored recto/verso margins; volume bundling that graduates a completed serial to retail through the same pipeline (§6.13); and the end-to-end ship report — the final artifact that lists Chekhov waivers, scorecard notes, and cadence streaks beside every gate verdict (§15)."
));
body.push(lib.p(
  "The **exit gate produced the full retail package in one action for two complete projects**, each round-tripped through a real .authr container first: the ten-chapter novel \u201CThe Iron Vale\u201D (single EPUB + print interior + metadata bundle + ship report) and the 15-chapter golden serial (two micro-arc volume EPUBs + print interior + metadata bundle + ship report). **Every EPUB then passed the real external epubcheck 5.2.1 tool with zero errors** — the plan's offer-for-upload precondition — and **all four gutter-ladder bands were exercised and measured compliant** (85, 206, 389, and 571-page synthetic interiors landing at 0.375, 0.5, 0.625, and 0.75 in gutters respectively). The full test suite stands at **209/209 green — the 159 P0–P4 tests unchanged and passing, plus 50 new P5 tests**, including live epubcheck integration runs (zero-errors verified; a deliberately broken EPUB confirmed failures block the clean verdict)."
));
body.push(lib.p(
  "Two authority decisions are recorded as code: the metadata workbench is the authoritative retail-metadata source, so the v2.3 compiled-manuscript ISBN placeholder no longer reaches retail exports (open question OQ-9 resolved); and epubcheck is an external, blocking gate — a package built on a machine without the tool is honestly marked UNVERIFIED and NOT clean, never silently green (ADR-0011, same measured-not-estimated discipline as ADR-0010). The KDP Print Previewer pass is disclosed as an upload-time, Amazon-side step (open question OQ-15); its measurable contract — trim size and the ladder — is verified locally at every band."
));

body.push(lib.h1("2. P5 Exit Gate (plan §13, Table 33)"));
body.push(...lib.tbl(
  "P5 exit-gate evidence (plan §13, Table 33, P5 row; gate run 2026-11-11)",
  ["Exit gate", "Verdict", "Measured evidence"],
  [
    ["epubcheck zero errors",
     "PASS",
     "epubcheck 5.2.1 (real tool) on the novel EPUB and both serial volume EPUBs: \u201CMessages: 0 fatals / 0 errors / 0 warnings / 0 infos\u201D each; a deliberately broken EPUB (dropped spine file) was confirmed to turn the verdict FAILED and the package NOT clean"],
    ["KDP Print Previewer pass (gutter ladder)",
     "PASS (measured contract)",
     "6×9 in trim (432×648 pt MediaBox) on every page; gutter ladder per plan §11.1 verified on all four bands by synthetic probes — 85→0.375 in, 206→0.5 in, 389→0.625 in, 571→0.75 in; pagination converged with mirrored recto/verso margins. The Previewer run itself is an upload-time step (OQ-15)"],
    ["A complete .authr project produces the full retail package in one action",
     "PASS",
     "Both projects exported to real .authr containers (checksums audited, structured workbench + arc ids preserved through the round-trip), then ONE RetailPackager.packageRetail call each emitted epub/ + print/interior.pdf + metadata.json + ship-report.md"],
    ["Ship report clean with disclosures",
     "PASS",
     "Both ship reports verdict CLEAN with process disclosures (canon exclusions, workbench derivation), Chekhov waivers with mandatory notes, pricing record for both royalty brackets, and the external-gate standing section"],
  ],
  [24, 14, 62]
));
body.push(lib.p(
  "Gate artifacts: `download/Authr-OS-3.0-P5-Retail-Package/` (novel/, serial/, probes/, gate-report.md) and the full run log `download/Authr-OS-3.0-P5-Retail-Package.gate-run.log`."
));

body.push(lib.h1("3. What Was Built (plan §11)"));
body.push(...lib.tbl(
  "P5 deliverables mapped to plan sections",
  ["Work item", "Engine surface", "What it does"],
  [
    ["Metadata workbench (§11.2)",
     "engine.publishing/PublishingMetadata.kt + WorkbenchStore",
     "KDP fields as a first-class editing surface: title, subtitle, the seven keywords, description/blurb, categories, pricing, per-platform blurb variants stored beside the KDP canonical; container entry `metadata/kdp_workbench.json` upgraded from the v2.3 string stub to a structured, schema-marked form that older containers still open (openers migrate forward, never downgrade)"],
    ["Pricing simulator (§11.2)",
     "RoyaltySimulator",
     "70% bracket (band $2.99–$12.99 on Amazon.com, per-MB delivery with a $0.01 floor) vs 35% bracket (any price, no delivery subtraction); print cost $0.85 + $0.012×pages at 60% royalty with the breakeven list price — constants pinned to KDP's published help pages and cited in code"],
    ["EPUB 3 exporter (§11.1)",
     "engine.publishing/Epub3Exporter.kt",
     "OCF zip with mimetype first and STORED, container.xml, package document carrying identifier (ISBN or UUID), title, language, creator, keywords as dc:subject, the required dcterms:modified, and series collection metadata; nav.xhtml in manifest and spine; semantic chapter XHTML with XML-escaped paragraphs; cover slot with cover-image properties"],
    ["Print interior (§11.1)",
     "engine.publishing/PrintInterior.kt",
     "PDFBox render to 6×9 in: title page, copyright page (workbench ISBN), contents, chapters with first-line indents and centered folios; gutter ladder (24–150→0.375 in, 151–300→0.5 in, 301–500→0.625 in, 501–700→0.75 in) applied by iterative pagination; mirrored recto/verso margins; embedded subset TTF serif (DejaVu default, path parameter for CJK faces)"],
    ["Volume bundling (§13, §6.13)",
     "engine.publishing/VolumeBundler.kt",
     "Serial canon chapters group per micro-arc (the natural retail unit — each arc closes its question), with a size-based fallback; every volume exports through the same EPUB 3 pipeline so the epubcheck gate applies per volume; novels are a single volume"],
    ["Ship report (§15)",
     "engine.publishing/ShipReport.kt",
     "The final artifact: artifacts table, gate-verdict table, package verdict (\u201Cclean — the export behind this report is defensible\u201D), process disclosures, Chekhov waivers with their mandatory notes, scorecard notes, cadence/buffer summary, pricing record, and the external-gates standing section"],
    ["One-action packager (§13)",
     "engine.publishing/RetailPackager.kt + EpubCheckLocator",
     "Writer gate → workbench resolution → EPUB(s) → epubcheck (external, blocking; verdict parsed from the tool's own Messages summary) → print interior → pricing → metadata.json + ship-report.md; UNVERIFIED ≠ clean, nothing silently green"],
    ["Container changes (§10, additive)",
     "container/AuthrContainer.kt",
     "`ProjectSnapshot.publishing` carries the structured workbench through export/import; `ArcPayload.id` added (default 0) so chapters' arcId joins survive round-trips; legacy string stub still written when no workbench exists — schema stays v2, P0 losslessness suite unchanged and green"],
  ],
  [16, 24, 60]
));

body.push(lib.h1("4. Exit-Gate Evidence"));
body.push(lib.h2("4.1 The two projects"));
body.push(...lib.tbl(
  "Retail packages produced in one action each (gate run 2026-11-11)",
  ["Project", "Container", "Package contents", "epubcheck", "Print"],
  [
    ["Novel — \u201CThe Iron Vale\u201D (10 confirmed chapters)",
     "The_Iron_Vale.authr, 17.3 KB, schema v2, 0 checksum warnings",
     "The_Iron_Vale.epub (10 chapters, 10.4 KB) + print/interior.pdf (13 pages, 0.375 in gutter) + metadata.json + ship-report.md",
     "0 fatals / 0 errors / 0 warnings",
     "compliant, 3 pagination passes"],
    ["Serial — \u201CThe Estate of Fallingwater Dike\u201D (15 canon chapters, completed)",
     "The_Estate_of_Fallingwater_Dike.authr, 73.1 KB, schema v2, 0 checksum warnings",
     "volume_01.epub + volume_02.epub (per micro-arc) + print/interior.pdf (63 pages, 0.375 in gutter) + metadata.json + ship-report.md",
     "0 fatals / 0 errors / 0 warnings (each volume)",
     "compliant"],
  ],
  [22, 22, 34, 12, 10]
));
body.push(lib.h2("4.2 Gutter-ladder band probes (plan §11.1 table)"));
body.push(...lib.tbl(
  "All four ladder bands exercised with synthetic interiors (self-calibrating probes)",
  ["Band", "Probe page count", "Measured gutter", "Expected gutter", "Verdict"],
  [
    ["24–150 pages", "85", "0.375 in", "0.375 in", "GREEN"],
    ["151–300 pages", "206", "0.5 in", "0.5 in", "GREEN"],
    ["301–500 pages", "389", "0.625 in", "0.625 in", "GREEN"],
    ["501–700 pages", "571", "0.75 in", "0.75 in", "GREEN"],
  ],
  [22, 20, 20, 20, 18]
));
body.push(lib.h2("4.3 The external gate, verified honestly"));
body.push(lib.p(
  "epubcheck 5.2.1 ran as the real external tool against every exported EPUB. The verdict parser reads the tool's own summary line rather than its exit code — a measured behavior: this version returns exit 1 for errors, so exit-code logic would have mislabeled the broken-EPUB case. That broken EPUB (a dropped spine file) was packaged and run through the same gate to prove failures block: verdict FAILED, package NOT clean, report says so. On a machine without epubcheck, the packager still produces artifacts but marks the verdict UNVERIFIED and the package NOT clean — the zero-errors precondition is unproven, and the report says exactly why (ADR-0011)."
));

body.push(lib.h1("5. Test Suite (209/209 green)"));
body.push(...lib.tbl(
  "New P5 test classes (50 tests; the 159 P0–P4 tests unchanged and passing)",
  ["Test class", "Tests", "Guards"],
  [
    ["PublishingMetadataTest", "13", "workbench caps (7 keywords / 3 categories), fromBook seeding, royalty bands and boundaries, per-MB delivery rounding, print cost/royalty/breakeven, workbench JSON round-trip + legacy string-envelope compatibility"],
    ["Epub3ExporterTest", "9", "mimetype first+STORED, package metadata, nav/chapter links, spine, XML escaping, cover slot, manifest-href resolution, collection metadata"],
    ["PrintInteriorTest", "7", "gutter ladder table exactness, 6×9 in MediaBox, ladder-compliant gutters, pagination convergence, embedded-font text extraction"],
    ["VolumeBundlerTest", "5", "arc grouping with reading order, size fallback, shared-exporter volume EPUBs"],
    ["ShipReportTest", "7", "clean ⟺ every verdict passed (UNVERIFIED ≠ clean), waiver disclosure with mandatory notes, report sections"],
    ["RetailPackagerTest", "9", "one-action package (novel + serial), canon exclusions disclosed, workbench authority, pricing record; live epubcheck 5.2.1 — zero errors VERIFIED, broken EPUB FAILED blocks clean"],
  ],
  [22, 8, 70]
));
body.push(lib.p(
  "The suite ran through the direct kotlinc + JUnitCore route (`scripts/p5_direct_test.sh`) because Gradle dependency resolution remains rate-limited in this sandbox; the P5 route adds the kotlinx-serialization compiler plugin jar (the container/workbench code needs it), pdfbox/fontbox, and the epubcheck dist. `AuthrContainerRoundTripTest` ran inside the same suite as the container-change guard. The Gradle task remains the primary command; desktop/build.gradle.kts gains the pdfbox dependency when D-15 wires the UI (recorded in tech-debt)."
));

body.push(lib.h1("6. Docs, ADR, and Handoff"));
body.push(lib.p(
  "**ADR-0011** records the three decisions: one-action packaging order, epubcheck as a blocking external gate with summary-line verdict parsing, and the workbench's metadata authority. The maintenance docs were updated in the same commit: `system-map.md` (six new engine/publishing rows + EpubCheckLocator), `change-impact.md` (publishing blast radius: the writer gate inside packageRetail, the summary-line parser, the linear nav, the additive workbench/arc-id contracts, the pinned ladder), `testing.md` (209-test inventory + the P5 verification recipe), `config-and-env.md` (EPUBCHECK_JAR / EPUBCHECK_HOME), `tech-debt.md` (D-15 publishing engine is engine-only — workbench UI, pdfbox dependency, and the packaging call site are the next slice; D-16 print fonts default to sandbox paths), and `open-questions.md` (OQ-9 resolved; OQ-15 Previewer ownership; OQ-16 epubcheck distribution story)."
));
body.push(lib.p(
  "**Handoff for the next slice (D-15):** a Publishing workbench screen (workbench editor → one-action package button → ship-report viewer), the pdfbox dependency in `build.gradle.kts`, and the packaging call site wired from the UI; a ~30-line integration is enough because the entire engine path is gate-proven. Bundling the epubcheck dist and the DejaVu serif TTFs with the installer closes OQ-16 and D-16 respectively."
));

body.push(lib.h1("7. Program Completion (P0–P5)"));
body.push(...lib.tbl(
  "All six phases green (plan §13 Table 33; reports P0–P5 in download/)",
  ["Phase", "Scope", "Exit gate (all green)"],
  [
    ["P0 — Foundation & Clean-up", "SCR-2/SCR-3 removals, .authr schema v2, Room v15, plugin scaffold, Pocket TTS bridge + spike", "v2.3 round-trip lossless; zero video/Kitten references; Pocket TTS 7.3× real time (≥2×)"],
    ["P1 — Writer Engine Core", "Six-layer contract, Live AST Annotator, Writer Studio, PROPOSED/CONFIRMED/ARCHIVED, canon-gated pipeline", "Five-chapter novella end to end; zero unconfirmed text in ledgers/exports"],
    ["P2 — Prose Quality", "Cliché cleaner (154 patterns), token-bias matrix, Chekhov ledger + final-act recovery gate, question ledger", "Precision 1.0000 on the golden corpus; planted guns 100%; zero false compile blocks"],
    ["P3 — Serialization", "Opening validators + anti-withholding gate, seven-beat model, balance meter, ending advisor, worksheet, scorecard, buffer/cadence, six platform profiles", "15-chapter golden serial 0 warnings; withholding gate fires exactly on the seeded project; scorecard bands correct"],
    ["P4 — Audiobook Engine", "IPA pronunciation pass, continuity contract, three-layer mastering chain, ACX audit, SMIL read-along", "Ten-chapter render ACX all-green; 587.6× real time (gate ≥2×); SMIL timing sane"],
    ["P5 — Publishing", "EPUB 3 exporter, print interior + gutter ladder, metadata workbench, volume bundling, ship report, one-action packager", "epubcheck 5.2.1 zero errors on every EPUB; ladder verified on all four bands; complete .authr → full retail package in one action"],
  ],
  [16, 42, 42]
));
body.push(lib.p(
  "The plan's §15 closing condition — \u201Cevery phase gate and every pillar metric is green\u201D — is met at the engine level with one standing exception, disclosed rather than claimed: the plan's beta-program metric (three beta authors each shipping one serial and one retail title end to end) is a program activity outside engineering scope (OQ-12), and the KDP Print Previewer runs per title at upload time (OQ-15). Both are recorded in docs/open-questions.md with owners wanted."
));

// ── Assembly ───────────────────────────────────────────────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Implementation Report — Phase P5",
  subtitle: "Publishing: EPUB 3 exporter, print interior with gutter ladder, metadata workbench, volume bundling, one-action retail package — the program's final gate green",
  englishLabel: "PHASE REPORT · P5",
  metaLines: [
    "Plan: Authr OS 3.0 Improvement Plan, Revision 2 (32 person-weeks, P0-P5)",
    "Status: P5 complete — exit gate green (209/209 tests; epubcheck 5.2.1 zero errors; all four ladder bands)",
    "Program: P0-P5 all phases implemented and gate-green · Date: November 11, 2026",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential — Internal Engineering Document",
  palette: P.cover,
};

const doc = new Document({
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
