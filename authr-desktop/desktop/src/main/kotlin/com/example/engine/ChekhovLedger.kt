package com.example.engine

import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun

/**
 * The Chekhov continuity ledger — state machine and final-act recovery gate
 * (v3.0, plan §5.2, Table 8).
 *
 * Every promise the confirmed text makes is a ledger entry with a lifecycle:
 *
 *   INTRODUCED -> ESCALATED -> RESOLVED
 *                                \-> ORPHANED -> WAIVED (writer note required)
 *
 * L6 extracts candidate guns from confirmed text; duplicate detection on
 * name + description similarity either inserts a new gun or escalates an
 * existing one when the same promise is re-emphasized in a later canon
 * chapter. The final-act recovery gate blocks a project's compile step while
 * any confirmed gun is still unfired, so no manuscript ships with an
 * orphaned promise unless the writer explicitly waives it with a note —
 * waivers are listed in the ship report and never silent.
 */
object ChekhovLedger {

    // Table 8 states
    const val INTRODUCED = "INTRODUCED"
    const val ESCALATED = "ESCALATED"
    const val RESOLVED = "RESOLVED"
    const val ORPHANED = "ORPHANED"
    const val WAIVED = "WAIVED"

    /** Guns that still owe the reader a payoff; they block the compile gate. */
    val UNRESOLVED = setOf(INTRODUCED, ESCALATED, ORPHANED)

    // ----------------------------------------------------------------------
    // Lifecycle guards (Table 8, "Guard" column)
    // ----------------------------------------------------------------------

    /**
     * Duplicate detection on name + description similarity: two mentions of
     * the same promise are one gun. Deterministic token OVERLAP coefficient
     * over the archived context quotes (|A∩B| / min(|A|,|B|) ≥ 0.6), with
     * containment counted as equal — a re-emphasized promise shares most of
     * the original's context tokens, while a distinct promise of the same
     * detector class shares almost none.
     */
    fun quotesSimilar(a: String, b: String): Boolean {
        val na = tokens(a); val nb = tokens(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na.containsAll(nb) || nb.containsAll(na)) return true
        val inter = na.intersect(nb).size.toDouble()
        return inter / minOf(na.size, nb.size) >= 0.6
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("""[^a-z0-9']+""")).filter { it.length >= 3 }.toSet()

    /**
     * The deterministic final-act boundary: the last quarter of the book.
     * ORPHANED lands automatically when the writing crosses this line with
     * guns still unfired (Table 8, "Automatic when final act begins").
     */
    fun finalActStartChapter(totalChapters: Int): Int = ((totalChapters * 3) / 4) + 1

    // ----------------------------------------------------------------------
    // The confirm-time upsert — insert new / escalate existing / skip dupes
    // ----------------------------------------------------------------------

    sealed class Op {
        /** A brand-new promise: insert into the ledger. */
        data class Insert(val gun: ChekhovGun) : Op()
        /** The same promise re-emphasized in a later canon chapter. */
        data class Escalate(val gun: ChekhovGun, val atChapter: Int) : Op()
        /** A same-chapter duplicate of an existing entry — skipped. */
        data class SkipDuplicate(val gunId: Int, val reason: String) : Op()
    }

    /**
     * Plans the ledger updates for a freshly confirmed chapter against the
     * existing ledger. Only canon chapters produce ops (empty otherwise).
     * Duplicate detection: same detector name and a similar quote within the
     * SAME chapter is a duplicate; in a LATER chapter it is an escalation
     * candidate (Table 8, ESCALATED guard: "escalation history kept as a
     * chapter list").
     */
    fun planUpsert(existing: List<ChekhovGun>, chapter: Chapter, ast: ManuscriptAst): List<Op> {
        if (!WriterGate.isCanon(chapter)) return emptyList()
        val nodes = ast.ofKind(AnnotationKind.CHEKHOV_GUN)
        val ops = mutableListOf<Op>()
        // The working ledger grows as ops are planned, so two mentions of
        // the same promise inside ONE chapter dedupe against each other.
        val ledger = existing.toMutableList()
        for (node in nodes) {
            val name = node.payload["gun"] ?: "Promise"
            val quote = node.payload["quote"] ?: node.excerpt
            val match = ledger.firstOrNull { g ->
                g.name == name && g.status in UNRESOLVED &&
                    quotesSimilar(g.quote, quote)
            }
            when {
                match != null && chapter.chapterNumber == match.introducedChapter ->
                    ops.add(Op.SkipDuplicate(match.id, "same promise extracted twice from chapter ${chapter.chapterNumber}"))
                match != null && chapter.chapterNumber > match.introducedChapter -> {
                    ops.add(Op.Escalate(match, chapter.chapterNumber))
                }
                match == null -> {
                    val gun = ChekhovGun(
                        bookId = chapter.bookId,
                        name = name,
                        description = node.message,
                        status = INTRODUCED,
                        introducedChapter = chapter.chapterNumber,
                        quote = quote,
                        severity = "normal"
                    )
                    ops.add(Op.Insert(gun))
                    ledger.add(gun.copy(id = Int.MAX_VALUE)) // placeholder id for same-call dedupe
                }
                else -> ops.add(Op.SkipDuplicate(match.id, "promise already traced to chapter ${match.introducedChapter}"))
            }
        }
        return ops
    }

