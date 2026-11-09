package com.example

import com.example.engine.AnnotationKind
import com.example.engine.ClicheCleaner
import com.example.engine.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2 exit gate 1 (plan §13, Table 33): "Precision ≥ 90% on the 50-page
 * golden corpus."
 *
 * The corpus is a deterministic 50-page manuscript (~350 words per page)
 * assembled from a clean sentence bank (crafted to fire none of the 154
 * patterns) and one planted instance per pattern — every planted catch sits
 * inside a known span. Precision is measured span-wise: a hit overlapping a
 * planted span is a true positive; any hit landing on clean text is a false
 * positive. The gate requires precision ≥ 0.90; the matrix is built to hit
 * 1.00, and the clean bank deliberately contains near-miss phrasing (e.g.
 * "morning chill" vs "a chill ran down her spine") that would fail a sloppy
 * pattern set.
 */
class ClicheCleanerGoldenCorpusTest {

    // ------------------------------------------------------------------
    // The matrix itself matches plan Table 6 exactly
    // ------------------------------------------------------------------

    @Test
    fun `matrix carries 154 patterns in six classes per plan table 6`() {
        assertEquals(154, ClicheCleaner.matrix.size, "plan Table 6: 154 patterns at launch")
        ClicheCleaner.Classes.planCounts.forEach { (className, expected) ->
            assertEquals(expected, ClicheCleaner.patternsByClass[className]?.size,
                "class \"$className\" must carry exactly $expected patterns")
        }
        // Every pattern is a proper record: class, severity, rewrite, hint.
        assertTrue(ClicheCleaner.matrix.all { it.clicheClass.isNotBlank() && it.rewrite.isNotBlank() && it.hint.isNotBlank() },
            "every pattern names its class, rewrite, and prompt hint")
        assertTrue(ClicheCleaner.matrix.all { it.severity in Severity.entries },
            "every pattern carries a severity")
    }

    // ------------------------------------------------------------------
    // The 50-page golden corpus
    // ------------------------------------------------------------------

    /** Clean sentence bank — none of these may fire any pattern. */
    private val cleanSentences = listOf(
        "The harbor bell counted the hour while Tanis roped the crate to the dock.",
        "Rain fell sideways across the yard, and the tarp over the woodpile would not hold past dawn.",
        "Marta stacked the plates, wiped the counter twice, and left the lantern burning low.",
        "The train arrived four minutes late, its windows fogged against the morning chill.",
        "Beneath the bridge, the current carried leaves and one unopened letter toward the weir.",
        "Herron copied the numbers twice, folded the page, and slid it under the office door.",
        "The dog refused the step stool and took the stairs one at a time instead.",
        "Snow filled the ruts in the road until the cart wheels ran silent.",
        "She left the window open a hand's width, and the smell of wet stone climbed the stairwell.",
        "On the third morning the pigeons returned, unhurried, and took their places along the gutter.",
        "The tenant signed the lease with the same pen his grandmother had used for her citizenship papers.",
        "Nobody touched the last piece of cake until after the funeral, and then nobody wanted it.",
        "Every clock in the house disagreed, and every clock was wrong in a different way.",
        "He patched the inner tube, pumped it half full, and walked the bicycle up the hill.",
        "The committee met for eleven minutes and voted to meet again in the spring.",
        "Sawdust covered the workshop floor, and the hinges of the half-built cabinet lay in a row.",
        "A single gull followed the ferry all the way in, screaming at nothing.",
        "The new clerk alphabetized the drawer twice because the first pass embarrassed her.",
        "Dust settled over the piano keys; no one had touched middle C since October.",
        "In the photograph, my grandfather stands exactly where the pharmacy door now hangs.",
        "The recipe called for patience more than skill, and both were in short supply.",
        "Barge traffic slowed after the lock failed, and the towpath emptied by noon.",
        "She counted the spoons, recounted, and wrote the missing number in the margin.",
        "The roof leaked above the bookcase, so the atlas lived on the kitchen table for a season.",
        "His boots left prints across the wet cement, and the mason let them stay.",
        "The letter arrived unsigned, postmarked from a town none of them could place.",
        "Frost silenced the pump handles, and the cattle gathered at the south fence.",
        "She tuned the radio between two stations until the static turned into music.",
        "The movers left the wardrobe on the landing because the banister would not forgive the angle.",
        "By evening the searchers had walked every field twice and found only the lost hat.",
        "The apprentice filed the gear teeth until the wheel turned without a sound.",
        "Cousins arrived in shifts, sleeping three to a room and arguing about cards.",
        "The librarian let the boy keep the overdue atlas one more week without a word.",
        "Rain shadowed the valley all week, and the river rose to the second plank of the footbridge.",
        "He read the telegram twice, folded it into his coat, and went out to feed the chickens.",
        "The school stove smoked until the janitor opened the flue and muttered something kind.",
        "The map on the wall had three borders penciled in and one border nobody could find.",
        "Thursday brought wind, wind brought rain, and rain brought the smell of the tannery into the kitchen.",
        "She darned the same sock twice in a month and decided the mending basket had won.",
        "Their argument about the fence line outlived both fence lines.",
        "The cat inspected the new sofa, found it wanting, and slept on the windowsill anyway.",
        "Grain prices held steady through the fall, which surprised everyone who sold feed.",
        "Electric light reached the valley late; candles kept their economies for years afterward.",
        "He practiced the eulogy on the drive over and still got her age wrong by a year.",
        "The tides brought in a door, unhinged, and the children made it a table."
    )

