package com.example

import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.engine.AnnotationKind
import com.example.engine.ChapterBeat
import com.example.engine.DraftState
import com.example.engine.EndingTypes
import com.example.engine.LiveAstAnnotator
import com.example.engine.Severity
import com.example.engine.WriterGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Live AST Annotator (plan §4.1, Table 5): deterministic local annotation of
 * confirmed text — beat coverage (Table 10), cliché hits, Chekhov promises,
 * lore references, ending signals (§6.4), continuity flags. The annotator
 * refuses non-canon text (writer sovereignty).
 */
class LiveAstAnnotatorTest {

    private fun canon(text: String, number: Int = 1) = Chapter(
        bookId = 1, chapterNumber = number, title = "Ch $number", content = text,
        summary = "", status = "Completed", writerConfirmed = true, draftStatus = DraftState.CONFIRMED
    )

    /** A seeded chapter exercising every detector deterministically. */
    private val seededChapter = """
        The forge was still smoking when Mara arrived, picking up where the night had left off.
        She needed to reclaim the sealed ledger before the guild woke. But the apprentice refused
        to hand it over, and the corridor door was blocked. Worse, the watch had doubled its rounds,
        and the stairwell collapsed behind her — the situation spiraled beyond anything she had planned.
        Then she realized the ledger was a forgery, and everything changed.
        At last she won the ledger back, and the debt between them was finally paid.
        Tomorrow, the Ashfall Accord would be tested, and somewhere across the city, the sealed
        ledger would matter again. She said breathily that nothing would be the same.
        The guild would never forgive her. The guild would never forgive her, no matter the outcome.
    """.trimIndent()

    @Test
    fun `annotator refuses non-canon text`() {
        val proposed = Chapter(
            bookId = 1, chapterNumber = 1, title = "Ch 1", content = seededChapter,
            summary = "", status = "Proposed", writerConfirmed = false, draftStatus = DraftState.PROPOSED
        )
        val result = LiveAstAnnotator.annotate(proposed)
        assertNotNull(result.refusal, "PROPOSED text must be refused")
        assertTrue(result.refusal!!.reason.contains("confirmed text only"))
        assertEquals(null, result.ast)
    }

    @Test
    fun `annotator runs on canon text and produces all six node kinds`() {
        val lorebook = listOf(
            Lorebook(bookId = 1, key = "Ashfall Accord", value = "The treaty that ended the ember wars."),
            Lorebook(bookId = 1, key = "the sealed ledger", value = "The guild's debt record.")
        )
        val ast = LiveAstAnnotator.annotate(canon(seededChapter), lorebook).astOrThrow
        val kinds = ast.annotations.map { it.kind }.toSet()
        assertTrue(AnnotationKind.BEAT in kinds, "beat coverage nodes present")
        assertTrue(AnnotationKind.CLICHE_HIT in kinds, "cliché nodes present")
        assertTrue(AnnotationKind.CHEKHOV_GUN in kinds, "Chekhov promise nodes present")
        assertTrue(AnnotationKind.LORE_REFERENCE in kinds, "lore nodes present")
        assertTrue(AnnotationKind.ENDING_SIGNAL in kinds, "ending signal present")
        assertTrue(AnnotationKind.CONTINUITY_FLAG in kinds, "echo/POV flags present")
    }

    @Test
    fun `beat coverage reports seven-of-seven on the seeded chapter`() {
        val ast = LiveAstAnnotator.annotate(canon(seededChapter)).astOrThrow
        val beatNodes = ast.ofKind(AnnotationKind.BEAT)
        val summary = beatNodes.first { it.payload["coverage"] != null }
        assertEquals("7/7", summary.payload["coverage"], "seeded chapter performs all seven beat functions")
        assertEquals(Severity.INFO, summary.severity)
    }