    /** Applies an [Op.Escalate] with the Table 8 guard (later canon chapter). */
    fun escalate(gun: ChekhovGun, atChapter: Int): ChekhovGun? {
        if (gun.status !in setOf(INTRODUCED, ESCALATED)) return null
        if (atChapter <= gun.introducedChapter) return null
        val history = runCatching {
            jsonChapterList(gun.escalationChapters)
        }.getOrDefault(emptyList())
        val updated = history + atChapter
        return gun.copy(
            status = ESCALATED,
            escalationChapters = jsonChapterList(updated)
        )
    }

    /**
     * Marks the payoff (Table 8, RESOLVED guard: "Resolution must occur in
     * confirmed text; a quote is archived with the entry"). Returns null
     * when the chapter is not canon or the proof quote is missing.
     */
    fun resolve(gun: ChekhovGun, chapter: Chapter, quote: String): ChekhovGun? {
        if (gun.status !in UNRESOLVED) return null
        if (!WriterGate.isCanon(chapter)) return null
        if (quote.isBlank()) return null
        return gun.copy(status = RESOLVED, resolvedChapter = chapter.chapterNumber, quote = quote)
    }

    /**
     * Writer accepts the orphan deliberately (Table 8, WAIVED guard: a
     * mandatory note; waivers are listed in the ship report, never silent).
     * Returns null when the note is blank — a waiver is never silent.
     */
    fun waive(gun: ChekhovGun, note: String): ChekhovGun? {
        if (gun.status !in UNRESOLVED) return null
        if (note.isBlank()) return null
        return gun.copy(status = WAIVED, waiveNote = note.trim())
    }

    /**
     * The automatic ORPHANED sweep (Table 8): once the writing has crossed
     * the final-act boundary, every confirmed gun still unfired becomes an
     * orphan so the recovery gate can see it.
     */
    fun orphanSweep(guns: List<ChekhovGun>, currentChapter: Int, finalActStart: Int): List<ChekhovGun> =
        if (currentChapter < finalActStart) guns
        else guns.map { g ->
            if (g.status in setOf(INTRODUCED, ESCALATED) &&
                g.introducedChapter < finalActStart
            ) g.copy(status = ORPHANED) else g
        }

    // ----------------------------------------------------------------------
    // The final-act recovery gate — compile is blocked while guns are unfired
    // ----------------------------------------------------------------------

    /**
     * The gate verdict for a compile step (plan §5.2: "the final-act
     * recovery gate blocks a project's compile step while any confirmed gun
     * is still unfired, so no manuscript ships with an orphaned promise
     * unless the writer explicitly waives it with a note").
     */
    data class GateResult(
        val blocked: Boolean,
        /** Unresolved guns holding the gate (the writer must resolve or waive). */
        val blocking: List<ChekhovGun>,
        /** Waived guns — listed in the ship report, never silent. */
        val waivers: List<ChekhovGun>,
        /** The report lines the gate emits (blocking + waiver disclosures). */
        val report: List<String>
    ) {
        val waiverCount: Int get() = waivers.size
    }

    fun recoveryGate(guns: List<ChekhovGun>): GateResult {
        val blocking = guns.filter { it.status in UNRESOLVED }
        val waivers = guns.filter { it.status == WAIVED }
        val report = buildList {
            blocking.forEach { g ->
                add("BLOCKED by ${g.status} gun \"${g.name}\" (introduced ch. ${g.introducedChapter}, status ${g.status}) — resolve it in the Writer Studio or waive it with a note.")
            }
            waivers.forEach { g ->
                add("Waived gun \"${g.name}\" (introduced ch. ${g.introducedChapter}): \u201C${g.waiveNote}\u201D — disclosed in the ship report.")
            }
        }
        return GateResult(blocked = blocking.isNotEmpty(), blocking = blocking, waivers = waivers, report = report)
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------

    private fun jsonChapterList(chapters: List<Int>): String =
        chapters.distinct().sorted().joinToString(",", "[", "]")

    private fun jsonChapterList(json: String): List<Int> =
        json.removePrefix("[").removeSuffix("]")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
}
