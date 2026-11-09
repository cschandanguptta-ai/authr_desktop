package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.QuestionLedger
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.OpeningValidators
import com.example.engine.serialization.SerialValidatorSuite
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P3 exit gate 1 (plan §13 Table 33, §15 Table 34): "a 15-chapter golden
 * serial passes the full validator suite with at most two dismissable
 * warnings."
 *
 * The corpus is [GoldenSerial]: fully deterministic, planted text — every
 * beat function cued, every free chapter hooked inside the 200-400 word
 * span, the final free chapter satisfying the anti-withholding gate, all
 * seven ending types exercised with no consecutive same-type high-intensity
 * repeats, chapters inside the 1,000-2,000-word envelope, chapters 1-11
 * released on Mon/Wed/Fri, and a complete micro-arc with its question
 * answered inside the window.
 */
class SerializationGoldenSerialTest {

    private fun fullInput(bookId: Int): SerialValidatorSuite.Input {
        val book = GoldenSerial.book().copy(id = bookId)
        val chapters = GoldenSerial.chapters(bookId)
        val questions = GoldenSerial.seedQuestions(bookId, chapters).mapIndexed { i, q -> q.copy(id = i + 1) }
        val arc = GoldenSerial.arc(bookId, questions.firstOrNull()?.id)
        return SerialValidatorSuite.Input(
            book = book, chapters = chapters, arcs = listOf(arc), questions = questions
        )
    }

    // ------------------------------------------------------------------
    // The exit gate
    // ------------------------------------------------------------------

    @Test
    fun `the 15-chapter golden serial passes the full validator suite with at most two dismissable warnings`() = runTest {
        val report = SerialValidatorSuite.run(fullInput(1))
        assertTrue(report.passes, "Hard gates must be green: ${report.hardBlocks.map { it.message }}")
        assertTrue(
            report.warnings.size <= 2,
            "At most two dismissable warnings allowed, got ${report.warnings.size}: ${report.warnings.map { it.message }}"
        )
        assertTrue(report.exitGateGreen, "Exit gate green")
        // Proof of coverage: the planted corpus actually exercises the checks.
        assertTrue(report.passedCount >= 40, "Expected 40+ passing checks, got ${report.passedCount}")
    }

    // ------------------------------------------------------------------
    // The corpus itself is what the plan specifies
    // ------------------------------------------------------------------

    @Test
    fun `every chapter carries all seven beat functions`() {
        GoldenSerial.chapters(2).forEach { ch ->
            val coverage = com.example.engine.LiveAstAnnotator.beatCoverage(ch.content).first()
            assertEquals("7/7", coverage.payload["coverage"], "Chapter ${ch.chapterNumber} beat coverage")
        }
    }

    @Test
    fun `every free-tier chapter carries a hook inside the 200-400 word span`() {
        val chapters = GoldenSerial.chapters(3)
        chapters.filter { it.chapterNumber <= GoldenSerial.FREE_TIER }.forEach { ch ->
            val hook = OpeningValidators.validateHook(ch)
            assertTrue(hook.passes, "Chapter ${ch.chapterNumber} hook: ${hook.message}")
            assertTrue(hook.scannedWords >= OpeningValidators.HOOK_SPAN_MIN_WORDS, "scanned span covers the benchmark")
        }
    }

    @Test
    fun `chapter 1 carries the full five-goal story promise`() {
        val missing = OpeningValidators.storyPromise(GoldenSerial.chapters(4).first())
        assertTrue(missing.isEmpty(), "Chapter 1 story promise missing goals: $missing")
    }

    @Test
    fun `chapters 4 and 5 carry substantial developments`() {
        val chapters = GoldenSerial.chapters(5)
        chapters.filter { it.chapterNumber in 4..5 }.forEach { ch ->
            assertTrue(
                OpeningValidators.substantialDevelopments(ch).isNotEmpty(),
                "Chapter ${ch.chapterNumber} needs a substantial development"
            )
        }
    }

    @Test
    fun `every chapter sits inside the 1000-2000 word envelope`() {
        val book = GoldenSerial.book()
        GoldenSerial.chapters(6).forEach { ch ->
            val wc = OpeningValidators.wordCount(ch.content)
            assertTrue(
                wc in book.wordMin..book.wordMax,
                "Chapter ${ch.chapterNumber} measures $wc words against the ${book.wordMin}-${book.wordMax} envelope"
            )
        }
    }

