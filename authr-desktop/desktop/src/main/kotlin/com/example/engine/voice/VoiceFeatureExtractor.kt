package com.example.engine.voice

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * VOICE FEATURE EXTRACTOR (v3.0 port S0 / spec engine-enhancements §1.2,
 * extends ADR-0004's deterministic-metrics doctrine): 20+ prose features
 * computed per chapter with pure JVM regex + token statistics — no spaCy,
 * no LLM, no network. Deterministic by construction so the drift tracker
 * (ADR-0019) compares like with like.
 *
 * The spec's spaCy-derived features (clause_depth_avg via dependency
 * parse, narrative_distance, show_tell_ratio, pov/tense classification)
 * are intentionally deferred to LLM-assisted passes (S1+): this extractor
 * delivers the deterministic 24-feature core the drift math needs.
 *
 * All per-1k rates are normalized by word count so chapters of different
 * lengths compare directly.
 */
object VoiceFeatureExtractor {

    /** Sentinel for "not measurable on this text" (e.g. no sentences). */
    const val UNDEFINED = -1.0

    // ------------------------------------------------------------------
    // Word lists (compact, deterministic; curated from the spec tables)
    // ------------------------------------------------------------------

    /** Class-2 filter words (spec §1.1: "she felt", "he saw", "he wondered"...). */
    private val FILTER_WORDS = listOf(
        "felt", "saw", "heard", "noticed", "realized", "wondered", "thought",
        "knew", "decided", "watched", "observed", "seemed", "looked like"
    )

    /** -ly adverb detection is morphological; this stoplist avoids false "-ly" nouns. */
    private val LY_NOT_ADVERBS = setOf(
        "only", "family", "reply", "supply", "july", "early", "holy", "ugly",
        "italy", "anomaly", "assembly", "alley", "valley", "belly", "jelly",
        "silly", "bully", "fully", "hilly", "holly", "belly", "petrol", "illy"
    )

    /** Passive-voice auxiliaries (be-verb + past participle heuristic). */
    private val BE_FORMS = setOf("is", "was", "are", "were", "be", "been", "being", "am")

    /** Common past-participle irregulars that precede "by" in true passives. */
    private val IRREGULAR_PAST_PARTICIPLES = setOf(
        "given", "taken", "seen", "done", "made", "known", "written", "drawn",
        "shown", "born", "broken", "chosen", "driven", "eaten", "fallen",
        "forgotten", "gotten", "hidden", "proven", "risen", "sewn", "spoken",
        "stolen", "struck", "sung", "sunk", "torn", "worn", "built", "held",
        "kept", "left", "meant", "met", "paid", "read", "said", "sat", "sent",
        "set", "spent", "told", "brought", "bought", "caught", "taught", "sent"
    )

    /** Sensory verbs/nouns per sense (sight, sound, touch, taste, smell). */
    private val SENSORY_WORDS: Map<String, Set<String>> = mapOf(
        "sight" to setOf(
            "saw", "see", "seen", "looked", "gaze", "glimpse", "glance", "viewed",
            "watched", "stared", "noticed", "visible", "darkness", "light", "shadow",
            "color", "colour", "glow", "shine", "blink", "peered", "observed"
        ),
        "sound" to setOf(
            "heard", "hear", "sound", "noise", "voice", "echo", "whisper", "shout",
            "cry", "scream", "murmur", "rumble", "creak", "silence", "silent",
            "music", "tone", "clatter", "thud", "howl", "hummed", "rattled"
        ),
        "touch" to setOf(
            "felt", "feel", "touch", "cold", "warm", "hot", "rough", "smooth",
            "texture", "grasped", "gripped", "pressed", "brushed", "tingle",
            "ache", "stung", "damp", "dry", "hard", "soft"
        ),
        "taste" to setOf(
            "taste", "tasted", "flavor", "flavour", "sweet", "bitter", "sour",
            "salty", "savory", "savoury", "swallowed", "bit", "bite", "chewed",
            "spicy", "delicious"
        ),
        "smell" to setOf(
            "smell", "smelled", "smelt", "scent", "aroma", "stench", "odor",
            "odour", "reek", "perfume", "fragrance", "whiff", "pungent", "acrid"
        )
    )

    /** Figurative-language markers: simile cues + common metaphor verbs. */
    private val SIMILE_CUES = listOf("like a", "like the", "as if", "as though", "as ___ as")
    private val METAPHOR_VERBS = setOf(
        "burned", "drowned", "shattered", "crumbled", "melted", "froze",
        "flooded", "soared", "sank", "exploded", "swallowed", "devoured"
    )

    private val CONTRACTION_RE = Regex("\\b\\w+['’](s|t|re|ve|ll|d|m)\\b")
    private val WORD_RE = Regex("[A-Za-z][A-Za-z'’-]*")
    private val SENTENCE_SPLIT_RE = Regex("[.!?]+[\"”'’)]*\\s+")
    private val ADVERB_RE = Regex("\\b\\w+ly\\b")
    private val EM_DASH = "—"
    private val ELLIPSIS = "…"
    private val DOT_DOT_DOT = "..."

