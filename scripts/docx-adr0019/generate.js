// ADR-0018 cross-check analysis report — v3.0 spec docs vs current Kotlin implementation.
// English document: Times New Roman body, R2 whitepaper cover (CM-2), Template A analysis.
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, PageNumber, NumberFormat, AlignmentType, HeadingLevel,
  WidthType, BorderStyle, ShadingType, TableLayoutType, TableOfContents,
  SectionType, PageBreak,
} = require("docx");
const fs = require("fs");

// ---------------------------------------------------------------- palette
// R2 whitepaper, CM-2 Blue Orange (tech/corporate). Table tokens per design-system.
const P = {
  bg: "FEFEFE", primary: "1284BA", accent: "FF862F",
  cover: { titleColor: "1284BA", subtitleColor: "606060", metaColor: "707070", footerColor: "A0A0A0" },
  table: { headerBg: "1284BA", headerText: "FFFFFF", accentLine: "1284BA", innerLine: "D8E4EC", surface: "EDF4F9" },
  body: "000000", secondary: "5A6B7A",
  green: "1E7A3C", amber: "9A6B00", red: "A03030",
};

const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { ...noBorders, insideHorizontal: NB, insideVertical: NB };

function safeText(v, ph) {
  if (v === undefined || v === null || v === "" || String(v) === "NaN" || String(v) === "undefined") return ph || "[n/a]";
  return String(v);
}

// ---------------------------------------------------------------- cover (R2 double-rule frame)
function buildCoverR2(config) {
  const padL = 1400, padR = 1400;
  const thickBorder = { style: BorderStyle.SINGLE, size: 18, color: P.accent, space: 20 };
  const children = [];
  children.push(new Paragraph({
    indent: { left: padL - 400, right: padR - 400 }, spacing: { before: 1200, after: 200 },
    border: { top: thickBorder }, children: [],
  }));
  children.push(new Paragraph({ spacing: { before: 1800 } }));
  if (config.englishLabel) {
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 500 },
      children: [new TextRun({ text: config.englishLabel.split("").join("  "), size: 18, color: P.accent, font: { ascii: "Calibri" }, characterSpacing: 40 })],
    }));
  }
  // English title: estimate width (Latin ~ pt*11 twips/char). 44 chars -> 2 lines at 36pt.
  const titleLines = ["AUTHR OS v3.0 Engine Specifications", "Cross-Check Against the Desktop Implementation"];
  const titlePt = 30;
  for (let i = 0; i < titleLines.length; i++) {
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: i < titleLines.length - 1 ? 80 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({ text: titleLines[i], size: titlePt * 2, bold: true, color: P.cover.titleColor, font: { eastAsia: "SimHei", ascii: "Arial" } })],
    }));
  }
  if (config.subtitle) {
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 400 },
      children: [new TextRun({ text: config.subtitle, size: 24, color: P.cover.subtitleColor, font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }
  children.push(new Paragraph({ spacing: { before: 1200 } }));
  for (const line of (config.metaLines || [])) {
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 100, line: Math.ceil(18 * 23), lineRule: "atLeast" },
      children: [new TextRun({ text: line, size: 36, color: P.cover.metaColor, font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }
  children.push(new Paragraph({ spacing: { before: 2000 } }));
  children.push(new Paragraph({
    alignment: AlignmentType.CENTER, indent: { left: padL - 400, right: padR - 400 }, spacing: { before: 200 },
    border: { bottom: thickBorder },
    children: [new TextRun({ text: config.footerRight || "", size: 18, color: P.cover.footerColor, font: { ascii: "Arial" } })],
  }));
  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({ shading: { type: ShadingType.CLEAR, fill: P.bg }, borders: noBorders, children })],
    })],
  })];
}

