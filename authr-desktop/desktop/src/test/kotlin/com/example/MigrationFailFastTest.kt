package com.example

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The migration policy (v3.0 D-02, OQ-3 decided 2026-11-12): FAIL LOUD,
 * never silently wipe. `fallbackToDestructiveMigration(true)` is GONE from
 * [AppDatabase.getDatabase]; this guard proves the loud half against the
 * REAL Room 2.8.5 KMP runtime the app ships — a database file whose schema
 * version has no contiguous migration path throws instead of being
 * recreated empty, and the file keeps its rows.
 */
class MigrationFailFastTest {

    /** Creates a real SQLite file stamped with an old, un-migratable schema version. */
    private fun oldVersionFile(version: Int): File {
        val dir = kotlin.io.path.createTempDirectory("migration-failfast").toFile()
        val dbFile = File(dir, "old_db")
        exec(dbFile, "CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
        exec(dbFile, "INSERT INTO books (id, title) VALUES (1, 'The Manuscript That Must Survive')")
        exec(dbFile, "PRAGMA user_version = $version")
        return dbFile
    }

    /** The KMP execution pattern (AppDatabase's own private exec helper). */
    private fun exec(dbFile: File, sql: String) {
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            conn.prepare(sql).use { it.step() }
        } finally {
            conn.close()
        }
    }

