package com.example.engine.serialization

import com.example.data.model.Chapter
import org.json.JSONObject

/**
 * The pre-publication QC scorecard — Blueprint 2.0 Part 9 (v3.0, plan
 * §6.9, Tables 17-18).
 *
 * Every completed chapter is scored before release on SEVEN criteria, each
 * 0-2, maximum 14. L6 proposes scores with one-line rationales
 * (deterministic checks contribute directly); the writer reviews and sets
 * the final scores. The scorecard is an editorial self-assessment aid, not a
 * scientifically validated reader-retention score — the product copy says
 * exactly that.
 *
 * Two triggers are HARD gates regardless of total: a zero on narrative
 * objective clarity (criterion 2) or on satisfying payoff (criterion 6)
 * forces a revision prompt before export, overridable only by an explicit
 * writer note.
 */
object QcScorecard {

    /** The seven criteria (Table 17), 0-2 each, max 14. */
    val CRITERIA = listOf(
        "1. Opening captures attention",
        "2. Narrative objective is clear",
        "3. Conflict and tension progress",
        "4. Character development is meaningful",
        "5. New information or plot advancement",
        "6. Chapter provides a satisfying payoff",
        "7. Ending encourages continuation"
    )

    /** Indices of the zero-triggers (criterion 2 and 6, 0-based: 1 and 5). */
    val ZERO_TRIGGERS = setOf(1, 5)

    const val MAX_SCORE = 14

    data class Scored(
        val scores: List<Int>,
        val rationales: List<String>
    ) {
        init { require(scores.size == 7) { "Seven criteria required" } }
        val total: Int get() = scores.sum()
        val zeroTriggered: List<String>
            get() = ZERO_TRIGGERS.filter { scores[it] == 0 }.map { CRITERIA[it] }
    }

    /** Interpretation bands (Table 18) and their export behaviour. */
    enum class Band(val min: Int, val max: Int, val interpretation: String, val exportBehavior: String) {
        STRONG(12, 14, "Strong chapter; proceed to final editing", "RELEASE"),
        REASONABLE(9, 11, "Reasonably effective; examine weaker areas", "RELEASE_WITH_NOTE"),
        REVISION(6, 8, "Requires structural revision", "BLOCKED_UNLESS_OVERRIDDEN"),
        RECONSIDER(0, 5, "Reconsider the chapter's purpose and narrative construction", "BLOCKED");

        companion object {
            fun of(total: Int): Band = entries.first { total in it.min..it.max }
        }
    }

    data class GateResult(
        val chapterNumber: Int,
        val total: Int,
        val band: Band,
        /** Zero on criterion 2 or 6 — hard gate, overridable only by writer note. */
        val zeroTriggers: List<String>,
        /** True when the chapter may enter the release queue. */
        val releasable: Boolean,
        /** True when the writer's explicit note overrides a REVISION-band block. */
        val overridden: Boolean,
        val message: String
    )

    /** Parses chapters.qcScores JSON ("{"c1":2,...}") into a Scored. */
    fun of(chapter: Chapter): Scored? {
        if (chapter.qcScores.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(chapter.qcScores)
            Scored(
                scores = (1..7).map { o.optInt("c$it", 0) },
                rationales = (1..7).map { o.optString("r$it", "") }
            )
        }.getOrNull()
    }

    /** Serializes scores + rationales for chapters.qcScores; total into qcTotal. */
    fun toJson(scored: Scored): String = JSONObject().apply {
        scored.scores.forEachIndexed { i, s -> put("c${i + 1}", s) }
        scored.rationales.forEachIndexed { i, r -> if (r.isNotBlank()) put("r${i + 1}", r) }
    }.toString()

