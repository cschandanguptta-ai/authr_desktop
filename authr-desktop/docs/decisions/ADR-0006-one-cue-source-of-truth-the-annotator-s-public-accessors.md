# ADR-0006 — One cue source of truth: the annotator's public accessors

**Status:** Accepted (P3)
**Context:** The balance meter and ending advisor need the same deterministic signals the Writer Studio rail paints; duplicated cue sets would drift.
**Decision:** `LiveAstAnnotator.beatCueList(beat)` and `LiveAstAnnotator.classifyEnding(text)` are the single cue/classifier source; `BalanceMeter` and `EndingAdvisor` consume them. The rail's anchors, the persisted `endingType`, and measured shares cannot diverge.
**Alternatives considered:** private cue copies in the serialization package (rejected — drift).
**Consequences:** Any cue change fans out to balance shares, advisor history, and the golden serial's planted endings simultaneously.
**Do not change unless:** the seven-beat model or the six ending types change in the blueprint — then `ChapterBeat`/`EndingTypes` move first.
**Evidence:** `engine/LiveAstAnnotator.kt` (accessors), `engine/serialization/BalanceMeter.kt`, `engine/serialization/EndingAdvisor.kt`.
