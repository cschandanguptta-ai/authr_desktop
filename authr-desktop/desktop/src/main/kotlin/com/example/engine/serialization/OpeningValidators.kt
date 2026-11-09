package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.EndingTypes

/**
 * Opening validators — Blueprint 2.0 Part 1 (v3.0, plan §6.2, Table 9).
 *
 * The opening benchmark: introduce a compelling source of interest within
 * the first 200-400 words, avoiding openings that run on extended geography,
 * history, family background, or fictional-system exposition. The hook
 * validator scans the opening span for any of the SEVEN acceptable sources
 * of interest and flags openings that run on pure description.
 *
 * The free-tier phase ladder (chapters 1-5) and the anti-withholding gate
 * (the Rev-2 replacement of the 1.0 paywall-cliffhanger rule) live here too:
 * the gate is chapter-count based — it binds to [Book.freeTierChapters] and
 * runs before export (plan §6.2, P2 handoff note in Table 33).
 */
object OpeningValidators {

    const val HOOK_SPAN_MIN_WORDS = 200
    const val HOOK_SPAN_MAX_WORDS = 400

    /** The seven acceptable sources of interest (Blueprint 2.0, Part 1). */
    val SOURCES_OF_INTEREST = listOf(
        "a disturbing or unusual event",
        "an urgent problem",
        "a character pursuing something important",
        "an unexpected confrontation",
        "a mystery that challenges the reader's assumptions",
        "a powerful emotional situation",
        "an intriguing voice or unusual narrative perspective"
    )

    /** One deterministic cue set per source of interest. */
    private val sourceCues: List<Pair<String, List<Regex>>> = listOf(
        "DISTURBING_EVENT" to listOf(
            Regex("""\b(something was (wrong|off)|unusual|disturbing|unnatural|wrongness|out of place|shouldn'?t (be|exist)|impossible)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(corpse|blood|mangled|shattered|desecrat\w*|grotesque)\b""", RegexOption.IGNORE_CASE)
        ),
        "URGENT_PROBLEM" to listOf(
            Regex("""\b(urgent|emergency|deadline|running out of time|no time|racing|before it was too late|had (hours|minutes) left)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(alarm|siren|evacuat\w*|collaps\w*|on fire|flood\w*)\b""", RegexOption.IGNORE_CASE)
        ),
        "PURSUIT" to listOf(
            Regex("""\b(pursu\w*|chasing|searched for|hunted|quest|determined to (find|reach|win|prove)|desperately (needed|wanted) to)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(the (search|hunt|race) (for|to)|on a mission|one goal)\b""", RegexOption.IGNORE_CASE)
        ),
        "CONFRONTATION" to listOf(
            Regex("""\b(confront\w*|stood (face to face|nose to nose)|demanded an answer|refused to (move|yield|back down)|blocked (his|her|their) path)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(argued|shouted|slammed|accused|challenged)\b""", RegexOption.IGNORE_CASE)
        ),
        "MYSTERY" to listOf(
            Regex("""\b(mystery|mysteriously|no one (knew|could explain)|unexplained|impossible (thing|event)|challenged everything (she|he|they) knew)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(why would|who (had|could have)|how (did|could))\b""", RegexOption.IGNORE_CASE)
        ),
        "EMOTIONAL_SITUATION" to listOf(
            Regex("""\b(grief|grieving|funeral|tears|wept|heartbreak|betrayal|reunion|farewell|last words|dying)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(shook (him|her|them) to|overwhelm\w* with (grief|anger|joy)|could barely breathe)\b""", RegexOption.IGNORE_CASE)
        ),
        "INTRIGUING_VOICE" to listOf(
            Regex("""\b(let me tell you|you should know|they will say|I was (never|always) the kind|strange thing about)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(unusual (way|habit|custom)|peculiar tradition|odd little)\b""", RegexOption.IGNORE_CASE)
        )
    )

