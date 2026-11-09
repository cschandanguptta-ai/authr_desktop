package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.AppRepository
import com.example.managers.EpubManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EpubExportWorker(
    private val context: Context,
    params: WorkerParameters,
    private val repository: AppRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getInt("BOOK_ID", -1)
        val storyId = inputData.getInt("STORY_ID", -1)

        Log.d("EpubExportWorker", "Starting EPUB export worker for bookId=$bookId, storyId=$storyId")

        try {
            val prefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
            val authorName = prefs.getString("author_name", "") ?: ""
            val authorPenName = prefs.getString("author_pen_name", "") ?: ""
            val authorBio = prefs.getString("author_bio", "") ?: ""
            val authorPublisher = prefs.getString("author_publisher", "") ?: ""
            val authorWebsite = prefs.getString("author_website", "") ?: ""
            val authorSocial = prefs.getString("author_social", "") ?: ""

            if (bookId != -1) {
                val book = repository.getBookById(bookId) ?: return@withContext Result.failure()
                // v3.0 §4 P1 exit gate: no unconfirmed text reaches any export —
                // the EPUB exporter consumes canon chapters only.
                val allChapters = repository.getChaptersForBookSync(bookId)
                val chapters = com.example.engine.WriterGate.canonChapters(allChapters)
                if (chapters.size < allChapters.size) {
                    android.util.Log.w("EpubExportWorker",
                        "Writer gate: excluded ${allChapters.size - chapters.size} unconfirmed chapter(s) from the EPUB export.")
                }
                
                // Construct a temporary Story object representation for EpubManager
                val fullTextParts = chapters.map { "${it.title}\n\n${it.content}" }
                val story = com.example.data.model.Story(
                    id = book.id,
                    title = book.title,
                    heroName = "Protagonist",
                    genres = book.genre,
                    format = book.format,
                    duration = "${chapters.size} Chapters",
                    hook = book.synopsis.take(100),
                    extras = "",
                    part1 = fullTextParts.getOrNull(0) ?: "",
                    part2 = fullTextParts.getOrNull(1) ?: "",
                    part3 = fullTextParts.getOrNull(2) ?: "",
                    part4 = fullTextParts.getOrNull(3) ?: "",
                    part5 = fullTextParts.drop(4).joinToString("\n\n"),
                    metaDesc = book.synopsis
                )

                val success = EpubManager.exportToEpub(
                    context = context,
                    story = story,
                    authorName = authorName,
                    authorPenName = authorPenName,
                    authorBio = authorBio,
                    authorPublisher = authorPublisher,
                    authorWebsite = authorWebsite,
                    authorSocial = authorSocial
                )

                if (success) {
                    Log.d("EpubExportWorker", "Successfully exported EPUB for Book ID $bookId")
                    return@withContext Result.success()
                } else {
                    Log.e("EpubExportWorker", "Failed to export EPUB for Book ID $bookId")
                    return@withContext Result.retry()
                }
            } else if (storyId != -1) {
                val story = repository.getStoryById(storyId) ?: return@withContext Result.failure()
                val success = EpubManager.exportToEpub(
                    context = context,
                    story = story,
                    authorName = authorName,
                    authorPenName = authorPenName,
                    authorBio = authorBio,
                    authorPublisher = authorPublisher,
                    authorWebsite = authorWebsite,
                    authorSocial = authorSocial
                )

                return@withContext if (success) Result.success() else Result.retry()
            }

            return@withContext Result.failure()
        } catch (e: Exception) {
            Log.e("EpubExportWorker", "Error exporting EPUB: ${e.message}", e)
            return@withContext Result.failure()
        }
    }
}
