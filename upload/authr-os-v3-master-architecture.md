# AUTHR OS v3.0 — Master Architecture & Engineering Specification

*Baseline Document — Version 3.0.0-draft*

---

## 1. Executive Summary

**AUTHR OS** is a local-first, full-lifecycle, AI-powered book production and reader-retention operating system. It operates as a **Hub-and-Spoke Deterministic State Machine** governed by strict mathematical invariants, transactional SQLite WAL + Vector RAG memory, multi-tier quality gates, and retail-compliant compilation engines.

### 1.1 What v3.0 Adds to the v2.1 Foundation

v3.0 preserves the **entire v2.1 architecture** (5 hardening upgrades, 12 ADRs, 14 invariants, 6-Layer Weaver Engine, 25×37×18 Craft Matrix) and adds **6 enhancement tracks**:

| Enhancement Track | Integration Point | New ADR |
|:---|:---|:---|
| **Cumulative Voice Drift & Intentional Evolution** | `retention_and_editorial.py`, Schema v2.2 | ADR-0013 |
| **Hierarchical Context Summarization & Token Budget** | `rag_engine.py`, `orchestrator.js`, Schema v2.2 | ADR-0014 |
| **Expanded Prose Feature Extraction (20+ metrics)** | `retention_and_editorial.py`, `blueprint_guards.py` | — (extends ADR-0004) |
| **Research Vault & Citation Management** | New module `research_vault.py`, Schema v2.2 | ADR-0015 |
| **Pre-Publication Compliance Gate** | New module `compliance_gate.py`, Schema v2.2 | ADR-0016 |
| **Dialogue Sub-Engine (Subtext & Arc Planning)** | `WEAVER_5_SCENE_COMPOSER`, new role `dialogue_director.md` | — (extends Weaver 5) |

### 1.2 Architectural Lineage

| Stream | Core Scope | v3.0 Status |
|:---|:---|:---|
| **Blueprint 2.0 & v3.0-Rev2** | SCR-1..4, Writer Sovereignty, 154-Pattern Cliché Cleaner, 3-Level Question Ledger, Anti-Withholding Gate, Kyutai Pocket TTS, `.authr` Container v2 | **Fully Preserved** |
| **ADRs 0001–0012** | 12 Load-Bearing Architectural Decision Records | **Fully Enforced**; 4 new ADRs added (0013–0016) |
| **V4 Weaver Multi-Agent Spec** | 6 Weaver Engines → 21 Specialist Roles via 2-Tier Routing Matrix | **Extended** with Dialogue Director role (22nd specialist) |
| **Schema v2.1** | Sub-chapter beat locking, causal dirty-tree, speech fingerprints | **Extended** to v2.2 with 8 new tables, 4 column additions |
| **v3.0 Enhancements** | Cumulative voice tracking, hierarchical summarization, research vault, compliance gate, token budget, expanded features | **NEW** |

---

## 2. Repository & Package Topology

