package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.QuestionEntry
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
public class QuestionDao_Impl(
  __db: RoomDatabase,
) : QuestionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfQuestionEntry: EntityInsertAdapter<QuestionEntry>

  private val __updateAdapterOfQuestionEntry: EntityDeleteOrUpdateAdapter<QuestionEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfQuestionEntry = object : EntityInsertAdapter<QuestionEntry>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `questions` (`id`,`bookId`,`level`,`text`,`openedChapter`,`dueChapter`,`answeredChapter`,`answeredByReveal`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: QuestionEntry) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.level)
        statement.bindText(4, entity.text)
        statement.bindLong(5, entity.openedChapter.toLong())
        val _tmpDueChapter: Int? = entity.dueChapter
        if (_tmpDueChapter == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpDueChapter.toLong())
        }
        val _tmpAnsweredChapter: Int? = entity.answeredChapter
        if (_tmpAnsweredChapter == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpAnsweredChapter.toLong())
        }
        val _tmpAnsweredByReveal: Int? = entity.answeredByReveal
        if (_tmpAnsweredByReveal == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpAnsweredByReveal.toLong())
        }
      }
    }
    this.__updateAdapterOfQuestionEntry = object : EntityDeleteOrUpdateAdapter<QuestionEntry>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `questions` SET `id` = ?,`bookId` = ?,`level` = ?,`text` = ?,`openedChapter` = ?,`dueChapter` = ?,`answeredChapter` = ?,`answeredByReveal` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: QuestionEntry) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindText(3, entity.level)
        statement.bindText(4, entity.text)
        statement.bindLong(5, entity.openedChapter.toLong())
        val _tmpDueChapter: Int? = entity.dueChapter
        if (_tmpDueChapter == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpDueChapter.toLong())
        }
        val _tmpAnsweredChapter: Int? = entity.answeredChapter
        if (_tmpAnsweredChapter == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpAnsweredChapter.toLong())
        }
        val _tmpAnsweredByReveal: Int? = entity.answeredByReveal
        if (_tmpAnsweredByReveal == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpAnsweredByReveal.toLong())
        }
        statement.bindLong(9, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertQuestion(question: QuestionEntry): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfQuestionEntry.insertAndReturnId(_connection, question)
    _result
  }

  public override suspend fun insertQuestions(questions: List<QuestionEntry>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfQuestionEntry.insert(_connection, questions)
  }

  public override suspend fun updateQuestion(question: QuestionEntry): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfQuestionEntry.handle(_connection, question)
  }

  public override fun getQuestionsForBook(bookId: Int): Flow<List<QuestionEntry>> {
    val _sql: String = "SELECT * FROM questions WHERE bookId = ? ORDER BY openedChapter ASC"
    return createFlow(__db, false, arrayOf("questions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfOpenedChapter: Int = getColumnIndexOrThrow(_stmt, "openedChapter")
        val _columnIndexOfDueChapter: Int = getColumnIndexOrThrow(_stmt, "dueChapter")
        val _columnIndexOfAnsweredChapter: Int = getColumnIndexOrThrow(_stmt, "answeredChapter")
        val _columnIndexOfAnsweredByReveal: Int = getColumnIndexOrThrow(_stmt, "answeredByReveal")
        val _result: MutableList<QuestionEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: QuestionEntry
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpOpenedChapter: Int
          _tmpOpenedChapter = _stmt.getLong(_columnIndexOfOpenedChapter).toInt()
          val _tmpDueChapter: Int?
          if (_stmt.isNull(_columnIndexOfDueChapter)) {
            _tmpDueChapter = null
          } else {
            _tmpDueChapter = _stmt.getLong(_columnIndexOfDueChapter).toInt()
          }
          val _tmpAnsweredChapter: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredChapter)) {
            _tmpAnsweredChapter = null
          } else {
            _tmpAnsweredChapter = _stmt.getLong(_columnIndexOfAnsweredChapter).toInt()
          }
          val _tmpAnsweredByReveal: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredByReveal)) {
            _tmpAnsweredByReveal = null
          } else {
            _tmpAnsweredByReveal = _stmt.getLong(_columnIndexOfAnsweredByReveal).toInt()
          }
          _item = QuestionEntry(_tmpId,_tmpBookId,_tmpLevel,_tmpText,_tmpOpenedChapter,_tmpDueChapter,_tmpAnsweredChapter,_tmpAnsweredByReveal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getQuestionsForBookSync(bookId: Int): List<QuestionEntry> {
    val _sql: String = "SELECT * FROM questions WHERE bookId = ? ORDER BY openedChapter ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfOpenedChapter: Int = getColumnIndexOrThrow(_stmt, "openedChapter")
        val _columnIndexOfDueChapter: Int = getColumnIndexOrThrow(_stmt, "dueChapter")
        val _columnIndexOfAnsweredChapter: Int = getColumnIndexOrThrow(_stmt, "answeredChapter")
        val _columnIndexOfAnsweredByReveal: Int = getColumnIndexOrThrow(_stmt, "answeredByReveal")
        val _result: MutableList<QuestionEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: QuestionEntry
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpOpenedChapter: Int
          _tmpOpenedChapter = _stmt.getLong(_columnIndexOfOpenedChapter).toInt()
          val _tmpDueChapter: Int?
          if (_stmt.isNull(_columnIndexOfDueChapter)) {
            _tmpDueChapter = null
          } else {
            _tmpDueChapter = _stmt.getLong(_columnIndexOfDueChapter).toInt()
          }
          val _tmpAnsweredChapter: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredChapter)) {
            _tmpAnsweredChapter = null
          } else {
            _tmpAnsweredChapter = _stmt.getLong(_columnIndexOfAnsweredChapter).toInt()
          }
          val _tmpAnsweredByReveal: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredByReveal)) {
            _tmpAnsweredByReveal = null
          } else {
            _tmpAnsweredByReveal = _stmt.getLong(_columnIndexOfAnsweredByReveal).toInt()
          }
          _item = QuestionEntry(_tmpId,_tmpBookId,_tmpLevel,_tmpText,_tmpOpenedChapter,_tmpDueChapter,_tmpAnsweredChapter,_tmpAnsweredByReveal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getOverdueProgressiveQuestions(bookId: Int, currentChapter: Int): List<QuestionEntry> {
    val _sql: String = "SELECT * FROM questions WHERE bookId = ? AND level = 'PROGRESSIVE' AND answeredChapter IS NULL AND dueChapter IS NOT NULL AND dueChapter <= ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, currentChapter.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfOpenedChapter: Int = getColumnIndexOrThrow(_stmt, "openedChapter")
        val _columnIndexOfDueChapter: Int = getColumnIndexOrThrow(_stmt, "dueChapter")
        val _columnIndexOfAnsweredChapter: Int = getColumnIndexOrThrow(_stmt, "answeredChapter")
        val _columnIndexOfAnsweredByReveal: Int = getColumnIndexOrThrow(_stmt, "answeredByReveal")
        val _result: MutableList<QuestionEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: QuestionEntry
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpOpenedChapter: Int
          _tmpOpenedChapter = _stmt.getLong(_columnIndexOfOpenedChapter).toInt()
          val _tmpDueChapter: Int?
          if (_stmt.isNull(_columnIndexOfDueChapter)) {
            _tmpDueChapter = null
          } else {
            _tmpDueChapter = _stmt.getLong(_columnIndexOfDueChapter).toInt()
          }
          val _tmpAnsweredChapter: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredChapter)) {
            _tmpAnsweredChapter = null
          } else {
            _tmpAnsweredChapter = _stmt.getLong(_columnIndexOfAnsweredChapter).toInt()
          }
          val _tmpAnsweredByReveal: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredByReveal)) {
            _tmpAnsweredByReveal = null
          } else {
            _tmpAnsweredByReveal = _stmt.getLong(_columnIndexOfAnsweredByReveal).toInt()
          }
          _item = QuestionEntry(_tmpId,_tmpBookId,_tmpLevel,_tmpText,_tmpOpenedChapter,_tmpDueChapter,_tmpAnsweredChapter,_tmpAnsweredByReveal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getQuestionById(questionId: Int): QuestionEntry? {
    val _sql: String = "SELECT * FROM questions WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, questionId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfOpenedChapter: Int = getColumnIndexOrThrow(_stmt, "openedChapter")
        val _columnIndexOfDueChapter: Int = getColumnIndexOrThrow(_stmt, "dueChapter")
        val _columnIndexOfAnsweredChapter: Int = getColumnIndexOrThrow(_stmt, "answeredChapter")
        val _columnIndexOfAnsweredByReveal: Int = getColumnIndexOrThrow(_stmt, "answeredByReveal")
        val _result: QuestionEntry?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpOpenedChapter: Int
          _tmpOpenedChapter = _stmt.getLong(_columnIndexOfOpenedChapter).toInt()
          val _tmpDueChapter: Int?
          if (_stmt.isNull(_columnIndexOfDueChapter)) {
            _tmpDueChapter = null
          } else {
            _tmpDueChapter = _stmt.getLong(_columnIndexOfDueChapter).toInt()
          }
          val _tmpAnsweredChapter: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredChapter)) {
            _tmpAnsweredChapter = null
          } else {
            _tmpAnsweredChapter = _stmt.getLong(_columnIndexOfAnsweredChapter).toInt()
          }
          val _tmpAnsweredByReveal: Int?
          if (_stmt.isNull(_columnIndexOfAnsweredByReveal)) {
            _tmpAnsweredByReveal = null
          } else {
            _tmpAnsweredByReveal = _stmt.getLong(_columnIndexOfAnsweredByReveal).toInt()
          }
          _result = QuestionEntry(_tmpId,_tmpBookId,_tmpLevel,_tmpText,_tmpOpenedChapter,_tmpDueChapter,_tmpAnsweredChapter,_tmpAnsweredByReveal)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteQuestion(questionId: Int) {
    val _sql: String = "DELETE FROM questions WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, questionId.toLong())
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
