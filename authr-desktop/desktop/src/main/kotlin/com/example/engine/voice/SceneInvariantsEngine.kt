package com.example.engine.voice

/**
 * SCENE INVARIANTS ENGINE (v3.0 port S4, spec ADR-0013 §1.2 narrative
 * features + §1.5 dialogue sub-engine = desktop ADR-0023): the
 * deterministic scene-level micro-invariants behind the v3 warning gates
 * `pov_consistency`, `tense_consistency`, `dialogue_differentiation`,
 * `opener_variety` and `composite_drift`.
 *
 * The spec's reference implementation LLM-classifies POV/narrative
 * distance per paragraph; this desktop port implements the same gates
 * with pure-Kotlin annotators (pronoun/tense-cue scans + attributed
 * dialogue splitting). Deterministic first: false positives from a
 * cheap annotator cost the writer a glance; false negatives from no
 * gate at all cost them a broken scene. The LLM-classified tier
 * (narrative_distance, show_tell) stays on the S5 model-router roadmap.
 *
 * Scene definition: a maximal run of paragraphs sharing the same POV
 * cue. A violation is a cue shift INSIDE one scene run — switching
 * scenes between chapters or at a section break is craft, not drift.
 */
object SceneInvariantsEngine {

    // ------------------------------------------------------------------
    // POV consistency (gate: 0 violations per scene)

    enum class Pov(val label: String) {
        FIRST("1st"), SECOND("2nd"), THIRD_LIMITED("3rd-limited"), THIRD_OMNI("3rd-omni"), NONE("none");

    companion object {
            // Quotes stripped BEFORE classification: pronouns inside speech
            // belong to the character, not the narrator ("'We go now,' she
            // said" is 3rd-limited narration, not 1st). Same discipline as
            // the tense scan.
            fun of(s: String): Pov = classify(stripQuotesStatic(s))

            private val QuoteSpanStatic = Regex("""["“][^"”]{2,}["”]""")
            private fun stripQuotesStatic(text: String): String = text.replace(QuoteSpanStatic, " ")

            private fun classify(s: String): Pov = when {
                s.isBlank() -> NONE
                FIRST_RE.containsMatchIn(s) -> FIRST
                SECOND_RE.containsMatchIn(s) -> SECOND
                THIRD_RE.containsMatchIn(s) -> if (HEAD_HOP_RE.containsMatchIn(s)) THIRD_OMNI else THIRD_LIMITED
                else -> NONE
            }
    }
    }

    data class PovFinding(
        val sceneStartParagraph: Int,
        val shiftParagraph: Int,
        val from: Pov,
        val to: Pov,
        /** Head-hop: two different limited-viewpoint names in one scene. */
        val headHop: Boolean
    )

    /**
     * Scan a chapter's paragraphs for POV shifts inside a scene run. A
     * new scene starts (a) at the first paragraph, (b) after a break cue
     * (*** / ### / * * * / '---' alone on a line), or (c) on a legitimate
     * POV change signalled by an explicit scene-break-ish blank run —
     * modeled as the break-cue rule above; anything else is a violation.
     */
    fun povViolations(paragraphs: List<String>): List<PovFinding> {
        if (paragraphs.isEmpty()) return emptyList()
        val findings = mutableListOf<PovFinding>()
        var sceneStart = 0
        var scenePov: Pov = Pov.NONE
        var sceneViewpoint: String? = null // the named limited-viewpoint character
        var anchorPov: Pov = Pov.NONE     // the scene's ESTABLISHED pov (slips return to it)

        paragraphs.forEachIndexed { i, para ->
            if (i == 0 || isSceneBreak(para)) {
                sceneStart = i
                scenePov = Pov.of(para)
                anchorPov = scenePov
                sceneViewpoint = viewpointName(para)
                return@forEachIndexed
            }
            val pov = Pov.of(para)
            when {
                // A pronoun-free paragraph (proper-noun narration, action
                // beats) is POV-neutral: it neither establishes nor
                // violates the scene's POV. NONE→X and X→NONE are never
                // violations — only two conflicting POVs are.
                pov == Pov.NONE -> {}
                scenePov == Pov.NONE -> {
                    // Late anchor on the first pronoun paragraph. A proper-
                    // noun paragraph may already have claimed the viewpoint
                    // ("Mara watched…") — if a DIFFERENT viewpoint arrives
                    // now, that is a head-hop, not an anchor.
                    if (anchorPov == Pov.NONE) anchorPov = pov
                    if (pov == Pov.THIRD_LIMITED) {
                        val vp = viewpointName(para)
                        if (vp != null && sceneViewpoint != null && vp != sceneViewpoint) {
                            findings += PovFinding(sceneStart, i, Pov.THIRD_LIMITED, Pov.THIRD_OMNI, headHop = true)
                        }
                        if (vp != null) sceneViewpoint = vp
                    }
                    scenePov = pov
                }
                pov == scenePov -> {
                    // same POV class; head-hop check for 3rd-limited
                    if (scenePov == Pov.THIRD_LIMITED) {
                        val vp = viewpointName(para)
                        if (vp != null && sceneViewpoint != null && vp != sceneViewpoint) {
                            findings += PovFinding(sceneStart, i, scenePov, Pov.THIRD_OMNI, headHop = true)
                            sceneViewpoint = vp // re-anchor; one finding per hop
                        } else if (vp != null && sceneViewpoint == null) {
                            sceneViewpoint = vp
                        }
                    }
                }
                else -> {
                    // A shift BACK to the scene's established POV closes the
                    // slip — one violation per slip, not two (the flip and
                    // the flop). Only a shift to yet another POV is a new
                    // violation. anchorPov NEVER moves on violations: it is
                    // the scene's established contract.
                    if (pov == anchorPov) {
                        scenePov = pov // silent recovery
                    } else {
                        findings += PovFinding(sceneStart, i, scenePov, pov, headHop = false)
                        scenePov = pov
                    }
                }
            }
        }
        return findings
    }