    // ------------------------------------------------------------------
    // Public feature vector
    // ------------------------------------------------------------------

    /**
     * The 24 deterministic voice features. Field names match the spec's
     * feature table (engine-enhancements §1.2) so persona targets and
     * drift weights align across systems.
     */
    data class Features(
        // Structural
        val sentenceLenMean: Double,
        val sentenceLenSd: Double,          // burstiness (spec σ_S ≥ 4.2)
        val paragraphLenMean: Double,
        val sentenceOpenerVariety: Double,  // Shannon entropy of first words (bits)
        val consecutiveSameOpener: Int,
        val punctEmDashPer1k: Double,
        val punctSemicolonPer1k: Double,
        val punctEllipsisPer1k: Double,
        val contractionRatio: Double,       // contractions / sentences
        // Lexical
        val ttrWindowed: Double,            // type-token ratio, 1k sliding window mean
        val hapaxRatio: Double,
        val avgWordLen: Double,
        val adverbPer1k: Double,
        val passivePer100Sentences: Double,
        val filterWordDensity: Double,      // per 1k words (spec < 3.0)
        val figurativePer1k: Double,
        val sensorySightPer1k: Double,
        val sensorySoundPer1k: Double,
        val sensoryTouchPer1k: Double,
        val sensoryTastePer1k: Double,
        val sensorySmellPer1k: Double,
        // Composition
        val dialogueRatio: Double,          // % words inside quotes
        val wordCount: Int,
        val sentenceCount: Int
    ) {
        /** Stable feature map (name -> value) for drift comparison + JSON persistence. */
        fun toMap(): Map<String, Double> = linkedMapOf(
            "sentence_len_mean" to sentenceLenMean,
            "sentence_len_sd" to sentenceLenSd,
            "paragraph_len_mean" to paragraphLenMean,
            "sentence_opener_variety" to sentenceOpenerVariety,
            "consecutive_same_opener" to consecutiveSameOpener.toDouble(),
            "punct_em_dash_per1k" to punctEmDashPer1k,
            "punct_semicolon_per1k" to punctSemicolonPer1k,
            "punct_ellipsis_per1k" to punctEllipsisPer1k,
            "contraction_ratio" to contractionRatio,
            "ttr_windowed" to ttrWindowed,
            "hapax_ratio" to hapaxRatio,
            "avg_word_len" to avgWordLen,
            "adverb_per1k" to adverbPer1k,
            "passive_per100" to passivePer100Sentences,
            "filter_word_density" to filterWordDensity,
            "figurative_per1k" to figurativePer1k,
            "sensory_sight_per1k" to sensorySightPer1k,
            "sensory_sound_per1k" to sensorySoundPer1k,
            "sensory_touch_per1k" to sensoryTouchPer1k,
            "sensory_taste_per1k" to sensoryTastePer1k,
            "sensory_smell_per1k" to sensorySmellPer1k,
            "dialogue_ratio" to dialogueRatio
        )
    }

    /** Extract the full deterministic feature vector from chapter text. */
    fun extract(text: String): Features {
        val paragraphs = text.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val sentences = splitSentences(text)
        val words = WORD_RE.findAll(text).map { it.value.lowercase() }.toList()
        val wordCount = words.size
        val n = wordCount.coerceAtLeast(1).toDouble()
        val sentenceCount = sentences.size.coerceAtLeast(1)

        // ---- structural ----
        val sentLens = sentences.map { WORD_RE.findAll(it).count() }.filter { it > 0 }
        val lens = if (sentLens.isEmpty()) listOf(0) else sentLens
        val mean = lens.average()
        val sd = if (lens.size > 1) sqrt(lens.map { (it - mean) * (it - mean) }.average()) else 0.0
        val openers = sentences
            .map { s -> WORD_RE.find(s)?.value?.lowercase() ?: "" }
            .filter { it.isNotEmpty() }
        val openerVariety = shannonEntropy(openers)
        val consecutive = maxConsecutiveSame(openers)
        val contractionCount = CONTRACTION_RE.findAll(text).count()

        // ---- lexical ----
        val hapax = words.groupingBy { it }.eachCount().count { it.value == 1 }
        val uniqueCount = words.toSet().size
        val avgWordLen = if (words.isEmpty()) 0.0 else words.sumOf { it.length } / n
        val adverbs = ADVERB_RE.findAll(text).count { it.value.lowercase() !in LY_NOT_ADVERBS }
        val passives = countPassives(sentences)
        val filterHits = FILTER_WORDS.sumOf { w ->
            Regex("\\b$w\\b", RegexOption.IGNORE_CASE).findAll(text).count()
        }
        val figurative = countFigurative(text)
        val sensory = SENSORY_WORDS.mapValues { (_, set) ->
            words.count { it in set }
        }

        // ---- composition ----
        val dialogueWords = countDialogueWords(text)

        return Features(
            sentenceLenMean = round2(mean),
            sentenceLenSd = round2(sd),
            paragraphLenMean = round2(
                if (paragraphs.isEmpty()) 0.0
                else paragraphs.map { WORD_RE.findAll(it).count().coerceAtLeast(1) }.average()
            ),
            sentenceOpenerVariety = round2(openerVariety),
            consecutiveSameOpener = consecutive,
            punctEmDashPer1k = round2(text.count { it.toString() == EM_DASH } * 1000.0 / n),
            punctSemicolonPer1k = round2(text.count { it == ';' } * 1000.0 / n),
            punctEllipsisPer1k = round2(
                (Regex("\\.{3}").findAll(text).count() + text.count { it.toString() == ELLIPSIS }) * 1000.0 / n
            ),
            contractionRatio = round2(contractionCount.toDouble() / sentenceCount),
            ttrWindowed = round2(windowedTtr(words, windowSize = 1000)),
            hapaxRatio = round2(if (uniqueCount == 0) 0.0 else hapax.toDouble() / uniqueCount),
            avgWordLen = round2(avgWordLen),
            adverbPer1k = round2(adverbs * 1000.0 / n),
            passivePer100Sentences = round2(passives * 100.0 / sentenceCount),
            filterWordDensity = round2(filterHits * 1000.0 / n),
            figurativePer1k = round2(figurative * 1000.0 / n),
            sensorySightPer1k = round2(sensory["sight"]!! * 1000.0 / n),
            sensorySoundPer1k = round2(sensory["sound"]!! * 1000.0 / n),
            sensoryTouchPer1k = round2(sensory["touch"]!! * 1000.0 / n),
            sensoryTastePer1k = round2(sensory["taste"]!! * 1000.0 / n),
            sensorySmellPer1k = round2(sensory["smell"]!! * 1000.0 / n),
            dialogueRatio = round2(dialogueWords * 100.0 / n),
            wordCount = wordCount,
            sentenceCount = sentences.size
        )
    }

