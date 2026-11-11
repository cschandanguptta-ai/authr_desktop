package com.example.engine.publishing

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.File

/**
 * The print interior (v3.0 P5, plan §11.1).
 *
 * "The print interior is rendered from the same confirmed manuscript to a
 * 6×9 in trim by default, with the inside-margin (gutter) ladder re-verified
 * against KDP's print requirements." The gate is external: the KDP Print
 * Previewer must pass the interior (§11.3) — this renderer produces the
 * PDF with the ladder applied per the plan's exact bands, mirrored
 * recto/verso margins, front matter, and body pagination, so the Previewer
 * check is a geometry verification rather than a repair job.
 *
 * Gutter ladder (plan §11.1, Table "Print interior gutter ladder
 * (6×9 in trim, black-and-white interior)") — KDP requires more inside
 * margin as the page count grows so the binding never swallows text:
 *
 *   24–150 pages → 0.375 in; 151–300 → 0.5 in; 301–500 → 0.625 in;
 *   501–700 → 0.75 in.
 *
 * Margins are mirrored (recto/verso): the gutter sits on the binding side
 * (left on odd/recto pages of a left-bound book, right on even/verso).
 * Because the gutter width depends on the final page count, pagination runs
 * iteratively: paginate → ladder lookup → re-paginate → converge.
 *
 * Fonts: a TTF serif is embedded (subset) so the PDF carries the text with
 * proper ToUnicode maps — sandbox defaults are DejaVu Serif; callers may
 * pass any TTF paths (e.g. a CJK face for non-Latin manuscripts).
 */
object PrintInterior {

    // ---- trim size: 6×9 in (plan default) -------------------------------
    const val TRIM_WIDTH_IN = 6.0
    const val TRIM_HEIGHT_IN = 9.0
    const val OUTSIDE_MARGIN_IN = 0.5
    const val TOP_MARGIN_IN = 0.75
    const val BOTTOM_MARGIN_IN = 0.75

    /** PDF user-space units are 1/72 inch. */
    const val PT_PER_IN = 72.0

    /**
     * The plan's ladder as (upper page bound, inside margin inches).
     * Page counts above the last band clamp to the largest margin.
     */
    val LADDER: List<Pair<Int, Double>> = listOf(
        150 to 0.375,
        300 to 0.5,
        500 to 0.625,
        700 to 0.75
    )

    /** The plan's ladder as display rows ("24–150 pages" → "0.375 in"). */
    fun ladderRows(): List<Triple<String, String, String>> {
        val lower = listOf(24) + LADDER.map { it.first + 1 }
        return LADDER.mapIndexed { i, (up, margin) ->
            Triple("${lower[i]}–$up pages", "$margin in", "inside (gutter) margin")
        }
    }

    /** Inside-margin (gutter) inches for a [pageCount]-page interior. */
    fun gutterForPageCount(pageCount: Int): Double {
        for ((up, margin) in LADDER) if (pageCount <= up) return margin
        return LADDER.last().second
    }

    data class ChapterSource(val number: Int, val title: String, val bodyText: String)

    data class Input(
        val title: String,
        val author: String = "",
        val subtitle: String = "",
        val language: String = "en",
        val copyrightLine: String = "",
        val isbn: String = "",
        val publisher: String = "",
        val seriesName: String = "",
        val seriesNumber: Int? = null,
        val chapters: List<ChapterSource>,
        /** Embedded body serif. Empty means auto-resolve across machines
         *  (D-16 step: see [resolveFontPath]); a non-empty path is strict —
         *  a CJK manuscript's Noto face is never silently swapped. */
        val fontRegularPath: String = "",
        val fontBoldPath: String = ""
    )

    /**
     * D-16 remediation step: resolve the embedded serif across machines.
     * An explicit non-empty path is STRICT (it must exist — the writer's
     * configured face must never be silently substituted); empty means
     * auto-resolve through known install locations, failing LOUDLY with the
     * full search list so the print verdict turns FAILED, never silent.
     * (Full fix — bundling the TTFs as installer resources — stays tracked
     * as D-16; this makes non-Linux machines work without code changes.)
     */
    fun resolveFontPath(explicitPath: String, bold: Boolean): String {
        val face = if (bold) "DejaVuSerif-Bold.ttf" else "DejaVuSerif.ttf"
        if (explicitPath.isNotEmpty()) {
            require(File(explicitPath).isFile) {
                "print interior font not found at the configured path: $explicitPath"
            }
            return explicitPath
        }
        val home = System.getProperty("user.home") ?: "."
        val candidates = listOf(
            "$home/.authr/fonts/$face",
            "/usr/share/fonts/truetype/dejavu/$face",
            "/usr/local/share/fonts/$face",
            "C:\\Windows\\Fonts\\$face",
            "/Library/Fonts/$face"
        )
        candidates.firstOrNull { File(it).isFile }?.let { return it }
        throw IllegalStateException(
            "print interior font \"$face\" not found — searched: ${candidates.joinToString(", ")}. " +
                "Pass PrintInterior.Input.font${if (bold) "Bold" else "Regular"}Path explicitly " +
                "(e.g. an installer-bundled face or a Noto face for CJK manuscripts)."
        )
    }