// ---------------------------------------------------------------- helpers
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 160, line: 312 },
    children: [new TextRun({ text, bold: true, size: 32, color: P.primary, font: { ascii: "Times New Roman", eastAsia: "SimHei" } })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2, spacing: { before: 240, after: 120, line: 312 },
    children: [new TextRun({ text, bold: true, size: 28, color: P.primary, font: { ascii: "Times New Roman", eastAsia: "SimHei" } })],
  });
}
function body(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.LEFT, spacing: { line: 312, after: 120 },
    children: [new TextRun({ text, size: 24, color: P.body, font: { ascii: "Times New Roman", eastAsia: "SimSun" }, ...opts })],
  });
}
function bodyRuns(runs) {
  return new Paragraph({
    alignment: AlignmentType.LEFT, spacing: { line: 312, after: 120 },
    children: runs.map(r => new TextRun({ size: 24, color: P.body, font: { ascii: "Times New Roman", eastAsia: "SimSun" }, ...r })),
  });
}
function caption(text) {
  return new Paragraph({
    keepNext: true, spacing: { before: 160, after: 80, line: 312 },
    children: [new TextRun({ text, bold: true, size: 21, color: P.secondary, font: { ascii: "Times New Roman" } })],
  });
}
function cell(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text: safeText(text) }];
  return new TableCell({
    children: [new Paragraph({
      spacing: { line: 276 },
      children: runs.map(r => new TextRun({
        size: opts.header ? 20 : 19, bold: !!opts.header || !!r.bold,
        color: opts.header ? P.table.headerText : (r.color || P.body),
        font: { ascii: "Times New Roman", eastAsia: "SimSun" },
      })),
    })],
    shading: opts.header ? { type: ShadingType.CLEAR, fill: P.table.headerBg }
      : (opts.fill ? { type: ShadingType.CLEAR, fill: opts.fill } : undefined),
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    width: opts.width ? { size: opts.width, type: WidthType.PERCENTAGE } : undefined,
  });
}
function table(headers, rows, widths) {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 2, color: P.table.accentLine },
      bottom: { style: BorderStyle.SINGLE, size: 2, color: P.table.accentLine },
      left: NB, right: NB,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 1, color: P.table.innerLine },
      insideVertical: NB,
    },
    rows: [
      new TableRow({
        tableHeader: true, cantSplit: true,
        children: headers.map((t, i) => cell(t, { header: true, width: widths ? widths[i] : undefined })),
      }),
      ...rows.map((r, ri) => new TableRow({
        cantSplit: true,
        children: r.map((c, i) => {
          const isObj = c && typeof c === "object" && !Array.isArray(c);
          return cell(isObj ? c.runs : c, {
            width: widths ? widths[i] : undefined,
            fill: ri % 2 === 0 ? P.table.surface : undefined,
          });
        }),
      })),
    ],
  });
}
const status = (label) => {
  const map = { PORTED: P.green, PARTIAL: P.amber, MISSING: P.red, COVERED: P.green, DIFFERENT: P.amber, N_A: P.secondary };
  return { runs: [{ text: label, bold: true, color: map[label] || P.body }] };
};

// ---------------------------------------------------------------- content
const coverConfig = {
  englishLabel: "TECHNICAL ANALYSIS REPORT",
  subtitle: "Gap analysis, integration map, and a recommended port roadmap for the AuthrDesktop (Weaver Studio) codebase",
  metaLines: [
    "Prepared for: Authr OS Engineering",
    "Baseline: authr-desktop @ ac0f73e (ADR-0018 delivered)",
    "Date: 2026-11-14",
  ],
  footerRight: "AUTHR OS — Commercial Authoring Platform",
};

const bodyChildren = [];

// ========== 1. Executive summary
bodyChildren.push(h1("1. Executive Summary"));
bodyChildren.push(body("Four uploaded v3.0 specifications — the Master Architecture, Engine Enhancements, Schema v2.2 DDL, and Implementation Roadmap — describe a set of engine-internal capabilities that the Python/Node lineage of Authr OS planned for its v3.0 release: a voice engine with cumulative drift tracking, a hierarchical context orchestrator with token budgets, a research vault, a pre-publication compliance gate, and a 25-format / 37-persona / 18-engine craft catalog."));
bodyChildren.push(body("The current Kotlin/Compose desktop implementation (Weaver Studio, commit ac0f73e) carries the same product doctrine — writer sovereignty (ADR-0001), deterministic guardrails (ADR-0004), additive-only migrations (ADR-0002), the six-layer Weaver engine, the 154-pattern cliché linter, and the Chekhov/Question ledgers — but its v3.0 scope diverged: the desktop port delivered the writer engine, serial validation, audiobook, and retail publishing stacks, plus the recently added ADR-0018 nine-stage commercial pipeline with its editorial intelligence layer. None of the four documents' six enhancement tracks exist in the desktop codebase."));
bodyChildren.push(body("The two scopes are complementary rather than conflicting: the specs fill the engine internals (voice measurement, context economics, compliance, research) that the desktop port never received, while ADR-0018 supplies the workflow scaffolding (stages, gates, memory) that the specs assume but never define. This report maps every spec item to the current implementation, classifies it as PORTED / PARTIAL / MISSING / DIFFERENT, and recommends a five-sprint port order that reuses existing desktop infrastructure wherever possible."));

