package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Lorebook
import kotlinx.coroutines.flow.Flow

@Dao
interface LorebookDao {
    @Query("SELECT * FROM lorebook WHERE bookId = :bookId")
    fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>>

    @Query("SELECT * FROM lorebook WHERE bookId = :bookId")
    suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLorebook(lorebook: Lorebook): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLorebooks(lorebooks: List<Lorebook>)

    @Update
    suspend fun updateLorebook(lorebook: Lorebook)

    @Query("DELETE FROM lorebook WHERE id = :lorebookId")
    suspend fun deleteLorebook(lorebookId: Int)
}
