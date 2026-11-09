// Shared builders for Authr OS 3.0 Improvement Plan (Rev 2)
// Palette: GO-1 Graphite Orange (proposal/PRD), Recipe R1 (long-title override)
"use strict";
const fs = require("fs");
const path = require("path");
const {
  Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType,
  TableLayoutType, PageBreak,
} = require("docx");

// ── Palette GO-1 (Graphite Orange) ──────────────────────────────────────────
const P = {
  ink: "1A2330",            // headings / cover bg
  body: "000000",           // body text (Profile A: pure black)
  secondary: "5A6472",      // captions
  accent: "D4875A",         // table headers, accent lines
  innerLine: "DDD0C8",
  surface: "F8F0EB",
  cover: {
    bg: "1A2330", titleColor: "FFFFFF", subtitleColor: "B0B8C0",
    metaColor: "90989F", footerColor: "687078", accent: "D4875A",
  },
};

const FONT_BODY = { ascii: "Times New Roman", eastAsia: "SimSun" };
const FONT_HEAD = { ascii: "Times New Roman", eastAsia: "SimHei" };
const FONT_MONO = { ascii: "Consolas", eastAsia: "SimSun" };

const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { top: NB, bottom: NB, left: NB, right: NB,
                       insideHorizontal: NB, insideVertical: NB };

// ── Rich text: **bold** segments ────────────────────────────────────────────
function rich(text, opts = {}) {
  const runs = [];
  const parts = String(text).split("**");
  for (let i = 0; i < parts.length; i++) {
    if (parts[i] === "") continue;
    runs.push(new TextRun({
      text: parts[i],
      bold: i % 2 === 1 ? true : (opts.bold || false),
      italics: opts.italics || false,
      size: opts.size || 24,
      color: opts.color || P.body,
      font: opts.font || FONT_BODY,
    }));
  }
  return runs;
}

// ── Headings ────────────────────────────────────────────────────────────────
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 160, line: 312 },
    children: [new TextRun({ text, bold: true, size: 32, color: P.ink, font: FONT_HEAD })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 120, line: 312 },
    children: [new TextRun({ text, bold: true, size: 28, color: P.ink, font: FONT_HEAD })],
  });
}

// ── Body paragraph (English: left aligned, no first-line indent) ───────────
function p(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { after: opts.after !== undefined ? opts.after : 120, line: 312 },
    keepNext: opts.keepNext || false,
    children: rich(text, opts),
  });
}

// ── Bullet list (used sparingly) ────────────────────────────────────────────
function bullets(items) {
  return items.map(t => new Paragraph({
    bullet: { level: 0 },
    alignment: AlignmentType.LEFT,
    spacing: { after: 60, line: 312 },
    children: rich(t),
  }));
}

// ── Counters for tables and figures ────────────────────────────────────────
const counters = { table: 0, figure: 0 };

// ── Table builder (horizontal-only business style, GO-1) ───────────────────
// headers: string[]; rows: string[][]; widths: number[] (percentages, sums 100)
function tbl(caption, headers, rows, widths) {
  counters.table += 1;
  const n = counters.table;
  const capText = caption ? `Table ${n} — ${caption}` : `Table ${n}`;
  const capPara = new Paragraph({
    keepNext: true,
    alignment: AlignmentType.LEFT,
    spacing: { before: 160, after: 80, line: 312 },
    children: [
      new TextRun({ text: `Table ${n}. `, bold: true, size: 21, color: P.ink, font: FONT_BODY }),
      new TextRun({ text: caption, size: 21, color: P.ink, font: FONT_BODY }),
    ],
  });
  const colWidths = widths || headers.map(() => Math.floor(100 / headers.length));

  const headerRow = new TableRow({
    tableHeader: true,
    cantSplit: true,
    children: headers.map((text, i) => new TableCell({
      width: { size: colWidths[i], type: WidthType.PERCENTAGE },
      shading: { type: ShadingType.CLEAR, fill: P.accent },
      margins: { top: 60, bottom: 60, left: 120, right: 120 },
      children: [new Paragraph({
        alignment: AlignmentType.LEFT,
        spacing: { line: 276 },
        children: rich(text, { bold: true, size: 20, color: "FFFFFF" }),
      })],
    })),
  });

  const dataRows = rows.map(cells => new TableRow({
    cantSplit: true,
    children: cells.map((text, i) => new TableCell({
      width: { size: colWidths[i], type: WidthType.PERCENTAGE },
      margins: { top: 60, bottom: 60, left: 120, right: 120 },
      children: [new Paragraph({
        alignment: AlignmentType.LEFT,
        spacing: { line: 276 },
        children: rich(text, { size: 20 }),
      })],
    })),
  }));

  const table = new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: P.accent },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: P.accent },
      left: NB, right: NB,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: P.innerLine },
      insideVertical: NB,
    },
    rows: [headerRow, ...dataRows],
  });
  return [capPara, table, new Paragraph({ spacing: { after: 80, line: 312 }, children: [] })];
}