// ========== 2. Background
bodyChildren.push(h1("2. Background, Method and Scope"));
bodyChildren.push(h2("2.1 Inputs analyzed"));
bodyChildren.push(body("The analysis covered all four documents end to end: (1) the Master Architecture & Engineering Specification (system topology, six-layer engine, craft matrix, 2-tier agent architecture, ADR-0001 through ADR-0016, 18 invariants); (2) the Engine Enhancement Specifications (voice engine feature extraction, cumulative EMA drift, dialogue sub-engine, 9-layer token budget, hierarchical summarization, research vault, compliance gate, extended gate matrix); (3) the Schema v2.2 DDL (8 new tables, 4 column additions, migration script, index set); and (4) the Implementation Roadmap (Sprint 0 through Sprint 4, risk matrix, acceptance criteria)."));
bodyChildren.push(h2("2.2 Verification method"));
bodyChildren.push(body("Every claim about the current implementation was verified against the actual source tree rather than from documentation alone. Key verification points included the shipped plugin catalog (ShippedPlugins.kt: 16 structures, 22 personas), the text-metrics utility (TextMetrics.kt: word count, sentence count, syllable-based Flesch-Kincaid only), the L4 draft generator's context assembly (a flat, one-line-per-chapter continuity summary of confirmed chapters), the ClicheCleaner's embedded filter-word patterns, the ApiService request schema (logit_bias passthrough, no token-usage fields), the editorial-memory store introduced by ADR-0018, and the PublishingMetadata/KdpWorkbench output format. Test counts come from the CI direct suite (298 tests green at the analyzed commit)."));
bodyChildren.push(h2("2.3 What this report does not cover"));
bodyChildren.push(body("The Python/Node codebase the specs target (py_core/, server/, client/ workspaces) is not present in this repository — the desktop port is a single Gradle module with Android API shims. The report therefore treats the specs as requirements to port, not as code to diff. CI hardware claims in the roadmap (performance overhead targets) are treated as goals to reproduce, not as measured facts."));

// ========== 3. Spec inventory
bodyChildren.push(h1("3. What the v3.0 Specifications Contain"));
bodyChildren.push(body("The Master Architecture organizes the work as six enhancement tracks, each anchored to a new ADR (0013-0016) and to schema v2.2. The table below inventories them together with the shared baseline they extend."));
bodyChildren.push(caption("Table 1: The six v3.0 enhancement tracks and their architectural anchors"));
bodyChildren.push(table(
  ["Track", "Spec content", "New ADR", "Schema additions"],
  [
    ["Cumulative voice drift and intentional evolution", "EMA (alpha = 0.1) drift tracking; flag at cumulative > 0.20; author-declared voice_evolution ranges suppress false alerts", "ADR-0013", "voice_evolution, voice_drift_log; chapters.cumulativeVoiceDrift"],
    ["Hierarchical context and token budget", "9-layer context assembly; stage-aware percentage allocation; 4-level summary cache (full / medium / brief / oneliner) with SHA-256 invalidation", "ADR-0014", "summary_cache, context_budget_log, token_spend_ledger; chapters.contextBudgetUsed"],
    ["Expanded prose feature extraction", "20+ metrics: structural (burstiness, clause depth, opener entropy), lexical (TTR, hapax, figurative density, sensory per sense, adverb and passive frequency), narrative (POV/tense consistency, show-vs-tell)", "extends ADR-0004", "chapter_beats.featureVector"],
    ["Research vault and citation management", "Source ingestion (URL, PDF, note, interview, dataset); fact extraction; chapter-linked citation verification; bibliography generation", "ADR-0015", "research_sources, research_facts"],
    ["Pre-publication compliance gate", "Plagiarism n-gram screening, trademark name screening, sensitivity review, AI-disclosure metadata; export blocked on unresolved blocking findings", "ADR-0016", "compliance_checks"],
    ["Dialogue sub-engine", "Dialogue Director role bound to the Scene Composer; conversation-arc planning; per-character speech fingerprint enforcement; pairwise voice-similarity check (flag > 0.70)", "extends Weaver 5", "none (uses character speechFingerprint)"],
  ],
  [20, 44, 12, 24]
));
bodyChildren.push(body("The roadmap sequences these across five sprints: Sprint 0 (schema migration and baseline verification), Sprints 1-3 in parallel (voice engine; context orchestrator; new modules), and Sprint 4 (integration and end-to-end validation). The spec's acceptance criteria include all 18 invariants enforced, zero-failure regression on the existing suite, and under 15 percent generation-time overhead."));