```text
Authr_Studio_App/
├── craft_catalog.db                      # Tier 0 Read-Only Catalog (25 Formats × 37 Personas × 18 Engines)
├── package.json                          # Root workspace runner & scripts
├── launch_authr_studio.sh / .bat         # Cross-platform 1-click studio launchers
│
├── client/                               # React + Vite + Tailwind Authoring & Telemetry Studio
│   └── src/
│       ├── App.jsx                       # Studio shell, navigation & global state orchestrator
│       └── workspaces/
│           ├── WizardWorkspace.jsx       # Stage 0: Combinatorial Craft Matrix & Market Positioning
│           ├── BibleLedgerWorkspace.jsx  # Stage 1: Bibles, Chekhov Guns, Question Ledger & Lorebook
│           ├── BeatEditorWorkspace.jsx   # Stage 2: L1-L6 Beat-Locking Editor & Live AST Margin Rail
│           ├── RetentionTelemetryHud.jsx # Stage 3: 14-Pt Scorecard, Burstiness, Voice Drift & NEW: Research HUD
│           ├── ResearchWorkspace.jsx     # Stage 3b [NEW v3]: Research Vault, Source Manager & Citation Browser
│           └── PublishingWorkspace.jsx   # Stage 4: KDP/EPUB/Typst/ACX Audio, Serial Exporters & NEW: Compliance Gate
│
├── server/                               # Node.js Express API, SSE Bridge & File/DB Synchronizer
│   ├── server.js                         # REST API & SSE endpoints (hardened execFileSync)
│   ├── orchestrator.js                   # 6-Weaver → 22-Role Prompt Compiler & Context Assembler [ENHANCED v3]
│   ├── pipeline_service.js               # L1-L6 pipeline state machine & beat-surgical rewrite loop
│   ├── function_bridge.js                # Provider router (OpenAI/Ollama logit_bias vs Gemini/Mistral)
│   ├── workspace_service.js              # Path-contained I/O + SHA-256 DB/Markdown sync guard
│   └── token_budget_middleware.js        # [NEW v3] Per-invocation cost estimation & model routing
│
├── py_core/                              # Python Deterministic Guardrails, RAG & Retail Compilers
│   ├── db_engine.py                      # SQLite WAL engine, Schema v2.2 migrations & causal diffing
│   ├── craft_catalog.py                  # 25 Formats, 37 Personas, 18 Engines & Mode Polymorphism
│   ├── blueprint_guards.py               # 154-Pattern Cliché Linter, Hook Validator & Anti-Withholding Gate
│   ├── retention_and_editorial.py        # 14-Pt Scorecard, Burstiness, Filter Density, Voice Sep [ENHANCED v3: 20+ features, cumulative drift, evolution]
│   ├── rag_engine.py                     # Hybrid SQLite FTS + LanceDB vector retrieval [ENHANCED v3: hierarchical summarization, freshness decay]
│   ├── subagents.py                      # 22 Specialized Role definitions bound to 6 Weaver Engines [+1 v3]
│   ├── roles/*.md                        # 22 Deterministic Role System Prompts [+dialogue_director.md]
│   ├── research_vault.py                 # [NEW v3] Source ingestion, fact tagging, citation management
│   ├── compliance_gate.py                # [NEW v3] Plagiarism detection, trademark screening, sensitivity review
│   ├── token_budget.py                   # [NEW v3] Cost estimation, spend tracking, model selection optimization
│   ├── export_epub.py                    # EPUB3 + Measured SMIL Media Overlay compiler
│   ├── export_docx.py                    # Standard Manuscript / Trade DOCX compiler
│   ├── export_typst.py                   # Measured KDP Print Interior Gutter Ladder → PDF via Typst
│   ├── synthesize_audiobook.py           # Kyutai Pocket TTS + espeak-ng IPA + 3-Layer FFmpeg Master
│   └── test_blueprint_v2.py              # Load-bearing exit-gate regression suite (ADR-0007) [EXTENDED v3]
│
└── books/<Book_Slug>/                    # Per-Book Project Workspace & .authr Container Source
    ├── config.json                       # Project configuration, Craft Matrix & pipelineMode
    ├── story_bible.db                    # Transactional SQLite WAL Master (Schema v2.2)
    ├── story_bible.md                    # Synchronized human-readable Story Bible projection
    ├── memory_tracker.md                 # Synchronized Episodic State & Open-Loop projection
    ├── plot_timeline.md                  # Synchronized Chronology & Beat-Spine projection
    ├── lorebook/characters.md            # Character Registry + 7 Attachment Elements + Speech Vectors
    ├── research/                         # [NEW v3] Research Vault file store
    │   ├── sources.json                  # Ingested source metadata index
    │   └── citations.json               # Citation registry for non-fiction
    ├── chapters/chapter_01.md .. N.md    # Beat-anchored Markdown manuscripts
    └── exports/                          # Compiled EPUB, DOCX, Typst PDF, ACX MP3, Serial & .authr
```

---

## 3. End-to-End System Topology

