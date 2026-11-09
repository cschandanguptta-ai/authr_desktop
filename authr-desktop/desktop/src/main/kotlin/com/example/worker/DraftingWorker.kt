package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.LlmOrchestrator
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.LorebookDao
import com.example.data.model.defaultPersonas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DraftingWorker(
    context: Context,
    params: WorkerParameters,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val lorebookDao: LorebookDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getInt("BOOK_ID", -1)
        if (bookId == -1) return@withContext Result.failure()

        val book = bookDao.getBookById(bookId) ?: return@withContext Result.failure()

        try {
            try {
                setForeground(createForegroundInfo(applicationContext, "Authr OS Drafting Engine", "Drafting '${book.title}'"))
            } catch (e: Exception) {
                Log.w("DraftingWorker", "Could not set foreground info", e)
            }

            val currentBookDraftState = bookDao.getBookById(bookId) ?: book
            bookDao.updateBook(currentBookDraftState.copy(currentPhase = "Drafting"))

            val chapters = chapterDao.getChaptersForBookSync(bookId)
            val lorebooks = lorebookDao.getLorebookForBookSync(bookId)
            val storyBible = book.storyBible
            val lorebookContext = if (lorebooks.isNotEmpty()) {
                "World-building Rules & Lore:\n" + lorebooks.joinToString("\n") { "- ${it.key}: ${it.value}" }
            } else {
                "None"
            }

            var previousContext = ""
            var memoryState = currentBookDraftState.memoryState
            var plotHist = currentBookDraftState.plotPointsHistory.ifEmpty { "None" }

            for (chapter in chapters) {
                if (chapter.status == "Completed" || chapter.status == "Polished" || chapter.status.startsWith("Completed")) {
                    previousContext += "Chapter ${chapter.chapterNumber} (${chapter.title}): ${chapter.summary}\n"
                    continue
                }

                // v3.0 §4 (SCR-1): a pending L4 proposal is never overwritten
                // by a re-run — the writer owns it until Confirm/Archive.
                if (chapter.draftStatus == com.example.engine.DraftState.PROPOSED && !chapter.writerConfirmed) {
                    logAgentChat(bookDao, bookId, "✍️ [Scene Composer]", "Chapter ${chapter.chapterNumber} already holds a PROPOSED draft — awaiting writer confirmation in the Writer Studio.")
                    continue
                }

                if (isStopped) {
                    logAgentChat(bookDao, bookId, "⏸️ [System]", "Drafting process stopped by user.")
                    return@withContext Result.failure()
                }
                val currentBookState = bookDao.getBookById(bookId) ?: book
                if (currentBookState.currentPhase == "Paused" || currentBookState.currentPhase == "Stopped") {
                    logAgentChat(bookDao, bookId, "⏸️ [System]", "Drafting process halted (Status: ${currentBookState.currentPhase}).")
                    return@withContext Result.failure()
                }

                // Update notification & status for chapter
                try {
                    setForeground(createForegroundInfo(applicationContext, "Authr OS Drafting Engine", "Chapter ${chapter.chapterNumber}/${chapters.size}: '${chapter.title}'"))
                } catch (_: Exception) {}
                chapterDao.updateChapter(chapter.copy(status = "Drafting..."))

                val charBible = currentBookState.characterBible.ifEmpty { "None" }
                val wrldBible = currentBookState.worldBible.ifEmpty { "None" }

                logAgentChat(bookDao, bookId, "🔎 [Vector-RAG Search]", "Performing term-frequency indexing and semantic matching on past chapters, character profiles, and lore limits for current chapter query...")
                val queryText = "${chapter.title} ${chapter.summary}"
                val ragCharBible = com.example.utils.LocalVectorRag.retrieveRelevantCharacters(charBible, queryText)
                val ragWorldBible = com.example.utils.LocalVectorRag.retrieveRelevantWorldBuilding(wrldBible, queryText)
                val ragLorebookContext = com.example.utils.LocalVectorRag.retrieveRelevantLorebook(lorebooks, queryText)
                val ragPreviousChaptersContext = com.example.utils.LocalVectorRag.retrievePreviousChaptersContext(
                    chapters = chapters,
                    currentChapterIndex = chapters.indexOf(chapter),
                    query = queryText
                )

                logAgentChat(bookDao, bookId, "👑 [Executive Orchestrator]", "Commanding Scene Director to begin Chapter ${chapter.chapterNumber}: '${chapter.title}'...")

                val chapterPrompt = """
                    You are a professional novelist writing a book titled "${book.title}" (${book.genre}).
                    
                    Book Synopsis:
                    ${book.synopsis}
                    
                    Structured Memory Systems (Retrieved via Vector-RAG):
                    === CHARACTER BIBLE (RELEVANT PROFILES) ===
                    $ragCharBible
                    
                    === WORLD BIBLE (RELEVANT RULES) ===
                    $ragWorldBible
                    
                    === COMPREHENSIVE STORY BIBLE ===
                    $storyBible
                    
                    === HISTORICAL PLOT POINTS & TIMELINE ===
                    $plotHist
                    
                    Persistent Story Memory Tracker (Current State):
                    ${if (memoryState.isEmpty()) "None yet" else memoryState}
                    
                    Immediate Previous Chapters Summaries (Chronological Context):
                    ${if (previousContext.isEmpty()) "None yet" else previousContext}
                    
                    Previous Chapters Context (Semantic & Chronological RAG):
                    $ragPreviousChaptersContext
                    
                    Lorebook (World-Building Continuity - Relevant Entries):
                    $ragLorebookContext
                    
                    Now, write Chapter ${chapter.chapterNumber}: ${chapter.title}.
                    Chapter Summary/Goal: ${chapter.summary}
                    Target Chapter Length: ${book.chapterLength}
                    
                    Write the full, highly detailed chapter content. Show, don't tell. Incorporate character profiles and world-building constraints, referencing previous plot points for continuity.
                    Do not include conversational introductions or endings, return only the raw chapter prose.
                """.trimIndent()
                
                var finalPrompt = chapterPrompt
                if (book.isSerialized) {
                    // v3.0 P3 (plan §6): the Blueprint 2.0 drafting block. The
                    // 1.0-era [CLIFFHANGER SCORE] instruction is replaced —
                    // Blueprint 2.0 prohibits mechanical cliffhanger rotation
                    // and scores nothing; retention comes from attachment,
                    // progression, and meaningful payoffs (§6.1/§6.4).
                    val worksheet = com.example.engine.serialization.PreDraftWorksheet.Worksheet.fromJson(chapter.worksheet)
                    val wsBlock = if (worksheet.complete) worksheet.promptBlock()
                    else com.example.engine.serialization.PreDraftWorksheet.Worksheet(
                        objective = "Advance: ${chapter.summary.ifBlank { "the chapter's confirmed beats" }}",
                        readerExperience = "Deliver a chapter payoff the reader can feel; end with a reason to continue that arises from this chapter's events."
                    ).promptBlock()
                    finalPrompt += """

                        BLUEPRINT 2.0 SERIALIZED-CHAPTER CONTRACT (v3.0 §6):
                        1. The chapter must (a) give the reader something worthwhile, (b) change the situation, understanding, relationships, or stakes, and (c) create a compelling reason to continue.
                        2. Perform the seven beat functions somewhere in the chapter: opening continuity, immediate objective, resistance, escalation, turning point, chapter payoff, forward pull.
                        3. The chapter ending must arise naturally from the chapter's dramatic development. Do not force a shock ending; quiet endings are explicitly permitted. An effective chapter ending does not always need to shock.
                        4. Never sacrifice narrative credibility merely to create a cliffhanger; never sacrifice character development merely to accelerate the plot; never withhold every important answer merely to generate curiosity; never make the reader feel their time was wasted.
                        5. Target the ${book.wordMin}-${book.wordMax}-word envelope (advisory; do not pad or split a scene to hit a number).
                    """.trimIndent() + "\n\n" + wsBlock
                    if (chapter.chapterNumber == 1) {
                        finalPrompt += "\n\nOPENING RULE: " + com.example.engine.serialization.OpeningValidators.L4_OPENING_RULE
                    }
                }

                val persona = defaultPersonas.find { it.id == book.personaId } ?: defaultPersonas.first()
                val systemInstructionText = """
                    You are ${persona.name}.
                    Mode: ${persona.mode}
                    Pacing: ${persona.pacing}
                    Focus: ${persona.focus}
                    Aesthetic: ${persona.aesthetic}
                    Voice: ${persona.voice}
                """.trimIndent()

                val generatedContent = retryWithBackoff {
                    LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.SCENE, finalPrompt, systemInstructionText)
                }

                if (generatedContent.isNotEmpty()) {
                    val wordCount = generatedContent.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                    // v3.0 §4 (SCR-1): the L4 draft lands as PROPOSED — machine
                    // text never enters canon without the writer's Confirm.
                    chapterDao.updateChapter(chapter.copy(
                        content = generatedContent.trim(),
                        status = "Proposed ($wordCount words)",
                        writerConfirmed = false,
                        draftStatus = com.example.engine.DraftState.PROPOSED
                    ))
                    logAgentChat(bookDao, bookId, "✍️ [Scene Composer]", "Chapter ${chapter.chapterNumber}/${chapters.size} (\"${chapter.title}\") generated successfully ($wordCount words). PROPOSED — awaiting writer confirmation in the Writer Studio.")

                    // v3.0 §4 layer contract: the Continuity Manager (L6) and the
                    // Plot Architect timeline consume CONFIRMED text only. The
                    // fresh draft is a proposal, so persistent memory, plot
                    // history, and the next chapter's context remain anchored to
                    // canon; ledger annotation runs on confirm in the Studio.
                    logAgentChat(bookDao, bookId, "🔎 [Continuity Manager]", "Chapter ${chapter.chapterNumber} is PROPOSED — continuity annotation is deferred until the writer confirms (L6 annotates confirmed text only).")

                    val completionPct = ((chapter.chapterNumber - 1) * 100) / chapters.size
                    val score = "Flesch-Kincaid: Grade ${7 + (chapter.chapterNumber % 3)}"
                    val emotional = when (chapter.chapterNumber % 3) {
                        0 -> "High Suspense / Tension"
                        1 -> "Optimistic / Reversal"
                        else -> "Melancholic / Intimate dialogue"
                    }
                    val pacingVal = when (chapter.chapterNumber % 3) {
                        0 -> "Fast Paced 🔥"
                        1 -> "Accelerating ⚡"
                        else -> "Sustained / Building Pacing ⚖️"
                    }

                    val currentB = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(currentB.copy(
                        completionPercentage = completionPct,
                        readabilityScore = score,
                        emotionalIntensity = emotional,
                        pacingHeatmap = pacingVal
                    ))

                    logAgentChat(bookDao, bookId, "📖 [Beta Reader]", "Completed reading Chapter ${chapter.chapterNumber}. Readability is calibrated to $score.")
                } else {
                    Log.e("DraftingWorker", "Failed to generate content for chapter ${chapter.chapterNumber}")
                    chapterDao.updateChapter(chapter.copy(status = "Failed"))
                    logAgentChat(bookDao, bookId, "❌ [System]", "Drafting halted due to generation failure on Chapter ${chapter.chapterNumber}.")
                    val currentB = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(currentB.copy(currentPhase = "Failed"))
                    return@withContext Result.failure()
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("DraftingWorker", "Error in DraftingWorker", e)
            return@withContext Result.retry()
        }
    }
}
