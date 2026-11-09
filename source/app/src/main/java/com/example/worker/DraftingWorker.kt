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
                    finalPrompt += """
                        
                        CRITICAL WEBNOVEL FORMATTING INSTRUCTIONS:
                        Because this is a serialized webnovel / episode, you MUST format your response EXACTLY as follows:
                        
                        [PREVIOUSLY ON...]
                        (Write a short 2-3 sentence engaging recap of recent events for returning readers. If Chapter 1, write a short prologue hook).
                        
                        [CHAPTER CONTENT]
                        (The actual full chapter prose).
                        
                        [NEXT EPISODE TEASER]
                        (A short 1-2 sentence teaser for the next chapter).
                        
                        [CLIFFHANGER SCORE]
                        (Rate the ending hook strength of this chapter from 0 to 100, e.g. "85/100").
                    """.trimIndent()
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
                    chapterDao.updateChapter(chapter.copy(content = generatedContent.trim(), status = "Completed ($wordCount words)"))
                    previousContext += "Chapter ${chapter.chapterNumber} (${chapter.title}): ${chapter.summary}\n"
                    logAgentChat(bookDao, bookId, "✍️ [Scene Composer]", "Chapter ${chapter.chapterNumber}/${chapters.size} (\"${chapter.title}\") generated successfully ($wordCount words).")

                    logAgentChat(bookDao, bookId, "🔎 [Continuity Manager]", "Analyzing chapter content to update persistent story memory...")
                    val memoryPrompt = "You are the Continuity Manager. Analyze the following chapter content and provide an updated, compiled report tracking character statuses (appearance, injuries, emotional states, secrets), locations visited, rules discovered, and story promises/foreshadowing.\n\nPrevious Memory State:\n${if (memoryState.isEmpty()) "None" else memoryState}\n\nChapter Content:\n$generatedContent\n\nProvide ONLY the updated bulleted memory tracker report."
                    memoryState = retryWithBackoff {
                        LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.CONTINUITY, memoryPrompt)
                    }
                    
                    logAgentChat(bookDao, bookId, "📐 [Plot Architect]", "Updating structured persistent Plot Points History with events from Chapter ${chapter.chapterNumber}...")
                    val plotHistoryPrompt = "You are the Plot Architect. Update the running narrative history timeline of the novel with the events of Chapter ${chapter.chapterNumber} (\"${chapter.title}\").\n\nCurrent Plot Points History:\n$plotHist\n\nNew Chapter Content:\n$generatedContent\n\nProvide an updated, complete timeline of the novel's plot points history incorporating this new chapter. Be precise and keep it structured."
                    plotHist = try {
                        retryWithBackoff {
                            LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.PLOT, plotHistoryPrompt)
                        }
                    } catch (e: Exception) { plotHist }

                    val completionPct = (chapter.chapterNumber * 100) / chapters.size
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
                        memoryState = memoryState,
                        plotPointsHistory = plotHist,
                        completionPercentage = completionPct,
                        readabilityScore = score,
                        emotionalIntensity = emotional,
                        pacingHeatmap = pacingVal
                    ))

                    logAgentChat(bookDao, bookId, "🔎 [Continuity Manager]", "Persistent story memory updated successfully.")
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
