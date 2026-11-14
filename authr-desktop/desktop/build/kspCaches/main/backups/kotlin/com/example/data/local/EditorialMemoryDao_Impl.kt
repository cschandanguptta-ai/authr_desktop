package com.example.`data`.local

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.EditorialMemory
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
public class EditorialMemoryDao_Impl(
  __db: RoomDatabase,
) : EditorialMemoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfEditorialMemory: EntityInsertAdapter<EditorialMemory>
  init {
    this.__db = __db
    this.__insertAdapterOfEditorialMemory = object : EntityInsertAdapter<EditorialMemory>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `editorial_memory` (`id`,`bookId`,`slot`,`payload`,`createdAt`,`lastModifiedAt`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: EditorialMemory) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.slot)
        statement.bindText(4, entity.payload)
        statement.bindLong(5, entity.createdAt)
        statement.bindLong(6, entity.lastModifiedAt)
      }
    }
  }

  public override suspend fun upsert(row: EditorialMemory): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfEditorialMemory.insert(_connection, row)
  }

  public override suspend fun upsertAll(rows: List<EditorialMemory>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfEditorialMemory.insert(_connection, rows)
  }

  public override suspend fun getSlot(bookId: Int, slot: String): EditorialMemory? {
    val _sql: String = "SELECT * FROM editorial_memory WHERE bookId = ? AND slot = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, slot)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfSlot: Int = getColumnIndexOrThrow(_stmt, "slot")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: EditorialMemory?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpSlot: String
          _tmpSlot = _stmt.getText(_columnIndexOfSlot)
          val _tmpPayload: String
          _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _result = EditorialMemory(_tmpId,_tmpBookId,_tmpSlot,_tmpPayload,_tmpCreatedAt,_tmpLastModifiedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllForBook(bookId: Int): List<EditorialMemory> {
    val _sql: String = "SELECT * FROM editorial_memory WHERE bookId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfSlot: Int = getColumnIndexOrThrow(_stmt, "slot")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: MutableList<EditorialMemory> = mutableListOf()
        while (_stmt.step()) {
          val _item: EditorialMemory
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpSlot: String
          _tmpSlot = _stmt.getText(_columnIndexOfSlot)
          val _tmpPayload: String
          _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _item = EditorialMemory(_tmpId,_tmpBookId,_tmpSlot,_tmpPayload,_tmpCreatedAt,_tmpLastModifiedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAllForBook(bookId: Int): Flow<List<EditorialMemory>> {
    val _sql: String = "SELECT * FROM editorial_memory WHERE bookId = ?"
    return createFlow(__db, false, arrayOf("editorial_memory")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfSlot: Int = getColumnIndexOrThrow(_stmt, "slot")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: MutableList<EditorialMemory> = mutableListOf()
        while (_stmt.step()) {
          val _item: EditorialMemory
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpSlot: String
          _tmpSlot = _stmt.getText(_columnIndexOfSlot)
          val _tmpPayload: String
          _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _item = EditorialMemory(_tmpId,_tmpBookId,_tmpSlot,_tmpPayload,_tmpCreatedAt,_tmpLastModifiedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearSlot(bookId: Int, slot: String) {
    val _sql: String = "DELETE FROM editorial_memory WHERE bookId = ? AND slot = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, slot)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearBook(bookId: Int) {
    val _sql: String = "DELETE FROM editorial_memory WHERE bookId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
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
