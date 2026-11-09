package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.Lorebook
import javax.`annotation`.processing.Generated
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
public class LorebookDao_Impl(
  __db: RoomDatabase,
) : LorebookDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLorebook: EntityInsertAdapter<Lorebook>

  private val __updateAdapterOfLorebook: EntityDeleteOrUpdateAdapter<Lorebook>
  init {
    this.__db = __db
    this.__insertAdapterOfLorebook = object : EntityInsertAdapter<Lorebook>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `lorebook` (`id`,`bookId`,`key`,`value`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Lorebook) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.key)
        statement.bindText(4, entity.value)
      }
    }
    this.__updateAdapterOfLorebook = object : EntityDeleteOrUpdateAdapter<Lorebook>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `lorebook` SET `id` = ?,`bookId` = ?,`key` = ?,`value` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Lorebook) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.key)
        statement.bindText(4, entity.value)
        statement.bindLong(5, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertLorebook(lorebook: Lorebook): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfLorebook.insertAndReturnId(_connection, lorebook)
    _result
  }

  public override suspend fun insertLorebooks(lorebooks: List<Lorebook>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLorebook.insert(_connection, lorebooks)
  }

  public override suspend fun updateLorebook(lorebook: Lorebook): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfLorebook.handle(_connection, lorebook)
  }

  public override fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> {
    val _sql: String = "SELECT * FROM lorebook WHERE bookId = ?"
    return createFlow(__db, false, arrayOf("lorebook")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfKey: Int = getColumnIndexOrThrow(_stmt, "key")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _result: MutableList<Lorebook> = mutableListOf()
        while (_stmt.step()) {
          val _item: Lorebook
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfKey)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          _item = Lorebook(_tmpId,_tmpBookId,_tmpKey,_tmpValue)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook> {
    val _sql: String = "SELECT * FROM lorebook WHERE bookId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfKey: Int = getColumnIndexOrThrow(_stmt, "key")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _result: MutableList<Lorebook> = mutableListOf()
        while (_stmt.step()) {
          val _item: Lorebook
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfKey)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          _item = Lorebook(_tmpId,_tmpBookId,_tmpKey,_tmpValue)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteLorebook(lorebookId: Int) {
    val _sql: String = "DELETE FROM lorebook WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, lorebookId.toLong())
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
