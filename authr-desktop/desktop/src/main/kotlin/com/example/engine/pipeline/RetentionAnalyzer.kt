package com.example.engine.pipeline

import com.example.data.model.Chapter
import com.example.engine.ClicheCleaner
import com.example.engine.LiveAstAnnotator

/**
 * Deterministic reader-retention analyzer (ADR-0018, Priority 4).
 *
 * Measures per-chapter engagement signals with LOCAL code only (ADR-0004 —
 * no LLM in the gate path): opening-hook strength, pacing (scene vs summary
 * ratio), tension vocabulary, curiosity gaps (open questions), chapter-ending
 * effectiveness (leveraging the existing six-type ending classifier), and
 * echo/cliché drag. The composite [RetentionReport.retentionIndex] is an
 * EDITORIAL MEASUREMENT, not a claim of measured reader behavior — same
 * honesty contract as QcScorecard's header disclaimer.
 *
 * Signals are chosen from craft criteria with decades of editorial use
 * (hook span, scene ratio, question cadence, ending pull) — each maps to a
 * number the writer can act on.
 */
object RetentionAnalyzer {

    /** Tension cue lexicon: conflict, threat, urgency, loss-stakes language. */
    private val TENSION_CUES = listOf(
        "blood", "scream", "shout", "slam", "tear", "shatter", "danger", "threat",
        "knife", "blade", "gun", "fire", "burn", "chase", "run", "escape", "trap",
        "betray", "secret", "death", "dead", "kill", "fight", "attack", "storm",
        "anger", "furious", "rage", "terror", "fear", "panic", "warning", "risk",
        "dangerous", "desperate", "collapse", "crash", "break", "hurt", "wound", "bleed"
    )

    /** Curiosity-gap openers: questions and unresolved statements. */
    private val CURIOSITY_CUES = listOf(
        "?", "nobody knew", "no one knew", "why did", "what if", "strange",
        "mystery", "secret", "unknown", "unanswered", "wondered", "puzzle",
        "hidden", "reveal", "discover", "truth", "clue", "riddle", "wrong"
    )

    private val SCENE_MARKERS = listOf(
        "\"", "—", "…", "!" // dialogue, beats, interruptions = scene-time text
    )

    private const val HOOK_SPAN_WORDS = 150

    data class ChapterRetention(
        val chapterNumber: Int,
        val hookScore: Double,
        val pacingScore: Double,
        val tensionScore: Double,
        val curiosityScore: Double,
        val endingScore: Double,
        val retentionIndex: Double,
        val notes: List<String>
    )

    data class RetentionReport(
        val perChapter: List<ChapterRetention>,
        /** Mean of per-chapter indices, 0..100. */
        val overallIndex: Double,
        /** Craft signals the writer should address, ranked by impact. */
        val recommendations: List<String>
    ) {
        companion object {
            fun band(index: Double): String = when {
                index >= 75 -> "STRONG"
                index >= 55 -> "REASONABLE"
                index >= 35 -> "NEEDS WORK"
                else -> "AT RISK"
            }
        }
    }

    fun analyze(chapters: List<Chapter>): RetentionReport {
        val sorted = chapters.sortedBy { it.chapterNumber }
        val lastIndex = sorted.lastOrNull()?.chapterNumber
        val per = sorted.map { analyzeChapter(it, isFinalChapter = it.chapterNumber == lastIndex) }
        val overall = if (per.isEmpty()) 0.0 else per.map { it.retentionIndex }.average()
        val recs = buildRecommendations(per)
        return RetentionReport(per, overall, recs)
    }