// ── Figure builder (reads PNG size from IHDR header) ───────────────────────
function pngDims(buf) {
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}
function figure(pngPath, caption, displayWidth = 480) {
  counters.figure += 1;
  const n = counters.figure;
  const buf = fs.readFileSync(pngPath);
  const dims = pngDims(buf);
  const displayHeight = Math.round(displayWidth * (dims.height / dims.width));
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      keepNext: true,
      spacing: { before: 160, after: 60, line: 312 },
      children: [new ImageRun({
        data: buf,
        transformation: { width: displayWidth, height: displayHeight },
        type: "png",
      })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 160, line: 276 },
      children: [
        new TextRun({ text: `Figure ${n}. `, bold: true, size: 21, color: P.secondary, font: FONT_BODY }),
        new TextRun({ text: caption, size: 21, color: P.secondary, font: FONT_BODY }),
      ],
    }),
  ];
}

// ── Code block (shaded paragraphs, monospace) ──────────────────────────────
function codeBlock(lines) {
  return lines.map((line, i) => new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: i === 0 ? 120 : 0, after: i === lines.length - 1 ? 160 : 0, line: 276 },
    shading: { type: ShadingType.CLEAR, fill: P.surface },
    border: {
      left: { style: BorderStyle.SINGLE, size: 12, color: P.accent, space: 8 },
    },
    indent: { left: 240 },
    children: [new TextRun({ text: line === "" ? " " : line, size: 18, color: "3A3A3A", font: FONT_MONO })],
  }));
}

// ── Cover: Recipe R1 (English title layout variant) ────────────────────────
function splitTitleLinesEN(title, charsPerLine) {
  const words = title.split(" ");
  const lines = [];
  let cur = "";
  for (const w of words) {
    const candidate = cur === "" ? w : cur + " " + w;
    if (candidate.length <= charsPerLine || cur === "") {
      cur = candidate;
    } else {
      lines.push(cur);
      cur = w;
    }
  }
  if (cur) lines.push(cur);
  // orphan prevention: merge a very short last line
  if (lines.length > 1 && lines[lines.length - 1].length <= 4) {
    const last = lines.pop();
    lines[lines.length - 1] += " " + last;
  }
  return lines;
}

function calcTitleLayoutEN(title, maxWidthTwips, preferredPt = 40, minPt = 24) {
  const charWidth = (pt) => pt * 11; // avg English glyph ~0.55 em
  let titlePt = preferredPt;
  let lines;
  while (titlePt >= minPt) {
    const cpl = Math.floor(maxWidthTwips / charWidth(titlePt));
    if (cpl < 8) { titlePt -= 2; continue; }
    lines = splitTitleLinesEN(title, cpl);
    if (lines.length <= 3) break;
    titlePt -= 2;
  }
  if (!lines || lines.length > 3) {
    lines = splitTitleLinesEN(title, Math.floor(maxWidthTwips / charWidth(minPt)));
    titlePt = minPt;
  }
  return { titlePt, titleLines: lines };
}

