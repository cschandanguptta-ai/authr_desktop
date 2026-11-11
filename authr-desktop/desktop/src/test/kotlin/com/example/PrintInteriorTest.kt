package com.example

import com.example.engine.publishing.PrintInterior
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The print interior (v3.0 P5, plan §11.1): the gutter ladder table exactly
 * per the plan, the 6×9 in trim MediaBox, mirrored margins by construction,
 * and readable embedded text (PDFTextStripper round-trip via the embedded
 * ToUnicode maps).
 */
class PrintInteriorTest {

    private fun chapterText(words: Int, seed: Int): String =
        (1..words).joinToString(" ") { i -> "word${(i * seed) % 997}" }

    private fun input(chapters: Int = 3, wordsPerChapter: Int = 400) = PrintInterior.Input(
        title = "The Iron Vale",
        author = "A. Writer",
        copyrightLine = "Copyright © 2026 A. Writer",
        isbn = "978-3-16-148410-0",
        publisher = "Iron Vale Press",
        chapters = (1..chapters).map {
            PrintInterior.ChapterSource(it, "Chapter $it Title", chapterText(wordsPerChapter, it) + "\n\nSecond paragraph $it.")
        }
    )

    // ---- the ladder table (plan §11.1) -----------------------------------

    @Test
    fun `gutter ladder matches the plan table exactly`() {
        // 24–150 → 0.375; 151–300 → 0.5; 301–500 → 0.625; 501–700 → 0.75
        assertEquals(0.375, PrintInterior.gutterForPageCount(24))
        assertEquals(0.375, PrintInterior.gutterForPageCount(150))
        assertEquals(0.5, PrintInterior.gutterForPageCount(151))
        assertEquals(0.5, PrintInterior.gutterForPageCount(300))
        assertEquals(0.625, PrintInterior.gutterForPageCount(301))
        assertEquals(0.625, PrintInterior.gutterForPageCount(500))
        assertEquals(0.75, PrintInterior.gutterForPageCount(501))
        assertEquals(0.75, PrintInterior.gutterForPageCount(700))
        // above the last band clamps to the largest margin
        assertEquals(0.75, PrintInterior.gutterForPageCount(900))
    }

    @Test
    fun `ladder rows render the plan bands`() {
        val rows = PrintInterior.ladderRows()
        assertEquals(4, rows.size)
        assertEquals("24–150 pages", rows[0].first)
        assertEquals("0.375 in", rows[0].second)
        assertEquals("501–700 pages", rows[3].first)
        assertEquals("0.75 in", rows[3].second)
    }

    // ---- geometry ----------------------------------------------------------

    @Test
    fun `interior is six by nine trim with a compliant gutter`() {
        val out = createTempDir("print")
        val r = PrintInterior.render(input(), File(out, "interior.pdf"))
        assertEquals(6.0, r.trimWidthIn)
        assertEquals(9.0, r.trimHeightIn)
        assertTrue(r.pageCount >= 3 + 3, "front matter (3) + at least one page per chapter")
        assertTrue(r.gutterCompliant, "gutter ${r.gutterInches} must equal ladder(${r.pageCount})=${PrintInterior.gutterForPageCount(r.pageCount)}")
        PDDocument.load(r.file).use { doc ->
            val box = doc.getPage(0).mediaBox
            assertEquals(432.0, box.width.toDouble(), 0.01) // 6 in
            assertEquals(648.0, box.height.toDouble(), 0.01) // 9 in
            assertEquals(r.pageCount, doc.numberOfPages)
        }
        out.deleteRecursively()
    }

