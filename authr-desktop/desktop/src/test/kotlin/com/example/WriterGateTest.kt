package com.example

import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.WriterGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Engine sovereignty gate (plan §15, Table 35): "Unconfirmed text reaching
 * ledger or export in the assertion suite — zero occurrences".
 */
class WriterGateTest {

    private fun chapter(
        number: Int,
        confirmed: Boolean,
        status: String
    ) = Chapter(
        bookId = 1, chapterNumber = number, title = "Ch $number",
        content = "text $number", summary = "", status = "Completed",
        writerConfirmed = confirmed, draftStatus = status
    )

    @Test
    fun `only writer-confirmed chapters are canon`() {
        val proposed = chapter(1, confirmed = false, status = DraftState.PROPOSED)
        val confirmed = chapter(2, confirmed = true, status = DraftState.CONFIRMED)
        val archived = chapter(3, confirmed = false, status = DraftState.ARCHIVED)

        assertFalse(WriterGate.isCanon(proposed), "PROPOSED is never canon")
        assertTrue(WriterGate.isCanon(confirmed), "confirmed chapter is canon")
        assertFalse(WriterGate.isCanon(archived), "ARCHIVED never feeds downstream")
    }

    @Test
    fun `proposed drafts queue for review`() {
        val chapters = listOf(
            chapter(1, confirmed = false, status = DraftState.PROPOSED),
            chapter(2, confirmed = false, status = DraftState.PROPOSED),
            chapter(3, confirmed = true, status = DraftState.CONFIRMED)
        )
        val queue = WriterGate.proposedChapters(chapters)
        assertEquals(listOf(1, 2), queue.map { it.chapterNumber }, "review queue holds PROPOSED drafts")
    }

    @Test
    fun `unconfirmed text never reaches ledger or export consumers`() {
        val chapters = listOf(
            chapter(1, confirmed = true, status = DraftState.CONFIRMED),
            chapter(2, confirmed = false, status = DraftState.PROPOSED),
            chapter(3, confirmed = false, status = DraftState.ARCHIVED)
        )
        val violations = WriterGate.violations(chapters, listOf("chekhov_ledger", "epub_export"))
        assertEquals(
            2, violations.size,
            "each unconfirmed chapter that reached a consumer is a violation"
        )
        assertTrue(violations[0].contains("chapter 2"))
        assertTrue(violations[1].contains("chapter 3"))
        assertEquals(
            emptyList<String>(),
            WriterGate.violations(chapters.filter { WriterGate.isCanon(it) }, listOf("chekhov_ledger")),
            "canon-only feeding produces zero violations (the P1 gate)"
        )
    }
}
