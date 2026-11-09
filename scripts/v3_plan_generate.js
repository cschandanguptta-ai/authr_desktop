// Authr OS 3.0 Improvement Plan (Rev 2) — document assembly
"use strict";
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Header, Footer, PageNumber,
  AlignmentType, NumberFormat, SectionType, TableOfContents,
} = require("docx");

const lib = require("./v3_plan_lib");
const a = require("./v3_plan_content_a");
const b = require("./v3_plan_content_b");
const c = require("./v3_plan_content_c");
const d = require("./v3_plan_content_d");

const OUT = "/home/z/my-project/download/Authr-OS-3.0-Improvement-Plan-Rev2.docx";
const P = lib.P;
const FONT_BODY = lib.FONT_BODY;
const FONT_HEAD = lib.FONT_HEAD;

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

const bodyHeader = new Header({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { line: 240 },
    children: [new TextRun({ text: "Authr OS 3.0 Improvement Plan — Rev 2", size: 18, color: "808080", font: FONT_BODY })],
  })],
});

// ── Cover (Recipe R1, GO-1, long-title override from R4) ─────────────────────
const coverConfig = {
  title: "Authr OS 3.0 Improvement Plan",
  subtitle: "Web Novel Serialization, Studio Audiobooks, and Publish-Ready Exports",
  englishLabel: "PRODUCT PLAN \u00b7 REV 2",
  metaLines: [
    "Product: Authr OS v2.3 to v3.0 (Android + Compose Desktop)",
    "Scope: Rev 2 corrections SCR-1 to SCR-4; roadmap P0-P5, 32 person-weeks",
    "Spec inputs: Serialized Fiction Blueprint 2.0; Pocket TTS (Kyutai)",
    "Date: November 9, 2026 \u00b7 Status: For approval",
  ],
  footerLeft: "Authr OS Product Team",
  footerRight: "Confidential \u2014 Internal Planning Document",
  palette: P.cover,
};

// ── Front matter: TOC ────────────────────────────────────────────────────────
const tocChildren = [
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 480, after: 360, line: 312 },
    children: [new TextRun({ text: "Table of Contents", bold: true, size: 32, font: FONT_HEAD, color: P.ink })],
  }),
  new TableOfContents("Table of Contents", {
    hyperlink: true,
    headingStyleRange: "1-2",
  }),
  new Paragraph({
    spacing: { before: 200, line: 312 },
    children: [new TextRun({
      text: "Note: this table of contents is generated via field codes. After editing the document, right-click the table and choose \u201cUpdate Field\u201d to refresh page numbers.",
      italics: true, size: 18, color: "888888", font: FONT_BODY,
    })],
  }),
];

// ── Body ─────────────────────────────────────────────────────────────────────
const bodyChildren = [
  ...a.build(),
  ...b.build(),
  ...c.build(),
  ...d.build(),
];

const doc = new Document({
  features: { updateFields: true },
  styles: {
    default: {
      document: {
        run: { font: FONT_BODY, size: 24, color: "000000" },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: FONT_HEAD, size: 32, bold: true, color: P.ink },
        paragraph: { spacing: { before: 360, after: 160, line: 312 } },
      },
      heading2: {
        run: { font: FONT_HEAD, size: 28, bold: true, color: P.ink },
        paragraph: { spacing: { before: 240, after: 120, line: 312 } },
      },
      heading3: {
        run: { font: FONT_HEAD, size: 24, bold: true, color: P.ink },
        paragraph: { spacing: { before: 200, after: 100, line: 312 } },
      },
    },
  },
  sections: [
    { // Section 1: cover — margin 0, no header/footer, no page numbers
      properties: {
        page: { size: pgSize, margin: { top: 0, bottom: 0, left: 0, right: 0 } },
      },
      children: lib.buildCoverR1(coverConfig),
    },
    { // Section 2: front matter (TOC) — Roman numerals
      properties: {
        type: SectionType.NEXT_PAGE,
        page: {
          size: pgSize, margin: pgMargin,
          pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN },
        },
      },
      footers: { default: pageNumFooter() },
      children: tocChildren,
    },
    { // Section 3: body — Arabic numerals from 1
      properties: {
        type: SectionType.NEXT_PAGE,
        page: {
          size: pgSize, margin: pgMargin,
          pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL },
        },
      },
      headers: { default: bodyHeader },
      footers: { default: pageNumFooter() },
      children: bodyChildren,
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT, buf);
  console.log("written:", OUT, buf.length, "bytes");
}).catch((err) => {
  console.error("FAILED:", err);
  process.exit(1);
});