function calcCoverSpacing(params) {
  const {
    titleLineCount = 1, titlePt = 36, hasSubtitle = false,
    hasEnglishLabel = false, metaLineCount = 0,
    fixedHeight = 800, pageHeight = 16838,
    marginTop = 0, marginBottom = 0,
  } = params;
  const SAFETY = 1200;
  const usableHeight = pageHeight - marginTop - marginBottom - SAFETY;
  const titleHeight = titleLineCount * (titlePt * 23 + 200);
  const subtitleHeight = hasSubtitle ? (12 * 23 + 600) : 0;
  const englishLabelHeight = hasEnglishLabel ? (9 * 23 + 600) : 0;
  const metaHeight = metaLineCount * (10 * 23 + 100);
  const implicitParaHeight = 3 * 300;
  const contentHeight = titleHeight + subtitleHeight + englishLabelHeight +
                        metaHeight + fixedHeight + implicitParaHeight;
  const remainingSpace = usableHeight - contentHeight;
  const safeRemaining = Math.max(remainingSpace, 400);
  const FOOTER_MIN = 800;
  const rawTop = Math.floor(safeRemaining * 0.45);
  const rawBottom = Math.floor(safeRemaining * 0.45);
  const bottomSpacing = Math.max(rawBottom, FOOTER_MIN);
  const topSpacing = Math.max(rawTop - Math.max(0, FOOTER_MIN - rawBottom), 400);
  const midSpacing = Math.max(safeRemaining - topSpacing - bottomSpacing, 0);
  return { topSpacing, midSpacing, bottomSpacing };
}

function buildCoverR1(config) {
  const C = config.palette;
  const padL = 1200, padR = 800;
  const availableWidth = 11906 - padL - padR - 300;
  const { titlePt, titleLines } = calcTitleLayoutEN(config.title, availableWidth, 40, 24);
  const titleSize = titlePt * 2;
  const spacing = calcCoverSpacing({
    titleLineCount: titleLines.length, titlePt,
    hasSubtitle: !!config.subtitle, hasEnglishLabel: !!config.englishLabel,
    metaLineCount: (config.metaLines || []).length,
    fixedHeight: 400,
  });
  const accentLeft = { style: BorderStyle.SINGLE, size: 8, color: C.accent, space: 12 };
  const children = [];

  children.push(new Paragraph({ spacing: { before: spacing.topSpacing } }));

  if (config.englishLabel) {
    children.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 500 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: C.accent, space: 8 } },
      children: [new TextRun({ text: config.englishLabel.split("").join("  "),
        size: 18, color: C.accent, font: { ascii: "Calibri", eastAsia: "SimHei" }, characterSpacing: 40 })],
    }));
  }

  for (let i = 0; i < titleLines.length; i++) {
    children.push(new Paragraph({
      indent: { left: padL },
      spacing: { after: i < titleLines.length - 1 ? 100 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({ text: titleLines[i], size: titleSize, bold: true,
        color: C.titleColor, font: { eastAsia: "SimHei", ascii: "Arial" } })],
    }));
  }

  if (config.subtitle) {
    children.push(new Paragraph({
      indent: { left: padL }, spacing: { after: 800 },
      children: [new TextRun({ text: config.subtitle, size: 24, color: C.subtitleColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }

  for (const line of (config.metaLines || [])) {
    children.push(new Paragraph({
      indent: { left: padL + 200 }, spacing: { after: 80 },
      border: { left: accentLeft },
      children: [new TextRun({ text: line, size: 24, color: C.metaColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }

  children.push(new Paragraph({ spacing: { before: spacing.bottomSpacing } }));

  children.push(new Paragraph({
    indent: { left: padL, right: padR },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: C.accent, space: 8 } },
    spacing: { before: 200 },
    children: [
      new TextRun({ text: config.footerLeft || "", size: 16, color: C.footerColor, font: { ascii: "Arial" } }),
      new TextRun({ text: "                                        " }),
      new TextRun({ text: config.footerRight || "", size: 16, color: C.footerColor, font: { ascii: "Arial" } }),
    ],
  }));

  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: C.bg }, borders: noBorders,
        verticalAlign: "top",
        children,
      })],
    })],
  })];
}

module.exports = {
  P, FONT_BODY, FONT_HEAD, FONT_MONO, NB, noBorders, allNoBorders,
  rich, h1, h2, p, bullets, tbl, figure, codeBlock, counters,
  buildCoverR1, calcTitleLayoutEN, calcCoverSpacing,
};
