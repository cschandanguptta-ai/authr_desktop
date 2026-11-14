package com.example.engine.pipeline

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun

/**
 * Deterministic per-stage quality gates (ADR-0018, requirement §5).
 *
 * A stage's LLM output lands PROPOSED; THIS object decides — with local code
 * only, never an LLM (ADR-0004) — whether the stage output is strong enough
 * to advance. Hard failures block; warnings surface to the writer.
 *
 * Gate criteria are craft-standard, measurable editorial checks:
 *  - MARKET_RESEARCH: findings must be non-trivial (expectations + gaps + comps)
 *  - CONCEPT: premise, differentiator and promise must exist and be specific
 *  - STORY_ARCHITECTURE: chapters must be planned, escalate, and cover the arc
 *  - WORLDBUILDING: the three bibles + narrative rules must be non-empty
 *  - MANUSCRIPT: canon chapters exist and confirm-flow gates already held
 *  - EDITORIAL: passes recorded, no unresolved MAJOR findings
 *  - ENGAGEMENT: retention index threshold, weak-hook cap
 *  - QUALITY_ASSURANCE: ManuscriptQaSuite passed (no HARD findings)
 *  - PUBLICATION_PREP: package complete (titles, blurb, keywords, categories)
 */
object StageGates {

    /** Minimum overall retention index to pass stage 7 (editorial threshold). */
    const val RETENTION_FLOOR = 55.0
    /** Maximum weak hooks (<45) allowed to still pass stage 7. */
    const val WEAK_HOOK_CAP = 2

    // ------------------------------------------------------------------
    // LLM-stage gates (evaluate the PROPOSED stage output the writer sees)

