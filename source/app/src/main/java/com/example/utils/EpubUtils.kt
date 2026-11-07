package com.example.utils

import com.example.data.model.Book
import com.example.data.model.Chapter
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubUtils {

    fun generateEpub(book: Book, chapters: List<Chapter>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile).buffered()).use { zos ->
            // Mimetype must be first, uncompressed.
            // (ZipOutputStream handles this simply if we just put entry)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            mimeEntry.size = 20
            mimeEntry.crc = 0x2CAB616F // CRC of "application/epub+zip"
            zos.putNextEntry(mimeEntry)
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            // META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("""
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            // OEBPS/content.opf
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val manifestItems = chapters.joinToString("\n") { 
                """<item id="chapter_${it.chapterNumber}" href="chapter_${it.chapterNumber}.xhtml" media-type="application/xhtml+xml"/>""" 
            }
            val spineItems = chapters.joinToString("\n") { 
                """<itemref idref="chapter_${it.chapterNumber}"/>""" 
            }
            zos.write("""
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${book.title}</dc:title>
                    <dc:language>en</dc:language>
                    <dc:identifier id="uuid_id">urn:uuid:${java.util.UUID.randomUUID()}</dc:identifier>
                    <dc:creator>Authr AI</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    $manifestItems
                  </manifest>
                  <spine toc="toc">
                    $spineItems
                  </spine>
                </package>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            // OEBPS/toc.ncx
            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val navPoints = chapters.mapIndexed { index, chapter ->
                """
                <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
                  <navLabel><text>${chapter.title}</text></navLabel>
                  <content src="chapter_${chapter.chapterNumber}.xhtml"/>
                </navPoint>
                """
            }.joinToString("\n")
            zos.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head>
                    <meta name="dtb:uid" content="urn:uuid:${java.util.UUID.randomUUID()}"/>
                  </head>
                  <docTitle>
                    <text>${book.title}</text>
                  </docTitle>
                  <navMap>
                    $navPoints
                  </navMap>
                </ncx>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            // Content files
            chapters.forEach { chapter ->
                zos.putNextEntry(ZipEntry("OEBPS/chapter_${chapter.chapterNumber}.xhtml"))
                val contentHtml = chapter.content.split("\n\n").joinToString("\n") { "<p>$it</p>" }
                zos.write("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                      <title>${chapter.title}</title>
                    </head>
                    <body>
                      <h1>${chapter.title}</h1>
                      $contentHtml
                    </body>
                    </html>
                """.trimIndent().toByteArray())
                zos.closeEntry()
            }
        }
    }
}
