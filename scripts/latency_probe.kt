/**
 * Latency probe for tech-debt D-08 (regex validator scaling, plan-follow-up).
 *
 * Measures the annotator path on synthetic chapters of the documented
 * envelope (1–2k words) and 10x over it (~20k words), the size the debt row
 * calls out. Deterministic text: a clean sentence bank plus planted
 * 5-word echo phrases and planted cliché hits so every detector does real
 * work. Run twice — before and after the D-08 fix — and record both numbers
 * in docs (ADR-0010 discipline: measured, not estimated).
 *
 * Classpath: engine main classes + app image jars (see p6_probe.sh).
 */
package com.example

import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.engine.ClicheCleaner
import com.example.engine.DraftState
import com.example.engine.LiveAstAnnotator

object LatencyProbe {

    // Clean sentences (fire none of the 154 linter patterns — same discipline
    // as the P2 golden-corpus clean bank).
    private val clean = listOf(
        "The ferry left the ore dock before the whistle finished its third note.",
        "Mira counted the shipment twice and found the same missing ton of stock.",
        "Frost had climbed the breakwater overnight and hung on the mooring rings.",
        "A boy sold hot coals wrapped in old newsprint near the customs gate.",
        "The foundry bell meant the second shift was already behind schedule.",
        "Rain walked down the windows of the assayer's office in slow beads.",
        "Jonah copied the manifest into the big book without looking up once.",
        "The tram to the upper works ran every eleven minutes all winter.",
        "Someone had chalked a small ship on the door of the pump house.",
        "They ate bread and hard cheese on the steps of the sample shed."
    )

    // A distinctive 6-word phrase planted far apart — the echo detector's
    // real workload is the scan itself, and planted repeats exercise the
    // span-mapping path.
    private const val echoPhrase = "the ledger would not stay closed"

    // Planted linter hits, spaced through the text so the dedupe path works.
    private val plantedCliche = listOf(
        "She felt the cold before she saw the door.",
        "\u201CStay back,\u201D he growled, and the lamp shook in his hand.",
        "She noticed the second footprints only after the bell.",
        "It seemed that the whole dock had come to watch."
    )

    fun buildText(targetWords: Int): String {
        val sb = StringBuilder(targetWords * 7)
        var words = 0
        var i = 0
        while (words < targetWords) {
            when {
                i % 37 == 11 -> { sb.append(echoPhrase).append(". "); words += 6 }
                i % 29 == 7 -> {
                    val c = plantedCliche[(i / 29) % plantedCliche.size]
                    sb.append(c).append(" "); words += c.split(" ").size
                }
                else -> {
                    val s = clean[i % clean.size]
                    sb.append(s).append(" "); words += s.split(" ").size
                }
            }
            if (i % 5 == 4) sb.append("\n\n")
            i++
        }
        return sb.toString().trim()
    }

    fun chapter(text: String, number: Int = 1) = Chapter(
        bookId = 1, chapterNumber = number, title = "Probe $number", content = text,
        summary = "", status = "Completed", writerConfirmed = true, draftStatus = DraftState.CONFIRMED
    )

    private val lore = listOf(
        Lorebook(bookId = 1, key = "Ashfall Accord", value = "The truce that ended the dock strikes."),
        Lorebook(bookId = 1, key = "the Ninth Bell", value = "Foundry signal for a town emergency."),
        Lorebook(bookId = 1, key = "Mira Voss", value = "Assayer of the lower works.")
    )

    fun main() {
        // JIT warm-up on small text.
        val warmText = buildText(400)
        repeat(3) {
            LiveAstAnnotator.annotate(chapter(warmText), lore)
            ClicheCleaner.scan(warmText)
        }

        for (target in intArrayOf(2000, 20000)) {
            val text = buildText(target)
            val words = text.split(Regex("\\s+")).size
            val ch = chapter(text)
            val ast = LiveAstAnnotator.annotate(ch, lore).ast
            val nodes = ast?.annotations?.size ?: -1

            // Post-fix builds memoize annotate (D-08): drop the cache so
            // run1 measures the true cold rescan. Pre-fix builds have no
            // cache; their baseline was measured before this line existed.
            LiveAstAnnotator.clearAnnotationCache()
            var t0 = System.nanoTime(); LiveAstAnnotator.annotate(ch, lore)
            var t1 = System.nanoTime()
            // second full run: same text again (what the margin rail does on
            // every recomposition today)
            var t2 = System.nanoTime(); LiveAstAnnotator.annotate(ch, lore)
            var t3 = System.nanoTime()
            var t4 = System.nanoTime(); LiveAstAnnotator.continuityFlags(text)
            var t5 = System.nanoTime()
            var t6 = System.nanoTime(); val hits = ClicheCleaner.scan(text)
            var t7 = System.nanoTime()

            println("TEXT_WORDS=$words TEXT_CHARS=${text.length} ANNOTATIONS=$nodes")
            println("  annotate_run1_ms=${(t1 - t0) / 1_000_000}")
            println("  annotate_run2_ms=${(t3 - t2) / 1_000_000}  (rail recomposition on unchanged text)")
            println("  echo_only_ms=${(t5 - t4) / 1_000_000}")
            println("  cliche_scan_ms=${(t7 - t6) / 1_000_000} hits=${hits.size}")
        }
    }
}

fun main() = LatencyProbe.main()
