package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ComplianceCheck
import com.example.data.model.ResearchFact
import com.example.data.model.ResearchSource
import kotlinx.coroutines.flow.Flow

/**
 * Research vault persistence (ADR-0021, spec `research_sources` +
 * `research_facts`). Facts cascade from sources (sourceId FK, ON DELETE
 * CASCADE in the migration DDL).
 */
@Dao
interface ResearchDao {

    // ---- sources -------------------------------------------------------

    @Insert
    suspend fun insertSource(source: ResearchSource): Long

    @Query("SELECT * FROM research_sources WHERE bookId = :bookId ORDER BY id DESC")
    suspend fun sourcesForBook(bookId: Int): List<ResearchSource>

    @Query("SELECT * FROM research_sources WHERE bookId = :bookId ORDER BY id DESC")
    fun observeSourcesForBook(bookId: Int): Flow<List<ResearchSource>>

    @Query("SELECT * FROM research_sources WHERE id = :id LIMIT 1")
    suspend fun sourceById(id: Int): ResearchSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: ResearchSource)

    @Query("DELETE FROM research_sources WHERE id = :id")
    suspend fun deleteSource(id: Int)

    // ---- facts ---------------------------------------------------------

    @Insert
    suspend fun insertFact(fact: ResearchFact): Long

    @Insert
    suspend fun insertFacts(facts: List<ResearchFact>)

    @Query("SELECT * FROM research_facts WHERE bookId = :bookId ORDER BY id DESC")
    suspend fun factsForBook(bookId: Int): List<ResearchFact>

    @Query("SELECT * FROM research_facts WHERE bookId = :bookId ORDER BY id DESC")
    fun observeFactsForBook(bookId: Int): Flow<List<ResearchFact>>

    @Query("SELECT * FROM research_facts WHERE sourceId = :sourceId")
    suspend fun factsForSource(sourceId: Int): List<ResearchFact>

    /** Facts cited by a chapter that are still unverified (Invariant 17 input). */
    @Query(
        "SELECT * FROM research_facts WHERE bookId = :bookId AND verified = 0 " +
            "AND chapterRefs IS NOT NULL"
    )
    suspend fun unverifiedCitedFacts(bookId: Int): List<ResearchFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFact(fact: ResearchFact)

    @Query("DELETE FROM research_facts WHERE bookId = :bookId")
    suspend fun clearFacts(bookId: Int)
}

/**
 * Compliance gate persistence (ADR-0022, spec `compliance_checks`): the
 * latest result row per (bookId, checkType, scope); unresolved blocking
 * rows stop packaging (Invariant 18 via [ComplianceCheck.blocksPackaging]).
 */
@Dao
interface ComplianceDao {

    @Insert
    suspend fun insert(check: ComplianceCheck): Long

    @Insert
    suspend fun insertAll(checks: List<ComplianceCheck>)

    @Query("SELECT * FROM compliance_checks WHERE bookId = :bookId ORDER BY id DESC")
    suspend fun forBook(bookId: Int): List<ComplianceCheck>

    @Query("SELECT * FROM compliance_checks WHERE bookId = :bookId ORDER BY id DESC")
    fun observeForBook(bookId: Int): Flow<List<ComplianceCheck>>

    @Query("SELECT * FROM compliance_checks WHERE bookId = :bookId AND checkType = :checkType ORDER BY id DESC LIMIT 1")
    suspend fun latestForType(bookId: Int, checkType: String): ComplianceCheck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(check: ComplianceCheck)

    @Query("DELETE FROM compliance_checks WHERE bookId = :bookId")
    suspend fun clearBook(bookId: Int)
}
