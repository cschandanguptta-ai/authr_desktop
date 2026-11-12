package com.example

import com.example.engine.publishing.Epub3Exporter
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The EPUB 3 exporter (v3.0 P5, plan §11.1): OCF structure (mimetype first,
 * STORED, exact bytes), package document with dcterms:modified, the nav
 * document in manifest + spine, semantic well-formed chapter XHTML, the
 * cover slot, and XML escaping. The external epubcheck run itself lives in
 * RetailPackagerTest / the P5 gate.
 */
class Epub3ExporterTest {

    private fun chapters(n: Int) = (1..n).map {
        Epub3Exporter.Chapter(
            number = it,
            title = "Chapter $it & <Title>",
            bodyText = "First paragraph of $it with <em>markup</em> & \"quotes\".\n\nSecond paragraph — with an em dash…"
        )
    }

    private fun input(n: Int = 3, cover: File? = null) = Epub3Exporter.Input(
        title = "The Iron Vale",
        author = "A. Writer",
        language = "en",
        description = "A test blurb & description",
        keywords = listOf("fantasy", "serial"),
        isbn = "978-3-16-148410-0",
        copyrightLine = "Copyright © 2026 A. Writer",
        coverFile = cover,
        identifierUuid = "test-uuid-1",
        modifiedIso = "2026-11-11T00:00:00Z",
        chapters = chapters(n)
    )

    private fun exportTo(dir: String, inp: Epub3Exporter.Input): Epub3Exporter.Result =
        Epub3Exporter.export(inp, File(dir, "test.epub"))

    @Test
    fun `mimetype is first entry stored and exact`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input())
        ZipFile(r.file).use { zf ->
            val entries = zf.entries().toList()
            assertEquals("mimetype", entries.first().name)
            assertEquals(java.util.zip.ZipEntry.STORED, entries.first().method)
            assertEquals("application/epub+zip", zf.getInputStream(zf.getEntry("mimetype")).readBytes().decodeToString())
        }
        out.deleteRecursively()
    }

    @Test
    fun `package document carries workbench metadata and modified stamp`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input())
        ZipFile(r.file).use { zf ->
            val opf = zf.getInputStream(zf.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf.contains("<dc:identifier id=\"pub-id\">urn:isbn:9783161484100</dc:identifier>"))
            assertTrue(opf.contains("<dc:title id=\"title\">The Iron Vale</dc:title>"))
            assertTrue(opf.contains("<dc:language>en</dc:language>"))
            assertTrue(opf.contains("<dc:creator id=\"creator\">A. Writer</dc:creator>"))
            assertTrue(opf.contains("<dc:subject>fantasy</dc:subject>"))
            assertTrue(opf.contains("<dc:description>A test blurb &amp; description</dc:description>"))
            assertTrue(opf.contains("<meta property=\"dcterms:modified\">2026-11-11T00:00:00Z</meta>"))
            assertTrue(opf.contains("properties=\"nav\""))
            assertTrue(opf.contains("unique-identifier=\"pub-id\""))
        }
        out.deleteRecursively()
    }

    @Test
    fun `uuid identifier used when isbn absent`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input().copy(isbn = ""))
        assertTrue(r.identifier.startsWith("urn:uuid:test-uuid-1"))
        out.deleteRecursively()
    }

    @Test
    fun `nav document links every chapter and nav sits in the spine`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input(5))
        ZipFile(r.file).use { zf ->
            val nav = zf.getInputStream(zf.getEntry("OEBPS/nav.xhtml")).readBytes().decodeToString()
            assertTrue(nav.contains("epub:type=\"toc\""))
            for (i in 1..5) {
                assertTrue(nav.contains("href=\"text/ch%03d.xhtml\"".format(i)))
                assertTrue(nav.contains(escapeExpected("Chapter $i & <Title>")))
            }
            val opf = zf.getInputStream(zf.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf.contains("<itemref idref=\"nav\"/>"))
        }
        out.deleteRecursively()
    }

    @Test
    fun `chapter xhtml escapes markup and splits paragraphs`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input(1))
        ZipFile(r.file).use { zf ->
            val ch = zf.getInputStream(zf.getEntry("OEBPS/text/ch001.xhtml")).readBytes().decodeToString()
            assertTrue(ch.contains("<section epub:type=\"chapter\">"))
            assertTrue(ch.contains("<h1>Chapter 1 &amp; &lt;Title&gt;</h1>"))
            // inline angle brackets escaped, paragraph split on blank line
            assertTrue(ch.contains("<p>First paragraph of 1 with &lt;em&gt;markup&lt;/em&gt; &amp; &quot;quotes&quot;.</p>"))
            assertEquals(2, Regex("<p>").findAll(ch).count())
        }
        out.deleteRecursively()
    }

    @Test
    fun `cover slot emits cover page image and manifest properties`() {
        val out = createTempDir("epub")
        val img = File(out, "cover.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())) }
        val r = exportTo(out.absolutePath, input(1, cover = img))
        ZipFile(r.file).use { zf ->
            assertTrue(zf.getEntry("OEBPS/images/cover.jpg") != null)
            assertTrue(zf.getEntry("OEBPS/cover.xhtml") != null)
            val opf = zf.getInputStream(zf.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf.contains("properties=\"cover-image\""))
            assertTrue(opf.contains("<itemref idref=\"cover-page\"/>"))
        }
        out.deleteRecursively()
    }

    @Test
    fun `every manifest href resolves to a zip entry`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input(4))
        ZipFile(r.file).use { zf ->
            val opf = zf.getInputStream(zf.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            val hrefs = Regex("href=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
            assertTrue(hrefs.isNotEmpty())
            for (h in hrefs) {
                val entry = "OEBPS/$h"
                assertTrue(zf.getEntry(entry) != null, "missing zip entry for manifest href: $entry")
            }
        }
        out.deleteRecursively()
    }

    @Test
    fun `series metadata lands as belongs-to-collection`() {
        val out = createTempDir("epub")
        val r = exportTo(out.absolutePath, input(1).copy(seriesName = "The Iron Vale", seriesNumber = 2))
        ZipFile(r.file).use { zf ->
            val opf = zf.getInputStream(zf.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf.contains("property=\"belongs-to-collection\""))
            assertTrue(opf.contains("property=\"collection-type\">series"))
            assertTrue(opf.contains("property=\"group-position\">2"))
        }
        out.deleteRecursively()
    }

    @Test
    fun `empty book is rejected`() {
        val out = createTempDir("epub")
        val ex = try {
            exportTo(out.absolutePath, input(1).copy(chapters = emptyList()))
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(ex != null)
        out.deleteRecursively()
    }

    private fun escapeExpected(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun assertFalseAny(haystack: String, needle: String) = assertFalse(haystack.contains(needle))
}