    @Test
    fun `all seven ending types are exercised with no consecutive same-type high-intensity repeat`() {
        val plan = GoldenSerial.ENDING_PLAN
        assertEquals(GoldenSerial.CHAPTER_COUNT, plan.size)
        assertTrue(plan.contains("QUIET"), "Quiet endings are explicitly supported — the corpus exercises one")
        plan.zipWithNext().forEach { (a, b) ->
            assertFalse(a == b && a != "QUIET", "Consecutive same-type high-intensity endings in the plan: $a -> $b")
        }
        // The planted closings must actually classify as intended.
        val report = SerialValidatorSuite.run(fullInput(7))
        val endingChecks = report.checks.filter { it.id == "ending" && it.severity == SerialValidatorSuite.Severity.PASS }
        assertEquals(GoldenSerial.CHAPTER_COUNT, endingChecks.size, "Every closing passage classifies as its planted type")
    }

    @Test
    fun `the micro-arc completes with its question answered and its contribution declared`() {
        val input = fullInput(8)
        val report = SerialValidatorSuite.run(input)
        val arcChecks = report.checks.filter { it.id.startsWith("arc_") }
        val failing = arcChecks.filter { it.severity == SerialValidatorSuite.Severity.HARD || it.severity == SerialValidatorSuite.Severity.WARN }
        assertTrue(failing.isEmpty(), "Arc checks must pass on the golden serial: ${failing.map { it.message }}")
        assertTrue(input.arcs.single().contribution.isNotBlank(), "The ship report lists every arc's contribution")
    }

    @Test
    fun `released chapters sit on the Mon-Wed-Fri schedule and the buffer stays amber`() {
        val report = SerialValidatorSuite.run(fullInput(9))
        val cadenceCheck = report.checks.first { it.id == "cadence" }
        assertEquals(SerialValidatorSuite.Severity.PASS, cadenceCheck.severity, cadenceCheck.message)
        val bufferCheck = report.checks.first { it.id == "buffer" }
        assertEquals(SerialValidatorSuite.Severity.PASS, bufferCheck.severity, bufferCheck.message)
        val buffer = report.buffer!!
        assertEquals(4, buffer.confirmedUnreleased, "15 canon - 11 released = 4 unreleased")
        assertEquals(com.example.engine.serialization.BufferCadence.BufferLevel.AMBER, buffer.level)
    }

    @Test
    fun `no seeded question is overdue`() {
        val input = fullInput(10)
        val report = SerialValidatorSuite.run(input)
        assertTrue(
            report.checks.none { it.id == "question_overdue" },
            "The arc's question is answered inside its expected-answer window"
        )
        assertTrue(input.questions.none { QuestionLedger.isOverdue(it, 15) })
    }

    // ------------------------------------------------------------------
    // Warning mechanics: dismissable, budget-aware
    // ------------------------------------------------------------------

    @Test
    fun `a planted beat gap raises a dismissable warning and dismissal clears it from the budget`() {
        val (book, chapters) = GoldenSerial.beatGapVariant(11)
        val questions = GoldenSerial.seedQuestions(11, chapters)
        val arc = GoldenSerial.arc(11, null)
        val report = SerialValidatorSuite.run(SerialValidatorSuite.Input(book, chapters, listOf(arc), questions))
        val beatWarnings = report.checks.filter { it.id == "beat_coverage" && it.severity == SerialValidatorSuite.Severity.WARN }
        assertTrue(beatWarnings.isNotEmpty(), "Chapter 9's stripped beats must raise coverage warnings")
        assertTrue(beatWarnings.all { it.dismissable }, "Coverage warnings are dismissable with a note")
        val dismissed = SerialValidatorSuite.run(
            SerialValidatorSuite.Input(book, chapters, listOf(arc), questions, dismissals = beatWarnings.map { it.key }.toSet())
        )
        assertTrue(dismissed.warnings.none { it.id == "beat_coverage" }, "Dismissed warnings leave the budget")
        assertTrue(dismissed.passes, "A dismissal is a writer note, never a hard-gate bypass")
    }

    @Test
    fun `non-serialized books short-circuit the suite`() {
        val book = Book(id = 12, title = "Novel", genre = "Drama", synopsis = "s", outline = "o")
        val report = SerialValidatorSuite.run(SerialValidatorSuite.Input(book, emptyList()))
        assertTrue(report.passes && report.warnings.isEmpty(), "The serialization profile is inactive outside serials (plan §6.12)")
    }

    @Test
    fun `unconfirmed chapters are withheld by the canon gate inside the suite`() {
        val input = fullInput(13)
        val proposed = input.chapters.map { if (it.chapterNumber == 15) it.copy(writerConfirmed = false, draftStatus = com.example.engine.DraftState.PROPOSED) else it }
        val report = SerialValidatorSuite.run(input.copy(chapters = proposed))
        val gate = report.checks.first { it.id == "canon_gate" }
        assertEquals(SerialValidatorSuite.Severity.HARD, gate.severity)
        assertFalse(report.passes, "Unconfirmed text never reaches export (the P1 gate composes with the P3 suite)")
    }
}