    @Test
    fun `missing beats raise a warn the writer can waive`() {
        val thin = "She walked home. It was quiet."
        val ast = LiveAstAnnotator.annotate(canon(thin)).astOrThrow
        val beatNodes = ast.ofKind(AnnotationKind.BEAT)
        val warn = beatNodes.first { it.severity == Severity.WARN }
        assertTrue(warn.message.contains("Missing"), "missing beats flagged: ${warn.message}")
        assertTrue(
            warn.payload["coverage"]!!.split("/")[0].toInt() < 7,
            "coverage summary reports fewer than seven beats: ${warn.payload["coverage"]}"
        )
    }

    @Test
    fun `cliche hits carry class severity and rewrite`() {
        val text = "Little did she know, the door was already open. \"Stop,\" he growled."
        val ast = LiveAstAnnotator.annotate(canon(text)).astOrThrow
        val hits = ast.ofKind(AnnotationKind.CLICHE_HIT)
        assertEquals(2, hits.size, "both planted clichés detected, got: ${hits.map { it.excerpt }}")
        assertTrue(hits.all { it.payload["clicheClass"] != null && it.payload["rewrite"] != null })
    }

    @Test
    fun `chekhov promises are marked introduced with quotes`() {
        val text = "She swore she would return the blade. He owed a debt of honor."
        val ast = LiveAstAnnotator.annotate(canon(text)).astOrThrow
        val guns = ast.ofKind(AnnotationKind.CHEKHOV_GUN)
        assertTrue(guns.size >= 2, "vow and debt detected as promises")
        assertTrue(guns.all { it.payload["status"] == "INTRODUCED" && it.payload["quote"]!!.isNotBlank() })
    }

    @Test
    fun `lore references match keys and propose undefined recurring terms`() {
        val text = "The Ashfall Accord bound the cities. The Ashfall Accord was old. The Ashfall Accord held."
        val lorebook = listOf(Lorebook(bookId = 1, key = "Ashfall Accord", value = "The treaty."))
        val ast = LiveAstAnnotator.annotate(canon(text), lorebook).astOrThrow
        val loreNodes = ast.ofKind(AnnotationKind.LORE_REFERENCE)
        assertEquals(3, loreNodes.count { it.payload["loreKey"] == "Ashfall Accord" && it.payload["proposed"] == null })
    }

    @Test
    fun `ending signal classifies the closing passage`() {
        val text = "Everything before was calm. Tomorrow they would strike, and she could not stop it."
        val ast = LiveAstAnnotator.annotate(canon(text)).astOrThrow
        val ending = ast.ofKind(AnnotationKind.ENDING_SIGNAL).single()
        assertEquals(EndingTypes.ANTICIPATION, ending.payload["endingType"])
    }

    @Test
    fun `quiet endings are explicitly permitted`() {
        val text = "The kettle cooled on the stove. She folded the letter into her pocket and sat."
        val ast = LiveAstAnnotator.annotate(canon(text)).astOrThrow
        val ending = ast.ofKind(AnnotationKind.ENDING_SIGNAL).single()
        assertEquals(EndingTypes.QUIET, ending.payload["endingType"])
    }

    @Test
    fun `text hash pins the ast to the frozen text`() {
        val h1 = LiveAstAnnotator.textHash(seededChapter)
        val h2 = LiveAstAnnotator.textHash(seededChapter + " ")
        assertTrue(h1 != h2, "any edit changes the hash")
        assertEquals(h1, LiveAstAnnotator.textHash(seededChapter), "hash is stable")
    }

    @Test
    fun `archived chapters are refused like proposals`() {
        val archived = Chapter(
            bookId = 1, chapterNumber = 2, title = "Ch 2", content = seededChapter,
            summary = "", status = "Archived", writerConfirmed = false, draftStatus = DraftState.ARCHIVED
        )
        assertTrue(WriterGate.violations(listOf(archived), listOf("chekhov_ledger")).isNotEmpty())
        val result = LiveAstAnnotator.annotate(archived)
        assertNotNull(result.refusal)
    }
}
