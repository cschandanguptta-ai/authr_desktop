package com.example

import com.example.data.model.ChekhovGun
import com.example.engine.ChekhovLedger
import com.example.engine.publishing.ShipReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ship report (v3.0 P5, plan §15): artifacts + gate verdicts + the
 * continuity disclosures (Chekhov waivers with mandatory notes, scorecard
 * notes, cadence) + the pricing record. "If the report is clean, the export
 * behind it is defensible."
 */
class ShipReportTest {

    private fun verdict(artifact: String, passed: Boolean, status: String = "VERIFIED") =
        ShipReport.GateVerdict(artifact, "gate", passed, status, "detail")

    private fun input(verdicts: List<ShipReport.GateVerdict>) = ShipReport.Input(
        bookTitle = "The Iron Vale",
        author = "A. Writer",
        generatedAtIso = "2026-11-11T00:00:00Z",
        generatorVersion = "3.0.0-alpha",
        artifacts = listOf(ShipReport.Artifact("epub/book.epub", "EPUB 3", 1000)),
        gateVerdicts = verdicts,
        chekhovWaivers = listOf(ShipReport.WaiverLine("The sealed door", 3, "Deliberate ambiguity — sequel hook")),
        scorecardNotes = listOf("ch. 4 scorecard note-only override: pace waiver"),
        cadenceLines = listOf("Buffer: 4 chapters (amber)"),
        pricingLines = listOf("eBook SEVENTY: list $4.99 → royalty $3.34/unit"),
        oneActionSummary = "one action"
    )

    @Test
    fun `clean only when every verdict passed`() {
        assertTrue(ShipReport.isClean(listOf(verdict("a", true))))
        assertFalse(ShipReport.isClean(listOf(verdict("a", true), verdict("b", false, "FAILED"))))
        // UNVERIFIED is NOT clean — the zero-errors precondition is unproven
        assertFalse(ShipReport.isClean(listOf(verdict("a", false, "UNVERIFIED"))))
    }

    @Test
    fun `report renders verdicts artifacts and package verdict`() {
        val md = ShipReport.render(input(listOf(verdict("book.epub", true), verdict("interior.pdf", true))))
        assertTrue(md.contains("# Ship Report — The Iron Vale"))
        assertTrue(md.contains("| book.epub | gate | GREEN | VERIFIED | detail |"))
        assertTrue(md.contains("**Package verdict: CLEAN — the export behind this report is defensible**"))
        assertTrue(md.contains("KDP Print Previewer"))
    }

    @Test
    fun `red verdicts make the package not clean and say so`() {
        val md = ShipReport.render(input(listOf(verdict("book.epub", false, "FAILED"))))
        assertTrue(md.contains("NOT CLEAN — fix the red gates before upload"))
        assertTrue(md.contains("| book.epub | gate | RED | FAILED | detail |"))
    }

    @Test
    fun `chekov waivers are disclosed with their notes never silent`() {
        val md = ShipReport.render(input(listOf(verdict("a", true))))
        assertTrue(md.contains("### Chekhov waivers"))
        assertTrue(md.contains("The sealed door"))
        assertTrue(md.contains("ch. 3"))
        assertTrue(md.contains("Deliberate ambiguity — sequel hook"))
    }

    @Test
    fun `empty waiver list says none`() {
        val md = ShipReport.render(input(listOf(verdict("a", true))).let {
            it.copy(chekhovWaivers = emptyList())
        })
        assertTrue(md.contains("None — no guns were waived."))
    }

    @Test
    fun `waiver lines derive from waived guns with mandatory notes`() {
        val guns = listOf(
            ChekhovGun(id = 1, bookId = 1, name = "Gun A", introducedChapter = 2, status = ChekhovLedger.WAIVED, waiveNote = "writer note"),
            ChekhovGun(id = 2, bookId = 1, name = "Gun B", introducedChapter = 4, status = ChekhovLedger.RESOLVED, resolvedChapter = 9),
            ChekhovGun(id = 3, bookId = 1, name = "Gun C", introducedChapter = 5, status = ChekhovLedger.WAIVED, waiveNote = "")
        )
        val lines = ShipReport.waiverLines(guns)
        assertEquals(2, lines.size)
        assertEquals("Gun A", lines[0].gunName)
        assertEquals("writer note", lines[0].note)
        assertEquals("(no note recorded)", lines[1].note)
    }

    @Test
    fun `serial disclosures sections render`() {
        val md = ShipReport.render(input(listOf(verdict("a", true))))
        assertTrue(md.contains("Buffer: 4 chapters (amber)"))
        assertTrue(md.contains("pace waiver"))
        assertTrue(md.contains("eBook SEVENTY"))
    }
}
