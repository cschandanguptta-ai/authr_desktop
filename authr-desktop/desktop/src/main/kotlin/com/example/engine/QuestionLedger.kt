package com.example.engine

import com.example.data.model.QuestionEntry

/**
 * The question ledger — foundations (v3.0, plan §6.7, Table 15).
 *
 * Serialized stories fail twice at information: once when they confuse the
 * reader, and once when their twists exist for shock. Information is
 * organized into three levels — immediate (needed to understand the current
 * scene), progressive (discovered over the next few chapters), and long-term
 * (unresolved until a major turning point or climax) — and the golden rule
 * is "mystery is not confusion". Progressive questions are tracked with
 * expected-answer windows; overdue questions surface on the writer's
 * dashboard. Long-term questions share one ledger UI with the Chekhov guns:
 * guns are promises about objects, skills, and grudges; questions are
 * promises about information (plan §6.7).
 */
object QuestionLedger {

    const val IMMEDIATE = "IMMEDIATE"
    const val PROGRESSIVE = "PROGRESSIVE"
    const val LONG_TERM = "LONG_TERM"

    /** Default expected-answer window for progressive questions (chapters). */
    const val DEFAULT_PROGRESSIVE_WINDOW = 3

    // ----------------------------------------------------------------------
    // Deterministic detection of open questions in confirmed text
    // ----------------------------------------------------------------------

    private data class QuestionCue(val regex: Regex, val template: String, val level: String)

    private val questionCues = listOf(
        QuestionCue(
            Regex("""\bthe question of (whether|who|what|why|where|how)[^.!?]*""", RegexOption.IGNORE_CASE),
            "The open question: %s", PROGRESSIVE
        ),
        QuestionCue(
            Regex("""\bno one knew (who|what|why|where|how)[^.!?]*""", RegexOption.IGNORE_CASE),
            "The unanswered mystery: %s", PROGRESSIVE
        ),
        QuestionCue(
            Regex("""\bthe mystery of[^.!?]*""", RegexOption.IGNORE_CASE),
            "The standing mystery: %s", PROGRESSIVE
        ),
        QuestionCue(
            Regex("""\bwould (haunt|linger|stay with)[^.!?]*""", RegexOption.IGNORE_CASE),
            "The long-horizon question: %s", LONG_TERM
        )
    )

    /**
     * Detects open questions in a confirmed chapter's text (§6.7
     * foundations). Each detection opens a ledger entry: PROGRESSIVE by
     * default, with its expected-answer window at [windowChapters] chapters;
     * long-horizon phrasing opens LONG_TERM (no window — it stays open until
     * a major turning point or climax). Duplicate questions within one
     * chapter are collapsed.
     */
    fun detectOpenQuestions(
        text: String,
        bookId: Int,
        chapterNumber: Int,
        windowChapters: Int = DEFAULT_PROGRESSIVE_WINDOW
    ): List<QuestionEntry> {
        val seen = mutableSetOf<String>()
        val entries = mutableListOf<QuestionEntry>()
        for (cue in questionCues) {
            cue.regex.findAll(text).forEach { m ->
                val snippet = m.value.trim().replace(Regex("""\s+"""), " ")
                // Duplicate cues within one chapter collapse to one entry —
                // keyed by the cue template + the snippet's opening words.
                val key = (cue.template + " " + snippet.lowercase())
                    .split(" ").take(4).joinToString(" ")
                if (key in seen) return@forEach
                seen.add(key)
                entries.add(
                    QuestionEntry(
                        bookId = bookId,
                        level = cue.level,
                        text = cue.template.format(snippet.take(160)),
                        openedChapter = chapterNumber,
                        dueChapter = if (cue.level == PROGRESSIVE) chapterNumber + windowChapters else null
                    )
                )
            }
        }
        return entries
    }

    // ----------------------------------------------------------------------
    // Windows, overdue surfacing, answering
    // ----------------------------------------------------------------------

    /** PROGRESSIVE questions whose expected-answer window has passed (Table 15). */
    fun isOverdue(question: QuestionEntry, currentChapter: Int): Boolean =
        question.level == PROGRESSIVE &&
            question.answeredChapter == null &&
            question.dueChapter != null &&
            question.dueChapter <= currentChapter

    /** Re-arms the expected-answer window (e.g. the writer extends it). */
    fun withWindow(question: QuestionEntry, windowChapters: Int): QuestionEntry =
        question.copy(dueChapter = question.openedChapter + windowChapters)

    /**
     * Answers a question: the chapter number is recorded and, when the
     * answer came through a revelation beat, the answering revelation entry
     * is linked ([answeredByReveal]).
     */
    fun answer(question: QuestionEntry, answeredChapter: Int, answeredByReveal: Int? = null): QuestionEntry =
        question.copy(answeredChapter = answeredChapter, answeredByReveal = answeredByReveal)

    /**
     * The dashboard surfacing: overdue progressive questions with their
     * opened/due chapters, most overdue first (§6.7, Table 15).
     */
    fun overdueReport(questions: List<QuestionEntry>, currentChapter: Int): List<String> =
        questions.filter { isOverdue(it, currentChapter) }
            .sortedBy { it.dueChapter ?: Int.MAX_VALUE }
            .map {
                "Overdue ${it.level.lowercase().replace('_', '-')} question (opened ch. ${it.openedChapter}, due ch. ${it.dueChapter}): \u201C${it.text}\u201D"
            }

    // ----------------------------------------------------------------------
    // Revelation quality — the six outcomes (§6.7, foundations hook)
    // ----------------------------------------------------------------------

    /**
     * Every important revelation must accomplish at least one of six things
     * (§6.7). L6 checks a revelation beat against this list; a twist that
     * matches none of the six is flagged — "a good revelation should feel
     * surprising when discovered and credible when the reader looks back at
     * the clues".
     */
    val revelationOutcomes = listOf(
        "answer an existing question",
        "create a more important question",
        "change the meaning of an earlier event",
        "change a character's objective",
        "alter a relationship",
        "increase the stakes"
    )

    private val outcomeCues = listOf(
        Regex("""\b(answered|the answer to|solved)\b""", RegexOption.IGNORE_CASE),                 // answers an existing question
        Regex("""\b(but now|which meant|the real question|an even bigger|new question)\b""", RegexOption.IGNORE_CASE), // creates a more important question
        Regex("""\b(all along|the truth about|what really happened|recontextual)\b""", RegexOption.IGNORE_CASE), // changes the meaning of an earlier event
        Regex("""\b(no longer|now she|now he|objective changed|new goal|instead,? (she|he|they) would)\b""", RegexOption.IGNORE_CASE), // changes an objective
        Regex("""\b(trust(ed)? (him|her|them)|betray|forgave|enemy|ally|broke (with|off))\b""", RegexOption.IGNORE_CASE), // alters a relationship
        Regex("""\b(stakes|everything to lose|even worse|now (everything|everyone)|more dangerous)\b""", RegexOption.IGNORE_CASE) // increases the stakes
    )

    /**
     * Checks a revelation beat against the six outcomes. Returns the matched
     * outcomes' indices (0-5); an empty list flags the twist as shock-only
     * (scorecard note; the question ledger marks the newly created question
     * so the next chapters inherit it honestly).
     */
    fun revelationOutcomeIndices(beatText: String): List<Int> =
        outcomeCues.withIndex().filter { (_, rx) -> rx.containsMatchIn(beatText) }.map { it.index }
}
