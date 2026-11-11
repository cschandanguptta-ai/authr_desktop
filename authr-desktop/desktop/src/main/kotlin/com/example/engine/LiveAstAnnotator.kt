package com.example.engine

import com.example.data.model.Chapter
import com.example.data.model.Lorebook

/**
 * Live AST Annotator (v3.0, plan §4.1, Table 5).
 *
 * Deterministic, fully local annotator for the Writer Studio margin rail.
 * It runs on confirmed text only — [annotate] refuses PROPOSED/ARCHIVED
 * drafts so the rail and statistics always reflect canon rather than
 * proposals. Every detector below is a pure function of the text (and, for
 * lore matching, the project lorebook); no model calls, no network.
 *
 * Latency contract (D-08 remediation, ADR-0012 — measured, not estimated):
 * the full scan of a 20,000-word chapter costs ~470 ms cold (154-pattern
 * cliché matrix dominates), so [annotate] memoizes the annotation list per
 * (text hash, lorebook hash) in a bounded LRU (32 entries). Recomposing the
 * rail on unchanged text — the hot UI path — is a cache hit and returns the
 * same immutable list instance. Any edit changes the text hash and misses.
 */
object LiveAstAnnotator {

    // ------------------------------------------------------------------
    // Annotation cache — D-08 "memoize per text-hash"
    // ------------------------------------------------------------------

