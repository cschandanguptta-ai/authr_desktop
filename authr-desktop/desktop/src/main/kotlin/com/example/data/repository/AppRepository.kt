package com.example.data.repository

import com.example.data.local.AudioTrackDao
import com.example.data.local.StoryDao
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.ChekhovGunDao
import com.example.data.local.LorebookDao
import com.example.data.model.AudioTrack
import com.example.data.model.Story
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val storyDao: StoryDao,
    private val audioTrackDao: AudioTrackDao,
    val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val lorebookDao: LorebookDao,
    private val chekhovGunDao: ChekhovGunDao? = null
) {
    val allStories: Flow<List<Story>> = storyDao.getAllStories()
    val allAudioTracks: Flow<List<AudioTrack>> = audioTrackDao.getAllAudioTracks()

    suspend fun getStoryById(id: Int): Story? = storyDao.getStoryById(id)

    suspend fun insertStory(story: Story): Long = storyDao.insertStory(story)

    suspend fun updateStory(story: Story) = storyDao.updateStory(story)

    suspend fun deleteStory(story: Story) = storyDao.deleteStory(story)

    suspend fun deleteStoryById(id: Int) = storyDao.deleteStoryById(id)

    suspend fun insertAudioTrack(audioTrack: AudioTrack): Long = audioTrackDao.insertAudioTrack(audioTrack)

    suspend fun deleteAudioTrack(audioTrack: AudioTrack) = audioTrackDao.deleteAudioTrack(audioTrack)

    suspend fun deleteAudioTrackById(id: Int) = audioTrackDao.deleteAudioTrackById(id)

    suspend fun searchStories(query: String): List<Story> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized.isBlank()) return emptyList()
        return try {
            storyDao.searchStories(sanitized)
        } catch (e: Exception) {
            android.util.Log.w("AppRepository", "FTS searchStories failed for query: $query", e)
            emptyList()
        }
    }

    suspend fun searchChapters(query: String): List<Chapter> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized.isBlank()) return emptyList()
        return try {
            chapterDao.searchChapters(sanitized)
        } catch (e: Exception) {
            android.util.Log.w("AppRepository", "FTS searchChapters failed for query: $query", e)
            emptyList()
        }
    }

    private fun sanitizeFtsQuery(query: String): String {
        val cleaned = query.replace(Regex("""["*^]"""), " ").trim()
        val tokens = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "\"$it\"*" }
    }

    // Book methods
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    suspend fun getBooksSync(): List<Book> = bookDao.getBooksSync()
    suspend fun getBookById(id: Int): Book? = bookDao.getBookById(id)
    suspend fun insertBook(book: Book): Long = bookDao.insertBook(book)
    suspend fun updateBook(book: Book) = bookDao.updateBook(book)
    suspend fun deleteBook(id: Int) = bookDao.deleteBook(id)

    // Chapter methods
    fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> = chapterDao.getChaptersForBook(bookId)
    suspend fun getChaptersForBookSync(bookId: Int): List<Chapter> = chapterDao.getChaptersForBookSync(bookId)
    suspend fun getProposedChaptersForBookSync(bookId: Int): List<Chapter> = chapterDao.getProposedChaptersForBookSync(bookId)
    suspend fun getChapterById(id: Int): Chapter? = chapterDao.getChapterById(id)
    suspend fun insertChapter(chapter: Chapter): Long = chapterDao.insertChapter(chapter)
    suspend fun insertChapters(chapters: List<Chapter>) = chapterDao.insertChapters(chapters)
    suspend fun updateChapter(chapter: Chapter) = chapterDao.updateChapter(chapter)
    suspend fun deleteChapter(id: Int) = chapterDao.deleteChapter(id)

    // Lorebook methods
    fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> = lorebookDao.getLorebookForBook(bookId)
    suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook> = lorebookDao.getLorebookForBookSync(bookId)
    suspend fun insertLorebook(lorebook: Lorebook): Long = lorebookDao.insertLorebook(lorebook)
    suspend fun insertLorebooks(lorebooks: List<Lorebook>) = lorebookDao.insertLorebooks(lorebooks)
    suspend fun updateLorebook(lorebook: Lorebook) = lorebookDao.updateLorebook(lorebook)
    suspend fun deleteLorebook(id: Int) = lorebookDao.deleteLorebook(id)

    // Chekhov continuity ledger methods (v3.0, plan §5.2; nullable DAO keeps
    // older construction sites and JVM tests without a ledger working).
    fun getGunsForBook(bookId: Int): Flow<List<ChekhovGun>> =
        chekhovGunDao?.getGunsForBook(bookId) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun> =
        chekhovGunDao?.getUnresolvedGuns(bookId) ?: emptyList()

    suspend fun insertGun(gun: ChekhovGun): Long = chekhovGunDao?.insertGun(gun) ?: -1L

    suspend fun insertGuns(guns: List<ChekhovGun>) { if (guns.isNotEmpty()) chekhovGunDao?.insertGuns(guns) }

    suspend fun updateGun(gun: ChekhovGun) = chekhovGunDao?.updateGun(gun) ?: Unit

    suspend fun deleteGun(id: Int) = chekhovGunDao?.deleteGun(id) ?: Unit
}
