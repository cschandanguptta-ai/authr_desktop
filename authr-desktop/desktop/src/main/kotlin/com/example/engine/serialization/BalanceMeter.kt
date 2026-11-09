package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.ChapterBeat
import com.example.engine.LiveAstAnnotator

/**
 * The seven-beat balance meter — Blueprint 2.0 Part 2 (v3.0, plan §6.3,
 * Tables 10-11).
 *
 * Measures each chapter's segment shares against the advisory bands:
 *   - Opening and continuity (beats 1-2): 10-15%
 *   - Objective, conflict, escalation (beats 2-4): 50-60%
 *   - Turning point and payoff (beats 5-6): 20-25%
 *   - Forward pull (beat 7): 10-15%
 *
 * Deviations are INFORMATIONAL, never blocking — the blueprint allows
 * substantial deviation for action sequences, emotional confrontations,
 * revelations, or quiet development. The chapter-length envelope
 * (wordMin/wordMax, default 1,000-2,000) is advisory too: warnings outside
 * the envelope never block, and the tooling never asks the writer to pad or
 * split a scene to hit a number (plan Table 11, last row).
 */
object BalanceMeter {

    /** Advisory band: name, member beats, min share %, max share %. */
    data class Band(val name: String, val beats: Set<ChapterBeat>, val minPct: Double, val maxPct: Double)

    val BANDS = listOf(
        Band("Opening and continuity", setOf(ChapterBeat.OPENING_CONTINUITY), 10.0, 15.0),
        Band("Objective, conflict, escalation", setOf(ChapterBeat.IMMEDIATE_OBJECTIVE, ChapterBeat.RESISTANCE, ChapterBeat.ESCALATION), 50.0, 60.0),
        Band("Turning point and payoff", setOf(ChapterBeat.TURNING_POINT, ChapterBeat.CHAPTER_PAYOFF), 20.0, 25.0),
        Band("Forward pull", setOf(ChapterBeat.FORWARD_PULL), 10.0, 15.0)
    )

    data class Share(val band: String, val measuredPct: Double, val minPct: Double, val maxPct: Double) {
        val withinBand: Boolean get() = measuredPct in minPct..maxPct
    }

    data class BalanceReport(
        val chapterNumber: Int,
        val totalWords: Int,
        val shares: List<Share>,
        /** Present beat anchors with their span start (0..1 relative position). */
        val anchors: Map<ChapterBeat, Double>,
        val wordEnvelope: Pair<Int, Int>?,
        /** True when the chapter sits outside wordMin/wordMax (advisory only). */
        val outsideEnvelope: Boolean,
        val notes: List<String>
    )

    /**
     * Measures the chapter's beat shares from the first cue span of each
     * present beat (the same deterministic cues the Live AST Annotator
     * paints). A beat that is absent contributes its mass to the band of
     * the nearest present predecessor so shares always sum to 100.
     */
    fun measure(chapter: Chapter, book: Book? = null): BalanceReport {
        val text = chapter.content
        val total = text.length.coerceAtLeast(1)
        val present = ChapterBeat.entries.filter { beat ->
            beatCuesFor(beat).any { it.containsMatchIn(text) }
        }
        val anchors = ChapterBeat.entries.associateWith { beat ->
            val idx = beatCuesFor(beat).firstNotNullOfOrNull { rx -> rx.find(text)?.range?.first }
            (idx ?: 0).toDouble() / total
        }
        // Segment boundaries: beat i (in anchor order) owns the span from
        // its anchor to the next beat's anchor — the first beat also owns
        // the lead-in from the chapter's start, the last beat owns the run
        // to the chapter's end, so the shares always cover 100%.
        val boundaries = present.map { anchors[it] ?: 0.0 }.sorted()

        // Map each present beat to its band; fold absent beats' share into
        // the previous present beat's band (or the first band when leading).
        val bandOfBeat: Map<ChapterBeat, String> = BANDS.flatMap { b -> b.beats.map { it to b.name } }.toMap()
        val sharesAccumulator = BANDS.associate { it.name to 0.0 }.toMutableMap()
        present.forEachIndexed { i, beat ->
            val start = if (i == 0) 0.0 else boundaries[i]
            val end = if (i == present.size - 1) 1.0 else boundaries[i + 1]
            val spanLen = end - start
            val bandName = bandOfBeat[beat] ?: BANDS.last().name
            sharesAccumulator[bandName] = (sharesAccumulator[bandName] ?: 0.0) + spanLen * 100.0
        }
        if (present.isEmpty()) {
            // No beat anchors at all: everything is unattributed; report 0s.
        }
        val shares = BANDS.map { b ->
            Share(b.name, (sharesAccumulator[b.name] ?: 0.0), b.minPct, b.maxPct)
        }
        val envelope = book?.let { it.wordMin to it.wordMax }
        val wc = OpeningValidators.wordCount(text)
        val outside = envelope != null && (wc < envelope.first || wc > envelope.second)
        val notes = buildList {
            shares.filter { !it.withinBand }.forEach {
                add("Balance advisory: ${it.band} measures ${"%.0f".format(it.measuredPct)}% against the ${it.minPct}-${it.maxPct}% band — informational, never blocking.")
            }
            if (outside && envelope != null) add(
                "Chapter length $wc words sits outside the ${envelope.first}-${envelope.second} word envelope — advisory; do not pad or split a scene to hit a number."
            )
        }
        return BalanceReport(
            chapterNumber = chapter.chapterNumber,
            totalWords = wc,
            shares = shares,
            anchors = anchors,
            wordEnvelope = envelope,
            outsideEnvelope = outside,
            notes = notes
        )
    }

    /** Exposes the annotator's beat cues for the meter (single source of truth). */
    private fun beatCuesFor(beat: ChapterBeat): List<Regex> =
        LiveAstAnnotator.beatCueList(beat)

    /** JSON fragment persisted into chapters.balanceShares (plan §6.12). */
    fun balanceSharesJson(report: BalanceReport): String = buildString {
        append("{")
        append(report.shares.joinToString(",") { """"${it.band}":${"%.1f".format(it.measuredPct)}""" })
        append("}")
    }
}