// ========== 4. Coverage matrix
bodyChildren.push(h1("4. Coverage Matrix: Specification vs Current Implementation"));
bodyChildren.push(body("The matrix below is the core of the cross-check. Each row names a spec capability, its status in the desktop codebase, and the evidence found. PORTED means an equivalent mechanism exists with comparable semantics; PARTIAL means a related mechanism exists but with materially different scope; MISSING means nothing equivalent exists; DIFFERENT means the capability is realized by a different mechanism the specs do not have."));
bodyChildren.push(caption("Table 2: Voice and prose-quality capabilities"));
bodyChildren.push(table(
  ["Spec capability", "Status", "Current implementation and evidence"],
  [
    ["154-pattern cliché linter with precision 1.0000 golden corpus", status("PORTED"), "ClicheCleaner ships the full matrix with six classes; ClicheCleanerGoldenCorpusTest pins precision; TokenBiasPolicy maps offenders to logit_bias for OpenAI-compatible providers (ADR-0005 honored)."],
    ["Sentence burstiness floor (SD >= 4.2)", status("MISSING"), "TextMetrics computes word, sentence and syllable counts for Flesch-Kincaid only; no sentence-length standard deviation anywhere in the engine."],
    ["Filter-word density (< 3.0 per 1,000 words) as a measured metric", status("PARTIAL"), "Filter words exist only as ClicheCleaner patterns that produce margin annotations; they are not aggregated into a per-1,000-words density measurement."],
    ["20+ feature extraction (clause depth, opener entropy, hapax, figurative density, sensory per sense, adverb and passive frequency)", status("MISSING"), "None of the lexical or structural features beyond word/sentence/syllable counts exist. The spec's spaCy dependency has no JVM equivalent in the tree."],
    ["Per-chapter voice drift detection (delta > 6.0)", status("MISSING"), "No drift concept exists. The annotator and QcScorecard score beat coverage and seven criteria, not voice statistics."],
    ["Cumulative EMA drift (alpha = 0.1, flag > 0.20)", status("MISSING"), "Absent; no persistence slot or column carries a drift value."],
    ["Intentional voice evolution with alert suppression", status("MISSING"), "Absent. The closest concept is ADR-0018's editorial decisions log, which is a generic rationale record, not a feature-target override."],
    ["Per-character speech fingerprints (max words per turn, contraction ratio, register, verbal tics)", status("MISSING"), "Character data lives as free-text characterBible prose; there is no structured character_registry table or fingerprint JSON."],
    ["Dialogue differentiation check (pairwise similarity < 0.70)", status("MISSING"), "No dialogue-by-speaker extraction or similarity computation exists."],
  ],
  [30, 12, 58]
));
bodyChildren.push(caption("Table 3: Context orchestration and economics"));
bodyChildren.push(table(
  ["Spec capability", "Status", "Current implementation and evidence"],
  [
    ["Hierarchical summarization (full / medium / brief / oneliner)", status("MISSING"), "L4LlmDraftGenerator embeds chapter.summary (a single hand-or-LLM-written paragraph) for every confirmed chapter — flat, uniform depth, no compression levels."],
    ["Freshness-ranked sliding window (N-1 full ... N-6+ one-liner)", status("MISSING"), "All confirmed chapters contribute identical one-line summaries regardless of distance from the drafting frontier."],
    ["9-layer token budget with stage-aware rebalancing", status("MISSING"), "Prompt assembly is unbounded string concatenation; no layer model, no per-layer caps, no overflow compression."],
    ["Summary cache with SHA-256 invalidation", status("MISSING"), "No cache; summaries are read straight from the chapter rows each time."],
    ["Token spend ledger and per-stage cost reporting", status("MISSING"), "ApiService.kt sends logit_bias but carries no usage fields; no token or cost accounting exists anywhere in the data layer."],
    ["Epistemic isolation (RAG filtered to confirmed, chapter <= k)", status("DIFFERENT"), "The desktop engine has no vector RAG, but WriterGate.canonChapters() enforces exactly this contract for every consumer (ADR-0001 invariant); L4 context includes confirmed chapters only."],
    ["Hybrid FTS + LanceDB vector retrieval", status("PARTIAL"), "SQLite FTS exists (stories_fts, chapters_fts with sanitizeFtsQuery); no vector store or embedding pipeline exists."],
  ],
  [30, 12, 58]
));
bodyChildren.push(caption("Table 4: New modules, catalog and gates"));
bodyChildren.push(table(
  ["Spec capability", "Status", "Current implementation and evidence"],
  [
    ["Research vault (source ingestion, fact tagging, citation verification, bibliography)", status("MISSING"), "No research concept exists. ADR-0018's market-research stage is an LLM analysis proposal, not a source-grounded research store."],
    ["Non-fiction citation gate (Invariant 17)", status("MISSING"), "The pipeline has no non-fiction mode; the 5-beat authority model is absent."],
    ["Compliance gate (plagiarism, trademark, sensitivity, AI disclosure)", status("MISSING"), "PublishingMetadata/KdpWorkbench writes marketing metadata and pricing only; no compliance checks run before packaging. The QA reuse noted below is partial."],
    ["Plagiarism n-gram self-similarity", status("PARTIAL"), "ManuscriptQaSuite (ADR-0018) already flags cross-chapter 5-gram repetition >= 3 occurrences as a defect — the same primitive the spec's roadmap proposes as the v3.0 starting point, though scoped to self-similarity within the manuscript only."],
    ["Craft catalog 25 formats x 37 personas x 18 engines", status("PARTIAL"), "ShippedPlugins.kt carries 16 structures and 22 personas; Book.format is a free-text field with a handful of presets (Novel, Novella, Short Story, ...). Missing: format word/chapter envelopes, non-fiction modes, script modes, persona numeric voice DNA."],
    ["Mode-polymorphic beat models (7-beat fiction, 5-beat authority, scene-slug, node-choice)", status("PARTIAL"), "LiveAstAnnotator implements the seven-beat fiction model with cue coverage; BalanceMeter enforces word-share bands. No non-fiction, script or CYOA beat models."],
    ["22 specialist roles across 6 Weaver engines", status("DIFFERENT"), "LlmOrchestrator has exactly 6 agents (Executive, Plot, Character, World, Scene, Continuity). PersonaRegistry adds 22 voice presets, which covers persona variety but not role specialization; ADR-0018 adds stage prompts, not roles."],
    ["18 invariants with deterministic gates", status("DIFFERENT"), "The desktop lineage has its own invariant set (writer sovereignty, canon-only ledgers, beat integrity, ending rotation, anti-withholding, ACX/KDP/epubcheck gates) enforced by 298 tests; the spec's 15-18 are additions, not replacements."],
    ["Quality scorecard (7 x 0-2, veto on clarity or payoff zero)", status("COVERED"), "QcScorecard implements exactly this with bands, zero-triggers and note-only overrides (P3, ADR-0007); wired into CompilationWorker and the Serial Dashboard."],
    ["Serial micro-arcs, buffer/cadence, anti-withholding, paste-ready exports", status("COVERED"), "MicroArcPlanner, BufferCadence, OpeningValidators' anti-withholding gate and SerialExportProfiles implement all of it; the spec carries these forward from v2.1 unchanged."],
    ["Audiobook chain (Pocket TTS, IPA, 3-layer mastering, ACX audit, SMIL)", status("COVERED"), "The P4 stack (PronunciationPass, MasteringChain, AcxAudit, SmilExporter, AudiobookRenderer) matches the spec section 8 verbatim — the desktop port is ahead here."],
    ["EPUB 3 + epubcheck gate, KDP print interior, retail packager", status("COVERED"), "Epub3Exporter, PrintInterior, RetailPackager with external epubcheck (ADR-0011/0013/0014) — also ahead of the spec baseline."],
    ["9-stage gated pipeline with persistent editorial memory", status("DIFFERENT"), "ADR-0018 (just delivered) adds market research through publication prep with deterministic stage gates, writer approval checkpoints, and an editorial_memory layer (Room v16) — capabilities the v3.0 specs do not define."],
  ],
  [30, 12, 58]
));