    fun analyzeChapter(ch: Chapter, isFinalChapter: Boolean = false): ChapterRetention {
        val text = ch.content ?: ""
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val notes = mutableListOf<String>()

        // --- Opening hook (0..100): density of scene motion + stakes cues
        // in the first HOOK_SPAN_WORDS, penalized by exposition starts.
        val hookWords = words.take(HOOK_SPAN_WORDS).joinToString(" ").lowercase()
        var hook = 40.0
        val hookDialogue = hookWords.count { it == '"' } >= 2
        val hookAction = listOf("ran", "grabbed", "slammed", "screamed", "fell", "exploded", "shouted", "threw", "burst", "snapped")
            .any { hookWords.contains(it) }
        val hookQuestion = hookWords.count { it == '?' } >= 1
        val hookCliche = ClicheCleaner.scan(hookWords).isNotEmpty()
        if (hookDialogue) hook += 15
        if (hookAction) hook += 20
        if (hookQuestion) hook += 10
        if (hookCliche) { hook -= 15; notes.add("Opening leans on cliché patterns — rewrite the first 150 words.") }
        if (words.size > 80 && hookWords.take(40).let { h ->
                h.startsWith("the ") || h.startsWith("it ") || h.startsWith("a ") || h.startsWith("in ")
            }) hook -= 5 // scene-setting opener with no immediate pull
        hook = hook.coerceIn(0.0, 100.0)

        // --- Pacing (0..100): share of scene-time markers (dialogue/beats)
        // vs summary narration; the existing BalanceMeter shares cue source.
        val sceneHits = SCENE_MARKERS.sumOf { m -> text.count { c -> c.toString() == m } }
        val pacingRatio = if (words.isEmpty()) 0.0 else sceneHits.toDouble() / words.size
        val pacing = (pacingRatio * 800.0).coerceIn(0.0, 100.0)
        if (pacing < 40.0) notes.add("Low scene-time ratio (${(pacingRatio * 100).toInt()}%): summary dominates — dramatize key beats in dialogue/action.")

        // --- Tension (0..100): tension-cue density per 1000 words.
        val lower = text.lowercase()
        val tensionHits = TENSION_CUES.sumOf { cue -> occurrences(lower, cue) }
        val per1k = if (words.isEmpty()) 0.0 else tensionHits * 1000.0 / words.size
        val tension = (per1k * 6.0).coerceIn(0.0, 100.0)
        if (tension < 35.0) notes.add("Sparse tension vocabulary (${tensionHits} cues): raise stakes or conflict visibility.")

        // --- Curiosity (0..100): open-question cadence using the existing
        // QuestionLedger detector cues + curiosity lexicon.
        val curiosityHits = CURIOSITY_CUES.sumOf { cue -> occurrences(lower, cue) }
        val curiosityPer1k = if (words.isEmpty()) 0.0 else curiosityHits * 1000.0 / words.size
        val curiosity = (curiosityPer1k * 4.5).coerceIn(0.0, 100.0)
        if (curiosity < 35.0) notes.add("Few curiosity gaps: plant an open question or mystery cue early in the chapter.")

        // --- Ending effectiveness (0..100): reuse the six ending types —
        // high-pull endings score high, QUIET endings score low for serial
        // momentum (retail final chapters exempt via isFinalChapter).
        val (endingType, _) = LiveAstAnnotator.classifyEnding(text)
        val ending = when (endingType) {
            "DANGER" -> 95.0
            "REVERSAL" -> 90.0
            "MYSTERY_DISCOVERY" -> 85.0
            "IRREVERSIBLE_CHOICE" -> 85.0
            "ANTICIPATION" -> 80.0
            "EMOTIONAL_REVELATION" -> 70.0
            else -> 35.0 // QUIET
        }
        if (endingType == "QUIET" && !isFinalChapter) {
            notes.add("Quiet chapter ending: mid-book chapters earn their next click with pull endings (danger/reversal/mystery).")
        }

        val index = (hook * 0.28 + pacing * 0.18 + tension * 0.18 + curiosity * 0.14 + ending * 0.22)
        return ChapterRetention(
            ch.chapterNumber, round1(hook), round1(pacing), round1(tension),
            round1(curiosity), ending, round1(index), notes
        )
    }

    /** Recommendations derived from the per-chapter measurements. */
    private fun buildRecommendations(per: List<ChapterRetention>): List<String> {
        val recs = mutableListOf<String>()
        val weakest = per.sortedBy { it.retentionIndex }.take(3)
        weakest.forEach { c ->
            if (c.retentionIndex < 55) recs.add("Chapter ${c.chapterNumber} retention index ${c.retentionIndex} — prioritize its notes: ${c.notes.joinToString("; ").ifBlank { "hook and ending are the usual levers" }}")
        }
        val weakHooks = per.filter { it.hookScore < 45 }
        if (weakHooks.isNotEmpty()) recs.add("${weakHooks.size} chapter(s) open weakly — rewrite the first 150 words of: ${weakHooks.joinToString { "ch. ${it.chapterNumber}" }}")
        val quietEndings = per.dropLast(1).filter { it.endingScore < 60 }
        if (quietEndings.isNotEmpty()) recs.add("${quietEndings.size} mid-book chapter(s) end quietly — convert to pull endings: ${quietEndings.joinToString { "ch. ${it.chapterNumber}" }}")
        val avgTension = if (per.isEmpty()) 0.0 else per.map { it.tensionScore }.average()
        if (avgTension < 40) recs.add("Manuscript-wide tension averages ${round1(avgTension)} — escalate conflict visibility across acts.")
        val avgCuriosity = if (per.isEmpty()) 0.0 else per.map { it.curiosityScore }.average()
        if (avgCuriosity < 40) recs.add("Curiosity cadence averages ${round1(avgCuriosity)} — open questions earlier, answer on schedule (QuestionLedger windows).")
        return recs
    }

    private fun occurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var idx = 0; var count = 0
        while (true) {
            idx = haystack.indexOf(needle, idx)
            if (idx < 0) break
            count++; idx += needle.length
        }
        return count
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
