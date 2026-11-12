# ADR-0017: D-13 — the Audiobook workbench ships; the production synthesizer never silently mixes engines

Date: 2026-11-12 · Status: accepted · Deciders: program agent (delegated decision — D-13 was the logged follow-up), no human in the loop — this ADR records the decision for audit

## Context

The P4 audiobook engine (`AudiobookRenderer`, pronunciation pass, mastering chain, ACX audit, SMIL export) was gate-proven but engine-only (D-13): no screen wired `render` into the app, the production Pocket TTS adapter (`AudiobookRenderer.Synthesizer` over `PocketTtsBridge`) did not exist — the gate ran through `EspeakSynthesizer` — and the writer-facing pronunciation review UI did not exist. Meanwhile `PocketTtsManager.synthesizeToFile` (the interactive paragraph-playback path) silently falls back to Edge TTS when the Pocket binary or pack is missing — correct for casual playback, catastrophic for a render that claims structural continuity.

## Decisions

1. **The Audiobook workbench ships as tab 9** (`AudiobookWorkbenchScreen` + `AudiobookViewModel`, the PublishingViewModel pattern): engine picker → pronunciation review (scan → approve/revoke, staged into the per-book container entry `authr/<bookId>/audio/pronunciation_rules.json`) → one-action render → the report card (per-chapter ACX verdicts, SMIL timing, master audit, speed factor). The engine catalog and ffmpeg/espeak/binary probes live in `Main.kt` (Context territory); the VM stays Context-free and JVM-unit-testable.
2. **The production adapter calls the bridge DIRECTLY — never the Edge-falling-back manager.** `PocketSynthesizer` (POCKET_STRUCTURAL) invokes `PocketTtsBridge.synthesizeToFile`; a missing binary or voice pack fails LOUD with a note naming the missing piece. Allowing the silent Edge fallback would mix engines inside one render while the report claims "identity identical across chapters by construction" — the continuity label must stay honest (plan §7.4). The writer's offline choice is the espeak engine, picked explicitly.
3. **Pronunciation approval stages immediately** (the OQ-9 authority pattern): the same container entry a real `.authr` export writes; `PronunciationStore` mirrors `WorkbenchStore`. Reload-on-reselect treats the staged rules as authoritative.
4. **Voice-pack distribution rides the ADR-0013 pattern (OQ-11 decided)**: packs live in `<data>/audio/voice_packs/<name>.safetensors`, per-user install documented in config-and-env, no runtime auto-download, and the bridge contract (`PocketTtsBridge`, the verified `pocket-tts` CLI invocation) is the frozen desktop-side interface — the pack FORMAT beyond that is Kyutai's external contract, not ours to freeze. The OQ-14 reference-laptop gate run remains the binary's own proof item.

## Evidence

- `AudiobookWorkbenchTest` 4/4 (Room-free fakes, fake synthesizer writing real WAVs + fake mastering + injected audit runner): lorebook scan → approval staging → container-entry reload; one-action render ALL GREEN (2 canon chapters, master audit, speed factor > 0); unavailable engine refused with its reason; render without a data root stays honest.
- `PocketSynthesizerTest` 2/2: loud failure naming the missing engine piece, no partial output, never an Edge round trip (by construction — the adapter never imports `EdgeTtsManager`); POCKET_STRUCTURAL + temperature 0.6 pinned.
- Full suite: d13 route 68/68, p5 59/59, p6 47/47 — 238 total. App smoke under Xvfb: alive-until-kill, zero exceptions.

## Consequences

- A writer can now produce an ACX-audited audiobook from the UI with either engine; on machines without the Kyutai binary (this sandbox included) the Pocket option shows its reason and espeak renders.
- The AudioScreen's per-paragraph playback is unchanged (Edge fallback stays where continuity does not matter).
- The P4 gate's espeak-route evidence stands; the Pocket-route gate run on the reference laptop is still OQ-14's outstanding item — this slice ships the code path it will run through.
