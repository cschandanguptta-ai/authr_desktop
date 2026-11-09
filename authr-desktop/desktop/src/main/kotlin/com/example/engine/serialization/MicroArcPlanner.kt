package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.engine.serialization.MicroArcPlanner.Phase

/**
 * The micro-arc planner — Blueprint 2.0 Part 4 (v3.0, plan §6.5, Table 13).
 *
 * The micro-arc is the macro planning unit: a chained 10-15 chapter cycle
 * with a Reversal & Pivot phase between escalation and climax, and an
 * explicit completion requirement. Arcs are first-class objects
 * (serial_arcs, §6.12); the planner lays arcs across the book's
 * targetChapters, each arc declaring its localized conflict, its phase
 * boundaries, and its contribution to the larger novel.
 *
 * Completion requirement (§6.5): every micro-arc must answer at least one
 * important question introduced within that arc, and must do at least one
 * of: advance the central story; develop the protagonist; change an
 * important relationship or power dynamic; reveal meaningful information;
 * or introduce a new complication or objective. Exact chapter allocation
 * flexes per story; the phase boundaries are defaults the planner can slide.
 */
object MicroArcPlanner {

    /** The four micro-arc phases with their default chapter spans. */
    enum class Phase(val title: String, val defaultStart: Int, val defaultEnd: Int) {
        SETUP("Objective and setup", 1, 3),
        ESCALATION("Escalation", 4, 7),
        REVERSAL("Reversal and pivot", 8, 11),
        CLIMAX("Climax and resolution", 12, 15)
    }

    /** The five contribution verbs of the completion requirement (any one suffices). */
    val CONTRIBUTION_KINDS = listOf(
        "advance the central story",
        "develop the protagonist",
        "change an important relationship or power dynamic",
        "reveal meaningful information",
        "introduce a new complication or objective"
    )

    /** Phase bounds JSON schema: {"setup":[1,3],"escalation":[4,7],"reversal":[8,11],"climax":[12,15]}
     *  For a truncated arc (the final arc of a book whose chapter count does
     *  not divide by 15), [length] compresses the four phases proportionally
     *  over the actual span so every arc can still close (§6.5: boundaries
     *  are defaults the planner can slide). */
    fun phaseBoundsJson(startChapter: Int, length: Int = 15): String {
        val l = length.coerceIn(Phase.entries.size, 15)
        // Default phase sizes are 3,4,4,4 (sum 15) — scale proportionally,
        // at least one chapter per phase, then distribute the rounding drift.
        val raw = Phase.entries.map { (it.defaultEnd - it.defaultStart + 1) * l / 15.0 }
        val sizes = raw.map { it.toInt().coerceAtLeast(1) }.toMutableList()
        var drift = l - sizes.sum()
        while (drift != 0) {
            val i = sizes.withIndex().maxByOrNull { it.value }!!.index
            if (drift > 0) { sizes[i] += 1; drift -= 1 }
            else if (sizes[i] > 1) { sizes[i] -= 1; drift += 1 }
            else break
        }
        return buildString {
            append("{")
            var cursor = startChapter
            Phase.entries.forEachIndexed { i, p ->
                if (i > 0) append(",")
                append(""""${p.name.lowercase()}":[$cursor,${cursor + sizes[i] - 1}]""")
                cursor += sizes[i]
            }
            append("}")
        }
    }

    /** Parses phase bounds JSON back to (phase -> [start, end]). */
    fun parsePhaseBounds(json: String): Map<Phase, IntRange> {
        val map = mutableMapOf<Phase, IntRange>()
        Regex(""""(\w+)":\[(\d+),(\d+)]""").findAll(json).forEach { m ->
            Phase.entries.firstOrNull { it.name.equals(m.groupValues[1], ignoreCase = true) }?.let { p ->
                map[p] = m.groupValues[2].toInt()..m.groupValues[3].toInt()
            }
        }
        return map
    }