// ========== 5. Schema
bodyChildren.push(h1("5. Schema v2.2 vs the Desktop Data Model"));
bodyChildren.push(body("The desktop app uses Room (currently v16 with 12 entities) in a single SQLite database, while the specs define a per-book story_bible.db plus a read-only craft_catalog.db. The porting question is therefore not a straight DDL translation but a mapping decision per table. The desktop already satisfies the spec's migration doctrine (ADR-0002 additive-only, fail-loud, no destructive fallback) through its own MIGRATION chain, and ADR-0018's editorial_memory slot design offers a ready home for several spec concepts without new tables."));
bodyChildren.push(caption("Table 5: Schema v2.2 table-by-table port mapping"));
bodyChildren.push(table(
  ["Schema v2.2 object", "Recommended desktop home", "Rationale"],
  [
    ["voice_evolution", "editorial_memory slot (voice_evolution) or Room table", "Low write volume, per-book scope, JSON-adjusted features fit the slot DTO pattern; promote to a table only if queries by chapter range become needed."],
    ["voice_drift_log", "New Room table (or chapters column + slot)", "Per-chapter append log with numeric EMA; a dedicated table keeps the time series queryable."],
    ["summary_cache", "New Room table", "Needs (bookId, scope, level) uniqueness plus sourceHash — matches a table better than slots."],
    ["context_budget_log, token_spend_ledger", "editorial_memory slots", "Reporting-only data; the slot store already round-trips typed JSON lists."],
    ["research_sources, research_facts", "New Room tables", "Relational integrity (facts reference sources; both cascade from books) favors real tables."],
    ["compliance_checks", "New Room table", "The ship report and RetailPackager gate need to query unresolved blocking findings — index-friendly table access."],
    ["character_registry (with speechFingerprint)", "Larger decision — see section 8", "The desktop keeps characters as bible prose; adding structured characters is the highest-value schema change but touches Writer Studio workflows."],
    ["chapter_beats (beat locking, featureVector)", "Deferred", "Beat-level locking is a v2.1 spec concept the desktop never ported; introducing beats is a separate, larger initiative beyond the six tracks."],
  ],
  [26, 26, 48]
));
bodyChildren.push(body("A port should land as Room v17, additive only, with the migration registered in AppDatabase and the MigrationFailFastTest updated to the new current version — exactly the pattern ADR-0018 established for v16."));

