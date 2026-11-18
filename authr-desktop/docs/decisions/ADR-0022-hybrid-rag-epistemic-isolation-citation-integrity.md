# ADR-0022: Hybrid RAG Retrieval with Epistemic Isolation and Citation Integrity

**Status:** Accepted (2026-11-18)
**Context:** v2.1's retrieval surface is the lorebook keyword scan — exact-key matching with no ranking, no semantic arm, and no research-source access. The v3.0 spec (engine-enhancements §2.1, Invariants 12 and 17) requires point-in-time RAG with **epistemic isolation** (`chapter_number <= k AND writer_confirmed = 1` — the model must never retrieve from unconfirmed drafts or future chapters) and, for nonfiction formats, a **citation-integrity export gate** (0 unverified citations). The spec's reference implementation pairs SQLite FTS with LanceDB vector search.

## Decision

1. **RagEngine** (`engine/rag/RagEngine.kt`): hybrid retrieval in pure Kotlin over the in-memory corpora —
   - **FTS arm**: BM25 (k1=1.2, b=0.75) over sentence-aligned ~120-word chunks; deterministic, index-free.
   - **Vector arm**: hashed bag-of-words TF embeddings (4096-dim) with cosine similarity; deterministic, no provider call. The LLM-embedding route can replace the embedder behind the same surface when the S5 model-router work lands.
   - **Fusion**: Reciprocal Rank Fusion (k=60) — rank-based combination, robust to the two arms' different score distributions.
   - **Budget**: results fill the layer-7 token budget best-ranked first; each result carries its arm provenance (`fts#n` / `vec#n`) for prompt transparency.

2. **Epistemic isolation is structural, not a post-filter** (Invariant 12): `buildCorpus` excludes unconfirmed or future chapters *before chunking*, and `retrieve` re-applies the filter defensively on any corpus it is handed. Isolation can therefore not leak via scoring order or tie-breaks.

3. **Layer-7 integration** (`engine/context/ContextAssembler.kt`): `assemble()` accepts an optional `ragEngine`; when wired, the RETRIEVED layer is the hybrid retrieval over confirmed canon + research sources, queried by the spec's `build_retrieval_query` (beat sheet + lorebook keys). Unwired, the layer stays the S2 lorebook surface — the fallback for tests and small local routes. `Main.kt` wires the engine into the L4 generator path.

4. **CitationIntegrityChecker** (`engine/rag/CitationIntegrityChecker.kt`): implements the `nonfiction_citations` gate (Invariant 17) over the S0 Room entities — a research fact cited by a chapter (via its `chapterRefs` JSON array) is export-blocking when unverified or when its source is DISPUTED; verified-but-low-confidence (< 0.5) facts surface as warnings. `checkBook()` runs the whole-book pass for the export pipeline.

## Consequences

- The L4 prompt's layer 7 now surfaces the most relevant confirmed material by ranked retrieval rather than flat lorebook listing — relevant distant canon (not just the freshness window) can ride the prompt within its budget slice.
- Research sources (S0 vault) join the retrieval surface: historical/technical facts the writer ingested are retrievable during drafting, and the citation gate blocks export until every cited fact is verified.
- Isolation is testable as a property: a corpus built for chapter k contains no chunk from a chapter > k or from any unconfirmed chapter.
- The 12% L4 / 7% L5 / 10% L6 layer-7 budget shares (S2 spec table) bound retrieval cost; `topK=8` plus the token budget caps the prompt surface.

## Alternatives rejected

- *SQLite FTS5 + a vector store (the spec's reference stack)* — rejected for S3: Room FTS entities and an on-disk vector index are real infrastructure; the pure-Kotlin arms deliver the hybrid semantics now, deterministically testable on the JVM route, and the embedder seam is one interface away from the LLM route.
- *FTS only* — rejected: keyword mismatch is the common failure (the scene about "the flood" won't match "the water rose"); the vector arm catches topical neighbors.
- *Post-filtering isolation* — rejected: filtering after ranking admits ordering bugs and future-chapter tie-breaks; the filter belongs before chunking.

## References

`engine/rag/RagEngine.kt`, `engine/rag/CitationIntegrityChecker.kt`, `engine/context/ContextAssembler.kt` (layer 7), `engine/L4LlmDraftGenerator.kt`, `Main.kt`, `data/model/ResearchVault.kt` (S0, schema v17), spec `upload/authr-os-v3-engine-enhancements.md` §2.1, Invariant 12 (`epistemic_isolation`), Invariant 17 (`nonfiction_citations`)
