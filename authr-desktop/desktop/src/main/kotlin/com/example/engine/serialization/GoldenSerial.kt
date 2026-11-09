package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.engine.DraftState
import com.example.engine.QuestionLedger
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The 15-chapter golden serial — the P3 exit-gate corpus (plan §13 Table 33,
 * §15 Table 34: "a 15-chapter golden serial passes the full validator suite
 * with at most two dismissable warnings").
 *
 * The corpus is fully deterministic and PLANTED: every chapter's text carries
 * the cue of each of the seven beat functions, each free-tier chapter opens
 * with a source of interest inside the 200-400 word span, chapters 4-5 carry
 * substantial developments, the final free chapter satisfies the
 * anti-withholding gate, and each closing passage contains exactly one
 * ending type's signature cues (all seven types exercised, no consecutive
 * same-type high-intensity repeats, QUIET supported). Chapter lengths sit
 * inside the 1,000-2,000-word envelope; released chapters 1-11 carry
 * Mon/Wed/Fri release dates, leaving an amber buffer of 4.
 *
 * The seeded negative variants ([withheldVariant], [beatGapVariant],
 * [scorecardBandVariants]) drive the other two P3 exit gates: the
 * anti-withholding gate firing exactly on the seeded withheld-payoff
 * project, and the scorecard interpretation bands behaving per Table 18.
 */
object GoldenSerial {

    const val CHAPTER_COUNT = 15
    const val FREE_TIER = 5

    /** Intended ending type per chapter (no consecutive same-type high-intensity). */
    val ENDING_PLAN = listOf(
        "MYSTERY_DISCOVERY", "DANGER", "QUIET", "REVERSAL", "EMOTIONAL_REVELATION",
        "ANTICIPATION", "MYSTERY_DISCOVERY", "IRREVERSIBLE_CHOICE", "REVERSAL", "DANGER",
        "QUIET", "EMOTIONAL_REVELATION", "IRREVERSIBLE_CHOICE", "ANTICIPATION", "MYSTERY_DISCOVERY"
    )

    private const val TITLE = "The Estate of Fallingwater Dike"
    private const val GENRE = "Family drama / light mystery"

    fun book(): Book = Book(
        id = 301,
        title = TITLE,
        genre = GENRE,
        synopsis = "A river-town estate, a sealed deed, and the daughter who must settle the family dispute before the dike gate opens.",
        outline = (1..CHAPTER_COUNT).joinToString("\n") { n -> "$n. Chapter $n Title: Kira advances the estate dispute one step further." },
        format = "Web Novel (Serial)",
        narrativeStructure = "Micro-Arc Cascade",
        isSerialized = true,
        targetChapters = CHAPTER_COUNT,
        releaseSchedule = "Mon/Wed/Fri",
        freeTierChapters = FREE_TIER,
        wordMin = 1000,
        wordMax = 2000,
        bufferTarget = 8,
        exportProfile = "royal_road",
        currentPhase = "Drafting"
    )

    // ------------------------------------------------------------------
    // Release schedule: chapters 1-11 on consecutive Mon/Wed/Fri slots
    // ------------------------------------------------------------------

    private val ANCHOR_MONDAY: LocalDate = LocalDate.parse("2025-01-06") // a Monday

    /** The scheduled release date for chapter [n] (1-based) — strictly on Mon/Wed/Fri. */
    fun releaseDate(n: Int): LocalDate {
        require(n >= 1) { "chapter number must be >= 1" }
        var date = ANCHOR_MONDAY
        var step = 1
        while (step < n) {
            date = nextScheduledDay(date)
            step++
        }
        return date
    }

    private fun nextScheduledDay(date: LocalDate): LocalDate {
        var d = date.plusDays(1)
        while (d.dayOfWeek !in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) d = d.plusDays(1)
        return d
    }

    // ------------------------------------------------------------------
    // Text generation
    // ------------------------------------------------------------------