// ========== 6. Conflicts
bodyChildren.push(h1("6. Conflicts and Reconciliations"));
bodyChildren.push(h2("6.1 ADR numbering collision"));
bodyChildren.push(body("The specs number their new decisions ADR-0013 through ADR-0016. The desktop lineage has already spent those numbers (0013 epubcheck distribution, 0014 publishing screen, 0015 worker registry, 0016 fail-loud persistence) and has reached ADR-0018. The ported decisions must therefore be renumbered into the desktop series (the natural next numbers are ADR-0019 through ADR-0022) with the original spec reference recorded inside each document. Content-wise there is no collision: the two sets of ADRs address disjoint concerns."));
bodyChildren.push(h2("6.2 Two schema versioning systems"));
bodyChildren.push(body("The specs speak of story_bible.db schema v2.1 to v2.2; the desktop speaks of Room v16 (with .authr container schema v2 as a separate axis). This report uses 'Room v17' for the port target to avoid ambiguity. The .authr container will also need a payload version bump if research and compliance data should travel between projects — the AuthrContainer round-trip test is the guard."));
bodyChildren.push(h2("6.3 Overlapping drift/quality concepts"));
bodyChildren.push(body("ADR-0018's RetentionAnalyzer measures per-chapter hook, pacing, tension, curiosity and ending scores; the spec's voice engine measures statistical prose features and drift. These are complementary axes (retention craft signals vs voice fidelity) and should be presented together in the pipeline's stage-7 analytics rather than merged. Similarly, ManuscriptQaSuite's 5-gram repetition check and the spec's plagiarism screen share a primitive; the QA suite finding should be relabeled as the self-similarity baseline the spec's own roadmap proposes, with external-corpus comparison deferred as the spec also recommends."));
bodyChildren.push(h2("6.4 Agent topology"));
bodyChildren.push(body("The specs' 22-role architecture is a prompt-layer concept over 6 engines; the desktop's LlmOrchestrator already routes per-agent providers. Porting roles as distinct agents would multiply provider configuration UI. Recommendation: keep the 6 agents and implement roles as persona-style prompt directives selected per pipeline stage — the same approach PersonaRegistry already uses, and consistent with how StagePrompts builds stage-specific prompts today."));

// ========== 7. Integration
bodyChildren.push(h1("7. Integration Map: Where Each Track Lands in the Desktop Architecture"));
bodyChildren.push(caption("Table 6: Integration points for the six tracks"));
bodyChildren.push(table(
  ["Track", "Engine home", "Pipeline wiring", "UI surface"],
  [
    ["Voice engine (features + drift + evolution)", "New engine/voice/VoiceFeatureExtractor.kt and VoiceDriftTracker.kt next to ClicheCleaner; pure JVM, no spaCy — regex and token statistics only", "Run after chapter confirmation (Writer Studio confirm flow); persist drift in the memory store; surface cumulative drift in ADR-0018 stage 7 alongside retention analytics; evolution entries authored via an editorial-memory panel", "Writer Studio margin rail extension + Pipeline workbench voice panel"],
    ["Context orchestrator (budget + hierarchical summaries)", "New engine/context/ContextAssembler.kt consumed by L4LlmDraftGenerator; summary levels generated via LlmOrchestrator on a cheap provider", "Replaces the flat canonContext summary block in L4 prompts; logs per-invocation budgets; chapter confirm triggers summary cache refresh (invalidate by content hash)", "Settings: context budget report; Pipeline: token spend per stage"],
    ["Research vault", "New engine/research/ResearchVault.kt + two Room tables", "ADR-0018 stage 1 (Market Research) becomes source-grounded when sources exist; QA stage cross-checks chapter facts", "New Research workspace (or a tab within the Pipeline workbench)"],
    ["Compliance gate", "New engine/publishing/ComplianceGate.kt beside RetailPackager", "Runs inside ADR-0018 stage 9 and blocks approveStage on unresolved blocking findings, mirroring how the QA gate blocks stage 8", "Publishing workbench pre-flight panel"],
    ["Dialogue sub-engine", "Extend StagePrompts/L4 with a dialogue-director prompt block fed by character fingerprints", "Applied during manuscript generation when a scene plan marks multi-character dialogue; differentiation check runs post-draft in the QA stage", "Writer Studio dialogue findings"],
    ["Craft catalog expansion", "Extend ShippedPlugins.kt and PersonaRegistry with numeric voice DNA fields", "Pipeline stages 2-4 (concept, architecture, worldbuilding) read format envelopes (target words, chapters, mode) instead of free text", "Story wizard format picker + pipeline stage 3 chapter-count default"],
  ],
  [22, 30, 28, 20]
));

