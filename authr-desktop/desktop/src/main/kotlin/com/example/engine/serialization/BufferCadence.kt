package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Retention through publication — buffer and cadence tooling (v3.0, plan
 * §6.10, Table 19).
 *
 * Sustainable cadence, a real buffer, and community engagement the author
 * controls. None of it depends on platform APIs; all of it is local tooling.
 *
 * Release state is carried on the v2.3 free-text [Chapter.status] field as a
 * "Released (ISO date)" prefix — the serialization field set (plan Table 21)
 * adds no released column, so the ported schema stays untouched. The buffer
 * meter counts confirmed-but-unreleased chapters; the cadence tracker
 * validates released chapters against [Book.releaseSchedule].
 */
object BufferCadence {

    /** The release marker prefix written into chapter status by the dashboard's Release action. */
    const val RELEASED_PREFIX = "Released"

    /** Marker factory: "Released (2026-11-09)". */
    fun releaseMarker(date: LocalDate): String = "$RELEASED_PREFIX ($date)"

    /** Parses a chapter's release date; null when not released or unparseable. */
    fun releaseDate(chapter: Chapter): LocalDate? {
        if (!chapter.status.startsWith(RELEASED_PREFIX)) return null
        val m = Regex("""\((\d{4}-\d{2}-\d{2})\)""").find(chapter.status) ?: return null
        return runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()
    }

    fun isReleased(chapter: Chapter): Boolean = releaseDate(chapter) != null

    // ------------------------------------------------------------------
    // Buffer meter (Table 19: green at 8+, amber 4-7, red below 4)
    // ------------------------------------------------------------------

    enum class BufferLevel(val label: String) { GREEN("green"), AMBER("amber"), RED("red") }

    data class BufferReport(
        val confirmedUnreleased: Int,
        val target: Int,
        val level: BufferLevel,
        val message: String
    )

    /**
     * The buffer meter over confirmed-but-unreleased chapters (Table 19):
     * green at 8+, amber 4-7, red below 4. The Autonomous Studio accepts a
     * "write to buffer" target; the run pauses at the configured level.
     */
    fun buffer(book: Book, chapters: List<Chapter>): BufferReport {
        val canon = chapters.filter { com.example.engine.WriterGate.isCanon(it) }
        val unreleased = canon.count { !isReleased(it) }
        val target = book.bufferTarget.coerceIn(4, 8)
        val level = when {
            unreleased >= 8 -> BufferLevel.GREEN
            unreleased >= 4 -> BufferLevel.AMBER
            else -> BufferLevel.RED
        }
        val message = "Buffer: $unreleased confirmed-but-unreleased chapter(s) against target $target — $level" +
            when (level) {
                BufferLevel.GREEN -> " (at or above the 8-chapter standard)."
                BufferLevel.AMBER -> " (inside the 4-7 range; the reserve protects the schedule during illness and editing delays)."
                BufferLevel.RED -> " (below the 4-chapter minimum — consider a 'write to buffer' run before releasing more)."
            }
        return BufferReport(unreleased, target, level, message)
    }

    // ------------------------------------------------------------------
    // Cadence tracker (Table 19: consistency with the communicated schedule)
    // ------------------------------------------------------------------

    data class CadenceReport(
        val scheduleDays: List<DayOfWeek>,
        val released: List<Pair<Int, LocalDate>>,
        val streak: Int,
        val slips: List<String>,
        val message: String
    )

    /** Parses "Mon/Wed/Fri" into days of week; unknown tokens are ignored. */
    fun parseSchedule(schedule: String): List<DayOfWeek> =
        schedule.split("/").mapNotNull { token ->
            val t = token.trim().lowercase()
            when {
                t.startsWith("mon") -> DayOfWeek.MONDAY
                t.startsWith("tue") -> DayOfWeek.TUESDAY
                t.startsWith("wed") -> DayOfWeek.WEDNESDAY
                t.startsWith("thu") -> DayOfWeek.THURSDAY
                t.startsWith("fri") -> DayOfWeek.FRIDAY
                t.startsWith("sat") -> DayOfWeek.SATURDAY
                t.startsWith("sun") -> DayOfWeek.SUNDAY
                else -> null
            }
        }

    /**
     * Validates released chapters against the release schedule (Table 19):
     * consistency is the requirement, not daily frequency. A slip is a
     * released chapter whose date does not fall on a scheduled day, or a gap
     * longer than the schedule's period between consecutive releases.
     * Streak counts consecutive scheduled-day releases ending at the latest.
     */
    fun cadence(book: Book, chapters: List<Chapter>): CadenceReport {
        val days = parseSchedule(book.releaseSchedule)
        val released = chapters.filter { com.example.engine.WriterGate.isCanon(it) && isReleased(it) }
            .sortedBy { it.chapterNumber }
            .map { it.chapterNumber to (releaseDate(it)!!) }
        val slips = mutableListOf<String>()
        if (days.isNotEmpty()) {
            released.forEach { (num, date) ->
                if (date.dayOfWeek !in days) slips.add(
                    "Chapter $num released on $date (${date.dayOfWeek}) — outside the ${book.releaseSchedule} schedule."
                )
            }
            released.zipWithNext().forEach { (a, b) ->
                val gapDays = java.time.temporal.ChronoUnit.DAYS.between(a.second, b.second)
                val maxGap = 7L // any weekly schedule must show progress at least weekly
                if (gapDays > maxGap) slips.add(
                    "Gap of $gapDays days between chapter ${a.first} and chapter ${b.first} — cadence slipped."
                )
            }
        }
        // Streak: consecutive scheduled-day releases counting back from the latest.
        var streak = 0
        for ((_, date) in released.reversed()) {
            if (days.isEmpty() || date.dayOfWeek in days) streak++ else break
        }
        return CadenceReport(
            scheduleDays = days,
            released = released,
            streak = streak,
            slips = slips,
            message = "Cadence: ${released.size} released against \"${book.releaseSchedule}\" — streak $streak" +
                (if (slips.isEmpty()) "." else "; ${slips.size} slip(s).")
        )
    }
}
