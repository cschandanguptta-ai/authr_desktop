# ADR-0021: The Context Orchestrator — Nine-Layer Budgeted Assembly, Hierarchical Summary Cache, Token Spend Ledger

**Status:** Accepted (2026-11-15)
**Context:** v2.x sends the L4 Scene Composer a flat prompt: every confirmed chapter concatenated in full, plus bibles and lorebook, with no budget accounting (spec engine-enhancements §2.1: "flat 4,500 token context cap per agent invocation" in the reference implementation; the desktop port's flat concatenation is unbounded and grows linearly with canon — by chapter 30 the prompt alone can exceed every provider window). The v3.0 spec (ADR-0014) replaces this with a layered, stage-budgeted context architecture and hierarchical summarization with freshness decay.

## Decision

1. **ContextAssembler** (`engine/context/ContextAssembler.kt`): assembles a nine-layer prompt prefix — system, voice, scene plan, recent manuscript, outline, worldbible, retrieved, editorial, foreshadowing — each truncated to a stage-specific percentage of the available window (`maxContext − outputReserve`, Invariant 16: total ≤ available). Stage allocations follow the spec table (L4 Scene Composer 25% recent manuscript; L5 Style Polisher 40% buffer because it receives the full generated text as input; L6 Continuity Manager 35% recent + 20% worldbible). Overflow compresses the manuscript layer by level downgrade before touching any other layer.

2. **Freshness-ranked manuscript window** (spec §2.3): previous chapter full, two back medium, three-to-five back brief, six-plus oneliner; a chapter whose slice overflows the remaining budget downgrades one level (LEVEL_DOWNGRADE) and older chapters fall off when the budget is exhausted.

3. **SummaryCacheService** (`engine/context/SummaryCacheService.kt`): the four-level cache over the schema-v17 `summary_cache` table. Invalidation is SHA-256 source-hash comparison (spec §2.4) — any edit to a confirmed chapter invalidates its summaries at every level. Summaries are DETERMINISTIC (extractive, salience-ordered: paragraph position + name density + unresolved-tension markers) in S2; the spec's `fast_cheap` LLM summarizer route lands with the model-router work (S5). Extractive summaries satisfy the spec's PRESERVE list (names, events, foreshadowing, tensions) by construction — the summary sentences ARE the source's sentences. DAO optional (ADR-0018 pattern): without one the service computes fresh, uncached.

4. **Token spend ledger** (`engine/context/TokenSpendLedger.kt`): records per-invocation prompt/completion tokens with cost (spec MODEL_COSTS per 1M) and exposes per-stage and per-project totals plus pre-run stage estimates (spec estimate_stage_cost). Stored in the reserved `token_spend_ledger` editorial-memory slot (schema v17 reserved the constant; same fields as the spec table, no migration needed).

5. **L4 integration** (`engine/L4LlmDraftGenerator.kt`): the generator takes optional assembler + ledger + modelSpec; when wired (Main.kt: flash-class 128k window, 16k output reserve), the flat canon concatenation is replaced by the assembled prefix. The legacy flat path remains selectable (null assembler) — it is the QC before/after baseline, and writers on small local models may prefer the smaller predictable prompt.

## Consequences

- L4 prompts are bounded and stage-shaped: recent chapters keep full detail where continuity matters most, distant canon compresses to oneliners, and the bibles ride within explicit budgets instead of unbounded.
- The before/after QC comparison (S2 acceptance) runs ManuscriptQaSuite over drafts generated both ways — acceptance is assembler-context ≥ flat-context pass-rate, with token reduction reported alongside.
- The `retrieved` layer is the lorebook + scene-relevant extracts today; the epistemic-isolation-scoped vector retrieval (Invariant 12) lands in S3 with the RAG engine and slots into the same layer budget.
- Deterministic summaries mean summary generation costs zero tokens in S2; when the LLM summarizer arrives, the ledger will see its spend as SUMMARIZER-stage entries (economy model per the spec's stage preference table).

## Alternatives rejected

- *Truncate the flat context harder* — rejected; truncation from the end silently deletes the most recent chapters (the opposite of what continuity needs) and keeps no budget accounting.
- *Summaries in the pipeline DB as a new table* — rejected; schema v17 already ships `summary_cache` (S0) with the exact spec fields including SHA-256 invalidation; the service wraps it.
- *LLM-generated summaries now* — rejected; the S2 route must run CI-green without any provider credentials, and extractive summaries are verifiable in tests (they are substrings of the source by construction).

## References

`engine/context/ContextAssembler.kt`, `engine/context/SummaryCacheService.kt`, `engine/context/TokenSpendLedger.kt`, `engine/L4LlmDraftGenerator.kt`, `Main.kt`, `data/model/SummaryCache.kt` (schema v17), spec `upload/authr-os-v3-engine-enhancements.md` §2.1-2.5 (ADR-0014)