    /**
     * Neutral narrative filler. Every template:
     *  - is 60-90 words (under the 120-word exposition-run threshold);
     *  - contains NONE of the six ending types' signature cues, so the
     *    closing 600 characters of a chapter score only its planted type;
     *  - contains no question-ledger cue ("the question of", "no one knew",
     *    "the mystery of", "would haunt") and no Chekhov promise cue, so the
     *    confirm flow seeds exactly the planted ledger entries.
     */
    private val FILLER: List<String> = listOf(
        "The town registry kept its ledgers behind a grilled window, and the clerk on duty that week had a habit of sorting papers by the weight of their wax rather than by any date. Kira waited while he worked, watching the river traffic gather on the far pier, and she used the interval to copy three entries into her own notebook before anyone asked what she was doing there.",
        "Breakfast at the widow Hara's boarding house was a quiet ceremony of bread, hard cheese, and one shared pot of tea. The other lodgers traded rumors about the granary contract with the restraint of people who had learned that talk travels faster than barges. Kira ate quickly, thanked the cook, and left before the conversation could turn to her family.",
        "The dike path ran level for a mile before it climbed, and the climb gave the whole valley at a glance: rooftops, drying racks, the estate's slate roof holding its patch of weather. She counted the boats at anchor out of habit rather than need, marked which workshops had their shutters open, and was at the top before the church bell finished its hour.",
        "Inside the archive room the air smelled of dust and cold iron, and the shelves stood in ranks like a jury waiting to be addressed. She signed the visitor sheet, took the numbered token for the deed shelves, and carried a reading stand to the window where the light lasted longest. The assistant on duty nodded once and returned to his catalog without curiosity.",
        "The ferry master ran his crossing on courtesy and hard arithmetic, and he did not confuse the two. He listened to her request, repeated it back in fewer words, and named his price with the flatness of a man who had set it years ago. She paid half on the spot and agreed to carry his letters back, which suited them both and cost the estate nothing.",
        "Rain came sideways off the water that afternoon, and the market stalls folded their awnings in a practiced sequence, each vendor knowing the wind's order without being told. Kira bought oil, salt, a coil of good rope, and a second lamp, then stood under the granary eave while the first squall passed. Her boots were soaked through, but her notes stayed dry inside her coat.",
        "Her cousin Bren held the estate's accounts in a hand so small it looked like print, and he read every column aloud twice before he let anyone touch the ledger. They worked through the toll receipts line by line, arguing gently about two entries that could not both be right. By supper the totals agreed, and the two disputed lines sat on their own page, patient as stones.",
        "The night watch assembled at the fountain with their lanterns hooded, a small ritual that told the town the hour better than any bell. Kira walked home through their ranks with her eyes down and her bundle held close. A woman at the second corner sold her a cup of warmed cider, and the two of them spoke of weather and nothing else, which was its own kind of mercy.",
        "The parish school kept its door unlocked during lessons so that parents could hear the recitation from the hall, and the sound of thirty children saying the river rule in unison carried two streets over. Kira paused there on her way to the notary, letting the rhythm settle her thoughts. Then the bell rang, the door burst open, and the hall filled with hats and hurries.",
        "Old Meterman Josse repaired scales for a living and opinions for a hobby, and his shop smelled of oil, iron, and strong tobacco. He tested her small weights against his standard set, pronounced them honest within a hair, and refused payment with a wave that ended the discussion. On the way out she noticed his wall of odd keys, each one labeled in a hand steadier than his age allowed.",
        "The estate garden had gone to rosemary and stone, which the head gardener defended as a deliberate economy rather than neglect. He walked Kira along the drip lines, showed her which taps fed which beds, and complained with evident satisfaction about the drainage. She took his measurements, promised him a decision by week's end, and wrote down his three grumbles as action items.",
        "Sunday stretched long and lawful across the town: no carts, no hammering, and the taverns serving food only. Families walked the dike in their good coats, and the estate's lawn hosted a croquet game whose scoring disputes outlasted the light. Kira sat with the widow Hara on the bench by the lych-gate, and they split a small paper of candied ginger between them.",
        "The granary's new hoist squealed on its rope until the millwright greased the drum, a fifteen-minute repair that saved the crew an hour a day. Kira watched the work from the loading dock, recorded the part number in her notebook, and asked how often the rope should be replaced. The foreman said every season, then added that nobody ever did, and both of them laughed.",
        "Her aunt Sela arrived on the late barge with two trunks, one hatbox, and a Temperance that filled the whole parlor. She approved of the tidy kitchen, distrusted the new stove on principle, and asked after the estate business with the delicacy of a customs officer. Kira gave her the short version, agreed to Sunday supper, and carried the heavier trunk upstairs herself.",
        "The notary's office kept its seals in a locked drawer that rattled when the drawer was moved, a sound every client learned to dread or enjoy depending on their business. Kira's business was straightforward: a certified copy, two signatures, and the standard fee. The clerk stamped the page, blew on the ink out of ceremony rather than need, and wished her a dry road home.",
        "Fog claimed the river before sunrise and released it in pieces, first the masts, then the far bank, then the bridge with its single lamp. Kira crossed with the milk cart, listening to the driver name every pothole by owner. At the estate gate she paid the cart's toll with her own coin, entered it in the household book under travel, and went straight to the deeds room.",
        "The boathouse held three hulls in various states of honesty, and the estate's youngest hand worked on all of them at once with an energy that made up for his tools. Kira commissioned the middle hull for a survey run, agreed on a rate and a day, and left him planing wood with the whole doorway ringing. The smell of cedar followed her up the path.",
        "Winter's first hard frost put lace on the dike grass and cracked the watering jug left outside the kitchen door. The household moved the tender plants to the cellar windows and argued about whether the fig could be saved. Kira voted yes, wrapped the pot herself, and set it where the morning sun reached longest, recording the transfer in the garden ledger like a small treaty.",
        "The town crier read the week's notices at the fountain: a road repair, a lost dog, and the granary contract's winning bid read twice for emphasis. Kira listened from the back, marked the bid amount in her notebook, and compared it against the estate's own estimate on her walk home. The difference was small, explainable, and worth a letter to the factor.",
        "Her brother Tam mended nets on the porch and narrated his knots as if teaching an invisible apprentice, a habit from their father that their mother had never cured. He asked nothing about the estate business, which was his way of asking everything. Kira gave him one true sentence about the deed, and he nodded, pulled a knot tight, and told her to eat more.",
        "The lecture at the mechanics' hall concerned pump efficiency and ran long past its advertised hour, but the audience stayed because the speaker drew his diagrams on a moving belt of cloth. Kira took the last seat, filled four pages of notes, and asked the one question about intake screens that made the speaker smile and the hall murmur its approval.",
        "The laundry line behind the boarding house strung between two poles that leaned toward each other like old conspirators, and on windless days the sheets dried stiff as sailcloth. Kira claimed the line at dawn, pinned her letter-writing dress to the sun, and used the hour to draft her petition twice: once short, once shorter. The short one went into her bag; the shortest went to the notary.",
        "Market day rearranged the square into a maze of canvas and credit, and the fishmonger's ice wagon made a corridor of cold down the middle that everyone used without acknowledging. Kira bought mackerel for the household, haggled the eel seller down two coppers out of sportsmanship, and lent her arithmetic to a wool dispute until both parties discovered they had been quoting different weights.",
        "The estate's accounts room held its records in pigeonholes labeled by a clerk long dead whose handwriting remained the most elegant thing in the building. Bren sorted receipts by quarter while Kira reconciled the toll book against the ferry master's own tally. Their two figures disagreed by exactly one crossing, and tracking that single boat took the rest of a satisfying afternoon.",
        "A letter from the capital arrived with a tax stamp and an apology for the delay, which was more courtesy than the post usually managed. The factor confirmed the granary contract's terms and enclosed a form Kira had to copy three times before the estate's seal could be affixed. She copied it, sealed it, and walked the reply to the barge herself, arriving as the rope was cast off."
    )

