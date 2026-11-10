package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.engine.DraftState
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.MicroArcPlanner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The micro-arc planner (v3.0 P3, plan §6.5, Table 13): phase layout across
 * targetChapters, the four planner checks per phase, and the completion
 * requirement — at least one arc question answered plus one of the five
 * contribution kinds; the ship report lists every arc's contribution.
 */
class MicroArcPlannerTest {

    private fun canon(n: Int, text: String, bookId: Int = 41) =
        Chapter(bookId = bookId, chapterNumber = n, title = "c$n", content = text, summary = "s",
            writerConfirmed = true, draftStatus = DraftState.CONFIRMED)

    @Test
    fun `default phase bounds cover 15 chapters in four phases`() {
        val bounds = MicroArcPlanner.parsePhaseBounds(MicroArcPlanner.phaseBoundsJson(1))
        assertEquals(1..3, bounds[MicroArcPlanner.Phase.SETUP])
        assertEquals(4..7, bounds[MicroArcPlanner.Phase.ESCALATION])
        assertEquals(8..11, bounds[MicroArcPlanner.Phase.REVERSAL])
        assertEquals(12..15, bounds[MicroArcPlanner.Phase.CLIMAX])
    }

    @Test
    fun `planArcs lays 15-chapter arcs across the target`() = runTest {
        val book = Book(id = 41, title = "Serial", genre = "g", synopsis = "s", outline = "o", isSerialized = true, targetChapters = 100)
        val arcs = MicroArcPlanner.planArcs(book)
        assertEquals(7, arcs.size, "ceil(100/15) arcs")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), arcs.map { it.arcIndex })
        assertEquals(1..15, MicroArcPlanner.arcSpan(arcs[0]))
        assertEquals(91..100, MicroArcPlanner.arcSpan(arcs[6]), "the final arc truncates at the target")
        val short = MicroArcPlanner.planArcs(book.copy(targetChapters = 15))
        assertEquals(1, short.size)
        val unsized = MicroArcPlanner.planArcs(book.copy(targetChapters = 0))
        assertEquals(1, unsized.size, "targetChapters=0 falls back to a single 15-chapter cycle")
    }

    @Test
    fun `a complete arc passes every planner check on the golden serial`() {
        val book = GoldenSerial.book()
        val chapters = GoldenSerial.chapters(42)
        val questions = GoldenSerial.seedQuestions(42, chapters).mapIndexed { i, q -> q.copy(id = i + 1) }
        val arc = GoldenSerial.arc(42, questions.firstOrNull()?.id)
        val checks = MicroArcPlanner.checkArcs(listOf(arc), chapters, questions)
        val failing = checks.filter { !it.passes }
        assertTrue(failing.isEmpty(), "All six planner checks pass: ${failing.map { it.message }}")
        assertEquals(6, checks.size)
        val lines = MicroArcPlanner.shipReportLines(listOf(arc), checks)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("contribution"), "the ship report lists the arc's contribution")
        assertTrue(lines[0].contains("all planner checks pass"))
    }

    @Test
    fun `the completion requirement is checked against the question ledger and the contribution`() {
        val chapters = GoldenSerial.chapters(43)
        val arc = GoldenSerial.arc(43, null).copy(contribution = "") // neither wired
        val checks = MicroArcPlanner.checkArcs(listOf(arc), chapters, emptyList())
        val completion = checks.first { it.check == "completion_requirement" }
        assertFalse(completion.passes)
        assertTrue(completion.message.contains("UNANSWERED") || completion.message.contains("not linked"))
        assertTrue(completion.message.contains("NOT declared"))
    }

    @Test
    fun `an arc whose question stays open fails the completion check`() {
        val chapters = GoldenSerial.chapters(44)
        val openQuestion = QuestionEntry(
            bookId = 44, level = "PROGRESSIVE", text = "The arc's question",
            openedChapter = 1, dueChapter = 4, answeredChapter = null
        ).copy(id = 9)
        val arc = GoldenSerial.arc(44, 9).copy(contribution = "advance the central story")
        val checks = MicroArcPlanner.checkArcs(listOf(arc), chapters, listOf(openQuestion))
        assertFalse(checks.first { it.check == "completion_requirement" }.passes, "the arc cannot close on an unanswered arc-level question")
    }

    @Test
    fun `in-flight arcs report progress without failing`() {
        val chapters = GoldenSerial.chapters(45).filter { it.chapterNumber <= 6 } // escalation, not yet closed
        val arc = GoldenSerial.arc(45, null)
        val checks = MicroArcPlanner.checkArcs(listOf(arc), chapters, emptyList())
        val climax = checks.first { it.check == "climax_payoff" }
        // The empty climax phase is progress reporting: the planner never
        // gates an in-flight arc — the suite's arcClosed rule (§6.5) decides
        // severity at export time.
        assertTrue(climax.message.contains("MISSING"), climax.message)
        assertFalse(MicroArcPlanner.arcSpan(arc).last <= 6, "the arc is not closed at chapter 6")
    }

    @Test
    fun `escalation needs at least two beats across the phase`() {
        val chapters = GoldenSerial.chapters(46).filter { it.chapterNumber in 4..5 } // two of four phase chapters
        val arc = GoldenSerial.arc(46, null)
        val checks = MicroArcPlanner.checkArcs(listOf(arc), chapters, emptyList())
        val escalation = checks.first { it.check == "escalation_beats" }
        assertTrue(escalation.message.contains("2 escalation beat(s)"), escalation.message)
    }

    @Test
    fun `seeds JSON parses into next-arc entries`() {
        val arc = GoldenSerial.arc(47, null)
        val seeds = MicroArcPlanner.parseSeeds(arc.seeds)
        assertTrue(seeds.isNotEmpty(), "the golden arc carries next-arc seeds")
        assertTrue(MicroArcPlanner.parseSeeds("[]").isEmpty())
    }
}