    /** Chapter-1 story-promise goals (the five, Blueprint 2.0 Part 1). */
    val STORY_PROMISE_GOALS = listOf(
        "introduce the protagonist",
        "establish the immediate situation",
        "introduce a conflict, disruption, or compelling objective",
        "give the reader a reason to care",
        "create an unanswered question that encourages further reading"
    )

    /** One cue per story-promise goal, index-aligned with [STORY_PROMISE_GOALS]. */
    private val promiseCues = listOf(
        Regex("""\b(protagonist|main character|her name|his name|introduced herself|introduced himself)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(immediate situation|right now|today|this morning|at that moment|the situation|this very (day|night))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(conflict|disruption|objective|enemy|threat|problem|struggle)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(reason to care|it mattered|nothing mattered more|couldn'?t afford to (lose|fail)|everything depended)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(unanswered|question|mystery|no one knew|what (had) (happened|become of))\b""", RegexOption.IGNORE_CASE)
    )

    /** Substantial developments for chapters 4-5 (the conversion point). */
    val SUBSTANTIAL_DEVELOPMENTS = listOf(
        "an unexpected revelation",
        "a significant victory with an unforeseen cost",
        "a confrontation that changes a relationship",
        "a major complication",
        "a decision that changes direction",
        "a new understanding of the central conflict"
    )

    /** One cue per substantial development, index-aligned with the list above. */
    private val developmentCues = listOf(
        Regex("""\b(revelation|revealed|discovered the truth|the truth (was|about)|discovery)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(victory|won the (fight|case|match)|succeeded),? (but|yet|however)|cost (him|her|them) (dearly|everything|the)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(confronted|confrontation|no longer (trusted|spoke)|the (friendship|partnership|alliance) (was|had) (over|ended|broken))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(complication|complicated everything|made everything (harder|worse)|setback)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(decided to|chose to|changed (course|direction)|from now on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(understood at last|finally understood|saw it (clearly|now)|the real (enemy|battle|problem))\b""", RegexOption.IGNORE_CASE)
    )

    // ------------------------------------------------------------------
    // Word helpers
    // ------------------------------------------------------------------

    fun words(text: String): List<String> = text.split(Regex("""\s+""")).filter { it.isNotBlank() }

    fun wordCount(text: String): Int = words(text).size

    /**
     * The opening span: the first 200-400 words (the benchmark is a range;
     * the validator scans up to the max span and reports the scanned size).
     */
    fun openingSpan(text: String, maxWords: Int = HOOK_SPAN_MAX_WORDS): String {
        val w = words(text)
        return if (w.size <= maxWords) text else w.take(maxWords).joinToString(" ")
    }

    // ------------------------------------------------------------------
    // Hook validator (§6.2)
    // ------------------------------------------------------------------

    data class HookResult(
        val chapterNumber: Int,
        val sourcesFound: List<String>,
        /** True when the opening runs on pure description (no source of interest). */
        val pureDescription: Boolean,
        val scannedWords: Int,
        val message: String
    ) {
        val passes: Boolean get() = sourcesFound.isNotEmpty()
    }

    /**
     * Scans the opening span against the seven sources of interest. An
     * opening with no source cue in the span is flagged as running on pure
     * description (WARN; the drafting rule for chapter one is encoded into
     * the L4 prompt: begin with a character experiencing, pursuing, or
     * confronting something that matters).
     */
    fun validateHook(chapter: Chapter): HookResult {
        val span = openingSpan(chapter.content)
        val found = sourceCues.filter { (_, cues) -> cues.any { it.containsMatchIn(span) } }.map { it.first }
        val scanned = wordCount(span)
        return HookResult(
            chapterNumber = chapter.chapterNumber,
            sourcesFound = found,
            pureDescription = found.isEmpty(),
            scannedWords = scanned,
            message = if (found.isNotEmpty())
                "Hook present in the first $scanned words: ${found.joinToString()}."
            else
                "Opening runs on pure description — no source of interest in the first $scanned words. " +
                    "Begin with a character experiencing, pursuing, or confronting something that matters."
        )
    }

