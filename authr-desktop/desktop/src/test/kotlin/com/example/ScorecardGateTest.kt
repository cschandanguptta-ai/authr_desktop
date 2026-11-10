package com.example

import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.QcScorecard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P3 exit gate 3 (plan §13 Table 33, §15 Table 34): "scorecard gates behave
 * per the interpretation bands — interpretation bands applied correctly;
 * zero-triggers always block."
 *
 * Bands (plan §6.9, Table 18): 12-14 strong → release queue; 9-11
 * reasonable → release queue with a writer note on the weak criteria;
 * 6-8 → blocked until revised or the writer overrides with a note; 0-5 →
 * blocked, revision required. Two triggers are HARD regardless of total: a
 * zero on narrative objective clarity (criterion 2) or satisfying payoff
 * (criterion 6), overridable ONLY by an explicit writer note.
 */
class ScorecardGateTest {

    private fun chapter(scores: List<Int>, note: String? = null) =
        GoldenSerial.scorecardBandVariants(31).first { it.first == "STRONG_13" }.second
            .copy(qcScores = QcScorecard.toJson(QcScorecard.Scored(scores, List(7) { "Seeded band case." })), qcTotal = scores.sum(), ctaNote = note)

    // ------------------------------------------------------------------
    // Interpretation bands (Table 18)
    // ------------------------------------------------------------------

    @Test
    fun `the seeded band cases behave per the interpretation bands`() {
        val cases = GoldenSerial.scorecardBandVariants(32)
        fun gate(id: String, note: String? = null) =
            QcScorecard.gate(cases.first { it.first == id }.second, note)

        // 12-14: strong chapter; proceed to final editing → release queue.
        val strong = gate("STRONG_13")
        assertTrue(strong.releasable)
        assertEquals(QcScorecard.Band.STRONG, strong.band)
        assertTrue(strong.message.contains("Strong chapter"))

        // 9-11: reasonably effective → release queue with a writer note.
        val reasonable = gate("REASONABLE_10")
        assertTrue(reasonable.releasable)
        assertEquals(QcScorecard.Band.REASONABLE, reasonable.band)
        assertTrue(reasonable.message.contains("Writer note on the weak criteria"))

        // 6-8: requires structural revision → blocked unless overridden with note.
        val revision = gate("REVISION_7")
        assertFalse(revision.releasable, "6-8 blocks from release until revised")
        val revisionOverride = gate("REVISION_7", "Writer override: the quiet chapter is intentional.")
        assertTrue(revisionOverride.releasable, "the writer may override a 6-8 with a note")
        assertTrue(revisionOverride.overridden)
        assertTrue(revisionOverride.message.contains("Overridden by writer note"))

        // 0-5: reconsider the chapter's purpose → blocked, never overridable.
        val reconsider = gate("RECONSIDER_4", "Even with a note, 0-5 blocks.")
        assertFalse(reconsider.releasable)
        assertEquals(QcScorecard.Band.RECONSIDER, reconsider.band)
        assertTrue(reconsider.message.contains("revision required — never overridable"))
    }

    @Test
    fun `band boundaries are exact`() {
        assertTrue(QcScorecard.gate(chapter(List(7) { 2 })).releasable)                        // 14
        assertTrue(QcScorecard.gate(chapter(listOf(2, 2, 2, 2, 2, 2, 0))).releasable)          // 12 — STRONG floor (criterion 7 is not a trigger)
        assertTrue(QcScorecard.gate(chapter(listOf(2, 2, 2, 2, 2, 1, 1))).releasable)          // 11 — REASONABLE top
        assertTrue(QcScorecard.gate(chapter(listOf(2, 1, 2, 1, 2, 1, 1))).releasable)          // 10 — REASONABLE
        assertTrue(QcScorecard.gate(chapter(listOf(2, 2, 2, 2, 1, 1, 1))).releasable)          // 11
        assertFalse(QcScorecard.gate(chapter(listOf(1, 1, 1, 1, 1, 1, 1))).releasable)         // 7 — REVISION
        assertFalse(QcScorecard.gate(chapter(listOf(1, 1, 1, 1, 0, 1, 1))).releasable)         // 6 — REVISION floor
        assertFalse(QcScorecard.gate(chapter(listOf(1, 1, 1, 0, 1, 1, 1))).releasable)         // 6 — still REVISION
        assertFalse(QcScorecard.gate(chapter(listOf(1, 0, 1, 0, 1, 0, 1))).releasable)         // 4 — RECONSIDER
    }