    /** Hook paragraph: a source of interest inside the opening span (plan §6.2). */
    private fun hookParagraph(chapterNumber: Int): String = when (chapterNumber) {
        1 -> "The summons came at dawn with its seal already broken, and something was wrong about the timing that Kira could not put down: the estate office did not wake before seven, yet the paper was stamped at five. She read it twice at the window, watching the dike road for the rider who should have delivered it, and no rider came."
        2 -> "An urgent letter waited on the boarding-house table, its margin marked in the factor's hurried hand, and the deadline inside it gave her until dusk to answer or lose the estate's claim to the toll appeal. She ate nothing, packed her notebook and her mother's transit card, and was first through the ferry queue."
        3 -> "The pursuit that had brought her to the archive began as her mother's and ended as hers: three parishes, one sealed deed, and a family that needed the search finished before the winter assizes. The reading room opened at eight; she was at the desk at eight, with her tokens counted and her lamps trimmed."
        4 -> "An unexpected confrontation closed the market square that morning: the toll clerk and the ferry master stood face to face over the crossing book, each refusing to move, and the crowd took sides the way crowds do. Kira pushed through with her receipts in hand, because whoever settled that argument first would settle hers."
        5 -> "The morning brought one powerful emotional situation she had rehearsed for and still missed her footing in: the ferry master handed her a small box of her mother's unclaimed letters, and the widow Hara put her hand on Kira's shoulder and held it there while grief arrived before the tea did. She sat with the box on her knees while the boarding house went about its breakfast."
        else -> "The chapter opens on Kira already in motion, mid-task, with the estate business pressing on her from the first line: she enters through action and observation, and the world explains itself as she goes."
    }

