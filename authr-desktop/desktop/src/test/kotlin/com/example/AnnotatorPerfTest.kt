package com.example

import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.engine.AnnotationKind
import com.example.engine.ClicheCleaner
import com.example.engine.DraftState
import com.example.engine.LiveAstAnnotator
import com.example.engine.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * D-08 latency remediation guards (ADR-0012 — measured, not estimated):
 *
 *  1. Memoization contract — [LiveAstAnnotator.annotate] caches the
 *     annotation list per (text hash, lorebook hash); the margin rail's
 *     recomposition on unchanged text is a cache hit that returns the SAME
 *     immutable list instance. Any text or lorebook change recomputes.
 *  2. Behavior preservation — the hoisted echo-loop regexes and the O(1)
 *     span-dedupe check must produce byte-identical results to the original
 *     semantics (punctuation windows, overlapping spans).
 *  3. Latency guard — the 20k-word cold path stays in a generous ceiling
 *     and the warm (cached) repeat is near-instant. Measured on this
 *     sandbox (2026-11-11): cold ~470 ms, warm <1 ms (see docs/tech-debt.md
 *     D-08 and ADR-0012); the assertions below are deliberately loose so
 *     machine noise cannot flake them.
 */
class AnnotatorPerfTest {

    private fun canon(text: String, number: Int = 1) = Chapter(
        bookId = 1, chapterNumber = number, title = "Ch $number", content = text,
        summary = "", status = "Completed", writerConfirmed = true, draftStatus = DraftState.CONFIRMED
    )

    private val lore = listOf(
        Lorebook(bookId = 1, key = "Ashfall Accord", value = "The treaty that ended the ember wars."),
        Lorebook(bookId = 1, key = "the sealed ledger", value = "The guild's debt record.")
    )

    private val seeded = """
        The forge was still smoking when Mara arrived, picking up where the night had left off.
        She needed to reclaim the sealed ledger before the guild woke. But the apprentice refused
        to hand it over, and the corridor door was blocked. Worse, the watch had doubled its rounds.
        Then she realized the ledger was a forgery, and everything changed.
        At last she won the ledger back, and the debt between them was finally paid.
        Tomorrow, the Ashfall Accord would be tested. She said breathily that nothing would be the same.
        The guild would never forgive her. The guild would never forgive her, no matter the outcome.
    """.trimIndent()

    // ------------------------------------------------------------------
    // 1. Memoization contract
    // ------------------------------------------------------------------

    @Test
    fun `unchanged text hits the cache and returns the same annotation list instance`() {
        LiveAstAnnotator.clearAnnotationCache()
        val first = LiveAstAnnotator.annotate(canon(seeded), lore).astOrThrow
        val second = LiveAstAnnotator.annotate(canon(seeded), lore).astOrThrow
        assertSame(first.annotations, second.annotations,
            "recomposing the rail on unchanged text must be a cache hit (same list instance)")
        // The AST itself still carries each chapter's own identity.
        val other = LiveAstAnnotator.annotate(canon(seeded, number = 7), lore).astOrThrow
        assertSame(first.annotations, other.annotations, "same text shares the annotation list")
        assertEquals(7, other.chapterNumber, "ManuscriptAst identity is per call, not cached")
    }

    @Test
    fun `editing the text misses the cache and recomputes`() {
        LiveAstAnnotator.clearAnnotationCache()
        val before = LiveAstAnnotator.annotate(canon(seeded), lore).astOrThrow
        val edited = seeded.replace("everything changed", "everything changed forever and again")
        val after = LiveAstAnnotator.annotate(canon(edited), lore).astOrThrow
        assertNotSame(before.annotations, after.annotations, "a text edit must recompute")
        assertTrue(after.textHash != before.textHash, "text hash pins the AST to the frozen text")
    }

    @Test
    fun `changing the lorebook misses the cache and recomputes`() {
        LiveAstAnnotator.clearAnnotationCache()
        val text = seeded + " The Ashfall Accord appears here and the sealed ledger too."
        val withLore = LiveAstAnnotator.annotate(canon(text), lore).astOrThrow
        val withoutLore = LiveAstAnnotator.annotate(canon(text), emptyList()).astOrThrow
        assertNotSame(withLore.annotations, withoutLore.annotations,
            "the lorebook is part of the cache key")
        val loreHits = withLore.annotations.count { it.kind == AnnotationKind.LORE_REFERENCE }
        assertTrue(loreHits > withoutLore.annotations.count { it.kind == AnnotationKind.LORE_REFERENCE },
            "defined lore keys must produce more lore nodes than an empty lorebook")
    }

    @Test
    fun `cache is bounded and evicted entries recompute`() {
        LiveAstAnnotator.clearAnnotationCache()
        val original = seeded
        val first = LiveAstAnnotator.annotate(canon(original), lore).astOrThrow
        // Fill the cache past its 32-entry bound with distinct texts.
        repeat(40) { n ->
            LiveAstAnnotator.annotate(canon(original + " filler passage number $n"), lore)
        }
        val reafter = LiveAstAnnotator.annotate(canon(original), lore).astOrThrow
        assertNotSame(first.annotations, reafter.annotations,
            "the LRU bound must evict old entries (bounded memory), so this recomputes")
    }