    // ------------------------------------------------------------------
    // Tense consistency (gate: 0 violations per scene)

    data class TenseFinding(
        val sceneStartParagraph: Int,
        val shiftParagraph: Int,
        val fromPastToPresent: Boolean
    )

    /**
     * Tense cue scan per paragraph: dominant tense = majority of past
     * vs present cues among verbs-in-frame words. A paragraph whose
     * dominant tense flips from the scene's establishes a violation.
     * (Dialogue and inner quotes are excluded — characters may speak in
     * any tense; narration is what must hold.)
     */
    fun tenseViolations(paragraphs: List<String>): List<TenseFinding> {
        if (paragraphs.isEmpty()) return emptyList()
        val findings = mutableListOf<TenseFinding>()
        var sceneStart = 0
        var scenePast: Boolean? = null

        paragraphs.forEachIndexed { i, para ->
            if (i == 0 || isSceneBreak(para)) {
                sceneStart = i
                scenePast = dominantTense(stripQuotes(para))
                return@forEachIndexed
            }
            val past = dominantTense(stripQuotes(para)) ?: return@forEachIndexed
            val anchor = scenePast ?: run { scenePast = past; return@forEachIndexed }
            if (past != anchor) {
                findings += TenseFinding(sceneStart, i, fromPastToPresent = !past)
                scenePast = past
            }
        }
        return findings
    }

    // ------------------------------------------------------------------
    // Dialogue differentiation (gate: pairwise similarity < 0.70)

    data class DialogueDifferentiationFinding(
        val characterA: String,
        val characterB: String,
        val similarity: Double
    )

