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
            // No migrations registered: 3 -> 16 has no path. With the old
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
        // The contiguous-path half of the policy: with an explicit 3 -> 16
        // migration registered, the same old file opens and migrates. The
        // file starts as a Room-created CURRENT-schema database rolled back
        // to user_version 3 (schema validation then passes once the
        // registered path runs — the real MIGRATION_12_13..14_15 chain
        // lives in AppDatabase).
        val dir = kotlin.io.path.createTempDirectory("migration-path").toFile()
        val dbFile = File(dir, "path_db")
        try {
            val pristine = Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                runBlocking { pristine.useConnection(false) {} } // creates the current schema at version 16
            } finally {
                pristine.close()
            }
            exec(dbFile, "PRAGMA user_version = 3") // roll the stamp back

            // ADR-0018: the current schema is v16 (editorial_memory); the
            // pristine build above stamps 16, so the explicit path targets 16.
            val migration = object : Migration(3, 16) {
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
                assertEquals(16L, userVersion(dbFile), "the explicit path ran to the current schema")
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