    /**
     * Lays default arcs across the book's chapter count: 15-chapter arcs
     * (the blueprint's cycle), each offset by 15. The final arc is truncated
     * at the book's target when the count does not divide evenly, with its
     * phase bounds compressed proportionally so it can still close.
     */
    fun planArcs(book: Book, conflicts: Map<Int, String> = emptyMap(), questions: Map<Int, Int> = emptyMap()): List<SerialArc> {
        val target = if (book.targetChapters > 0) book.targetChapters else 15
        val arcs = mutableListOf<SerialArc>()
        var arcIndex = 1
        var start = 1
        while (start <= target) {
            val end = minOf(start + 14, target)
            arcs.add(
                SerialArc(
                    bookId = book.id,
                    arcIndex = arcIndex,
                    conflict = conflicts[arcIndex] ?: "Localized conflict of arc $arcIndex (writer edits this statement).",
                    questionRef = questions[arcIndex],
                    phaseBounds = phaseBoundsJson(start, end - start + 1),
                    contribution = "",
                    seeds = "[]"
                )
            )
            arcIndex++
            start += 15
        }
        return arcs
    }

    /** The chapter span an arc covers, from its parsed phase bounds. */
    fun arcSpan(arc: SerialArc): IntRange {
        val bounds = parsePhaseBounds(arc.phaseBounds)
        val starts = bounds.values.map { it.first }
        val ends = bounds.values.map { it.last }
        return if (starts.isEmpty() || ends.isEmpty()) 1..1 else (starts.min()..(ends.max()))
    }

    // ------------------------------------------------------------------
    // Planner checks (Table 13, planner-check column)
    // ------------------------------------------------------------------

    data class ArcCheck(val arcIndex: Int, val check: String, val passes: Boolean, val message: String)