    /**
     * Spec check_dialogue_differentiation: split attributed dialogue per
     * character, extract the dialogue feature subset, and flag pairs with
     * cosine similarity > 0.70 (DIALOGUE_VOICE_COLLAPSE).
     *
     * Attribution: a dialogue line followed by a "X said" tag, or an
     * action-beat paragraph naming X that opens the quote's paragraph.
     * Unattributed lines are skipped (nothing to compare).
     */
    fun dialogueDifferentiation(
        paragraphs: List<String>,
        characters: Set<String> = emptySet()
    ): List<DialogueDifferentiationFinding> {
        val byChar = HashMap<String, StringBuilder>()
        // known names first (from the character bible), then any Name-said
        val names = characters.map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        paragraphs.forEach { p -> AttributionTag.findAll(p).forEach { names += it.groupValues[1].trim() } }

        var lastSpeaker: String? = null
        paragraphs.forEach { para ->
            val quotes = quotedSpans(para)
            if (quotes.isEmpty()) { lastSpeaker = null; return@forEach }
            val tag = AttributionTag.find(para)
            val speaker = when {
                tag != null -> tag.groupValues[1].trim()
                lastSpeaker != null -> lastSpeaker // alternating-reply convention
                else -> null
            }
            if (speaker != null && names.contains(speaker) || speaker != null && speaker.isNotEmpty() && names.isEmpty()) {
                quotes.forEach { q -> byChar.getOrPut(speaker!!) { StringBuilder() }.append(q).append(' ') }
                lastSpeaker = speaker
            } else if (speaker != null) {
                // an unfamiliar Name said → learn it (character bible may lag)
                names += speaker
                quotes.forEach { q -> byChar.getOrPut(speaker) { StringBuilder() }.append(q).append(' ') }
                lastSpeaker = speaker
            } else {
                lastSpeaker = null
            }
        }

        val findings = mutableListOf<DialogueDifferentiationFinding>()
        val entries = byChar.entries.filter { dialogueWordCount(it.value) >= MIN_DIALOGUE_WORDS }
        for (a in entries.indices) for (b in a + 1 until entries.size) {
            val sim = dialogueSimilarity(entries[a].value.toString(), entries[b].value.toString())
            if (sim > SIMILARITY_FLAG) {
                findings += DialogueDifferentiationFinding(
                    entries[a].key, entries[b].key, (sim * 100).round() / 100.0
                )
            }
        }
        return findings.sortedByDescending { it.similarity }
    }

    /** Spec extract_dialogue_features subset: function-word + length profile. */
    fun dialogueSimilarity(a: String, b: String): Double {
        val va = dialogueFeatureVector(a); val vb = dialogueFeatureVector(b)
        var dot = 0.0
        va.forEach { (k, v) -> dot += v * (vb[k] ?: 0.0) }
        if (dot == 0.0) return 0.0
        val na = Math.sqrt(va.values.sumOf { it * it })
        val nb = Math.sqrt(vb.values.sumOf { it * it })
        return dot / (na * nb)
    }

    /**
     * The dialogue subset of the voice profile: mean sentence length,
     * contraction rate, 1st/2nd-person rates, tag-word rate, and the
     * top function words — enough to catch two characters speaking in
     * the same voice without full spaCy parsing.
     */
    fun dialogueFeatureVector(text: String): Map<String, Double> {
        val sentences = VoiceFeatureExtractor.splitSentences(text)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyMap()
        val sentLens = sentences.map { s -> s.split(Regex("\\s+")).count { it.isNotBlank() } }
        val vec = linkedMapOf<String, Double>()
        vec["len_mean"] = sentLens.average()
        vec["len_sd"] = if (sentLens.size > 1) stdev(sentLens) else 0.0
        vec["contraction"] = words.count { it.contains('\'') }.toDouble() / words.size
        vec["first_person"] = words.count { FIRST_PRONOUNS.contains(it.lowercase()) }.toDouble() / words.size
        vec["second_person"] = words.count { it.lowercase() == "you" || it.lowercase() == "your" }.toDouble() / words.size
        vec["avg_word_len"] = words.sumOf { it.length }.toDouble() / words.size
        // top function words as explicit dims
        FunctionWords.forEach { fw ->
            val c = words.count { it.lowercase() == fw }
            if (c > 0) vec["fw_$fw"] = c.toDouble() / words.size
        }
        return vec
    }

    // ------------------------------------------------------------------
    // Opener variety (gate: consecutive same-opener count ≤ 2)

    data class OpenerFinding(
        val sentenceIndex: Int,
        val opener: String,
        val runLength: Int
    )

    fun openerViolations(text: String): List<OpenerFinding> {
        val sentences = VoiceFeatureExtractor.splitSentences(text)
        val openers = sentences.map { it.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: "" }
        val findings = mutableListOf<OpenerFinding>()
        var runOpener = ""; var runLen = 0
        openers.forEachIndexed { i, op ->
            if (op == runOpener) runLen++ else { runOpener = op; runLen = 1 }
            if (runLen == 3) findings += OpenerFinding(i - 2, op, runLen)
            else if (runLen > 3) findings[findings.lastIndex] = OpenerFinding(findings.last().sentenceIndex, op, runLen)
        }
        return findings
    }

    // ------------------------------------------------------------------
    // Composite drift (gate: per-scene composite drift < 0.30)

