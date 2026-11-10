# ADR-0001 — Writer sovereignty: proposals become canon only on writer confirmation

**Status:** Accepted (implemented P1; invariants hardened P2/P3)
**Context:** The engine's Level 4 Scene Auto-Drafter generates prose with an LLM. Before v3.0, generated text flowed straight into chapters. The v3.0 plan (Rev 2, SCR-1) retains auto-drafting as the productivity core but requires that nothing becomes canon without the writer.
**Decision:** All L4 output lands as `draftStatus = PROPOSED, writerConfirmed = false` (`worker/DraftingWorker.kt`; `WriterStudioViewModel.regenerateDraft`). Only `WriterStudioViewModel.confirmChapter` promotes text to canon (`writerConfirmed = true, draftStatus = CONFIRMED`), freezing it, running L6 annotation and ledger updates on the frozen text. Archived drafts (`ARCHIVED`) are kept on disk for diffing and never feed downstream layers (`engine/SixLayerPipeline.DraftArchive`). Every consumer filters through `WriterGate.canonChapters()` (`engine/DraftState.kt`).
**Alternatives considered:** none evidenced in-repo; the plan's Rev 1 "remove auto-drafting" alternative was rejected by Rev 2 (SCR-1) — gating, not removal.
**Consequences:** Extra confirm step per chapter; the P1 exit-gate assertion suite (`SixLayerPipelineTest`, `WriterStudioFlowTest`) proves no unconfirmed text reaches ledgers/exports; the anti-withholding and scorecard gates (P3) stack on the same canon filter.
**Do not change unless:** the plan itself is revised — the gate is the product's core promise. Any change must keep the seeded assertion suites green.
**Evidence:** `engine/DraftState.kt` (WriterGate), `ui/viewmodel/WriterStudioViewModel.kt` (confirm flow), `worker/DraftingWorker.kt`, tests above.
