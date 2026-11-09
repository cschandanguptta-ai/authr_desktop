package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.Chapter
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
public class ChapterDao_Impl(
  __db: RoomDatabase,
) : ChapterDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChapter: EntityInsertAdapter<Chapter>

  private val __updateAdapterOfChapter: EntityDeleteOrUpdateAdapter<Chapter>
  init {
    this.__db = __db
    this.__insertAdapterOfChapter = object : EntityInsertAdapter<Chapter>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `chapters` (`id`,`bookId`,`chapterNumber`,`title`,`content`,`summary`,`status`,`writerConfirmed`,`draftStatus`,`arcId`,`endingType`,`beatCoverage`,`balanceShares`,`worksheet`,`qcScores`,`qcTotal`,`hookCheck`,`ctaNote`,`pollJson`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Chapter) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindLong(3, entity.chapterNumber.toLong())
        statement.bindText(4, entity.title)
        statement.bindText(5, entity.content)
        statement.bindText(6, entity.summary)
        statement.bindText(7, entity.status)
        val _tmp: Int = if (entity.writerConfirmed) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.draftStatus)
        val _tmpArcId: Int? = entity.arcId
        if (_tmpArcId == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpArcId.toLong())
        }
        val _tmpEndingType: String? = entity.endingType
        if (_tmpEndingType == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpEndingType)
        }
        val _tmpBeatCoverage: String? = entity.beatCoverage
        if (_tmpBeatCoverage == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpBeatCoverage)
        }
        val _tmpBalanceShares: String? = entity.balanceShares
        if (_tmpBalanceShares == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpBalanceShares)
        }
        val _tmpWorksheet: String? = entity.worksheet
        if (_tmpWorksheet == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpWorksheet)
        }
        val _tmpQcScores: String? = entity.qcScores
        if (_tmpQcScores == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpQcScores)
        }
        val _tmpQcTotal: Int? = entity.qcTotal
        if (_tmpQcTotal == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpQcTotal.toLong())
        }
        val _tmpHookCheck: String? = entity.hookCheck
        if (_tmpHookCheck == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpHookCheck)
        }
        val _tmpCtaNote: String? = entity.ctaNote
        if (_tmpCtaNote == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpCtaNote)
        }
        val _tmpPollJson: String? = entity.pollJson
        if (_tmpPollJson == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpPollJson)
        }
        statement.bindLong(20, entity.createdAt)
      }
    }
    this.__updateAdapterOfChapter = object : EntityDeleteOrUpdateAdapter<Chapter>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `chapters` SET `id` = ?,`bookId` = ?,`chapterNumber` = ?,`title` = ?,`content` = ?,`summary` = ?,`status` = ?,`writerConfirmed` = ?,`draftStatus` = ?,`arcId` = ?,`endingType` = ?,`beatCoverage` = ?,`balanceShares` = ?,`worksheet` = ?,`qcScores` = ?,`qcTotal` = ?,`hookCheck` = ?,`ctaNote` = ?,`pollJson` = ?,`createdAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Chapter) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.bookId.toLong())
        statement.bindLong(3, entity.chapterNumber.toLong())
        statement.bindText(4, entity.title)
        statement.bindText(5, entity.content)
        statement.bindText(6, entity.summary)
        statement.bindText(7, entity.status)
        val _tmp: Int = if (entity.writerConfirmed) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.draftStatus)
        val _tmpArcId: Int? = entity.arcId
        if (_tmpArcId == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpArcId.toLong())
        }
        val _tmpEndingType: String? = entity.endingType
        if (_tmpEndingType == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpEndingType)
        }
        val _tmpBeatCoverage: String? = entity.beatCoverage
        if (_tmpBeatCoverage == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpBeatCoverage)
        }
        val _tmpBalanceShares: String? = entity.balanceShares
        if (_tmpBalanceShares == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpBalanceShares)
        }
        val _tmpWorksheet: String? = entity.worksheet
        if (_tmpWorksheet == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpWorksheet)
        }
        val _tmpQcScores: String? = entity.qcScores
        if (_tmpQcScores == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpQcScores)
        }
        val _tmpQcTotal: Int? = entity.qcTotal
        if (_tmpQcTotal == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpQcTotal.toLong())
        }
        val _tmpHookCheck: String? = entity.hookCheck
        if (_tmpHookCheck == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpHookCheck)
        }
        val _tmpCtaNote: String? = entity.ctaNote
        if (_tmpCtaNote == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpCtaNote)
        }
        val _tmpPollJson: String? = entity.pollJson
        if (_tmpPollJson == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpPollJson)
        }
        statement.bindLong(20, entity.createdAt)
        statement.bindLong(21, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertChapter(chapter: Chapter): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfChapter.insertAndReturnId(_connection, chapter)
    _result
  }

  public override suspend fun insertChapters(chapters: List<Chapter>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChapter.insert(_connection, chapters)
  }

  public override suspend fun updateChapter(chapter: Chapter): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfChapter.handle(_connection, chapter)
  }

  public override fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> {
    val _sql: String = "SELECT * FROM chapters WHERE bookId = ? ORDER BY chapterNumber ASC"
    return createFlow(__db, false, arrayOf("chapters")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfWriterConfirmed: Int = getColumnIndexOrThrow(_stmt, "writerConfirmed")
        val _columnIndexOfDraftStatus: Int = getColumnIndexOrThrow(_stmt, "draftStatus")
        val _columnIndexOfArcId: Int = getColumnIndexOrThrow(_stmt, "arcId")
        val _columnIndexOfEndingType: Int = getColumnIndexOrThrow(_stmt, "endingType")
        val _columnIndexOfBeatCoverage: Int = getColumnIndexOrThrow(_stmt, "beatCoverage")
        val _columnIndexOfBalanceShares: Int = getColumnIndexOrThrow(_stmt, "balanceShares")
        val _columnIndexOfWorksheet: Int = getColumnIndexOrThrow(_stmt, "worksheet")
        val _columnIndexOfQcScores: Int = getColumnIndexOrThrow(_stmt, "qcScores")
        val _columnIndexOfQcTotal: Int = getColumnIndexOrThrow(_stmt, "qcTotal")
        val _columnIndexOfHookCheck: Int = getColumnIndexOrThrow(_stmt, "hookCheck")
        val _columnIndexOfCtaNote: Int = getColumnIndexOrThrow(_stmt, "ctaNote")
        val _columnIndexOfPollJson: Int = getColumnIndexOrThrow(_stmt, "pollJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Chapter> = mutableListOf()
        while (_stmt.step()) {
          val _item: Chapter
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpChapterNumber: Int
          _tmpChapterNumber = _stmt.getLong(_columnIndexOfChapterNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpWriterConfirmed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfWriterConfirmed).toInt()
          _tmpWriterConfirmed = _tmp != 0
          val _tmpDraftStatus: String
          _tmpDraftStatus = _stmt.getText(_columnIndexOfDraftStatus)
          val _tmpArcId: Int?
          if (_stmt.isNull(_columnIndexOfArcId)) {
            _tmpArcId = null
          } else {
            _tmpArcId = _stmt.getLong(_columnIndexOfArcId).toInt()
          }
          val _tmpEndingType: String?
          if (_stmt.isNull(_columnIndexOfEndingType)) {
            _tmpEndingType = null
          } else {
            _tmpEndingType = _stmt.getText(_columnIndexOfEndingType)
          }
          val _tmpBeatCoverage: String?
          if (_stmt.isNull(_columnIndexOfBeatCoverage)) {
            _tmpBeatCoverage = null
          } else {
            _tmpBeatCoverage = _stmt.getText(_columnIndexOfBeatCoverage)
          }
          val _tmpBalanceShares: String?
          if (_stmt.isNull(_columnIndexOfBalanceShares)) {
            _tmpBalanceShares = null
          } else {
            _tmpBalanceShares = _stmt.getText(_columnIndexOfBalanceShares)
          }
          val _tmpWorksheet: String?
          if (_stmt.isNull(_columnIndexOfWorksheet)) {
            _tmpWorksheet = null
          } else {
            _tmpWorksheet = _stmt.getText(_columnIndexOfWorksheet)
          }
          val _tmpQcScores: String?
          if (_stmt.isNull(_columnIndexOfQcScores)) {
            _tmpQcScores = null
          } else {
            _tmpQcScores = _stmt.getText(_columnIndexOfQcScores)
          }
          val _tmpQcTotal: Int?
          if (_stmt.isNull(_columnIndexOfQcTotal)) {
            _tmpQcTotal = null
          } else {
            _tmpQcTotal = _stmt.getLong(_columnIndexOfQcTotal).toInt()
          }
          val _tmpHookCheck: String?
          if (_stmt.isNull(_columnIndexOfHookCheck)) {
            _tmpHookCheck = null
          } else {
            _tmpHookCheck = _stmt.getText(_columnIndexOfHookCheck)
          }
          val _tmpCtaNote: String?
          if (_stmt.isNull(_columnIndexOfCtaNote)) {
            _tmpCtaNote = null
          } else {
            _tmpCtaNote = _stmt.getText(_columnIndexOfCtaNote)
          }
          val _tmpPollJson: String?
          if (_stmt.isNull(_columnIndexOfPollJson)) {
            _tmpPollJson = null
          } else {
            _tmpPollJson = _stmt.getText(_columnIndexOfPollJson)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Chapter(_tmpId,_tmpBookId,_tmpChapterNumber,_tmpTitle,_tmpContent,_tmpSummary,_tmpStatus,_tmpWriterConfirmed,_tmpDraftStatus,_tmpArcId,_tmpEndingType,_tmpBeatCoverage,_tmpBalanceShares,_tmpWorksheet,_tmpQcScores,_tmpQcTotal,_tmpHookCheck,_tmpCtaNote,_tmpPollJson,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChaptersForBookSync(bookId: Int): List<Chapter> {
    val _sql: String = "SELECT * FROM chapters WHERE bookId = ? ORDER BY chapterNumber ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfWriterConfirmed: Int = getColumnIndexOrThrow(_stmt, "writerConfirmed")
        val _columnIndexOfDraftStatus: Int = getColumnIndexOrThrow(_stmt, "draftStatus")
        val _columnIndexOfArcId: Int = getColumnIndexOrThrow(_stmt, "arcId")
        val _columnIndexOfEndingType: Int = getColumnIndexOrThrow(_stmt, "endingType")
        val _columnIndexOfBeatCoverage: Int = getColumnIndexOrThrow(_stmt, "beatCoverage")
        val _columnIndexOfBalanceShares: Int = getColumnIndexOrThrow(_stmt, "balanceShares")
        val _columnIndexOfWorksheet: Int = getColumnIndexOrThrow(_stmt, "worksheet")
        val _columnIndexOfQcScores: Int = getColumnIndexOrThrow(_stmt, "qcScores")
        val _columnIndexOfQcTotal: Int = getColumnIndexOrThrow(_stmt, "qcTotal")
        val _columnIndexOfHookCheck: Int = getColumnIndexOrThrow(_stmt, "hookCheck")
        val _columnIndexOfCtaNote: Int = getColumnIndexOrThrow(_stmt, "ctaNote")
        val _columnIndexOfPollJson: Int = getColumnIndexOrThrow(_stmt, "pollJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Chapter> = mutableListOf()
        while (_stmt.step()) {
          val _item: Chapter
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpChapterNumber: Int
          _tmpChapterNumber = _stmt.getLong(_columnIndexOfChapterNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpWriterConfirmed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfWriterConfirmed).toInt()
          _tmpWriterConfirmed = _tmp != 0
          val _tmpDraftStatus: String
          _tmpDraftStatus = _stmt.getText(_columnIndexOfDraftStatus)
          val _tmpArcId: Int?
          if (_stmt.isNull(_columnIndexOfArcId)) {
            _tmpArcId = null
          } else {
            _tmpArcId = _stmt.getLong(_columnIndexOfArcId).toInt()
          }
          val _tmpEndingType: String?
          if (_stmt.isNull(_columnIndexOfEndingType)) {
            _tmpEndingType = null
          } else {
            _tmpEndingType = _stmt.getText(_columnIndexOfEndingType)
          }
          val _tmpBeatCoverage: String?
          if (_stmt.isNull(_columnIndexOfBeatCoverage)) {
            _tmpBeatCoverage = null
          } else {
            _tmpBeatCoverage = _stmt.getText(_columnIndexOfBeatCoverage)
          }
          val _tmpBalanceShares: String?
          if (_stmt.isNull(_columnIndexOfBalanceShares)) {
            _tmpBalanceShares = null
          } else {
            _tmpBalanceShares = _stmt.getText(_columnIndexOfBalanceShares)
          }
          val _tmpWorksheet: String?
          if (_stmt.isNull(_columnIndexOfWorksheet)) {
            _tmpWorksheet = null
          } else {
            _tmpWorksheet = _stmt.getText(_columnIndexOfWorksheet)
          }
          val _tmpQcScores: String?
          if (_stmt.isNull(_columnIndexOfQcScores)) {
            _tmpQcScores = null
          } else {
            _tmpQcScores = _stmt.getText(_columnIndexOfQcScores)
          }
          val _tmpQcTotal: Int?
          if (_stmt.isNull(_columnIndexOfQcTotal)) {
            _tmpQcTotal = null
          } else {
            _tmpQcTotal = _stmt.getLong(_columnIndexOfQcTotal).toInt()
          }
          val _tmpHookCheck: String?
          if (_stmt.isNull(_columnIndexOfHookCheck)) {
            _tmpHookCheck = null
          } else {
            _tmpHookCheck = _stmt.getText(_columnIndexOfHookCheck)
          }
          val _tmpCtaNote: String?
          if (_stmt.isNull(_columnIndexOfCtaNote)) {
            _tmpCtaNote = null
          } else {
            _tmpCtaNote = _stmt.getText(_columnIndexOfCtaNote)
          }
          val _tmpPollJson: String?
          if (_stmt.isNull(_columnIndexOfPollJson)) {
            _tmpPollJson = null
          } else {
            _tmpPollJson = _stmt.getText(_columnIndexOfPollJson)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Chapter(_tmpId,_tmpBookId,_tmpChapterNumber,_tmpTitle,_tmpContent,_tmpSummary,_tmpStatus,_tmpWriterConfirmed,_tmpDraftStatus,_tmpArcId,_tmpEndingType,_tmpBeatCoverage,_tmpBalanceShares,_tmpWorksheet,_tmpQcScores,_tmpQcTotal,_tmpHookCheck,_tmpCtaNote,_tmpPollJson,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getProposedChaptersForBookSync(bookId: Int): List<Chapter> {
    val _sql: String = "SELECT * FROM chapters WHERE bookId = ? AND draftStatus = 'PROPOSED' ORDER BY chapterNumber ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfWriterConfirmed: Int = getColumnIndexOrThrow(_stmt, "writerConfirmed")
        val _columnIndexOfDraftStatus: Int = getColumnIndexOrThrow(_stmt, "draftStatus")
        val _columnIndexOfArcId: Int = getColumnIndexOrThrow(_stmt, "arcId")
        val _columnIndexOfEndingType: Int = getColumnIndexOrThrow(_stmt, "endingType")
        val _columnIndexOfBeatCoverage: Int = getColumnIndexOrThrow(_stmt, "beatCoverage")
        val _columnIndexOfBalanceShares: Int = getColumnIndexOrThrow(_stmt, "balanceShares")
        val _columnIndexOfWorksheet: Int = getColumnIndexOrThrow(_stmt, "worksheet")
        val _columnIndexOfQcScores: Int = getColumnIndexOrThrow(_stmt, "qcScores")
        val _columnIndexOfQcTotal: Int = getColumnIndexOrThrow(_stmt, "qcTotal")
        val _columnIndexOfHookCheck: Int = getColumnIndexOrThrow(_stmt, "hookCheck")
        val _columnIndexOfCtaNote: Int = getColumnIndexOrThrow(_stmt, "ctaNote")
        val _columnIndexOfPollJson: Int = getColumnIndexOrThrow(_stmt, "pollJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Chapter> = mutableListOf()
        while (_stmt.step()) {
          val _item: Chapter
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpChapterNumber: Int
          _tmpChapterNumber = _stmt.getLong(_columnIndexOfChapterNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpWriterConfirmed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfWriterConfirmed).toInt()
          _tmpWriterConfirmed = _tmp != 0
          val _tmpDraftStatus: String
          _tmpDraftStatus = _stmt.getText(_columnIndexOfDraftStatus)
          val _tmpArcId: Int?
          if (_stmt.isNull(_columnIndexOfArcId)) {
            _tmpArcId = null
          } else {
            _tmpArcId = _stmt.getLong(_columnIndexOfArcId).toInt()
          }
          val _tmpEndingType: String?
          if (_stmt.isNull(_columnIndexOfEndingType)) {
            _tmpEndingType = null
          } else {
            _tmpEndingType = _stmt.getText(_columnIndexOfEndingType)
          }
          val _tmpBeatCoverage: String?
          if (_stmt.isNull(_columnIndexOfBeatCoverage)) {
            _tmpBeatCoverage = null
          } else {
            _tmpBeatCoverage = _stmt.getText(_columnIndexOfBeatCoverage)
          }
          val _tmpBalanceShares: String?
          if (_stmt.isNull(_columnIndexOfBalanceShares)) {
            _tmpBalanceShares = null
          } else {
            _tmpBalanceShares = _stmt.getText(_columnIndexOfBalanceShares)
          }
          val _tmpWorksheet: String?
          if (_stmt.isNull(_columnIndexOfWorksheet)) {
            _tmpWorksheet = null
          } else {
            _tmpWorksheet = _stmt.getText(_columnIndexOfWorksheet)
          }
          val _tmpQcScores: String?
          if (_stmt.isNull(_columnIndexOfQcScores)) {
            _tmpQcScores = null
          } else {
            _tmpQcScores = _stmt.getText(_columnIndexOfQcScores)
          }
          val _tmpQcTotal: Int?
          if (_stmt.isNull(_columnIndexOfQcTotal)) {
            _tmpQcTotal = null
          } else {
            _tmpQcTotal = _stmt.getLong(_columnIndexOfQcTotal).toInt()
          }
          val _tmpHookCheck: String?
          if (_stmt.isNull(_columnIndexOfHookCheck)) {
            _tmpHookCheck = null
          } else {
            _tmpHookCheck = _stmt.getText(_columnIndexOfHookCheck)
          }
          val _tmpCtaNote: String?
          if (_stmt.isNull(_columnIndexOfCtaNote)) {
            _tmpCtaNote = null
          } else {
            _tmpCtaNote = _stmt.getText(_columnIndexOfCtaNote)
          }
          val _tmpPollJson: String?
          if (_stmt.isNull(_columnIndexOfPollJson)) {
            _tmpPollJson = null
          } else {
            _tmpPollJson = _stmt.getText(_columnIndexOfPollJson)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Chapter(_tmpId,_tmpBookId,_tmpChapterNumber,_tmpTitle,_tmpContent,_tmpSummary,_tmpStatus,_tmpWriterConfirmed,_tmpDraftStatus,_tmpArcId,_tmpEndingType,_tmpBeatCoverage,_tmpBalanceShares,_tmpWorksheet,_tmpQcScores,_tmpQcTotal,_tmpHookCheck,_tmpCtaNote,_tmpPollJson,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChapterById(chapterId: Int): Chapter? {
    val _sql: String = "SELECT * FROM chapters WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, chapterId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfWriterConfirmed: Int = getColumnIndexOrThrow(_stmt, "writerConfirmed")
        val _columnIndexOfDraftStatus: Int = getColumnIndexOrThrow(_stmt, "draftStatus")
        val _columnIndexOfArcId: Int = getColumnIndexOrThrow(_stmt, "arcId")
        val _columnIndexOfEndingType: Int = getColumnIndexOrThrow(_stmt, "endingType")
        val _columnIndexOfBeatCoverage: Int = getColumnIndexOrThrow(_stmt, "beatCoverage")
        val _columnIndexOfBalanceShares: Int = getColumnIndexOrThrow(_stmt, "balanceShares")
        val _columnIndexOfWorksheet: Int = getColumnIndexOrThrow(_stmt, "worksheet")
        val _columnIndexOfQcScores: Int = getColumnIndexOrThrow(_stmt, "qcScores")
        val _columnIndexOfQcTotal: Int = getColumnIndexOrThrow(_stmt, "qcTotal")
        val _columnIndexOfHookCheck: Int = getColumnIndexOrThrow(_stmt, "hookCheck")
        val _columnIndexOfCtaNote: Int = getColumnIndexOrThrow(_stmt, "ctaNote")
        val _columnIndexOfPollJson: Int = getColumnIndexOrThrow(_stmt, "pollJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: Chapter?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpChapterNumber: Int
          _tmpChapterNumber = _stmt.getLong(_columnIndexOfChapterNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpWriterConfirmed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfWriterConfirmed).toInt()
          _tmpWriterConfirmed = _tmp != 0
          val _tmpDraftStatus: String
          _tmpDraftStatus = _stmt.getText(_columnIndexOfDraftStatus)
          val _tmpArcId: Int?
          if (_stmt.isNull(_columnIndexOfArcId)) {
            _tmpArcId = null
          } else {
            _tmpArcId = _stmt.getLong(_columnIndexOfArcId).toInt()
          }
          val _tmpEndingType: String?
          if (_stmt.isNull(_columnIndexOfEndingType)) {
            _tmpEndingType = null
          } else {
            _tmpEndingType = _stmt.getText(_columnIndexOfEndingType)
          }
          val _tmpBeatCoverage: String?
          if (_stmt.isNull(_columnIndexOfBeatCoverage)) {
            _tmpBeatCoverage = null
          } else {
            _tmpBeatCoverage = _stmt.getText(_columnIndexOfBeatCoverage)
          }
          val _tmpBalanceShares: String?
          if (_stmt.isNull(_columnIndexOfBalanceShares)) {
            _tmpBalanceShares = null
          } else {
            _tmpBalanceShares = _stmt.getText(_columnIndexOfBalanceShares)
          }
          val _tmpWorksheet: String?
          if (_stmt.isNull(_columnIndexOfWorksheet)) {
            _tmpWorksheet = null
          } else {
            _tmpWorksheet = _stmt.getText(_columnIndexOfWorksheet)
          }
          val _tmpQcScores: String?
          if (_stmt.isNull(_columnIndexOfQcScores)) {
            _tmpQcScores = null
          } else {
            _tmpQcScores = _stmt.getText(_columnIndexOfQcScores)
          }
          val _tmpQcTotal: Int?
          if (_stmt.isNull(_columnIndexOfQcTotal)) {
            _tmpQcTotal = null
          } else {
            _tmpQcTotal = _stmt.getLong(_columnIndexOfQcTotal).toInt()
          }
          val _tmpHookCheck: String?
          if (_stmt.isNull(_columnIndexOfHookCheck)) {
            _tmpHookCheck = null
          } else {
            _tmpHookCheck = _stmt.getText(_columnIndexOfHookCheck)
          }
          val _tmpCtaNote: String?
          if (_stmt.isNull(_columnIndexOfCtaNote)) {
            _tmpCtaNote = null
          } else {
            _tmpCtaNote = _stmt.getText(_columnIndexOfCtaNote)
          }
          val _tmpPollJson: String?
          if (_stmt.isNull(_columnIndexOfPollJson)) {
            _tmpPollJson = null
          } else {
            _tmpPollJson = _stmt.getText(_columnIndexOfPollJson)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = Chapter(_tmpId,_tmpBookId,_tmpChapterNumber,_tmpTitle,_tmpContent,_tmpSummary,_tmpStatus,_tmpWriterConfirmed,_tmpDraftStatus,_tmpArcId,_tmpEndingType,_tmpBeatCoverage,_tmpBalanceShares,_tmpWorksheet,_tmpQcScores,_tmpQcTotal,_tmpHookCheck,_tmpCtaNote,_tmpPollJson,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchChapters(query: String): List<Chapter> {
    val _sql: String = """
        |
        |        SELECT chapters.* 
        |        FROM chapters 
        |        JOIN chapters_fts ON chapters.id = chapters_fts.rowid 
        |        WHERE chapters_fts MATCH ?
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfWriterConfirmed: Int = getColumnIndexOrThrow(_stmt, "writerConfirmed")
        val _columnIndexOfDraftStatus: Int = getColumnIndexOrThrow(_stmt, "draftStatus")
        val _columnIndexOfArcId: Int = getColumnIndexOrThrow(_stmt, "arcId")
        val _columnIndexOfEndingType: Int = getColumnIndexOrThrow(_stmt, "endingType")
        val _columnIndexOfBeatCoverage: Int = getColumnIndexOrThrow(_stmt, "beatCoverage")
        val _columnIndexOfBalanceShares: Int = getColumnIndexOrThrow(_stmt, "balanceShares")
        val _columnIndexOfWorksheet: Int = getColumnIndexOrThrow(_stmt, "worksheet")
        val _columnIndexOfQcScores: Int = getColumnIndexOrThrow(_stmt, "qcScores")
        val _columnIndexOfQcTotal: Int = getColumnIndexOrThrow(_stmt, "qcTotal")
        val _columnIndexOfHookCheck: Int = getColumnIndexOrThrow(_stmt, "hookCheck")
        val _columnIndexOfCtaNote: Int = getColumnIndexOrThrow(_stmt, "ctaNote")
        val _columnIndexOfPollJson: Int = getColumnIndexOrThrow(_stmt, "pollJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Chapter> = mutableListOf()
        while (_stmt.step()) {
          val _item: Chapter
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpChapterNumber: Int
          _tmpChapterNumber = _stmt.getLong(_columnIndexOfChapterNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpWriterConfirmed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfWriterConfirmed).toInt()
          _tmpWriterConfirmed = _tmp != 0
          val _tmpDraftStatus: String
          _tmpDraftStatus = _stmt.getText(_columnIndexOfDraftStatus)
          val _tmpArcId: Int?
          if (_stmt.isNull(_columnIndexOfArcId)) {
            _tmpArcId = null
          } else {
            _tmpArcId = _stmt.getLong(_columnIndexOfArcId).toInt()
          }
          val _tmpEndingType: String?
          if (_stmt.isNull(_columnIndexOfEndingType)) {
            _tmpEndingType = null
          } else {
            _tmpEndingType = _stmt.getText(_columnIndexOfEndingType)
          }
          val _tmpBeatCoverage: String?
          if (_stmt.isNull(_columnIndexOfBeatCoverage)) {
            _tmpBeatCoverage = null
          } else {
            _tmpBeatCoverage = _stmt.getText(_columnIndexOfBeatCoverage)
          }
          val _tmpBalanceShares: String?
          if (_stmt.isNull(_columnIndexOfBalanceShares)) {
            _tmpBalanceShares = null
          } else {
            _tmpBalanceShares = _stmt.getText(_columnIndexOfBalanceShares)
          }
          val _tmpWorksheet: String?
          if (_stmt.isNull(_columnIndexOfWorksheet)) {
            _tmpWorksheet = null
          } else {
            _tmpWorksheet = _stmt.getText(_columnIndexOfWorksheet)
          }
          val _tmpQcScores: String?
          if (_stmt.isNull(_columnIndexOfQcScores)) {
            _tmpQcScores = null
          } else {
            _tmpQcScores = _stmt.getText(_columnIndexOfQcScores)
          }
          val _tmpQcTotal: Int?
          if (_stmt.isNull(_columnIndexOfQcTotal)) {
            _tmpQcTotal = null
          } else {
            _tmpQcTotal = _stmt.getLong(_columnIndexOfQcTotal).toInt()
          }
          val _tmpHookCheck: String?
          if (_stmt.isNull(_columnIndexOfHookCheck)) {
            _tmpHookCheck = null
          } else {
            _tmpHookCheck = _stmt.getText(_columnIndexOfHookCheck)
          }
          val _tmpCtaNote: String?
          if (_stmt.isNull(_columnIndexOfCtaNote)) {
            _tmpCtaNote = null
          } else {
            _tmpCtaNote = _stmt.getText(_columnIndexOfCtaNote)
          }
          val _tmpPollJson: String?
          if (_stmt.isNull(_columnIndexOfPollJson)) {
            _tmpPollJson = null
          } else {
            _tmpPollJson = _stmt.getText(_columnIndexOfPollJson)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Chapter(_tmpId,_tmpBookId,_tmpChapterNumber,_tmpTitle,_tmpContent,_tmpSummary,_tmpStatus,_tmpWriterConfirmed,_tmpDraftStatus,_tmpArcId,_tmpEndingType,_tmpBeatCoverage,_tmpBalanceShares,_tmpWorksheet,_tmpQcScores,_tmpQcTotal,_tmpHookCheck,_tmpCtaNote,_tmpPollJson,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteChapter(chapterId: Int) {
    val _sql: String = "DELETE FROM chapters WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, chapterId.toLong())
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