    /**
     * The seven planted beat sentences (plan §6.3, Table 10). Each contains
     * the cue of its beat function; the balance meter reads their spans.
     */
    private fun beatParagraph(chapterNumber: Int): String = listOf(
        // 1 Opening continuity — reconnect without repetition
        "The morning still carried the weight of the previous day's argument, and the workshop she had left last night stood exactly where her patience had run out, which was its own form of picking up where we left things.",
        // 2 Immediate objective
        "Her objective had not changed since the toll office shut its window on her: she needed to reach the ferry master before noon and prove that the estate's crossing book and his tally agreed.",
        // 3 Resistance
        "But the clerk refused her papers a second time, an obstacle with no visible reason to exist, and the queue behind her grew longer as the argument against her claim hardened into procedure.",
        // 4 Escalation
        "Every hour made the situation worse: the fine doubled at dusk, the river would grow more dangerous once the ice brokers arrived, and the countdown on her appeal left no room for a second mistake.",
        // 5 Turning point
        "Then she discovered the second stamp on her mother's transit card, and the truth of the old debt shifted at last into a shape she could finally argue against.",
        // 6 Chapter payoff
        "By evening the ferry master answered her petition and the disputed fee was paid back at last, so the first crossing was resolved and one problem stood behind her for good.",
        // 7 Forward pull
        "Tomorrow the second gate awaited her name in its ledger, and with it the questions she could not yet frame, and she wrote none of them down because sleep was going to take her where she sat."
    ).joinToString(" ")

    /**
     * Closing paragraph: exactly one ending type's signature cues (plan
     * §6.4, Table 12). All other cue families are absent from the final
     * 600 characters by construction.
     */
    private fun endingParagraph(type: String): String = when (type) {
        "DANGER" ->
            "Outside, the night watch raised its lanterns along the granary road: riders had breached the lower dike, the danger had already reached the gatehouse, and the first attack struck while the town still slept, steel ringing against the bar Kira had latched only an hour before."
        "EMOTIONAL_REVELATION" ->
            "By lamplight she finally understood the confession her mother had admitted only once, on a night neither of them had named since; she wept for the woman she had loved without ever knowing, and the lamp burned low and steady until morning."
        "MYSTERY_DISCOVERY" ->
            "Beneath the loose floorboard she discovered a hidden letter, folded around a single clue in her grandfather's hand; the name in its margin did not match any entry in the parish register, and she read it four times before she could breathe."
        "IRREVERSIBLE_CHOICE" ->
            "She signed the transfer deed, sealed it with the estate's wax, and gave up the family name to save the dike works; there was no going back from a thing done in front of two witnesses, and she did not look at the pen when she set it down."
        "REVERSAL" ->
            "The clerk had praised her petition until now; then he produced the counter-deed from his second drawer, and the assumption the whole family had carried turned out to rest on a copy, not an original, and the room went quiet around the paper."
        "ANTICIPATION" ->
            "At dawn the dike gate would open for the assizes, and tomorrow both families would stand on opposite sides of the same bridge, prepared for the meeting each had spent a season dreading; the town talked of nothing else, and the evening seemed to lean toward morning."
        "QUIET" ->
            "She banked the fire, set two cups on the tray out of habit rather than hope, and let the small apartment settle into the kind of evening that asks nothing of anyone; the kettle ticked as it cooled, and the river ran on without her."
        else -> endingParagraph("QUIET")
    }

