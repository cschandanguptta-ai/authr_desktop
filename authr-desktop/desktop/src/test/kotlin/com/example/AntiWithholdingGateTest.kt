package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.OpeningValidators
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P3 exit gate 2 (plan §13 Table 33, §15 Table 34): "the anti-withholding
 * gate fires correctly on a seeded free tier — it fires exactly on the
 * seeded withheld-payoff project."
 *
 * The gate (plan §6.2, Table 9 last row) is the INVERSE of the 1.0
 * paywall-cliffhanger rule Blueprint 2.0 replaces: the final free chapter
 * must contain a confirmed payoff beat resolving part of the opening
 * conflict PLUS the seed of a more consequential development. It is
 * chapter-count based — it binds to Book.freeTierChapters and runs before
 * export (the P2 handoff note in Table 33).
 */
class AntiWithholdingGateTest {

    @Test
    fun `the golden serial passes the gate`() {
        val book = GoldenSerial.book()
        val verdict = OpeningValidators.antiWithholdingGate(book, GoldenSerial.chapters(21))
        assertTrue(verdict.gated, "freeTierChapters=5 gates the project")
        assertEquals(5, verdict.finalFreeChapter)
        assertTrue(verdict.payoffPresent, "chapter 5 plants the payoff resolving part of the opening conflict")
        assertTrue(verdict.consequentialSeedPresent, "chapter 5 plants the consequential seed")
        assertTrue(verdict.passes)
    }

    @Test
    fun `the gate fires exactly on the seeded withheld-payoff project`() {
        val (book, chapters) = GoldenSerial.withheldVariant(22)
        val verdict = OpeningValidators.antiWithholdingGate(book, chapters)
        assertTrue(verdict.gated)
        assertEquals(5, verdict.finalFreeChapter)
        assertFalse(verdict.payoffPresent, "the seeded project strips the payoff")
        assertFalse(verdict.consequentialSeedPresent, "the seeded project strips the consequential seed")
        assertFalse(verdict.passes, "the gate fires on the seeded withheld-payoff project")
        // And ONLY on that project: the golden serial itself stays green.
        val golden = OpeningValidators.antiWithholdingGate(GoldenSerial.book(), GoldenSerial.chapters(23))
        assertTrue(golden.passes)
    }

    @Test
    fun `a payoff without a consequential seed still fails — both halves are required`() {
        val book = GoldenSerial.book()
        val chapters = GoldenSerial.chapters(24).map {
            if (it.chapterNumber == 5) it.copy(content = it.content.replace(
                "But the far worse matter of who had sealed the original deed was only the beginning: the archive yielded a greater threat than the fine, and what she didn't know about the counter-deed would outweigh everything the payoff just gave her.",
                "The afternoon closed quietly over the toll office roof."
            )) else it
        }
        val verdict = OpeningValidators.antiWithholdingGate(book, chapters)
        assertTrue(verdict.payoffPresent, "the payoff half is intact")
        assertFalse(verdict.consequentialSeedPresent, "the seed half is stripped")
        assertFalse(verdict.passes, "resolving the conflict alone is not enough — the next chapter must promise genuinely valuable continuation")
    }

    @Test
    fun `retail-only books without a free tier are not gated`() {
        val book = GoldenSerial.book().copy(id = 25, freeTierChapters = 0)
        val verdict = OpeningValidators.antiWithholdingGate(book, GoldenSerial.chapters(25))
        assertFalse(verdict.gated)
        assertTrue(verdict.passes, "freeTierChapters=0 means no paywall boundary — the gate does not apply")
    }

    @Test
    fun `non-serialized projects are never gated`() {
        val book = Book(id = 26, title = "Novel", genre = "Drama", synopsis = "s", outline = "o", isSerialized = false, freeTierChapters = 5)
        val chapters = listOf(Chapter(bookId = 26, chapterNumber = 1, title = "c1", content = "plain text", summary = "s"))
        val verdict = OpeningValidators.antiWithholdingGate(book, chapters)
        assertFalse(verdict.gated)
        assertTrue(verdict.passes)
    }

    @Test
    fun `the gate binds to the free tier size, not chapter 5`() {
        // A free tier of 3 moves the boundary to chapter 3; the planted
        // payoff lives in chapter 5, so the gate fires on chapter 3.
        val book = GoldenSerial.book().copy(id = 27, freeTierChapters = 3)
        val verdict = OpeningValidators.antiWithholdingGate(book, GoldenSerial.chapters(27))
        assertEquals(3, verdict.finalFreeChapter)
        assertFalse(verdict.passes, "chapter 3 carries no planted payoff — the gate binds to the configured tier")
    }

    @Test
    fun `the seeded withheld variant differs from the golden serial only in chapter 5`() = runTest {
        val (_, withheld) = GoldenSerial.withheldVariant(28)
        val golden = GoldenSerial.chapters(28)
        withheld.zip(golden).forEach { (w, g) ->
            if (w.chapterNumber == 5) {
                assertFalse(w.content == g.content, "chapter 5 is the seeded change")
            } else {
                assertEquals(g.content, w.content, "all other chapters are untouched")
            }
        }
    }
}