    // ------------------------------------------------------------------
    // Zero-triggers (criterion 2 and 6)
    // ------------------------------------------------------------------

    @Test
    fun `a zero on satisfying payoff blocks a 12-total chapter — the trigger is hard regardless of total`() {
        val cases = GoldenSerial.scorecardBandVariants(33)
        val zeroPayoff = cases.first { it.first == "ZERO_PAYOFF_12" }
        val gate = QcScorecard.gate(zeroPayoff.second)
        assertEquals(12, gate.total)
        assertEquals(QcScorecard.Band.STRONG, gate.band)
        assertFalse(gate.releasable, "the zero-trigger overrides the strong total")
        assertTrue(gate.zeroTriggers.contains(QcScorecard.CRITERIA[5]), "criterion 6 is the trigger")
        assertTrue(gate.message.contains("overridable only by an explicit writer note"))
    }

    @Test
    fun `a zero-trigger clears only with an explicit writer note`() {
        val cases = GoldenSerial.scorecardBandVariants(34)
        val zeroPayoff = cases.first { it.first == "ZERO_PAYOFF_12_OVERRIDDEN" }.second
        val withoutNote = QcScorecard.gate(zeroPayoff, null)
        assertFalse(withoutNote.releasable)
        val withNote = QcScorecard.gate(zeroPayoff, "Writer note: payoff deliberately moved to chapter 8; revision prompt acknowledged.")
        assertTrue(withNote.releasable, "overridable only by an explicit writer note")
        assertTrue(withNote.overridden)
        assertTrue(withNote.message.contains("disclosed"), "the override is disclosed, never silent")
    }

    @Test
    fun `a zero on narrative objective clarity triggers as well`() {
        val ch = chapter(listOf(2, 0, 2, 2, 2, 2, 2)) // 12/14, zero on criterion 2
        val gate = QcScorecard.gate(ch)
        assertFalse(gate.releasable)
        assertTrue(gate.zeroTriggers.contains(QcScorecard.CRITERIA[1]))
    }

    @Test
    fun `blank writer notes never override`() {
        val ch = chapter(listOf(1, 1, 1, 1, 1, 1, 1)) // 7 — REVISION
        assertFalse(QcScorecard.gate(ch, "   ").releasable, "a whitespace note is no note")
        assertFalse(QcScorecard.gate(ch, null).releasable)
    }

    @Test
    fun `unscored chapters cannot enter the release queue`() {
        val ch = GoldenSerial.chapters(35).first().copy(qcScores = null, qcTotal = null)
        val gate = QcScorecard.gate(ch)
        assertFalse(gate.releasable)
        assertTrue(gate.message.contains("score the chapter before release"))
    }

    // ------------------------------------------------------------------
    // The deterministic proposal anchors the writer's review (Table 17)
    // ------------------------------------------------------------------

    @Test
    fun `the L6 proposal zeroes exactly on the two hard criteria when their inputs are missing`() {
        val proposal = QcScorecard.propose(
            hookPasses = false,
            objectiveCovered = false,
            conflictEscalationCovered = false,
            worksheetB = false,
            worksheetC = false,
            payoffLinked = false,
            endingEncourages = false
        )
        assertEquals(0, proposal.scores[1], "criterion 2 (objective) zero-proposes on missing input")
        assertEquals(0, proposal.scores[5], "criterion 6 (payoff) zero-proposes on missing input")
        assertEquals(QcScorecard.ZERO_TRIGGERS, setOf(1, 5))
        val gate = QcScorecard.gate(
            GoldenSerial.chapters(36).first().copy(
                qcScores = QcScorecard.toJson(proposal), qcTotal = proposal.total
            )
        )
        assertFalse(gate.releasable, "the proposal itself never releases while triggers are active")
    }
}