    private val conflictCue = Regex(
        """\b(conflict|enemy|rival|threat|dispute|obstacle|antagonist|struggle|war|hunt|debt|revenge|contested)\b""",
        RegexOption.IGNORE_CASE
    )
    private val escalationBeatCue = Regex(
        """\b(worse|harder|more dangerous|escalat\w*|worsened|no longer|even more|too late|deadline|countdown|setback)\b""",
        RegexOption.IGNORE_CASE
    )
    private val reversalBeatCue = Regex(
        """\b(realiz\w*|discovered|the truth|reversal|turned|shifted|changed everything|assumption|challenged everything)\b""",
        RegexOption.IGNORE_CASE
    )
    private val climaxBeatCue = Regex(
        """\b(climax|decisive|confrontation|final (battle|stand|confrontation)|at last|the (fight|duel|confrontation) (ended|was over)|decisive confrontation)\b""",
        RegexOption.IGNORE_CASE
    )
    private val payoffBeatCue = Regex(
        """\b(payoff|succeeded|failed|won|lost|consequence|reward|resolved|it was done|nothing would be the same)\b""",
        RegexOption.IGNORE_CASE
    )
    private val nextObjectiveSeedCue = Regex(
        """\b(next (arc|objective|step|target|mission)|the next narrative objective|a new (objective|quest|problem|question)|what comes next|looked toward)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Runs the planner checks over every arc that has canon chapters inside
     * its span. An arc is COMPLETE when all its chapters exist and are
     * canon; complete arcs must pass every check. In-flight arcs report
     * progress only (no blocking).
     */
    fun checkArcs(
        arcs: List<SerialArc>,
        chapters: List<Chapter>,
        questions: List<QuestionEntry>
    ): List<ArcCheck> {
        val canon = chapters.filter { com.example.engine.WriterGate.isCanon(it) }
        return arcs.flatMap { arc ->
            val span = arcSpan(arc)
            val arcChapters = canon.filter { it.chapterNumber in span }
            val arcComplete = arcChapters.size == (span.last - span.first + 1)
            val checks = mutableListOf<ArcCheck>()
            fun add(check: String, passes: Boolean, message: String) =
                checks.add(ArcCheck(arc.arcIndex, check, passes, message))

            // 1. Conflict statement recorded and conflict introduced by chapter 3 of the arc.
            val setupSpan = parsePhaseBounds(arc.phaseBounds)[Phase.SETUP] ?: (span.first..(span.first + 2))
            val hasConflictStatement = arc.conflict.isNotBlank() && conflictCue.containsMatchIn(arc.conflict)
            val conflictIntroduced = arcChapters.filter { it.chapterNumber <= setupSpan.last }
                .any { conflictCue.containsMatchIn(it.content) || conflictCue.containsMatchIn(it.summary) }
            add("conflict_by_ch3", !arcComplete || (hasConflictStatement && conflictIntroduced),
                "Arc ${arc.arcIndex}: conflict statement ${if (hasConflictStatement) "recorded" else "MISSING"}; " +
                    "conflict ${if (conflictIntroduced) "introduced by chapter ${setupSpan.last}" else "not yet introduced by chapter ${setupSpan.last}"}.")

            // 2. At least two escalation beats confirmed across the escalation span.
            val escalationSpan = parsePhaseBounds(arc.phaseBounds)[Phase.ESCALATION] ?: (span.first + 3..span.first + 6)
            val escalationBeats = arcChapters.filter { it.chapterNumber in escalationSpan && escalationBeatCue.containsMatchIn(it.content) }
            add("escalation_beats", !arcComplete || escalationBeats.size >= 2,
                "Arc ${arc.arcIndex}: ${escalationBeats.size} escalation beat(s) in chapters ${escalationSpan.first}-${escalationSpan.last} (need 2 for a complete arc).")

            // 3. A reversal beat confirmed in the reversal phase.
            val reversalSpan = parsePhaseBounds(arc.phaseBounds)[Phase.REVERSAL] ?: (span.first + 7..span.first + 10)
            val reversalBeat = arcChapters.filter { it.chapterNumber in reversalSpan && reversalBeatCue.containsMatchIn(it.content) }
            add("reversal_beat", !arcComplete || reversalBeat.isNotEmpty(),
                "Arc ${arc.arcIndex}: reversal beat ${if (reversalBeat.isNotEmpty()) "confirmed in chapters $reversalSpan" else "MISSING in chapters $reversalSpan"}.")

            // 4. Climax and payoff beats confirmed in the climax phase.
            val climaxSpan = parsePhaseBounds(arc.phaseBounds)[Phase.CLIMAX] ?: (span.first + 11..span.last)
            val climaxChapters = arcChapters.filter { it.chapterNumber in climaxSpan }
            val hasClimax = climaxChapters.any { climaxBeatCue.containsMatchIn(it.content) }
            val hasPayoff = climaxChapters.any { payoffBeatCue.containsMatchIn(it.content) }
            add("climax_payoff", !arcComplete || (hasClimax && hasPayoff),
                "Arc ${arc.arcIndex}: climax ${if (hasClimax) "confirmed" else "MISSING"}; payoff ${if (hasPayoff) "confirmed" else "MISSING"} in chapters $climaxSpan.")

            // 5. Next-arc objective seeded before arc close.
            val seed = arcChapters.any { nextObjectiveSeedCue.containsMatchIn(it.content) } ||
                parseSeeds(arc.seeds).isNotEmpty()
            add("next_arc_seed", !arcComplete || seed,
                "Arc ${arc.arcIndex}: next-arc objective ${if (seed) "seeded" else "MISSING"} before arc close.")

            // 6. Completion requirement: the arc's question is answered at close
            //    and the contribution declares one of the five kinds.
            val arcQuestion = questions.firstOrNull { it.id == arc.questionRef }
            val questionAnswered = arcQuestion == null || arcQuestion.answeredChapter != null
            val contributes = CONTRIBUTION_KINDS.any { arc.contribution.contains(it, ignoreCase = true) }
            add("completion_requirement", !arcComplete || (questionAnswered && contributes),
                "Arc ${arc.arcIndex}: arc question ${if (arcQuestion == null) "not linked" else if (questionAnswered) "answered" else "UNANSWERED"}; " +
                    "contribution ${if (contributes) "declared" else "NOT declared"} (need one of: ${CONTRIBUTION_KINDS.joinToString()}).")

            checks
        }
    }

    /** Parses the arc's seeds JSON array into strings. */
    fun parseSeeds(json: String): List<String> =
        Regex(""""([^"]+)"""").findAll(json).map { it.groupValues[1] }.toList()

    /**
     * The ship-report line per arc (§6.5: "the ship report for a serial
     * lists every arc's contribution — preventing the fifteen-chapter
     * sequence that reads as unrelated episodes").
     */
    fun shipReportLines(arcs: List<SerialArc>, checks: List<ArcCheck>): List<String> =
        arcs.sortedBy { it.arcIndex }.map { arc ->
            val failed = checks.filter { it.arcIndex == arc.arcIndex && !it.passes }
            "Arc ${arc.arcIndex} (ch. ${arcSpan(arc)}): " +
                (if (arc.contribution.isNotBlank()) "contribution — ${arc.contribution}" else "contribution not declared") +
                (if (failed.isEmpty()) "; all planner checks pass." else "; failing: " + failed.joinToString { it.check } + ".")
        }
}
