package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.ConstructedBy
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.data.model.AudioTrack
import com.example.data.model.Story
import com.example.data.model.StoryFts
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChapterFts
import com.example.data.model.Lorebook

/**
 * DESKTOP PORT NOTE (AppDatabase):
 *  - Schema, entities, DAOs, version (14) and migrations are identical to the
 *    Android app.
 *  - Room KMP is used with the bundled SQLite driver (no OS SQLite needed).
 *  - @ConstructedBy + RoomDatabaseConstructor replace AGP reflection
 *    instantiation (standard Room multiplatform pattern).
 *  - The database file lives in <app data>/files/databases/, the same layout
 *    Android uses.
 */
@Database(entities = [Story::class, StoryFts::class, AudioTrack::class, Book::class, Chapter::class, ChapterFts::class, Lorebook::class], version = 14, exportSchema = false)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun lorebookDao(): LorebookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT " + System.currentTimeMillis())
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN isSerialized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN targetChapters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN releaseSchedule TEXT NOT NULL DEFAULT 'Mon/Wed/Fri'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder<AppDatabase>(
                    context.getDatabasePath("audio_novel_studio_db").absolutePath
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration(true)
                .setDriver(BundledSQLiteDriver())
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// The KSP processor generates the implementation of this constructor.
@RoomDatabaseConstructor
interface AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
