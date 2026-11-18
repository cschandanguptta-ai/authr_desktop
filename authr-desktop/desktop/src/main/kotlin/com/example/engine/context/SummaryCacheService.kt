package com.example.engine.context

import com.example.data.local.SummaryCacheDao
import com.example.data.model.Chapter
import com.example.data.model.SummaryCache
import java.security.MessageDigest

/**
 * SUMMARY CACHE SERVICE (v3.0 port S2, spec ADR-0014 = desktop ADR-0021):
 * four-level hierarchical summaries with SHA-256 source-hash invalidation
 * (spec engine-enhancements §2.3-2.4).
 *
 * Levels (freshness decay table):
 *   full     — the original text, no compression
 *   medium   — ~15% of source tokens (schema-v2.2 LEVEL_TOKEN_SHARE)
 *   brief    — ~5%
 *   oneliner — ~2%, one or two sentences
 *
 * Summaries are DETERMINISTIC (extractive) in S2 — the spec's
 * `fast_cheap` LLM summarizer route arrives with the model-router work;
 * extractive summaries preserve character names, events and unresolved
 * tensions (the spec's PRESERVE list) by construction: they ARE the
 * source's own sentences, chosen by position + keyword salience.
 *
 * Invalidation: cache hit requires an exact sourceHash match (SHA-256 of
 * the chapter content); any edit to a confirmed chapter invalidates its
 * summaries at every level. The DAO is optional (ADR-0018 pattern):
 * without one the service computes and returns fresh summaries, uncached.
 */
class SummaryCacheService(
    private val dao: SummaryCacheDao? = null
) {

    /** Estimate tokens ~ words × 4/3 (spec count_tokens calibration). */
    fun estimateTokens(text: String): Int = (text.split(Regex("\\s+")).count { it.isNotBlank() } * 4 / 3)

    fun sourceHash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Get or create the summary for a scope at a level (spec
     * get_or_create_summary). [sourceText] is the chapter content; the
     * scope key is the caller's ('chapter:5').
     */
    suspend fun getOrCreate(
        bookId: Int,
        scope: String,
        level: String,
        sourceText: String
    ): SummaryCache {
        val hash = sourceHash(sourceText)
        val cached = dao?.get(bookId, scope, level)
        if (cached != null && cached.sourceHash == hash) return cached // cache hit

        val text = summarize(sourceText, level)
        val row = SummaryCache(
            bookId = bookId, scope = scope, level = level,
            summaryText = text, tokenCount = estimateTokens(text),
            sourceHash = hash, modelUsed = null // deterministic in S2
        )
        dao?.upsert(row)
        return row
    }

    /** All cached levels for a scope (UI + tests). */
    suspend fun cachedLevels(bookId: Int, scope: String): List<SummaryCache> =
        dao?.allLevelsForScope(bookId, scope) ?: emptyList()

    /**
     * Deterministic extractive summary at [level]. The strategy keeps the
     * spec's PRESERVE list: first sentences of paragraphs (events, beats),
     * sentences with character-name density (names, relationships) and
     * sentences carrying unresolved-tension markers (foreshadowing words).
     */
    fun summarize(text: String, level: String): String {
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return ""
        val picked: List<String> = when (level) {
            SummaryCache.LEVEL_FULL -> return text
            SummaryCache.LEVEL_MEDIUM -> extract(sentences, targetCount(sentences, SummaryCache.LEVEL_TOKEN_SHARE.getValue(SummaryCache.LEVEL_MEDIUM)))
            SummaryCache.LEVEL_BRIEF -> extract(sentences, targetCount(sentences, SummaryCache.LEVEL_TOKEN_SHARE.getValue(SummaryCache.LEVEL_BRIEF)))
            SummaryCache.LEVEL_ONELINER -> extract(sentences, 2)
            else -> return text
        }
        return picked.joinToString(" ")
    }

    // ------------------------------------------------------------------

    private fun targetCount(sentences: List<String>, share: Double): Int =
        (sentences.size * share).toInt().coerceAtLeast(1)

    /**
     * Salience-ordered extractive selection: sentence score = paragraph
     * position (openers carry the beat) + capitalized-name density +
     * unresolved-tension markers; ties break by original order.
     */
    private fun extract(sentences: List<String>, count: Int): List<String> {
        if (count >= sentences.size) return sentences
        val scored = sentences.mapIndexed { idx, s ->
            var score = 0.0
            // paragraph openers (first sentence of the source or after a break)
            if (idx == 0 || sentences.getOrNull(idx - 1)?.endsWith("?") == true) score += 2.0
            // character-name density (capitalized mid-sentence words)
            score += Regex("\\b(?<!^)[A-Z][a-z]{2,}\\b").findAll(s).count() * 0.5
            // unresolved-tension / foreshadowing markers
            if (TENSION_MARKERS.any { s.contains(it, ignoreCase = true) }) score += 1.5
            Triple(idx, s, score)
        }
        return scored.sortedWith(
            compareByDescending<Triple<Int, String, Double>> { it.third }.thenBy { it.first }
        ).take(count).sortedBy { it.first }.map { it.second }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }

    companion object {
        private val TENSION_MARKERS = listOf(
            "unresolved", "mystery", "secret", "promise", "threat", "warn",
            "wonder", "question", "suspect", "noticed", "realized", "decided",
            "never told", "hid", "lied", "swore", "vowed"
        )
    }
}