    private fun userVersion(dbFile: File): Long {
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            return conn.prepare("PRAGMA user_version").use { stmt ->
                check(stmt.step()) { "no user_version row" }
                stmt.getLong(0)
            }
        } finally {
            conn.close()
        }
    }

    private fun firstTitle(dbFile: File): String {
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            return conn.prepare("SELECT title FROM books WHERE id = 1").use { stmt ->
                check(stmt.step()) { "the row is gone" }
                stmt.getText(0)
            }
        } finally {
            conn.close()
        }
    }

    @Test
    fun `a schema gap throws instead of wiping the database`() {
        val dbFile = oldVersionFile(version = 3)
        try {
            // No migrations registered: 3 -> 17 has no path. With the old
            // fallbackToDestructiveMigration(true) this SILENTLY RECREATED
            // the file (the manuscript died); now it must throw.
            val db = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                val ex = assertFailsWith<IllegalStateException> {
                    runBlocking { db.useConnection(false) {} } // forces open + migration validation
                }
                assertTrue(
                    ex.message?.contains("migration") == true,
                    "the failure names the migration gap: ${ex.message}"
                )
            } finally {
                db.close()
            }
            // the file was NOT destructively recreated
            assertEquals(3L, userVersion(dbFile), "user_version untouched — no destructive recreation")
            assertEquals("The Manuscript That Must Survive", firstTitle(dbFile), "the row survives the failed open")
        } finally {
            dbFile.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `a migration path runs instead of throwing`() {
        // The contiguous-path half of the policy: with an explicit
        // 3 -> CURRENT migration registered, the same old file opens and
        // migrates. The file starts as a Room-created CURRENT-schema
        // database rolled back to user_version 3 (schema validation then
        // passes once the registered path runs — the real MIGRATION_12_13
        // .. chain lives in AppDatabase).
        //
        // The target version is DERIVED from the Impl actually on the
        // classpath, not hardcoded: in the no-Gradle direct route the
        // prebuilt AppDatabase_Impl is one KSP build behind the source
        // (v16 while the source says v17); the Gradle route regenerates
        // it. Deriving keeps this guard true in both worlds.
        val dir = kotlin.io.path.createTempDirectory("migration-path").toFile()
        val dbFile = File(dir, "path_db")
        try {
            val pristine = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                runBlocking { pristine.useConnection(false) {} } // creates the Impl's current schema
            } finally {
                pristine.close()
            }
            val implVersion = userVersion(dbFile)
            exec(dbFile, "PRAGMA user_version = 3") // roll the stamp back

            val migration = object : Migration(3, implVersion.toInt()) {
                override fun migrate(db: SQLiteConnection) {
                    // no-op: the schema is already current (see above)
                }
            }
            val db = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .addMigrations(migration)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                runBlocking { db.useConnection(false) {} }
                assertEquals(implVersion, userVersion(dbFile), "the explicit path ran to the Impl's current schema")
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * v3.0 port S0 acceptance: MIGRATION_16_17 runs on a genuine v16
     * database seeded with manuscript data and (a) creates the five
     * engine tables with the exact columns the v17 entities declare, (b)
     * preserves every v16 row, and (c) leaves user_version to Room (the
     * helper stamps it when IT runs the migration; migrate() itself must
     * not).
     *
     * Route note: the direct no-Gradle route cannot open Room AT v17 (the
     * prebuilt AppDatabase_Impl is v16 until KSP regenerates it via the
     * Gradle route), so this test drives the real migration object over a
     * raw KMP SQLiteConnection — the same interface Room hands it — and
     * validates the resulting schema via PRAGMA. Room's own v17 identity
     * check (TableInfo equality) runs in the Gradle route.
     */
    @Test
    fun `the real 16 to 17 migration preserves manuscript data and creates the engine tables`() {
        val dir = kotlin.io.path.createTempDirectory("migration-16-17").toFile()
        val dbFile = File(dir, "v16_db")
        try {
            // 1. A genuine v16 database from the prebuilt Impl.
            val pristine = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                runBlocking { pristine.useConnection(false) {} }
            } finally {
                pristine.close()
            }
            assertEquals(16L, userVersion(dbFile), "prebuilt Impl creates v16 (v17 awaits KSP regeneration)")

            // 2. Seed a v16 row set: a complete book (every NOT NULL
            //    column), a chapter, an editorial-memory slot.
            exec(
                dbFile,
                "INSERT INTO books (id, title, genre, synopsis, outline, personaId, format, " +
                    "targetWordCount, narrativeStructure, chapterLength, isSerialized, targetChapters, " +
                    "releaseSchedule, freeTierChapters, wordMin, wordMax, bufferTarget, exportProfile, " +
                    "compiledManuscript, currentPhase, storyBible, characterBible, worldBible, " +
                    "plotPointsHistory, memoryState, agentLogs, marketingMaterials, coverPrompt, " +
                    "completionPercentage, wordsPerMinute, readabilityScore, emotionalIntensity, " +
                    "pacingHeatmap, continuityWarnings, createdAt, lastModifiedAt) VALUES (" +
                    "1, 'The Estate of Fallingwater Dike', 'Family drama', 'A sealed deed.', " +
                    "'1. Chapter 1', 'standard', 'Web Novel (Serial)', 50000, " +
                    "'Micro-Arc Cascade', 'Medium (3000 words)', 1, 15, 'Mon/Wed/Fri', 5, " +
                    "1000, 2000, 8, 'royal_road', '', 'Outlining', '', '', '', '', '', '', '', '', " +
                    "0, 250, 'N/A', 'MEDIUM', '', '', 0, 0)"
            )
            exec(
                dbFile,
                "INSERT INTO chapters (id, bookId, chapterNumber, title, content, summary, status, " +
                    "writerConfirmed, draftStatus, createdAt) VALUES (" +
                    "10, 1, 1, 'The Deed', 'Kira found the deed.', 'Kira finds the deed.', " +
                    "'Completed', 1, 'CONFIRMED', 0)"
            )
            exec(
                dbFile,
                "INSERT INTO editorial_memory (bookId, slot, payload, createdAt, lastModifiedAt) VALUES (" +
                    "1, 'market_findings', '{\"notes\":\"survived\"}', 0, 0)"
            )

            // 3. Run the REAL migration object over the raw connection —
            //    the same call Room makes inside its migration transaction.
            val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
            try {
                AppDatabase.MIGRATION_16_17.migrate(conn)

                // 4a. Every v16 row survived.
                conn.prepare("SELECT title FROM books WHERE id = 1").use { stmt ->
                    check(stmt.step()) { "book row lost in migration" }
                    assertEquals("The Estate of Fallingwater Dike", stmt.getText(0))
                }
                conn.prepare("SELECT content, draftStatus FROM chapters WHERE id = 10").use { stmt ->
                    check(stmt.step()) { "chapter row lost in migration" }
                    assertEquals("Kira found the deed.", stmt.getText(0))
                    assertEquals("CONFIRMED", stmt.getText(1))
                }
                conn.prepare(
                    "SELECT payload FROM editorial_memory WHERE bookId = 1 AND slot = 'market_findings'"
                ).use { stmt ->
                    check(stmt.step()) { "editorial memory row lost in migration" }
                    assertEquals("{\"notes\":\"survived\"}", stmt.getText(0))
                }

                // 4b. The five engine tables exist with the exact column
                //     sets the v17 entities declare (guards migration-DDL
                //     typos that would fail Room's v17 TableInfo check).
                assertTableColumns(conn, "voice_drift_log", listOf(
                    "id", "bookId", "chapterId", "beatIndex", "profileType", "profileName",
                    "measuredFeatures", "perFeatureDrift", "compositeDrift", "cumulativeDrift",
                    "flagged", "evolutionApplied", "createdAt"
                ))
                assertTableColumns(conn, "summary_cache", listOf(
                    "id", "bookId", "scope", "level", "summaryText", "tokenCount",
                    "sourceHash", "modelUsed", "createdAt"
                ))
                assertTableColumns(conn, "research_sources", listOf(
                    "id", "bookId", "sourceType", "title", "author", "url", "filePath",
                    "contentSnippet", "tags", "citationFormat", "reliability",
                    "ingestedAt", "lastVerifiedAt"
                ))
                assertTableColumns(conn, "research_facts", listOf(
                    "id", "bookId", "sourceId", "factText", "category", "chapterRefs",
                    "verified", "confidence", "verificationNote", "createdAt"
                ))
                assertTableColumns(conn, "compliance_checks", listOf(
                    "id", "bookId", "checkType", "scope", "status", "findings",
                    "recommendedActions", "resolved", "resolvedNote", "checkedAt", "resolvedAt"
                ))

                // 4c. The tables start empty and are wired to the book.
                for (table in listOf(
                    "voice_drift_log", "summary_cache", "research_sources",
                    "research_facts", "compliance_checks"
                )) {
                    conn.prepare("SELECT COUNT(*) FROM $table").use { stmt ->
                        check(stmt.step()) { "no count row for $table" }
                        assertEquals(0L, stmt.getLong(0), "$table starts empty")
                    }
                }

                // 4d. migrate() itself does not stamp user_version — that
                //     is RoomOpenHelper's job after the transaction.
                assertEquals(16L, userVersion(dbFile), "the migration object leaves version stamping to Room")
            } finally {
                conn.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** PRAGMA table_info column-name check against the entity declarations. */
    private fun assertTableColumns(conn: androidx.sqlite.SQLiteConnection, table: String, expected: List<String>) {
        val actual = ArrayList<String>()
        conn.prepare("PRAGMA table_info($table)").use { stmt ->
            while (stmt.step()) actual.add(stmt.getText(1))
        }
        assertEquals(expected, actual, "$table columns must match the entity field order")
    }
}
