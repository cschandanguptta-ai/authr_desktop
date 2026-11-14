package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SummaryCache
import com.example.data.model.VoiceDriftLog
import kotlinx.coroutines.flow.Flow

/**
 * Voice-engine persistence (ADR-0019, spec `voice_drift_log`): append-only
 * drift measurements plus the cumulative EMA series per book.
 */
@Dao
interface VoiceDriftDao {

    @Insert
    suspend fun insert(row: VoiceDriftLog): Long

    @Query("SELECT * FROM voice_drift_log WHERE bookId = :bookId ORDER BY id ASC")
    suspend fun forBook(bookId: Int): List<VoiceDriftLog>

    @Query("SELECT * FROM voice_drift_log WHERE bookId = :bookId ORDER BY id ASC")
    fun observeForBook(bookId: Int): Flow<List<VoiceDriftLog>>

    /** The latest chapter-level measurement (beatIndex IS NULL) — the EMA seed. */
    @Query(
        "SELECT * FROM voice_drift_log WHERE bookId = :bookId AND chapterId = :chapterId " +
            "AND beatIndex IS NULL ORDER BY id DESC LIMIT 1"
    )
    suspend fun latestForChapter(bookId: Int, chapterId: Int): VoiceDriftLog?

    @Query("DELETE FROM voice_drift_log WHERE bookId = :bookId")
    suspend fun clearBook(bookId: Int)
}

/**
 * Hierarchical summary cache (ADR-0020, spec `summary_cache`): keyed by
 * (bookId, scope, level); SHA-256 [SummaryCache.sourceHash] drives
 * invalidation before any read is trusted.
 */
@Dao
interface SummaryCacheDao {

    @Query("SELECT * FROM summary_cache WHERE bookId = :bookId AND scope = :scope AND level = :level LIMIT 1")
    suspend fun get(bookId: Int, scope: String, level: String): SummaryCache?

    @Query("SELECT * FROM summary_cache WHERE bookId = :bookId AND scope = :scope")
    suspend fun allLevelsForScope(bookId: Int, scope: String): List<SummaryCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SummaryCache)

    @Query("DELETE FROM summary_cache WHERE bookId = :bookId AND scope = :scope")
    suspend fun invalidateScope(bookId: Int, scope: String)

    @Query("DELETE FROM summary_cache WHERE bookId = :bookId")
    suspend fun clearBook(bookId: Int)
}
