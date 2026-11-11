package com.example

import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.SerialArc
import com.example.engine.DraftState
import com.example.engine.ChekhovLedger
import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.RetailPackager
import com.example.desktopbridge.EpubCheckLocator
import org.apache.pdfbox.pdmodel.PDDocument
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The retail packager end-to-end (v3.0 P5 exit gate shape, plan §13):
 * a complete project produces the full retail package in one action —
 * canon gate → EPUB(s) → epubcheck → print interior → pricing → ship
 * report. The epubcheck integration test runs when the tool is present
 * (it is, in the gate sandbox; elsewhere it self-skips with a disclosure).
 */
class RetailPackagerTest {

    private fun chapter(n: Int, canon: Boolean = true) =
        Chapter(
            bookId = 1,
            chapterNumber = n,
            title = "Chapter $n Title",
            content = "Opening of $n.\n\n" + (1..350).joinToString(" ") { i -> "word${(i * n) % 733}" } + "\n\nClosing of $n.",
            summary = "s",
            writerConfirmed = canon,
            draftStatus = if (canon) DraftState.CONFIRMED else DraftState.PROPOSED
        )

    /** The fully edited workbench most tests package with. */
    private fun fullWorkbench() = KdpWorkbench(
        title = "The Iron Vale",
        subtitle = "A mining ballad",
        author = "A. Writer",
        description = "A blurb.",
        keywords = List(7) { "kw$it" },
        isbn = "978-3-16-148410-0",
        publisher = "Iron Vale Press",
        copyrightLine = "Copyright © 2026 A. Writer",
        listPriceUsd = 6.99
    )

    /** workbench = null exercises the derive-from-project-record path (OQ-9). */
    private fun novelInput(chapters: Int = 4, workbench: KdpWorkbench? = fullWorkbench()) = RetailPackager.Input(
        bookTitle = "The Iron Vale",
        author = "A. Writer",
        isSerial = false,
        chapters = (1..chapters).map { chapter(it) },
        workbench = workbench
    )

    @Test
    fun `one action produces epub print metadata and ship report for a novel`() {
        val out = createTempDir("retail")
        val r = RetailPackager.packageRetail(novelInput(), out)

        assertTrue(File(out, "epub/The_Iron_Vale.epub").length() > 0)
        assertTrue(File(out, "print/interior.pdf").length() > 0)
        assertTrue(File(out, "metadata.json").length() > 0)
        assertTrue(r.shipReport.length() > 0)
        assertEquals(4, r.epubResults[0].chapterCount)
        assertTrue(r.printResult!!.gutterCompliant)

        // print interior is a real 6×9 PDF
        PDDocument.load(File(out, "print/interior.pdf")).use { doc ->
            assertEquals(432.0, doc.getPage(0).mediaBox.width.toDouble(), 0.01)
        }
        out.deleteRecursively()
    }

    @Test
    fun `unconfirmed chapters are excluded from every artifact and disclosed`() {
        val out = createTempDir("retail")
        val chapters = listOf(chapter(1), chapter(2, canon = false), chapter(3))
        val r = RetailPackager.packageRetail(novelInput().copy(chapters = chapters), out)
        assertEquals(2, r.epubResults[0].chapterCount)
        assertTrue(r.disclosures.any { it.contains("excluded 1 unconfirmed chapter") })
        // the ship report carries the disclosure
        assertTrue(r.shipReport.readText().contains("excluded 1 unconfirmed chapter"))
        out.deleteRecursively()
    }

    @Test
    fun `serial project bundles volume epubs per arc`() {
        val out = createTempDir("retail")
        val arcs = listOf(
            SerialArc(id = 101, bookId = 1, arcIndex = 1, conflict = "The toll bridge dispute", phaseBounds = "{}"),
            SerialArc(id = 102, bookId = 1, arcIndex = 2, conflict = "The sealed shaft", phaseBounds = "{}")
        )
        val chapters = (1..6).map { n ->
            chapter(n).copy(arcId = if (n <= 3) 101 else 102)
        }
        val r = RetailPackager.packageRetail(
            novelInput(chapters = 1).copy(isSerial = true, chapters = chapters, arcs = arcs),
            out
        )
        assertTrue(File(out, "epub/volume_01.epub").length() > 0)
        assertTrue(File(out, "epub/volume_02.epub").length() > 0)
        assertEquals(2, r.epubResults.size)
        assertEquals(3, r.epubResults[0].chapterCount)
        assertTrue(r.disclosures.any { it.contains("2 volume(s)") })
        out.deleteRecursively()
    }