    @Test
    fun `refusals are never cached as annotations`() {
        LiveAstAnnotator.clearAnnotationCache()
        val proposed = Chapter(
            bookId = 1, chapterNumber = 1, title = "Ch 1", content = seeded,
            summary = "", status = "Proposed", writerConfirmed = false, draftStatus = DraftState.PROPOSED
        )
        val refusal = LiveAstAnnotator.annotate(proposed, lore)
        assertNotNull(refusal.refusal)
        assertNull(refusal.ast)
        // The same text, once confirmed, still computes fresh (a refusal must
        // not poison the cache for the confirmed twin).
        val confirmed = LiveAstAnnotator.annotate(canon(seeded), lore).astOrThrow
        assertTrue(confirmed.annotations.isNotEmpty(), "confirmed text annotates normally")
    }

    // ------------------------------------------------------------------
    // 2. Behavior preservation (hoisted regexes + O(1) dedupe)
    // ------------------------------------------------------------------

    @Test
    fun `echo detection still fires across punctuation-adjacent windows`() {
        // The phrase repeats far apart with attached punctuation — the exact
        // shapes the strip-and-count logic must keep handling identically.
        val text = "The ledger would not stay closed, no matter the lock. " +
            "She walked the dock until dawn and counted the crates twice over. " +
            "By the end the ledger would not stay closed and everyone knew it."
        val flags = LiveAstAnnotator.continuityFlags(text)
        assertTrue(flags.any { it.payload["check"] == "echo" },
            "echo with attached punctuation must still be detected")
    }

    @Test
    fun `echo detection ignores punctuation-only window members as before`() {
        // A punctuation-only word disappears in the strip step; no real
        // 5-word phrase repeats here, so the detector must not fabricate an
        // echo out of the trim/punctuation shapes (original scan semantics).
        val text = "One two three four five . six seven eight nine ten eleven twelve."
        val flags = LiveAstAnnotator.continuityFlags(text)
        assertTrue(flags.none { it.payload["check"] == "echo" },
            "no real 5-word phrase repeats here; punctuation must not fabricate one")
    }

    @Test
    fun `overlapping cliche spans still dedupe to the winning severity`() {
        // "she said breathily" is a WARN dialogue-tag hit; craft an overlap
        // with an INFO filter-word span and assert one node wins per span.
        val text = "She felt the cold. She said breathily that the dock was closed. She felt the rain."
        val hits = ClicheCleaner.scan(text)
        val tags = hits.filter { it.payload["clicheClass"] == "Dialogue-tag crutches" }
        val filters = hits.filter { it.payload["clicheClass"] == "Filter words" }
        assertTrue(tags.isNotEmpty() && filters.isNotEmpty(), "both classes detected")
        // No two kept hits may overlap — the invariant the O(1) check guards.
        val sortedByStart = hits.sortedBy { it.start }
        for (i in 1 until sortedByStart.size) {
            assertTrue(sortedByStart[i - 1].end <= sortedByStart[i].start,
                "kept spans must be non-overlapping")
        }
        assertTrue(hits.any { it.severity == Severity.WARN }, "severity survives the dedupe")
    }

    // ------------------------------------------------------------------
    // 3. Latency guard (generous ceilings — measured numbers live in docs)
    // ------------------------------------------------------------------

    private fun longChapter(): String {
        val sb = StringBuilder()
        var words = 0
        var i = 0
        while (words < 20_000) {
            when {
                i % 37 == 11 -> { sb.append("the ledger would not stay closed. "); words += 6 }
                i % 29 == 7 -> { sb.append("She felt the cold before she saw the door. "); words += 9 }
                else -> { sb.append("The ferry left the ore dock before the whistle finished. "); words += 10 }
            }
            if (i % 5 == 4) sb.append("\n\n")
            i++
        }
        return sb.toString().trim()
    }

    @Test
    fun `20k-word cold annotate stays inside the documented ceiling and warm repeat is near-instant`() {
        LiveAstAnnotator.clearAnnotationCache()
        val text = longChapter()
        LiveAstAnnotator.annotate(canon(text), lore) // JIT warm-up on the real shape
        LiveAstAnnotator.clearAnnotationCache()

        val t0 = System.nanoTime()
        val ast = LiveAstAnnotator.annotate(canon(text), lore).astOrThrow
        val coldMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue(coldMs < 5_000, "cold 20k-word annotate measured $coldMs ms (ceiling 5_000)")

        val t1 = System.nanoTime()
        val warm = LiveAstAnnotator.annotate(canon(text), lore).astOrThrow
        val warmMs = (System.nanoTime() - t1) / 1_000_000
        assertSame(ast.annotations, warm.annotations, "warm repeat is the cached instance")
        assertTrue(warmMs < 100, "warm (cached) rail recomposition measured $warmMs ms (ceiling 100)")
    }
}
