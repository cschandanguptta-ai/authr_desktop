package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import com.example.data.model.AudioTrack
import com.example.data.model.Story
import com.example.data.model.StoryFts
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChapterFts
import com.example.data.model.Lorebook
import com.example.data.model.SerialArc
import com.example.data.model.ChekhovGun
import com.example.data.model.QuestionEntry
import com.example.data.model.PluginRecord

/**
 * DESKTOP PORT NOTE (AppDatabase):
 *  - Entities, DAOs and migrations mirror the Android app.
 *  - Room KMP is used with the bundled SQLite driver (no OS SQLite needed).
 *  - The JVM-only target instantiates the generated AppDatabase_Impl via
 *    reflection (Room's documented JVM behavior), so no @ConstructedBy
 *    declaration is required — the KMP expect/actual constructor object is
 *    only needed for platforms without reflection.
 *  - The database file lives in <app data>/files/databases/, the same layout
 *    Android uses.
 *
 * SCHEMA HISTORY:
 *  - v14: v2.3 baseline (isSerialized / targetChapters / releaseSchedule).
 *  - v15: v3.0 schema v2 (plan Table 32, Appendix A) — additive only:
 *    serialization profile columns on books/chapters, writer-confirmed engine
 *    state, and the serial_arcs / chekhov_guns / questions / plugins tables.
 */
@Database(
    entities = [
        Story::class, StoryFts::class, AudioTrack::class, Book::class,
        Chapter::class, ChapterFts::class, Lorebook::class,
        SerialArc::class, ChekhovGun::class, QuestionEntry::class, PluginRecord::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun lorebookDao(): LorebookDao
    abstract fun serialArcDao(): SerialArcDao
    abstract fun chekhovGunDao(): ChekhovGunDao
    abstract fun questionDao(): QuestionDao
    abstract fun pluginDao(): PluginDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Helper: run one DDL statement on the KMP SQLiteConnection. */
        private fun SQLiteConnection.exec(sql: String) {
            prepare(sql).use { it.step() }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SQLiteConnection) {
                // Fixed seed default: Room tolerates database-side defaults that
                // the entity does not declare (TableInfo.equalsCommon).
                db.exec("ALTER TABLE books ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SQLiteConnection) {
                db.exec("ALTER TABLE books ADD COLUMN isSerialized INTEGER NOT NULL DEFAULT 0")
                db.exec("ALTER TABLE books ADD COLUMN targetChapters INTEGER NOT NULL DEFAULT 0")
                db.exec("ALTER TABLE books ADD COLUMN releaseSchedule TEXT NOT NULL DEFAULT 'Mon/Wed/Fri'")
            }
        }

        /**
         * v3.0 schema v2 (plan §12 Table 32 + Appendix A). Additive ALTERs and
         * CREATEs only — v2.3 rows keep their identity. Legacy chapters are
         * canon: writerConfirmed defaults to 1 and draftStatus to 'CONFIRMED'.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SQLiteConnection) {
                // Serialization profile (books) — plan Appendix A
                db.exec("ALTER TABLE books ADD COLUMN freeTierChapters INTEGER NOT NULL DEFAULT 5")
                db.exec("ALTER TABLE books ADD COLUMN wordMin INTEGER NOT NULL DEFAULT 1000")
                db.exec("ALTER TABLE books ADD COLUMN wordMax INTEGER NOT NULL DEFAULT 2000")
                db.exec("ALTER TABLE books ADD COLUMN bufferTarget INTEGER NOT NULL DEFAULT 8")
                db.exec("ALTER TABLE books ADD COLUMN exportProfile TEXT NOT NULL DEFAULT 'royal_road'")
                // Engine state (Table 32): writer sovereignty
                db.exec("ALTER TABLE chapters ADD COLUMN writerConfirmed INTEGER NOT NULL DEFAULT 1")
                db.exec("ALTER TABLE chapters ADD COLUMN draftStatus TEXT NOT NULL DEFAULT 'CONFIRMED'")
                // Serialization fields (chapters)
                db.exec("ALTER TABLE chapters ADD COLUMN arcId INTEGER")
                db.exec("ALTER TABLE chapters ADD COLUMN endingType TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN beatCoverage TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN balanceShares TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN worksheet TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN qcScores TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN qcTotal INTEGER")
                db.exec("ALTER TABLE chapters ADD COLUMN hookCheck TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN ctaNote TEXT")
                db.exec("ALTER TABLE chapters ADD COLUMN pollJson TEXT")
                // New tables
                db.exec(
                    "CREATE TABLE IF NOT EXISTS serial_arcs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, " +
                        "arcIndex INTEGER NOT NULL, " +
                        "conflict TEXT NOT NULL, " +
                        "questionRef INTEGER, " +
                        "phaseBounds TEXT NOT NULL, " +
                        "answeredQuestionRef INTEGER, " +
                        "contribution TEXT NOT NULL, " +
                        "seeds TEXT NOT NULL, " +
                        "FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)"
                )
                db.exec("CREATE INDEX IF NOT EXISTS index_serial_arcs_bookId ON serial_arcs(bookId)")
                db.exec("CREATE UNIQUE INDEX IF NOT EXISTS index_serial_arcs_bookId_arcIndex ON serial_arcs(bookId, arcIndex)")
                db.exec(
                    "CREATE TABLE IF NOT EXISTS chekhov_guns (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "introducedChapter INTEGER NOT NULL, " +
                        "escalationChapters TEXT NOT NULL, " +
                        "resolvedChapter INTEGER, " +
                        "quote TEXT NOT NULL, " +
                        "severity TEXT NOT NULL, " +
                        "waiveNote TEXT NOT NULL, " +
                        "FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)"
                )
                db.exec("CREATE INDEX IF NOT EXISTS index_chekhov_guns_bookId ON chekhov_guns(bookId)")
                db.exec("CREATE INDEX IF NOT EXISTS index_chekhov_guns_bookId_name ON chekhov_guns(bookId, name)")
                db.exec(
                    "CREATE TABLE IF NOT EXISTS questions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, " +
                        "level TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "openedChapter INTEGER NOT NULL, " +
                        "dueChapter INTEGER, " +
                        "answeredChapter INTEGER, " +
                        "answeredByReveal INTEGER, " +
                        "FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)"
                )
                db.exec("CREATE INDEX IF NOT EXISTS index_questions_bookId ON questions(bookId)")
                db.exec("CREATE INDEX IF NOT EXISTS index_questions_bookId_level ON questions(bookId, level)")
                db.exec(
                    "CREATE TABLE IF NOT EXISTS plugins (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "version TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL)"
                )
                db.exec("CREATE UNIQUE INDEX IF NOT EXISTS index_plugins_kind_name ON plugins(kind, name)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // v3.0 D-02 RESOLVED (OQ-3 decided, 2026-11-12): NO
                // destructive fallback. `fallbackToDestructiveMigration(true)`
                // silently WIPED user manuscripts on any schema drift — the
                // exact data loss the plan's writer-sovereignty pillar forbids.
                // Fail LOUD instead: opening a database whose schema version
                // has no contiguous migration path throws
                // IllegalStateException ("A migration from X to Y was
                // required but not found"), and a fresh install creates the
                // current schema unchanged. MigrationFailFastTest guards the
                // loud-failure half. Desktop edition pre-release note: only
                // developer databases predate this; the beta ships schema 15.
                val instance = Room.databaseBuilder<AppDatabase>(
                    context.getDatabasePath("audio_novel_studio_db").absolutePath
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .setDriver(BundledSQLiteDriver())
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}