    @Test
    fun `workbench authoritative - unedited workbench discloses derivation`() {
        val out = createTempDir("retail")
        val r = RetailPackager.packageRetail(novelInput(workbench = null), out)
        assertTrue(r.disclosures.any { it.contains("Workbench never edited") })
        // the metadata bundle embeds the workbench
        val meta = File(out, "metadata.json").readText()
        assertTrue(meta.contains("retail_metadata/1"))
        assertTrue(meta.contains("The Iron Vale"))
        out.deleteRecursively()
    }

    @Test
    fun `chekov waivers land in the ship report`() {
        val out = createTempDir("retail")
        val guns = listOf(
            ChekhovGun(id = 1, bookId = 1, name = "The sealed door", introducedChapter = 3,
                status = ChekhovLedger.WAIVED, waiveNote = "Sequel hook, deliberate")
        )
        val r = RetailPackager.packageRetail(novelInput().copy(guns = guns), out)
        val md = r.shipReport.readText()
        assertTrue(md.contains("The sealed door"))
        assertTrue(md.contains("Sequel hook, deliberate"))
        out.deleteRecursively()
    }

    @Test
    fun `pricing record quotes both brackets`() {
        val out = createTempDir("retail")
        val r = RetailPackager.packageRetail(novelInput(), out)
        val md = r.shipReport.readText()
        assertTrue(md.contains("eBook SEVENTY"))
        assertTrue(md.contains("Paperback:"))
        assertTrue(md.contains("breakeven list"))
        assertTrue(r.ebookQuote != null)
        assertTrue(r.printQuote != null)
        out.deleteRecursively()
    }

    @Test
    fun `epubcheck verdict is verified when the tool runs - zero errors`() {
        // integration: the real external gate (skips where epubcheck is absent)
        assumeTrue("epubcheck dist not present", EpubCheckLocator.isAvailable())
        val out = createTempDir("retail")
        val r = RetailPackager.packageRetail(novelInput(), out)
        val epubVerdicts = r.gateVerdicts.filter { it.gate.contains("epubcheck") }
        assertEquals(1, epubVerdicts.size)
        assertTrue(epubVerdicts[0].passed, "expected zero errors: ${epubVerdicts[0].detail}")
        assertEquals("VERIFIED", epubVerdicts[0].status)
        assertTrue(r.clean, "package must be clean: " + r.gateVerdicts.filter { !it.passed }.joinToString { it.detail })
        out.deleteRecursively()
    }

    @Test
    fun `epubcheck failures block the clean verdict`() {
        // a deliberately broken EPUB: rename a chapter inside the zip so the
        // manifest href dangles — epubcheck must flag it, the packager must
        // mark the package NOT clean
        assumeTrue("epubcheck dist not present", EpubCheckLocator.isAvailable())
        val out = createTempDir("retail")
        val r = RetailPackager.packageRetail(novelInput(), out)
        val broken = File(out, "broken.epub")
        java.util.zip.ZipFile(r.epubResults[0].file).use { zf ->
            java.util.zip.ZipOutputStream(broken.outputStream()).use { zos ->
                zf.entries().toList().forEach { e ->
                    if (e.name == "OEBPS/text/ch001.xhtml") return@forEach // drop one spine file
                    zos.putNextEntry(java.util.zip.ZipEntry(e.name))
                    if (!e.isDirectory) zf.getInputStream(e).copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        val verdict = RetailPackager.runEpubCheck(
            com.example.engine.publishing.Epub3Exporter.Result(
                file = broken, chapterCount = 4, sizeBytes = broken.length(), identifier = "urn:uuid:x"
            )
        )
        assertFalse(verdict.passed)
        assertEquals("FAILED", verdict.status)
        out.deleteRecursively()
    }

    @Test
    fun `serial volume epubs also pass the external gate`() {
        assumeTrue("epubcheck dist not present", EpubCheckLocator.isAvailable())
        val out = createTempDir("retail")
        val arcs = listOf(
            SerialArc(id = 201, bookId = 1, arcIndex = 1, conflict = "Arc one", phaseBounds = "{}")
        )
        val chapters = (1..3).map { n -> chapter(n).copy(arcId = 201) }
        val r = RetailPackager.packageRetail(
            novelInput(chapters = 1).copy(isSerial = true, chapters = chapters, arcs = arcs), out
        )
        val epubVerdicts = r.gateVerdicts.filter { it.gate.contains("epubcheck") }
        assertEquals(1, epubVerdicts.size)
        assertTrue(epubVerdicts[0].passed, epubVerdicts[0].detail)
        out.deleteRecursively()
    }
}