    fun marketResearch(findings: EditorialMemoryStore.MarketFindings?): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        findings ?: return GateVerdict.blocked(PipelineStage.MARKET_RESEARCH, listOf("No market findings recorded."), 0.0)
        if (findings.genreExpectations.size < 3) hard += "Fewer than 3 genre expectations recorded — the analysis is too thin to position against."
        if (findings.marketGaps.isEmpty()) hard += "No market gaps identified — a concept cannot differentiate without them."
        if (findings.comparableTitles.isEmpty()) warn += "No comparable titles listed; positioning claims will be unverifiable."
        if (findings.summary.isBlank()) hard += "Executive summary is empty."
        val score = completeness(
            findings.genreExpectations.size to 6, findings.marketGaps.size to 3,
            findings.comparableTitles.size to 4, findings.commercialOpportunities.size to 3,
            (if (findings.summary.isNotBlank()) 1 else 0) to 1
        )
        return verdict(PipelineStage.MARKET_RESEARCH, hard, warn, score)
    }

    fun concept(p: EditorialMemoryStore.Positioning?): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        p ?: return GateVerdict.blocked(PipelineStage.CONCEPT, listOf("No positioning recorded."), 0.0)
        if (p.premise.isBlank()) hard += "Premise is empty."
        if (p.premise.length < 40) warn += "Premise is very short — a sellable premise states protagonist, stakes and twist."
        if (p.differentiator.isBlank()) hard += "No differentiator — the concept does not answer 'why this book over the comps'."
        if (p.sellingProposition.isBlank()) warn += "Selling proposition missing — write the one-sentence friend recommendation."
        if (p.promiseToReader.isBlank()) warn += "Promise to reader missing — the emotional contract drives completion."
        if (p.avoidList.isEmpty()) warn += "No avoid-list — without named clichés to avoid, drafting will drift into them."
        val score = completeness(
            (if (p.premise.length >= 40) 2 else if (p.premise.isNotBlank()) 1 else 0) to 2,
            (if (p.differentiator.isNotBlank()) 1 else 0) to 1,
            (if (p.sellingProposition.isNotBlank()) 1 else 0) to 1,
            (if (p.promiseToReader.isNotBlank()) 1 else 0) to 1,
            (if (p.avoidList.isNotEmpty()) 1 else 0) to 1
        )
        return verdict(PipelineStage.CONCEPT, hard, warn, score)
    }

    fun storyArchitecture(book: Book, chapters: List<Chapter>): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (book.outline.isBlank()) return GateVerdict.blocked(PipelineStage.STORY_ARCHITECTURE, listOf("Outline is empty."), 0.0)
        val canon = chapters.filter { it.writerConfirmed && it.draftStatus == "CONFIRMED" }
        // Escalation check: chapter summaries must not all be near-identical.
        val summaries = canon.map { (it.summary ?: "").trim() }.filter { it.isNotEmpty() }
        if (summaries.size >= 3) {
            val distinct = summaries.map { it.lowercase().split(Regex("\\W+")).filter { w -> w.length > 4 }.toSet() }
            val jaccards = distinct.zipWithNext { a, b -> a.intersect(b).size.toDouble() / (a.union(b).size.toDouble()) }
            val maxSim = jaccards.maxOrNull() ?: 0.0
            if (maxSim > 0.85) warn += "Two adjacent chapter plans are near-duplicates (similarity ${"%.2f".format(maxSim)}) — chapters must escalate, not repeat."
        }
        val score = completeness(
            (if (book.outline.length > 200) 2 else 1) to 2,
            (if (book.narrativeStructure.isNotBlank()) 1 else 0) to 1,
            (if (canon.isNotEmpty() || book.targetChapters > 0) 1 else 0) to 1
        )
        return verdict(PipelineStage.STORY_ARCHITECTURE, hard, warn, score)
    }

    fun worldbuilding(book: Book): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (book.storyBible.isBlank()) hard += "Story bible is empty."
        if (book.characterBible.isBlank()) hard += "Character bible is empty."
        if (book.worldBible.isBlank()) hard += "World bible is empty."
        if (book.characterBible.length < 200) warn += "Character bible is thin — voice drift follows from under-specified characters."
        val score = completeness(
            (if (book.storyBible.length > 300) 2 else if (book.storyBible.isNotBlank()) 1 else 0) to 2,
            (if (book.characterBible.length > 300) 2 else if (book.characterBible.isNotBlank()) 1 else 0) to 2,
            (if (book.worldBible.length > 300) 2 else if (book.worldBible.isNotBlank()) 1 else 0) to 2
        )
        return verdict(PipelineStage.WORLDBUILDING, hard, warn, score)
    }

    fun manuscript(canonChapters: List<Chapter>, book: Book): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (canonChapters.isEmpty()) hard += "No confirmed chapters — the manuscript stage cannot advance with an empty canon."
        val shortChapters = canonChapters.filter { wordCount(it.content) < book.wordMin * 0.6 }
        if (shortChapters.isNotEmpty()) warn += "${shortChapters.size} chapter(s) below 60% of the chapter envelope (${book.wordMin}-${book.wordMax} words): ${shortChapters.joinToString { "ch. ${it.chapterNumber}" }}"
        val emptyContent = canonChapters.filter { (it.content ?: "").isBlank() }
        if (emptyContent.isNotEmpty()) hard += "Confirmed chapter(s) with empty content: ${emptyContent.joinToString { "ch. ${it.chapterNumber}" }}"
        val score = if (canonChapters.isEmpty()) 0.0 else completeness(
            canonChapters.size to maxOf(1, book.targetChapters.takeIf { it > 0 } ?: canonChapters.size),
            (canonChapters.size - shortChapters.size) to canonChapters.size
        )
        return verdict(PipelineStage.MANUSCRIPT, hard, warn, score)
    }

    fun editorial(majorFindingsOpen: Int, passesRecorded: Int): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (passesRecorded == 0) hard += "No editorial pass recorded — stage 6 requires at least the developmental pass."
        if (passesRecorded in 1..2) warn += "Only $passesRecorded editorial pass(es) recorded; the full ladder is developmental → structural → line → copyedit → proofread."
        if (majorFindingsOpen > 0) hard += "$majorFindingsOpen unresolved MAJOR editorial finding(s) — resolve or accept-with-note before advancing."
        val score = completeness(passesRecorded to 5, (5 - majorFindingsOpen.coerceAtMost(5)) to 5)
        return verdict(PipelineStage.EDITORIAL, hard, warn, score)
    }

    fun engagement(report: RetentionAnalyzer.RetentionReport): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (report.perChapter.isEmpty()) hard += "No chapters to analyze."
        if (report.overallIndex < RETENTION_FLOOR) hard += "Overall retention index ${"%.1f".format(report.overallIndex)} is below the editorial floor $RETENTION_FLOOR."
        val weakHooks = report.perChapter.filter { it.hookScore < 45 }
        if (weakHooks.size > WEAK_HOOK_CAP) hard += "${weakHooks.size} chapters open weakly (cap $WEAK_HOOK_CAP): ${weakHooks.joinToString { "ch. ${it.chapterNumber}" }}"
        else if (weakHooks.isNotEmpty()) warn += "Weak opening hook(s): ${weakHooks.joinToString { "ch. ${it.chapterNumber}" }}"
        report.recommendations.take(3).forEach { warn += it }
        return verdict(PipelineStage.ENGAGEMENT, hard, warn, report.overallIndex)
    }

    fun qualityAssurance(qa: ManuscriptQaSuite.QaReport): GateVerdict {
        val hard = qa.findings.filter { it.severity == Finding.Severity.HARD }.map { "[${it.kind}] ${it.message}" }.toMutableList()
        val warn = qa.findings.filter { it.severity == Finding.Severity.WARNING }.map { "[${it.kind}] ${it.message}" }.toMutableList()
        return verdict(PipelineStage.QUALITY_ASSURANCE, hard, warn, qa.score)
    }

    fun publicationPrep(titles: Int, blurbWords: Int, keywords: Int, categories: Int): GateVerdict {
        val hard = mutableListOf<String>()
        val warn = mutableListOf<String>()
        if (titles == 0) hard += "No title candidates."
        if (blurbWords < 80) hard += "Retail description too short (${blurbWords} words; 150-250 expected)."
        else if (blurbWords !in 150..250) warn += "Retail description is ${blurbWords} words (150-250 is the store convention)."
        if (keywords < 7) hard += "Only $keywords/7 store keywords."
        if (categories < 1) hard += "No store category selected."
        else if (categories < 3) warn += "Only $categories/3 categories — use every slot for discoverability."
        val score = completeness(
            titles to 3, (blurbWords.coerceAtMost(250)) to 250,
            keywords to 7, categories to 3
        )
        return verdict(PipelineStage.PUBLICATION_PREP, hard, warn, score)
    }

    // ------------------------------------------------------------------

    private fun verdict(stage: PipelineStage, hard: List<String>, warnings: List<String>, score: Double): GateVerdict =
        if (hard.isEmpty()) GateVerdict(stage, true, emptyList(), warnings, warnings.map { "Address: $it" }, score, System.currentTimeMillis())
        else GateVerdict(stage, false, hard, warnings, emptyList(), score, System.currentTimeMillis())

    private fun completeness(vararg parts: Pair<Int, Int>): Double =
        (parts.sumOf { (num, den) -> num.toDouble().coerceAtMost(den.toDouble()) } / parts.sumOf { (_, den) -> den.toDouble().coerceAtLeast(1.0) } * 100.0)

    fun wordCount(text: String?): Int = text?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
}