    data class Result(
        val file: File,
        val pageCount: Int,
        val gutterInches: Double,
        val trimWidthIn: Double,
        val trimHeightIn: Double,
        /** How many ladder iterations pagination needed to converge. */
        val paginationPasses: Int,
        /** True when the final gutter matches the ladder row for [pageCount]. */
        val gutterCompliant: Boolean
    )

    fun render(input: Input, outFile: File): Result {
        require(input.chapters.isNotEmpty()) { "print interior needs at least one chapter" }
        outFile.parentFile?.mkdirs()

        var gutter = 0.5 // initial estimate inside the 151–300 band
        var passes = 0
        var pageCount = 0
        while (passes < 5) {
            passes++
            pageCount = paginateTo(input, gutter, outFile, save = false)
            val next = gutterForPageCount(pageCount)
            if (next == gutter) break
            gutter = next
        }
        // Final authoritative write with the converged gutter.
        pageCount = paginateTo(input, gutter, outFile, save = true)

        return Result(
            file = outFile,
            pageCount = pageCount,
            gutterInches = gutter,
            trimWidthIn = TRIM_WIDTH_IN,
            trimHeightIn = TRIM_HEIGHT_IN,
            paginationPasses = passes + 1,
            gutterCompliant = gutter == gutterForPageCount(pageCount)
        )
    }

    /** Lays the whole interior out; returns the total page count. */
    private fun paginateTo(input: Input, gutterIn: Double, outFile: File, save: Boolean): Int {
        val doc = PDDocument()
        try {
            val regular = PDType0Font.load(doc, File(resolveFontPath(input.fontRegularPath, bold = false)))
            val bold = PDType0Font.load(doc, File(resolveFontPath(input.fontBoldPath, bold = true)))
            val st = Cursor(doc, regular, bold, gutterIn)

            newPage(st)
            drawTitlePage(st, input)
            newPage(st)
            drawCopyrightPage(st, input)
            newPage(st)
            drawContentsPage(st, input)

            for (ch in input.chapters) {
                newPage(st)
                drawChapter(st, ch)
            }
            // the last page's content stream must be closed before save/load
            st.cs?.close()
            st.cs = null
            if (save) doc.save(outFile)
            return st.pageNo
        } finally {
            doc.close()
        }
    }

    // ------------------------------------------------------------------ //
    //  Layout cursor — one mutable position over the mirrored geometry     //
    // ------------------------------------------------------------------ //

    private class Cursor(
        val doc: PDDocument,
        val regular: PDType0Font,
        val bold: PDType0Font,
        val gutterIn: Double
    ) {
        val trimW = TRIM_WIDTH_IN * PT_PER_IN
        val trimH = TRIM_HEIGHT_IN * PT_PER_IN
        val gutterPt = gutterIn * PT_PER_IN
        val outsidePt = OUTSIDE_MARGIN_IN * PT_PER_IN
        val topPt = TOP_MARGIN_IN * PT_PER_IN
        val bottomPt = BOTTOM_MARGIN_IN * PT_PER_IN
        var pageNo = 0
        var cs: PDPageContentStream? = null
        var y = 0.0
        var x = 0.0
        var width = 0.0

        /** Mirrored margins: odd (1-based) = recto → gutter on the LEFT. */
        fun leftMargin(pageNumber: Int): Double = if (pageNumber % 2 == 1) gutterPt else outsidePt
        fun rightMargin(pageNumber: Int): Double = if (pageNumber % 2 == 1) outsidePt else gutterPt
        fun contentWidth(pageNumber: Int): Double =
            trimW - leftMargin(pageNumber) - rightMargin(pageNumber)
    }

    /** Opens a fresh page and recomputes the mirrored margins for its parity. */
    private fun newPage(st: Cursor) {
        st.cs?.close()
        val page = PDPage(PDRectangle(st.trimW.toFloat(), st.trimH.toFloat()))
        st.doc.addPage(page)
        st.pageNo++
        st.cs = PDPageContentStream(st.doc, page)
        st.y = st.trimH - st.topPt
        st.x = st.leftMargin(st.pageNo)
        st.width = st.contentWidth(st.pageNo)
    }

    // ------------------------------------------------------------------ //
    //  Page painters                                                       //
    // ------------------------------------------------------------------ //

    private fun drawTitlePage(st: Cursor, input: Input) {
        val cx = st.x + st.width / 2
        var y = st.trimH * 0.68

        drawCentered(st, input.title, st.bold, 24f, cx, y)
        y -= 34
        if (input.subtitle.isNotBlank()) {
            drawCentered(st, input.subtitle, st.regular, 13f, cx, y)
            y -= 30
        }
        if (input.author.isNotBlank()) {
            y -= 12
            drawCentered(st, input.author, st.regular, 14f, cx, y)
        }
        if (input.seriesName.isNotBlank()) {
            y -= 44
            val seriesLine = if (input.seriesNumber != null) {
                "${input.seriesName} — Book ${input.seriesNumber}"
            } else input.seriesName
            drawCentered(st, seriesLine, st.regular, 11f, cx, y)
        }
    }