```text
========================================================================================================
                              [ AUTHOR / CREATIVE DIRECTOR (HITL) ]
   • Gate 1: Concept & Craft Matrix Lock         • Gate 3: Beat & Chapter Confirmation (writer_confirmed=1)
   • Gate 2: Master Architecture & Bible Lock     • Gate 4: Pre-Flight Scorecard, EPUBCheck & ACX Approval
   • Gate 5 [NEW v3]: Compliance & Research Verification
========================================================================================================
                                                   │
                                                   ▼
========================================================================================================
                     [ AUTHR OS ORCHESTRATOR KERNEL (6 WEAVER RUNTIME ENGINES) ]
  ┌────────────────────────┬────────────────────────┬────────────────────────┬────────────────────────┐
  │ 1. Executive           │ 2. Plot Architect      │ 3. Character           │ 4. World Builder       │
  │    Orchestrator        │    (L2/L3 Structure)   │    Psychologist (L1)   │    (L1 Lore & Rules)   │
  ├────────────────────────┴───────────────┬────────┴────────────────────────┴────────────────────────┤
  │ 5. Scene Composer (L4 Beat Drafter)    │ 6. Continuity Manager (L6 Ledgers, AST & 14-Pt)         │
  │    + [v3] Dialogue Director sub-role   │    + [v3] Compliance Gate & Research Verification        │
  └────────────────────────────────────────┴──────────────────────────────────────────────────────────┘
========================================================================================================
     │                    │                    │                    │                    │
     ▼                    ▼                    ▼                    ▼                    ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ TIER 0       │  │ TIER 1-3         │  │ DETERMINISTIC    │  │ [v3] ENHANCED    │  │ [v3] NEW         │
│ CRAFT CATALOG│  │ .authr v2.2      │  │ GUARDRAILS       │  │ ENGINES          │  │ MODULES          │
│ • 25 Formats │  │ • SQLite WAL     │  │ • 154 Cliché AST │  │ • Hierarchical   │  │ • Research Vault │
│ • 37 Personas│  │ • Beat Locks     │  │ • Burstiness ≥4.2│  │   Context Summ.  │  │ • Compliance Gate│
│ • 18 Engines │  │ • Chekhov Ledger │  │ • Speaker Voice  │  │ • Cumulative     │  │ • Token Budget   │
│ • 4 Modes    │  │ • Question DB    │  │ • Hook Scanner   │  │   Voice Drift    │  │ • Dialogue Dir.  │
│ • 7/5 Beats  │  │ • Dirty-Tree     │  │ • Anti-Withhold  │  │ • 20+ Features   │  │ • Comp Intel     │
│              │  │ • Summary Cache  │  │ • Ending Rotation│  │ • Token Budget   │  │                  │
└──────────────┘  └──────────────────┘  └──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## 4. The Six-Layer Weaver Drafting Engine (L1–L6) with v3 Enhancements

```text
[MACRO PHASE: BOOK ARCHITECTURE]
  L1: Premise, Character Bible (7 Attachment Elements + Speech Vectors) & World Lorebook
   │  (Requires HITL Gate 1 Confirmation)
   │  [v3] Research Vault sources can be attached to lorebook entries
   ▼
  L2: Macro Plot Structure Mapping (1 of 18 Plot Engines + Micro-Arc Cascade Bounds)
   │
   ▼
  L3: Chapter Beat Decomposition & Pre-Draft Worksheet (Sections A–E)
   │  (Seeds 7 Fiction Beats or 5 Authority Non-Fiction Beats in chapter_beats table)
   │  [v3] Token budget estimation per chapter generated here
   ▼
[MICRO LOOP: PER-CHAPTER / PER-BEAT EXECUTION]
  L4: Scene Composer (Beat-Scoped Drafting → writes PROPOSED spans)
   │  • Assembles strictly scoped context: Active Characters, Location Rules, Immediate Prior Beat,
   │    Open Chekhov Guns, Due Questions, and Active Persona DNA.
   │  [v3] Context assembly uses hierarchical summarization with freshness decay:
   │       — Previous chapter: full text
   │       — N-2: medium summary (~30% of original)
   │       — N-3 to N-5: brief summary (~10%)
   │       — N-6+: one-liner synopsis
   │  [v3] Token budget allocator distributes context across 9 layers with stage-aware rebalancing
   │  [v3] Dialogue Director sub-role handles multi-character scenes with subtext & arc planning
   ▼
  L5: Style Polisher & Deterministic AST Linter
   │  • Runs 154-Pattern Cliché Cleaner, Sentence Burstiness (SD ≥ 4.2), Filter-Word Density (< 3/1k),
   │    and Speaker Voice Drift Check.
   │  [v3] Expanded to 20+ feature extraction (clause depth, punctuation rhythm, sentence opener variety,
   │       hapax ratio, figurative density, passive voice freq, narrative distance, show-vs-tell)
   │  [v3] Cumulative voice drift tracking (EMA, α=0.1): flags when cumulative > 0.20
   │  [v3] Voice evolution awareness: suppresses false drift alerts for intentional shifts
   │  • Presents side-by-side diff to Author → Author marks Beats CONFIRMED / LOCKED.
   ▼
  L6: Continuity & Scorecard Auditor (Triggered ONLY when writer_confirmed = 1)
      • Computes 7×0-2 (14-Pt) Scorecard. If clarity == 0 or payoff == 0, flags failing Beat(s)
        and loops back to L4/L5 for Surgical Beat Rewrite (preserving LOCKED beats).
      • Commits new Chekhov Gun transitions, Question Ledger updates, Chronology entries,
        SHA-256 contentHash, and LanceDB embeddings.
      [v3] Updates summary_cache for the completed chapter
      [v3] Logs token spend to token_spend_ledger
      [v3] Runs research fact cross-check if sources are attached
