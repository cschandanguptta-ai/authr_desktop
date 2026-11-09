package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChekhovGun
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the Chekhov continuity ledger (v3.0, plan §5.2).
 * The final-act recovery gate reads UNRESOLVED = status IN
 * ('INTRODUCED','ESCALATED','ORPHANED') before a compile step.
 */
@Dao
interface ChekhovGunDao {
    @Query("SELECT * FROM chekhov_guns WHERE bookId = :bookId ORDER BY introducedChapter ASC")
    fun getGunsForBook(bookId: Int): Flow<List<ChekhovGun>>

    @Query("SELECT * FROM chekhov_guns WHERE bookId = :bookId ORDER BY introducedChapter ASC")
    suspend fun getGunsForBookSync(bookId: Int): List<ChekhovGun>

    @Query(
        "SELECT * FROM chekhov_guns WHERE bookId = :bookId AND status IN " +
            "('INTRODUCED', 'ESCALATED', 'ORPHANED') ORDER BY introducedChapter ASC"
    )
    suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun>

    @Query("SELECT * FROM chekhov_guns WHERE id = :gunId LIMIT 1")
    suspend fun getGunById(gunId: Int): ChekhovGun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGun(gun: ChekhovGun): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuns(guns: List<ChekhovGun>)

    @Update
    suspend fun updateGun(gun: ChekhovGun)

    @Query("DELETE FROM chekhov_guns WHERE id = :gunId")
    suspend fun deleteGun(gunId: Int)
}