    private fun drawCopyrightPage(st: Cursor, input: Input) {
        var y = st.trimH - st.topPt - 20
        val lines = buildList {
            add(input.copyrightLine.ifBlank { "Copyright © ${java.time.LocalDate.now().year}. All rights reserved." })
            if (input.isbn.isNotBlank()) add("ISBN: ${input.isbn}")
            if (input.publisher.isNotBlank()) add("Published by ${input.publisher}")
            add("All rights reserved. No part of this book may be reproduced without written permission of the author.")
        }
        for (line in lines) {
            wrapText(st, line, st.regular, 9.5f, indentFirst = 0.0, startY = y)
            y = st.y - 10
        }
    }

    private fun drawContentsPage(st: Cursor, input: Input) {
        var y = st.trimH - st.topPt
        drawLeft(st, "Contents", st.bold, 15f, st.x, y)
        y -= 28
        for (ch in input.chapters) {
            val line = "Chapter ${ch.number} — ${ch.title}"
            wrapText(st, line, st.regular, 10.5f, indentFirst = 0.0, startY = y)
            y = st.y - 8
            if (y < st.bottomPt) {
                newPage(st)
                y = st.trimH - st.topPt
            }
        }
    }

    private fun drawChapter(st: Cursor, ch: ChapterSource) {
        var y = st.trimH - st.topPt

        drawLeft(st, "Chapter ${ch.number}", st.regular, 10f, st.x, y)
        y -= 24
        wrapText(st, ch.title, st.bold, 16f, indentFirst = 0.0, startY = y)
        y = st.y - 20

        val paragraphs = ch.bodyText.split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (p in paragraphs) {
            val flat = p.replace(Regex("\\r?\\n"), " ")
            wrapText(st, flat, st.regular, 10.5f, indentFirst = 18.0, startY = y)
            y = st.y - 8 // paragraph gap
        }
        drawPageNumber(st)
    }

    private fun drawPageNumber(st: Cursor) {
        val cx = st.x + st.width / 2
        drawCentered(st, st.pageNo.toString(), st.regular, 9f, cx, st.bottomPt * 0.55)
    }

    // ------------------------------------------------------------------ //
    //  Text primitives                                                     //
    // ------------------------------------------------------------------ //

    private fun stringWidth(font: PDType0Font, text: String, size: Double): Double =
        font.getStringWidth(text) / 1000.0 * size

    private fun drawLeft(st: Cursor, text: String, font: PDType0Font, size: Float, x: Double, y: Double) {
        st.cs!!.beginText()
        st.cs!!.setFont(font, size)
        st.cs!!.newLineAtOffset(x.toFloat(), y.toFloat())
        st.cs!!.showText(text)
        st.cs!!.endText()
    }

    private fun drawCentered(st: Cursor, text: String, font: PDType0Font, size: Float, cx: Double, y: Double) {
        val w = stringWidth(font, text, size.toDouble())
        drawLeft(st, text, font, size, cx - w / 2, y)
    }

    /**
     * Word-wraps [text] into the current content column starting at baseline
     * [startY]; updates st.y to the next free baseline. Lines that would not
     * fit above the bottom margin trigger a mirrored new page; the x/width
     * pair is then recomputed for the new page's parity.
     */
    private fun wrapText(
        st: Cursor,
        text: String,
        font: PDType0Font,
        size: Float,
        startY: Double,
        indentFirst: Double
    ) {
        val leading = size * 1.42
        var y = startY
        var indent = indentFirst

        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            st.y = y
            return
        }

        val sb = StringBuilder()
        var i = 0
        while (i < words.size) {
            val candidate = if (sb.isEmpty()) words[i] else "$sb ${words[i]}"
            if (stringWidth(font, candidate, size.toDouble()) <= st.width - indent) {
                sb.append(if (sb.isEmpty()) words[i] else " ${words[i]}")
                i++
                continue
            }
            if (sb.isEmpty()) {
                // single word wider than the column — hard-place it
                sb.append(words[i]); i++
            }
            if (y < st.bottomPt) {
                newPage(st)
                y = st.trimH - st.topPt
                indent = 0.0
            }
            emitLine(st, sb.toString(), font, size, st.x + indent, y)
            y -= leading
            sb.setLength(0)
            indent = 0.0
        }
        if (sb.isNotEmpty()) {
            if (y < st.bottomPt) {
                newPage(st)
                y = st.trimH - st.topPt
                indent = 0.0
            }
            emitLine(st, sb.toString(), font, size, st.x + indent, y)
            y -= leading
        }
        st.y = y
    }

    private fun emitLine(st: Cursor, text: String, font: PDType0Font, size: Float, x: Double, y: Double) {
        st.cs!!.beginText()
        st.cs!!.setFont(font, size)
        st.cs!!.newLineAtOffset(x.toFloat(), y.toFloat())
        st.cs!!.showText(text)
        st.cs!!.endText()
    }
}
