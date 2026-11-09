package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SerialArc
import kotlinx.coroutines.flow.Flow

/** DAO for the serial micro-arc planner (v3.0, plan §6.5). */
@Dao
interface SerialArcDao {
    @Query("SELECT * FROM serial_arcs WHERE bookId = :bookId ORDER BY arcIndex ASC")
    fun getArcsForBook(bookId: Int): Flow<List<SerialArc>>

    @Query("SELECT * FROM serial_arcs WHERE bookId = :bookId ORDER BY arcIndex ASC")
    suspend fun getArcsForBookSync(bookId: Int): List<SerialArc>

    @Query("SELECT * FROM serial_arcs WHERE id = :arcId LIMIT 1")
    suspend fun getArcById(arcId: Int): SerialArc?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArc(arc: SerialArc): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArcs(arcs: List<SerialArc>)

    @Update
    suspend fun updateArc(arc: SerialArc)

    @Query("DELETE FROM serial_arcs WHERE id = :arcId")
    suspend fun deleteArc(arcId: Int)

    @Query("DELETE FROM serial_arcs WHERE bookId = :bookId")
    suspend fun deleteArcsForBook(bookId: Int)
}
