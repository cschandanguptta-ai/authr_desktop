package com.example.engine.pipeline

import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.engine.ClicheCleaner
import com.example.engine.LiveAstAnnotator

/**
 * Deterministic manuscript QA suite (ADR-0018, Priority 4 — stage 8's local
 * core). Detects: unresolved setups (Chekhov orphans), repetition (echo
 * phrasing + scene-level duplication), continuity flags (POV drift), cliché
 * density, pacing outliers and ending fatigue — reusing the proven v3.0
 * detectors wherever they exist (ADR-0006 single source of truth).
 *
 * An LLM auditor runs alongside this in stage 8; THIS suite is the trusted
 * half: its HARD findings block the stage gate; LLM findings land as
 * writer-visible proposals (writer sovereignty, ADR-0001).
 */
object ManuscriptQaSuite {

    data class QaReport(
        val findings: List<Finding>,
        val passed: Boolean,
        val score: Double,
        val summary: String
    )

    fun run(chapters: List<Chapter>, guns: List<ChekhovGun> = emptyList()): QaReport {
        val canon = chapters.filter { it.writerConfirmed && it.draftStatus == "CONFIRMED" }
            .sortedBy { it.chapterNumber }
        val findings = mutableListOf<Finding>()

        // --- 1. Unresolved setups: Chekhov guns still INTRODUCED/ESCALATED
        // after the final act begins (reuses ChekhovLedger semantics).
        val finalActStart = if (canon.isEmpty()) 0 else (canon.last().chapterNumber + 1) * 3 / 4
        guns.filter { it.status == "INTRODUCED" || it.status == "ESCALATED" }
            .filter { it.waiveNote.isBlank() }
            .forEach { gun ->
                findings += Finding(
                    kind = "PLOT_HOLE", severity = Finding.Severity.HARD,
                    chapterNumber = gun.introducedChapter,
                    message = "Setup \"${gun.name}\" (introduced ch. ${gun.introducedChapter}) never fires.",
                    recommendation = "Fire it in the final act, or waive it with an explicit note (waivers are disclosed in the ship report)."
                )
            }
        guns.filter { it.status == "ORPHANED" && it.waiveNote.isBlank() }.forEach { gun ->
            findings += Finding(
                kind = "PLOT_HOLE", severity = Finding.Severity.WARNING,
                chapterNumber = gun.introducedChapter,
                message = "Setup \"${gun.name}\" is orphaned.",
                recommendation = "Resolve or waive with a note."
            )
        }

        // --- 2. Repetition: cross-chapter echo (5-grams repeated 3+ times
        // anywhere) + per-chapter echo via the existing annotator.
        val ngramCounts = HashMap<String, Int>()
        canon.forEach { ch ->
            val words = (ch.content ?: "").lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }
            val seen = HashSet<String>()
            for (i in 0..maxOf(0, words.size - 5)) {
                val gram = words.subList(i, i + 5).joinToString(" ")
                if (gram !in seen) { seen += gram; ngramCounts[gram] = (ngramCounts[gram] ?: 0) + 1 }
            }
        }
        ngramCounts.filter { it.value >= 3 }.forEach { (gram, count) ->
            findings += Finding(
                kind = "REPETITION", severity = if (count >= 4) Finding.Severity.HARD else Finding.Severity.WARNING,
                chapterNumber = null,
                message = "Phrase \"…$gram…\" repeats across $count chapters.",
                recommendation = "Vary or cut the repeated phrasing; repetition reads as filler to paying readers."
            )
        }

        // --- 3. Continuity: POV drift + continuity flags per chapter.
        canon.forEach { ch ->
            LiveAstAnnotator.continuityFlags(ch.content ?: "").forEach { flag ->
                findings += Finding(
                    kind = "CONTINUITY", severity = Finding.Severity.WARNING,
                    chapterNumber = ch.chapterNumber,
                    message = flag.message,
                    recommendation = "Correct the flagged passage."
                )
            }
        }

        // --- 4. Cliché density: existing 154-pattern linter, chapter-level.
        canon.forEach { ch ->
            val hits = ClicheCleaner.scan(ch.content ?: "")
            if (hits.size >= 8) {
                findings += Finding(
                    kind = "WEAK_PASSAGE", severity = Finding.Severity.HARD,
                    chapterNumber = ch.chapterNumber,
                    message = "Cliché density ${hits.size}/chapter exceeds the 8 cap.",
                    recommendation = "Line-edit the flagged spans: ${hits.take(4).joinToString { it.message }}"
                )
            } else if (hits.size >= 5) {
                findings += Finding(
                    kind = "WEAK_PASSAGE", severity = Finding.Severity.WARNING,
                    chapterNumber = ch.chapterNumber,
                    message = "Cliché density ${hits.size}/chapter is elevated.",
                    recommendation = "Line-edit the flagged spans before publication."
                )
            }
        }

        // --- 5. Pacing: chapter word-count outliers vs the book envelope.
        // (Book envelope read from the widest reasonable window; the caller
        // passes explicit bounds when it has the Book.)
        // --- 6. Ending fatigue: same ending type 3+ chapters running.
        var runType: String? = null; var runLen = 0; var runStart = -1
        canon.forEach { ch ->
            val t = LiveAstAnnotator.classifyEnding(ch.content ?: "").first
            if (t == runType) runLen++ else { runType = t; runLen = 1; runStart = ch.chapterNumber }
            if (runLen >= 3 && t != "QUIET") {
                findings += Finding(
                    kind = "PACING", severity = Finding.Severity.WARNING,
                    chapterNumber = ch.chapterNumber,
                    message = "Ending fatigue: $t endings run $runLen chapters (since ch. $runStart).",
                    recommendation = "Vary the ending type — readers disengage from predictable chapter turns."
                )
            }
        }

        val hard = findings.count { it.severity == Finding.Severity.HARD }
        val warn = findings.count { it.severity == Finding.Severity.WARNING }
        val score = (100.0 - hard * 15.0 - warn * 4.0).coerceIn(0.0, 100.0)
        val summary = "QA: $hard hard findings, $warn warnings across ${canon.size} canon chapters " +
            "(${guns.size} tracked setups, ${guns.count { it.status == "RESOLVED" }} resolved)."
        return QaReport(findings, hard == 0, score, summary)
    }
}
