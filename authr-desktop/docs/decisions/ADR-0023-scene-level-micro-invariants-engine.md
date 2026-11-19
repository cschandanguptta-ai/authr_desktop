# ADR-0023: Scene-Level Micro-Invariants Engine

**Status:** Accepted (2026-11-19)
**Context:** The v3.0 spec's gate matrix adds five warning-severity scene gates — `pov_consistency` (0 POV shifts within scene), `tense_consistency` (0 tense shifts within scene), `dialogue_differentiation` (pairwise character similarity < 0.70), `opener_variety` (consecutive same-opener count ≤ 2), and `composite_drift` (per-scene composite < 0.30). The spec's reference implementation LLM-classifies POV and narrative distance per paragraph; no desktop port of these gates existed — ManuscriptQaSuite checked setups, repetition, clichés and pacing but nothing scene-structural.

## Decision

1. **SceneInvariantsEngine** (`engine/voice/SceneInvariantsEngine.kt`), pure-Kotlin deterministic annotators:
   - **POV**: paragraph-level cue scan (1st/2nd/3rd-limited/3rd-omni via pronoun sets + viewpoint-verb anchoring), with **head-hop detection** for 3rd-limited (two distinct viewpoint names doing viewpoint verbs in one scene). A scene is a maximal run of paragraphs without a break cue (`***`, `###`, `---`); shifts *inside* a run are violations, shifts across breaks are craft.
   - **Tense**: dominant-tense vote per paragraph over past/present verb-cue lists, **quotes stripped** — narration must hold, speech may do anything. Flips inside a scene run are violations.
   - **Dialogue differentiation** (spec §1.5 `check_dialogue_differentiation`): attributed dialogue extracted per character (speech tags + action beats, alternating-reply convention as fallback), pairwise cosine over the dialogue feature subset (sentence-length signature, contraction rate, 1st/2nd-person rates, function-word profile); > 0.70 flags `DIALOGUE_VOICE_COLLAPSE`. Characters with < 25 words of dialogue are skipped (a single line cannot evidence a voice).
   - **Opener variety**: reuses the S1 extractor's sentence splitter; 3+ consecutive same-word openers flags.
   - **Composite drift**: mean of POV/tense/opener violation rates, warning at ≥ 0.30 per the gate matrix.

2. **QA wiring** (`engine/pipeline/ManuscriptQaSuite.kt` §7): all five gates run per confirmed chapter and emit `Finding(WARNING)` rows (`POV`, `TENSE`, `DIALOGUE`, `OPENER`, `COMPOSITE_DRIFT`) — they surface in the stage-8 report and the writer's queue without blocking (warning severity, per the spec's gate matrix).

3. **Determinism first**: no LLM classification in this port. False positives cost the writer a glance in the findings list; the LLM-classified tier (narrative_distance, show_tell) stays on the S5 model-router roadmap behind the same `Finding` surface.

## Consequences

- Stage 8's QA report now catches scene-structural breakage (POV/tense whiplash, head-hops, same-voice dialogue, opener monotony) that previously sailed through.
- The gates respect scene boundaries — a deliberate POV switch after a `***` break is never flagged.
- Dialogue attribution is tag-based; heavily untagged or script-style dialogue yields fewer comparisons rather than wrong ones.
- `composite_drift` gives the pipeline one number per chapter for the dashboard, aligned with the spec's 0.30 warning threshold.

## Alternatives rejected

- *LLM classification per paragraph (the spec's route)* — rejected for S4: provider-dependent, non-deterministic, and unfalsifiable in tests. The deterministic scan covers the dominant violation classes (pronoun/verb cues); the LLM tier can refine later.
- *Per-sentence POV voting* — rejected: single sentences are routinely POV-neutral (dialogue tags, action beats); paragraph granularity matches how writers actually break scenes.

## References

`engine/voice/SceneInvariantsEngine.kt`, `engine/pipeline/ManuscriptQaSuite.kt` (§7), `engine/voice/VoiceFeatureExtractor.kt` (S1 sentence splitter, reused), spec `upload/authr-os-v3-engine-enhancements.md` §1.2 (narrative features), §1.5 (dialogue sub-engine), gate matrix v3 rows
