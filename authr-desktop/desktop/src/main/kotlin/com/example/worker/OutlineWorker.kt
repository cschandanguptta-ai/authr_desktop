package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.LlmOrchestrator
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.LorebookDao
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutlineWorker(
    context: Context,
    params: WorkerParameters,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val lorebookDao: LorebookDao
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
                setForeground(createForegroundInfo(applicationContext, "Authr OS Autonomous Engine", "Outlining '${book.title}'"))
            } catch (e: Exception) {
                Log.w("OutlineWorker", "Could not set foreground info", e)
            }

            var currentOutline = book.outline
            
            if (currentOutline.isEmpty()) {
                logAgentChat(bookDao, bookId, "📐 [Plot Architect]", "Generating chapter-by-chapter Narrative Blueprint...")
                val outlinePrompt = if (book.isSerialized) {
                    """
                    You are a professional webnovel author. Create a chapter-by-chapter Narrative Blueprint for an episodic serialized webnovel with EXACTLY ${book.targetChapters} chapters.
                    Title: ${book.title}
                    Genre: ${book.genre}
                    Synopsis: ${book.synopsis}
                    
                    Use the Story Bible for guidance:
                    $storyBible
                    
                    Respond ONLY with a numbered list from 1 to ${book.targetChapters}. Keep summaries extremely concise (1 sentence max per chapter) to ensure the full list fits in your response. Format exactly like:
                    1. Episode 1 Title: Summary.
                    2. Episode 2 Title: Summary.
                    """.trimIndent()
                } else {
                    """
                    You are a professional novelist. Create a comprehensive chapter-by-chapter Narrative Blueprint for a book with the following details:
                    Title: ${book.title}
                    Genre: ${book.genre}
                    Synopsis: ${book.synopsis}
                    Narrative Structure: ${book.narrativeStructure}
                    Target Word Count: ${book.targetWordCount}
                    Chapter Length: ${book.chapterLength}
                    
                    Use the Story Bible for guidance:
                    $storyBible
                    
                    Respond ONLY with a numbered list of chapters, formatted exactly like:
                    1. Chapter 1 Title: Summary of what happens in this chapter.
                    2. Chapter 2 Title: Summary of what happens in this chapter.
                    """.trimIndent()
                }

                val generatedOutline = retryWithBackoff {
                    LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.PLOT, outlinePrompt)
                }

                if (generatedOutline.isNotEmpty()) {
                    currentOutline = generatedOutline
                    val currentBook = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(currentBook.copy(outline = currentOutline, currentPhase = "Drafting"))

                    // v3.0 P3 (plan §6.2, Table 9 chapter-1 row): the outline
                    // gate — Chapter 1 must carry the inciting beat before the
                    // L2 outline can be approved for a serial.
                    if (book.isSerialized &&
                        !com.example.engine.serialization.OpeningValidators.outlineCarriesIncitingBeat(currentOutline)
                    ) {
                        logAgentChat(bookDao, bookId, "📐 [Plot Architect]",
                            "Outline gate: the serial's chapter 1 does not carry the inciting beat — regenerate the outline or edit episode 1 to open on the disruption, then re-run.")
                        val b = bookDao.getBookById(bookId) ?: currentBook
                        bookDao.updateBook(b.copy(currentPhase = "Failed"))
                        return@withContext Result.failure()
                    }
                } else {
                    logAgentChat(bookDao, bookId, "❌ [System]", "Failed to generate outline from LLM. Halting.")
                    val b = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(b.copy(currentPhase = "Failed"))
                    return@withContext Result.failure()
                }
            } else {
                val currentBook = bookDao.getBookById(bookId) ?: book
                bookDao.updateBook(currentBook.copy(currentPhase = "Drafting"))
            }

            val existingChapters = chapterDao.getChaptersForBookSync(bookId)
            if (existingChapters.isEmpty() && currentOutline.isNotEmpty()) {
                val regex = Regex("""(\d+)\.\s*(.*?)(?:[:\-])\s*(.*)""")
                val matches = regex.findAll(currentOutline)
                val chaptersToInsert = matches.map { matchResult ->
                    Chapter(
                        bookId = bookId,
                        chapterNumber = matchResult.groupValues[1].toIntOrNull() ?: 0,
                        title = matchResult.groupValues[2].trim(),
                        content = "",
                        summary = matchResult.groupValues[3].trim(),
                        status = "Drafting"
                    )
                }.toList()

                if (chaptersToInsert.isNotEmpty()) {
                    chapterDao.insertChapters(chaptersToInsert)
                    logAgentChat(bookDao, bookId, "📐 [Plot Architect]", "Chapter Outline completed and verified. Registered ${chaptersToInsert.size} sequential writing nodes.")
                } else {
                    logAgentChat(bookDao, bookId, "❌ [System]", "Failed to parse chapter outline. Halting.")
                    val b = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(b.copy(currentPhase = "Failed"))
                    return@withContext Result.failure()
                }
            }

            val existingLore = lorebookDao.getLorebookForBookSync(bookId)
            if (existingLore.isEmpty()) {
                logAgentChat(bookDao, bookId, "🌍 [World Builder]", "Synthesizing custom Lorebook continuity rules from Story Bible...")
                val lorePrompt = "Based on this book:\nTitle: ${book.title}\nGenre: ${book.genre}\nSynopsis: ${book.synopsis}\nStory Bible:\n$storyBible\n\nGenerate exactly 4 key world-building/character continuity rules.\nFormat them strictly as key-value lines, e.g.:\nProtagonist: [Details of protagonist, personality, goals]\nSetting: [Details of the location, rules of the world, atmosphere]\nCore Conflict: [Details of the central struggle and antagonist]\nKey Object: [Details of a central item/power]\n\nProvide ONLY the key-value lines, do not add introductory text."
                val loreText = retryWithBackoff {
                    LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.WORLD, lorePrompt)
                }
                if (loreText.isNotEmpty()) {
                    val loreEntries = mutableListOf<Lorebook>()
                    loreText.lines().forEach { line ->
                        if (line.contains(":")) {
                            val parts = line.split(":", limit = 2)
                            val key = parts[0].trim().replace("- ", "")
                            val value = parts[1].trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                loreEntries.add(Lorebook(bookId = bookId, key = key, value = value))
                            }
                        }
                    }
                    if (loreEntries.isNotEmpty()) {
                        lorebookDao.insertLorebooks(loreEntries)
                    }
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("OutlineWorker", "Error in OutlineWorker", e)
            return@withContext Result.retry()
        }
    }
}