    /** The chapter-1 five-goal story promise; returns the MISSING goals (WARN). */
    fun storyPromise(chapter: Chapter): List<String> {
        val hits = promiseCues.withIndex().filter { (_, rx) -> rx.containsMatchIn(chapter.content) }.map { it.index }
        return STORY_PROMISE_GOALS.filterIndexed { i, _ -> i !in hits }
    }

    // ------------------------------------------------------------------
    // Free-tier phase checks (§6.2 Table 9)
    // ------------------------------------------------------------------

    /** Long narration stretch without dialogue or action (chapters 2-3 investment check). */
    data class ExpositionRun(val paragraphStart: Int, val paragraphEnd: Int, val words: Int)

    /**
     * Flags paragraphs of [minRunWords]+ words that contain no dialogue
     * quotes and no action verbs — the "do not spend two chapters explaining
     * the world" rule (Table 9, chapters 2-3).
     */
    fun expositionRuns(chapter: Chapter, minRunWords: Int = 120): List<ExpositionRun> {
        val runs = mutableListOf<ExpositionRun>()
        val actionVerbs = Regex(
            """\b(ran|walked|grabbed|threw|jumped|climbed|pushed|pulled|opened|closed|turned|stood|sat|reached|struck|hit|fled|chased|fought|drew|took|gave|looked|watched|listened|spoke|said|shouted|whispered|nodded|smiled|frowned|knocked|kicked|carried|followed|stopped)\b""",
            RegexOption.IGNORE_CASE
        )
        var offset = 0
        for (para in chapter.content.split(Regex("""\n\s*\n"""))) {
            val wc = wordCount(para)
            val hasDialogue = para.contains('"') || para.contains('\u201C') || para.contains('\u201D')
            val hasAction = actionVerbs.containsMatchIn(para)
            if (wc >= minRunWords && !hasDialogue && !hasAction) {
                runs.add(ExpositionRun(offset, offset + para.length, wc))
            }
            offset += para.length + 1
        }
        return runs
    }

    /**
     * Chapters 4-5 conversion-point check: at least one substantial
     * development (Table 9). Returns the developments found.
     */
    fun substantialDevelopments(chapter: Chapter): List<String> =
        developmentCues.withIndex().filter { (_, rx) -> rx.containsMatchIn(chapter.content) }
            .map { SUBSTANTIAL_DEVELOPMENTS[it.index] }

    // ------------------------------------------------------------------
    // The anti-withholding gate (§6.2, Table 9 last row)
    // ------------------------------------------------------------------

    data class WithholdingVerdict(
        val gated: Boolean,
        val finalFreeChapter: Int?,
        /** A confirmed payoff beat resolving part of the opening conflict. */
        val payoffPresent: Boolean,
        /** The seed of a more consequential development. */
        val consequentialSeedPresent: Boolean,
        val message: String
    ) {
        val passes: Boolean get() = !gated || (payoffPresent && consequentialSeedPresent)
    }

    private val openingConflictPayoffCues = listOf(
        Regex("""\b(resolved|settled|answered|closed the (matter|case)|dealt with|ended the (threat|problem|dispute|hunt|search))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(paid off|fulfilled|completed the (task|search|delivery)|found the (answer|medicine|key|location|herb|remedy))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(the first (battle|conflict) (was|had been) (over|won)|part of the (conflict|problem|mystery) (was )?resolved|one problem (was )?behind (her|him|them))\b""", RegexOption.IGNORE_CASE)
    )

