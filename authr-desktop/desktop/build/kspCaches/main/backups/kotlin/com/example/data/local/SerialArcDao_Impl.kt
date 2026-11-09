package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.SerialArc
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
public class SerialArcDao_Impl(
  __db: RoomDatabase,
) : SerialArcDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSerialArc: EntityInsertAdapter<SerialArc>

  private val __updateAdapterOfSerialArc: EntityDeleteOrUpdateAdapter<SerialArc>
  init {
    this.__db = __db
    this.__insertAdapterOfSerialArc = object : EntityInsertAdapter<SerialArc>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `serial_arcs` (`id`,`bookId`,`arcIndex`,`conflict`,`questionRef`,`phaseBounds`,`answeredQuestionRef`,`contribution`,`seeds`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SerialArc) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindLong(3, entity.arcIndex.toLong())
        statement.bindText(4, entity.conflict)
        val _tmpQuestionRef: Int? = entity.questionRef
        if (_tmpQuestionRef == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpQuestionRef.toLong())
        }
        statement.bindText(6, entity.phaseBounds)
        val _tmpAnsweredQuestionRef: Int? = entity.answeredQuestionRef
        if (_tmpAnsweredQuestionRef == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpAnsweredQuestionRef.toLong())
        }
        statement.bindText(8, entity.contribution)
        statement.bindText(9, entity.seeds)
      }
    }
    this.__updateAdapterOfSerialArc = object : EntityDeleteOrUpdateAdapter<SerialArc>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `serial_arcs` SET `id` = ?,`bookId` = ?,`arcIndex` = ?,`conflict` = ?,`questionRef` = ?,`phaseBounds` = ?,`answeredQuestionRef` = ?,`contribution` = ?,`seeds` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SerialArc) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindLong(3, entity.arcIndex.toLong())
        statement.bindText(4, entity.conflict)
        val _tmpQuestionRef: Int? = entity.questionRef
        if (_tmpQuestionRef == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpQuestionRef.toLong())
        }
        statement.bindText(6, entity.phaseBounds)
        val _tmpAnsweredQuestionRef: Int? = entity.answeredQuestionRef
        if (_tmpAnsweredQuestionRef == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpAnsweredQuestionRef.toLong())
        }
        statement.bindText(8, entity.contribution)
        statement.bindText(9, entity.seeds)
        statement.bindLong(10, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertArc(arc: SerialArc): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSerialArc.insertAndReturnId(_connection, arc)
    _result
  }

  public override suspend fun insertArcs(arcs: List<SerialArc>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSerialArc.insert(_connection, arcs)
  }

  public override suspend fun updateArc(arc: SerialArc): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfSerialArc.handle(_connection, arc)
  }

  public override fun getArcsForBook(bookId: Int): Flow<List<SerialArc>> {
    val _sql: String = "SELECT * FROM serial_arcs WHERE bookId = ? ORDER BY arcIndex ASC"
    return createFlow(__db, false, arrayOf("serial_arcs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfArcIndex: Int = getColumnIndexOrThrow(_stmt, "arcIndex")
        val _columnIndexOfConflict: Int = getColumnIndexOrThrow(_stmt, "conflict")
        val _columnIndexOfQuestionRef: Int = getColumnIndexOrThrow(_stmt, "questionRef")
        val _columnIndexOfPhaseBounds: Int = getColumnIndexOrThrow(_stmt, "phaseBounds")
        val _columnIndexOfAnsweredQuestionRef: Int = getColumnIndexOrThrow(_stmt, "answeredQuestionRef")
        val _columnIndexOfContribution: Int = getColumnIndexOrThrow(_stmt, "contribution")
        val _columnIndexOfSeeds: Int = getColumnIndexOrThrow(_stmt, "seeds")
        val _result: MutableList<SerialArc> = mutableListOf()
        while (_stmt.step()) {
          val _item: SerialArc
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpArcIndex: Int
          _tmpArcIndex = _stmt.getLong(_columnIndexOfArcIndex).toInt()
          val _tmpConflict: String
          _tmpConflict = _stmt.getText(_columnIndexOfConflict)
          val _tmpQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfQuestionRef)) {
            _tmpQuestionRef = null
          } else {
            _tmpQuestionRef = _stmt.getLong(_columnIndexOfQuestionRef).toInt()
          }
          val _tmpPhaseBounds: String
          _tmpPhaseBounds = _stmt.getText(_columnIndexOfPhaseBounds)
          val _tmpAnsweredQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredQuestionRef)) {
            _tmpAnsweredQuestionRef = null
          } else {
            _tmpAnsweredQuestionRef = _stmt.getLong(_columnIndexOfAnsweredQuestionRef).toInt()
          }
          val _tmpContribution: String
          _tmpContribution = _stmt.getText(_columnIndexOfContribution)
          val _tmpSeeds: String
          _tmpSeeds = _stmt.getText(_columnIndexOfSeeds)
          _item = SerialArc(_tmpId,_tmpBookId,_tmpArcIndex,_tmpConflict,_tmpQuestionRef,_tmpPhaseBounds,_tmpAnsweredQuestionRef,_tmpContribution,_tmpSeeds)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getArcsForBookSync(bookId: Int): List<SerialArc> {
    val _sql: String = "SELECT * FROM serial_arcs WHERE bookId = ? ORDER BY arcIndex ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfArcIndex: Int = getColumnIndexOrThrow(_stmt, "arcIndex")
        val _columnIndexOfConflict: Int = getColumnIndexOrThrow(_stmt, "conflict")
        val _columnIndexOfQuestionRef: Int = getColumnIndexOrThrow(_stmt, "questionRef")
        val _columnIndexOfPhaseBounds: Int = getColumnIndexOrThrow(_stmt, "phaseBounds")
        val _columnIndexOfAnsweredQuestionRef: Int = getColumnIndexOrThrow(_stmt, "answeredQuestionRef")
        val _columnIndexOfContribution: Int = getColumnIndexOrThrow(_stmt, "contribution")
        val _columnIndexOfSeeds: Int = getColumnIndexOrThrow(_stmt, "seeds")
        val _result: MutableList<SerialArc> = mutableListOf()
        while (_stmt.step()) {
          val _item: SerialArc
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpArcIndex: Int
          _tmpArcIndex = _stmt.getLong(_columnIndexOfArcIndex).toInt()
          val _tmpConflict: String
          _tmpConflict = _stmt.getText(_columnIndexOfConflict)
          val _tmpQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfQuestionRef)) {
            _tmpQuestionRef = null
          } else {
            _tmpQuestionRef = _stmt.getLong(_columnIndexOfQuestionRef).toInt()
          }
          val _tmpPhaseBounds: String
          _tmpPhaseBounds = _stmt.getText(_columnIndexOfPhaseBounds)
          val _tmpAnsweredQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredQuestionRef)) {
            _tmpAnsweredQuestionRef = null
          } else {
            _tmpAnsweredQuestionRef = _stmt.getLong(_columnIndexOfAnsweredQuestionRef).toInt()
          }
          val _tmpContribution: String
          _tmpContribution = _stmt.getText(_columnIndexOfContribution)
          val _tmpSeeds: String
          _tmpSeeds = _stmt.getText(_columnIndexOfSeeds)
          _item = SerialArc(_tmpId,_tmpBookId,_tmpArcIndex,_tmpConflict,_tmpQuestionRef,_tmpPhaseBounds,_tmpAnsweredQuestionRef,_tmpContribution,_tmpSeeds)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getArcById(arcId: Int): SerialArc? {
    val _sql: String = "SELECT * FROM serial_arcs WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, arcId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfArcIndex: Int = getColumnIndexOrThrow(_stmt, "arcIndex")
        val _columnIndexOfConflict: Int = getColumnIndexOrThrow(_stmt, "conflict")
        val _columnIndexOfQuestionRef: Int = getColumnIndexOrThrow(_stmt, "questionRef")
        val _columnIndexOfPhaseBounds: Int = getColumnIndexOrThrow(_stmt, "phaseBounds")
        val _columnIndexOfAnsweredQuestionRef: Int = getColumnIndexOrThrow(_stmt, "answeredQuestionRef")
        val _columnIndexOfContribution: Int = getColumnIndexOrThrow(_stmt, "contribution")
        val _columnIndexOfSeeds: Int = getColumnIndexOrThrow(_stmt, "seeds")
        val _result: SerialArc?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpArcIndex: Int
          _tmpArcIndex = _stmt.getLong(_columnIndexOfArcIndex).toInt()
          val _tmpConflict: String
          _tmpConflict = _stmt.getText(_columnIndexOfConflict)
          val _tmpQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfQuestionRef)) {
            _tmpQuestionRef = null
          } else {
            _tmpQuestionRef = _stmt.getLong(_columnIndexOfQuestionRef).toInt()
          }
          val _tmpPhaseBounds: String
          _tmpPhaseBounds = _stmt.getText(_columnIndexOfPhaseBounds)
          val _tmpAnsweredQuestionRef: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredQuestionRef)) {
            _tmpAnsweredQuestionRef = null
          } else {
            _tmpAnsweredQuestionRef = _stmt.getLong(_columnIndexOfAnsweredQuestionRef).toInt()
          }
          val _tmpContribution: String
          _tmpContribution = _stmt.getText(_columnIndexOfContribution)
          val _tmpSeeds: String
          _tmpSeeds = _stmt.getText(_columnIndexOfSeeds)
          _result = SerialArc(_tmpId,_tmpBookId,_tmpArcIndex,_tmpConflict,_tmpQuestionRef,_tmpPhaseBounds,_tmpAnsweredQuestionRef,_tmpContribution,_tmpSeeds)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteArc(arcId: Int) {
    val _sql: String = "DELETE FROM serial_arcs WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, arcId.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteArcsForBook(bookId: Int) {
    val _sql: String = "DELETE FROM serial_arcs WHERE bookId = ?"
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
