package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.Story
import javax.`annotation`.processing.Generated
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class StoryDao_Impl(
  __db: RoomDatabase,
) : StoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfStory: EntityInsertAdapter<Story>

  private val __deleteAdapterOfStory: EntityDeleteOrUpdateAdapter<Story>

  private val __updateAdapterOfStory: EntityDeleteOrUpdateAdapter<Story>
  init {
    this.__db = __db
    this.__insertAdapterOfStory = object : EntityInsertAdapter<Story>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `stories` (`id`,`title`,`heroName`,`genres`,`format`,`duration`,`hook`,`extras`,`part1`,`part2`,`part3`,`part4`,`part5`,`wordCount`,`persona`,`ambientTheme`,`ambientVolume`,`metaTitle`,`metaDesc`,`metaTags`,`metaThumbPrompt`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Story) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.heroName)
        statement.bindText(4, entity.genres)
        statement.bindText(5, entity.format)
        statement.bindText(6, entity.duration)
        statement.bindText(7, entity.hook)
        statement.bindText(8, entity.extras)
        statement.bindText(9, entity.part1)
        statement.bindText(10, entity.part2)
        statement.bindText(11, entity.part3)
        statement.bindText(12, entity.part4)
        statement.bindText(13, entity.part5)
        statement.bindLong(14, entity.wordCount.toLong())
        statement.bindText(15, entity.persona)
        statement.bindText(16, entity.ambientTheme)
        statement.bindDouble(17, entity.ambientVolume.toDouble())
        statement.bindText(18, entity.metaTitle)
        statement.bindText(19, entity.metaDesc)
        statement.bindText(20, entity.metaTags)
        statement.bindText(21, entity.metaThumbPrompt)
        statement.bindLong(22, entity.timestamp)
      }
    }
    this.__deleteAdapterOfStory = object : EntityDeleteOrUpdateAdapter<Story>() {
      protected override fun createQuery(): String = "DELETE FROM `stories` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Story) {
        statement.bindLong(1, entity.id.toLong())
      }
    }
    this.__updateAdapterOfStory = object : EntityDeleteOrUpdateAdapter<Story>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `stories` SET `id` = ?,`title` = ?,`heroName` = ?,`genres` = ?,`format` = ?,`duration` = ?,`hook` = ?,`extras` = ?,`part1` = ?,`part2` = ?,`part3` = ?,`part4` = ?,`part5` = ?,`wordCount` = ?,`persona` = ?,`ambientTheme` = ?,`ambientVolume` = ?,`metaTitle` = ?,`metaDesc` = ?,`metaTags` = ?,`metaThumbPrompt` = ?,`timestamp` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Story) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.heroName)
        statement.bindText(4, entity.genres)
        statement.bindText(5, entity.format)
        statement.bindText(6, entity.duration)
        statement.bindText(7, entity.hook)
        statement.bindText(8, entity.extras)
        statement.bindText(9, entity.part1)
        statement.bindText(10, entity.part2)
        statement.bindText(11, entity.part3)
        statement.bindText(12, entity.part4)
        statement.bindText(13, entity.part5)
        statement.bindLong(14, entity.wordCount.toLong())
        statement.bindText(15, entity.persona)
        statement.bindText(16, entity.ambientTheme)
        statement.bindDouble(17, entity.ambientVolume.toDouble())
        statement.bindText(18, entity.metaTitle)
        statement.bindText(19, entity.metaDesc)
        statement.bindText(20, entity.metaTags)
        statement.bindText(21, entity.metaThumbPrompt)
        statement.bindLong(22, entity.timestamp)
        statement.bindLong(23, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertStory(story: Story): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfStory.insertAndReturnId(_connection, story)
    _result
  }

  public override suspend fun deleteStory(story: Story): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfStory.handle(_connection, story)
  }

  public override suspend fun updateStory(story: Story): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfStory.handle(_connection, story)
  }

  public override fun getAllStories(): Flow<List<Story>> {
    val _sql: String = "SELECT * FROM stories ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("stories")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfHeroName: Int = getColumnIndexOrThrow(_stmt, "heroName")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfHook: Int = getColumnIndexOrThrow(_stmt, "hook")
        val _columnIndexOfExtras: Int = getColumnIndexOrThrow(_stmt, "extras")
        val _columnIndexOfPart1: Int = getColumnIndexOrThrow(_stmt, "part1")
        val _columnIndexOfPart2: Int = getColumnIndexOrThrow(_stmt, "part2")
        val _columnIndexOfPart3: Int = getColumnIndexOrThrow(_stmt, "part3")
        val _columnIndexOfPart4: Int = getColumnIndexOrThrow(_stmt, "part4")
        val _columnIndexOfPart5: Int = getColumnIndexOrThrow(_stmt, "part5")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "wordCount")
        val _columnIndexOfPersona: Int = getColumnIndexOrThrow(_stmt, "persona")
        val _columnIndexOfAmbientTheme: Int = getColumnIndexOrThrow(_stmt, "ambientTheme")
        val _columnIndexOfAmbientVolume: Int = getColumnIndexOrThrow(_stmt, "ambientVolume")
        val _columnIndexOfMetaTitle: Int = getColumnIndexOrThrow(_stmt, "metaTitle")
        val _columnIndexOfMetaDesc: Int = getColumnIndexOrThrow(_stmt, "metaDesc")
        val _columnIndexOfMetaTags: Int = getColumnIndexOrThrow(_stmt, "metaTags")
        val _columnIndexOfMetaThumbPrompt: Int = getColumnIndexOrThrow(_stmt, "metaThumbPrompt")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<Story> = mutableListOf()
        while (_stmt.step()) {
          val _item: Story
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpHeroName: String
          _tmpHeroName = _stmt.getText(_columnIndexOfHeroName)
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpDuration: String
          _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          val _tmpHook: String
          _tmpHook = _stmt.getText(_columnIndexOfHook)
          val _tmpExtras: String
          _tmpExtras = _stmt.getText(_columnIndexOfExtras)
          val _tmpPart1: String
          _tmpPart1 = _stmt.getText(_columnIndexOfPart1)
          val _tmpPart2: String
          _tmpPart2 = _stmt.getText(_columnIndexOfPart2)
          val _tmpPart3: String
          _tmpPart3 = _stmt.getText(_columnIndexOfPart3)
          val _tmpPart4: String
          _tmpPart4 = _stmt.getText(_columnIndexOfPart4)
          val _tmpPart5: String
          _tmpPart5 = _stmt.getText(_columnIndexOfPart5)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpPersona: String
          _tmpPersona = _stmt.getText(_columnIndexOfPersona)
          val _tmpAmbientTheme: String
          _tmpAmbientTheme = _stmt.getText(_columnIndexOfAmbientTheme)
          val _tmpAmbientVolume: Float
          _tmpAmbientVolume = _stmt.getDouble(_columnIndexOfAmbientVolume).toFloat()
          val _tmpMetaTitle: String
          _tmpMetaTitle = _stmt.getText(_columnIndexOfMetaTitle)
          val _tmpMetaDesc: String
          _tmpMetaDesc = _stmt.getText(_columnIndexOfMetaDesc)
          val _tmpMetaTags: String
          _tmpMetaTags = _stmt.getText(_columnIndexOfMetaTags)
          val _tmpMetaThumbPrompt: String
          _tmpMetaThumbPrompt = _stmt.getText(_columnIndexOfMetaThumbPrompt)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = Story(_tmpId,_tmpTitle,_tmpHeroName,_tmpGenres,_tmpFormat,_tmpDuration,_tmpHook,_tmpExtras,_tmpPart1,_tmpPart2,_tmpPart3,_tmpPart4,_tmpPart5,_tmpWordCount,_tmpPersona,_tmpAmbientTheme,_tmpAmbientVolume,_tmpMetaTitle,_tmpMetaDesc,_tmpMetaTags,_tmpMetaThumbPrompt,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getStoryById(id: Int): Story? {
    val _sql: String = "SELECT * FROM stories WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfHeroName: Int = getColumnIndexOrThrow(_stmt, "heroName")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfHook: Int = getColumnIndexOrThrow(_stmt, "hook")
        val _columnIndexOfExtras: Int = getColumnIndexOrThrow(_stmt, "extras")
        val _columnIndexOfPart1: Int = getColumnIndexOrThrow(_stmt, "part1")
        val _columnIndexOfPart2: Int = getColumnIndexOrThrow(_stmt, "part2")
        val _columnIndexOfPart3: Int = getColumnIndexOrThrow(_stmt, "part3")
        val _columnIndexOfPart4: Int = getColumnIndexOrThrow(_stmt, "part4")
        val _columnIndexOfPart5: Int = getColumnIndexOrThrow(_stmt, "part5")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "wordCount")
        val _columnIndexOfPersona: Int = getColumnIndexOrThrow(_stmt, "persona")
        val _columnIndexOfAmbientTheme: Int = getColumnIndexOrThrow(_stmt, "ambientTheme")
        val _columnIndexOfAmbientVolume: Int = getColumnIndexOrThrow(_stmt, "ambientVolume")
        val _columnIndexOfMetaTitle: Int = getColumnIndexOrThrow(_stmt, "metaTitle")
        val _columnIndexOfMetaDesc: Int = getColumnIndexOrThrow(_stmt, "metaDesc")
        val _columnIndexOfMetaTags: Int = getColumnIndexOrThrow(_stmt, "metaTags")
        val _columnIndexOfMetaThumbPrompt: Int = getColumnIndexOrThrow(_stmt, "metaThumbPrompt")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: Story?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpHeroName: String
          _tmpHeroName = _stmt.getText(_columnIndexOfHeroName)
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpDuration: String
          _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          val _tmpHook: String
          _tmpHook = _stmt.getText(_columnIndexOfHook)
          val _tmpExtras: String
          _tmpExtras = _stmt.getText(_columnIndexOfExtras)
          val _tmpPart1: String
          _tmpPart1 = _stmt.getText(_columnIndexOfPart1)
          val _tmpPart2: String
          _tmpPart2 = _stmt.getText(_columnIndexOfPart2)
          val _tmpPart3: String
          _tmpPart3 = _stmt.getText(_columnIndexOfPart3)
          val _tmpPart4: String
          _tmpPart4 = _stmt.getText(_columnIndexOfPart4)
          val _tmpPart5: String
          _tmpPart5 = _stmt.getText(_columnIndexOfPart5)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpPersona: String
          _tmpPersona = _stmt.getText(_columnIndexOfPersona)
          val _tmpAmbientTheme: String
          _tmpAmbientTheme = _stmt.getText(_columnIndexOfAmbientTheme)
          val _tmpAmbientVolume: Float
          _tmpAmbientVolume = _stmt.getDouble(_columnIndexOfAmbientVolume).toFloat()
          val _tmpMetaTitle: String
          _tmpMetaTitle = _stmt.getText(_columnIndexOfMetaTitle)
          val _tmpMetaDesc: String
          _tmpMetaDesc = _stmt.getText(_columnIndexOfMetaDesc)
          val _tmpMetaTags: String
          _tmpMetaTags = _stmt.getText(_columnIndexOfMetaTags)
          val _tmpMetaThumbPrompt: String
          _tmpMetaThumbPrompt = _stmt.getText(_columnIndexOfMetaThumbPrompt)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _result = Story(_tmpId,_tmpTitle,_tmpHeroName,_tmpGenres,_tmpFormat,_tmpDuration,_tmpHook,_tmpExtras,_tmpPart1,_tmpPart2,_tmpPart3,_tmpPart4,_tmpPart5,_tmpWordCount,_tmpPersona,_tmpAmbientTheme,_tmpAmbientVolume,_tmpMetaTitle,_tmpMetaDesc,_tmpMetaTags,_tmpMetaThumbPrompt,_tmpTimestamp)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchStories(query: String): List<Story> {
    val _sql: String = """
        |
        |        SELECT stories.* 
        |        FROM stories 
        |        JOIN stories_fts ON stories.id = stories_fts.rowid 
        |        WHERE stories_fts MATCH ?
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfHeroName: Int = getColumnIndexOrThrow(_stmt, "heroName")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfHook: Int = getColumnIndexOrThrow(_stmt, "hook")
        val _columnIndexOfExtras: Int = getColumnIndexOrThrow(_stmt, "extras")
        val _columnIndexOfPart1: Int = getColumnIndexOrThrow(_stmt, "part1")
        val _columnIndexOfPart2: Int = getColumnIndexOrThrow(_stmt, "part2")
        val _columnIndexOfPart3: Int = getColumnIndexOrThrow(_stmt, "part3")
        val _columnIndexOfPart4: Int = getColumnIndexOrThrow(_stmt, "part4")
        val _columnIndexOfPart5: Int = getColumnIndexOrThrow(_stmt, "part5")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "wordCount")
        val _columnIndexOfPersona: Int = getColumnIndexOrThrow(_stmt, "persona")
        val _columnIndexOfAmbientTheme: Int = getColumnIndexOrThrow(_stmt, "ambientTheme")
        val _columnIndexOfAmbientVolume: Int = getColumnIndexOrThrow(_stmt, "ambientVolume")
        val _columnIndexOfMetaTitle: Int = getColumnIndexOrThrow(_stmt, "metaTitle")
        val _columnIndexOfMetaDesc: Int = getColumnIndexOrThrow(_stmt, "metaDesc")
        val _columnIndexOfMetaTags: Int = getColumnIndexOrThrow(_stmt, "metaTags")
        val _columnIndexOfMetaThumbPrompt: Int = getColumnIndexOrThrow(_stmt, "metaThumbPrompt")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<Story> = mutableListOf()
        while (_stmt.step()) {
          val _item: Story
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpHeroName: String
          _tmpHeroName = _stmt.getText(_columnIndexOfHeroName)
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpDuration: String
          _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          val _tmpHook: String
          _tmpHook = _stmt.getText(_columnIndexOfHook)
          val _tmpExtras: String
          _tmpExtras = _stmt.getText(_columnIndexOfExtras)
          val _tmpPart1: String
          _tmpPart1 = _stmt.getText(_columnIndexOfPart1)
          val _tmpPart2: String
          _tmpPart2 = _stmt.getText(_columnIndexOfPart2)
          val _tmpPart3: String
          _tmpPart3 = _stmt.getText(_columnIndexOfPart3)
          val _tmpPart4: String
          _tmpPart4 = _stmt.getText(_columnIndexOfPart4)
          val _tmpPart5: String
          _tmpPart5 = _stmt.getText(_columnIndexOfPart5)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpPersona: String
          _tmpPersona = _stmt.getText(_columnIndexOfPersona)
          val _tmpAmbientTheme: String
          _tmpAmbientTheme = _stmt.getText(_columnIndexOfAmbientTheme)
          val _tmpAmbientVolume: Float
          _tmpAmbientVolume = _stmt.getDouble(_columnIndexOfAmbientVolume).toFloat()
          val _tmpMetaTitle: String
          _tmpMetaTitle = _stmt.getText(_columnIndexOfMetaTitle)
          val _tmpMetaDesc: String
          _tmpMetaDesc = _stmt.getText(_columnIndexOfMetaDesc)
          val _tmpMetaTags: String
          _tmpMetaTags = _stmt.getText(_columnIndexOfMetaTags)
          val _tmpMetaThumbPrompt: String
          _tmpMetaThumbPrompt = _stmt.getText(_columnIndexOfMetaThumbPrompt)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = Story(_tmpId,_tmpTitle,_tmpHeroName,_tmpGenres,_tmpFormat,_tmpDuration,_tmpHook,_tmpExtras,_tmpPart1,_tmpPart2,_tmpPart3,_tmpPart4,_tmpPart5,_tmpWordCount,_tmpPersona,_tmpAmbientTheme,_tmpAmbientVolume,_tmpMetaTitle,_tmpMetaDesc,_tmpMetaTags,_tmpMetaThumbPrompt,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteStoryById(id: Int) {
    val _sql: String = "DELETE FROM stories WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
