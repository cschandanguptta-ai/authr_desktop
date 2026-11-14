# ADR-0020: The Voice Engine — Cumulative Drift Tracking with Intentional Evolution

**Status:** Accepted (2026-11-14)
**Context:** v2.x measures voice fidelity only per chapter (TextMetrics deltas, ΔW_c > 6.0 style warnings). The v3.0 spec's central voice insight (engine-enhancements §1.3, spec ADR-0013): a drift of 4.0 per chapter across 30 chapters produces a completely different voice by the end of the book — and no individual chapter ever trips a per-chapter alert. Slow voice collapse is invisible without a cumulative metric. At the same time, authors intentionally shift voice (darker tone as stakes rise, telegraphic sentences in climax chapters); a naive cumulative alarm would flag every intentional arc as decay.

## Decision

1. **Spec-faithful drift math** (`engine/voice/VoiceDriftTracker.kt`): per-feature **tolerance-band** drift — 0.0 inside the band (default ±20% around the target), outside it `min(|m−low|, |m−high|) / max(high−low, 0.01)` clamped to 1.0 — then a **genre-weighted composite** (spec GENRE_FEATURE_WEIGHTS: literary weights figurative 2.0, romance weights dialogue 1.8, …) and the **cumulative EMA** with α = 0.1. Flag rule: `composite > 0.30 OR cumulative > 0.20`. Only deterministic features are weighted — POV/tense/show-tell are LLM-assisted (S2+) and deliberately absent from the composite.

2. **Intentional voice evolution** (spec §1.4): author-declared entries (persona, chapter range, adjusted features, reason) stored in the `voice_evolution` editorial-memory slot. A covered chapter's drift is computed against the **shifted target** (the evolution's adjusted features REPLACE the base persona targets) — not a blind suppression. The measurement still happens and is recorded with `evolutionApplied = 1`; the UI shows "intentional shift" instead of "drift warning" (writer sovereignty, ADR-0001).

3. **The VoiceEngine orchestration layer** (`engine/voice/VoiceEngine.kt`): measures each confirmed chapter against persona targets (S4 will populate numeric persona DNA) or the **rolling baseline** of earlier confirmed chapters; persists every measurement to `voice_drift_log` (append-only; the EMA seeds from the latest row across sweeps); exposes per-book verdicts with `worstFeatures` (top 3) for the UI. The DAO is optional (ADR-0018 nullable-DAO pattern): without one the engine still computes — a `SweepState` accumulator carries the history and running EMA inside a single `measureBook` pass.

4. **Stage-7 integration, complementary axis** (`PipelineOrchestrator.runEngagement`): when a voice engine is wired, the drift sweep runs BESIDE `RetentionAnalyzer` — retention measures craft signals (hooks, pacing, endings), drift measures voice fidelity; two separate systems, never merged into one score. Drift verdicts surface as gate **WARNINGS only** (`StageGates.engagement(report, voiceVerdicts)`): with calibration-stage ±20% bands, drift never auto-blocks a stage.

5. **Wiring**: `PipelineViewModel`/`PipelineOrchestrator` take an optional `VoiceEngine`; `Main.kt` builds it over `database.voiceDriftDao()` (schema v17, ADR-0019). `EditorialMemorySnapshot` carries the evolution slot payload; the memory store gains typed `writeVoiceEvolutions`/`voiceEvolutionsRaw`.

## Consequences

- The s1 direct route (17 tests) joins the CI suite: golden-serial stability (cumulative < 0.20 across 15 chapters), planted rich-corpus collapse (composite > 0.30 immediately, EMA climbs), evolution override (recorded as intentional, not flagged), gate warning-only semantics, slot round-trip with loud-fail parse, DAO-free contract.
- A test-design lesson recorded here: an impoverished planted corpus (bare declaratives) leaves genre-weighted features at 0 in both texts — no drift signal. The planted corpora now carry dialogue, figurative language and filter words so every weighted feature is measurable.
- First-chapter semantics: the first confirmed chapter of a book has no baseline and is recorded with zero drift; it becomes the rolling baseline for chapter 2 onward.
- Persona numeric voice DNA (spec catalog_personas voice_dna_json) lands in S4; until then the rolling baseline is the target and genre weights come from `Book.genre` free text.

## Alternatives rejected

- *Boolean evolution suppression* (flag = false, full stop) — rejected; the spec computes drift against the SHIFTED target, so a chapter that matches its evolution reads as on-voice, and a chapter that drifts even from its declared evolution still measures.
- *Drift as a hard stage-7 gate failure* — rejected; the tolerance bands are explicitly calibration-stage (spec advice: start ±20-30%), so blocking publication on them would violate the fail-loud-only-for-real-defects doctrine.
- *Merging drift into the retention index* — rejected; the axes measure different things and the spec keeps them separate.

## References

`engine/voice/VoiceDriftTracker.kt`, `engine/voice/VoiceEngine.kt`, `engine/pipeline/PipelineOrchestrator.kt` (runEngagement), `engine/pipeline/StageGates.kt` (engagement), `engine/pipeline/EditorialMemoryStore.kt` (evolution slot), `ui/viewmodel/PipelineViewModel.kt`, `Main.kt`, spec `upload/authr-os-v3-engine-enhancements.md` §1.3-1.4 (ADR-0013)
