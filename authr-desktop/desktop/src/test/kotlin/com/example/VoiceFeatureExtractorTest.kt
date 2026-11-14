package com.example

import com.example.data.model.VoiceEvolutionEntry
import com.example.data.model.VoiceDriftLog
import com.example.engine.serialization.GoldenSerial
import com.example.engine.voice.VoiceDriftTracker
import com.example.engine.voice.VoiceFeatureExtractor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.0 port S0 spike validation (roadmap task 1.1 acceptance, pulled
 * forward into S0): the 24-feature extractor runs on every golden-serial
 * chapter, values are sane for the 1,000-2,000-word envelope, per-chapter
 * wall time stays under 50 ms, and the drift math behaves on planted
 * corpora (stable text converges, monotone shift crosses the spec
 * thresholds, voice evolution suppresses the alert).
 */
class VoiceFeatureExtractorTest {

    private fun goldenChapters(): List<String> =
        (1..GoldenSerial.CHAPTER_COUNT).map { n ->
            GoldenSerial.chapters(bookId = 1).first { it.chapterNumber == n }.content
        }

    @Test
    fun `all 24 features are computed and sane on every golden chapter`() {
        val texts = goldenChapters()
        assertEquals(GoldenSerial.CHAPTER_COUNT, texts.size)
        for ((n, text) in texts.withIndex()) {
            val f = VoiceFeatureExtractor.extract(text)
            val chapterNo = n + 1
            // word envelope (plan): 1,000-2,000 words per chapter
            assertTrue(f.wordCount in 1000..2000, "ch$chapterNo wordCount=${f.wordCount}")
            assertTrue(f.sentenceCount >= 10, "ch$chapterNo sentences=${f.sentenceCount}")
            // structural sanity
            assertTrue(f.sentenceLenMean in 4.0..40.0, "ch$chapterNo lenMean=${f.sentenceLenMean}")
            assertTrue(f.sentenceLenSd >= 0.0, "ch$chapterNo sd=${f.sentenceLenSd}")
            assertTrue(f.sentenceOpenerVariety > 0.0, "ch$chapterNo opener entropy")
            assertTrue(f.consecutiveSameOpener >= 1, "ch$chapterNo consecutive")
            assertTrue(f.avgWordLen in 3.0..8.0, "ch$chapterNo avgWordLen=${f.avgWordLen}")
            // rates are non-negative
            for ((k, v) in f.toMap()) {
                assertTrue(v >= 0.0, "ch$chapterNo feature $k = $v < 0")
            }
            // hapax ratio strictly inside (0,1) on 1k+ words of prose
            assertTrue(f.hapaxRatio > 0.0 && f.hapaxRatio < 1.0, "ch$chapterNo hapax=${f.hapaxRatio}")
        }
    }

    @Test
    fun `extraction stays under 50 ms per chapter`() {
        // warmup (JIT + regex compilation) then measure a full pass
        val texts = goldenChapters()
        repeat(2) { texts.forEach { VoiceFeatureExtractor.extract(it) } }
        var totalMs = 0.0
        for (text in texts) {
            val t0 = System.nanoTime()
            VoiceFeatureExtractor.extract(text)
            totalMs += (System.nanoTime() - t0) / 1_000_000.0
        }
        val perChapter = totalMs / texts.size
        println("S0 SPIKE TIMING: ${texts.size} chapters, mean ${"%.1f".format(perChapter)} ms/chapter, total ${"%.0f".format(totalMs)} ms")
        assertTrue(perChapter < 50.0, "mean per-chapter extraction ${perChapter}ms exceeds the 50ms budget")
    }

    @Test
    fun `feature vectors are deterministic`() {
        val text = goldenChapters().first()
        assertEquals(
            VoiceFeatureExtractor.extract(text).toMap(),
            VoiceFeatureExtractor.extract(text).toMap(),
            "same input must yield the identical vector (drift compares like with like)"
        )
    }

    @Test
    fun `source hash is stable and content-sensitive`() {
        val a = goldenChapters()[0]
        assertEquals(VoiceFeatureExtractor.sourceHash(a), VoiceFeatureExtractor.sourceHash(a))
        assertTrue(
            VoiceFeatureExtractor.sourceHash(a) != VoiceFeatureExtractor.sourceHash(a + " edited"),
            "an edit must change the hash (summary-cache invalidation)"
        )
    }

    @Test
    fun `stable text keeps drift near zero across a book`() {
        val texts = goldenChapters()
        val baseline = VoiceFeatureExtractor.extract(texts[0]).toMap()
        var cumulative = 0.0
        for (text in texts) {
            val current = VoiceFeatureExtractor.extract(text).toMap()
            val result = VoiceDriftTracker.computeDrift(current, baseline, cumulative)
            cumulative = result.cumulativeDrift
        }
        // The golden serial is one voice throughout; the planted drift this
        // corpus exercises is narrative (beats/endings), not stylistic.
        assertTrue(
            cumulative < VoiceDriftLog.CUMULATIVE_FLAG,
            "golden serial cumulative drift ${cumulative} must stay under ${VoiceDriftLog.CUMULATIVE_FLAG}"
        )
    }

