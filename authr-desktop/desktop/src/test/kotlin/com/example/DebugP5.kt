package com.example

import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.engine.ChekhovLedger
import com.example.engine.DraftState
import com.example.engine.publishing.RetailPackager
import java.io.File

/** Temporary debug harness: one novel package, full disclosure dump. */
fun main() {
    val chapters = (1..4).map { n ->
        val canon = n != 2
        val body = buildString {
            append("Opening of $n.\n\n")
            repeat(350) { i -> append("word${(i * n) % 733} ") }
            append("\n\nClosing of $n.")
        }
        Chapter(
            bookId = 1, chapterNumber = n, title = "Chapter $n Title", content = body, summary = "s",
            writerConfirmed = canon, draftStatus = if (canon) DraftState.CONFIRMED else DraftState.PROPOSED
        )
    }
    val guns = listOf(ChekhovGun(id = 1, bookId = 1, name = "The sealed door", introducedChapter = 3,
        status = ChekhovLedger.WAIVED, waiveNote = "Sequel hook, deliberate"))
    val r = RetailPackager.packageRetail(
        RetailPackager.Input(
            bookTitle = "The Iron Vale", author = "A. Writer", isSerial = false,
            chapters = chapters, guns = guns
        ),
        File("/tmp/p5debug")
    )
    println("CLEAN=${r.clean}")
    println("--- disclosures ---")
    r.disclosures.forEach { println("  $it") }
    println("--- verdicts ---")
    r.gateVerdicts.forEach { println("  ${it.artifact} | ${it.status} | passed=${it.passed} | ${it.detail}") }
    println("--- ship report ---")
    r.shipReport.readText().lines().take(48).forEach { println("  | $it") }
}