    /**
     * The v3 composite drift score for a scene text: the mean of the
     * POV, tense and opener violation RATES, each normalized to "violations
     * per 10 units" and clipped to 1.0 — so one POV shift + one tense flip
     * + one opener run across a 10-paragraph scene lands at 1.0/3 ≈ 0.33,
     * just over the 0.30 warning line, matching the spec's intent ("≥ 1 of
     * each violation kind per ~30 units warns").
     */
    fun compositeDrift(paragraphs: List<String>): Double {
        if (paragraphs.isEmpty()) return 0.0
        val povRate = (povViolations(paragraphs).size * 10.0 / paragraphs.size).coerceIn(0.0, 1.0)
        val tenseRate = (tenseViolations(paragraphs).size * 10.0 / paragraphs.size).coerceIn(0.0, 1.0)
        val sentences = paragraphs.sumOf { VoiceFeatureExtractor.splitSentences(it).size }.coerceAtLeast(1)
        val openerRate = (openerViolations(paragraphs.joinToString("\n")).size * 10.0 / sentences).coerceIn(0.0, 1.0)
        return ((povRate + tenseRate + openerRate) / 3.0).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------------------

    fun paragraphsOf(content: String): List<String> =
        content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

    fun isSceneBreak(para: String): Boolean =
        para.length <= 24 && (Regex("^\\s*[*#-]{3,}\\s*$").containsMatchIn(para) || para == "---")

    private fun viewpointName(para: String): String? {
        // "Mara saw / thought / felt / watched" — the 3rd-limited anchor.
        val m = ViewpointCue.find(para) ?: return null
        return m.groupValues[1].replaceFirstChar { it.uppercase() }
    }

    private fun dominantTense(text: String): Boolean? {
        val past = PAST_CUE.count { text.contains(Regex("\\b$it\\b", RegexOption.IGNORE_CASE)) }
        val present = PRESENT_CUE.count { text.contains(Regex("\\b$it\\b", RegexOption.IGNORE_CASE)) }
        if (past + present == 0) return null
        return past >= present
    }

    private fun stripQuotes(text: String): String = text.replace(QuoteSpan, " ")

    private fun quotedSpans(text: String): List<String> = QuoteSpan.findAll(text).map { it.value }.toList()

    private fun dialogueWordCount(sb: StringBuilder): Int =
        sb.toString().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun stdev(xs: List<Int>): Double {
        val m = xs.average()
        return Math.sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    private fun Double.round(): Long = Math.round(this)

    private val AttributionTag = Regex("""\b([A-Z][a-zA-Z]+)\s+(?:said|asked|replied|whispered|shouted|muttered|answered|growled|snapped|called)\b""")
    private val ViewpointCue = Regex("""\b([A-Z][a-z]+)\s+(?:saw|thought|felt|watched|noticed|realized|wondered|knew)""")

    private val FIRST_RE = Regex("""\b(I|me|my|mine|we|us|our)\b""", RegexOption.IGNORE_CASE)
    private val SECOND_RE = Regex("""\b(you|your)\b""", RegexOption.IGNORE_CASE)
    private val THIRD_RE = Regex("""\b(he|she|him|his|her|they|them|their|it)\b""", RegexOption.IGNORE_CASE)
    // head-hop cue: two distinct capitalized names each doing viewpoint verbs
    private val HEAD_HOP_RE = Regex("""\b[A-Z][a-z]+\s+(saw|thought|felt|watched|noticed|realized|wondered|knew)\b.*\b[A-Z][a-z]+\s+(saw|thought|felt|watched|noticed|realized|wondered|knew)\b""", RegexOption.DOT_MATCHES_ALL)

    private val QuoteSpan = Regex("""["“][^"”]{2,}["”]""")

    private val PAST_CUE = listOf("was", "were", "had", "said", "went", "came", "saw", "took", "walked", "turned", "looked", "knew", "thought", "felt", "did", "made")
    private val PRESENT_CUE = listOf("is", "are", "has", "says", "goes", "comes", "sees", "takes", "walks", "turns", "looks", "knows", "thinks", "feels", "does", "makes")

    private val FIRST_PRONOUNS = setOf("i", "me", "my", "mine", "we", "us", "our", "ours")
    private val FunctionWords = setOf("and", "but", "so", "because", "then", "when", "if", "though", "just", "very", "really", "well", "now", "here", "there")

    private const val SIMILARITY_FLAG = 0.70
    private const val MIN_DIALOGUE_WORDS = 25
}
