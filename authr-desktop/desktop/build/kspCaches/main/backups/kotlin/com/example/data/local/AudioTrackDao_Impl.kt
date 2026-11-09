package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.AudioTrack
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
public class AudioTrackDao_Impl(
  __db: RoomDatabase,
) : AudioTrackDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAudioTrack: EntityInsertAdapter<AudioTrack>

  private val __deleteAdapterOfAudioTrack: EntityDeleteOrUpdateAdapter<AudioTrack>
  init {
    this.__db = __db
    this.__insertAdapterOfAudioTrack = object : EntityInsertAdapter<AudioTrack>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `audio_tracks` (`id`,`storyId`,`title`,`filePath`,`voiceName`,`language`,`rate`,`durationMs`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AudioTrack) {
        statement.bindLong(1, entity.id.toLong())
        val _tmpStoryId: Int? = entity.storyId
        if (_tmpStoryId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpStoryId.toLong())
        }
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.filePath)
        statement.bindText(5, entity.voiceName)
        statement.bindText(6, entity.language)
        statement.bindDouble(7, entity.rate.toDouble())
        statement.bindLong(8, entity.durationMs)
        statement.bindLong(9, entity.timestamp)
      }
    }
    this.__deleteAdapterOfAudioTrack = object : EntityDeleteOrUpdateAdapter<AudioTrack>() {
      protected override fun createQuery(): String = "DELETE FROM `audio_tracks` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AudioTrack) {
        statement.bindLong(1, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertAudioTrack(audioTrack: AudioTrack): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfAudioTrack.insertAndReturnId(_connection, audioTrack)
    _result
  }

  public override suspend fun deleteAudioTrack(audioTrack: AudioTrack): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfAudioTrack.handle(_connection, audioTrack)
  }

  public override fun getAllAudioTracks(): Flow<List<AudioTrack>> {
    val _sql: String = "SELECT * FROM audio_tracks ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("audio_tracks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStoryId: Int = getColumnIndexOrThrow(_stmt, "storyId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfVoiceName: Int = getColumnIndexOrThrow(_stmt, "voiceName")
        val _columnIndexOfLanguage: Int = getColumnIndexOrThrow(_stmt, "language")
        val _columnIndexOfRate: Int = getColumnIndexOrThrow(_stmt, "rate")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<AudioTrack> = mutableListOf()
        while (_stmt.step()) {
          val _item: AudioTrack
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpStoryId: Int?
          if (_stmt.isNull(_columnIndexOfStoryId)) {
            _tmpStoryId = null
          } else {
            _tmpStoryId = _stmt.getLong(_columnIndexOfStoryId).toInt()
          }
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpVoiceName: String
          _tmpVoiceName = _stmt.getText(_columnIndexOfVoiceName)
          val _tmpLanguage: String
          _tmpLanguage = _stmt.getText(_columnIndexOfLanguage)
          val _tmpRate: Float
          _tmpRate = _stmt.getDouble(_columnIndexOfRate).toFloat()
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = AudioTrack(_tmpId,_tmpStoryId,_tmpTitle,_tmpFilePath,_tmpVoiceName,_tmpLanguage,_tmpRate,_tmpDurationMs,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAudioTrackById(id: Int) {
    val _sql: String = "DELETE FROM audio_tracks WHERE id = ?"
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