    /** One planted catch per pattern, order-aligned with the matrix. */
    private val plantedPerClass: Map<String, List<String>> = mapOf(
        com.example.engine.ClicheCleaner.Classes.DIALOGUE_TAG to listOf(
            "she said breathily", "he replied coldly", "she whispered shakily", "he growled",
            "she hissed", "they snarled", "he spat out", "she rasped", "he croaked", "she purred",
            "he drawled", "she cooed", "he crooned", "she quipped", "he chortled", "she guffawed",
            "he opined", "she chided", "he jeered", "they taunted", "she sneered", "he barked",
            "she bit out", "he choked out", "Enough: growled he", "in a husky voice",
            "her voice sounded strained", "he said with a growl", "she offered sweetly",
            "she said in a silky voice"
        ),
        com.example.engine.ClicheCleaner.Classes.FILTER_WORD to listOf(
            "she felt", "he felt", "they felt", "she could feel", "he noticed", "she realized",
            "he saw that", "she watched as", "they heard", "he listened as", "she sensed",
            "he seemed to", "it seemed that", "there seemed to be", "she appeared to",
            "he began to", "she started to", "he decided to", "she thought that", "he wondered if",
            "she knew that", "he was aware that", "she could see", "he could hear", "she tried to",
            "he was able to", "she managed to", "found herself", "sort of", "kind of",
            "very angry", "incredibly cold"
        ),
        com.example.engine.ClicheCleaner.Classes.PURPLE_PROSE to listOf(
            "a symphony of noise", "symphony of shadows", "the dance of light", "dust motes danced",
            "shadows danced", "a tapestry of", "a kaleidoscope of", "an orchestra of", "a ballet of",
            "heart of hearts", "time stood still", "in the nick of time", "a sea of faces",
            "an ocean of", "a river of stars", "a storm raged within her", "a whirlwind of emotions",
            "a cauldron of", "a labyrinth of", "her world came crashing down",
            "the weight of the world on her shoulders", "the air was thick with",
            "electricity crackled in the air", "silence hung heavy", "light danced across",
            "the night whispered", "as if fate wove", "a ghost of a smile"
        ),
        com.example.engine.ClicheCleaner.Classes.BODY_CLICHE to listOf(
            "her eyes widened", "her eyes went wide", "his breath hitched", "her breath caught",
            "his heart hammered", "her heart skipped a beat", "his knees buckled",
            "her blood ran cold", "his spine tingled", "a chill ran down her spine",
            "her stomach dropped", "his jaw clenched", "her fists clenched", "his hands trembled",
            "her pulse raced", "his throat tightened", "her chest ached", "his cheeks burned",
            "her face drained of color", "his skin crawled", "her hair stood on end",
            "his mouth went dry", "her brow furrowed", "his nostrils flared"
        ),
        com.example.engine.ClicheCleaner.Classes.PLOT_FORMULA to listOf(
            "little did she know", "unbeknownst to", "upon further inspection", "as fate would have it",
            "at the eleventh hour", "when suddenly", "all of a sudden", "without warning",
            "out of nowhere", "against all odds", "in the blink of an eye", "time was running out",
            "the clock was ticking", "fate had other plans", "all hell broke loose",
            "everything changed", "It was a dark and stormy night", "and in that moment she"
        ),
        com.example.engine.ClicheCleaner.Classes.WEB_NOVEL_FILLER to listOf(
            "without further ado", "needless to say", "it goes without saying", "suffice it to say",
            "previously on", "last time on", "to recap:", "in the last chapter", "as you know",
            "as I said before", "as mentioned earlier", "in other words", "as expected,",
            "just as she expected", "just as he thought", "yes, that's right", "Yes, exactly",
            "he was right, of course", "of course, it was", "dear reader", "gentle reader",
            "if you recall"
        )
    )

