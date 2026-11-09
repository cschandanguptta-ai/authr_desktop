package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.QuestionEntry
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the question ledger (v3.0, plan §6.7): the three information
 * levels (IMMEDIATE / PROGRESSIVE / LONG_TERM) with expected-answer windows.
 */
@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE bookId = :bookId ORDER BY openedChapter ASC")
    fun getQuestionsForBook(bookId: Int): Flow<List<QuestionEntry>>

    @Query("SELECT * FROM questions WHERE bookId = :bookId ORDER BY openedChapter ASC")
    suspend fun getQuestionsForBookSync(bookId: Int): List<QuestionEntry>

    /** Progressive questions whose expected-answer window has passed. */
    @Query(
        "SELECT * FROM questions WHERE bookId = :bookId AND level = 'PROGRESSIVE' " +
            "AND answeredChapter IS NULL AND dueChapter IS NOT NULL AND dueChapter <= :currentChapter"
    )
    suspend fun getOverdueProgressiveQuestions(bookId: Int, currentChapter: Int): List<QuestionEntry>

    @Query("SELECT * FROM questions WHERE id = :questionId LIMIT 1")
    suspend fun getQuestionById(questionId: Int): QuestionEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntry>)

    @Update
    suspend fun updateQuestion(question: QuestionEntry)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestion(questionId: Int)
}