    /** Substantial-development sentence for chapters 4-5 (plan §6.2, Table 9). */
    private const val DEVELOPMENT_CH4: String =
        "The market's unexpected revelation settled the square: the counter-ledger proved a significant victory for her claim, but its unforeseen cost was the toll clerk's resignation, a confrontation that changed every relationship she had built at the office, and a major complication she now owned."
    private const val DEVELOPMENT_CH5: String =
        "The letters changed her direction: her decision to open them was a new understanding of the central conflict, and it made everything that followed harder to undo."

    /** Anti-withholding sentences for the final free chapter (plan §6.2, Table 9). */
    private const val WITHHOLDING_PAYOFF: String =
        "The toll office resolved her mother's standing fine that afternoon, and the first part of the estate dispute was settled and paid, so the reader's weeks of investment receive a real, deliverable reward."
    private const val WITHHOLDING_SEED: String =
        "But the far worse matter of who had sealed the original deed was only the beginning: the archive yielded a greater threat than the fine, and what she didn't know about the counter-deed would outweigh everything the payoff just gave her."

    /** Next-arc objective seed for the arc's close (plan §6.5). */
    private const val NEXT_ARC_SEED: String =
        "A new objective took shape behind the estate's sealed doors, and the next narrative objective could no longer be postponed: the survey of the counter-deed's parish would open the next arc of the family's argument."

    /** Climax-phase sentence for chapters 12-15 (plan §6.5). */
    private const val CLIMAX_SENTENCE: String =
        "The decisive confrontation of the estate dispute arrived with the assizes, and the climax the whole arc had walked toward was heard in one sitting, with the payoff read into the record and the consequence entered beside it."

    /** Full text of one chapter: hook + beats + development/withholding/gates + filler + ending. */
    private fun chapterText(n: Int): String {
        val parts = mutableListOf<String>()
        parts.add(hookParagraph(n))
        parts.add(beatParagraph(n))
        if (n == 4) parts.add(DEVELOPMENT_CH4)
        if (n == 5) {
            parts.add(DEVELOPMENT_CH5)
            parts.add(WITHHOLDING_PAYOFF)
            parts.add(WITHHOLDING_SEED)
        }
        if (n == 1) {
            parts.add(
                "Her name was Kira Vess and the situation had found her this very day; the dispute over the estate gave her a reason to care that needed no dressing, because everything depended on the answer, and no one knew who had sealed the original deed."
            )
        }
        if (n in 12..15) parts.add(CLIMAX_SENTENCE)
        if (n >= 14) parts.add(NEXT_ARC_SEED)
        // 12 filler paragraphs, rotated deterministically per chapter so every
        // paragraph inside a chapter is distinct (25 templates, 12 used).
        for (k in 0 until 12) {
            parts.add(FILLER[(n * 7 + k) % FILLER.size])
        }
        parts.add(endingParagraph(ENDING_PLAN[n - 1]))
        return parts.joinToString("\n\n")
    }

    // ------------------------------------------------------------------
    // Seeded artifacts
    // ------------------------------------------------------------------