    /**
     * The L6 proposal (Table 17, deterministic-contribution column): anchors
     * the proposal so the writer's final scores are reviewed, not invented.
     * Criterion → deterministic input:
     *   1 hook validator, 2 worksheet section A + beat 2 coverage,
     *   3 beats 3-4 coverage + balance middle span, 4 worksheet section B,
     *   5 worksheet section C + ledger deltas, 6 payoff beat link,
     *   7 ending advisor result.
     */
    fun propose(
        hookPasses: Boolean,
        objectiveCovered: Boolean,
        conflictEscalationCovered: Boolean,
        worksheetB: Boolean,
        worksheetC: Boolean,
        payoffLinked: Boolean,
        endingEncourages: Boolean
    ): Scored = Scored(
        scores = listOf(
            if (hookPasses) 2 else 1,
            if (objectiveCovered) 2 else 0,
            if (conflictEscalationCovered) 2 else 1,
            if (worksheetB) 2 else 1,
            if (worksheetC) 2 else 1,
            if (payoffLinked) 2 else 0,
            if (endingEncourages) 2 else 1
        ),
        rationales = listOf(
            "Hook validator result" + if (hookPasses) ": source of interest present." else ": no source of interest in the opening span.",
            "Worksheet section A + beat 2 coverage" + if (objectiveCovered) "." else ": objective missing — zero-trigger.",
            "Beats 3-4 coverage and the balance middle span.",
            "Worksheet section B (character development).",
            "Worksheet section C (new information / plot advancement).",
            "Beat 6 payoff link" + if (payoffLinked) "." else ": no payoff — zero-trigger.",
            "Ending advisor result."
        )
    )

    /**
     * The export gate (Table 18 + zero-triggers). A chapter is releasable when:
     *   - band STRONG or REASONABLE (with a note on the weak criteria), AND
     *   - no zero-trigger is active, OR the writer supplied an explicit note
     *     covering it (overridable ONLY by an explicit writer note).
     * REVISION (6-8) is blocked from release until revised, or the writer
     * overrides with a note. RECONSIDER (0-5) is blocked, revision required —
     * never overridable.
     */
    fun gate(chapter: Chapter, writerNote: String? = null): GateResult {
        val scored = of(chapter)
            ?: return GateResult(
                chapterNumber = chapter.chapterNumber, total = -1, band = Band.STRONG,
                zeroTriggers = emptyList(), releasable = false, overridden = false,
                message = "No scorecard recorded — score the chapter before release (the scorecard is an editorial self-assessment aid)."
            )
        val band = Band.of(scored.total)
        val zeros = scored.zeroTriggered
        val hasNote = !writerNote.isNullOrBlank() && writerNote.isNotBlank()
        val zeroOverridden = zeros.isNotEmpty() && hasNote
        val releasable = when (band) {
            Band.STRONG, Band.REASONABLE -> zeros.isEmpty() || zeroOverridden
            Band.REVISION -> hasNote // blocked until revised, or writer overrides with note
            Band.RECONSIDER -> false  // blocked; revision required
        }
        val message = buildString {
            append("Scorecard ${scored.total}/14 — ${band.interpretation} (export: ${band.exportBehavior}).")
            if (zeros.isNotEmpty()) append(
                " Zero-trigger(s): ${zeros.joinToString()} — a revision prompt fires before export" +
                    (if (zeroOverridden) "; overridden by writer note (disclosed)." else "; overridable only by an explicit writer note.")
            )
            if (band == Band.REASONABLE && zeros.isEmpty()) append(" Writer note on the weak criteria accompanies the release.")
            if (band == Band.REVISION) append(if (hasNote) " Overridden by writer note (disclosed)." else " Blocked from release until revised, or override with a note.")
            if (band == Band.RECONSIDER) append(" Blocked; revision required — never overridable.")
        }
        return GateResult(
            chapterNumber = chapter.chapterNumber, total = scored.total, band = band,
            zeroTriggers = zeros, releasable = releasable,
            overridden = zeroOverridden || (band == Band.REVISION && hasNote),
            message = message
        )
    }

    /**
     * The export pre-flight checklist (§6.9): the ten final drafting
     * principles, then the four ultimate rules quoted verbatim.
     */
    val TEN_FINAL_PRINCIPLES = listOf(
        "Clear narrative purpose",
        "Identifiable objective",
        "Meaningful resistance",
        "Events producing consequences",
        "Changed situation",
        "Integrated exposition",
        "Some narrative gratification",
        "Appropriate forward pull",
        "No unnecessary repetition",
        "Contribution to the larger story"
    )

    val FOUR_ULTIMATE_RULES = listOf(
        "Never sacrifice narrative credibility merely to create a cliffhanger.",
        "Never sacrifice character development merely to accelerate the plot.",
        "Never withhold every important answer merely to generate curiosity.",
        "Never make the reader feel their time was wasted."
    )
}
