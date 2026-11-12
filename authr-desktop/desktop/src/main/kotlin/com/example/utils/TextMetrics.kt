package com.example.utils

/**
 * Honest local text metrics (2026-11-12 audit fix, finding #8).
 *
 * Replaces the fabricated DraftingWorker telemetry that computed
 * "Flesch-Kincaid: Grade ${7 + (chapter.chapterNumber % 3)}" and keyed the
 * emotional/pacing dashboard cards off the chapter number. Every value here
 * is computed from the actual text; anything that cannot be measured returns
 * null and the caller reports "Not analyzed" instead of inventing a number.
 *
 * Pure JVM — no android imports (docs/testing.md engine rule).
 */
object TextMetrics {

    private val sentenceEndRegex = Regex("[.!?]+(?=\\s|$)")

    fun wordCount(text: String): Int =
        text.trim().split("\\s+".toRegex()).count { it.isNotBlank() }

    fun sentenceCount(text: String): Int =
        sentenceEndRegex.findAll(text).count().coerceAtLeast(if (text.isBlank()) 0 else 1)

    /**
     * Syllable estimate for one word: vowel-group counting with the standard
     * silent-'e' adjustment, minimum one syllable. A heuristic, but a real
     * computation over the real text — not a constant.
     */
    fun syllableCount(word: String): Int {
        val w = word.lowercase().filter { it.isLetter() }
        if (w.isEmpty()) return 0
        val groups = Regex("[aeiouy]+").findAll(w).count()
        val adjusted = if (w.length > 2 && w.endsWith("e") &&
            !w.endsWith("le") && groups > 1
        ) groups - 1 else groups
        return adjusted.coerceAtLeast(1)
    }

    /**
     * Flesch-Kincaid grade level: 0.39·(words/sentences) + 11.8·(syllables/words)
     * − 15.59. Returns null when the sample is too small to mean anything
     * (fewer than 40 words or no sentences) — callers must surface
     * "Not analyzed" rather than a number in that case.
     */
    fun fleschKincaidGrade(text: String): Double? {
        val words = wordCount(text)
        if (words < 40) return null
        val sentences = sentenceCount(text)
        if (sentences == 0) return null
        val syllables = text.trim().split("\\s+".toRegex())
            .sumOf { syllableCount(it) }
        val grade = 0.39 * (words.toDouble() / sentences) +
            11.8 * (syllables.toDouble() / words) - 15.59
        return grade.coerceIn(0.0, 20.0)
    }

    /** The dashboard string: a real grade when computable, honest absence otherwise. */
    fun readabilityLabel(text: String): String =
        fleschKincaidGrade(text)
            ?.let { "Flesch-Kincaid: Grade %.1f (computed locally)".format(it) }
            ?: "Readability: Not analyzed (text too short)"
}
