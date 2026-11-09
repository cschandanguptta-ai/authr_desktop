package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.engine.DraftState
import com.example.engine.LiveAstAnnotator
import com.example.engine.QuestionLedger
import com.example.engine.WriterGate

/**
 * The serialization validator suite — the deterministic Blueprint 2.0 gate
 * over a serial project (v3.0, plan §6; exit gate §13 Table 33: "a
 * 15-chapter golden serial passes the full validator suite with at most two
 * dismissable warnings; the anti-withholding gate fires correctly on a
 * seeded free tier; scorecard gates behave per the interpretation bands").
 *
 * Severity model:
 *  - [Severity.PASS]  — check ran and passed (proof of the exit gate).
 *  - [Severity.INFO]  — informational measurement (balance bands, ending
 *    classifications); NEVER blocking, per plan Table 11.
 *  - [Severity.WARN]  — dismissable warning; the writer can dismiss any
 *    warning with a note (risk register: "every serialization and quality
 *    check is tunable per project, dismissable with a note").
 *  - [Severity.HARD]  — export-blocking gate: the writer-sovereignty canon
 *    gate, the anti-withholding gate, the scorecard interpretation bands and
 *    zero-triggers, and an unclosed arc's unanswered arc-level question.
 */
object SerialValidatorSuite {

    enum class Severity { PASS, INFO, WARN, HARD }

    data class Check(
        val id: String,
        val chapter: Int?,
        val severity: Severity,
        val message: String,
        /** WARNs are dismissable; a dismissed check does not count against the warning budget. */
        val dismissable: Boolean = severity == Severity.WARN,
        /** Dismissal key: "<id>:<chapter|project>". */
        val key: String = "$id:${chapter ?: "project"}"
    ) {
        val blocks: Boolean get() = severity == Severity.HARD
    }

    data class Report(
        val checks: List<Check>,
        val dismissed: Set<String>,
        val buffer: BufferCadence.BufferReport?,
        val cadence: BufferCadence.CadenceReport?,
        val arcReportLines: List<String>
    ) {
        val warnings: List<Check> get() = checks.filter { it.severity == Severity.WARN && it.key !in dismissed }
        val hardBlocks: List<Check> get() = checks.filter { it.severity == Severity.HARD }
        val passes: Boolean get() = hardBlocks.isEmpty()
        /** The exit-gate predicate: green with at most two dismissable warnings. */
        val exitGateGreen: Boolean get() = passes && warnings.size <= 2
        val passedCount: Int get() = checks.count { it.severity == Severity.PASS }
    }

    data class Input(
        val book: Book,
        val chapters: List<Chapter>,
        val arcs: List<SerialArc> = emptyList(),
        val questions: List<QuestionEntry> = emptyList(),
        /** Writer override notes for the scorecard gates, keyed by chapter number. */
        val scorecardNotes: Map<Int, String> = emptyMap(),
        /** Dismissed check keys ("id:chapter"); a dismissed WARN leaves the budget. */
        val dismissals: Set<String> = emptySet()
    )

