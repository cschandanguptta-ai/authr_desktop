package com.example.engine.rag

import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.data.model.ResearchSource

/**
 * RAG ENGINE (v3.0 port S3, spec ADR-0012 = desktop ADR-0022): hybrid
 * FTS + vector retrieval with EPISTEMIC ISOLATION (Invariant 12).
 *
 * The spec's reference stack is SQLite FTS + LanceDB embeddings; this
 * desktop port implements the same hybrid scoring in pure Kotlin over
 * the in-memory chapter/research corpora:
 *
 *  - FTS arm: BM25 ranking over fixed-size chunks (k1=1.2, b=0.75 —
 *    the classic Robertson constants; deterministic, index-free).
 *  - Vector arm: hashed bag-of-words embeddings with cosine similarity
 *    (the "vector" is a 4096-dim hashed TF vector; deterministic, no
 *    provider call — the LLM embedding route lands with the S5
 *    model-router work and can replace the embedder behind the same
 *    interface).
 *  - Fusion: Reciprocal Rank Fusion (RRF, k=60) — the standard
 *    rank-based hybrid combiner; score-fusion is brittle across arms
 *    with different distributions, rank-fusion is not.
 *
 * EPISTEMIC ISOLATION (Invariant 12, gate severity BLOCKING): a query
 * for chapter k may only retrieve from chunks whose source chapter
 * satisfies `chapterNumber <= k AND writerConfirmed` — the model must
 * never see unconfirmed drafts (its own or the writer's WIP) or future
 * chapters. The filter is applied BEFORE chunking/scoring: isolation is
 * structural, not a post-filter that could leak via scoring order.
 * Research sources are always writer-confirmed material (ingested by
 * the writer), so they ride the same retrieval surface unrestricted by
 * chapter number but still respect the confirmation discipline of their
 * ingest-time state.
 */
class RagEngine {

    /** One retrievable unit: a chunk of a confirmed chapter or a research source. */
    data class Chunk(
        val sourceType: String,     // "chapter" | "research"
        val chapterNumber: Int,     // 0 for research sources
        val title: String,
        val text: String,
        /** True only when the source chapter is writer-confirmed canon. */
        val writerConfirmed: Boolean,
        val sourceId: Int = 0       // research source id, chapter id
    )

    /** A retrieval result with its fused rank and arm provenance. */
    data class Retrieval(
        val chunk: Chunk,
        val rrfScore: Double,
        val ftsRank: Int?,          // 1-based rank in the BM25 arm, null if absent
        val vectorRank: Int?        // 1-based rank in the cosine arm, null if absent
    )

    // ------------------------------------------------------------------
    // Corpus building

