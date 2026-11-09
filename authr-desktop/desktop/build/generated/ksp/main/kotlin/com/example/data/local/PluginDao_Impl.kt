package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.PluginRecord
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class PluginDao_Impl(
  __db: RoomDatabase,
) : PluginDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPluginRecord: EntityInsertAdapter<PluginRecord>

  private val __updateAdapterOfPluginRecord: EntityDeleteOrUpdateAdapter<PluginRecord>
  init {
    this.__db = __db
    this.__insertAdapterOfPluginRecord = object : EntityInsertAdapter<PluginRecord>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `plugins` (`id`,`kind`,`name`,`path`,`version`,`enabled`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PluginRecord) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.kind)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.path)
        statement.bindText(5, entity.version)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp.toLong())
      }
    }
    this.__updateAdapterOfPluginRecord = object : EntityDeleteOrUpdateAdapter<PluginRecord>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `plugins` SET `id` = ?,`kind` = ?,`name` = ?,`path` = ?,`version` = ?,`enabled` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PluginRecord) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.kind)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.path)
        statement.bindText(5, entity.version)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertPlugin(plugin: PluginRecord): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfPluginRecord.insertAndReturnId(_connection, plugin)
    _result
  }

  public override suspend fun insertPlugins(plugins: List<PluginRecord>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPluginRecord.insert(_connection, plugins)
  }

  public override suspend fun updatePlugin(plugin: PluginRecord): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfPluginRecord.handle(_connection, plugin)
  }

  public override fun getAllPlugins(): Flow<List<PluginRecord>> {
    val _sql: String = "SELECT * FROM plugins ORDER BY kind, name"
    return createFlow(__db, false, arrayOf("plugins")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: MutableList<PluginRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginRecord
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _item = PluginRecord(_tmpId,_tmpKind,_tmpName,_tmpPath,_tmpVersion,_tmpEnabled)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEnabledPlugins(kind: String): Flow<List<PluginRecord>> {
    val _sql: String = "SELECT * FROM plugins WHERE kind = ? AND enabled = 1 ORDER BY name"
    return createFlow(__db, false, arrayOf("plugins")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, kind)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: MutableList<PluginRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginRecord
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _item = PluginRecord(_tmpId,_tmpKind,_tmpName,_tmpPath,_tmpVersion,_tmpEnabled)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllPluginsSync(): List<PluginRecord> {
    val _sql: String = "SELECT * FROM plugins"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: MutableList<PluginRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: PluginRecord
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _item = PluginRecord(_tmpId,_tmpKind,_tmpName,_tmpPath,_tmpVersion,_tmpEnabled)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findPlugin(kind: String, name: String): PluginRecord? {
    val _sql: String = "SELECT * FROM plugins WHERE kind = ? AND name = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, kind)
        _argIndex = 2
        _stmt.bindText(_argIndex, name)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: PluginRecord?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _result = PluginRecord(_tmpId,_tmpKind,_tmpName,_tmpPath,_tmpVersion,_tmpEnabled)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deletePlugin(pluginId: Int) {
    val _sql: String = "DELETE FROM plugins WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, pluginId.toLong())
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
