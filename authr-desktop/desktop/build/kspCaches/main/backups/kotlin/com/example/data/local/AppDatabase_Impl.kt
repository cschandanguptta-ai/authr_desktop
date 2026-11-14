package com.example.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.FtsTableInfo
import androidx.room.util.TableInfo
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass
import androidx.room.util.FtsTableInfo.Companion.read as ftsTableInfoRead
import androidx.room.util.TableInfo.Companion.read as tableInfoRead

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _storyDao: Lazy<StoryDao> = lazy {
    StoryDao_Impl(this)
  }

  private val _audioTrackDao: Lazy<AudioTrackDao> = lazy {
    AudioTrackDao_Impl(this)
  }

  private val _bookDao: Lazy<BookDao> = lazy {
    BookDao_Impl(this)
  }

  private val _chapterDao: Lazy<ChapterDao> = lazy {
    ChapterDao_Impl(this)
  }

  private val _lorebookDao: Lazy<LorebookDao> = lazy {
    LorebookDao_Impl(this)
  }

  private val _serialArcDao: Lazy<SerialArcDao> = lazy {
    SerialArcDao_Impl(this)
  }

  private val _chekhovGunDao: Lazy<ChekhovGunDao> = lazy {
    ChekhovGunDao_Impl(this)
  }

  private val _questionDao: Lazy<QuestionDao> = lazy {
    QuestionDao_Impl(this)
  }

  private val _pluginDao: Lazy<PluginDao> = lazy {
    PluginDao_Impl(this)
  }

  private val _editorialMemoryDao: Lazy<EditorialMemoryDao> = lazy {
    EditorialMemoryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(16, "d73604bca82cd31b709ce722b465a7a6", "6a73a874b01d51705116ac751f6439ed") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `stories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `heroName` TEXT NOT NULL, `genres` TEXT NOT NULL, `format` TEXT NOT NULL, `duration` TEXT NOT NULL, `hook` TEXT NOT NULL, `extras` TEXT NOT NULL, `part1` TEXT NOT NULL, `part2` TEXT NOT NULL, `part3` TEXT NOT NULL, `part4` TEXT NOT NULL, `part5` TEXT NOT NULL, `wordCount` INTEGER NOT NULL, `persona` TEXT NOT NULL, `ambientTheme` TEXT NOT NULL, `ambientVolume` REAL NOT NULL, `metaTitle` TEXT NOT NULL, `metaDesc` TEXT NOT NULL, `metaTags` TEXT NOT NULL, `metaThumbPrompt` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `stories_fts` USING FTS4(`title` TEXT NOT NULL, `hook` TEXT NOT NULL, `part1` TEXT NOT NULL, `part2` TEXT NOT NULL, `part3` TEXT NOT NULL, `part4` TEXT NOT NULL, `part5` TEXT NOT NULL, content=`stories`)")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_BEFORE_UPDATE BEFORE UPDATE ON `stories` BEGIN DELETE FROM `stories_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_BEFORE_DELETE BEFORE DELETE ON `stories` BEGIN DELETE FROM `stories_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_AFTER_UPDATE AFTER UPDATE ON `stories` BEGIN INSERT INTO `stories_fts`(`docid`, `title`, `hook`, `part1`, `part2`, `part3`, `part4`, `part5`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`hook`, NEW.`part1`, NEW.`part2`, NEW.`part3`, NEW.`part4`, NEW.`part5`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_AFTER_INSERT AFTER INSERT ON `stories` BEGIN INSERT INTO `stories_fts`(`docid`, `title`, `hook`, `part1`, `part2`, `part3`, `part4`, `part5`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`hook`, NEW.`part1`, NEW.`part2`, NEW.`part3`, NEW.`part4`, NEW.`part5`); END")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `audio_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `storyId` INTEGER, `title` TEXT NOT NULL, `filePath` TEXT NOT NULL, `voiceName` TEXT NOT NULL, `language` TEXT NOT NULL, `rate` REAL NOT NULL, `durationMs` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `genre` TEXT NOT NULL, `synopsis` TEXT NOT NULL, `outline` TEXT NOT NULL, `personaId` TEXT NOT NULL, `format` TEXT NOT NULL, `targetWordCount` INTEGER NOT NULL, `narrativeStructure` TEXT NOT NULL, `chapterLength` TEXT NOT NULL, `isSerialized` INTEGER NOT NULL, `targetChapters` INTEGER NOT NULL, `releaseSchedule` TEXT NOT NULL, `freeTierChapters` INTEGER NOT NULL, `wordMin` INTEGER NOT NULL, `wordMax` INTEGER NOT NULL, `bufferTarget` INTEGER NOT NULL, `exportProfile` TEXT NOT NULL, `compiledManuscript` TEXT NOT NULL, `currentPhase` TEXT NOT NULL, `storyBible` TEXT NOT NULL, `characterBible` TEXT NOT NULL, `worldBible` TEXT NOT NULL, `plotPointsHistory` TEXT NOT NULL, `memoryState` TEXT NOT NULL, `agentLogs` TEXT NOT NULL, `marketingMaterials` TEXT NOT NULL, `coverPrompt` TEXT NOT NULL, `completionPercentage` INTEGER NOT NULL, `wordsPerMinute` INTEGER NOT NULL, `readabilityScore` TEXT NOT NULL, `emotionalIntensity` TEXT NOT NULL, `pacingHeatmap` TEXT NOT NULL, `continuityWarnings` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastModifiedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `chapterNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `summary` TEXT NOT NULL, `status` TEXT NOT NULL, `writerConfirmed` INTEGER NOT NULL, `draftStatus` TEXT NOT NULL, `arcId` INTEGER, `endingType` TEXT, `beatCoverage` TEXT, `balanceShares` TEXT, `worksheet` TEXT, `qcScores` TEXT, `qcTotal` INTEGER, `hookCheck` TEXT, `ctaNote` TEXT, `pollJson` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
        connection.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `chapters_fts` USING FTS4(`bookId` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `summary` TEXT NOT NULL, content=`chapters`)")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_BEFORE_UPDATE BEFORE UPDATE ON `chapters` BEGIN DELETE FROM `chapters_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_BEFORE_DELETE BEFORE DELETE ON `chapters` BEGIN DELETE FROM `chapters_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_AFTER_UPDATE AFTER UPDATE ON `chapters` BEGIN INSERT INTO `chapters_fts`(`docid`, `bookId`, `title`, `content`, `summary`) VALUES (NEW.`rowid`, NEW.`bookId`, NEW.`title`, NEW.`content`, NEW.`summary`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_AFTER_INSERT AFTER INSERT ON `chapters` BEGIN INSERT INTO `chapters_fts`(`docid`, `bookId`, `title`, `content`, `summary`) VALUES (NEW.`rowid`, NEW.`bookId`, NEW.`title`, NEW.`content`, NEW.`summary`); END")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `lorebook` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_lorebook_bookId` ON `lorebook` (`bookId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `serial_arcs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `arcIndex` INTEGER NOT NULL, `conflict` TEXT NOT NULL, `questionRef` INTEGER, `phaseBounds` TEXT NOT NULL, `answeredQuestionRef` INTEGER, `contribution` TEXT NOT NULL, `seeds` TEXT NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_serial_arcs_bookId` ON `serial_arcs` (`bookId`)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_serial_arcs_bookId_arcIndex` ON `serial_arcs` (`bookId`, `arcIndex`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chekhov_guns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `introducedChapter` INTEGER NOT NULL, `escalationChapters` TEXT NOT NULL, `resolvedChapter` INTEGER, `quote` TEXT NOT NULL, `severity` TEXT NOT NULL, `waiveNote` TEXT NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chekhov_guns_bookId` ON `chekhov_guns` (`bookId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chekhov_guns_bookId_name` ON `chekhov_guns` (`bookId`, `name`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `level` TEXT NOT NULL, `text` TEXT NOT NULL, `openedChapter` INTEGER NOT NULL, `dueChapter` INTEGER, `answeredChapter` INTEGER, `answeredByReveal` INTEGER, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_bookId` ON `questions` (`bookId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_bookId_level` ON `questions` (`bookId`, `level`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `plugins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `path` TEXT NOT NULL, `version` TEXT NOT NULL, `enabled` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugins_kind_name` ON `plugins` (`kind`, `name`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `editorial_memory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `slot` TEXT NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastModifiedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd73604bca82cd31b709ce722b465a7a6')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `stories`")
        connection.execSQL("DROP TABLE IF EXISTS `stories_fts`")
        connection.execSQL("DROP TABLE IF EXISTS `audio_tracks`")
        connection.execSQL("DROP TABLE IF EXISTS `books`")
        connection.execSQL("DROP TABLE IF EXISTS `chapters`")
        connection.execSQL("DROP TABLE IF EXISTS `chapters_fts`")
        connection.execSQL("DROP TABLE IF EXISTS `lorebook`")
        connection.execSQL("DROP TABLE IF EXISTS `serial_arcs`")
        connection.execSQL("DROP TABLE IF EXISTS `chekhov_guns`")
        connection.execSQL("DROP TABLE IF EXISTS `questions`")
        connection.execSQL("DROP TABLE IF EXISTS `plugins`")
        connection.execSQL("DROP TABLE IF EXISTS `editorial_memory`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_BEFORE_UPDATE BEFORE UPDATE ON `stories` BEGIN DELETE FROM `stories_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_BEFORE_DELETE BEFORE DELETE ON `stories` BEGIN DELETE FROM `stories_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_AFTER_UPDATE AFTER UPDATE ON `stories` BEGIN INSERT INTO `stories_fts`(`docid`, `title`, `hook`, `part1`, `part2`, `part3`, `part4`, `part5`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`hook`, NEW.`part1`, NEW.`part2`, NEW.`part3`, NEW.`part4`, NEW.`part5`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stories_fts_AFTER_INSERT AFTER INSERT ON `stories` BEGIN INSERT INTO `stories_fts`(`docid`, `title`, `hook`, `part1`, `part2`, `part3`, `part4`, `part5`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`hook`, NEW.`part1`, NEW.`part2`, NEW.`part3`, NEW.`part4`, NEW.`part5`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_BEFORE_UPDATE BEFORE UPDATE ON `chapters` BEGIN DELETE FROM `chapters_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_BEFORE_DELETE BEFORE DELETE ON `chapters` BEGIN DELETE FROM `chapters_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_AFTER_UPDATE AFTER UPDATE ON `chapters` BEGIN INSERT INTO `chapters_fts`(`docid`, `bookId`, `title`, `content`, `summary`) VALUES (NEW.`rowid`, NEW.`bookId`, NEW.`title`, NEW.`content`, NEW.`summary`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chapters_fts_AFTER_INSERT AFTER INSERT ON `chapters` BEGIN INSERT INTO `chapters_fts`(`docid`, `bookId`, `title`, `content`, `summary`) VALUES (NEW.`rowid`, NEW.`bookId`, NEW.`title`, NEW.`content`, NEW.`summary`); END")
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsStories: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStories.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("heroName", TableInfo.Column("heroName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("genres", TableInfo.Column("genres", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("format", TableInfo.Column("format", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("duration", TableInfo.Column("duration", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("hook", TableInfo.Column("hook", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("extras", TableInfo.Column("extras", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("part1", TableInfo.Column("part1", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("part2", TableInfo.Column("part2", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("part3", TableInfo.Column("part3", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("part4", TableInfo.Column("part4", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("part5", TableInfo.Column("part5", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("wordCount", TableInfo.Column("wordCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("persona", TableInfo.Column("persona", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("ambientTheme", TableInfo.Column("ambientTheme", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("ambientVolume", TableInfo.Column("ambientVolume", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("metaTitle", TableInfo.Column("metaTitle", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("metaDesc", TableInfo.Column("metaDesc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("metaTags", TableInfo.Column("metaTags", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("metaThumbPrompt", TableInfo.Column("metaThumbPrompt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStories.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStories: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesStories: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoStories: TableInfo = TableInfo("stories", _columnsStories, _foreignKeysStories, _indicesStories)
        val _existingStories: TableInfo = tableInfoRead(connection, "stories")
        if (!_infoStories.equals(_existingStories)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stories(com.example.data.model.Story).
              | Expected:
              |""".trimMargin() + _infoStories + """
              |
              | Found:
              |""".trimMargin() + _existingStories)
        }
        val _columnsStoriesFts: MutableSet<String> = mutableSetOf()
        _columnsStoriesFts.add("title")
        _columnsStoriesFts.add("hook")
        _columnsStoriesFts.add("part1")
        _columnsStoriesFts.add("part2")
        _columnsStoriesFts.add("part3")
        _columnsStoriesFts.add("part4")
        _columnsStoriesFts.add("part5")
        val _infoStoriesFts: FtsTableInfo = FtsTableInfo("stories_fts", _columnsStoriesFts, "CREATE VIRTUAL TABLE IF NOT EXISTS `stories_fts` USING FTS4(`title` TEXT NOT NULL, `hook` TEXT NOT NULL, `part1` TEXT NOT NULL, `part2` TEXT NOT NULL, `part3` TEXT NOT NULL, `part4` TEXT NOT NULL, `part5` TEXT NOT NULL, content=`stories`)")
        val _existingStoriesFts: FtsTableInfo = ftsTableInfoRead(connection, "stories_fts")
        if (!_infoStoriesFts.equals(_existingStoriesFts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stories_fts(com.example.data.model.StoryFts).
              | Expected:
              |""".trimMargin() + _infoStoriesFts + """
              |
              | Found:
              |""".trimMargin() + _existingStoriesFts)
        }
        val _columnsAudioTracks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAudioTracks.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("storyId", TableInfo.Column("storyId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("filePath", TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("voiceName", TableInfo.Column("voiceName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("language", TableInfo.Column("language", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("rate", TableInfo.Column("rate", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("durationMs", TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioTracks.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAudioTracks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAudioTracks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAudioTracks: TableInfo = TableInfo("audio_tracks", _columnsAudioTracks, _foreignKeysAudioTracks, _indicesAudioTracks)
        val _existingAudioTracks: TableInfo = tableInfoRead(connection, "audio_tracks")
        if (!_infoAudioTracks.equals(_existingAudioTracks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |audio_tracks(com.example.data.model.AudioTrack).
              | Expected:
              |""".trimMargin() + _infoAudioTracks + """
              |
              | Found:
              |""".trimMargin() + _existingAudioTracks)
        }
        val _columnsBooks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBooks.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("genre", TableInfo.Column("genre", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("synopsis", TableInfo.Column("synopsis", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("outline", TableInfo.Column("outline", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("personaId", TableInfo.Column("personaId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("format", TableInfo.Column("format", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("targetWordCount", TableInfo.Column("targetWordCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("narrativeStructure", TableInfo.Column("narrativeStructure", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("chapterLength", TableInfo.Column("chapterLength", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("isSerialized", TableInfo.Column("isSerialized", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("targetChapters", TableInfo.Column("targetChapters", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("releaseSchedule", TableInfo.Column("releaseSchedule", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("freeTierChapters", TableInfo.Column("freeTierChapters", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("wordMin", TableInfo.Column("wordMin", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("wordMax", TableInfo.Column("wordMax", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("bufferTarget", TableInfo.Column("bufferTarget", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("exportProfile", TableInfo.Column("exportProfile", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("compiledManuscript", TableInfo.Column("compiledManuscript", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("currentPhase", TableInfo.Column("currentPhase", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("storyBible", TableInfo.Column("storyBible", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("characterBible", TableInfo.Column("characterBible", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("worldBible", TableInfo.Column("worldBible", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("plotPointsHistory", TableInfo.Column("plotPointsHistory", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("memoryState", TableInfo.Column("memoryState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("agentLogs", TableInfo.Column("agentLogs", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("marketingMaterials", TableInfo.Column("marketingMaterials", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("coverPrompt", TableInfo.Column("coverPrompt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("completionPercentage", TableInfo.Column("completionPercentage", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("wordsPerMinute", TableInfo.Column("wordsPerMinute", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("readabilityScore", TableInfo.Column("readabilityScore", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("emotionalIntensity", TableInfo.Column("emotionalIntensity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("pacingHeatmap", TableInfo.Column("pacingHeatmap", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("continuityWarnings", TableInfo.Column("continuityWarnings", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("lastModifiedAt", TableInfo.Column("lastModifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBooks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBooks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBooks: TableInfo = TableInfo("books", _columnsBooks, _foreignKeysBooks, _indicesBooks)
        val _existingBooks: TableInfo = tableInfoRead(connection, "books")
        if (!_infoBooks.equals(_existingBooks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |books(com.example.data.model.Book).
              | Expected:
              |""".trimMargin() + _infoBooks + """
              |
              | Found:
              |""".trimMargin() + _existingBooks)
        }
        val _columnsChapters: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChapters.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("chapterNumber", TableInfo.Column("chapterNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("summary", TableInfo.Column("summary", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("writerConfirmed", TableInfo.Column("writerConfirmed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("draftStatus", TableInfo.Column("draftStatus", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("arcId", TableInfo.Column("arcId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("endingType", TableInfo.Column("endingType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("beatCoverage", TableInfo.Column("beatCoverage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("balanceShares", TableInfo.Column("balanceShares", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("worksheet", TableInfo.Column("worksheet", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("qcScores", TableInfo.Column("qcScores", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("qcTotal", TableInfo.Column("qcTotal", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("hookCheck", TableInfo.Column("hookCheck", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("ctaNote", TableInfo.Column("ctaNote", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("pollJson", TableInfo.Column("pollJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChapters: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysChapters.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        val _indicesChapters: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesChapters.add(TableInfo.Index("index_chapters_bookId", false, listOf("bookId"), listOf("ASC")))
        val _infoChapters: TableInfo = TableInfo("chapters", _columnsChapters, _foreignKeysChapters, _indicesChapters)
        val _existingChapters: TableInfo = tableInfoRead(connection, "chapters")
        if (!_infoChapters.equals(_existingChapters)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chapters(com.example.data.model.Chapter).
              | Expected:
              |""".trimMargin() + _infoChapters + """
              |
              | Found:
              |""".trimMargin() + _existingChapters)
        }
        val _columnsChaptersFts: MutableSet<String> = mutableSetOf()
        _columnsChaptersFts.add("bookId")
        _columnsChaptersFts.add("title")
        _columnsChaptersFts.add("content")
        _columnsChaptersFts.add("summary")
        val _infoChaptersFts: FtsTableInfo = FtsTableInfo("chapters_fts", _columnsChaptersFts, "CREATE VIRTUAL TABLE IF NOT EXISTS `chapters_fts` USING FTS4(`bookId` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `summary` TEXT NOT NULL, content=`chapters`)")
        val _existingChaptersFts: FtsTableInfo = ftsTableInfoRead(connection, "chapters_fts")
        if (!_infoChaptersFts.equals(_existingChaptersFts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chapters_fts(com.example.data.model.ChapterFts).
              | Expected:
              |""".trimMargin() + _infoChaptersFts + """
              |
              | Found:
              |""".trimMargin() + _existingChaptersFts)
        }
        val _columnsLorebook: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLorebook.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLorebook.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLorebook.put("key", TableInfo.Column("key", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLorebook.put("value", TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLorebook: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysLorebook.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        val _indicesLorebook: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesLorebook.add(TableInfo.Index("index_lorebook_bookId", false, listOf("bookId"), listOf("ASC")))
        val _infoLorebook: TableInfo = TableInfo("lorebook", _columnsLorebook, _foreignKeysLorebook, _indicesLorebook)
        val _existingLorebook: TableInfo = tableInfoRead(connection, "lorebook")
        if (!_infoLorebook.equals(_existingLorebook)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |lorebook(com.example.data.model.Lorebook).
              | Expected:
              |""".trimMargin() + _infoLorebook + """
              |
              | Found:
              |""".trimMargin() + _existingLorebook)
        }
        val _columnsSerialArcs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSerialArcs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("arcIndex", TableInfo.Column("arcIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("conflict", TableInfo.Column("conflict", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("questionRef", TableInfo.Column("questionRef", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("phaseBounds", TableInfo.Column("phaseBounds", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("answeredQuestionRef", TableInfo.Column("answeredQuestionRef", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("contribution", TableInfo.Column("contribution", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSerialArcs.put("seeds", TableInfo.Column("seeds", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSerialArcs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysSerialArcs.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        val _indicesSerialArcs: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesSerialArcs.add(TableInfo.Index("index_serial_arcs_bookId", false, listOf("bookId"), listOf("ASC")))
        _indicesSerialArcs.add(TableInfo.Index("index_serial_arcs_bookId_arcIndex", true, listOf("bookId", "arcIndex"), listOf("ASC", "ASC")))
        val _infoSerialArcs: TableInfo = TableInfo("serial_arcs", _columnsSerialArcs, _foreignKeysSerialArcs, _indicesSerialArcs)
        val _existingSerialArcs: TableInfo = tableInfoRead(connection, "serial_arcs")
        if (!_infoSerialArcs.equals(_existingSerialArcs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |serial_arcs(com.example.data.model.SerialArc).
              | Expected:
              |""".trimMargin() + _infoSerialArcs + """
              |
              | Found:
              |""".trimMargin() + _existingSerialArcs)
        }
        val _columnsChekhovGuns: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChekhovGuns.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("description", TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("introducedChapter", TableInfo.Column("introducedChapter", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("escalationChapters", TableInfo.Column("escalationChapters", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("resolvedChapter", TableInfo.Column("resolvedChapter", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("quote", TableInfo.Column("quote", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("severity", TableInfo.Column("severity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChekhovGuns.put("waiveNote", TableInfo.Column("waiveNote", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChekhovGuns: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysChekhovGuns.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        val _indicesChekhovGuns: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesChekhovGuns.add(TableInfo.Index("index_chekhov_guns_bookId", false, listOf("bookId"), listOf("ASC")))
        _indicesChekhovGuns.add(TableInfo.Index("index_chekhov_guns_bookId_name", false, listOf("bookId", "name"), listOf("ASC", "ASC")))
        val _infoChekhovGuns: TableInfo = TableInfo("chekhov_guns", _columnsChekhovGuns, _foreignKeysChekhovGuns, _indicesChekhovGuns)
        val _existingChekhovGuns: TableInfo = tableInfoRead(connection, "chekhov_guns")
        if (!_infoChekhovGuns.equals(_existingChekhovGuns)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chekhov_guns(com.example.data.model.ChekhovGun).
              | Expected:
              |""".trimMargin() + _infoChekhovGuns + """
              |
              | Found:
              |""".trimMargin() + _existingChekhovGuns)
        }
        val _columnsQuestions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsQuestions.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("level", TableInfo.Column("level", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("text", TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("openedChapter", TableInfo.Column("openedChapter", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("dueChapter", TableInfo.Column("dueChapter", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("answeredChapter", TableInfo.Column("answeredChapter", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("answeredByReveal", TableInfo.Column("answeredByReveal", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysQuestions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysQuestions.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        val _indicesQuestions: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesQuestions.add(TableInfo.Index("index_questions_bookId", false, listOf("bookId"), listOf("ASC")))
        _indicesQuestions.add(TableInfo.Index("index_questions_bookId_level", false, listOf("bookId", "level"), listOf("ASC", "ASC")))
        val _infoQuestions: TableInfo = TableInfo("questions", _columnsQuestions, _foreignKeysQuestions, _indicesQuestions)
        val _existingQuestions: TableInfo = tableInfoRead(connection, "questions")
        if (!_infoQuestions.equals(_existingQuestions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |questions(com.example.data.model.QuestionEntry).
              | Expected:
              |""".trimMargin() + _infoQuestions + """
              |
              | Found:
              |""".trimMargin() + _existingQuestions)
        }
        val _columnsPlugins: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlugins.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("path", TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("version", TableInfo.Column("version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlugins.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlugins: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPlugins: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPlugins.add(TableInfo.Index("index_plugins_kind_name", true, listOf("kind", "name"), listOf("ASC", "ASC")))
        val _infoPlugins: TableInfo = TableInfo("plugins", _columnsPlugins, _foreignKeysPlugins, _indicesPlugins)
        val _existingPlugins: TableInfo = tableInfoRead(connection, "plugins")
        if (!_infoPlugins.equals(_existingPlugins)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |plugins(com.example.data.model.PluginRecord).
              | Expected:
              |""".trimMargin() + _infoPlugins + """
              |
              | Found:
              |""".trimMargin() + _existingPlugins)
        }
        val _columnsEditorialMemory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsEditorialMemory.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEditorialMemory.put("bookId", TableInfo.Column("bookId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEditorialMemory.put("slot", TableInfo.Column("slot", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEditorialMemory.put("payload", TableInfo.Column("payload", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEditorialMemory.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEditorialMemory.put("lastModifiedAt", TableInfo.Column("lastModifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysEditorialMemory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesEditorialMemory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoEditorialMemory: TableInfo = TableInfo("editorial_memory", _columnsEditorialMemory, _foreignKeysEditorialMemory, _indicesEditorialMemory)
        val _existingEditorialMemory: TableInfo = tableInfoRead(connection, "editorial_memory")
        if (!_infoEditorialMemory.equals(_existingEditorialMemory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |editorial_memory(com.example.data.model.EditorialMemory).
              | Expected:
              |""".trimMargin() + _infoEditorialMemory + """
              |
              | Found:
              |""".trimMargin() + _existingEditorialMemory)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    _shadowTablesMap.put("stories_fts", "stories")
    _shadowTablesMap.put("chapters_fts", "chapters")
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "stories", "stories_fts", "audio_tracks", "books", "chapters", "chapters_fts", "lorebook", "serial_arcs", "chekhov_guns", "questions", "plugins", "editorial_memory")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(StoryDao::class, StoryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AudioTrackDao::class, AudioTrackDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BookDao::class, BookDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChapterDao::class, ChapterDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LorebookDao::class, LorebookDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SerialArcDao::class, SerialArcDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChekhovGunDao::class, ChekhovGunDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(QuestionDao::class, QuestionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PluginDao::class, PluginDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(EditorialMemoryDao::class, EditorialMemoryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun storyDao(): StoryDao = _storyDao.value

  public override fun audioTrackDao(): AudioTrackDao = _audioTrackDao.value

  public override fun bookDao(): BookDao = _bookDao.value

  public override fun chapterDao(): ChapterDao = _chapterDao.value

  public override fun lorebookDao(): LorebookDao = _lorebookDao.value

  public override fun serialArcDao(): SerialArcDao = _serialArcDao.value

  public override fun chekhovGunDao(): ChekhovGunDao = _chekhovGunDao.value

  public override fun questionDao(): QuestionDao = _questionDao.value

  public override fun pluginDao(): PluginDao = _pluginDao.value

  public override fun editorialMemoryDao(): EditorialMemoryDao = _editorialMemoryDao.value
}