```

---

## 5. Mode-Polymorphic Pipeline (4 Modes)

### 5.1 Fiction 7-Beat Proportional Chapter Model (`FICTION_NARRATIVE`)

| Beat | Code | Function | Word-Share |
|:---:|:---|:---|:---:|
| 1 | `OPENING_HOOK` | Immediate sensory/situational anchor & opening disturbance | **10–15%** (combined 1–2) |
| 2 | `CONTINUITY_BRIDGE` | Grounds POV, time/place transition, immediate goal | *(included above)* |
| 3 | `OBJECTIVE_DRIVE` | Protagonist takes active step toward scene objective | **50–60%** (combined 3–5) |
| 4 | `CONFLICT_FRICTION` | Antagonistic force, obstacle, or interpersonal subtext | *(included above)* |
| 5 | `ESCALATION_COMPLICATION` | Stakes rise; initial tactic fails or costs increase | *(included above)* |
| 6 | `TURNING_POINT_PAYOFF` | Value shift (+ → − or − → +) or earned revelation | **20–25%** |
| 7 | `FORWARD_PULL_SIGNAL` | Closing micro-hook (DANGER, REVERSAL, ANTICIPATION, etc.) | **10–15%** |

### 5.2 Non-Fiction 5-Beat Authority Chapter Model (`NONFICTION_AUTHORITY`)

| Beat | Code | Function | Word-Share |
|:---:|:---|:---|:---:|
| 1 | `COUNTER_INTUITIVE_HOOK` | Challenges conventional wisdom with high-stakes problem/paradox | **10–15%** |
| 2 | `CORE_THESIS_FRICTION` | Defines root mechanism; why standard approaches fail | **20–25%** |
| 3 | `EMPIRICAL_CASE_PROOF` | Concrete case study, historical reconstruction, or cited data | **30–35%** |
| 4 | `FRAMEWORK_SYNTHESIS` | Distills the mental model, repeatable system, or diagnostic matrix | **15–20%** |
| 5 | `TACTICAL_ACTION_BRIDGE` | Immediate implementation exercise + cognitive bridge to next chapter | **10–15%** |

### 5.3 Script Visual & Interactive CYOA

- `SCRIPT_VISUAL`: Uses `SCENE_SLUG_ACTION`, `AUDIO_CUE_DIALOGUE`, or `PANEL_PAGE_BEAT` models depending on format (screenplay, audio drama, graphic novel).
- `INTERACTIVE_CYOA`: Uses `NODE_CHOICE_STATE` model with branching state tracking.

---

## 6. The Combinatorial Craft Matrix (25 × 37 × 18 = 16,650 Configurations)

### 6.1 The 25 Project Formats

| ID | Key | Name | Words | Chapters | Mode | Beat Model |
|:---|:---|:---|:---:|:---:|:---|:---|
| `FMT_01` | `novel_standard` | Standard Commercial Novel | 85,000 | 30 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_02` | `novella` | Commercial Novella | 30,000 | 12 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_03` | `short_story_collection` | Short Story Anthology | 55,000 | 15 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_04` | `light_novel` | Light Novel (Illustrated) | 50,000 | 18 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_05` | `web_serial` | High-Velocity Web Serial | 250,000 | 100 | `FICTION_NARRATIVE` | `7_BEAT_SERIAL` |
| `FMT_06` | `litrpg_progression` | LitRPG / Progression Fantasy | 120,000 | 45 | `FICTION_NARRATIVE` | `7_BEAT_SERIAL` |
| `FMT_07` | `nonfiction_trade` | Trade Non-Fiction (Big Idea) | 65,000 | 12 | `NONFICTION_AUTHORITY` | `5_BEAT_AUTHORITY` |
| `FMT_08` | `self_help_howto` | Actionable Self-Help / How-To | 48,000 | 10 | `NONFICTION_AUTHORITY` | `5_BEAT_AUTHORITY` |
| `FMT_09` | `memoir_narrative` | Narrative Memoir | 75,000 | 20 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_10` | `biography_historical` | Definitive Historical Biography | 105,000 | 24 | `NONFICTION_AUTHORITY` | `5_BEAT_AUTHORITY` |
| `FMT_11` | `technical_manual` | Technical / Engineering Manual | 70,000 | 16 | `NONFICTION_AUTHORITY` | `5_BEAT_AUTHORITY` |
| `FMT_12` | `academic_monograph` | Academic Monograph | 75,000 | 10 | `NONFICTION_AUTHORITY` | `5_BEAT_AUTHORITY` |
| `FMT_13` | `screenplay_feature` | Feature Film Screenplay | 22,000 | 45 | `SCRIPT_VISUAL` | `SCENE_SLUG_ACTION` |
| `FMT_14` | `tv_pilot_hour` | TV Drama Pilot (One-Hour) | 13,500 | 32 | `SCRIPT_VISUAL` | `SCENE_SLUG_ACTION` |
| `FMT_15` | `tv_pilot_halfhour` | TV Comedy Pilot (Half-Hour) | 7,500 | 18 | `SCRIPT_VISUAL` | `SCENE_SLUG_ACTION` |
| `FMT_16` | `stage_play` | Full-Length Stage Play | 19,000 | 12 | `SCRIPT_VISUAL` | `SCENE_SLUG_ACTION` |
| `FMT_17` | `audio_drama_script` | Multi-Cast Audio Drama Script | 45,000 | 10 | `SCRIPT_VISUAL` | `AUDIO_CUE_DIALOGUE` |
| `FMT_18` | `graphic_novel_script` | Graphic Novel Full Script | 35,000 | 8 | `SCRIPT_VISUAL` | `PANEL_PAGE_BEAT` |
| `FMT_19` | `comic_issue_script` | Standard Comic Issue Script | 5,500 | 22 | `SCRIPT_VISUAL` | `PANEL_PAGE_BEAT` |
| `FMT_20` | `manga_script` | Tankobon Manga Script | 28,000 | 8 | `SCRIPT_VISUAL` | `PANEL_PAGE_BEAT` |
| `FMT_21` | `picture_book` | Children's Picture Book (32p) | 750 | 16 | `FICTION_NARRATIVE` | `SPREAD_TURN_BEAT` |
| `FMT_22` | `chapter_book` | Early Reader Chapter Book | 8,500 | 10 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_23` | `middle_grade` | Middle Grade Novel | 45,000 | 22 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_24` | `young_adult` | Young Adult (YA) Commercial Novel | 72,000 | 28 | `FICTION_NARRATIVE` | `7_BEAT_FICTION` |
| `FMT_25` | `interactive_cyoa` | Interactive Fiction / CYOA | 50,000 | 60 | `INTERACTIVE_CYOA` | `NODE_CHOICE_STATE` |

