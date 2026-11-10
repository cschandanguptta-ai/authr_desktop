package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.ChekhovGun
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
public class ChekhovGunDao_Impl(
  __db: RoomDatabase,
) : ChekhovGunDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChekhovGun: EntityInsertAdapter<ChekhovGun>

  private val __updateAdapterOfChekhovGun: EntityDeleteOrUpdateAdapter<ChekhovGun>
  init {
    this.__db = __db
    this.__insertAdapterOfChekhovGun = object : EntityInsertAdapter<ChekhovGun>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `chekhov_guns` (`id`,`bookId`,`name`,`description`,`status`,`introducedChapter`,`escalationChapters`,`resolvedChapter`,`quote`,`severity`,`waiveNote`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChekhovGun) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.description)
        statement.bindText(5, entity.status)
        statement.bindLong(6, entity.introducedChapter.toLong())
        statement.bindText(7, entity.escalationChapters)
        val _tmpResolvedChapter: Int? = entity.resolvedChapter
        if (_tmpResolvedChapter == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpResolvedChapter.toLong())
        }
        statement.bindText(9, entity.quote)
        statement.bindText(10, entity.severity)
        statement.bindText(11, entity.waiveNote)
      }
    }
    this.__updateAdapterOfChekhovGun = object : EntityDeleteOrUpdateAdapter<ChekhovGun>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `chekhov_guns` SET `id` = ?,`bookId` = ?,`name` = ?,`description` = ?,`status` = ?,`introducedChapter` = ?,`escalationChapters` = ?,`resolvedChapter` = ?,`quote` = ?,`severity` = ?,`waiveNote` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ChekhovGun) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.description)
        statement.bindText(5, entity.status)
        statement.bindLong(6, entity.introducedChapter.toLong())
        statement.bindText(7, entity.escalationChapters)
        val _tmpResolvedChapter: Int? = entity.resolvedChapter
        if (_tmpResolvedChapter == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpResolvedChapter.toLong())
        }
        statement.bindText(9, entity.quote)
        statement.bindText(10, entity.severity)
        statement.bindText(11, entity.waiveNote)
        statement.bindLong(12, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertGun(gun: ChekhovGun): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfChekhovGun.insertAndReturnId(_connection, gun)
    _result
  }

  public override suspend fun insertGuns(guns: List<ChekhovGun>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChekhovGun.insert(_connection, guns)
  }

  public override suspend fun updateGun(gun: ChekhovGun): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfChekhovGun.handle(_connection, gun)
  }

  public override fun getGunsForBook(bookId: Int): Flow<List<ChekhovGun>> {
    val _sql: String = "SELECT * FROM chekhov_guns WHERE bookId = ? ORDER BY introducedChapter ASC"
    return createFlow(__db, false, arrayOf("chekhov_guns")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIntroducedChapter: Int = getColumnIndexOrThrow(_stmt, "introducedChapter")
        val _columnIndexOfEscalationChapters: Int = getColumnIndexOrThrow(_stmt, "escalationChapters")
        val _columnIndexOfResolvedChapter: Int = getColumnIndexOrThrow(_stmt, "resolvedChapter")
        val _columnIndexOfQuote: Int = getColumnIndexOrThrow(_stmt, "quote")
        val _columnIndexOfSeverity: Int = getColumnIndexOrThrow(_stmt, "severity")
        val _columnIndexOfWaiveNote: Int = getColumnIndexOrThrow(_stmt, "waiveNote")
        val _result: MutableList<ChekhovGun> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChekhovGun
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpIntroducedChapter: Int
          _tmpIntroducedChapter = _stmt.getLong(_columnIndexOfIntroducedChapter).toInt()
          val _tmpEscalationChapters: String
          _tmpEscalationChapters = _stmt.getText(_columnIndexOfEscalationChapters)
          val _tmpResolvedChapter: Int?
          if (_stmt.isNull(_columnIndexOfResolvedChapter)) {
            _tmpResolvedChapter = null
          } else {
            _tmpResolvedChapter = _stmt.getLong(_columnIndexOfResolvedChapter).toInt()
          }
          val _tmpQuote: String
          _tmpQuote = _stmt.getText(_columnIndexOfQuote)
          val _tmpSeverity: String
          _tmpSeverity = _stmt.getText(_columnIndexOfSeverity)
          val _tmpWaiveNote: String
          _tmpWaiveNote = _stmt.getText(_columnIndexOfWaiveNote)
          _item = ChekhovGun(_tmpId,_tmpBookId,_tmpName,_tmpDescription,_tmpStatus,_tmpIntroducedChapter,_tmpEscalationChapters,_tmpResolvedChapter,_tmpQuote,_tmpSeverity,_tmpWaiveNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getGunsForBookSync(bookId: Int): List<ChekhovGun> {
    val _sql: String = "SELECT * FROM chekhov_guns WHERE bookId = ? ORDER BY introducedChapter ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIntroducedChapter: Int = getColumnIndexOrThrow(_stmt, "introducedChapter")
        val _columnIndexOfEscalationChapters: Int = getColumnIndexOrThrow(_stmt, "escalationChapters")
        val _columnIndexOfResolvedChapter: Int = getColumnIndexOrThrow(_stmt, "resolvedChapter")
        val _columnIndexOfQuote: Int = getColumnIndexOrThrow(_stmt, "quote")
        val _columnIndexOfSeverity: Int = getColumnIndexOrThrow(_stmt, "severity")
        val _columnIndexOfWaiveNote: Int = getColumnIndexOrThrow(_stmt, "waiveNote")
        val _result: MutableList<ChekhovGun> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChekhovGun
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpIntroducedChapter: Int
          _tmpIntroducedChapter = _stmt.getLong(_columnIndexOfIntroducedChapter).toInt()
          val _tmpEscalationChapters: String
          _tmpEscalationChapters = _stmt.getText(_columnIndexOfEscalationChapters)
          val _tmpResolvedChapter: Int?
          if (_stmt.isNull(_columnIndexOfResolvedChapter)) {
            _tmpResolvedChapter = null
          } else {
            _tmpResolvedChapter = _stmt.getLong(_columnIndexOfResolvedChapter).toInt()
          }
          val _tmpQuote: String
          _tmpQuote = _stmt.getText(_columnIndexOfQuote)
          val _tmpSeverity: String
          _tmpSeverity = _stmt.getText(_columnIndexOfSeverity)
          val _tmpWaiveNote: String
          _tmpWaiveNote = _stmt.getText(_columnIndexOfWaiveNote)
          _item = ChekhovGun(_tmpId,_tmpBookId,_tmpName,_tmpDescription,_tmpStatus,_tmpIntroducedChapter,_tmpEscalationChapters,_tmpResolvedChapter,_tmpQuote,_tmpSeverity,_tmpWaiveNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun> {
    val _sql: String = "SELECT * FROM chekhov_guns WHERE bookId = ? AND status IN ('INTRODUCED', 'ESCALATED', 'ORPHANED') ORDER BY introducedChapter ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIntroducedChapter: Int = getColumnIndexOrThrow(_stmt, "introducedChapter")
        val _columnIndexOfEscalationChapters: Int = getColumnIndexOrThrow(_stmt, "escalationChapters")
        val _columnIndexOfResolvedChapter: Int = getColumnIndexOrThrow(_stmt, "resolvedChapter")
        val _columnIndexOfQuote: Int = getColumnIndexOrThrow(_stmt, "quote")
        val _columnIndexOfSeverity: Int = getColumnIndexOrThrow(_stmt, "severity")
        val _columnIndexOfWaiveNote: Int = getColumnIndexOrThrow(_stmt, "waiveNote")
        val _result: MutableList<ChekhovGun> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChekhovGun
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpIntroducedChapter: Int
          _tmpIntroducedChapter = _stmt.getLong(_columnIndexOfIntroducedChapter).toInt()
          val _tmpEscalationChapters: String
          _tmpEscalationChapters = _stmt.getText(_columnIndexOfEscalationChapters)
          val _tmpResolvedChapter: Int?
          if (_stmt.isNull(_columnIndexOfResolvedChapter)) {
            _tmpResolvedChapter = null
          } else {
            _tmpResolvedChapter = _stmt.getLong(_columnIndexOfResolvedChapter).toInt()
          }
          val _tmpQuote: String
          _tmpQuote = _stmt.getText(_columnIndexOfQuote)
          val _tmpSeverity: String
          _tmpSeverity = _stmt.getText(_columnIndexOfSeverity)
          val _tmpWaiveNote: String
          _tmpWaiveNote = _stmt.getText(_columnIndexOfWaiveNote)
          _item = ChekhovGun(_tmpId,_tmpBookId,_tmpName,_tmpDescription,_tmpStatus,_tmpIntroducedChapter,_tmpEscalationChapters,_tmpResolvedChapter,_tmpQuote,_tmpSeverity,_tmpWaiveNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getGunById(gunId: Int): ChekhovGun? {
    val _sql: String = "SELECT * FROM chekhov_guns WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, gunId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfIntroducedChapter: Int = getColumnIndexOrThrow(_stmt, "introducedChapter")
        val _columnIndexOfEscalationChapters: Int = getColumnIndexOrThrow(_stmt, "escalationChapters")
        val _columnIndexOfResolvedChapter: Int = getColumnIndexOrThrow(_stmt, "resolvedChapter")
        val _columnIndexOfQuote: Int = getColumnIndexOrThrow(_stmt, "quote")
        val _columnIndexOfSeverity: Int = getColumnIndexOrThrow(_stmt, "severity")
        val _columnIndexOfWaiveNote: Int = getColumnIndexOrThrow(_stmt, "waiveNote")
        val _result: ChekhovGun?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpIntroducedChapter: Int
          _tmpIntroducedChapter = _stmt.getLong(_columnIndexOfIntroducedChapter).toInt()
          val _tmpEscalationChapters: String
          _tmpEscalationChapters = _stmt.getText(_columnIndexOfEscalationChapters)
          val _tmpResolvedChapter: Int?
          if (_stmt.isNull(_columnIndexOfResolvedChapter)) {
            _tmpResolvedChapter = null
          } else {
            _tmpResolvedChapter = _stmt.getLong(_columnIndexOfResolvedChapter).toInt()
          }
          val _tmpQuote: String
          _tmpQuote = _stmt.getText(_columnIndexOfQuote)
          val _tmpSeverity: String
          _tmpSeverity = _stmt.getText(_columnIndexOfSeverity)
          val _tmpWaiveNote: String
          _tmpWaiveNote = _stmt.getText(_columnIndexOfWaiveNote)
          _result = ChekhovGun(_tmpId,_tmpBookId,_tmpName,_tmpDescription,_tmpStatus,_tmpIntroducedChapter,_tmpEscalationChapters,_tmpResolvedChapter,_tmpQuote,_tmpSeverity,_tmpWaiveNote)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteGun(gunId: Int) {
    val _sql: String = "DELETE FROM chekhov_guns WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, gunId.toLong())
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
