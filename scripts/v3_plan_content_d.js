// Sections 12-16: Architecture & Data Model, Roadmap, Risks, Acceptance, Appendices
"use strict";
const path = require("path");
const { h1, h2, p, tbl, figure, codeBlock } = require("./v3_plan_lib");

const ASSETS = path.join(__dirname, "v3_assets");

function build() {
  const out = [];

  // ── 12. Architecture & Data Model Changes ────────────────────────────────
  out.push(h1("12. Architecture & Data Model Changes"));
  out.push(p(
    "v3.0 keeps the v2.3 architecture — Room database, WorkManager pipeline, the multi-provider orchestrator, and the Compose Desktop port sharing the same code through the shim layer — and changes it at the edges the Rev 2 corrections touch. The Room schema moves to v2 with additive migrations only: the v2.3 baseline already demonstrates the pattern with its ALTER TABLE migration for isSerialized, targetChapters, and releaseSchedule."
  ));
  out.push(...tbl(
    "Schema v2 delta summary",
    ["Change", "Entity / table", "Detail"],
    [
      ["Add columns", "books", "freeTierChapters (5), wordMin (1000), wordMax (2000), bufferTarget (8), exportProfile (\u201croyal_road\u201d)"],
      ["Add columns", "chapters", "arcId, endingType, beatCoverage (JSON), balanceShares (JSON), worksheet (JSON), qcScores (JSON), qcTotal, hookCheck (JSON), ctaNote, pollJson"],
      ["New table", "serial_arcs", "id, bookId, arcIndex, conflict, questionRef, phaseBounds (JSON), answeredQuestionRef, contribution, seeds (JSON)"],
      ["New table", "chekhov_guns", "id, bookId, name, description, status, introducedChapter, escalationChapters (JSON), resolvedChapter, quote, severity, waiveNote"],
      ["New table", "questions", "id, bookId, level (immediate/progressive/long-term), text, openedChapter, dueChapter, answeredChapter, answeredByReveal"],
      ["New table", "plugins", "id, kind (structure/persona), name, path, enabled, version"],
      ["Remove", "settings", "Video-related preference keys (SCR-2); migration ignores unknown keys"],
      ["Remove", "code artifacts", "SceneAutomationScreen.kt, 14 video state flows, generateVideo(), KittenTtsManager, LocalKittenTtsManager, libkittentts-native.so"],
      ["Add", "desktop bridge", "Pocket TTS process bridge (standalone binary invocation, voice pack management); RxFFmpeg shim reduced to audio-only"],
      ["Add", "engine state", "writer_confirmed on chapters and beats; PROPOSED / CONFIRMED / ARCHIVED draft status"],
    ],
    [16, 18, 66]
  ));
  out.push(p(
    "The desktop port inherits every change through the shared module; its two port-specific items are the Pocket TTS process bridge and the removal of the video shims. The .authr container (section 10) is the interchange format between devices: schema v2 containers open on both Android and desktop, with the plugin and voice-pack directories copied verbatim so a project sounds and drafts identically wherever it is opened."
  ));

  // ── 13. Implementation Roadmap ───────────────────────────────────────────
  out.push(h1("13. Implementation Roadmap (P0\u2013P5)"));
  out.push(p(
    "Six phases, 32 person-weeks, each closing on a measurable exit gate. P3 is the serialization phase promoted from the post-P5 backlog by SCR-4. Phases are staffed for one engineer plus part-time product review by the writer-in-residence; the order minimizes rework: foundation and removals first, engine and quality next, then the two big feature stacks, publishing last."
  ));
  out.push(...tbl(
    "Phase plan and exit gates",
    ["Phase", "Weeks", "Scope", "Exit gate"],
    [
      ["P0 — Foundation & Clean-up", "4", ".authr container schema v2 + migrations; SCR-2 video removal; SCR-3 Kitten TTS removal; plugin directory scaffold; Pocket TTS desktop spike", "A v2.3 project round-trips through the container losslessly; zero video or Kitten references in the codebase; Pocket TTS standalone binary synthesizes a 10,000-word chapter on the reference laptop"],
      ["P1 — Writer Engine Core", "6", "Six-layer engine with writer_confirmed gates; Writer Studio; Live AST Annotator on confirmed text; PROPOSED/CONFIRMED/ARCHIVED states", "A five-chapter novella runs end to end with the confirm flow; no unconfirmed text reaches any ledger or export (seeded-project assertion suite)"],
      ["P2 — Prose Quality Systems", "5", "Cliché cleaner (154 patterns, 6 classes); provider token-bias matrix; Chekhov ledger with final-act gate; question ledger foundations", "Precision \u2265 90% on the 50-page golden corpus; planted guns in the regression corpus all detected; zero false compile blocks"],
      ["P3 — Serialization", "6", "Full Blueprint 2.0 stack: opening validators, seven-beat coverage and balance meter, ending advisor, micro-arc planner, worksheet, scorecard, buffer and cadence tooling, platform export profiles — both modes", "A 15-chapter golden serial passes the full validator suite with at most two dismissable warnings; the anti-withholding gate fires correctly on a seeded free tier; scorecard gates behave per the interpretation bands"],
      ["P4 — Audiobook Engine", "6", "Pocket TTS full integration (voice packs, streaming, optional Android pack); IPA pass; continuity modes; three-layer mastering chain; ACX audit; SMIL export", "A ten-chapter audiobook renders and passes the ACX audit all-green; synthesis \u2265 2x real time on the reference laptop; SMIL timing sanity check passes"],
      ["P5 — Publishing", "5", "EPUB 3 exporter; print interior with gutter ladder; metadata workbench; volume bundling; end-to-end ship report", "epubcheck zero errors; KDP Print Previewer pass; a complete .authr project produces the full retail package in one action"],
    ],
    [19, 11, 35, 35]
  ));
  out.push(...figure(
    path.join(ASSETS, "fig2_roadmap_effort.png"),
    "v3.0 roadmap effort by phase. P3 (highlighted) is the Web Novel Serialization phase promoted into the main plan by Rev 2 correction SCR-4."
  ));
  out.push(p(
    "Two dependencies are worth flagging for scheduling: P3 consumes the question ledger built in P2 (the information levels of section 6.7 share storage with the Chekhov machinery), and P4's ACX audit depends on P0's audio-only ffmpeg reduction being complete so the mastering chain is measured against a stable binary set. Neither blocks parallel staffing, but both gates must hold."
  ));

  // ── 14. Risks & Mitigations ──────────────────────────────────────────────
  out.push(h1("14. Risks & Mitigations"));
  out.push(...tbl(
    "Risk register",
    ["Risk", "Prob.", "Impact", "Mitigation"],
    [
      ["Pocket TTS on Android is disproportionate (PyTorch CPU bundle size)", "Med", "Med", "Desktop-first integration; Android ships system + cloud engines by default with Pocket TTS as an optional model pack; the engine matrix keeps every route functional"],
      ["Low-end CPUs slow down long chapters", "Med", "Med", "Small variants (down to 1.9M) for constrained hardware; queue-and-render background service; speed surfaced in the audiobook report"],
      ["Validator false positives frustrate writers", "Med", "Med", "Every serialization and quality check is tunable per project, dismissable with a note, and tested against golden corpora; the scorecard is explicitly labeled an editorial self-assessment aid"],
      ["Scorecard scores drift with LLM proposal quality", "Med", "Low", "L6 proposes, the writer always sets final scores; deterministic checks contribute directly so the proposal is anchored"],
      ["Platform terms of service change", "High", "Med", "Paste-ready exports rather than platform APIs; profiles are versioned data, adjustable without a release"],
      ["Video removal disrupts existing users", "Low", "Med", "Deprecation note one minor release ahead; one-time SRT export before purge; ffmpeg audio path untouched and asserted in smoke tests"],
      ["Gemini routes lack decode-time bias", "Certain", "Low", "Accepted: prompt constraints plus the deterministic linter is the enforcement contract; the linter is the gate"],
      ["Ending advisor over-prescribes", "Med", "Low", "The advisor classifies and records; it never assigns. Consecutive-repeat warnings are advisory only, and quiet endings are a supported classification"],
      ["Serialization scope creep beyond Blueprint 2.0", "Med", "Med", "The blueprint is frozen as the spec for v3.0; additions queue for a future revision rather than landing mid-phase"],
      ["Container migration corrupts legacy projects", "Low", "High", "Additive migrations only; unknown keys ignored; automatic backup before every migration; round-trip test in the P0 gate"],
    ],
    [32, 10, 10, 48]
  ));

  // ── 15. Acceptance Criteria & Success Metrics ────────────────────────────
  out.push(h1("15. Acceptance Criteria & Success Metrics"));
  out.push(p(
    "Acceptance is defined against external gates and golden corpora, never against impressions. Each pillar carries the metrics below; the programme closes only when every phase gate (section 13) and every pillar metric is green, and three beta authors have each shipped one serial and one retail title end to end from a .authr container."
  ));
  out.push(...tbl(
    "Pillar acceptance metrics",
    ["Pillar", "Metric", "Target"],
    [
      ["Engine sovereignty", "Unconfirmed text reaching ledger or export in the assertion suite", "Zero occurrences"],
      ["Prose quality", "Cliché linter precision on the golden corpus", "\u2265 90%"],
      ["Prose quality", "Planted-gun detection in the regression corpus", "100% of planted guns, zero silent waivers"],
      ["Serialization", "Validator suite on the 15-chapter golden serial", "All checks pass; \u2264 2 dismissable warnings"],
      ["Serialization", "Anti-withholding gate on a seeded free tier", "Fires exactly on the seeded withheld-payoff project"],
      ["Serialization", "Scorecard gates on seeded chapters", "Interpretation bands applied correctly; zero-triggers always block"],
      ["Audiobook", "Synthesis speed on the reference laptop", "\u2265 2x real time (main model or small variant)"],
      ["Audiobook", "ACX audit on a rendered ten-chapter master", "All-green, every chapter and the master"],
      ["Publishing", "epubcheck on the KDP export", "Zero errors"],
      ["Publishing", "KDP Print Previewer on the interior", "Pass at every gutter-ladder band exercised"],
      ["Container", "v2.3 project round-trip", "Lossless; manifest records the migration"],
      ["Removal", "Video and Kitten TTS references in the codebase", "Zero after P0"],
    ],
    [18, 46, 36]
  ));
  out.push(p(
    "The ship report — the same one that lists Chekhov waivers, scorecard notes, and cadence streaks — is the final artifact: if the report is clean, the export behind it is defensible. That is the standard Rev 2 commits to: publish-ready means provable."
  ));

  // ── 16. Appendices ───────────────────────────────────────────────────────
  out.push(h1("Appendix A — Serialization and Ledger DDL"));
  out.push(p(
    "The migrations below are additive ALTERs and CREATEs matching section 12; unknown-key tolerance and backup-before-migrate are handled by the container layer, not the SQL."
  ));
  out.push(...codeBlock([
    '-- Schema v2: serialization profile (books)',
    'ALTER TABLE books ADD COLUMN freeTierChapters INTEGER NOT NULL DEFAULT 5;',
    'ALTER TABLE books ADD COLUMN wordMin INTEGER NOT NULL DEFAULT 1000;',
    'ALTER TABLE books ADD COLUMN wordMax INTEGER NOT NULL DEFAULT 2000;',
    'ALTER TABLE books ADD COLUMN bufferTarget INTEGER NOT NULL DEFAULT 8;',
    'ALTER TABLE books ADD COLUMN exportProfile TEXT NOT NULL DEFAULT \'royal_road\';',
    '',
    '-- Serialization fields on chapters',
    'ALTER TABLE chapters ADD COLUMN arcId INTEGER REFERENCES serial_arcs(id);',
    'ALTER TABLE chapters ADD COLUMN endingType TEXT CHECK (endingType IN',
    '    (\'DANGER\',\'EMOTIONAL_REVELATION\',\'MYSTERY_DISCOVERY\',',
    '     \'IRREVERSIBLE_CHOICE\',\'REVERSAL\',\'ANTICIPATION\',\'QUIET\'));',
    'ALTER TABLE chapters ADD COLUMN beatCoverage TEXT;   -- JSON, seven beats',
    'ALTER TABLE chapters ADD COLUMN balanceShares TEXT;   -- JSON, advisory bands',
    'ALTER TABLE chapters ADD COLUMN worksheet TEXT;      -- JSON, sections A-E',
    'ALTER TABLE chapters ADD COLUMN qcScores TEXT;       -- JSON, criteria 1-7',
    'ALTER TABLE chapters ADD COLUMN qcTotal INTEGER CHECK (qcTotal BETWEEN 0 AND 14);',
    'ALTER TABLE chapters ADD COLUMN hookCheck TEXT;      -- JSON, chapters 1-5',
    'ALTER TABLE chapters ADD COLUMN ctaNote TEXT;',
    'ALTER TABLE chapters ADD COLUMN pollJson TEXT;',
    '',
    'CREATE TABLE serial_arcs (',
    '  id INTEGER PRIMARY KEY AUTOINCREMENT,',
    '  bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,',
    '  arcIndex INTEGER NOT NULL,',
    '  conflict TEXT NOT NULL,',
    '  questionRef INTEGER REFERENCES questions(id),',
    '  phaseBounds TEXT NOT NULL,          -- JSON {setup,escalation,reversal,climax}',
    '  answeredQuestionRef INTEGER REFERENCES questions(id),',
    '  contribution TEXT,',
    '  seeds TEXT                          -- JSON array of next-arc seeds',
    ');',
    '',
    'CREATE TABLE chekhov_guns (',
    '  id INTEGER PRIMARY KEY AUTOINCREMENT,',
    '  bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,',
    '  name TEXT NOT NULL,',
    '  description TEXT,',
    '  status TEXT NOT NULL CHECK (status IN',
    '    (\'INTRODUCED\',\'ESCALATED\',\'RESOLVED\',\'ORPHANED\',\'WAIVED\')),',
    '  introducedChapter INTEGER NOT NULL,',
    '  escalationChapters TEXT,           -- JSON array',
    '  resolvedChapter INTEGER,',
    '  quote TEXT,',
    '  severity TEXT NOT NULL DEFAULT \'normal\',',
    '  waiveNote TEXT',
    ');',
    '',
    'CREATE TABLE questions (',
    '  id INTEGER PRIMARY KEY AUTOINCREMENT,',
    '  bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,',
    '  level TEXT NOT NULL CHECK (level IN',
    '    (\'IMMEDIATE\',\'PROGRESSIVE\',\'LONG_TERM\')),',
    '  text TEXT NOT NULL,',
    '  openedChapter INTEGER NOT NULL,',
    '  dueChapter INTEGER,',
    '  answeredChapter INTEGER,',
    '  answeredByReveal INTEGER REFERENCES questions(id)',
    ');',
    '',
    'CREATE TABLE plugins (',
    '  id INTEGER PRIMARY KEY AUTOINCREMENT,',
    '  kind TEXT NOT NULL CHECK (kind IN (\'structure\',\'persona\')),',
    '  name TEXT NOT NULL,',
    '  path TEXT NOT NULL,',
    '  version TEXT,',
    '  enabled INTEGER NOT NULL DEFAULT 1',
    ');',
  ]));

  out.push(h1("Appendix B — Reference Sources"));
  out.push(...tbl(
    "Sources verified for this revision",
    ["#", "Source"],
    [
      ["1", "Kyutai Labs, pocket-tts repository — github.com/kyutai-labs/pocket-tts (model family, MIT licence, packaging, streaming, cloning API)"],
      ["2", "Kyutai, \u201cPocket TTS: a high-quality TTS with voice cloning that runs on a CPU\u201d, technical report, January 2026 — kyutai.org/pocket-tts-technical-report (100M parameters; real-time CPU benchmarks)"],
      ["3", "GetStream engineering, \u201cHow to Build a Local AI Voice Agent with Pocket TTS\u201d, January 2026 — getstream.io/blog/pocket-tts-voice-agent (integration patterns)"],
      ["4", "Arm community benchmark, pocket-tts-pt-mix-precision, September 2026 (median end-to-end 0.897 s for 4.04 s audio; 0.107 s time to first chunk on CPU)"],
      ["5", "Serialized Fiction Blueprint 2.0 — product input document supplied with this revision (all of section 6; supersedes the 1.0 draft)"],
      ["6", "Authr OS v2.3 source baseline — upload archive: AppDatabase.kt (isSerialized migration), OutlineWorker.kt / DraftingWorker.kt (serial prompt branches), SceneAutomationScreen.kt (SCR-2 inventory), KittenTtsManager.kt (SCR-3 baseline), StudioViewModel.kt (14 video flows)"],
      ["7", "ACX audio submission requirements (RMS, peak, noise floor, MP3 CBR specifications) and Amazon KDP print interior requirements (inside-margin ladder) — re-verified in the Rev 1 spike"],
      ["8", "ElevenLabs previous_request_ids chaining for cross-segment voice consistency — verified in the Rev 1 spike"],
    ],
    [6, 94]
  ));

  return out;
}

module.exports = { build };
