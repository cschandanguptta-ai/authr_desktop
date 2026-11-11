package com.example.engine.publishing

import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The EPUB 3 exporter (v3.0 P5, plan §11.1).
 *
 * "The EPUB 3 exporter extends the v2.3 EpubManager: a navigable TOC,
 * semantic chapter markup, embedded cover slot, and metadata from the
 * workbench. The gate is external: the export must pass epubcheck with zero
 * errors before it is offered for upload."
 *
 * The v2.3 [com.example.managers.EpubManager] is Android-coupled
 * (ContentResolver/MediaStore); this exporter is the JVM-pure engine that
 * both the desktop app and the exit gate drive. Every structural rule that
 * epubcheck 5 enforces is respected by construction:
 *
 *  - `mimetype` is the first zip entry, STORED (no compression), exact bytes
 *    "application/epub+zip" (OCF requirement);
 *  - `META-INF/container.xml` points at the package document;
 *  - the package document carries `dc:identifier`, `dc:title`,
 *    `dc:language`, the creator, the workbench keywords as `dc:subject`,
 *    and the REQUIRED `dcterms:modified` meta;
 *  - the EPUB 3 navigation document (`nav.xhtml`) is manifest-declared with
 *    `properties="nav"` and present in the spine;
 *  - every chapter is a well-formed XHTML document with semantic markup
 *    (h1/section/epub:type="chapter") and XML-escaped text;
 *  - the cover slot: `cover.xhtml` + the image manifest-declared with
 *    `properties="cover-image"` when a cover file is provided.
 *
 * Canon-only is enforced by the caller (the packager runs
 * [com.example.engine.WriterGate.canonChapters] first); the exporter never
 * sees unconfirmed text.
 */
object Epub3Exporter {

    /** One chapter of the book: plain text body (paragraphs split on blank lines). */
    data class Chapter(val number: Int, val title: String, val bodyText: String)

    data class Input(
        val title: String,
        val author: String = "",
        val language: String = "en",
        val description: String = "",
        val keywords: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val isbn: String = "",
        val publisher: String = "",
        val copyrightLine: String = "",
        val seriesName: String = "",
        val seriesNumber: Int? = null,
        /** Embedded cover image (JPEG); null exports without the cover slot. */
        val coverFile: File? = null,
        /** Stable identifier (tests/gate fix it); random UUID when null. */
        val identifierUuid: String? = null,
        /** Fixed dcterms:modified value (tests/gate fix it); now when null. */
        val modifiedIso: String? = null,
        val chapters: List<Chapter>
    )

    data class Result(val file: File, val chapterCount: Int, val sizeBytes: Long, val identifier: String)

    private val ISO_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /** XML-escapes text for element content and attribute values. */
    fun escapeXml(text: String): String = buildString {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    /** Renders one chapter body as semantic XHTML paragraphs (escaped). */
    fun chapterBodyHtml(bodyText: String): String = buildString {
        val paragraphs = bodyText.split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) {
            append("      <p></p>\n")
            return@buildString
        }
        for (p in paragraphs) {
            val withBreaks = p.replace(Regex("\\r?\\n"), "<br/>")
            append("      <p>").append(escapeXml(withBreaks)).append("</p>\n")
        }
    }

    fun export(input: Input, outFile: File): Result {
        require(input.chapters.isNotEmpty()) { "EPUB export needs at least one chapter" }
        outFile.parentFile?.mkdirs()

        val uuid = input.identifierUuid ?: UUID.randomUUID().toString()
        val identifier = if (input.isbn.isNotBlank()) "urn:isbn:${input.isbn.replace("-", "").trim()}" else "urn:uuid:$uuid"
        val modified = input.modifiedIso ?: ISO_UTC.format(Instant.now())
        val hasCover = input.coverFile != null && input.coverFile.exists()

        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
            // 1. mimetype — first entry, STORED, exact bytes (OCF).
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            mimeEntry.size = mime.size.toLong()
            val crc = CRC32().apply { update(mime) }
            mimeEntry.crc = crc.value
            zos.putNextEntry(mimeEntry)
            zos.write(mime)
            zos.closeEntry()

            // 2. META-INF/container.xml
            put(zos, "META-INF/container.xml", CONTAINER_XML)

            // 3. package document
            put(zos, "OEBPS/content.opf", packageOpf(input, identifier, modified, hasCover))

            // 4. navigation document (also a spine item)
            put(zos, "OEBPS/nav.xhtml", navDocument(input))

            // 5. stylesheet
            put(zos, "OEBPS/style.css", CSS)

            // 6. cover slot
            if (hasCover) {
                put(zos, "OEBPS/cover.xhtml", coverDocument(input, lang = input.language))
                val img = ZipEntry("OEBPS/images/cover.jpg")
                img.method = ZipEntry.DEFLATED
                zos.putNextEntry(img)
                input.coverFile!!.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            // 7. chapters (semantic XHTML)
            for (ch in input.chapters) {
                put(zos, "OEBPS/text/ch%03d.xhtml".format(ch.number), chapterDocument(input, ch))
            }
        }
        return Result(
            file = outFile,
            chapterCount = input.chapters.size,
            sizeBytes = outFile.length(),
            identifier = identifier
        )
    }

