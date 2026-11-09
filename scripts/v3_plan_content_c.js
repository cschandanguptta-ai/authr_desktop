// Sections 7-11: Audiobook Engine & Pocket TTS, Plugin System, Project Formats,
// .authr Container, Publishing & Distribution
"use strict";
const path = require("path");
const { h1, h2, p, tbl, figure, codeBlock } = require("./v3_plan_lib");

const ASSETS = path.join(__dirname, "v3_assets");

function build() {
  const out = [];

  // ── 7. Audiobook Engine & Pocket TTS ─────────────────────────────────────
  out.push(h1("7. Audiobook Engine & Pocket TTS"));
  out.push(p(
    "The audiobook engine follows the studio-grade Module 03 specification: narration with verifiable pronunciation, voice consistency across chapter boundaries, a deterministic mastering chain, retail compliance auditing, and read-along output. Rev 2 changes the synthesis foundation — **Pocket TTS replaces Kitten TTS** as the local engine — and the rest of the specification carries forward from Rev 1."
  ));

  out.push(h2("7.1 Pocket TTS: Specification and Fit"));
  out.push(p(
    "Pocket TTS (Kyutai Labs, MIT licence) is a text-to-speech family purpose-built to run locally: a 100M-parameter main model that synthesizes in real time on ordinary CPUs, plus small English variants released in October 2026 at 18.2M, 6.9M, and 1.9M parameters for constrained hardware. It replaces a cloud engine hosted on a free shared Gradio HF Space — a queue with no availability guarantee — and a 762 KB native library that shipped for a single 32-bit ABI, English-only, with no voice cloning."
  ));
  out.push(...tbl(
    "Pocket TTS engineering specification (from the Kyutai repository and technical report)",
    ["Property", "Specification"],
    [
      ["Model family", "100M-parameter main model (generative core ~90M); small English variants english_18m / english_7m / english_2m (18.2M / 6.9M / 1.9M); 24-layer higher-quality variants for some languages; reward fine-tuned (_refl) variants"],
      ["License", "MIT — bundling in the desktop distribution is unambiguous"],
      ["Speed", "About 6x real time on an Apple M4 CPU; small variants up to 25x real time on a single CPU thread; ~6.3x on a Tesla T4 GPU vs ~2.4x on a typical x86 laptop CPU; ~0.2 s median time to first audio chunk (0.107 s in the Arm mixed-precision benchmark)"],
      ["Streaming", "First-class streaming; unbounded input length — a whole chapter synthesizes without chunk-stitching artifacts"],
      ["Voice cloning", "From a short WAV reference via get_state_for_audio_prompt; export-voice compiles a reference into a safetensors voice pack for instant reload"],
      ["Languages", "Core: English, French, German, Portuguese, Italian, Spanish, Dutch; community models: Czech, Hindi, Korean, Persian, Indonesian, Estonian, Welsh, Turkish, Polish, Greek, Japanese, Chinese; Africa TTS pack (Nov 2026) adds 92 African languages plus English"],
      ["Packaging", "pip package (Python 3.10\u20133.14, PyTorch 2.5+, CPU build sufficient, ~200 MB CPU-only install); standalone binaries for Linux, macOS, and Windows with no Python dependency; uses only two CPU cores; WebAssembly runs in-browser where needed"],
      ["Determinism", "A fixed voice state reproduces the same voice identity across sessions — no drift, no re-prompting"],
    ],
    [22, 78]
  ));
  out.push(...figure(
    path.join(ASSETS, "fig1_pocket_tts_rtf.png"),
    "Pocket TTS synthesis speed by model and hardware. All configurations exceed real time (1x dashed line). Sources: Kyutai technical report (Jan 2026), Arm mixed-precision benchmark (Sep 2026)."
  ));
  out.push(p(
    "**Integration.** Pocket TTS slots into the existing TTS manager abstraction as a new engine beside Edge, Cloud HuggingFace, and the Android system engine; KittenTtsManager and its native library are deleted. The desktop build is the primary integration target: the standalone MIT-licensed binaries ship inside the distribution next to the existing espeak-ng dependency, one per platform, invoked through the desktop process bridge. On Android, a full PyTorch CPU bundle is disproportionate for a first release, so Pocket TTS lands as an optional downloadable model pack where the platform permits; otherwise Android keeps the system engine and the cloud engines as defaults. Figure 1 shows why the desktop bet is safe: even the main model outruns real time on a laptop CPU, and the small variants are an order of magnitude faster still."
  ));

  out.push(h2("7.2 TTS Engine Matrix"));
  out.push(...tbl(
    "TTS engine matrix in v3.0",
    ["Engine", "Location", "Voice cloning", "Role in v3.0"],
    [
      ["Pocket TTS", "Local (desktop bundled; Android optional pack)", "Yes — WAV reference to voice pack", "**Primary local engine**: offline narration, narrator brand consistency, privacy"],
      ["Edge TTS", "Cloud (Microsoft)", "No", "Streaming accents and low-latency previews; kept from v2.3"],
      ["Cloud HuggingFace TTS", "Cloud", "Per-model", "Custom cloud voice models; kept from v2.3"],
      ["ElevenLabs", "Cloud", "Yes", "Premium productions; the only engine with cross-request chaining (7.4)"],
      ["Android system TTS", "Local", "No", "Legacy fallback on mobile devices"],
      ["Kitten TTS", "Cloud (shared HF Space) + native lib", "No", "**Removed in Rev 2** (SCR-3): queue-dependent, English-only, no arm64, no cloning"],
    ],
    [16, 22, 20, 42]
  ));

  out.push(h2("7.3 IPA Annotation and Pronunciation Rules"));
  out.push(p(
    "Narration quality fails quietly on proper nouns. The engine therefore runs an **automatic IPA pass**: unusual names, coined terms, and lorebook entries are phonemized to IPA (the desktop build reuses the bundled espeak-ng phonemizer), the LLM proposes human-readable respellings where the phonemization looks wrong, and the writer approves corrections once per project. Approved entries land in the existing pronunciation-rules list with an IPA field; the synthesizer consumes the rules as pre-synthesis text normalization (\u201cAelyth\u201d spoken as \u201cAY-lith\u201d), so every engine — local or cloud — inherits the same corrections."
  ));

  out.push(h2("7.4 Cross-Segment Acoustic Continuity"));
  out.push(p(
    "A chapter boundary must not sound like a new narrator. For **ElevenLabs**, continuity uses the verified previous_request_ids chaining technique from the Rev 1 spike: each synthesis request references the previous one, carrying prosody and timbre across segment boundaries. For **Pocket TTS**, continuity is structural rather than negotiated: the narrator voice is a fixed safetensors voice pack, so identity is identical across chapters by construction; loudness is pinned by the mastering chain (7.5), and a consistent synthesis temperature is recorded per project so renders are reproducible. The audiobook report records which continuity mode was used, because ACX reviewers care about consistency, and so should the writer."
  ));

  out.push(h2("7.5 The Three-Layer ffmpeg Mastering Chain"));
  out.push(p(
    "Mastering is deterministic post-processing, all of it through the ffmpeg chain that already ships in v2.3 — the same binary the Rev 2 video removal (section 3.2) spares for audio. The chain runs in fixed order per chapter segment and once more across the joined master."
  ));
  out.push(...tbl(
    "Mastering chain layers",
    ["Layer", "Process", "Parameters"],
    [
      ["1. Ambience ducking", "Sidechain compression: the narration track ducks the ambient soundscape bed", "Duck depth \u221212 dB under speech, release 400 ms; the v2.3 ambient-loop feature feeds this bed"],
      ["2. Loudness normalization", "EBU R128 integrated loudness normalization", "Target \u221218 LUFS (upper end of the ACX RMS window), measured per chapter and on the master"],
      ["3. True-peak limiting", "Transparent limiter as the final stage", "Ceiling \u22123.1 dBFS true peak — 0.1 dB of engineering margin below the ACX \u22123 dB peak ceiling"],
    ],
    [22, 44, 34]
  ));

  out.push(h2("7.6 ACX Compliance Audit and SMIL Read-Along Export"));
  out.push(p(
    "The **ACX audit** meters every chapter and the whole master against the retail audio standard — RMS between \u221223 and \u221218 dB, peaks at or below \u22123 dB, noise floor at or below \u221260 dB, 44.1 kHz, MP3 CBR at 192 kbps or higher — and produces a red/green report per criterion with the measured values. The audit is deterministic: the mastering chain parameters above are chosen so a clean synthesis passes by construction, and the report proves it. The second retail surface is **read-along**: chapters export as EPUB 3 Media Overlays, pairing each text fragment with its audio segment in a SMIL file so Apple Books, Kobo, and other readers can highlight text as it is narrated — the same confirmed manuscript and the same audio master, no re-authoring."
  ));

  // ── 8. Plot Structures & Author Persona Plugins ───────────────────────────
  out.push(h1("8. Plot Structures & Author Persona Plugins"));
  out.push(p(
    "Structures and personas become **JSON plugins** rather than compiled code: the engine loads them from the plugin directory inside the .authr container, validates them against a published schema, and offers create/edit/duplicate in the UI. The shipped library is the floor, not the ceiling — a writer can capture a favorite author's cadence as a persona card without waiting for a product release."
  ));
  out.push(...tbl(
    "The 16 shipped plot structures (14 classics + 2 serial-native)",
    ["Structure", "Shape", "Best fit"],
    [
      ["Three-Act Structure", "Setup / confrontation / resolution", "General long-form default"],
      ["Hero's Journey", "12-stage mythic cycle", "Fantasy, adventure, transformation arcs"],
      ["Fichtean Curve", "Rapid crisis-and-resolution spikes to a major climax", "Thrillers, serialized tension"],
      ["Save the Cat Beat Sheet", "15 beats weighted by page", "Commercial genre fiction"],
      ["Kish\u014dtenketsu", "Four-part structure without antagonism (ki-sh\u014d-ten-ketsu)", "Quiet literary arcs, slice-of-serial pacing"],
      ["Seven-Point Story", "Hook through resolution in seven fixed points", "Tight novellas"],
      ["Freytag's Pyramid", "Five-act rise, climax, fall", "Tragedy, classics"],
      ["Story Circle", "Eight-step you/need/go/search/find/take/return/change", "Character-driven episodic work"],
      ["In Medias Res", "Open mid-action, backfill", "Cold-open thrillers"],
      ["Snowflake Method", "Sentence to paragraph to scene ladder", "Planners who grow outlines iteratively"],
      ["Fair-Play Mystery", "Clue plant / misdirection / reveal contract", "Whodunits"],
      ["Romance Arc", "Meet / attract / break / reunite (HEA contract)", "Romance in any setting"],
      ["Thriller Reversal", "Escalating reversals with a ticking clock", "Thrillers"],
      ["Frame Narrative", "Story within a story", "Collections, literary experiments"],
      ["**Micro-Arc Cascade** (serial-native)", "Chained 10\u201315 chapter arcs with reversal-and-pivot phases (section 6.5)", "**Web novel serials — the default structure for the serial format**"],
      ["**Serial Seven-Beat** (serial-native)", "Seven chapter beats over an arc skeleton (section 6.3)", "Serials with per-chapter structural checks"],
    ],
    [26, 42, 32]
  ));
  out.push(...tbl(
    "The 22 shipped author personas (7 built-ins carried from v2.3 + 15 new)",
    ["Persona", "Signature"],
    [
      ["The Weaver", "Emotional interiority, tactile and thermal sensory detail, slow anticipatory builds (v2.3)"],
      ["NightKing", "Restrained pacing, monsoon light and psychological tension (v2.3)"],
      ["Velvet", "Intimate, confessional proximity prose (v2.3)"],
      ["Hitchcock", "Ticking-clock suspense construction (v2.3)"],
      ["Lovecraft", "Cosmic dread and unreliable perception (v2.3)"],
      ["Poe", "Obsessive first-person descent (v2.3)"],
      ["BlackMirror", "Speculative near-future detachment (v2.3)"],
      ["Progression Fantasy", "Power systems, ranks, and earned payoffs — the web-novel default pairing"],
      ["Xianxia Flow", "Cultivation cadence, honor registers, meditative action"],
      ["Serial Sprinter", "Short sentences, mobile-first paragraphs, clean scene cuts"],
      ["Staccato Thriller", "Clipped declaratives, hard scene jumps"],
      ["Rom-Com Banter", "Quick-witted dialogue exchanges and comedic beats"],
      ["Cozy Voice", "Warm register, low-threat mysteries, community texture"],
      ["Grimdark", "Moral weight, consequence-heavy narration"],
      ["LitRPG System", "Interfaces, stats, and system-voice framing"],
      ["Hardboiled", "Terse first-person, sensory urban detail"],
      ["Space Opera", "Wide vistas, political scale, operatic stakes"],
      ["Heist Caper", "Ensemble competence, intercut timelines"],
      ["Domestic Suspense", "Suburban surfaces, intimate dread"],
      ["Literary Slow", "Long breath, close consciousness, image-forward"],
      ["Gothic", "Decaying settings, inherited secrets, dread"],
      ["Coming of Age", "Voice-driven growth, bittersweet resolution"],
    ],
    [26, 74]
  ));
  out.push(p(
    "A plugin file is small, schema-validated, and self-describing. The persona schema below is the complete contract: the drafting prompt assembles from these fields, and the cliché cleaner reads bannedPatterns as an extra project-local pattern class."
  ));
  out.push(...codeBlock([
    '{',
    '  "id": "progression_fantasy",',
    '  "kind": "persona",',
    '  "name": "Progression Fantasy",',
    '  "version": "1.0.0",',
    '  "prompt_directive": "Clear power-system logic, earned escalations, concrete sensory grounding.",',
    '  "sentence_length": "medium",',
    '  "sensory_focus": ["kinesthetic", "spatial"],',
    '  "pacing_curve": "step-rise with plateau valleys",',
    '  "cliffhanger_lean": "anticipation",',
    '  "banned_patterns": ["without further ado", "needless to say"]',
    '}',
  ]));

  // ── 9. Project Formats ───────────────────────────────────────────────────
  out.push(h1("9. Project Formats"));
  out.push(p(
    "Eight Rev 1 formats plus the new serial format make nine. A format is a profile over the same container: it presets word envelopes, chapter organization, validation suites, and export targets, while every engine capability remains available in every format."
  ));
  out.push(...tbl(
    "The nine project formats",
    ["Format", "Typical scale", "Primary outputs"],
    [
      ["Novel", "70,000\u2013120,000 words", "EPUB 3 (KDP), print interior PDF, audiobook"],
      ["Novella", "17,500\u201340,000 words", "EPUB 3, print interior, audiobook"],
      ["Short Story", "up to 7,500 words", "EPUB 3, submission-friendly manuscript layout"],
      ["**Web Novel Serial** (new)", "1,000\u20132,000-word chapters to targetChapters (default 100)", "Platform chapter packages (section 6.13), volume EPUB 3, per-chapter audio"],
      ["Audio Drama", "Script + full audio production", "Master audio, SMIL read-along"],
      ["Screenplay", "Feature or episode length", "Industry-layout screenplay export"],
      ["Non-Fiction", "Variable", "EPUB 3, print interior PDF"],
      ["Poetry & Prose Collection", "Per-piece organization", "EPUB 3, print interior PDF"],
      ["Graphic / Comic Script", "Issue or volume", "Panel-script export with beat notes"],
    ],
    [22, 34, 44]
  ));

  // ── 10. .authr Project Container ──────────────────────────────────────────
  out.push(h1("10. The .authr Project Container"));
  out.push(p(
    "The .authr container (Module 06) is a zip archive — one file, whole project, portable across devices and survives application updates. It carries a manifest that declares its schema version; openers migrate forward on load, never silently downgrade, and always write a backup before migrating. The v2.3 ZipUtils foundation is extended with atomic save (temp file + rename) so a crash mid-save can never corrupt a project."
  ));
  out.push(...tbl(
    ".authr container layout (schema v2)",
    ["Path", "Contents"],
    [
      ["/manifest.json", "Container and app versions, format, checksums, feature flags"],
      ["/manuscript/", "Confirmed chapters, archived L4 proposals, draft history for diffing"],
      ["/bibles/", "character bible, world bible, relationships, attachment panel records"],
      ["/lorebook/", "Key definitions injected into drafting prompts"],
      ["/ledger/", "chekhov.json (guns), questions.json (information levels 6.7), timeline.json"],
      ["/structure/", "outline.json (L2), beats.json (L3), arcs.json (serial micro-arcs)"],
      ["/audio/", "pronunciation rules with IPA, voice packs (safetensors), rendered segments, masters"],
      ["/plugins/", "structures/*.json, personas/*.json (section 8); the user-extensible directory"],
      ["/exports/", "epub/, print/, serial/, audio/ — regenerable outputs, stamped with generator version"],
      ["/metadata/", "KDP workbench data: blurb, keywords, categories, pricing (section 11)"],
    ],
    [24, 76]
  ));

  // ── 11. Publishing & Distribution Pipeline ────────────────────────────────
  out.push(h1("11. Publishing & Distribution Pipeline"));
  out.push(p(
    "The publishing pillar takes a confirmed manuscript to retail-ready artifacts. Amazon KDP is the anchor destination — the largest addressable market for a one-person publisher — with serial platforms covered by the paste-ready export profiles of section 6.13. The metadata workbench serves both."
  ));
  out.push(h2("11.1 EPUB 3 and the Print Interior"));
  out.push(p(
    "The EPUB 3 exporter extends the v2.3 EpubManager: a navigable TOC, semantic chapter markup, embedded cover slot, and metadata from the workbench. The gate is external: the export must pass epubcheck with zero errors before it is offered for upload. The print interior is rendered from the same confirmed manuscript to a 6\u00d79 in trim by default, with the inside-margin (gutter) ladder re-verified against KDP's print requirements during the Rev 1 spike."
  ));
  out.push(...tbl(
    "Print interior gutter ladder (6\u00d79 in trim, black-and-white interior)",
    ["Page count", "Inside margin (gutter)"],
    [
      ["24\u2013150 pages", "0.375 in"],
      ["151\u2013300 pages", "0.5 in"],
      ["301\u2013500 pages", "0.625 in"],
      ["501\u2013700 pages", "0.75 in"],
    ],
    [50, 50]
  ));
  out.push(h2("11.2 The Metadata Workbench"));
  out.push(p(
    "Retail metadata is a first-class editing surface, not a form at upload time. The workbench holds the KDP fields — title, subtitle, the seven keywords, the description/blurb, categories, and pricing — with an LLM drafting pass for blurb variants (writer confirms, always) and a pricing simulator that shows the 70% and 35% royalty brackets against the estimated print cost. Per-platform blurb lengths and tone variants are stored beside the KDP canonical so a serial can carry a punchier storefront blurb than its retail counterpart without maintaining two documents."
  ));
  out.push(h2("11.3 Export and Gate Matrix"));
  out.push(...tbl(
    "Export targets and their acceptance gates",
    ["Destination", "Artifact", "Gate"],
    [
      ["Amazon KDP (eBook)", "EPUB 3 with workbench metadata", "epubcheck zero errors"],
      ["Amazon KDP Print", "Print interior PDF (gutter ladder applied)", "KDP Print Previewer pass"],
      ["ACX / Audible", "Master MP3 192 kbps+ CBR, 44.1 kHz, chaptered; retail sample", "ACX audit all-green (section 7.6)"],
      ["Read-along retailers", "EPUB 3 with Media Overlays (SMIL)", "epubcheck zero errors; overlay timing sanity check"],
      ["Royal Road / Wattpad", "Chapter packages, author notes", "Serialization validator suite; scorecard export note"],
      ["GoodNovel / Dreame / Webnovel / Tapas", "Paste-ready chapter bodies with optional recap blocks", "Serialization validator suite; anti-withholding gate on free tier"],
    ],
    [26, 40, 34]
  ));

  return out;
}

module.exports = { build };