    /**
     * Runs the full suite over the serial project. Non-serialized books
     * short-circuit to an all-pass report — every serialization field is
     * ignored outside the serial profile (plan §6.12).
     */
    fun run(input: Input): Report {
        if (!input.book.isSerialized) return Report(
            checks = listOf(Check("not_serialized", null, Severity.PASS, "Non-serialized project — the serialization profile is inactive.")),
            dismissed = emptySet(), buffer = null, cadence = null, arcReportLines = emptyList()
        )
        val checks = mutableListOf<Check>()
        val canon = input.chapters.filter { WriterGate.isCanon(it) }.sortedBy { it.chapterNumber }
        val proposed = input.chapters.filter { it.draftStatus == DraftState.PROPOSED && !it.writerConfirmed }

        // -- The writer-sovereignty gate composes first (P1) ----------------
        input.chapters.filter { !WriterGate.isCanon(it) }.forEach { ch ->
            checks.add(Check(
                "canon_gate", ch.chapterNumber, Severity.HARD,
                "Chapter ${ch.chapterNumber} is ${ch.draftStatus} — unconfirmed text is withheld from every export surface (confirm it in the Writer Studio)."
            ))
        }

        // -- Opening validators, free tier (§6.2) ---------------------------
        canon.filter { it.chapterNumber <= input.book.freeTierChapters }.forEach { ch ->
            val hook = OpeningValidators.validateHook(ch)
            checks.add(if (hook.passes) Check("hook", ch.chapterNumber, Severity.PASS, hook.message)
            else Check("hook", ch.chapterNumber, Severity.WARN, hook.message))
        }
        val ch1 = canon.firstOrNull { it.chapterNumber == 1 }
        if (ch1 != null) {
            val missingGoals = OpeningValidators.storyPromise(ch1)
            checks.add(if (missingGoals.isEmpty())
                Check("story_promise", 1, Severity.PASS, "Chapter 1 story promise: all five goals present.")
            else Check("story_promise", 1, Severity.WARN,
                "Chapter 1 story promise misses: ${missingGoals.joinToString()}."))
        }
        canon.filter { it.chapterNumber in 2..3 }.forEach { ch ->
            val runs = OpeningValidators.expositionRuns(ch)
            checks.add(if (runs.isEmpty())
                Check("exposition_run", ch.chapterNumber, Severity.PASS, "Chapter ${ch.chapterNumber}: no exposition run (the world explains itself through action and observation).")
            else Check("exposition_run", ch.chapterNumber, Severity.WARN,
                "Chapter ${ch.chapterNumber}: ${runs.size} narration stretch(es) of ${runs.maxOf { it.words }} words without dialogue or action — do not spend the opening chapters explaining the world."))
        }
        canon.filter { it.chapterNumber in 4..5 }.forEach { ch ->
            val developments = OpeningValidators.substantialDevelopments(ch)
            checks.add(if (developments.isNotEmpty())
                Check("substantial_development", ch.chapterNumber, Severity.PASS,
                    "Chapter ${ch.chapterNumber}: substantial development present (${developments.first()}).")
            else Check("substantial_development", ch.chapterNumber, Severity.WARN,
                "Chapter ${ch.chapterNumber}: no substantial development — strengthen the conversion point before the paywall."))
        }

        // -- The anti-withholding gate (§6.2 Table 9; HARD at export) -------
        val withholding = OpeningValidators.antiWithholdingGate(input.book, input.chapters)
        checks.add(if (withholding.passes)
            Check("anti_withholding", null, Severity.PASS, withholding.message)
        else Check("anti_withholding", withholding.finalFreeChapter, Severity.HARD,
            withholding.message + " Resolve an important part of the opening conflict in the final free chapter and seed a more consequential development — do not withhold a deliverable payoff to force payment."))

        // -- Seven-beat coverage (§6.3) --------------------------------------
        canon.forEach { ch ->
            val coverage = LiveAstAnnotator.beatCoverage(ch.content).firstOrNull()
            val covered = coverage?.payload?.get("coverage") ?: "0/7"
            checks.add(if (covered == "7/7")
                Check("beat_coverage", ch.chapterNumber, Severity.PASS, "Chapter ${ch.chapterNumber}: all seven beat functions covered.")
            else Check("beat_coverage", ch.chapterNumber, Severity.WARN,
                "Chapter ${ch.chapterNumber}: $covered beat functions covered — missing: ${coverage?.payload?.get("missing") ?: "unknown"}. The writer can address or waive with a note."))
        }

        // -- Balance meter: informational, never blocking (§6.3 Table 11) ----
        canon.forEach { ch ->
            val report = BalanceMeter.measure(ch, input.book)
            report.notes.forEach { note ->
                checks.add(Check("balance", ch.chapterNumber, Severity.INFO, note))
            }
        }

        // -- Ending advisor: classifications + soft fatigue (§6.4) -----------
        val advisor = EndingAdvisor.advise(input.chapters)
        advisor.history.forEach { c ->
            val stored = canon.firstOrNull { it.chapterNumber == c.chapterNumber }?.endingType
            val agreed = stored == null || stored == c.type
            checks.add(Check(
                "ending", c.chapterNumber,
                if (agreed) Severity.PASS else Severity.INFO,
                "Chapter ${c.chapterNumber}: closing signal ${c.type}" +
                    (if (stored == null) " (not yet confirmed by the writer — the advisor never auto-assigns)."
                    else if (agreed) " matches the writer-confirmed type." else " differs from the writer-confirmed $stored — informational; the writer picks the type.")
            ))
        }
        advisor.fatigueFlags.forEach { flag ->
            checks.add(Check("ending_fatigue", flag.chapters.last(), Severity.WARN, flag.message))
        }

        // -- Worksheet (§6.8) -------------------------------------------------
        canon.forEach { ch ->
            val missing = PreDraftWorksheet.validate(PreDraftWorksheet.of(ch))
            checks.add(if (missing.isEmpty())
                Check("worksheet", ch.chapterNumber, Severity.PASS, "Chapter ${ch.chapterNumber}: worksheet complete (sections A-E).")
            else Check("worksheet", ch.chapterNumber, Severity.WARN,
                "Chapter ${ch.chapterNumber}: worksheet incomplete — missing ${missing.joinToString()}. The L4 drafter consumes the worksheet as prompt context."))
        }

        // -- Scorecard gates (§6.9 Tables 17-18) ------------------------------
        canon.forEach { ch ->
            val note = input.scorecardNotes[ch.chapterNumber]
            val gate = QcScorecard.gate(ch, note)
            checks.add(if (gate.releasable)
                Check("scorecard", ch.chapterNumber, Severity.PASS, "Chapter ${ch.chapterNumber}: ${gate.message}")
            else Check("scorecard", ch.chapterNumber, Severity.HARD, "Chapter ${ch.chapterNumber}: ${gate.message}"))
        }

        // -- Micro-arc planner (§6.5) -----------------------------------------
        if (input.arcs.isEmpty()) {
            checks.add(Check("arc_plan", null, Severity.WARN,
                "No micro-arc plan — lay arcs across the ${input.book.targetChapters} chapters with the planner (the arc is the macro planning unit)."))
        } else {
            val lastCanonChapter = canon.maxOfOrNull { it.chapterNumber } ?: 0
            val arcChecks = MicroArcPlanner.checkArcs(input.arcs, input.chapters, input.questions)
            arcChecks.forEach { c ->
                val arc = input.arcs.first { it.arcIndex == c.arcIndex }
                val arcClosed = MicroArcPlanner.arcSpan(arc).last <= lastCanonChapter
                val severity = when {
                    c.passes -> Severity.PASS
                    // The completion requirement is the hard rule (§6.5): an arc
                    // cannot close while its arc-level question is unanswered.
                    arcClosed && c.check == "completion_requirement" -> Severity.HARD
                    else -> Severity.WARN
                }
                checks.add(Check("arc_${c.check}", null, severity, c.message))
            }
            input.arcs.forEach { arc ->
                checks.add(Check("arc_contribution", null, Severity.INFO,
                    "Ship report — " + MicroArcPlanner.shipReportLines(listOf(arc), arcChecks).first()))
            }
        }

        // -- Question ledger: overdue surfacing (§6.7) ------------------------
        val currentChapter = canon.maxOfOrNull { it.chapterNumber } ?: 0
        QuestionLedger.overdueReport(input.questions, currentChapter).forEach { line ->
            checks.add(Check("question_overdue", null, Severity.WARN, line + " Surface it on the writer's dashboard."))
        }

        // -- Buffer & cadence (§6.10) -----------------------------------------
        val buffer = BufferCadence.buffer(input.book, input.chapters)
        checks.add(if (buffer.level == BufferCadence.BufferLevel.RED)
            Check("buffer", null, Severity.WARN, buffer.message)
        else Check("buffer", null, Severity.PASS, buffer.message))
        val cadence = BufferCadence.cadence(input.book, input.chapters)
        if (cadence.slips.isEmpty()) {
            checks.add(Check("cadence", null, Severity.PASS, cadence.message))
        } else {
            cadence.slips.forEach { slip ->
                checks.add(Check("cadence", null, Severity.WARN, slip))
            }
        }

        return Report(
            checks = checks,
            dismissed = input.dismissals,
            buffer = buffer,
            cadence = cadence,
            arcReportLines = MicroArcPlanner.shipReportLines(input.arcs, MicroArcPlanner.checkArcs(input.arcs, input.chapters, input.questions))
        )
    }

    /**
     * The Autonomous Studio run report (§6.11): the same checks over PROPOSED
     * chapters before they enter the review queue — advisory only, no hard
     * gates (proposals are not canon and never reach export).
     */
    fun draftReport(book: Book, proposedChapters: List<Chapter>): List<String> {
        if (!book.isSerialized) return emptyList()
        val lines = mutableListOf<String>()
        proposedChapters.forEach { ch ->
            val coverage = LiveAstAnnotator.beatCoverage(ch.content).firstOrNull()?.payload?.get("coverage") ?: "0/7"
            val hook = OpeningValidators.validateHook(ch)
            val ending = LiveAstAnnotator.classifyEnding(ch.content).first
            lines.add("PROPOSED ch. ${ch.chapterNumber}: beats $coverage, hook ${if (hook.passes) "present" else "MISSING"}, closing signal $ending (failed coverage re-drafts once, then flags).")
        }
        return lines
    }
}