    private val consequentialSeedCues = listOf(
        Regex("""\b(more consequential|bigger (than|problem|threat)|greater (danger|threat|stakes)|only the beginning|first (move|blow) of)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(what (she|he|they) didn'?t know|far worse|had just (discovered|learned)|new (enemy|threat|danger)|the real (battle|threat|enemy))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(was (already )?(underway|coming|waiting)|would change everything|something (much )?(bigger|larger) (was|had) (beginning|started))\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * The gate (Table 9): the final free chapter must contain a confirmed
     * payoff beat resolving part of the opening conflict PLUS the seed of a
     * more consequential development — the reader must feel the story
     * rewarded their investment and that the next chapter offers genuinely
     * valuable continuation. This is the INVERSE of the 1.0 paywall-
     * cliffhanger rule, which Blueprint 2.0 replaces.
     *
     * Chapter-count based: it binds to [Book.freeTierChapters] and fires
     * before export (plan Table 33, P2 handoff). Books with a free tier of
     * 0 (retail-only) are not gated.
     */
    fun antiWithholdingGate(book: Book, chapters: List<Chapter>): WithholdingVerdict {
        val freeTier = book.freeTierChapters
        if (!book.isSerialized || freeTier <= 0) return WithholdingVerdict(
            gated = false, finalFreeChapter = null, payoffPresent = false,
            consequentialSeedPresent = false, message = "No free tier — the anti-withholding gate does not apply."
        )
        val canon = chapters.filter { com.example.engine.WriterGate.isCanon(it) }
        val finalFree = canon.filter { it.chapterNumber <= freeTier }.maxByOrNull { it.chapterNumber }
            ?: return WithholdingVerdict(
                gated = true, finalFreeChapter = null, payoffPresent = false,
                consequentialSeedPresent = false,
                message = "Anti-withholding gate: no confirmed chapter exists within the $freeTier-chapter free tier."
            )
        val payoff = openingConflictPayoffCues.any { it.containsMatchIn(finalFree.content) }
        val seed = consequentialSeedCues.any { it.containsMatchIn(finalFree.content) }
        return WithholdingVerdict(
            gated = true,
            finalFreeChapter = finalFree.chapterNumber,
            payoffPresent = payoff,
            consequentialSeedPresent = seed,
            message = buildString {
                append("Anti-withholding gate (final free chapter ${finalFree.chapterNumber}): ")
                append(if (payoff) "payoff resolving part of the opening conflict present" else "MISSING payoff resolving part of the opening conflict")
                append("; ")
                append(if (seed) "seed of a more consequential development present" else "MISSING seed of a more consequential development")
                append(".")
            }
        )
    }

    // ------------------------------------------------------------------
    // Outline gate (§6.2 Table 9, chapter 1 row)
    // ------------------------------------------------------------------

    /**
     * The L2 outline gate for serials: Chapter 1 must carry the inciting
     * beat before the outline can be approved. Deterministic cue scan over
     * the outline's first numbered entry.
     */
    fun outlineCarriesIncitingBeat(outline: String): Boolean {
        val firstEntry = outline.lineSequence().firstOrNull { it.trim().matches(Regex("""\d+\..*""")) } ?: outline
        return Regex(
            """\b(inciting|discovers|discovery|attack|disappear\w*|murder|lost|stolen|arrives|meets|receives (a|an|the)|is chosen|awakens|inherits|witness\w*|summon\w*|deadline|crisis)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(firstEntry)
    }

    // ------------------------------------------------------------------
    // Shared constants
    // ------------------------------------------------------------------

    /** The serialized drafting rule encoded into the L4 prompt for chapter one (§6.2). */
    const val L4_OPENING_RULE: String =
        "Begin with a character experiencing, pursuing, or confronting something that matters " +
            "within the first 200-400 words; let world-building emerge through action and observation. " +
            "Do not open with extended geography, history, family background, or fictional-system exposition."

    /** Ending types that count as high-intensity for the fatigue rule (§6.4). */
    val HIGH_INTENSITY_ENDINGS = setOf(
        EndingTypes.DANGER, EndingTypes.EMOTIONAL_REVELATION, EndingTypes.MYSTERY_DISCOVERY,
        EndingTypes.IRREVERSIBLE_CHOICE, EndingTypes.REVERSAL
    )
}
