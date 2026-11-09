package com.example.engine.serialization

import com.example.data.model.Chapter
import com.example.engine.EndingTypes
import com.example.engine.LiveAstAnnotator

/**
 * The ending advisor — Blueprint 2.0 Part 3 (v3.0, plan §6.4, Table 12).
 *
 * Blueprint 2.0 expands the ending catalogue to SIX types and prohibits
 * mechanical rotation: the ending must be selected according to the
 * chapter's dramatic development and arise naturally from preceding events.
 * The 1.0 rotation wheel and its least-recently-used suggestion are NOT
 * implemented. The advisor therefore:
 *   - classifies the closing passage against the six types (+ QUIET);
 *   - records type history per chapter;
 *   - raises a SOFT fatigue flag only when the same high-intensity type
 *     repeats in consecutive chapters;
 *   - never auto-assigns a type (the writer confirms or picks another);
 *   - supports quiet, reflective endings as a first-class classification.
 *
 * The objective rule quoted in the product: an effective chapter ending does
 * not always need to shock — a character's decision, an unexpected emotional
 * realization, or a fulfilled promise is often more compelling.
 */
object EndingAdvisor {

    /** High-intensity types the fatigue rule watches (QUIET and ANTICIPATION are gentler). */
    private val highIntensity = OpeningValidators.HIGH_INTENSITY_ENDINGS

    data class Classification(val chapterNumber: Int, val type: String, val cueHits: Int)

    data class FatigueFlag(val type: String, val chapters: List<Int>, val message: String)

    data class AdvisorReport(
        /** Classification per chapter number (canon chapters only). */
        val history: List<Classification>,
        /** Consecutive same-type high-intensity repeats — soft, advisory. */
        val fatigueFlags: List<FatigueFlag>,
        /** The six selection principles the advisor applies (never auto-assigns). */
        val selectionPrinciples: List<String>,
        val notes: List<String>
    )

    /**
     * Classifies every canon chapter's closing passage and checks the type
     * history for consecutive same-type high-intensity repeats. Returns the
     * history plus soft fatigue flags; callers persist the type into
     * chapters.endingType (§6.12) after the writer confirms it.
     */
    fun advise(chapters: List<Chapter>): AdvisorReport {
        val canon = chapters.filter { com.example.engine.WriterGate.isCanon(it) }.sortedBy { it.chapterNumber }
        val history = canon.map { ch ->
            val (type, hits) = LiveAstAnnotator.classifyEnding(ch.content)
            Classification(ch.chapterNumber, type, hits)
        }
        val fatigueFlags = mutableListOf<FatigueFlag>()
        var i = 0
        while (i < history.size) {
            val type = history[i].type
            if (type in highIntensity) {
                var j = i
                while (j + 1 < history.size && history[j + 1].type == type) j++
                if (j > i) {
                    val span = history.slice(i..j).map { it.chapterNumber }
                    fatigueFlags.add(FatigueFlag(
                        type = type, chapters = span,
                        message = "Ending fatigue: $type repeats in consecutive chapters ${span.first()}-${span.last()}. " +
                            "The ending must arise naturally from the chapter's dramatic development — consider a quieter or different classification."
                    ))
                }
                i = j + 1
            } else i++
        }
        return AdvisorReport(
            history = history,
            fatigueFlags = fatigueFlags,
            selectionPrinciples = listOf(
                "The advisor never auto-assigns a type; the writer confirms or picks another.",
                "The ending's stakes must be understandable.",
                "The unresolved question must matter to the protagonist or the central plot.",
                "Consecutive high-intensity endings of the same type raise an advisory fatigue flag only.",
                "Endings that promise answers the following chapters do not deliver surface at the next chapter's drafting time (promise ledger cross-reference).",
                "An effective chapter ending does not always need to shock."
            ),
            notes = fatigueFlags.map { it.message }
        )
    }

    /** JSON fragment persisted into chapters.endingType / advisor history. */
    fun historyJson(report: AdvisorReport): String = report.history.joinToString(",") {
        """"${it.chapterNumber}:${it.type}""""
    }.let { "[$it]" }
}