// ========== 8. Port roadmap
bodyChildren.push(h1("8. Recommended Port Roadmap"));
bodyChildren.push(body("The order below follows the same dependency logic as the spec's roadmap (schema first, parallelizable tracks, integration last) but re-sequences for the desktop reality: ADR-0018's pipeline already exists, so each track should wire into it as it lands, and the highest reader-facing value comes first."));
bodyChildren.push(caption("Table 7: Sprint plan for the desktop port"));
bodyChildren.push(table(
  ["Sprint", "Scope", "Key deliverables", "Acceptance criteria"],
  [
    ["S0. Schema and probes", "Room v17 migration; toolchain probes", "voice_drift_log, summary_cache, research_sources, research_facts, compliance_checks tables; editorial-memory slots for token ledger and voice evolution; MigrationFailFastTest at v17; a JVM feature-extraction spike to validate regex/token statistics against the golden serial corpus", "Migration runs on a v16 database without data loss; CI direct suite green; spike measures 20+ features on GoldenSerial chapters in under 50 ms per chapter"],
    ["S1. Voice engine", "ADR-0019 (was spec 0013)", "VoiceFeatureExtractor (20+ metrics, pure JVM); cumulative EMA drift tracker with configurable alpha; voice_evolution authoring and suppression; drift persisted per confirmed chapter; Invariant 15 gate wired into the pipeline stage 7 verdict", "Planted-corpus tests: drifting chapters flagged, evolution-covered chapters pass, stable chapters stay under 0.20; existing 298 tests unaffected"],
    ["S2. Context orchestrator", "ADR-0020 (was spec 0014)", "ContextAssembler with 9 layers and stage allocations; 4-level summary cache with SHA-256 invalidation; L4 prompt assembly migrated to it; token spend logged per invocation and reported per stage and per project", "GoldenSerial generation prompt fits declared budgets; cache invalidates on chapter edit; budget logs retrievable; no regression in draft quality gates"],
    ["S3. Research vault and compliance gate", "ADR-0021/0022 (was spec 0015/0016)", "Source ingestion (paste/URL/PDF text), fact extraction, chapter linking, bibliography export; compliance checks: n-gram self-similarity (reusing ManuscriptQaSuite's primitive), trademark regex list, sensitivity wordlist review, AI-disclosure metadata in PublishingMetadata; export blocked on blocking findings", "A project with unverified cited facts and blocking findings cannot pass stage 9 or package for retail; a clean project sails through; container round-trip carries the new data"],
    ["S4. Catalog and integration", "Craft matrix + end-to-end", "Format envelopes for the 25 formats (words, chapters, mode, beat model) in the plugin catalog; persona numeric voice DNA; non-fiction 5-beat model in the annotator where feasible; end-to-end fiction and serial validation runs; docs and ADR cross-references updated", "Spec acceptance checklist items 1-8 and 10-14 reproduced for the desktop (item 9, the <15 percent overhead target, measured on the sandbox hardware and recorded honestly)"],
  ],
  [14, 18, 40, 28]
));
bodyChildren.push(body("Two items deserve explicit deferral decisions rather than silent omission: the character_registry with speech fingerprints (highest value, but it changes how writers author characters — recommend a dedicated design pass before S1 so the fingerprint editor lands in Writer Studio properly) and beat-level locking with the causal dirty-tree (a v2.1 concept orthogonal to all six tracks; defer until the voice and context layers are stable). Vector RAG (LanceDB) should also stay deferred: SQLite FTS plus hierarchical summaries covers the context needs, and a vector store is a large dependency for a JVM desktop app."));

