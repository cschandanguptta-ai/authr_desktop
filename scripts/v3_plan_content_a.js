// Sections 1–5: Executive Summary, Vision, Rev 2 Corrections, Engine, Quality Systems
"use strict";
const { h1, h2, p, tbl } = require("./v3_plan_lib");

function build() {
  const out = [];

  // ── 1. Executive Summary ──────────────────────────────────────────────────
  out.push(h1("1. Executive Summary"));
  out.push(p(
    "Authr OS 2.3 is a multi-agent authoring workstation: six Weaver sub-agents (Executive Orchestrator, Plot Architect, Character Psychologist, World Builder, Scene Composer, Continuity Manager) run over a dynamic multi-provider LLM orchestrator, exposed through two workspaces — the **Interactive Wizard** for step-by-step co-writing and the **Autonomous Studio** for hands-free background production. Version 3.0 turns this workstation into a **one-person author publisher**: a single writer who plans, drafts, serializes, narrates, and ships finished products to retail and pay-per-chapter platforms without leaving the application."
  ));
  out.push(p(
    "The v3.0 programme stands on four pillars. First, a **writer-sovereign six-layer drafting engine** in which every generated artifact — bible records, outlines, beats, prose, style passes — is a proposal that becomes canon only when the writer confirms it. Second, **serialized fiction as a first-class project format**, available in both workspaces, with deterministic validators for hooks, chapter anatomy, cliffhanger rotation, and retention cadence. Third, a **studio-grade audiobook engine** built on Pocket TTS as the local synthesis engine, with IPA pronunciation, cross-segment acoustic continuity, a three-layer ffmpeg mastering chain, ACX compliance auditing, and SMIL read-along export. Fourth, **publish-ready output**: EPUB 3 for Kindle, print interiors with a verified gutter ladder, and paste-ready chapter packages for serial platforms."
  ));
  out.push(p(
    "This document is **Revision 2** of the improvement plan. Rev 1 contained four scope decisions that are corrected here before implementation begins: scene auto-drafting is **retained** (not removed) behind a writer-confirmation gate; the feature actually being removed is the **Scene Automation & Video tab** in Interactive Mode; the local TTS engine is **Pocket TTS** (replacing Kitten TTS); and **web novel serialization is added to both modes** and promoted from the post-P5 backlog into the main roadmap as phase P3. Table 1 summarizes the corrections."
  ));
  out.push(...tbl(
    "Rev 2 scope corrections relative to the Rev 1 draft",
    ["Area", "Rev 1 draft decision", "Rev 2 decision", "Primary rationale"],
    [
      ["Scene auto-drafting (engine Level 4)", "Removed; replaced by Writer Studio + Live AST Annotator", "**Retained.** L4 Scene Auto-Drafter stays the productivity core; drafts land as PROPOSED and become canon only on writer confirmation", "Solo serial authors need throughput; sovereignty is achieved by gating, not removal"],
      ["Scene Automation & Video tab (Interactive Mode)", "Not addressed", "**Removed** in v3.0 (UI, view-model state, video rendering path, exports)", "Video muxing is the highest-maintenance, lowest-core-value subsystem; v3.0 is a publishing platform, not a video marketing suite"],
      ["Local TTS engine", "Kitten TTS (cloud HF Space + 762 KB native lib)", "**Pocket TTS** (Kyutai, MIT): 100M-parameter local engine, voice cloning, real-time on CPU, streaming", "Offline, private, multilingual, no shared HF Space queue; see section 7"],
      ["Web novel serialization", "Royal Road and serial platforms deferred to post-P5", "**Full format in both modes**, promoted to phase P3 of the main roadmap", "Pay-per-chapter is where the one-person-publisher model compounds; v2.3 already seeds the data model"],
    ],
    [22, 26, 28, 24]
  ));
  out.push(p(
    "Delivery is planned as six phases (P0–P5) totalling **32 person-weeks**, each phase closing on a measurable acceptance gate (section 13). This revision supersedes the Rev 1 plan; all other Rev 1 content — the cliché cleaner, the Chekhov ledger, the .authr container, the KDP pipeline — carries forward unchanged except where explicitly restated."
  ));

  // ── 2. Product Vision & Guiding Principles ────────────────────────────────
  out.push(h1("2. Product Vision & Guiding Principles"));
  out.push(p(
    "The product thesis is the **one-person author publisher**. A single writer operates as an entire publishing house: editorial (bibles, outlines, beats), production (prose, style passes), serialization (chapter packaging and cadence), audio (narration and mastering), and distribution (retail and platform exports). Authr OS 3.0 supplies the staff — six agents, validators, ledgers, and exporters — while the writer remains the sole editor-in-chief. Every design decision in this plan is tested against a single question: does it increase what one writer can plan, produce, and publish without giving up creative control?"
  ));
  out.push(p(
    "**Principle 1 — Writer sovereignty.** The application keeps the v2.3 contract \u201cAI proposes, human approves & refines\u201d and hardens it into a data-level rule: no text enters the manuscript canon without an explicit **writer_confirmed** flag. Rev 2 restates this deliberately: retaining the Level 4 Scene Auto-Drafter is safe precisely because its output is a proposal that the writer opens, edits, and confirms — auto-drafting accelerates the writer; it never replaces the writer's signature."
  ));
  out.push(p(
    "**Principle 2 — One manuscript, many formats.** The same confirmed manuscript source flows to every output: EPUB 3 and print interior for retail, chapter packages for serial platforms, and the audiobook pipeline for audio. Serialization is not a separate product; it is a view and a validation profile over the same project container."
  ));
  out.push(p(
    "**Principle 3 — Local-first with escape hatches.** Where a task can run locally, it does: Pocket TTS synthesizes narration on-device, validators and ledgers are pure local computation, and the .authr container keeps the whole project portable. Cloud LLM providers and cloud TTS remain configurable per agent for scale and quality, never as a hard dependency."
  ));
  out.push(p(
    "**Principle 4 — Deterministic guardrails around stochastic engines.** Language models propose; deterministic code decides what passes. Cliché detection, hook validation, cliffhanger signatures, anatomy ratios, and the Chekhov ledger are all implemented as local, testable, versioned checkers. This is what makes the guardrails auditable: the same chapter always produces the same audit result."
  ));
  out.push(p(
    "**Principle 5 — Publish-ready, not draft-ready.** An export is only done when it passes the destination's own gate: epubcheck for Kindle files, the KDP Print Previewer for interiors, the ACX audio meter for audiobooks, and the serialization validator suite for chapter packages. Internal success metrics are defined against those external gates, not against internal impressions."
  ));
  out.push(...tbl(
    "Principles and their design consequences",
    ["Principle", "Design consequence in v3.0"],
    [
      ["Writer sovereignty", "writer_confirmed flag on beats and chapters; PROPOSED/CONFIRMED status machine at engine Level 4; side-by-side style diffs; dismissable flags with mandatory notes"],
      ["One manuscript, many formats", "Single .authr container feeds EPUB 3, print PDF, serial chapter packages, and audio masters; format-specific exporters never fork the manuscript"],
      ["Local-first", "Pocket TTS local engine; local validators and ledger; plugin directory inside the project container; cloud providers per-agent, optional"],
      ["Deterministic guardrails", "154-pattern cliché linter, hook and cliffhanger signature checkers, 10/70/20 anatomy meter, Chekhov ledger — all local and unit-tested against golden corpora"],
      ["Publish-ready outputs", "Acceptance gates mirror external validators: epubcheck, KDP Print Previewer, ACX meter, serialization golden suite"],
    ],
    [26, 74]
  ));

  // ── 3. Revision 2 Scope Corrections ────────────────────────────────────────
  out.push(h1("3. Revision 2 Scope Corrections"));
  out.push(p(
    "Rev 1 of this plan misread two requirements and deferred a third that belongs in the core. This section is the authoritative scope baseline: four corrections, numbered SCR-1 through SCR-4. Nothing else in the Rev 1 plan changes. Because implementation has not started, these corrections carry zero rework cost; they are made now, before P0 begins."
  ));

  out.push(h2("3.1 SCR-1 — Scene Auto-Drafting Retained at Level 4"));
  out.push(p(
    "Rev 1 removed the Level 4 auto-expansion step — the component that turns confirmed beats into full prose scenes — and proposed Writer Studio and a Live AST Annotator as replacements. That was a misreading of the requirement. **Rev 2 retains the Level 4 Scene Auto-Drafter as the productivity core of the engine.** The sovereignty concern that motivated the removal is solved by a gate, not by amputation: every L4 draft is written into the chapter workspace with status **PROPOSED**; it becomes canon only when the writer opens it, edits or accepts it, and taps **Confirm Chapter**, which sets writer_confirmed = 1. Unconfirmed drafts never feed the continuity ledger, the bibles, or any export."
  ));
  out.push(p(
    "Writer Studio and the Live AST Annotator (section 4) are therefore **complementary surfaces, not replacements**: the Studio is where the writer reads, edits, and confirms auto-drafted chapters, and the Annotator paints quality and continuity signals over confirmed text. For the target user — a solo author sustaining two to three released chapters per week on a serial cadence — removing auto-drafting would collapse exactly the throughput v3.0 exists to protect."
  ));

  out.push(h2("3.2 SCR-2 — Removed: Scene Automation & Video Tab (Interactive Mode)"));
  out.push(p(
    "The feature actually being removed is the **Scene Automation & Video** tab in Interactive Mode. In v2.3 this screen is a vertical-video generator: the user uploads still images, sets a display time per image (1–30 s), optional Ken Burns slow zoom and per-image focal points, an aspect ratio (9:16 default), and neon equalizer overlays; the feature then muxes the project's synthesized audio with the image slideshow into a 1080×1920 video (YouTube Shorts / long-form audiobook video), optionally auto-splitting long-form output and exporting an SRT subtitle sidecar. Rendering runs through the RxFFmpeg binding and exports to the system media store."
  ));
  out.push(p(
    "Removal rationale, in priority order: (1) **product focus** — v3.0 is a publishing platform for writers; social-video marketing is a different product with a different audience; (2) **maintenance cost** — the video path is the single largest ffmpeg surface in the app (codec muxing, resolution handling, focal-point animation, equalizer rendering), and it is the least exercised path in the desktop port; (3) **opportunity cost** — the engineering weeks it consumes in every release are redirected to serialization and publishing, which sit directly on the revenue loop of the target user. Note what is **not** removed: the ffmpeg **audio** DSP chain (sidechain ducking, EBU R128 normalization, true-peak limiting) is kept in full — audiobook mastering depends on it (section 7.3) — and image handling for EPUB covers is unaffected."
  ));
  out.push(...tbl(
    "Scene Automation & Video removal touchpoints (v2.3 code baseline)",
    ["Surface", "v2.3 artifact", "Action in v3.0"],
    [
      ["UI", "SceneAutomationScreen.kt (556 lines): image grid, focal-point modal, duration slider, Ken Burns switch, aspect chips, equalizer styles, SRT toggle, auto-split, quality picker", "Delete; remove tab from Interactive Mode navigation"],
      ["View-model state", "14 MutableStateFlow fields (videoImages, videoImageFocalPoints, videoImageDuration, videoKenBurnsEnabled, videoAutoSplit, videoAspectRatio, videoEqualizerEnabled, videoEqualizerStyle, videoExportSrt, videoGenerationLogs, isGeneratingVideo, and companions)", "Delete fields and all collectors"],
      ["Render pipeline", "generateVideo() in StudioViewModel: RxFFmpegInvoke video muxing commands, per-image zoompan/focal filters, equalizer overlay filters, split logic", "Delete; keep RxFFmpegInvoke for the audio mastering chain only"],
      ["Exports", "MediaStore video export; SRT sidecar writer; quality presets", "Delete; on first v3.0 launch offer a one-time export of any previously generated SRT files, then purge video outputs"],
      ["Settings / prefs", "Video-related preference keys and restore paths in AutosaveManager", "Remove keys; migration ignores unknown keys (forward-compatible)"],
      ["Desktop port", "Shim surface for media-store video writes and video file dialogs", "Remove from the desktop bridge; reduces the port's ffmpeg surface to audio-only"],
      ["Logbook", "videoGenerationLogs feed strings in the developer console", "Remove feed; audio pipeline logs unchanged"],
    ],
    [16, 52, 32]
  ));

  out.push(h2("3.3 SCR-3 — Local TTS Engine: Kitten TTS Is Replaced by Pocket TTS"));
  out.push(p(
    "v2.3 ships Kitten TTS in two forms: a **cloud** client that drives a free shared Gradio HF Space over a queue-and-poll SSE protocol with retry, and a **local** native library (libkittentts-native.so, 762 KB) that exists only for the armeabi-v7a ABI. The practical defects are structural: the cloud form depends on a shared space with no availability guarantee; the local form is English-only, ships no arm64 build, and offers no voice cloning. **Rev 2 replaces both with Pocket TTS** (Kyutai Labs, MIT licence): a 100M-parameter local engine that runs in real time on a CPU, clones a narrator voice from a short WAV reference, streams unbounded text, and covers seven core languages with community models for twelve more, including Chinese, Japanese, Hindi, and Korean. Section 7 carries the full specification and engine matrix; the integration cost is bounded because v2.3 already abstracts TTS engines behind a manager interface."
  ));

  out.push(h2("3.4 SCR-4 — Web Novel Serialization Added to Both Modes"));
  out.push(p(
    "Rev 1 parked serial platforms behind P5 as a post-release extension. **Rev 2 promotes serialized fiction to a first-class project format, implemented in phase P3, available in both the Interactive Wizard and the Autonomous Studio.** Two facts drive the change. First, the product input for this revision — the Serialized Fiction Blueprint — specifies the structural, pacing, and retention mechanics of pay-per-chapter serialization in implementable detail (section 6 encodes it end to end). Second, v2.3 already seeds the data model: Book carries isSerialized, targetChapters (default 100), and releaseSchedule (default \u201cMon/Wed/Fri\u201d), and the Outline and Drafting workers already branch on isSerialized. The remaining distance to a complete format — validators, rotation tracking, micro-arc planning, buffer and cadence tooling, platform export profiles — is squarely inside v3.0 scope."
  ));

  // ── 4. Six-Layer Drafting Engine & Writer Studio ───────────────────────────
  out.push(h1("4. Six-Layer Drafting Engine & Writer Studio"));
  out.push(p(
    "The v3.0 drafting engine organizes the six Weaver agents into **six layers**, each consuming the previous layer's confirmed output. The layer contract makes the pipeline auditable: every artifact names its producing layer, its owning agent, and its confirmation state. Table 5 is the complete layer map; the paragraph that follows details Level 4, which Rev 2 explicitly retains."
  ));
  out.push(...tbl(
    "The six-layer drafting engine: layers, agents, and writer gates",
    ["Layer", "Name", "Owning agent", "Input → output", "Writer gate"],
    [
      ["L1", "Premise & Bible Intake", "World Builder, Character Psychologist", "Premise and wizard inputs → character and world bible records, lorebook keys", "Confirm each bible record; edits respected downstream"],
      ["L2", "Structure Planner", "Plot Architect", "Confirmed bibles → chapter-by-chapter structure from the 16-structure library (section 8)", "Review and approve outline (existing Save / Approve Outline flow)"],
      ["L3", "Beat Decomposer", "Plot Architect", "Approved structure → per-chapter beat sheets with plot-point markers", "Editable beat cards; beats confirmed per chapter"],
      ["L4", "Scene Auto-Drafter", "Scene Composer", "Confirmed beats + bibles + lorebook + continuity logs + persona → full prose scene draft in the target word envelope", "PROPOSED → CONFIRMED via writer_confirmed; the Rev 2 core (SCR-1)"],
      ["L5", "Style Polisher", "Executive Orchestrator", "Confirmed draft + persona style card → styled revision (cliché-suppressed)", "Side-by-side diff; accept, edit, or reject"],
      ["L6", "Continuity Auditor", "Continuity Manager", "Confirmed text → timeline entries, Chekhov ledger updates, contradiction flags", "Flags are dismissable only with a writer note"],
    ],
    [7, 17, 20, 33, 23]
  ));
  out.push(p(
    "**Level 4 in detail.** The Scene Auto-Drafter assembles its prompt from confirmed material only: the chapter's confirmed beats, the active character sheets, matching lorebook keys (the existing keyword-injection mechanism), the continuity summaries of all confirmed chapters, the selected persona card, and the format's word envelope — 1,000–2,000 words for serial chapters, 2,000–4,000 for long-form formats. The draft is written to the chapter workspace with status PROPOSED. The writer edits freely; confirming the chapter freezes the text, sets writer_confirmed = 1, and triggers L6 annotation and ledger updates on the frozen text. An L4 draft may also be **regenerated** (new proposal) or **archived**; archived drafts are kept in the container for diffing but never feed downstream layers."
  ));
  out.push(h2("4.1 Writer Studio & Live AST Annotator"));
  out.push(p(
    "The **Writer Studio** is the v3.0 editing surface, evolved from v2.3's distraction-free manuscript editor. It adds a live margin rail that renders the **manuscript AST** — the analyzed representation of the confirmed text — as inline annotations. The Annotator runs entirely locally and deterministically; it annotates **confirmed text only**, so the ledger and statistics always reflect canon rather than proposals. In Interactive Mode the Studio opens per chapter from the drafting card; in Autonomous Mode it renders a batch review queue of PROPOSED chapters with accept/edit/confirm actions and keyboard-first navigation."
  ));
  out.push(...tbl(
    "Live AST annotator node types",
    ["AST node", "Annotates", "Writer action"],
    [
      ["Beat", "Coverage of each confirmed beat in the chapter; missing/weak beats flagged", "Open beat card; regenerate scene section or waive the beat with a note"],
      ["Cliché hit", "Every linter match with class, severity, and suggested rewrite", "Accept suggestion, edit manually, or whitelist the phrase project-wide"],
      ["Chekhov gun", "Introduced, escalated, and resolved promises tracked by the ledger", "Mark false positive, adjust severity, or schedule escalation"],
      ["Lore reference", "Lorebook keys matched in text; missing definitions proposed inline", "Accept the proposed lorebook entry or discard"],
      ["Cliffhanger signal", "Serial chapters: tier signature detected in the final 50 words", "Confirm the detected tier or pick another; re-run the signature check"],
      ["Continuity flag", "Contradictions against the timeline and bibles raised by L6", "Resolve in text or dismiss with a mandatory note"],
    ],
    [18, 46, 36]
  ));

  // ── 5. Prose Quality Systems ───────────────────────────────────────────────
  out.push(h1("5. Prose Quality Systems: Cliché Cleaner & Chekhov Ledger"));
  out.push(p(
    "Two deterministic systems bracket the stochastic engine: the **cliché cleaner** runs before and after generation (suppressing patterns when the model proposes them and linting them out of confirmed text), and the **Chekhov ledger** runs after confirmation, accounting for every narrative promise the text makes. Both are local, versioned, and unit-tested; neither depends on any cloud service."
  ));
  out.push(h2("5.1 Cliché Cleaner"));
  out.push(p(
    "The cleaner ships **154 patterns across six classes**, each with a severity (info, warn, error), a plain-language explanation, and in most cases a suggested rewrite. Patterns are regular expressions compiled once per project open. During generation, patterns are injected into the drafting prompt as hard constraints for every provider, and where the provider's API supports token biasing, the most frequent offender tokens are additionally suppressed at decode time. After confirmation, the linter re-scans the frozen text so the AST can render hits inline in the Writer Studio."
  ));
  out.push(...tbl(
    "Cliché cleaner pattern classes (154 patterns at launch)",
    ["Class", "Patterns", "Typical catch", "Example rewrite"],
    [
      ["Dialogue-tag crutches", "30", "\u201cshe said breathily\u201d, said-bookisms (\u201che growled\u201d, \u201cshe hissed\u201d) in tense scenes", "Action beat instead of an exotic tag: \u201cShe slammed the cup down. \u2018Ask me again.\u2019\u201d"],
      ["Filter words", "32", "she felt, he noticed, she realized, he saw that…", "Direct perception: \u201cThe cold seeped through her coat\u201d"],
      ["Purple prose & overwrought metaphor", "28", "\u201ca symphony of shadows\u201d, \u201cdance of dust motes\u201d", "One concrete image, stated plainly"],
      ["Body-cliché cluster", "24", "eyes widened, breath hitched, heart hammered, knees buckled", "Situation-specific physical response"],
      ["Plot-formula phrases", "18", "little did she know, unbeknownst to, upon further inspection", "Delete or dramatize the knowledge gap"],
      ["Web-novel filler", "22", "without further ado, needless to say, recap openers (\u201cpreviously…\u201d), mirror-confirmation filler", "Delete; recaps belong to the optional recap block, not the prose"],
    ],
    [24, 10, 34, 32]
  ));
  out.push(p(
    "Token-bias capability differs by provider, and the cleaner adapts per route. The capability matrix was verified against each provider's API surface in the Rev 1 spike; the load-bearing fact is that **Gemini exposes no logit-bias parameter**, so for Gemini routes the prompt constraint plus the deterministic linter are the only enforcement layers — which is acceptable because the linter, not the bias, is the gate that decides what ships."
  ));
  out.push(...tbl(
    "Provider token-bias capability matrix",
    ["Provider route", "Decode-time bias support", "v3.0 enforcement strategy"],
    [
      ["OpenAI-compatible (Groq, custom endpoints)", "Yes — logit_bias on completion APIs", "Map the top cliché tokens to a −5 bias plus prompt constraints plus linter"],
      ["Ollama (local / cloud endpoint)", "Yes — via OpenAI-compatible options", "Same as OpenAI-compatible"],
      ["Mistral", "No logit_bias in the current API", "Prompt constraints plus deterministic linter"],
      ["Gemini", "**No logit_bias parameter** (verified)", "Prompt constraints plus deterministic linter; the linter is the compliance gate"],
    ],
    [30, 30, 40]
  ));
  out.push(h2("5.2 Chekhov Continuity Ledger"));
  out.push(p(
    "The ledger operationalizes the \u201cgun on the wall\u201d rule: every promise the text makes — a weapon described in detail, a debt, a skill, a named grudge, an unanswered question — is a ledger entry with a lifecycle. L6 extracts candidate guns from confirmed text (proposal by LLM, confirmation deterministic against the extraction rules), and the writer confirms or rejects each entry in the Studio rail. Guns the writer confirms then move through a strict state machine; the **final-act recovery gate** blocks a project's compile step while any confirmed gun is still unfired, so no manuscript ships with an orphaned promise unless the writer explicitly waives it with a note."
  ));
  out.push(...tbl(
    "Chekhov ledger states and transitions",
    ["State", "Meaning", "Transition trigger", "Guard"],
    [
      ["INTRODUCED", "Promise made in confirmed text; entry created with chapter reference", "L6 extraction confirmed by writer", "Duplicate detection on name + description similarity"],
      ["ESCALATED", "The gun is re-emphasized (raised stakes, added detail) in a later confirmed chapter", "L6 match on escalation rules; writer confirms", "Escalation history kept as a chapter list"],
      ["RESOLVED", "The promise fires; payoff chapter referenced", "Writer marks resolution from the AST rail", "Resolution must occur in confirmed text; a quote is archived with the entry"],
      ["ORPHANED", "Confirmed gun unfired at the start of the final act", "Automatic when final act begins", "**Final-act recovery gate:** compile is blocked until the gun is resolved or waived"],
      ["WAIVED", "Writer explicitly accepts the orphan (deliberate ambiguity, sequel hook)", "Writer action with mandatory note", "Waivers are listed in the ship report; never silent"],
    ],
    [16, 30, 28, 26]
  ));
  out.push(p(
    "The ledger is stored as ledger/chekhov.json inside the .authr container (section 10) and mirrored to a SQL table for query (Appendix A). Regression testing uses a seeded corpus: manuscripts with known planted guns must produce known ledgers, which keeps the extraction rules honest across releases."
  ));

  return out;
}

module.exports = { build };
