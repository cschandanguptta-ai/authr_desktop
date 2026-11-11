package com.example.engine.audio

/**
 * EPUB 3 Media Overlays (SMIL) read-along export (v3.0 P4, plan §7.6).
 *
 * The second retail surface: chapters export as EPUB 3 Media Overlays,
 * pairing each text fragment with its audio segment in a SMIL file so Apple
 * Books, Kobo, and other readers can highlight text as it is narrated — the
 * same confirmed manuscript and the same audio master, no re-authoring.
 *
 * The exporter is pure string generation (deterministic, no external tools):
 * it emits the SMIL overlay, the paired EPUB XHTML content document with
 * fragment ids, and the timing sanity check the renderer's exit gate runs.
 */
object SmilExporter {

    /** One text fragment paired with its audio segment in the chapter master. */
    data class Fragment(
        val id: String,
        val text: String,
        val clipBeginSec: Double,
        val clipEndSec: Double
    )

    /** The result of the timing sanity check (plan §13, P4 exit gate). */
    data class TimingCheck(
        val ok: Boolean,
        val fragments: Int,
        val coveredSec: Double,
        val chapterDurationSec: Double,
        val problems: List<String>
    ) {
        fun summary(): String = buildString {
            append(
                "SMIL timing: $fragments fragments, " +
                    "covered %.3fs of %.3fs".format(coveredSec, chapterDurationSec)
            )
            append(if (ok) " — OK" else " — PROBLEMS: ${problems.joinToString("; ")}")
        }
    }

    /**
     * Fragments chapter text into narration segments: paragraphs, with long
     * paragraphs split by sentence into groups of at most [maxWords] words
     * (deterministic, order-preserving). Blank paragraphs are dropped.
     */
    fun fragmentText(content: String, maxWords: Int = 80): List<String> {
        val out = ArrayList<String>()
        content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }.forEach { para ->
            val words = para.split(Regex("\\s+"))
            if (words.size <= maxWords) {
                out += para
            } else {
                val sentences = para.split(Regex("(?<=[.!?])\\s+"))
                var group = ArrayList<String>()
                var groupWords = 0
                sentences.forEach { s ->
                    val sw = s.split(Regex("\\s+")).size
                    if (groupWords > 0 && groupWords + sw > maxWords) {
                        out += group.joinToString(" ")
                        group = ArrayList()
                        groupWords = 0
                    }
                    group += s
                    groupWords += sw
                }
                if (group.isNotEmpty()) out += group.joinToString(" ")
            }
        }
        return out
    }

    /**
     * Builds the fragment list with clip timings laid end-to-end over the
     * chapter audio duration, weighted by fragment word count (deterministic
     * proportional allocation; the renderer synthesizes per fragment, so
     * measured segment durations replace these weights when available).
     */
    fun fragmentsWithTimings(
        texts: List<String>,
        chapterDurationSec: Double
    ): List<Fragment> {
        if (texts.isEmpty() || chapterDurationSec <= 0.0) return emptyList()
        val weights = texts.map { it.split(Regex("\\s+")).count { w -> w.isNotBlank() }.coerceAtLeast(1) }
        val total = weights.sum()
        val frags = ArrayList<Fragment>()
        var cursor = 0.0
        texts.forEachIndexed { idx, text ->
            val dur = chapterDurationSec * weights[idx] / total
            val end = if (idx == texts.lastIndex) chapterDurationSec else cursor + dur
            frags += Fragment("frag-${idx + 1}", text, round3(cursor), round3(end))
            cursor = end
        }
        return frags
    }

    /** The SMIL overlay document for one chapter (EPUB 3 Media Overlays 1.0). */
    fun smilDocument(
        chapterNumber: Int,
        xhtmlName: String,
        audioRelPath: String,
        fragments: List<Fragment>
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">""")
        appendLine("  <body>")
        appendLine("""    <seq epub:textref="$xhtmlName" id="ch${pad2(chapterNumber)}">""")
        fragments.forEach { f ->
            appendLine("""      <par id="${f.id}">""")
            appendLine("""        <text src="$xhtmlName#${f.id}"/>""")
            appendLine(
                "        <audio src=\"$audioRelPath\" clipBegin=\"${fmt(f.clipBeginSec)}s\" " +
                    "clipEnd=\"${fmt(f.clipEndSec)}s\"/>"
            )
            appendLine("      </par>")
        }
        appendLine("    </seq>")
        appendLine("  </body>")
        appendLine("</smil>")
    }.trimEnd() + "\n"

    /** The paired EPUB XHTML content document carrying the fragment ids. */
    fun xhtmlDocument(chapterNumber: Int, title: String, fragments: List<Fragment>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""")
        appendLine("  <head><title>${escapeXml(title)}</title></head>")
        appendLine("  <body>")
        appendLine("""    <section epub:type="chapter">""")
        appendLine("""      <h1 id="ch-title">Chapter $chapterNumber: ${escapeXml(title)}</h1>""")
        fragments.forEach { f ->
            appendLine("""      <p id="${f.id}">${escapeXml(f.text)}</p>""")
        }
        appendLine("    </section>")
        appendLine("  </body>")
        appendLine("</html>")
    }.trimEnd() + "\n"

    /**
     * The timing sanity check (P4 exit gate): clip times must be monotonic,
     * non-overlapping, contiguous (no gaps), cover the chapter duration, and
     * the last clipEnd must equal the measured chapter duration within
     * [toleranceSec].
     */
    fun timingSanity(
        fragments: List<Fragment>,
        chapterDurationSec: Double,
        toleranceSec: Double = 0.05
    ): TimingCheck {
        val problems = ArrayList<String>()
        if (fragments.isEmpty()) {
            return TimingCheck(false, 0, 0.0, chapterDurationSec, listOf("no fragments"))
        }
        fragments.forEachIndexed { idx, f ->
            if (f.clipBeginSec < 0 || f.clipEndSec <= f.clipBeginSec) {
                problems += "${f.id}: non-increasing clip ${f.clipBeginSec}s..${f.clipEndSec}s"
            }
            if (idx > 0) {
                val prev = fragments[idx - 1]
                if (kotlin.math.abs(prev.clipEndSec - f.clipBeginSec) > toleranceSec) {
                    problems += "${f.id}: gap/overlap after ${prev.id} " +
                        "(prev ends ${prev.clipEndSec}s, this begins ${f.clipBeginSec}s)"
                }
            }
        }
        val covered = fragments.sumOf { it.clipEndSec - it.clipBeginSec }
        if (kotlin.math.abs(fragments.last().clipEndSec - chapterDurationSec) > toleranceSec) {
            problems += "last clipEnd ${fragments.last().clipEndSec}s != chapter duration ${chapterDurationSec}s"
        }
        if (kotlin.math.abs(covered - chapterDurationSec) > toleranceSec * fragments.size) {
            problems += "covered $covered${'s'} != chapter duration ${chapterDurationSec}s"
        }
        return TimingCheck(problems.isEmpty(), fragments.size, round3(covered), chapterDurationSec, problems)
    }

    private fun fmt(t: Double): String = "%.3f".format(t)

    private fun round3(t: Double): Double = Math.round(t * 1000.0) / 1000.0

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