// ========== 9. Risks
bodyChildren.push(h1("9. Risks and Mitigations"));
bodyChildren.push(caption("Table 8: Port-specific risks (extending the spec's risk matrix)"));
bodyChildren.push(table(
  ["Risk", "Likelihood", "Impact", "Mitigation"],
  [
    ["Feature tolerances mis-calibrated without spaCy-grade parsing", "High", "Medium", "Start with the spec's own advice: wide tolerance bands (+/-30 percent), promote narrow bands only after real-project data; golden-corpus tests pin the detector, not the thresholds"],
    ["Token-count estimator diverges from provider reality", "Medium", "Medium", "Ship the estimator with a calibration factor and log estimate-vs-actual once usage fields are added to ApiService; the spec's 10 percent buffer applies"],
    ["Summary quality drops plot-critical details", "Medium", "High", "Use the spec's preserve-entities prompt (names, plot events, foreshadowing, unresolved tensions); cache invalidation by content hash keeps summaries honest after edits"],
    ["Compliance false positives block legitimate exports", "High", "Medium", "Follow the spec: sensitivity starts warning-only; only AI-disclosure absence and confirmed plagiarism self-similarity start blocking"],
    ["L4 prompt growth degrades generation quality", "Low", "High", "The budget allocator's entire purpose; S2 acceptance explicitly compares gate pass-rates before and after on the golden serial"],
    ["Desktop test suite regression under new gates", "Low", "High", "Every sprint keeps the CI direct suite green; new gates land behind pipeline stage verdicts, not inside CompilationWorker's existing chain"],
  ],
  [34, 12, 10, 44]
));

// ========== 10. Conclusions
bodyChildren.push(h1("10. Conclusions and Recommendations"));
bodyChildren.push(body("The four v3.0 specifications are well-aligned with the desktop codebase's doctrine and fill genuine gaps: nothing in the current engine measures voice fidelity, nothing manages context economics, nothing verifies research facts, and nothing gates publication on compliance. At the same time, the desktop port already exceeds the spec baseline in audiobook, publishing and workflow capability, and its ADR-0018 pipeline gives the six tracks ready-made integration points (stage gates, editorial memory, writer approval) that the specs lack."));
bodyChildren.push(body("The recommended course of action is: (1) adopt the roadmap in section 8, renumbering the spec ADRs into the desktop series; (2) start with Sprint 0's schema migration and the JVM feature-extraction spike, since both de-risk every later sprint; (3) wire each track into the ADR-0018 pipeline as it lands so gates, approvals and memory stay the single control surface; and (4) defer character restructuring and vector RAG to explicit follow-up decisions. Executed this way, the port converts the specs' engine internals into measurable quality signals inside a pipeline that already enforces writer sovereignty — which is precisely the combination the product's stated goal requires: commercially compelling books through a controlled, iterative, quality-driven workflow."));

// ---------------------------------------------------------------- document
const pageHeader = new Header({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "AUTHR OS v3.0 Engine Specifications — Cross-Check Analysis", size: 18, color: "808080", font: { ascii: "Times New Roman" } })],
  })],
});
const romanFooter = new Footer({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "808080" })],
  })],
});
const arabicFooter = new Footer({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "808080" })],
  })],
});

const doc = new Document({
  styles: {
    default: {
      document: {
        run: { font: { ascii: "Times New Roman", eastAsia: "SimSun" }, size: 24, color: "000000" },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: { ascii: "Times New Roman", eastAsia: "SimHei" }, size: 32, bold: true, color: P.primary },
        paragraph: { spacing: { before: 360, after: 160, line: 312 } },
      },
      heading2: {
        run: { font: { ascii: "Times New Roman", eastAsia: "SimHei" }, size: 28, bold: true, color: P.primary },
        paragraph: { spacing: { before: 240, after: 120, line: 312 } },
      },
    },
  },
  sections: [
    // Section 1: cover (margin 0, no page number)
    {
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 0, bottom: 0, left: 0, right: 0 } } },
      children: buildCoverR2(coverConfig),
    },
    // Section 2: TOC (Roman numerals)
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 }, pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN } },
      },
      headers: { default: pageHeader },
      footers: { default: romanFooter },
      children: [
        new Paragraph({
          alignment: AlignmentType.CENTER, spacing: { before: 240, after: 240 },
          children: [new TextRun({ text: "Table of Contents", bold: true, size: 32, color: P.primary, font: { ascii: "Times New Roman", eastAsia: "SimHei" } })],
        }),
        new TableOfContents("Table of Contents", { hyperlink: true, headingStyleRange: "1-2" }),
        new Paragraph({
          spacing: { before: 200 },
          children: [new TextRun({ text: "Note: right-click the table of contents and choose Update Field to refresh page numbers after opening in Word.", italics: true, size: 18, color: "909090", font: { ascii: "Times New Roman" } })],
        }),
      ],
    },
    // Section 3: body (Arabic, reset to 1)
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 }, pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } },
      },
      headers: { default: pageHeader },
      footers: { default: arabicFooter },
      children: bodyChildren,
    },
  ],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/home/z/my-project/download/authr-os-v3-spec-crosscheck-analysis.docx", buf);
  console.log("WROTE docx:", buf.length, "bytes");
});
