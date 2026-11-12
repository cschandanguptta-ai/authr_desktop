# Beta Plan Briefing (OQ-12)

Program-level acceptance of v3.0 per plan §15: **"three beta authors each ship one serial and one retail title end to end."** This briefing structures the beta so a staffing decision is the only thing left to make. It is a briefing, not a decision — staffing/scheduling stays an open question (OQ-12) until a human names the participants and dates.

## What the beta must prove

The program's success metric is a full-pipeline claim, so the beta exercises exactly the surfaces that make it defensible — each author runs BOTH tracks:

1. **Serial track**: draft → confirm (writer gate, P1) → buffer/cadence/arc dashboard (P3) → platform paste exports (§6.13) → a retail package through the Publishing workbench (P5).
2. **Retail track**: a novel project through the same writer gate → metadata workbench (subtitle/keywords/blurb/pricing, OQ-9 authority) → the one-action `Package retail` (D-15 screen) → EPUB(s), print interior, `metadata.json` + `ship-report.md`.

Acceptance per author: the ship report reads **CLEAN** (every gate verdict passed — epubcheck zero errors, gutter ladder compliant), the writer-gate disclosures are understood, and one KDP upload attempt is made per artifact. The KDP Print Previewer pass (OQ-15) is recorded per title at upload time — the local gutter evidence does not substitute for it.

## Who runs it (OPEN — the decision this briefing asks for)

- **Three authors**, at least one writing a serial-only project and at least one writing a novel-only project; the third may do both tracks on one project.
- A support engineer for install + gate-tool setup (epubcheck is bundled with the app image since the D-15 slice; ffmpeg + espeak-ng are the author-installed externals for the audio track).

## What we measure (ADR-0010 discipline — measured, not estimated)

- Wall clock of the one-action package per title (gate hardware reference: ~7 s novel, ~13 s two-volume serial — the external epubcheck gate dominates; see tech-debt D-17 for the breakdown).
- Gate verdicts per artifact, and any UNVERIFIED/FAILED verdicts with the disclosed reason.
- Buffer/cadence state at ship time (the serial's §6.10 evidence rides the ship report).
- The author's friction notes per workspace — the beta's qualitative half.

## Known gaps the beta explicitly tolerates (pointers, not blockers)

- D-01: the Autonomous Studio background chain is inert on desktop — authors use the interactive flows; nothing in the beta metric requires the autonomous worker.
- D-13: audiobook rendering is engine-gated but not yet a screen; the beta's audio expectations are limited to the existing per-paragraph TTS.
- OQ-14: the Pocket TTS reference-laptop gate run is outstanding; the beta does not re-litigate it.

## Exit

The beta closes when all three authors' evidence lands in the release notes: per-author ship reports (CLEAN), per-title Previewer records, and the friction notes triaged into tech-debt rows. Until staffing is decided, OQ-12 remains the only open question this document depends on.