    private const val CACHE_MAX = 32
    private val cacheLock = Any()
    private val annotationCache = object : LinkedHashMap<String, List<AstAnnotation>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AstAnnotation>>): Boolean =
            size > CACHE_MAX
    }

    /** Test hook: drop all cached annotation lists (cache-behavior tests). */
    fun clearAnnotationCache() = synchronized(cacheLock) { annotationCache.clear() }

    private fun cacheKey(content: String, lorebook: List<Lorebook>): String {
        val loreFingerprint = lorebook.joinToString("\u0000") { "${it.id}:${it.key}:${it.value}" }
        return textHash(content) + "#" + textHash(loreFingerprint)
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /**
     * Annotates a canon chapter. Returns null for non-canon text with the
     * reason carried in [Refusal.reason].
     */
    fun annotate(chapter: Chapter, lorebook: List<Lorebook> = emptyList()): RefusalOrAst =
        if (!WriterGate.isCanon(chapter)) {
            RefusalOrAst(refusal = Refusal(
                chapterId = chapter.id,
                reason = "Refused: chapter ${chapter.chapterNumber} is ${chapter.draftStatus} " +
                    "(writerConfirmed=${chapter.writerConfirmed}); the annotator annotates confirmed text only."
            ))
        } else {
            RefusalOrAst(ast = ManuscriptAst(
                chapterId = chapter.id,
                chapterNumber = chapter.chapterNumber,
                textHash = textHash(chapter.content),
                annotations = cachedAnnotations(chapter.content, lorebook)
            ))
        }

    /**
     * The memoized half of [annotate]. Returns the SAME immutable list
     * instance on a cache hit (observable in tests via identity); computes
     * and stores it on a miss. The expensive computation runs outside the
     * lock so parallel rails on different chapters never serialize.
     */
    private fun cachedAnnotations(content: String, lorebook: List<Lorebook>): List<AstAnnotation> {
        val key = cacheKey(content, lorebook)
        synchronized(cacheLock) { annotationCache[key] }?.let { return it }
        val computed = buildList {
            addAll(beatCoverage(content))
            addAll(clicheHits(content))
            addAll(chekhovPromises(content))
            addAll(loreReferences(content, lorebook))
            addAll(endingSignal(content))
            addAll(continuityFlags(content))
        }.sortedBy { it.start }
        synchronized(cacheLock) { annotationCache[key] = computed }
        return computed
    }

    data class Refusal(val chapterId: Int, val reason: String)
    data class RefusalOrAst(val ast: ManuscriptAst? = null, val refusal: Refusal? = null) {
        val astOrThrow: ManuscriptAst get() = ast ?: error(refusal?.reason ?: "No AST")
    }

    /** Stable content hash pinning the AST to the frozen text. */
    fun textHash(text: String): String {
        var h = 1125899906842597L
        text.forEach { c -> h = 31L * h + c.code }
        return java.lang.Long.toHexString(h)
    }

    // ------------------------------------------------------------------
    // 1. Beat coverage — seven-beat model (plan §6.3, Table 10)
    // ------------------------------------------------------------------

    /** Deterministic keyword cues per beat function; a hit marks the beat present. */
    private val beatCues: Map<ChapterBeat, List<Regex>> = mapOf(
        ChapterBeat.OPENING_CONTINUITY to listOf(
            Regex("""\b(still|already|since|earlier|previous|yesterday|last night|continued|picking up)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(where we left|the morning after|moments (after|later))\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.IMMEDIATE_OBJECTIVE to listOf(
            Regex("""\b(needed to|had to|must|wanted to|planned to|goal|mission|task|find|reach|prove|rescue|deliver)\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.RESISTANCE to listOf(
            Regex("""\b(but|however|refused|blocked|obstacle|problem|wouldn'?t|couldn'?t|denied|forbid|struggle|against)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(argued|clashed|opposed|stood in the way|got in the way)\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.ESCALATION to listOf(
            Regex("""\b(worse|harder|more dangerous|escalat\w*|spiraling|worsened|no longer|even more|too late|deadline|countdown)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(exploded|erupted|collapsed|betray\w*|ambush\w*|disaster)\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.TURNING_POINT to listOf(
            Regex("""\b(realiz\w*|understood|discovered|the truth|turned|shifted|changed everything|finally saw|at last|reversal)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(decided|chose|made her choice|made his choice|the moment everything)\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.CHAPTER_PAYOFF to listOf(
            Regex("""\b(at last|succeeded|failed|won|lost|paid|answered|resolved|finally (arrived|ended|finished)|it was done)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(the door (closed|shut)|it changed|nothing would be the same|consequence)\b""", RegexOption.IGNORE_CASE)
        ),
        ChapterBeat.FORWARD_PULL to listOf(
            Regex("""\b(tomorrow|next (time|morning|chapter|step)|about to|going to|would soon|couldn'?t know|what (he|she|they) didn'?t know|loomed|awaited)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(but (somewhere|across|beyond|far away)|meanwhile|unbeknownst|little did)\b""", RegexOption.IGNORE_CASE)
        )
    )

    /**
     * Verifies each of the seven beat functions is performed somewhere in the
     * chapter; missing functions raise a warning the writer can address or
     * waive with a note (plan Table 10, coverage check column).
     */
    fun beatCoverage(text: String): List<AstAnnotation> {
        val present = beatCues.map { (beat, cues) -> beat to cues.any { it.containsMatchIn(text) } }.toMap()
        val missing = present.filterValues { !it }.keys
        val weak = if (present.count { it.value } in 1..3 && missing.isNotEmpty()) true else false
        return buildList {
            if (missing.isEmpty()) {
                add(AstAnnotation(
                    kind = AnnotationKind.BEAT, start = 0, end = 0.coerceAtLeast(text.length),
                    excerpt = "", message = "All seven beat functions covered (Blueprint 2.0).",
                    severity = Severity.INFO, suggestedAction = WriterAction.OPEN_BEAT_CARD,
                    payload = mapOf("coverage" to "7/7")
                ))
            } else {
                add(AstAnnotation(
                    kind = AnnotationKind.BEAT, start = 0, end = 0.coerceAtLeast(text.length),
                    excerpt = "",
                    message = "Missing / weak beats: " + missing.joinToString { it.function } +
                        (if (weak) " — coverage is thin; consider a beat pass before confirming." else ""),
                    severity = Severity.WARN, suggestedAction = WriterAction.OPEN_BEAT_CARD,
                    payload = mapOf(
                        "coverage" to "${present.count { it.value }}/7",
                        "missing" to missing.joinToString(",") { it.name }
                    )
                ))
            }
            // Per-beat present markers give the rail anchors to open beat cards from.
            present.filterValues { it }.keys.forEach { beat ->
                val span = spanOf(text, beatCues[beat].orEmpty())
                add(AstAnnotation(
                    kind = AnnotationKind.BEAT, start = span.first, end = span.second,
                    excerpt = text.substring(span.first, span.second).take(120),
                    message = "${beat.function}: present.", severity = Severity.INFO,
                    suggestedAction = WriterAction.OPEN_BEAT_CARD,
                    payload = mapOf("beat" to beat.name)
                ))
            }
        }
    }

    private fun spanOf(text: String, cues: List<Regex>): Pair<Int, Int> {
        for (cue in cues) {
            val m = cue.find(text)
            if (m != null) return m.range.first to (m.range.last + 1)
        }
        return 0 to 0
    }

    /**
     * Public accessor for one beat's deterministic cues (v3.0 P3): the
     * balance meter shares the annotator's cue lists as a single source of
     * truth so the rail anchors and the measured shares can never diverge.
     */
    fun beatCueList(beat: ChapterBeat): List<Regex> = beatCues[beat].orEmpty()

    // ------------------------------------------------------------------
    // 2. Cliché hits (plan §5.1) — since P2 the annotator delegates to the
    //    full 154-pattern matrix in ClicheCleaner (Table 6). One annotation
    //    per distinct offending span; overlapping matches dedupe to the
    //    higher-severity pattern so a writer sees one node per catch.
    // ------------------------------------------------------------------

    fun clicheHits(text: String): List<AstAnnotation> = ClicheCleaner.scan(text)

    // ------------------------------------------------------------------
    // 3. Chekhov promises — introduced / escalated / resolved (plan §5.2)
    // ------------------------------------------------------------------

    data class PromiseDetector(val name: String, val regex: Regex, val description: String)

    private val promiseDetectors = listOf(
        PromiseDetector("Detailed weapon or artifact", Regex("""\bthe (glowing|ancient|ornate|forgotten|sealed|cursed)? ?(blade|dagger|revolver|rifle|pistol|amulet|locket|signet|key|ledger|journal|letter|map)\b""", RegexOption.IGNORE_CASE), "A described object is a promise it will matter."),
        PromiseDetector("Debt or owed favor", Regex("""\b(owed|owes|debt of|in (his|her|their) debt|pay you back|owe you)\b""", RegexOption.IGNORE_CASE), "An unpaid debt promises repayment or default."),
        PromiseDetector("Vow or promise", Regex("""\b(swore|sworn|vowed|made a promise|gave (his|her|their) word|promised)\b""", RegexOption.IGNORE_CASE), "A spoken vow promises fulfillment or consequence."),
        PromiseDetector("Named grudge", Regex("""\b(sworn revenge|never forg\w+|revenge for|vengeance|holds? a grudge)\b""", RegexOption.IGNORE_CASE), "A grudge promises a reckoning."),
        PromiseDetector("Open question", Regex("""\b(the question of|no one knew (who|what|why|where)|the mystery of)\b""", RegexOption.IGNORE_CASE), "An explicit question promises an answer.")
    )

    /** Marks narrative promises as CHEKHOV_GUN nodes with INTRODUCED lifecycle.
     *  The archived quote carries a context window around the match (±40
     *  chars), which is what the ledger's duplicate detection compares —
     *  a re-emphasized promise in a later canon chapter shares most of its
     *  context tokens with the original (plan Table 8, ESCALATED guard). */
    fun chekhovPromises(text: String): List<AstAnnotation> =
        promiseDetectors.flatMap { d ->
            d.regex.findAll(text).map { m ->
                val context = text.substring(
                    (m.range.first - 40).coerceAtLeast(0),
                    (m.range.last + 1 + 40).coerceAtMost(text.length)
                ).replace(Regex("""\s+"""), " ").trim()
                AstAnnotation(
                    kind = AnnotationKind.CHEKHOV_GUN, start = m.range.first, end = m.range.last + 1,
                    excerpt = m.value, message = "${d.name}: \"${m.value}\" — ${d.description}",
                    severity = Severity.INFO, suggestedAction = WriterAction.SCHEDULE_ESCALATION,
                    payload = mapOf("gun" to d.name, "quote" to context, "status" to "INTRODUCED")
                )
            }
        }

    // ------------------------------------------------------------------
    // 4. Lore references (Table 5, LORE row)
    // ------------------------------------------------------------------

    /**
     * Matches lorebook keys in the text (word-boundary, case-insensitive)
     * and flags recurring capitalized terms that have no lorebook entry —
     * "missing definitions proposed inline".
     */
    fun loreReferences(text: String, lorebook: List<Lorebook>): List<AstAnnotation> {
        val nodes = mutableListOf<AstAnnotation>()
        for (entry in lorebook) {
            val key = entry.key.trim()
            if (key.isEmpty()) continue
            val rx = Regex("""(?<![\w])${Regex.escape(key)}(?![\w])""", RegexOption.IGNORE_CASE)
            rx.findAll(text).forEach { m ->
                nodes.add(AstAnnotation(
                    kind = AnnotationKind.LORE_REFERENCE, start = m.range.first, end = m.range.last + 1,
                    excerpt = m.value, message = "Lore reference: \"${entry.key}\" — defined: \"${entry.value.take(80)}${if (entry.value.length > 80) "…" else ""}\"",
                    severity = Severity.INFO, suggestedAction = WriterAction.ACCEPT_LORE_ENTRY,
                    payload = mapOf("loreKey" to entry.key)
                ))
            }
        }
        // Recurring capitalized bigrams absent from the lorebook → propose an entry.
        val definedKeys = lorebook.map { it.key.lowercase() }.toSet()
        val candidates = Regex("""(?<![.!?]\s|\n)(?<![.!?])\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?\b""")
            .findAll(text)
            .map { it.value }
            .filter { it.lowercase() !in definedKeys }
            .groupingBy { it }
            .eachCount()
            .filter { (term, count) -> count >= 3 && term.lowercase() !in stopTerms }
        candidates.forEach { (term, count) ->
            val first = text.indexOf(term)
            nodes.add(AstAnnotation(
                kind = AnnotationKind.LORE_REFERENCE, start = first, end = first + term.length,
                excerpt = term,
                message = "Undefined recurring term \"$term\" appears $count times — propose a lorebook entry?",
                severity = Severity.WARN, suggestedAction = WriterAction.ACCEPT_LORE_ENTRY,
                payload = mapOf("loreKey" to term, "proposed" to "true", "occurrences" to count.toString())
            ))
        }
        return nodes
    }

    private val stopTerms = setOf(
        "the", "chapter", "he", "she", "it", "they", "his", "her", "their", "i",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december", "god", "okay", "yes", "no"
    )

    // ------------------------------------------------------------------
    // 5. Ending signal — six Blueprint 2.0 types + QUIET (plan §6.4)
    // ------------------------------------------------------------------

    private val endingCues: Map<String, List<Regex>> = mapOf(
        EndingTypes.DANGER to listOf(Regex("""\b(danger|threat\w*|attack\w*|kill\w*|blood|screamed|lunge\w*|blade (came|fell)|ambush|trap)\b""", RegexOption.IGNORE_CASE)),
        EndingTypes.EMOTIONAL_REVELATION to listOf(Regex("""\b(realiz\w*|confess\w*|admitted|the truth|finally understood|loved (him|her|them)|forgave)\b""", RegexOption.IGNORE_CASE)),
        EndingTypes.MYSTERY_DISCOVERY to listOf(Regex("""\b(discover\w*|found out|uncover\w*|revealed|the clue|hidden|secret (was|door|room))\b""", RegexOption.IGNORE_CASE)),
        EndingTypes.IRREVERSIBLE_CHOICE to listOf(Regex("""\b(chose|chosen|decided|no going back|burned|signed|sealed|gave up|sacrific\w*|the door closed)\b""", RegexOption.IGNORE_CASE)),
        EndingTypes.REVERSAL to listOf(Regex("""\b(suddenly|instead|but it was|until now|the opposite|in fact|had been (the|his|her) all along|turned out)\b""", RegexOption.IGNORE_CASE)),
        EndingTypes.ANTICIPATION to listOf(Regex("""\b(tomorrow|next (time|morning|dawn|move)|about to|going to|would soon|prepared for|awaited|loomed)\b""", RegexOption.IGNORE_CASE))
    )

    /**
     * Public classification primitive (v3.0 P3): scores the closing passage
     * against the six ending types and returns (type, cue hits) for the best
     * match, or QUIET when no cue fires. The EndingAdvisor builds on this so
     * the rail signal and the advisor history share one classifier.
     */
    fun classifyEnding(text: String, closingChars: Int = 600): Pair<String, Int> {
        val closing = text.takeLast(closingChars)
        if (closing.isBlank()) return EndingTypes.QUIET to 0
        val scored = endingCues.map { (type, cues) ->
            type to cues.sumOf { it.findAll(closing).count() }
        }
        val best = scored.maxByOrNull { it.second }
        val type = best?.takeIf { it.second > 0 }?.first ?: EndingTypes.QUIET
        return type to (best?.second ?: 0)
    }

    /**
     * Classifies the closing passage (final ~600 characters) against the six
     * ending types or a quiet ending; the writer confirms the detected type
     * or picks another (Table 5, ENDING row).
     */
    fun endingSignal(text: String): List<AstAnnotation> {
        val closing = text.takeLast(600)
        if (closing.isBlank()) return emptyList()
        val scored = endingCues.map { (type, cues) ->
            type to cues.sumOf { it.findAll(closing).count() }
        }
        val best = scored.maxByOrNull { it.second }
        val detected = if (best != null && best.second > 0) best.first else EndingTypes.QUIET
        val firstIdx = (text.length - closing.length).coerceAtLeast(0)
        val anchor = closing.indexOfFirst { !it.isWhitespace() }.let { if (it >= 0) firstIdx + it else firstIdx }
        return listOf(AstAnnotation(
            kind = AnnotationKind.ENDING_SIGNAL, start = anchor, end = text.length,
            excerpt = closing.take(160).trim(),
            message = "Closing signal classified as $detected" +
                (if (detected == EndingTypes.QUIET) " (quiet ending — explicitly permitted)." else "."),
            severity = Severity.INFO, suggestedAction = WriterAction.CONFIRM_DETECTED_TYPE,
            payload = mapOf("endingType" to detected, "hits" to (best?.second ?: 0).toString())
        ))
    }

    // ------------------------------------------------------------------
    // 6. Continuity flags — deterministic local checks (L6 input)
    // ------------------------------------------------------------------

    /** Hoisted once (D-08): was recompiled inside the per-position loop. */
    private val NON_WORD = Regex("""[^\w\s]""")
    private val WHITESPACE_RUN = Regex("""\s+""")
    private val POV_FIRST = Regex("""\b(I|me|my|mine)\b""")
    private val POV_THIRD = Regex("""\b(he|she|his|her|they|them)\b""", RegexOption.IGNORE_CASE)

    /** Echo: the same 5+ word phrase appearing twice within the chapter. */
    fun continuityFlags(text: String): List<AstAnnotation> {
        val nodes = mutableListOf<AstAnnotation>()
        val normalized = text.lowercase().replace(WHITESPACE_RUN, " ")
        val words = normalized.split(" ").filter { it.isNotBlank() }
        if (words.size >= 10) {
            val seen = HashMap<String, Int>() // phrase -> first index
            val window = 5
            for (i in 0..words.size - window) {
                val phrase = words.subList(i, i + window).joinToString(" ")
                    .replace(NON_WORD, "").trim()
                if (phrase.split(" ").count { it.isNotBlank() } < window) continue
                val first = seen[phrase]
                if (first == null) {
                    seen[phrase] = i
                } else if (i - first >= window) { // overlapping windows don't count
                    // Map back into the original text for the span.
                    val original = findOriginalSpan(text, phrase)
                    if (original != null) {
                        nodes.add(AstAnnotation(
                            kind = AnnotationKind.CONTINUITY_FLAG, start = original.first, end = original.second,
                            excerpt = text.substring(original.first, original.second),
                            message = "Echo: the phrase \"${text.substring(original.first, original.second).take(80)}\" repeats.",
                            severity = Severity.WARN, suggestedAction = WriterAction.RESOLVE_IN_TEXT,
                            payload = mapOf("check" to "echo")
                        ))
                        if (nodes.count { it.payload["check"] == "echo" } >= 3) break
                    }
                }
            }
        }
        // POV drift: first-person and third-person narration mixed heavily.
        val firstPerson = POV_FIRST.findAll(text).count()
        val thirdPerson = POV_THIRD.findAll(text).count()
        if (firstPerson > 12 && thirdPerson > 12) {
            val ratio = firstPerson.coerceAtLeast(thirdPerson).toDouble() / firstPerson.coerceAtMost(thirdPerson)
            if (ratio < 2.0) {
                nodes.add(AstAnnotation(
                    kind = AnnotationKind.CONTINUITY_FLAG, start = 0, end = text.length,
                    excerpt = "", message = "POV drift: first-person ($firstPerson) and third-person ($thirdPerson) cues are both dense.",
                    severity = Severity.WARN, suggestedAction = WriterAction.DISMISS_WITH_NOTE,
                    payload = mapOf("check" to "pov_drift")
                ))
            }
        }
        return nodes
    }

    private fun findOriginalSpan(text: String, normalizedPhrase: String): Pair<Int, Int>? {
        val pattern = normalizedPhrase.split(" ").joinToString("""\s+""") { Regex.escape(it) }
        val m = Regex(pattern, RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.range.first to (m.range.last + 1)
    }
}