    /** Complete worksheet JSON (plan §6.8, Table 16). */
    fun worksheetJson(n: Int): String =
        PreDraftWorksheet.Worksheet(
            objective = "Advance the estate dispute: what Kira wants from chapter $n, and what procedure prevents.",
            characterDevelopment = "Kira's contradiction shows: competence in public, grief in private; a relationship shifts with Bren.",
            plotDevelopment = "New information from the archive; the complication of the counter-deed; the situation changes by the end.",
            readerExperience = "The reader cares because the household depends on the outcome; the expected answer moves closer; the payoff of chapter $n delivers; the next chapter raises the stake.",
            chapterEnding = "Arises from the chapter's events; anticipation without artificial interruption."
        ).toJson()

    /** Strong scorecard JSON (plan §6.9, Table 17): 2s with one deliberate 1, total 13. */
    fun scorecardJson(n: Int): String =
        QcScorecard.toJson(QcScorecard.Scored(
            scores = listOf(2, 2, 2, 1, 2, 2, 2),
            rationales = List(7) { "Deterministic contribution confirmed on planted text (chapter $n)." }
        ))

    fun chapters(bookId: Int, releasedCount: Int = 11): List<Chapter> =
        (1..CHAPTER_COUNT).map { n ->
            val text = chapterText(n)
            val released = n <= releasedCount
            Chapter(
                bookId = bookId,
                chapterNumber = n,
                title = "Chapter $n: ${listOf(
                    "The Broken Seal", "The Urgent Margin", "Three Parishes", "The Market Confrontation",
                    "The Unclaimed Letters", "The Second Gate", "The Counter-Ledger", "The Transfer Deed",
                    "The Clerk's Drawer", "The Lower Dike", "The Long Sunday", "The Assizes Open",
                    "The Recorded Payoff", "The Bridge Meeting", "The Parish Survey"
                )[n - 1]}",
                content = text,
                summary = "Kira advances the estate dispute; the family debt and the sealed deed move one step toward the assizes.",
                status = if (released) BufferCadence.releaseMarker(releaseDate(n)) else "Completed (${OpeningValidators.wordCount(text)} words)",
                writerConfirmed = true,
                draftStatus = DraftState.CONFIRMED,
                endingType = ENDING_PLAN[n - 1],
                beatCoverage = """{"coverage":"7/7"}""",
                balanceShares = """{"Opening and continuity":12.5,"Objective, conflict, escalation":55.0,"Turning point and payoff":22.0,"Forward pull":10.5}""",
                worksheet = worksheetJson(n),
                qcScores = scorecardJson(n),
                qcTotal = 13,
                hookCheck = """{"scannedWords":${OpeningValidators.wordCount(OpeningValidators.openingSpan(text))},"sources":["URGENT_PROBLEM"]}"""
            )
        }

    /** The single complete micro-arc covering chapters 1-15 (plan §6.5). */
    fun arc(bookId: Int, questionId: Int?): SerialArc = SerialArc(
        bookId = bookId,
        arcIndex = 1,
        conflict = "The inheritance dispute over the sealed deed divides the family against the toll office.",
        questionRef = questionId,
        phaseBounds = MicroArcPlanner.phaseBoundsJson(1),
        answeredQuestionRef = questionId,
        contribution = "Reveal meaningful information: the counter-deed's copy is exposed, advancing the central story into the next arc.",
        seeds = """["Open the parish survey of the counter-deed","Rebuild trust with the toll office"]"""
    )

    /**
     * Seeds the question ledger the way the confirm flow would: detection on
     * chapter 1's planted cue, the arc's question answered in chapter 4
     * (inside its expected-answer window, so nothing is overdue).
     */
    fun seedQuestions(bookId: Int, chapters: List<Chapter>): List<QuestionEntry> {
        val ch1 = chapters.first()
        val opened = QuestionLedger.detectOpenQuestions(ch1.content, bookId, ch1.chapterNumber)
        return opened.map { q ->
            if (q.level == QuestionLedger.PROGRESSIVE) QuestionLedger.answer(q, answeredChapter = 4) else q
        }
    }

    // ------------------------------------------------------------------
    // Seeded negative variants (the other two P3 exit gates)
    // ------------------------------------------------------------------

    /**
     * The withheld-payoff project: identical to the golden serial except
     * chapter 5 (the final free chapter) strips the payoff, the
     * consequential seed, AND the chapter-payoff beat sentence — the
     * anti-withholding gate must fire EXACTLY here (plan §15 Table 34) and
     * nowhere else.
     */
    fun withheldVariant(bookId: Int): Pair<Book, List<Chapter>> {
        val book = book().copy(id = bookId)
        val chapters = chapters(bookId).map { ch ->
            if (ch.chapterNumber == 5) ch.copy(
                content = ch.content
                    .replace("By evening the ferry master answered her petition and the disputed fee was paid back at last, so the first crossing was resolved and one problem stood behind her for good.",
                        "By evening the ferry book still carried her mother's name between two lines of ink, and the master had heard the same petition twice.")
                    .replace(WITHHOLDING_PAYOFF, "The afternoon passed with ordinary paperwork and no resolution of any kind.")
                    .replace(WITHHOLDING_SEED, "She filed the letters and closed the office.")
            ) else ch
        }
        return book to chapters
    }

    /**
     * The beat-gap variant: chapter 9 loses its whole beat paragraph — the
     * coverage checker must flag the missing functions (WARN, dismissable)
     * while the suite still passes once the warnings are dismissed with a
     * note. The replacement text carries none of the seven beats' cues.
     */
    fun beatGapVariant(bookId: Int): Pair<Book, List<Chapter>> {
        val book = book().copy(id = bookId)
        val chapters = chapters(bookId).map { ch ->
            if (ch.chapterNumber == 9) ch.copy(content = ch.content.replace(
                beatParagraph(9),
                "The afternoon moved along the archive shelves at the pace the catalog allowed, and the visitor sheet filled with other names while her token stayed in her pocket."
            )) else ch
        }
        return book to chapters
    }

    /**
     * Scorecard band variants (plan §15 Table 34: bands applied correctly,
     * zero-triggers always block). Returns (chapter, writerNote) cases:
     *  - STRONG 13/14 → releasable
     *  - REASONABLE 10/14 → releasable with note on the weak criteria
     *  - REVISION 7/14 → blocked unless the writer overrides with a note
     *  - RECONSIDER 4/14 → blocked, never overridable
     *  - zero on criterion 6 (payoff) at total 12 → hard trigger, overridable
     *    only by an explicit writer note
     */
    fun scorecardBandVariants(bookId: Int): List<Triple<String, Chapter, String?>> {
        val book = book().copy(id = bookId)
        fun chapter(n: Int, scores: List<Int>, note: String? = null): Chapter =
            chapters(bookId)[n - 1].copy(
                qcScores = QcScorecard.toJson(QcScorecard.Scored(scores, List(7) { "Seeded band case." })),
                qcTotal = scores.sum(),
                ctaNote = note
            )
        return listOf(
            Triple("STRONG_13", chapter(1, listOf(2, 2, 2, 2, 2, 2, 1)), null),
            Triple("REASONABLE_10", chapter(2, listOf(2, 1, 2, 1, 2, 1, 1)), "Weak criteria: character development and payoff texture; note accompanies the release."),
            Triple("REVISION_7", chapter(3, listOf(1, 1, 1, 1, 1, 1, 1)), null),
            Triple("REVISION_7_OVERRIDDEN", chapter(4, listOf(1, 1, 1, 1, 1, 1, 1)), "Writer override: the quiet chapter is intentional; structural revision declined."),
            Triple("RECONSIDER_4", chapter(5, listOf(1, 0, 1, 0, 1, 0, 1)), "Even with a note, 0-5 blocks."),
            Triple("ZERO_PAYOFF_12", chapter(6, listOf(2, 2, 2, 2, 2, 0, 2)), null),
            Triple("ZERO_PAYOFF_12_OVERRIDDEN", chapter(7, listOf(2, 2, 2, 2, 2, 0, 2)), "Writer note: payoff deliberately moved to chapter 8; revision prompt acknowledged.")
        )
    }
}
