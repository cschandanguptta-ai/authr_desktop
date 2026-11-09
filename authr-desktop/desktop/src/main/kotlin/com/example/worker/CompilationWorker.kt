package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.LlmOrchestrator
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.ChekhovGunDao
import com.example.data.local.QuestionDao
import com.example.data.local.SerialArcDao
import com.example.engine.ChekhovLedger
import com.example.engine.serialization.SerialValidatorSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompilationWorker(
    context: Context,
    params: WorkerParameters,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    /** v3.0 P2 (plan §5.2): the Chekhov ledger powers the final-act
     *  recovery gate — compile is blocked while any confirmed gun is
     *  still unfired, unless the writer waived it with a note. */
    private val chekhovGunDao: ChekhovGunDao? = null,
    /** v3.0 P3 (plan §6): the serialization export gates — the
     *  anti-withholding gate, the scorecard interpretation bands, and the
     *  arc completion requirement run before any serial export. */
    private val serialArcDao: SerialArcDao? = null,
    private val questionDao: QuestionDao? = null
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getInt("BOOK_ID", -1)
        if (bookId == -1) return@withContext Result.failure()
        if (isStopped) return@withContext Result.failure()

        val book = bookDao.getBookById(bookId) ?: return@withContext Result.failure()
        if (book.currentPhase == "Paused" || book.currentPhase == "Stopped") return@withContext Result.failure()
        val storyBible = book.storyBible

        try {
            try {
                setForeground(createForegroundInfo(applicationContext, "Authr OS Compilation Engine", "Compiling Manuscript for '${book.title}'"))
            } catch (e: Exception) {
                Log.w("CompilationWorker", "Could not set foreground info", e)
            }

            logAgentChat(bookDao, bookId, "👑 [Executive Orchestrator]", "Editorial revision complete. Launching Phase 4 Production compilation...")
            val bookForCompiling = bookDao.getBookById(bookId) ?: book
            bookDao.updateBook(bookForCompiling.copy(currentPhase = "Compiling"))

            // v3.0 §4 P1 exit gate: no unconfirmed text reaches any export —
            // the compiler consumes canon chapters only (plan Table 33).
            val allChaptersForCompile = chapterDao.getChaptersForBookSync(bookId)
            val finalChapters = com.example.engine.WriterGate.canonChapters(allChaptersForCompile)
            val excluded = allChaptersForCompile.size - finalChapters.size
            if (excluded > 0) {
                logAgentChat(bookDao, bookId, "📚 [Publishing Agent]", "Writer gate: excluded $excluded unconfirmed chapter(s) from the compiled manuscript — confirm them in the Writer Studio and re-compile.")
            }

            // v3.0 P2 (plan §5.2): the final-act recovery gate — compile is
            // blocked while any confirmed gun is still unfired. Waived guns
            // pass, but their waiver notes are disclosed in the log (never
            // silent). A clean/resolved/waived ledger compiles without
            // friction — zero false compile blocks.
            chekhovGunDao?.let { gunDao ->
                val allGuns = gunDao.getGunsForBookSync(bookId)
                val gate = ChekhovLedger.recoveryGate(allGuns)
                gate.report.forEach { line ->
                    logAgentChat(bookDao, bookId, "🧨 [Chekhov Ledger]", line)
                }
                if (gate.blocked) {
                    logAgentChat(bookDao, bookId, "🧨 [Chekhov Ledger]",
                        "Final-act recovery gate: compilation blocked — ${gate.blocking.size} unfired promise(s) in the ledger. Resolve them in the Writer Studio (L6 rail) or waive them with a note, then re-compile.")
                    return@withContext Result.failure()
                }
            }

            // v3.0 P3 (plan §6): the serialization export gates for serial
            // projects. Composes with the recovery gate above — the recovery
            // gate is ledger-only; the withholding and scorecard gates are
            // chapter-count based; both run before export (plan Table 33).
            if (book.isSerialized) {
                val serialArcs = serialArcDao?.getArcsForBookSync(bookId) ?: emptyList()
                val questions = questionDao?.getQuestionsForBookSync(bookId) ?: emptyList()
                val suite = SerialValidatorSuite.run(SerialValidatorSuite.Input(
                    book = book,
                    chapters = allChaptersForCompile,
                    arcs = serialArcs,
                    questions = questions
                ))
                suite.warnings.take(10).forEach {
                    logAgentChat(bookDao, bookId, "📖 [Serialization]", "⚠ ${it.message}")
                }
                suite.hardBlocks.forEach {
                    logAgentChat(bookDao, bookId, "⛔ [Serialization]", it.message)
                }
                if (!suite.passes) {
                    logAgentChat(bookDao, bookId, "📖 [Serialization]",
                        "Serial export gate: compilation blocked — ${suite.hardBlocks.size} hard gate(s). The anti-withholding rule, the scorecard interpretation bands and the arc completion requirement run before every serial export.")
                    return@withContext Result.failure()
                }
                suite.arcReportLines.forEach {
                    logAgentChat(bookDao, bookId, "📖 [Serialization]", it)
                }
            }
            val manuscriptBuilder = StringBuilder()
            manuscriptBuilder.append("# ${book.title}\n")
            manuscriptBuilder.append("## Genre: ${book.genre} | Format: ${book.format}\n\n")
            manuscriptBuilder.append("### Series Metadata & Publisher Notes\n")
            manuscriptBuilder.append("ISBN: 978-6648-507-48-5\n")
            manuscriptBuilder.append("Copyright © 2026. All Rights Reserved.\n\n")
            manuscriptBuilder.append("### Synopsis\n${book.synopsis}\n\n")
            manuscriptBuilder.append("### Comprehensive Story Bible\n$storyBible\n\n")
            manuscriptBuilder.append("---\n\n")

            for (chapter in finalChapters) {
                manuscriptBuilder.append("# Chapter ${chapter.chapterNumber}: ${chapter.title}\n\n")
                manuscriptBuilder.append(chapter.content)
                manuscriptBuilder.append("\n\n---\n\n")
            }

            logAgentChat(bookDao, bookId, "📚 [Publishing Agent]", "Full compiled manuscript consolidated with front matter and Series Metadata.")

            logAgentChat(bookDao, bookId, "🎨 [Cover Design Assistant]", "Generating publishing cover design prompts, typography styles, and mood boards...")
            val coverPromptText = "You are a book cover designer. Generate a book cover design proposal for \"${book.title}\" (Genre: ${book.genre}).\nInclude:\n1. Image Generation Prompt (highly descriptive for Midjourney/Dall-E)\n2. Typography style (font choice, styling, sizing ideas)\n3. Mood Board description (colors, texture, vibe, aesthetic)"
            val generatedCoverPrompt = retryWithBackoff {
                LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.EXECUTIVE, coverPromptText)
            }

            logAgentChat(bookDao, bookId, "📢 [Marketing Agent]", "Drafting promotional social media launch package, trailers, and newsletters...")
            val marketingPromptText = "You are a publishing marketing expert. Write a comprehensive launch package for the novel \"${book.title}\" (${book.genre}).\nInclude:\n1. Launch social media posts (Twitter, Instagram)\n2. Author Newsletter announcement email\n3. 1-Minute Book Trailer audio narration script with background cue indicators"
            val generatedMktMaterials = retryWithBackoff {
                LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.EXECUTIVE, marketingPromptText)
            }

            val finalBookState = bookDao.getBookById(bookId) ?: book
            val finalBook = finalBookState.copy(
                compiledManuscript = manuscriptBuilder.toString().trim(),
                coverPrompt = generatedCoverPrompt,
                marketingMaterials = generatedMktMaterials,
                currentPhase = "Completed",
                completionPercentage = 100
            )
            bookDao.updateBook(finalBook)

            // AUTO-SAVE: Write metadata and manuscript to book directory
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val sanitizedTitle = finalBook.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val exportDir = java.io.File(downloads, "Weaver_Studio/$sanitizedTitle")
            if (!exportDir.exists()) exportDir.mkdirs()

            // 1. metadata.txt
            java.io.File(exportDir, "metadata.txt").writeText("""
                TITLE: ${finalBook.title}
                GENRE: ${finalBook.genre}
                FORMAT: ${finalBook.format}
                WORD COUNT TARGET: ${finalBook.targetWordCount}
                COMPLETION: 100%
                SYNOPSIS:
                ${finalBook.synopsis}
                
                MARKETING MATERIALS:
                $generatedMktMaterials
                
                COVER PROMPT:
                $generatedCoverPrompt
            """.trimIndent())

            // 2. manuscript.txt
            java.io.File(exportDir, "manuscript.txt").writeText(finalBook.compiledManuscript)

            // 3. metadata.json
            val metadataJson = org.json.JSONObject().apply {
                put("id", finalBook.id)
                put("title", finalBook.title)
                put("genre", finalBook.genre)
                put("marketing", generatedMktMaterials)
                put("cover_prompt", generatedCoverPrompt)
            }
            java.io.File(exportDir, "metadata.json").writeText(metadataJson.toString(4))

            logAgentChat(bookDao, bookId, "👑 [Executive Orchestrator]", "🎉 Weaver 2.0 has successfully completed the Autonomous Book Production! All publishing assets saved to Downloads/Weaver_Studio/$sanitizedTitle")
            Log.d("CompilationWorker", "Compilation completed successfully")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("CompilationWorker", "Error in CompilationWorker", e)
            return@withContext Result.retry()
        }
    }
}
