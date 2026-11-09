package com.example

import com.example.engine.QuestionLedger
import com.example.data.model.QuestionEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * The question ledger foundations (v3.0, plan §6.7 / Table 15): the three
 * information levels, expected-answer windows, overdue surfacing, answering,
 * and the six-outcome revelation check.
 */
class QuestionLedgerTest {

    private fun q(
        level: String = QuestionLedger.PROGRESSIVE,
        text: String = "The open question: who moved the stones?",
        opened: Int = 1,
        due: Int? = 4,
        answered: Int? = null
    ) = QuestionEntry(bookId = 1, level = level, text = text, openedChapter = opened, dueChapter = due, answeredChapter = answered)

    @Test
    fun `detection opens progressive questions with answer windows`() {
        val text = "No one knew who had signed the ledger. The question of whether the ferry would run stayed open."
        val found = QuestionLedger.detectOpenQuestions(text, bookId = 1, chapterNumber = 2)
        assertTrue(found.size >= 2, "both cues detected: ${found.map { it.text }}")
        assertTrue(found.all { it.level == QuestionLedger.PROGRESSIVE }, "progressive by default")
        assertTrue(found.all { it.dueChapter == 5 }, "window = opened + 3 chapters")
        assertTrue(found.all { it.openedChapter == 2 }, "opened at the confirming chapter")
    }

    @Test
    fun `long-horizon phrasing opens long-term questions without a window`() {
        val text = "The choice would haunt her for the rest of her days."
        val found = QuestionLedger.detectOpenQuestions(text, bookId = 1, chapterNumber = 3)
        assertTrue(found.isNotEmpty(), "long-horizon cue detected")
        val entry = found.first()
        assertEquals(QuestionLedger.LONG_TERM, entry.level, "long-term information level")
        assertEquals(null, entry.dueChapter, "long-term questions carry no expected-answer window")
    }

    @Test
    fun `duplicate cues within one chapter collapse to one entry`() {
        val text = "The mystery of the stones. The mystery of the stones again."
        val found = QuestionLedger.detectOpenQuestions(text, bookId = 1, chapterNumber = 1)
        assertTrue(found.size == 1, "one entry for one repeated cue, got ${found.size}")
    }

    @Test
    fun `only overdue progressive questions surface`() {
        val overdue = q(level = QuestionLedger.PROGRESSIVE, opened = 1, due = 4)          // current ch 6 → overdue
        val answered = q(answered = 3)                                                    // answered → never overdue
        val longTerm = q(level = QuestionLedger.LONG_TERM, due = null)                    // long-term → never overdue
        val future = q(opened = 5, due = 8)                                               // window not passed
        val questions = listOf(overdue, answered, longTerm, future)

        assertTrue(QuestionLedger.isOverdue(overdue, 6), "unanswered past-due progressive question is overdue")
        assertFalse(QuestionLedger.isOverdue(answered, 6), "answered questions are not overdue")
        assertFalse(QuestionLedger.isOverdue(longTerm, 6), "long-term questions are never overdue")
        assertFalse(QuestionLedger.isOverdue(future, 6), "the window has not passed yet")

        val report = QuestionLedger.overdueReport(questions, 6)
        assertEquals(1, report.size, "the dashboard surfaces exactly the overdue question")
        assertTrue(report[0].contains("opened ch. 1") && report[0].contains("due ch. 4"), report[0])
    }

    @Test
    fun `answering records the chapter and links the revelation`() {
        val entry = q(opened = 2, due = 5)
        val answered = QuestionLedger.answer(entry, answeredChapter = 7, answeredByReveal = 42)
        assertEquals(7, answered.answeredChapter)
        assertEquals(42, answered.answeredByReveal)
        assertFalse(QuestionLedger.isOverdue(answered, 100), "an answered question never goes overdue")
        // The window can be re-armed by the writer.
        val reWindowed = QuestionLedger.withWindow(entry, 5)
        assertEquals(7, reWindowed.dueChapter, "opened ch. 2 + 5 = due ch. 7")
    }

    @Test
    fun `revelation outcomes cover the six checks`() {
        assertEquals(6, QuestionLedger.revelationOutcomes.size, "the blueprint's six revelation outcomes")
        // A twist that answers a question + raises stakes matches two outcomes.
        val good = QuestionLedger.revelationOutcomeIndices("She finally answered the question, and now everything to lose was at stake.")
        assertTrue(0 in good && 5 in good, "answers-a-question + raises-stakes matched: $good")
        // A shock-only twist matches none of the six.
        val shock = QuestionLedger.revelationOutcomeIndices("Then a previously unmentioned cousin appeared with a crossbow.")
        assertTrue(shock.isEmpty(), "shock-only twist matches no outcome: $shock")
    }
}