    /** The planted instances, flattened in matrix order (class order as built). */
    private val plantedInstances: List<String> by lazy {
        ClicheCleaner.Classes.planCounts.keys.flatMap { plantedPerClass[it] ?: error("no planted set for $it") }
    }

    /** Sanity: the planted set aligns 1:1 with the matrix. */
    @Test
    fun `planted set aligns with the matrix one instance per pattern`() {
        assertEquals(ClicheCleaner.matrix.size, plantedInstances.size,
            "one planted catch per pattern, in matrix order")
        // Every planted instance must actually fire its aligned pattern.
        val misses = ClicheCleaner.matrix.withIndex()
            .filter { (index, pat) -> !pat.regex.containsMatchIn(plantedInstances[index]) }
            .map { (index, pat) -> "${pat.clicheClass}#${index}: ${plantedInstances[index]}" }
        assertTrue(misses.isEmpty(), "every planted catch fires its pattern; misses: $misses")
    }

    // ------------------------------------------------------------------
    // Corpus assembly
    // ------------------------------------------------------------------

    private data class Corpus(
        val text: String,
        val plantedSpans: List<Pair<Int, Int>>,
        val wordCount: Int
    )

    private fun buildCorpus(): Corpus {
        val sb = StringBuilder()
        val plantedSpans = mutableListOf<Pair<Int, Int>>()
        val tailCleanParagraphs = 100

        fun cleanSentence(i: Int) = cleanSentences[((i % cleanSentences.size) + cleanSentences.size) % cleanSentences.size]
        fun cleanParagraph(seed: Int): String =
            (0 until 5).joinToString(" ") { k -> cleanSentence(seed * 7 + k * 11 + seed) }

        // The anchored opening-crotch pattern must sit at the very start.
        val opening = plantedInstances.indexOfFirst { it.startsWith("It was a dark") }
        sb.append(plantedInstances[opening]).append(". The harbor bell counted the hour while Tanis roped the crate to the dock.\n\n")
        plantedSpans.add(0 to plantedInstances[opening].length)

        // One planted paragraph between clean paragraphs, until every
        // planted instance is placed; then clean tail paragraphs lift the
        // corpus over the 50-page floor.
        val plantedOrder = plantedInstances.indices.filter { it != opening }
        var n = 0
        var p = 0
        while (n < plantedOrder.size) {
            sb.append(cleanParagraph(p++)).append("\n\n")
            val instance = plantedOrder[n++]
            val context = cleanSentence(p * 3)
            val paragraphStart = sb.length
            sb.append(context).append(" ").append(plantedInstances[instance])
                .append(" ").append(cleanSentence(p * 5 + 2)).append("\n\n")
            val at = sb.indexOf(plantedInstances[instance], paragraphStart)
            plantedSpans.add(at to at + plantedInstances[instance].length)
        }
        repeat(tailCleanParagraphs) { sb.append(cleanParagraph(p++)).append("\n\n") }

        val text = sb.toString()
        val words = text.split(Regex("""\s+""")).count { it.isNotBlank() }
        return Corpus(text, plantedSpans, words)
    }