    /**
     * Build the retrieval corpus from confirmed chapters (epistemic
     * isolation applied at construction: only chapterNumber <= maxChapter
     * AND writerConfirmed enter) plus research sources (S3: the
     * nonfiction fact surface; fiction books have none).
     */
    fun buildCorpus(
        chapters: List<Chapter>,
        researchSources: List<ResearchSource> = emptyList(),
        maxChapter: Int = Int.MAX_VALUE
    ): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        // Isolation BEFORE chunking: unconfirmed or future chapters never
        // produce chunks at all.
        chapters
            .filter { it.writerConfirmed && it.chapterNumber <= maxChapter }
            .forEach { ch ->
                chunkParagraphs(ch.content).forEach { (idx, text) ->
                    chunks += Chunk(
                        sourceType = "chapter",
                        chapterNumber = ch.chapterNumber,
                        title = "${ch.title} ¶${idx + 1}",
                        text = text,
                        writerConfirmed = true,
                        sourceId = ch.id
                    )
                }
            }
        researchSources.filter { it.contentSnippet != null }.forEach { src ->
            chunks += Chunk(
                sourceType = "research",
                chapterNumber = 0,
                title = src.title,
                text = src.contentSnippet ?: "",
                writerConfirmed = true, // writer-ingested material
                sourceId = src.id
            )
        }
        return chunks
    }

    /** Split into ~[targetWords]-word chunks at sentence boundaries. */
    fun chunkParagraphs(content: String, targetWords: Int = 120): List<Pair<Int, String>> {
        val sentences = content.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var words = 0
        for (s in sentences) {
            val sw = s.split(Regex("\\s+")).count { it.isNotBlank() }
            if (words > 0 && words + sw > targetWords) {
                out += current
                current = mutableListOf()
                words = 0
            }
            current += s
            words += sw
        }
        if (current.isNotEmpty()) out += current
        return out.mapIndexed { idx, sentencesList -> idx to sentencesList.joinToString(" ") }
    }

    // ------------------------------------------------------------------
    // Retrieval

    /**
     * Hybrid retrieve (spec retrieve_relevant_chunks): query for chapter
     * [currentChapter], epistemically isolated, RRF-fused, token-budgeted
     * ([maxTokens] via the 4/3 words-per-token calibration).
     */
    fun retrieve(
        corpus: List<Chunk>,
        query: String,
        currentChapter: Int,
        maxTokens: Int,
        topK: Int = 8
    ): List<Retrieval> {
        if (corpus.isEmpty() || query.isBlank() || maxTokens <= 0) return emptyList()

        // Invariant 12, defensively re-applied on the corpus (buildCorpus
        // already isolates; this guards corpora assembled elsewhere).
        val isolated = corpus.filter { it.writerConfirmed && (it.sourceType == "research" || it.chapterNumber <= currentChapter) }

        val ftsRanked = bm25Rank(isolated, query).take(topK)
        val vecRanked = cosineRank(isolated, query).take(topK)

        // Reciprocal Rank Fusion over the two arms.
        val rrf = LinkedHashMap<Chunk, Double>()
        val ftsRankOf = HashMap<Chunk, Int>()
        val vecRankOf = HashMap<Chunk, Int>()
        ftsRanked.forEachIndexed { i, c -> rrf[c] = (rrf[c] ?: 0.0) + 1.0 / (RRF_K + i + 1); ftsRankOf[c] = i + 1 }
        vecRanked.forEachIndexed { i, c -> rrf[c] = (rrf[c] ?: 0.0) + 1.0 / (RRF_K + i + 1); vecRankOf[c] = i + 1 }

        // Token budget: best-ranked first until the budget is spent.
        val results = mutableListOf<Retrieval>()
        var remaining = maxTokens
        for ((chunk, score) in rrf.entries.sortedByDescending { it.value }) {
            val tokens = estimateTokens(chunk.text)
            if (tokens <= remaining) {
                results += Retrieval(chunk, score, ftsRankOf[chunk], vecRankOf[chunk])
                remaining -= tokens
            }
            if (remaining <= 0) break
        }
        return results
    }

    /**
     * The retrieval layer body for the context assembler: numbered,
     * provenance-attributed extracts within the layer budget.
     */
    fun formatRetrieved(results: List<Retrieval>): String =
        results.joinToString("\n\n") { (chunk, _, ftsRank, vecRank) ->
            val arms = listOfNotNull(
                ftsRank?.let { "fts#$it" },
                vecRank?.let { "vec#$it" }
            ).joinToString(",")
            "[${chunk.sourceType} ch.${chunk.chapterNumber.takeIf { it > 0 } ?: "-"} | $arms] ${chunk.title}\n${chunk.text}"
        }

    /**
     * Spec build_retrieval_query: the query is assembled from the beat
     * sheet (target chapter summary) + active lorebook keys — the
     * entities the scene must be consistent with.
     */
    fun buildRetrievalQuery(
        targetChapterSummary: String,
        lorebook: List<Lorebook> = emptyList(),
        instruction: String = ""
    ): String = listOf(
        targetChapterSummary,
        lorebook.joinToString(" ") { "${it.key} ${it.value}" },
        instruction
    ).filter { it.isNotBlank() }.joinToString(" ")

    // ------------------------------------------------------------------
    // Arms

    /** BM25 ranking (k1=1.2, b=0.75). Empty query terms → empty ranking. */
    fun bm25Rank(corpus: List<Chunk>, query: String): List<Chunk> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        val docs = corpus.map { tokenize(it.text) }
        val avgLen = if (docs.isEmpty()) 0.0 else docs.sumOf { it.size } / docs.size.toDouble()
        val df = HashMap<String, Int>()
        terms.forEach { t -> df[t] = docs.count { d -> t in d } }
        val scores = docs.mapIndexed { i, d ->
            val len = d.size.toDouble()
            var score = 0.0
            terms.forEach { t ->
                if (df[t]!! > 0 && t in d) {
                    val tf = d.count { it == t }.toDouble()
                    val idf = kotlin.math.ln((corpus.size - df[t]!! + 0.5) / (df[t]!! + 0.5) + 1.0)
                    score += idf * (tf * (BM25_K1 + 1)) /
                        (tf + BM25_K1 * (1 - BM25_B + BM25_B * len / avgLen.coerceAtLeast(1.0)))
                }
            }
            score
        }
        return corpus.indices.filter { scores[it] > 0 }
            .sortedByDescending { scores[it] }
            .map { corpus[it] }
    }

    /**
     * Vector arm: hashed bag-of-words TF vectors, cosine similarity.
     * Deterministic; 4096 dimensions keeps hash collisions negligible
     * for book-scale vocabularies.
     */
    fun cosineRank(corpus: List<Chunk>, query: String): List<Chunk> {
        val q = embed(query)
        if (q.isEmpty()) return emptyList()
        return corpus
            .map { it to cosine(embed(it.text), q) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun embed(text: String): Map<Int, Double> {
        val vec = HashMap<Int, Double>()
        tokenize(text).forEach { t -> vec[t.hashCode() and VEC_DIM - 1] = (vec[t.hashCode() and VEC_DIM - 1] ?: 0.0) + 1.0 }
        return vec
    }

    private fun cosine(a: Map<Int, Double>, b: Map<Int, Double>): Double {
        val (small, large) = if (a.size < b.size) a to b else b to a
        var dot = 0.0
        small.forEach { (k, v) -> dot += v * (large[k] ?: 0.0) }
        if (dot == 0.0) return 0.0
        val na = kotlin.math.sqrt(a.values.sumOf { it * it })
        val nb = kotlin.math.sqrt(b.values.sumOf { it * it })
        return dot / (na * nb)
    }

    // ------------------------------------------------------------------

    fun estimateTokens(text: String): Int =
        (text.split(Regex("\\s+")).count { it.isNotBlank() } * 4 / 3)

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9']+")).filter { it.isNotBlank() && it.length > 1 }

    companion object {
        private const val RRF_K = 60
        private const val BM25_K1 = 1.2
        private const val BM25_B = 0.75
        private const val VEC_DIM = 4096
    }
}
