package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.EditorialMemory
import kotlinx.coroutines.flow.Flow

/**
 * Editorial intelligence layer DAO (ADR-0018). All access goes through
 * EditorialMemoryStore, which enforces slot names and JSON payload shapes.
 */
@Dao
interface EditorialMemoryDao {

    @Query("SELECT * FROM editorial_memory WHERE bookId = :bookId AND slot = :slot LIMIT 1")
    suspend fun getSlot(bookId: Int, slot: String): EditorialMemory?

    @Query("SELECT * FROM editorial_memory WHERE bookId = :bookId")
    suspend fun getAllForBook(bookId: Int): List<EditorialMemory>

    @Query("SELECT * FROM editorial_memory WHERE bookId = :bookId")
    fun observeAllForBook(bookId: Int): Flow<List<EditorialMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: EditorialMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<EditorialMemory>)

    @Query("DELETE FROM editorial_memory WHERE bookId = :bookId AND slot = :slot")
    suspend fun clearSlot(bookId: Int, slot: String)

    @Query("DELETE FROM editorial_memory WHERE bookId = :bookId")
    suspend fun clearBook(bookId: Int)
}