### 6.2 The 37 Style Personas (4-Layer Voice DNA)

Each persona defines: `POV Mode`, `Tense Mode`, `Target Burstiness`, `Sentence Length Target`, `Sensory Priority`, `Banned Lexicon`.

| ID | Name | POV | Tense |
|:---|:---|:---|:---|
| `PER_01` | Hard-Boiled Street Noir | `FIRST_PERSON_IMMEDIATE` | `PAST` |
| `PER_02` | Clockwork Locked-Room Suspense | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_03` | Neuro-Sensory High-Tension Romance | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_04` | Screwball Witty Rom-Com Banter | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_05` | Grimdark Mud-and-Iron Mercenary | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_06` | Grand Mythic World-Weaver | `MULTI_POV_CLOSE_THIRD` | `PAST` |
| `PER_07` | Kinetic LitRPG & System Tactician | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_08` | Speculative Techno-Ethics Analyst | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_09` | High-Velocity Space Opera | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_10` | Warm Observational Cozy Sleuth | `FIRST_PERSON_RETROSPECTIVE` | `PAST` |
| `PER_11` | Clinical Forensic Procedural | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_12` | Claustrophobic Unreliable Interior | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_13` | Velvet Gothic Chiaroscuro | `DIRECT_SECOND_PERSON` | `PRESENT` |
| `PER_14` | Uncanny Cosmic Dread | `FIRST_PERSON_RETROSPECTIVE` | `PAST` |
| `PER_15` | Kinetic Street Urban Fantasy | `FIRST_PERSON_IMMEDIATE` | `PAST` |
| `PER_16` | Atmospheric Slow-Burn Tension | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_17` | Period-Authentic Historical Immersion | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_18` | Drawing-Room Wit & Deduction | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_19` | Passionate Vernacular & Pulp Realism | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_20` | Stark Paratactic Frontier Minimalist | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_21` | Upmarket Relational Crucible | `MULTI_POV_CLOSE_THIRD` | `PAST` |
| `PER_22` | Contemplative Acoustic Literary | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_23` | High-Stakes Fierce Immediate Voice | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_24` | Buoyant Wonder & Tactical Adventure | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_25` | Kinetic Martial & Daoist Progression | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_26` | High-Density Chrome & Neural Wire | `FIRST_PERSON_IMMEDIATE` | `PRESENT` |
| `PER_27` | Clipped Tactical CQB Operator | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_28` | Rhetorical Cross-Examination Sparring | `CLOSE_THIRD_LIMITED` | `PAST` |
| `PER_29` | High-Pulse Triage & Diagnostic Urgency | `CLOSE_THIRD_LIMITED` | `PRESENT` |
| `PER_30` | Counter-Intuitive Big-Idea Synthesizer | `NONFICTION_AUTHORITATIVE` | `PRESENT` |
| `PER_31` | Direct Imperative Stoic Practitioner | `DIRECT_SECOND_PERSON` | `PRESENT` |
| `PER_32` | Warm Clinical & Reflective Guide | `DIRECT_SECOND_PERSON` | `PRESENT` |
| `PER_33` | Documentary Investigative Reconstructor | `NONFICTION_AUTHORITATIVE` | `PAST` |
| `PER_34` | Split-Lens Confessional Memoirist | `FIRST_PERSON_RETROSPECTIVE` | `DUAL_TIMELINE_SPLIT` |
| `PER_35` | Declarative Systems Architect | `NONFICTION_AUTHORITATIVE` | `PRESENT` |
| `PER_36` | Cinematic Showrunner Scriptwriter | `CINEMATIC_OBJECTIVE` | `PRESENT` |
| `PER_37` | Deadpan Satirical & Metafictional Wit | `OMNISCIENT_NARRATOR` | `PAST` |

### 6.3 The 18 Plot & Genre Engines

| ID | Name |
|:---|:---|
| `ENG_01` | Save the Cat (15-Beat Commercial Spine) |
| `ENG_02` | Hero's Journey (12-Stage Monomyth) |
| `ENG_03` | Kishōtenketsu (4-Act Twist & Synthesis) |
| `ENG_04` | Dan Harmon Story Circle (8-Step Loop) |
| `ENG_05` | Fichtean Curve (In-Media-Res Escalation) |
| `ENG_06` | 7-Point Story Structure |
| `ENG_07` | Freytag's 5-Act Pyramid |
| `ENG_08` | Classic Three-Act Structure (25/50/25) |
| `ENG_09` | Romancing the Beat (4-Phase Romance) |
| `ENG_10` | Whodunit Fair-Play Mystery Puzzle Box |
| `ENG_11` | Ticking-Clock Thriller Engine |
| `ENG_12` | Heist / Caper Blueprint |
| `ENG_13` | Dual-Timeline Braided Convergence |
| `ENG_14` | Episodic Web-Serial Engine (Micro-Arc Cascade) |
| `ENG_15` | Non-Fiction Problem-Agitate-Solve (PAS) |
| `ENG_16` | Non-Fiction Argument-Evidence-Action (AEA) |
| `ENG_17` | Memoir Transformation Arc |
| `ENG_18` | Heroic Progression / Cultivation Ladder |

---

## 7. 2-Tier Agent Architecture (6 Weaver Engines × 22 Specialist Roles)

| Weaver Engine | Layers | Bound Roles | v3 Changes |
|:---|:---:|:---|:---|
| **1. `WEAVER_1_EXECUTIVE_ORCHESTRATOR`** | Stage 0, L5, Stage 4 | `market_scout.md`, `premise_synthesizer.md`, `style_editor.md`, `copy_desk.md`, `marketing_agent.md`, `ad_optimizer.md` | — |
| **2. `WEAVER_2_PLOT_ARCHITECT`** | L2, L3 | `plot_architect.md`, `chapter_scaffolder.md`, `cadence_controller.md` | Token budget estimation at L3 |
| **3. `WEAVER_3_CHARACTER_PSYCHOLOGIST`** | L1, L5 | `character_psychologist.md`, `dev_editor.md` | Voice evolution profiles |
| **4. `WEAVER_4_WORLD_BUILDER`** | L1, L3 | `world_builder.md`, `rag_director.md` | Research vault integration |
| **5. `WEAVER_5_SCENE_COMPOSER`** | L4 | `scene_composer.md`, **`dialogue_director.md` [NEW v3]** | Subtext calibration, pairwise voice similarity, conversation arc planning |
| **6. `WEAVER_6_CONTINUITY_MANAGER`** | L6, Stage 4 | `continuity_manager.md`, `chekhov_auditor.md`, `qc_auditor.md`, `audio_director.md`, `cover_designer.md`, `kdp_validator.md`, `storefront_exporter.md` | Compliance gate, research fact cross-check |

### 7.1 Deterministic Micro-Step Execution Protocol

For every agent invocation:
1. **Context Assembly** — Pulls strictly scoped records from `story_bible.db` and `rag_engine.py`. **[v3]** Uses hierarchical summarization with token budget allocation across 9 context layers.
2. **Mode-Polymorphic Prompt Compilation** — Injects `pipelineMode`, `PersonaPlugin` voice DNA, and `specialistRole` markdown into `AgentExecutionEnvelope`.
3. **Guarded Inference** — Executes LLM inference with provider-appropriate decode policies (ADR-0005). **[v3]** Token budget engine selects optimal model per task.
4. **Deterministic Post-Validation** — Parses output through `JsonStructuralEnforcer` / `blueprint_guards.py`. If validation fails on a specific beat, triggers surgical retry (max 2). **[v3]** Expanded to 20+ feature checks.
5. **Sovereignty Gate** — Stages output as `PROPOSED` until author confirms (`writer_confirmed = 1`). **[v3]** Cumulative drift score logged.

---

## 8. External Compliance Standards

1. **Amazon KDP Print Interior**: Dynamic gutter ladder (0.375 in to 0.750 in), ≥ 300 DPI assets, embedded fonts, zero widow/orphan via Typst.
2. **EPUB 3.3 & W3C Media Overlays**: Structural compliance via external `epubcheck` gate (ADR-0011) and measured SMIL timestamps (ADR-0010).
3. **ACX / Audible Retail Mastering**: -18.0 LUFS (within -23 to -18 dB RMS), true-peak ceiling -3.1 dBFS, noise floor ≤ -60 dB RMS, 44.1 kHz / 192 kbps CBR MP3.
4. **Web-Serial Platform Formatting (ADR-0009)**: Paste-ready exports for `royal_road`, `wattpad`, `goodnovel_dreame_webnovel`, `tapas`.

---

## 9. Architectural Decision Records (ADR-0001 through ADR-0016)

### Preserved from v2.1 (ADR-0001 to ADR-0012)

| ADR | Rule |
|:---|:---|
| **0001** | **Writer Sovereignty** — No AI artifact enters canon until `writer_confirmed = 1`. Extended to beat level. |
| **0002** | **Additive-Only Migrations** — Schema evolutions must be `ALTER TABLE ADD COLUMN` or `CREATE TABLE IF NOT EXISTS`. Atomic backup before migration. |
| **0003** | **Single-Source-Set Desktop Shim** — JVM Desktop port maps Android framework via thin JDBC/coroutine shims. |
| **0004** | **Deterministic Guardrails** — LLM never trusted to self-grade. Every gate combines deterministic NLP/AST computation with structured JSON rubrics. |
| **0005** | **Provider Decode-Time `logit_bias`** — OpenAI/Ollama: inject `logit_bias: -5` on cliché tokens. Gemini/Mistral: system prompt + deterministic post-linter only. |
| **0006** | **One Cue Source of Truth** — All margin annotations computed by single deterministic AST/span parser. |
| **0007** | **Load-Bearing Exit-Gate Tests** — `test_blueprint_v2.py` is non-negotiable release gate. |
| **0008** | **Serial Release State on Chapter Metadata** — Buffer tracking persisted directly on chapter metadata. |
| **0009** | **Paste-Ready Serial Exports** — Zero fragile third-party APIs; deterministic paste packages. |
| **0010** | **Measured SMIL Overlays** — Audio timestamps measured from WAV/MP3 segment durations, never estimated. |
| **0011** | **One-Action Retail Packager** — Pre-flight validation before emitting final bundle. |
| **0012** | **Beat-Scoped Surgical Rewrites & Causal Dirty-Tree** — SHA-256 sync, sub-chapter beat isolation, downstream invalidation. |

### New in v3.0 (ADR-0013 to ADR-0016)

| ADR | Rule | Rationale |
|:---|:---|:---|
| **0013** | **Cumulative Voice Drift & Intentional Evolution** — Track per-scene drift via EMA ($\alpha = 0.1$). Flag when cumulative drift > 0.20. `voice_evolution` table allows author-declared tonal shifts without false alerts. | Per-scene drift checks miss slow voice collapse across 200+ scenes in long-form works. |
| **0014** | **Hierarchical Context Summarization & Token Budget** — Replace flat 4,500 token cap with percentage-based budget allocation across 9 context layers. Multi-level summary cache (`full`, `medium`, `brief`, `oneliner`) with freshness-ranked sliding window. Invalidate cached summaries via `source_hash`. | Web serials (FMT_05, 100 chapters, 250k words) exceed flat cap effectiveness. Stage-aware context improves generation quality. |
| **0015** | **Research Vault & Citation Management** — `research_vault.py` ingests external sources (URLs, PDFs, notes), extracts facts, tags them to chapters, and verifies citations. Non-fiction formats (`FMT_07`–`FMT_12`) require verified citations before export. | Non-fiction represents 6 of 25 formats but had no source management infrastructure. |
| **0016** | **Pre-Publication Compliance Gate** — Before retail compilation, run: plagiarism similarity check, trademark name screening, content sensitivity review, AI-disclosure metadata injection. Results stored in `compliance_checks` table. Export blocked if unresolved `blocking` findings exist. | Emerging legal requirements for AI-generated/assisted content. No existing gate covered pre-publication legal risk. |

---

## 10. System Invariants (14 Preserved + 4 New)

### Preserved Invariants (1–14)

$$\text{Invariant 1:} \quad \forall c \in \text{Chapters}: c.\text{writer\_confirmed} = 0 \implies c \notin \text{RAG} \land c \notin \text{Ledgers} \land c \notin \text{Exports}$$

$$\text{Invariant 3:} \quad \text{Veto}(c) = \mathbb{I}(\text{clarity} = 0) \lor \mathbb{I}(\text{payoff} = 0) \lor \mathbb{I}(\text{totalScore} < 10)$$

$$\text{Invariant 5:} \quad \sigma_S \ge 4.2 \text{ words (sentence burstiness floor)}$$

$$\text{Invariant 6:} \quad \text{FilterDensity} = \frac{\text{Count(Class\_2\_Hits)}}{\text{TotalWords}} \times 1000 < 3.0$$

$$\text{Invariant 9:} \quad \text{endingType}(C_k) \in \text{HighIntensity} \implies \text{endingType}(C_{k+1}) \neq \text{endingType}(C_k)$$

$$\text{Invariant 11:} \quad \text{SHA256}(C_k^{\text{new}}) \neq C_k.\text{contentHash} \implies \forall m > k: C_m.\text{continuityDirty} = 1$$

*(Full list: see v2.1 invariants document — all 14 enforced without modification.)*

### New Invariants (15–18)

$$\text{Invariant 15 (ADR-0013):} \quad \text{CumulativeDrift}_{\text{EMA}}(C_k) = \alpha \cdot \text{drift}(C_k) + (1 - \alpha) \cdot \text{CumulativeDrift}(C_{k-1}), \quad \alpha = 0.1$$

$$\text{Flag when } \text{CumulativeDrift} > 0.20 \text{ AND no active } \texttt{voice\_evolution} \text{ covering chapter } k$$

$$\text{Invariant 16 (ADR-0014):} \quad \text{ContextTokens}(\text{assembled}) \le \text{Model.maxContext} - \text{OutputReserve}$$

$$\text{Invariant 17 (ADR-0015):} \quad \forall b \in \text{Books}: b.\text{pipelineMode} = \text{NONFICTION\_AUTHORITY} \implies \text{UnverifiedCitations}(b) = 0 \text{ before export}$$

$$\text{Invariant 18 (ADR-0016):} \quad \forall b \in \text{Books}: \text{BlockingComplianceFindings}(b) = 0 \text{ before retail compilation}$$

---

## 11. v3 Enhancement Integration Map

| Enhancement | Existing Module | What Changes | New Files |
|:---|:---|:---|:---|
| Cumulative voice drift | `retention_and_editorial.py` | Add EMA tracking loop, cumulative score to chapter row | — |
| Intentional voice evolution | `craft_catalog.py`, `orchestrator.js` | Evolution-aware profile loading, drift suppression | `voice_evolution` table (Schema v2.2) |
| 20+ feature extraction | `retention_and_editorial.py` | Add clause depth, punct rhythm, opener variety, hapax, figurative density, passive voice, narrative distance, show-vs-tell, sensory per-sense | — |
| Hierarchical summarization | `rag_engine.py` | Multi-level summary generation, freshness-ranked window, cache invalidation | `summary_cache` table |
| Token budget allocation | `orchestrator.js` | Replace flat cap with 9-layer % allocation, stage-aware rebalancing | `token_budget.py`, `token_budget_middleware.js`, `context_budget_log` + `token_spend_ledger` tables |
| Research Vault | — | New module: source ingestion, fact extraction, citation management, bibliography | `research_vault.py`, `research_sources` + `research_facts` tables |
| Compliance Gate | — | New pre-publication gate: plagiarism, trademark, sensitivity, AI disclosure | `compliance_gate.py`, `compliance_checks` table |
| Dialogue Director | `scene_composer.md` | New specialist role: subtext calibration, conversation arcs, pairwise voice similarity | `dialogue_director.md` |

---

## 12. Scope Corrections (SCR-1 through SCR-4) — Carried Forward

| ID | Directive | Status |
|:---|:---|:---|
| `SCR-1` | Retain L4 behind Writer Sovereignty (`PROPOSED → CONFIRMED`, `writer_confirmed = 1`) | **Enforced** |
| `SCR-2` | Remove video generation; FFmpeg for audio DSP only | **Enforced** |
| `SCR-3` | Kyutai Pocket TTS + `espeak-ng` IPA + `.safetensors` voice cloning | **Enforced** |
| `SCR-4` | Web Novel Serialization as first-class pipeline (4 exporters, anti-withholding) | **Enforced** |

---

*End of Master Architecture Specification.*
