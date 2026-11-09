package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.LlmOrchestrator
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.model.defaultPersonas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PolishingWorker(
    context: Context,
    params: WorkerParameters,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
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
                setForeground(createForegroundInfo(applicationContext, "Authr OS Polishing Engine", "Editorial Polish for '${book.title}'"))
            } catch (e: Exception) {
                Log.w("PolishingWorker", "Could not set foreground info", e)
            }

            logAgentChat(bookDao, bookId, "👑 [Executive Orchestrator]", "Hands-off raw drafting complete. Initiating Phase 3 Editorial Polish...")
            val bookForPolishing = bookDao.getBookById(bookId) ?: book
            bookDao.updateBook(bookForPolishing.copy(currentPhase = "Polishing"))

            // v3.0 §4 layer contract: L5 (Style Polisher) consumes CONFIRMED
            // drafts only — PROPOSED/ARCHIVED chapters are skipped and stay
            // untouched for the writer (plan Table 4, L5 row).
            val allChapters = chapterDao.getChaptersForBookSync(bookId)
            val chaptersToPolish = com.example.engine.WriterGate.canonChapters(allChapters)
            val skipped = allChapters.size - chaptersToPolish.size
            if (skipped > 0) {
                logAgentChat(bookDao, bookId, "📝 [Style Editor]", "Skipped $skipped non-canon chapter(s) — L5 polishes confirmed drafts only (writer sovereignty, plan §4).")
            }
            for (chapter in chaptersToPolish) {
                if (chapter.status == "Polished") continue
                
                logAgentChat(bookDao, bookId, "📝 [Style Editor]", "Performing a stylistic pass on Chapter ${chapter.chapterNumber}. Enhancing sensory prose, refining speech subtext, and checking pacing dynamics...")

                val polishPrompt = """
                    You are an expert editorial consultant and style editor. Perform a style polish pass on Chapter ${chapter.chapterNumber}: "${chapter.title}" of the book "${book.title}".
                    
                    Book Details:
                    Genre: ${book.genre}
                    Story Bible:
                    $storyBible
                    
                    Raw Chapter Content:
                    ---
                    ${chapter.content}
                    ---
                    
                    Improve literary quality:
                    1. Show, don't tell: add vivid sensory markers.
                    2. Dialogue: make it sound crisp, and fully consistent with the characters' cognitive profiles.
                    3. Correct any grammar or mechanical issues.
                    
                    Respond with ONLY the polished chapter content. No extra notes or labels.
                """.trimIndent()

                val persona = defaultPersonas.find { it.id == book.personaId } ?: defaultPersonas.first()
                val systemInstructionText = """
                    You are ${persona.name}, acting as a professional book editor.
                    Mode: ${persona.mode}
                    Pacing: ${persona.pacing}
                    Focus: ${persona.focus}
                    Aesthetic: ${persona.aesthetic}
                    Voice: ${persona.voice}
                """.trimIndent()

                val polishedContent = retryWithBackoff {
                    LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.EXECUTIVE, polishPrompt, systemInstructionText)
                }
                
                if (polishedContent.isNotEmpty()) {
                    // v3.0 §4 (L5 gate — side-by-side diff; accept, edit, or
                    // reject): the styled revision is a NEW PROPOSAL, never a
                    // silent overwrite of canon. The writer accepts it in the
                    // Writer Studio by confirming the chapter.
                    val polishedWords = polishedContent.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                    chapterDao.updateChapter(chapter.copy(
                        content = polishedContent.trim(),
                        status = "Proposed (polished, $polishedWords words)",
                        writerConfirmed = false,
                        draftStatus = com.example.engine.DraftState.PROPOSED
                    ))
                    logAgentChat(bookDao, bookId, "🔍 [Copy Editor]", "Styled revision for Chapter ${chapter.chapterNumber} landed as PROPOSED — accept it via Confirm in the Writer Studio.")
                } else {
                    Log.e("PolishingWorker", "Failed to polish chapter ${chapter.chapterNumber}")
                    chapterDao.updateChapter(chapter.copy(status = "Failed"))
                    logAgentChat(bookDao, bookId, "❌ [System]", "Polishing halted due to generation failure on Chapter ${chapter.chapterNumber}.")
                    val currentB = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(currentB.copy(currentPhase = "Failed"))
                    return@withContext Result.failure()
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("PolishingWorker", "Error in PolishingWorker", e)
            return@withContext Result.retry()
        }
    }
}