    // ------------------------------------------------------------------
    // The exit gate
    // ------------------------------------------------------------------

    @Test
    fun `golden corpus reaches fifty pages`() {
        val corpus = buildCorpus()
        val pages = corpus.wordCount / 350.0
        assertTrue(pages >= 50.0, "corpus is ${"%.1f".format(pages)} pages (350 words/page) — needs ≥ 50")
        assertTrue(corpus.plantedSpans.size == plantedInstances.size,
            "every planted instance made it into the corpus")
    }

    @Test
    fun `precision on the golden corpus is at least ninety percent`() {
        val corpus = buildCorpus()
        val hits = ClicheCleaner.scan(corpus.text)
        val truePositives = hits.count { h -> corpus.plantedSpans.any { (s, e) -> h.start < e && s < h.end } }
        val falsePositives = hits.size - truePositives
        val precision = truePositives.toDouble() / hits.size
        assertTrue(precision >= 0.90,
            "precision must be ≥ 0.90 — got ${"%.4f".format(precision)} " +
                "(TP=$truePositives, FP=$falsePositives, hits=${hits.size})")
        // The matrix is engineered for a clean sheet: report the margin.
        println("Golden corpus precision: ${"%.4f".format(precision)} (TP=$truePositives FP=$falsePositives hits=${hits.size}, ${corpus.wordCount} words)")
    }

    @Test
    fun `every planted catch on the golden corpus is recovered`() {
        val corpus = buildCorpus()
        val hits = ClicheCleaner.scan(corpus.text)
        val caught = hits.count { h -> corpus.plantedSpans.any { (s, e) -> h.start < e && s < h.end } }
        val recall = caught.toDouble() / corpus.plantedSpans.size
        assertTrue(recall >= 0.95, "planted catches recovered at ≥ 95% — got ${"%.4f".format(recall)}")
    }

    @Test
    fun `all six classes fire on the golden corpus`() {
        val corpus = buildCorpus()
        val hits = ClicheCleaner.scan(corpus.text)
        val classes = hits.mapNotNull { it.payload["clicheClass"] }.toSet()
        ClicheCleaner.Classes.planCounts.keys.forEach { className ->
            assertTrue(className in classes, "class \"$className\" fires on the corpus")
        }
        assertTrue(hits.all { it.kind == AnnotationKind.CLICHE_HIT }, "hits are CLICHE_HIT nodes")
    }

    // ------------------------------------------------------------------
    // Generation-time suppression — the prompt block
    // ------------------------------------------------------------------

    @Test
    fun `prompt constraints name all six classes with their counts`() {
        val block = ClicheCleaner.promptConstraints()
        ClicheCleaner.Classes.planCounts.forEach { (className, count) ->
            assertTrue(className in block && "$count patterns" in block,
                "constraint block names \"$className ($count patterns)\"")
        }
        assertTrue("FORBIDDEN" in block, "the block is marked as hard constraints")
    }

    @Test
    fun `offender tokens rank the planted vocabulary`() {
        val text = "She said breathily. He growled. She said breathily again, and he growled back."
        val tokens = ClicheCleaner.offenderTokens(text, limit = 6)
        assertTrue("breathily" in tokens && "growled" in tokens,
            "the most frequent offender tokens surface first: $tokens")
    }
}