    /** SHA-256 hex of the source text (summary-cache invalidation key, ADR-0020). */
    fun sourceHash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Sentence split on . ! ? with closing quotes kept off the next opener. */
    fun splitSentences(text: String): List<String> =
        SENTENCE_SPLIT_RE.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() && WORD_RE.containsMatchIn(it) }

    /** Shannon entropy (bits) of a symbol distribution. */
    fun shannonEntropy(symbols: List<String>): Double {
        if (symbols.isEmpty()) return 0.0
        return symbols.groupingBy { it }.eachCount().values
            .map { c -> val p = c.toDouble() / symbols.size; -p * log2(p) }
            .sum()
    }

    /** Max run length of the same consecutive symbol. */
    fun maxConsecutiveSame(symbols: List<String>): Int {
        var best = 0; var run = 0; var prev: String? = null
        for (s in symbols) {
            run = if (s == prev) run + 1 else 1
            prev = s
            if (run > best) best = run
        }
        return best
    }

    /**
     * Mean type-token ratio over a sliding window (spec: 1,000-word
     * window; shorter texts degrade to whole-text TTR).
     */
    fun windowedTtr(words: List<String>, windowSize: Int = 1000): Double {
        if (words.isEmpty()) return 0.0
        if (words.size <= windowSize) return words.toSet().size.toDouble() / words.size
        val ratios = ArrayList<Double>(words.size - windowSize + 1)
        var start = 0
        while (start + windowSize <= words.size) {
            val window = words.subList(start, start + windowSize)
            ratios.add(window.toSet().size.toDouble() / windowSize)
            start += windowSize / 2 // 50% overlap per convention
        }
        return ratios.average()
    }

    /**
     * Passive constructions per sentence list: be-form followed by a word
     * that is either a regular past participle (-ed) or a known irregular.
     */
    fun countPassives(sentences: List<String>): Int {
        var count = 0
        for (sentence in sentences) {
            val toks = WORD_RE.findAll(sentence).map { it.value.lowercase() }.toList()
            for (i in 0 until toks.size - 1) {
                val w = toks[i]
                val next = toks[i + 1]
                if (w in BE_FORMS && (next.endsWith("ed") || next in IRREGULAR_PAST_PARTICIPLES)) {
                    count++
                }
            }
        }
        return count
    }

    /** Simile cues + metaphor verbs per text. */
    fun countFigurative(text: String): Int {
        val lower = text.lowercase()
        var count = SIMILE_CUES.count { cue -> lower.contains(cue) || cue == "as ___ as" && false }
        // "as X as" simile pattern
        count += Regex("\\bas \\w+ as\\b").findAll(lower).count()
        count += WORD_RE.findAll(lower).count { it.value in METAPHOR_VERBS }
        return count
    }

    /** Words inside straight or curly double quotes (dialogue proxy). */
    fun countDialogueWords(text: String): Int {
        var total = 0
        // straight quotes
        Regex("\"([^\"]*)\"").findAll(text).forEach { m ->
            total += WORD_RE.findAll(m.groupValues[1]).count()
        }
        // curly quotes (opening “ ... closing ”)
        Regex("“([^”]*)”").findAll(text).forEach { m ->
            total += WORD_RE.findAll(m.groupValues[1]).count()
        }
        return total
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