    @Test
    fun `monotone stylistic shift crosses the cumulative threshold`() {
        // Plant a voice collapse: same content, sentences progressively
        // flattened into uniform short declaratives.
        val baselineText = (1..60).joinToString(" ") { n ->
            if (n % 3 == 0) "The river swallowed the town whole."
            else "Kira watched the dike with a cold, patient attention that her mother had never taught her."
        }
        val baseline = VoiceFeatureExtractor.extract(baselineText).toMap()
        // Drifted text: uniform telegraphic sentences, no subordination.
        val driftedText = (1..200).joinToString(" ") { "It was dark. She ran fast." }
        val drifted = VoiceFeatureExtractor.extract(driftedText).toMap()

        val result = VoiceDriftTracker.computeDrift(drifted, baseline, previousCumulative = 0.0)
        assertTrue(
            result.compositeDrift > VoiceDriftLog.COMPOSITE_FLAG,
            "planted collapse composite=${result.compositeDrift} must exceed ${VoiceDriftLog.COMPOSITE_FLAG}"
        )
        assertTrue(result.flagged, "the collapse must flag without an evolution active")
    }

    @Test
    fun `voice evolution suppresses the alert but records the movement`() {
        val baseline = mapOf(
            "sentence_len_mean" to 18.0, "sentence_len_sd" to 6.0,
            "ttr_windowed" to 0.45, "avg_word_len" to 5.0,
            "contraction_ratio" to 0.3, "dialogue_ratio" to 25.0,
            "filter_word_density" to 1.0, "adverb_per1k" to 4.0,
            "passive_per100" to 5.0, "sentence_opener_variety" to 3.0,
            "figurative_per1k" to 2.0
        )
        val drifted = mapOf(
            "sentence_len_mean" to 9.0, "sentence_len_sd" to 12.0,
            "ttr_windowed" to 0.30, "avg_word_len" to 4.4,
            "contraction_ratio" to 0.1, "dialogue_ratio" to 8.0,
            "filter_word_density" to 2.5, "adverb_per1k" to 9.0,
            "passive_per100" to 14.0, "sentence_opener_variety" to 2.0,
            "figurative_per1k" to 6.0
        )
        val evolution = VoiceEvolutionEntry(
            personaId = "AuthorMain",
            chapterStart = 5,
            reason = "Protagonist descends into grief arc"
        )
        assertTrue(evolution.covers(7), "chapter 7 is inside the range")
        assertTrue(!evolution.covers(4), "chapter 4 is before the range")

        val plain = VoiceDriftTracker.computeDrift(drifted, baseline, 0.0, evolutionActive = false)
        val covered = VoiceDriftTracker.computeDrift(drifted, baseline, 0.0, evolutionActive = true)
        assertTrue(plain.flagged, "without evolution the shift flags")
        assertTrue(!covered.flagged, "an active evolution suppresses the alert")
        assertTrue(covered.evolutionApplied, "the suppressed movement is still recorded")
        assertEquals(plain.compositeDrift, covered.compositeDrift, "the measurement itself is identical")
    }

    @Test
    fun `rolling baseline averages confirmed history`() {
        val texts = goldenChapters().take(3)
        val history = texts.map { VoiceFeatureExtractor.extract(it).toMap() }
        val baseline = VoiceDriftTracker.rollingBaseline(history)
        for (feature in baseline.keys) {
            val mean = history.map { it[feature]!! }.average()
            assertEquals(mean, baseline[feature]!!, 0.001, "baseline of $feature is the history mean")
        }
    }

    @Test
    fun `unit probes for the tricky primitives`() {
        // sentence split keeps the opener word clean
        val s = VoiceFeatureExtractor.splitSentences("She ran. \"It's here!\" he said.")
        assertTrue(s.size >= 2, "two sentences, got ${s.size}")
        // passive heuristic
        val passives = VoiceDriftTracker.run {
            VoiceFeatureExtractor.countPassives(listOf("The deed was sealed by her father.", "She was running late."))
        }
        assertEquals(1, passives, "one passive (was sealed), 'was running' is progressive")
        // dialogue words
        val dl = VoiceFeatureExtractor.countDialogueWords("“I am here,” she said, and left.")
        assertTrue(dl in 2..4, "dialogue words counted, got $dl")
        // windowed TTR degrades to whole-text TTR on short input
        val short = listOf("a", "b", "a", "c")
        assertEquals(0.75, VoiceFeatureExtractor.windowedTtr(short), 0.0001)
    }
}
