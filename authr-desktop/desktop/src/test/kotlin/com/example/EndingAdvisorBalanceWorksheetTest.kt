package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.serialization.BalanceMeter
import com.example.engine.serialization.EndingAdvisor
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.PreDraftWorksheet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ending advisor, the balance meter, and the pre-draft worksheet
 * (v3.0 P3, plan §6.3/§6.4/§6.8).
 */
class EndingAdvisorBalanceWorksheetTest {

    // ------------------------------------------------------------------
    // Ending advisor (§6.4)
    // ------------------------------------------------------------------

    @Test
    fun `the advisor classifies the golden serial's closings into the planned types`() {
        val report = EndingAdvisor.advise(GoldenSerial.chapters(71))
        assertEquals(GoldenSerial.ENDING_PLAN, report.history.map { it.type }, "every planted closing classifies as intended")
        assertTrue(report.fatigueFlags.isEmpty(), "no consecutive same-type high-intensity repeats")
        assertTrue(report.selectionPrinciples.any { it.contains("never auto-assigns") })
    }

    @Test
    fun `the fatigue flag fires on consecutive same-type high-intensity endings only`() {
        fun chapter(n: Int, content: String) = Chapter(bookId = 71, chapterNumber = n, title = "c$n", content = content, summary = "s",
            writerConfirmed = true, draftStatus = DraftState.CONFIRMED)
        val danger = "The night ended badly; the attack struck the gate at dusk, and the danger reached the inner ward before the bells."
        val quiet = "She closed the ledger, poured the last of the tea, and let the small room settle around her."
        val repeat = EndingAdvisor.advise(listOf(chapter(1, danger), chapter(2, danger), chapter(3, quiet)))
        assertEquals(1, repeat.fatigueFlags.size, "DANGER twice consecutively flags once")
        assertEquals("DANGER", repeat.fatigueFlags[0].type)
        assertEquals(listOf(1, 2), repeat.fatigueFlags[0].chapters)
        val nonConsecutive = EndingAdvisor.advise(listOf(chapter(1, danger), chapter(2, quiet), chapter(3, danger)))
        assertTrue(nonConsecutive.fatigueFlags.isEmpty(), "non-consecutive repeats do not flag — mechanical rotation stays removed")
        val mixed = EndingAdvisor.advise(listOf(chapter(1, danger), chapter(2, danger), chapter(3, danger)))
        assertEquals(1, mixed.fatigueFlags.size, "a three-run is one flag over the span")
    }

    @Test
    fun `quiet endings are a first-class classification`() {
        val report = EndingAdvisor.advise(GoldenSerial.chapters(72))
        assertTrue(report.history.any { it.type == "QUIET" && it.chapterNumber == 3 }, "chapter 3 closes quiet")
        assertTrue(report.history.any { it.type == "QUIET" && it.chapterNumber == 11 })
    }

    // ------------------------------------------------------------------
    // Balance meter (§6.3, Tables 10-11) — informational, never blocking
    // ------------------------------------------------------------------

    @Test
    fun `the meter reports shares against the advisory bands`() {
        val book = GoldenSerial.book()
        val report = BalanceMeter.measure(GoldenSerial.chapters(73).first(), book)
        assertEquals(4, report.shares.size)
        assertEquals(100.0, report.shares.sumOf { it.measuredPct }, 0.5, "shares sum to 100")
        assertTrue(report.notes.all { it.contains("informational, never blocking") || it.contains("advisory") })
    }

    @Test
    fun `an outside-envelope chapter is advisory, never a hard failure`() {
        val book = GoldenSerial.book().copy(wordMax = 100) // every golden chapter exceeds it
        val report = BalanceMeter.measure(GoldenSerial.chapters(74).first(), book)
        assertTrue(report.outsideEnvelope)
        assertTrue(report.notes.any { it.contains("do not pad or split a scene to hit a number") })
    }

    @Test
    fun `balance shares persist as the plan 612 JSON fragment`() {
        val report = BalanceMeter.measure(GoldenSerial.chapters(75).first(), GoldenSerial.book())
        val json = BalanceMeter.balanceSharesJson(report)
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("Objective, conflict, escalation"))
    }

    // ------------------------------------------------------------------
    // Pre-draft worksheet (§6.8, Table 16)
    // ------------------------------------------------------------------

    @Test
    fun `the worksheet validates its five sections with the mandatory payoff link`() {
        val complete = PreDraftWorksheet.Worksheet(
            objective = "o", characterDevelopment = "b", plotDevelopment = "c",
            readerExperience = "d — the payoff link", chapterEnding = "e"
        )
        assertTrue(complete.complete)
        assertTrue(PreDraftWorksheet.validate(complete).isEmpty())
        val partial = complete.copy(readerExperience = "")
        assertFalse(partial.complete)
        assertTrue(PreDraftWorksheet.validate(partial).any { it.contains("payoff link is mandatory") })
    }

    @Test
    fun `the worksheet round-trips through JSON and feeds the L4 prompt block`() {
        val ws = PreDraftWorksheet.Worksheet.fromJson(GoldenSerial.worksheetJson(1))
        assertTrue(ws.complete)
        val prompt = ws.promptBlock()
        assertTrue(prompt.contains("=== PRE-DRAFT WORKSHEET"))
        assertTrue(prompt.contains("A. Narrative objective"))
        assertTrue(prompt.contains("E. Chapter ending"))
    }

    @Test
    fun `autoFill prefills from the arc plan and the question ledger — writer edits before the run`() {
        val ws = PreDraftWorksheet.autoFill(
            arcConflict = "The inheritance dispute over the sealed deed",
            openQuestions = listOf("Who sealed the original deed?", "Where did the counter-deed come from?"),
            previousEnding = "DANGER"
        )
        assertTrue(ws.objective.contains("Advance the chapter toward resolving"))
        assertTrue(ws.plotDevelopment.contains("Who sealed the original deed?"))
        assertTrue(ws.chapterEnding.contains("Avoid repeating the DANGER ending"))
        assertFalse(ws.complete, "auto-fill is a proposal; sections B and D stay with the writer")
        assertTrue(PreDraftWorksheet.validate(ws).isNotEmpty())
    }

    @Test
    fun `a missing worksheet parses to an empty proposal, never a crash`() {
        val ws = PreDraftWorksheet.Worksheet.fromJson(null)
        assertFalse(ws.complete)
        val broken = PreDraftWorksheet.Worksheet.fromJson("{not json")
        assertFalse(broken.complete)
    }
}