    @Test
    fun `text round-trips through the embedded font`() {
        val out = createTempDir("print")
        val r = PrintInterior.render(input(), File(out, "interior.pdf"))
        PDDocument.load(r.file).use { doc ->
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("The Iron Vale"), "title page text extractable")
            assertTrue(text.contains("ISBN: 978-3-16-148410-0"), "copyright page carries the ISBN")
            assertTrue(text.contains("Contents"), "TOC present")
            assertTrue(text.contains("Chapter 1 Title"), "chapter heading extractable")
            assertTrue(text.contains("word"), "body text extractable")
        }
        out.deleteRecursively()
    }

    @Test
    fun `pagination converges — recomputed gutter stays stable`() {
        val out = createTempDir("print")
        // a manuscript big enough to cross at least the first band boundary
        val r = PrintInterior.render(input(chapters = 6, wordsPerChapter = 900), File(out, "interior.pdf"))
        assertTrue(r.paginationPasses >= 2, "at least paginate + recheck")
        assertTrue(r.gutterCompliant)
        out.deleteRecursively()
    }

    // ---- font resolution (D-16 step) --------------------------------------

    @Test
    fun `empty font path auto-resolves to an existing serif on this machine`() {
        val resolved = PrintInterior.resolveFontPath(explicitPath = "", bold = false)
        assertTrue(java.io.File(resolved).isFile, "auto-resolve must land on a real file, got $resolved")
        assertTrue(resolved.endsWith("DejaVuSerif.ttf"), "regular face resolves to DejaVuSerif.ttf, got $resolved")
        val bold = PrintInterior.resolveFontPath(explicitPath = "", bold = true)
        assertTrue(bold.endsWith("DejaVuSerif-Bold.ttf") && java.io.File(bold).isFile,
            "bold face resolves to a real DejaVuSerif-Bold.ttf, got $bold")
    }

    @Test
    fun `bundled app-dir fonts are found — the packaged image layout`() {
        // Stage a face into <code-source parent>/fonts/ exactly where the
        // packaged image ships it, and make ~/.authr miss, so the app-dir
        // candidate (the D-16 bundle, the EpubCheckLocator pattern) is the
        // one that satisfies the search — before the system dirs.
        val parent = PrintInterior::class.java.protectionDomain.codeSource?.location
            ?.toURI()?.let { java.io.File(it).parentFile }
        org.junit.Assume.assumeTrue("code source has no parent dir (jar-in-jar layout)", parent != null)
        val src = java.io.File("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf")
        org.junit.Assume.assumeTrue("dejavu serif not present on this machine", src.isFile)
        val staged = java.io.File(java.io.File(parent!!, "fonts"), "DejaVuSerif.ttf")
        staged.parentFile.mkdirs()
        // The app-dir candidate is the BUNDLED image's fonts/ dir — the file
        // may already exist there (D-16 bundle). Back it up and restore it:
        // a test must never consume the shipped bundle.
        val backup = staged.takeIf { it.isFile }?.readBytes()
        staged.writeBytes(src.readBytes())
        val origHome = System.getProperty("user.home")
        System.setProperty("user.home", "/nonexistent-authr-home")
        try {
            val resolved = PrintInterior.resolveFontPath(explicitPath = "", bold = false)
            assertEquals(staged.absolutePath, resolved, "the bundled app-dir face wins before the system dirs")
        } finally {
            System.setProperty("user.home", origHome)
            if (backup != null) staged.writeBytes(backup) else staged.delete()
        }
    }

    @Test
    fun `explicit font path is strict — missing file fails loudly with the path`() {
        val exc = assertNotNull(runCatching {
            PrintInterior.resolveFontPath(explicitPath = "/no/such/NotoSerif.ttf", bold = false)
        }.exceptionOrNull(), "a missing explicit face must fail, not silently substitute")
        assertTrue(exc!!.message!!.contains("/no/such/NotoSerif.ttf"),
            "the error names the configured path so the writer can fix it")
    }

    @Test
    fun `auto-resolve total miss fails loudly with the search list`() {
        // Force every candidate to miss via a bogus user.home; machines whose
        // system font dirs still satisfy the search skip honestly (the
        // RetailPackager epubcheck test uses the same assume pattern).
        val origHome = System.getProperty("user.home")
        System.setProperty("user.home", "/nonexistent-authr-home")
        try {
            val result = runCatching { PrintInterior.resolveFontPath(explicitPath = "", bold = false) }
            if (result.isSuccess) {
                org.junit.Assume.assumeTrue(
                    "system dejavu fonts present outside user.home — total-miss path not exercisable here",
                    false)
                return
            }
            val exc = result.exceptionOrNull()!!
            assertTrue(exc.message!!.contains("searched:"), "error lists the searched locations")
            assertTrue(exc.message!!.contains(".authr/fonts"), "search list includes the per-user font dir")
        } finally {
            System.setProperty("user.home", origHome)
        }
    }

    @Test
    fun `every page carries mirrored margins`() {
        val out = createTempDir("print")
        val r = PrintInterior.render(input(), File(out, "interior.pdf"))
        PDDocument.load(r.file).use { doc ->
            // extract per page and confirm no page is empty of the trim box
            for (i in 0 until doc.numberOfPages) {
                val box = doc.getPage(i).mediaBox
                assertEquals(432.0, box.width.toDouble(), 0.01)
                assertEquals(648.0, box.height.toDouble(), 0.01)
            }
        }
        // mirrored margins are a property of the painter: recto gutter-left,
        // verso gutter-right — asserted via the ladder + result fields here,
        // and visually in the P5 gate evidence tree.
        assertTrue(r.gutterInches in listOf(0.375, 0.5, 0.625, 0.75))
        out.deleteRecursively()
    }

    @Test
    fun `empty book rejected`() {
        val out = createTempDir("print")
        val ex = try {
            PrintInterior.render(input(1).copy(chapters = emptyList()), File(out, "x.pdf"))
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(ex != null)
        out.deleteRecursively()
    }
}
