package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.serialization.BufferCadence
import com.example.engine.serialization.GoldenSerial
import kotlinx.coroutines.test.runTest
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Buffer and cadence tooling (v3.0 P3, plan §6.10, Table 19): the buffer
 * meter is green at 8+, amber 4-7, red below 4 over confirmed-but-unreleased
 * chapters; the cadence tracker validates released chapters against the
 * release schedule with streak and slip warnings. Release state rides the
 * v2.3 free-text chapter status ("Released (ISO date)") — the serialization
 * field set adds no released column.
 */
class BufferCadenceTest {

    private fun book(freeTier: Int = 5, bufferTarget: Int = 8, schedule: String = "Mon/Wed/Fri") =
        Book(id = 51, title = "Serial", genre = "g", synopsis = "s", outline = "o",
            isSerialized = true, freeTierChapters = freeTier, bufferTarget = bufferTarget, releaseSchedule = schedule)

    private fun canon(n: Int, released: LocalDate? = null, bookId: Int = 51) =
        Chapter(bookId = bookId, chapterNumber = n, title = "c$n", content = "text", summary = "s",
            writerConfirmed = true, draftStatus = DraftState.CONFIRMED,
            status = released?.let { BufferCadence.releaseMarker(it) } ?: "Completed (50 words)")

    @Test
    fun `buffer levels follow the 8-4 standard`() {
        val b = book()
        assertEquals(BufferCadence.BufferLevel.GREEN, BufferCadence.buffer(b, (1..8).map { canon(it) }).level)
        assertEquals(BufferCadence.BufferLevel.AMBER, BufferCadence.buffer(b, (1..7).map { canon(it) }).level)
        assertEquals(BufferCadence.BufferLevel.AMBER, BufferCadence.buffer(b, (1..4).map { canon(it) }).level)
        assertEquals(BufferCadence.BufferLevel.RED, BufferCadence.buffer(b, (1..3).map { canon(it) }).level)
        assertEquals(BufferCadence.BufferLevel.RED, BufferCadence.buffer(b, emptyList()).level)
        val report = BufferCadence.buffer(b, (1..9).map { canon(it) })
        assertTrue(report.message.contains("8-chapter standard"))
    }

    @Test
    fun `proposed and archived chapters never enter the buffer`() {
        val proposed = Chapter(bookId = 51, chapterNumber = 1, title = "t", content = "x", summary = "s",
            writerConfirmed = false, draftStatus = DraftState.PROPOSED)
        val archived = proposed.copy(chapterNumber = 2, draftStatus = DraftState.ARCHIVED)
        val report = BufferCadence.buffer(book(), listOf(proposed, archived))
        assertEquals(0, report.confirmedUnreleased, "the buffer counts canon only")
    }

    @Test
    fun `released chapters are counted by their status marker`() {
        val ch = canon(1, LocalDate.parse("2025-01-06"))
        assertTrue(BufferCadence.isReleased(ch))
        assertEquals(LocalDate.parse("2025-01-06"), BufferCadence.releaseDate(ch))
        assertNull(BufferCadence.releaseDate(canon(2)))
        assertNull(BufferCadence.releaseDate(ch.copy(status = "Released without a date")))
        assertNull(BufferCadence.releaseDate(ch.copy(status = "Released (not-a-date)")))
    }

    @Test
    fun `the golden serial's release dates all land on Mon-Wed-Fri`() {
        (1..GoldenSerial.CHAPTER_COUNT).forEach { n ->
            val date = GoldenSerial.releaseDate(n)
            assertTrue(date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), "chapter $n releases on $date (${date.dayOfWeek})")
        }
        assertTrue(GoldenSerial.releaseDate(1) < GoldenSerial.releaseDate(2), "dates advance")
    }

    @Test
    fun `the cadence tracker passes the golden serial's schedule and flags off-schedule releases`() {
        val b = book()
        val goldenChapters = GoldenSerial.chapters(52)
        val clean = BufferCadence.cadence(b, goldenChapters)
        assertEquals(emptyList(), clean.slips, clean.message)
        assertEquals(11, clean.released.size)
        assertTrue(clean.streak >= 1, "streak counts consecutive on-schedule releases")

        val slipped = BufferCadence.cadence(b, goldenChapters.take(11) + canon(12, LocalDate.parse("2025-01-11"))) // a Saturday
        assertTrue(slipped.slips.any { it.contains("outside the Mon/Wed/Fri schedule") }, slipped.slips.joinToString())

        val gapped = BufferCadence.cadence(b, goldenChapters.take(11) + canon(12, LocalDate.parse("2025-03-01")))
        assertTrue(gapped.slips.any { it.contains("cadence slipped") }, "a multi-week gap slips")
    }

    @Test
    fun `schedule parsing handles the known day tokens`() {
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), BufferCadence.parseSchedule("Mon/Wed/Fri"))
        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), BufferCadence.parseSchedule("Tue/Thu/Sat/Sun"))
        assertTrue(BufferCadence.parseSchedule("").isEmpty())
        assertTrue(BufferCadence.parseSchedule("Whenever").isEmpty())
    }

    @Test
    fun `non-serialized or retail projects still report buffers locally`() = runTest {
        // The tooling is local, platform-independent, and needs no API (§6.10).
        val report = BufferCadence.buffer(book().copy(isSerialized = false), (1..6).map { canon(it) })
        assertEquals(6, report.confirmedUnreleased, "the meter is a pure function of the chapters")
    }
}
