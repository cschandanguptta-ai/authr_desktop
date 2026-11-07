package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.data.api.LlmOrchestrator
import com.example.data.local.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorldBuildingWorker(
    context: Context,
    params: WorkerParameters,
    private val bookDao: BookDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getInt("BOOK_ID", -1)
        if (bookId == -1) return@withContext Result.failure()
        if (isStopped) return@withContext Result.failure()

        val book = bookDao.getBookById(bookId) ?: return@withContext Result.failure()
        if (book.currentPhase == "Paused" || book.currentPhase == "Stopped") return@withContext Result.failure()

        try {
            try {
                setForeground(createForegroundInfo(applicationContext, "Authr OS Autonomous Engine", "Worldbuilding & Story Bible for '${book.title}'"))
            } catch (e: Exception) {
                Log.w("WorldBuildingWorker", "Could not set foreground info", e)
            }

            logAgentChat(bookDao, bookId, "👑 [Executive Orchestrator]", "Assembling specialized Weaver 2.0 sub-agents for '${book.title}'...")
            
            var storyBible = book.storyBible
            if (storyBible.isEmpty()) {
                logAgentChat(bookDao, bookId, "🌍 [World Builder]", "Compiling comprehensive Story Bible, Character profiles, setting laws, and relationship graphs...")
                val biblePrompt = """
                    You are a world-builder and novelist. Create a comprehensive Story Bible for the book:
                    Title: ${book.title}
                    Genre: ${book.genre}
                    Synopsis: ${book.synopsis}
                    Narrative Structure: ${book.narrativeStructure}
                    
                    Respond with the following structured sections:
                    1. Story Bible Overview & Theme
                    2. Character Cognitive Profiles (appearance, goals, beliefs, fears, needs, values, traumas, secrets, relationships, speech rhythm, dialogue styles)
                    3. World Bible Setting Laws (major locations, historical facts, societal rules)
                    4. Relationship Graph, Theme Graph, and Conflict Graph
                    5. Mystery and Romance Trackers (clues, subplots, milestones)
                    6. Foreshadowing Plan
                """.trimIndent()

                storyBible = retryWithBackoff {
                    LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.WORLD, biblePrompt)
                }

                if (storyBible.isNotEmpty()) {
                    logAgentChat(bookDao, bookId, "🧠 [Character Psychologist]", "Generating structured Character Bible profiling appearance, cognitive schemas, and relationship graphs...")
                    val charPrompt = "Based on the Story Bible and synopsis below, compile a highly structured Character Bible.\nFor each character, detail their core motivations, fears, speech patterns, dialogue vocabulary, and secrets.\n\nStory Bible:\n$storyBible\n\nSynopsis:\n${book.synopsis}"
                    val characterBible = retryWithBackoff {
                        LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.CHARACTER, charPrompt)
                    }

                    logAgentChat(bookDao, bookId, "🌍 [World Builder]", "Generating structured World Bible outlining setting laws, locations, and cultural or technological constraints...")
                    val worldPrompt = "Based on the Story Bible and synopsis below, compile a highly structured World Bible.\nDetail the major settings, environmental rules, cultural structures, and historical context of the world.\n\nStory Bible:\n$storyBible"
                    val worldBible = retryWithBackoff {
                        LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.WORLD, worldPrompt)
                    }

                    logAgentChat(bookDao, bookId, "🕵️ [Continuity Manager]", "Analyzing Story, Character, and World Bibles for plot holes and contradictions (Lorebook Enforcer)...")
                    val continuityPrompt = """
                        You are the Continuity Manager & Lorebook Enforcer.
                        Review the following Story Bible, Character Bible, and World Bible for any plot holes, timeline contradictions, or character trait inconsistencies.
                        Fix any contradictions you find to ensure a cohesive and logical narrative framework.
                        Output ONLY the corrected, unified Story Bible containing all the updated sections. Maintain the original structure but with the inconsistencies resolved.
                        
                        Original Story Bible:
                        $storyBible
                        
                        Character Bible:
                        $characterBible
                        
                        World Bible:
                        $worldBible
                    """.trimIndent()
                    val correctedBible = retryWithBackoff {
                        LlmOrchestrator.callAgent(applicationContext, LlmOrchestrator.Agent.CONTINUITY, continuityPrompt)
                    }
                    val finalStoryBible = if (correctedBible.isNotEmpty()) correctedBible else storyBible
                    if (correctedBible.isNotEmpty()) {
                        logAgentChat(bookDao, bookId, "🕵️ [Continuity Manager]", "Inconsistencies resolved. Unified and corrected Story Bible finalized.")
                    }

                    val currentBook = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(currentBook.copy(
                        storyBible = finalStoryBible,
                        characterBible = characterBible,
                        worldBible = worldBible,
                        plotPointsHistory = "Plot timeline initialized with outline and Story Bible core themes."
                    ))
                    logAgentChat(bookDao, bookId, "🌍 [World Builder]", "Story Bible, Character Bible, and World Bible successfully written to persistent structured database.")
                } else {
                    logAgentChat(bookDao, bookId, "❌ [System]", "Failed to generate story bibles. Halting.")
                    val b = bookDao.getBookById(bookId) ?: book
                    bookDao.updateBook(b.copy(currentPhase = "Failed"))
                    return@withContext Result.failure()
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("WorldBuildingWorker", "Error in WorldBuildingWorker", e)
            return@withContext Result.retry()
        }
    }
}
