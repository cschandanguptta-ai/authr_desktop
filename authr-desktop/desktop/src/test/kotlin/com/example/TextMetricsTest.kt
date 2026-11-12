package com.example

import com.example.utils.TextMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards for the 2026-11-12 audit fix #8: telemetry is computed from the
 * actual text or honestly absent — never keyed off the chapter number.
 */
class TextMetricsTest {

    @Test
    fun `counts words and sentences from the real text`() {
        assertEquals(8, TextMetrics.wordCount("one two three four five six seven eight"))
        assertEquals(3, TextMetrics.sentenceCount("First one. Second one! Third one?"))
        assertEquals(0, TextMetrics.wordCount("   "))
    }

    @Test
    fun `syllable estimation handles silent e and vowel groups`() {
        assertEquals(1, TextMetrics.syllableCount("the"))
        assertEquals(2, TextMetrics.syllableCount("table")) // ta-ble: 'le' ending keeps both
        assertEquals(3, TextMetrics.syllableCount("beautiful"))
        assertEquals(1, TextMetrics.syllableCount("x"))
    }

    @Test
    fun `a simple passage grades low and a complex one grades higher`() {
        val simple = ("The dog ran fast. The dog saw a cat. The cat ran too. " +
            "The dog was big. The sky was blue. The day was nice. " +
            "We went home. We had fun. The end came fast. The dog slept. " +
            "The cat slept. The sun went down. It was dark. We all slept.").repeat(3)
        val complex = ("The extraordinarily peripatetic protagonist circumnavigated the metropolis, " +
            "scrutinizing the indiscriminately heterogeneous architecture with an almost " +
            "obsessively methodological inquisitiveness, interrogating each edifice's " +
            "provenance with unrelenting pedantry and incomprehensible perseverance, " +
            "simultaneously evaluating its characteristic peculiarities and its " +
            "incontrovertible significance to the municipality's heterogeneous " +
            "constituency, notwithstanding the overwhelmingly unfavorable circumstances.").repeat(3)
        val simpleGrade = TextMetrics.fleschKincaidGrade(simple)
        val complexGrade = TextMetrics.fleschKincaidGrade(complex)
        assertTrue(simpleGrade != null && complexGrade != null, "both 40+ word samples grade")
        assertTrue(complexGrade!! > simpleGrade!!, "complex prose must grade higher than simple prose ($complexGrade vs $simpleGrade)")
        assertTrue(simpleGrade < 8.0, "plain short sentences grade low (got $simpleGrade)")
    }

    @Test
    fun `a sample too small to mean anything returns null - never an invented number`() {
        assertNull(TextMetrics.fleschKincaidGrade("Too short."))
        assertNull(TextMetrics.fleschKincaidGrade(""))
    }

    @Test
    fun `the dashboard label reports honesty instead of fabrication`() {
        assertTrue(TextMetrics.readabilityLabel("Too short.").contains("Not analyzed"),
            "short text reports Not analyzed — the old code fabricated 'Grade 7..9' from chapterNumber % 3")
        val longText = ("The dog ran fast. The dog saw a cat. The cat ran too. " +
            "The dog was big. The sky was blue. The day was nice. " +
            "We went home. We had fun. The end came fast. The dog slept. " +
            "The cat slept. The sun went down. It was dark. We all slept.").repeat(3)
        assertTrue(TextMetrics.readabilityLabel(longText).startsWith("Flesch-Kincaid: Grade "))
    }
}
