package com.example.engine.rag

import com.example.data.model.ResearchFact
import com.example.data.model.ResearchSource

/**
 * CITATION INTEGRITY CHECKER (v3.0 port S3, spec Invariant 17 =
 * desktop ADR-0022): for NONFICTION_AUTHORITY formats, every cited
 * research fact must be verified before export.
 *
 * Gate semantics (spec gate matrix): `nonfiction_citations` — threshold
 * "All citations verified for NONFICTION_AUTHORITY", 0 unverified,
 * severity BLOCKING (export). The checker reports the finding set; the
 * export pipeline turns a non-empty blocking set into a hard stop.
 *
 * A citation is a research fact whose chapterRefs include the chapter
 * being checked. Verification state lives on the fact (verified flag +
 * confidence + reliability on the source). This checker is pure Kotlin
 * over the S0 Room entities — no provider calls.
 */
class CitationIntegrityChecker {

    /** One citation finding: a fact cited but not (fully) verified. */
    data class CitationFinding(
        val factId: Int,
        val sourceId: Int,
        val sourceTitle: String,
        val factText: String,
        val chapterNumber: Int,
        val reason: String,
        val blocking: Boolean
    )

    data class CitationReport(
        val findings: List<CitationFinding>,
        /** True when zero blocking findings (the gate passes). */
        val passed: Boolean,
        val verifiedCount: Int,
        val unverifiedCount: Int
    )

    /**
     * Check the citations of [chapterNumber] against the research facts.
     * [chapterRefs] on each fact is a JSON array of chapter numbers
     * (S0 schema); a fact with a null/blank chapterRefs is uncited and
     * not export-blocking.
     *
     * Blocking findings: facts cited by the chapter that are unverified,
     * or whose source is DISPUTED. Warnings: low-confidence (< 0.5)
     * verified facts and unverified-but-uncited facts are out of scope
     * entirely.
     */
    fun check(
        chapterNumber: Int,
        facts: List<ResearchFact>,
        sources: List<ResearchSource>
    ): CitationReport {
        val sourcesById = sources.associateBy { it.id }
        val findings = mutableListOf<CitationFinding>()
        var verified = 0
        var unverified = 0

        facts.forEach { fact ->
            val refs = parseChapterRefs(fact.chapterRefs)
            if (chapterNumber in refs) {
                val source = sourcesById[fact.sourceId]
                if (fact.verified && source?.reliability != ResearchSource.RELIABILITY_DISPUTED) {
                    verified++
                    if (fact.confidence < 0.5) {
                        findings += CitationFinding(
                            factId = fact.id, sourceId = fact.sourceId,
                            sourceTitle = source?.title ?: "source #${fact.sourceId}",
                            factText = fact.factText, chapterNumber = chapterNumber,
                            reason = "Verified but confidence ${"%.2f".format(fact.confidence)} < 0.5 — consider re-checking against the source.",
                            blocking = false
                        )
                    }
                } else {
                    unverified++
                    val reason = when {
                        source?.reliability == ResearchSource.RELIABILITY_DISPUTED ->
                            "Source is DISPUTED — citation cannot ride an export."
                        !fact.verified ->
                            "Fact not verified against its source."
                        else -> "Fact verified but source missing from the vault."
                    }
                    findings += CitationFinding(
                        factId = fact.id, sourceId = fact.sourceId,
                        sourceTitle = source?.title ?: "source #${fact.sourceId}",
                        factText = fact.factText, chapterNumber = chapterNumber,
                        reason = reason, blocking = true
                    )
                }
            }
        }
        return CitationReport(
            findings = findings,
            passed = findings.none { it.blocking },
            verifiedCount = verified,
            unverifiedCount = unverified
        )
    }

    /** Whole-book pass: blocking findings across every cited chapter. */
    fun checkBook(
        facts: List<ResearchFact>,
        sources: List<ResearchSource>
    ): CitationReport {
        val allChapters = facts.flatMap { parseChapterRefs(it.chapterRefs) }.distinct().sorted()
        val perChapter = allChapters.map { check(it, facts, sources) }
        return CitationReport(
            findings = perChapter.flatMap { it.findings },
            passed = perChapter.all { it.passed },
            verifiedCount = perChapter.sumOf { it.verifiedCount },
            unverifiedCount = perChapter.sumOf { it.unverifiedCount }
        )
    }

    /** chapterRefs is a JSON array ('[1,4,7]') or blank. */
    fun parseChapterRefs(refs: String?): List<Int> {
        if (refs.isNullOrBlank()) return emptyList()
        return Regex("-?\\d+").findAll(refs).map { it.value.toInt() }.toList()
    }
}