    // ------------------------------------------------------------------ //
    //  Document templates (placeholders @@LANG@@ / @@...@@ keep quotes      //
    //  away from raw-string terminators — a Kotlin lexer trap)             //
    // ------------------------------------------------------------------ //

    private val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private fun packageOpf(input: Input, identifier: String, modified: String, hasCover: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id" xml:lang="@@LANG@@">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">@@ID@@</dc:identifier>
    <dc:title id="title">@@TITLE@@</dc:title>
""")
        if (input.author.isNotBlank()) {
            append("""    <dc:creator id="creator">@@AUTHOR@@</dc:creator>
    <meta refines="#creator" property="role" scheme="marc:relators">aut</meta>
""")
        }
        append("""    <dc:language>@@LANG@@</dc:language>
""")
        if (input.publisher.isNotBlank()) {
            append("    <dc:publisher>").append(escapeXml(input.publisher)).append("</dc:publisher>\n")
        }
        if (input.description.isNotBlank()) {
            append("    <dc:description>").append(escapeXml(input.description)).append("</dc:description>\n")
        }
        for (k in input.keywords) {
            if (k.isNotBlank()) append("    <dc:subject>").append(escapeXml(k.trim())).append("</dc:subject>\n")
        }
        if (input.copyrightLine.isNotBlank()) {
            append("    <dc:rights>").append(escapeXml(input.copyrightLine)).append("</dc:rights>\n")
        }
        if (input.seriesName.isNotBlank()) {
            append("    <meta property=\"belongs-to-collection\" id=\"coll\">")
                .append(escapeXml(input.seriesName)).append("</meta>\n")
            append("    <meta refines=\"#coll\" property=\"collection-type\">series</meta>\n")
            input.seriesNumber?.let {
                append("    <meta refines=\"#coll\" property=\"group-position\">").append(it).append("</meta>\n")
            }
        }
        append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n")
        append("  </metadata>\n")

        append("  <manifest>\n")
        append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
        append("    <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
        if (hasCover) {
            append("    <item id=\"cover-page\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            append("    <item id=\"cover-image\" href=\"images/cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n")
        }
        for (ch in input.chapters) {
            append("    <item id=\"ch").append("%03d".format(ch.number))
            append("\" href=\"text/ch").append("%03d".format(ch.number))
            append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        }
        append("  </manifest>\n")

        append("  <spine>\n")
        if (hasCover) append("    <itemref idref=\"cover-page\"/>\n")
        // nav stays linear: EPUB 3.3 (OPF-096) requires non-linear content
        // to be reachable by hyperlink — a linear TOC page is the simplest
        // always-valid shape.
        append("    <itemref idref=\"nav\"/>\n")
        for (ch in input.chapters) {
            append("    <itemref idref=\"ch").append("%03d".format(ch.number)).append("\"/>\n")
        }
        append("  </spine>\n")
        append("</package>\n")
    }
        .replace("@@LANG@@", escapeXml(input.language))
        .replace("@@ID@@", escapeXml(identifier))
        .replace("@@TITLE@@", escapeXml(input.title))
        .replace("@@AUTHOR@@", escapeXml(input.author))

    private fun navDocument(input: Input): String {
        val items = input.chapters.joinToString("\n") { ch ->
            "      <li><a href=\"text/ch%03d.xhtml\">".format(ch.number) +
                escapeXml("Chapter ${ch.number}: ${ch.title}") + "</a></li>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="@@LANG@@">
<head>
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
@@ITEMS@@
    </ol>
  </nav>
</body>
</html>
"""
            .replace("@@LANG@@", escapeXml(input.language))
            .replace("@@ITEMS@@", items)
    }

    private fun coverDocument(input: Input, lang: String): String {
        val alt = escapeXml("Cover of ${input.title}")
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="@@LANG@@">
<head>
  <title>Cover</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body epub:type="cover">
  <figure>
    <img src="images/cover.jpg" alt="@@ALT@@"/>
  </figure>
</body>
</html>
"""
            .replace("@@LANG@@", escapeXml(lang))
            .replace("@@ALT@@", alt)
    }

    private fun chapterDocument(input: Input, ch: Chapter): String {
        val body = chapterBodyHtml(ch.bodyText)
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="@@LANG@@">
<head>
  <title>@@TITLE@@</title>
  <link rel="stylesheet" type="text/css" href="../style.css"/>
</head>
<body>
  <section epub:type="chapter">
    <h1>@@TITLE@@</h1>
@@BODY@@
  </section>
</body>
</html>
"""
            .replace("@@LANG@@", escapeXml(input.language))
            .replace("@@TITLE@@", escapeXml(ch.title))
            .replace("@@BODY@@", body)
    }

    private val CSS = """
body { font-family: serif; line-height: 1.5; margin: 1em; }
h1 { font-size: 1.4em; margin: 1.2em 0 0.8em 0; text-align: center; }
p { text-indent: 1.2em; margin: 0; text-align: justify; }
nav ol { list-style: none; }
figure { margin: 0; text-align: center; }
img { max-width: 100%; }
""".trim() + "\n"

    private fun put(zos: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.DEFLATED
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}
