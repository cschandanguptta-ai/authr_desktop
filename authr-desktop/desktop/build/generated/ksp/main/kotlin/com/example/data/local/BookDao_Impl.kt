package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.`data`.model.Book
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
public class BookDao_Impl(
  __db: RoomDatabase,
) : BookDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBook: EntityInsertAdapter<Book>

  private val __updateAdapterOfBook: EntityDeleteOrUpdateAdapter<Book>
  init {
    this.__db = __db
    this.__insertAdapterOfBook = object : EntityInsertAdapter<Book>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `books` (`id`,`title`,`genre`,`synopsis`,`outline`,`personaId`,`format`,`targetWordCount`,`narrativeStructure`,`chapterLength`,`isSerialized`,`targetChapters`,`releaseSchedule`,`freeTierChapters`,`wordMin`,`wordMax`,`bufferTarget`,`exportProfile`,`compiledManuscript`,`currentPhase`,`storyBible`,`characterBible`,`worldBible`,`plotPointsHistory`,`memoryState`,`agentLogs`,`marketingMaterials`,`coverPrompt`,`completionPercentage`,`wordsPerMinute`,`readabilityScore`,`emotionalIntensity`,`pacingHeatmap`,`continuityWarnings`,`createdAt`,`lastModifiedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Book) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.genre)
        statement.bindText(4, entity.synopsis)
        statement.bindText(5, entity.outline)
        statement.bindText(6, entity.personaId)
        statement.bindText(7, entity.format)
        statement.bindLong(8, entity.targetWordCount.toLong())
        statement.bindText(9, entity.narrativeStructure)
        statement.bindText(10, entity.chapterLength)
        val _tmp: Int = if (entity.isSerialized) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindLong(12, entity.targetChapters.toLong())
        statement.bindText(13, entity.releaseSchedule)
        statement.bindLong(14, entity.freeTierChapters.toLong())
        statement.bindLong(15, entity.wordMin.toLong())
        statement.bindLong(16, entity.wordMax.toLong())
        statement.bindLong(17, entity.bufferTarget.toLong())
        statement.bindText(18, entity.exportProfile)
        statement.bindText(19, entity.compiledManuscript)
        statement.bindText(20, entity.currentPhase)
        statement.bindText(21, entity.storyBible)
        statement.bindText(22, entity.characterBible)
        statement.bindText(23, entity.worldBible)
        statement.bindText(24, entity.plotPointsHistory)
        statement.bindText(25, entity.memoryState)
        statement.bindText(26, entity.agentLogs)
        statement.bindText(27, entity.marketingMaterials)
        statement.bindText(28, entity.coverPrompt)
        statement.bindLong(29, entity.completionPercentage.toLong())
        statement.bindLong(30, entity.wordsPerMinute.toLong())
        statement.bindText(31, entity.readabilityScore)
        statement.bindText(32, entity.emotionalIntensity)
        statement.bindText(33, entity.pacingHeatmap)
        statement.bindText(34, entity.continuityWarnings)
        statement.bindLong(35, entity.createdAt)
        statement.bindLong(36, entity.lastModifiedAt)
      }
    }
    this.__updateAdapterOfBook = object : EntityDeleteOrUpdateAdapter<Book>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `books` SET `id` = ?,`title` = ?,`genre` = ?,`synopsis` = ?,`outline` = ?,`personaId` = ?,`format` = ?,`targetWordCount` = ?,`narrativeStructure` = ?,`chapterLength` = ?,`isSerialized` = ?,`targetChapters` = ?,`releaseSchedule` = ?,`freeTierChapters` = ?,`wordMin` = ?,`wordMax` = ?,`bufferTarget` = ?,`exportProfile` = ?,`compiledManuscript` = ?,`currentPhase` = ?,`storyBible` = ?,`characterBible` = ?,`worldBible` = ?,`plotPointsHistory` = ?,`memoryState` = ?,`agentLogs` = ?,`marketingMaterials` = ?,`coverPrompt` = ?,`completionPercentage` = ?,`wordsPerMinute` = ?,`readabilityScore` = ?,`emotionalIntensity` = ?,`pacingHeatmap` = ?,`continuityWarnings` = ?,`createdAt` = ?,`lastModifiedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Book) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.genre)
        statement.bindText(4, entity.synopsis)
        statement.bindText(5, entity.outline)
        statement.bindText(6, entity.personaId)
        statement.bindText(7, entity.format)
        statement.bindLong(8, entity.targetWordCount.toLong())
        statement.bindText(9, entity.narrativeStructure)
        statement.bindText(10, entity.chapterLength)
        val _tmp: Int = if (entity.isSerialized) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindLong(12, entity.targetChapters.toLong())
        statement.bindText(13, entity.releaseSchedule)
        statement.bindLong(14, entity.freeTierChapters.toLong())
        statement.bindLong(15, entity.wordMin.toLong())
        statement.bindLong(16, entity.wordMax.toLong())
        statement.bindLong(17, entity.bufferTarget.toLong())
        statement.bindText(18, entity.exportProfile)
        statement.bindText(19, entity.compiledManuscript)
        statement.bindText(20, entity.currentPhase)
        statement.bindText(21, entity.storyBible)
        statement.bindText(22, entity.characterBible)
        statement.bindText(23, entity.worldBible)
        statement.bindText(24, entity.plotPointsHistory)
        statement.bindText(25, entity.memoryState)
        statement.bindText(26, entity.agentLogs)
        statement.bindText(27, entity.marketingMaterials)
        statement.bindText(28, entity.coverPrompt)
        statement.bindLong(29, entity.completionPercentage.toLong())
        statement.bindLong(30, entity.wordsPerMinute.toLong())
        statement.bindText(31, entity.readabilityScore)
        statement.bindText(32, entity.emotionalIntensity)
        statement.bindText(33, entity.pacingHeatmap)
        statement.bindText(34, entity.continuityWarnings)
        statement.bindLong(35, entity.createdAt)
        statement.bindLong(36, entity.lastModifiedAt)
        statement.bindLong(37, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertBook(book: Book): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfBook.insertAndReturnId(_connection, book)
    _result
  }

  public override suspend fun updateBook(book: Book): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfBook.handle(_connection, book)
  }

  public override fun getAllBooks(): Flow<List<Book>> {
    val _sql: String = "SELECT * FROM books ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfGenre: Int = getColumnIndexOrThrow(_stmt, "genre")
        val _columnIndexOfSynopsis: Int = getColumnIndexOrThrow(_stmt, "synopsis")
        val _columnIndexOfOutline: Int = getColumnIndexOrThrow(_stmt, "outline")
        val _columnIndexOfPersonaId: Int = getColumnIndexOrThrow(_stmt, "personaId")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfTargetWordCount: Int = getColumnIndexOrThrow(_stmt, "targetWordCount")
        val _columnIndexOfNarrativeStructure: Int = getColumnIndexOrThrow(_stmt, "narrativeStructure")
        val _columnIndexOfChapterLength: Int = getColumnIndexOrThrow(_stmt, "chapterLength")
        val _columnIndexOfIsSerialized: Int = getColumnIndexOrThrow(_stmt, "isSerialized")
        val _columnIndexOfTargetChapters: Int = getColumnIndexOrThrow(_stmt, "targetChapters")
        val _columnIndexOfReleaseSchedule: Int = getColumnIndexOrThrow(_stmt, "releaseSchedule")
        val _columnIndexOfFreeTierChapters: Int = getColumnIndexOrThrow(_stmt, "freeTierChapters")
        val _columnIndexOfWordMin: Int = getColumnIndexOrThrow(_stmt, "wordMin")
        val _columnIndexOfWordMax: Int = getColumnIndexOrThrow(_stmt, "wordMax")
        val _columnIndexOfBufferTarget: Int = getColumnIndexOrThrow(_stmt, "bufferTarget")
        val _columnIndexOfExportProfile: Int = getColumnIndexOrThrow(_stmt, "exportProfile")
        val _columnIndexOfCompiledManuscript: Int = getColumnIndexOrThrow(_stmt, "compiledManuscript")
        val _columnIndexOfCurrentPhase: Int = getColumnIndexOrThrow(_stmt, "currentPhase")
        val _columnIndexOfStoryBible: Int = getColumnIndexOrThrow(_stmt, "storyBible")
        val _columnIndexOfCharacterBible: Int = getColumnIndexOrThrow(_stmt, "characterBible")
        val _columnIndexOfWorldBible: Int = getColumnIndexOrThrow(_stmt, "worldBible")
        val _columnIndexOfPlotPointsHistory: Int = getColumnIndexOrThrow(_stmt, "plotPointsHistory")
        val _columnIndexOfMemoryState: Int = getColumnIndexOrThrow(_stmt, "memoryState")
        val _columnIndexOfAgentLogs: Int = getColumnIndexOrThrow(_stmt, "agentLogs")
        val _columnIndexOfMarketingMaterials: Int = getColumnIndexOrThrow(_stmt, "marketingMaterials")
        val _columnIndexOfCoverPrompt: Int = getColumnIndexOrThrow(_stmt, "coverPrompt")
        val _columnIndexOfCompletionPercentage: Int = getColumnIndexOrThrow(_stmt, "completionPercentage")
        val _columnIndexOfWordsPerMinute: Int = getColumnIndexOrThrow(_stmt, "wordsPerMinute")
        val _columnIndexOfReadabilityScore: Int = getColumnIndexOrThrow(_stmt, "readabilityScore")
        val _columnIndexOfEmotionalIntensity: Int = getColumnIndexOrThrow(_stmt, "emotionalIntensity")
        val _columnIndexOfPacingHeatmap: Int = getColumnIndexOrThrow(_stmt, "pacingHeatmap")
        val _columnIndexOfContinuityWarnings: Int = getColumnIndexOrThrow(_stmt, "continuityWarnings")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: MutableList<Book> = mutableListOf()
        while (_stmt.step()) {
          val _item: Book
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpGenre: String
          _tmpGenre = _stmt.getText(_columnIndexOfGenre)
          val _tmpSynopsis: String
          _tmpSynopsis = _stmt.getText(_columnIndexOfSynopsis)
          val _tmpOutline: String
          _tmpOutline = _stmt.getText(_columnIndexOfOutline)
          val _tmpPersonaId: String
          _tmpPersonaId = _stmt.getText(_columnIndexOfPersonaId)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpTargetWordCount: Int
          _tmpTargetWordCount = _stmt.getLong(_columnIndexOfTargetWordCount).toInt()
          val _tmpNarrativeStructure: String
          _tmpNarrativeStructure = _stmt.getText(_columnIndexOfNarrativeStructure)
          val _tmpChapterLength: String
          _tmpChapterLength = _stmt.getText(_columnIndexOfChapterLength)
          val _tmpIsSerialized: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSerialized).toInt()
          _tmpIsSerialized = _tmp != 0
          val _tmpTargetChapters: Int
          _tmpTargetChapters = _stmt.getLong(_columnIndexOfTargetChapters).toInt()
          val _tmpReleaseSchedule: String
          _tmpReleaseSchedule = _stmt.getText(_columnIndexOfReleaseSchedule)
          val _tmpFreeTierChapters: Int
          _tmpFreeTierChapters = _stmt.getLong(_columnIndexOfFreeTierChapters).toInt()
          val _tmpWordMin: Int
          _tmpWordMin = _stmt.getLong(_columnIndexOfWordMin).toInt()
          val _tmpWordMax: Int
          _tmpWordMax = _stmt.getLong(_columnIndexOfWordMax).toInt()
          val _tmpBufferTarget: Int
          _tmpBufferTarget = _stmt.getLong(_columnIndexOfBufferTarget).toInt()
          val _tmpExportProfile: String
          _tmpExportProfile = _stmt.getText(_columnIndexOfExportProfile)
          val _tmpCompiledManuscript: String
          _tmpCompiledManuscript = _stmt.getText(_columnIndexOfCompiledManuscript)
          val _tmpCurrentPhase: String
          _tmpCurrentPhase = _stmt.getText(_columnIndexOfCurrentPhase)
          val _tmpStoryBible: String
          _tmpStoryBible = _stmt.getText(_columnIndexOfStoryBible)
          val _tmpCharacterBible: String
          _tmpCharacterBible = _stmt.getText(_columnIndexOfCharacterBible)
          val _tmpWorldBible: String
          _tmpWorldBible = _stmt.getText(_columnIndexOfWorldBible)
          val _tmpPlotPointsHistory: String
          _tmpPlotPointsHistory = _stmt.getText(_columnIndexOfPlotPointsHistory)
          val _tmpMemoryState: String
          _tmpMemoryState = _stmt.getText(_columnIndexOfMemoryState)
          val _tmpAgentLogs: String
          _tmpAgentLogs = _stmt.getText(_columnIndexOfAgentLogs)
          val _tmpMarketingMaterials: String
          _tmpMarketingMaterials = _stmt.getText(_columnIndexOfMarketingMaterials)
          val _tmpCoverPrompt: String
          _tmpCoverPrompt = _stmt.getText(_columnIndexOfCoverPrompt)
          val _tmpCompletionPercentage: Int
          _tmpCompletionPercentage = _stmt.getLong(_columnIndexOfCompletionPercentage).toInt()
          val _tmpWordsPerMinute: Int
          _tmpWordsPerMinute = _stmt.getLong(_columnIndexOfWordsPerMinute).toInt()
          val _tmpReadabilityScore: String
          _tmpReadabilityScore = _stmt.getText(_columnIndexOfReadabilityScore)
          val _tmpEmotionalIntensity: String
          _tmpEmotionalIntensity = _stmt.getText(_columnIndexOfEmotionalIntensity)
          val _tmpPacingHeatmap: String
          _tmpPacingHeatmap = _stmt.getText(_columnIndexOfPacingHeatmap)
          val _tmpContinuityWarnings: String
          _tmpContinuityWarnings = _stmt.getText(_columnIndexOfContinuityWarnings)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _item = Book(_tmpId,_tmpTitle,_tmpGenre,_tmpSynopsis,_tmpOutline,_tmpPersonaId,_tmpFormat,_tmpTargetWordCount,_tmpNarrativeStructure,_tmpChapterLength,_tmpIsSerialized,_tmpTargetChapters,_tmpReleaseSchedule,_tmpFreeTierChapters,_tmpWordMin,_tmpWordMax,_tmpBufferTarget,_tmpExportProfile,_tmpCompiledManuscript,_tmpCurrentPhase,_tmpStoryBible,_tmpCharacterBible,_tmpWorldBible,_tmpPlotPointsHistory,_tmpMemoryState,_tmpAgentLogs,_tmpMarketingMaterials,_tmpCoverPrompt,_tmpCompletionPercentage,_tmpWordsPerMinute,_tmpReadabilityScore,_tmpEmotionalIntensity,_tmpPacingHeatmap,_tmpContinuityWarnings,_tmpCreatedAt,_tmpLastModifiedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBooksSync(): List<Book> {
    val _sql: String = "SELECT * FROM books"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfGenre: Int = getColumnIndexOrThrow(_stmt, "genre")
        val _columnIndexOfSynopsis: Int = getColumnIndexOrThrow(_stmt, "synopsis")
        val _columnIndexOfOutline: Int = getColumnIndexOrThrow(_stmt, "outline")
        val _columnIndexOfPersonaId: Int = getColumnIndexOrThrow(_stmt, "personaId")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfTargetWordCount: Int = getColumnIndexOrThrow(_stmt, "targetWordCount")
        val _columnIndexOfNarrativeStructure: Int = getColumnIndexOrThrow(_stmt, "narrativeStructure")
        val _columnIndexOfChapterLength: Int = getColumnIndexOrThrow(_stmt, "chapterLength")
        val _columnIndexOfIsSerialized: Int = getColumnIndexOrThrow(_stmt, "isSerialized")
        val _columnIndexOfTargetChapters: Int = getColumnIndexOrThrow(_stmt, "targetChapters")
        val _columnIndexOfReleaseSchedule: Int = getColumnIndexOrThrow(_stmt, "releaseSchedule")
        val _columnIndexOfFreeTierChapters: Int = getColumnIndexOrThrow(_stmt, "freeTierChapters")
        val _columnIndexOfWordMin: Int = getColumnIndexOrThrow(_stmt, "wordMin")
        val _columnIndexOfWordMax: Int = getColumnIndexOrThrow(_stmt, "wordMax")
        val _columnIndexOfBufferTarget: Int = getColumnIndexOrThrow(_stmt, "bufferTarget")
        val _columnIndexOfExportProfile: Int = getColumnIndexOrThrow(_stmt, "exportProfile")
        val _columnIndexOfCompiledManuscript: Int = getColumnIndexOrThrow(_stmt, "compiledManuscript")
        val _columnIndexOfCurrentPhase: Int = getColumnIndexOrThrow(_stmt, "currentPhase")
        val _columnIndexOfStoryBible: Int = getColumnIndexOrThrow(_stmt, "storyBible")
        val _columnIndexOfCharacterBible: Int = getColumnIndexOrThrow(_stmt, "characterBible")
        val _columnIndexOfWorldBible: Int = getColumnIndexOrThrow(_stmt, "worldBible")
        val _columnIndexOfPlotPointsHistory: Int = getColumnIndexOrThrow(_stmt, "plotPointsHistory")
        val _columnIndexOfMemoryState: Int = getColumnIndexOrThrow(_stmt, "memoryState")
        val _columnIndexOfAgentLogs: Int = getColumnIndexOrThrow(_stmt, "agentLogs")
        val _columnIndexOfMarketingMaterials: Int = getColumnIndexOrThrow(_stmt, "marketingMaterials")
        val _columnIndexOfCoverPrompt: Int = getColumnIndexOrThrow(_stmt, "coverPrompt")
        val _columnIndexOfCompletionPercentage: Int = getColumnIndexOrThrow(_stmt, "completionPercentage")
        val _columnIndexOfWordsPerMinute: Int = getColumnIndexOrThrow(_stmt, "wordsPerMinute")
        val _columnIndexOfReadabilityScore: Int = getColumnIndexOrThrow(_stmt, "readabilityScore")
        val _columnIndexOfEmotionalIntensity: Int = getColumnIndexOrThrow(_stmt, "emotionalIntensity")
        val _columnIndexOfPacingHeatmap: Int = getColumnIndexOrThrow(_stmt, "pacingHeatmap")
        val _columnIndexOfContinuityWarnings: Int = getColumnIndexOrThrow(_stmt, "continuityWarnings")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: MutableList<Book> = mutableListOf()
        while (_stmt.step()) {
          val _item: Book
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpGenre: String
          _tmpGenre = _stmt.getText(_columnIndexOfGenre)
          val _tmpSynopsis: String
          _tmpSynopsis = _stmt.getText(_columnIndexOfSynopsis)
          val _tmpOutline: String
          _tmpOutline = _stmt.getText(_columnIndexOfOutline)
          val _tmpPersonaId: String
          _tmpPersonaId = _stmt.getText(_columnIndexOfPersonaId)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpTargetWordCount: Int
          _tmpTargetWordCount = _stmt.getLong(_columnIndexOfTargetWordCount).toInt()
          val _tmpNarrativeStructure: String
          _tmpNarrativeStructure = _stmt.getText(_columnIndexOfNarrativeStructure)
          val _tmpChapterLength: String
          _tmpChapterLength = _stmt.getText(_columnIndexOfChapterLength)
          val _tmpIsSerialized: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSerialized).toInt()
          _tmpIsSerialized = _tmp != 0
          val _tmpTargetChapters: Int
          _tmpTargetChapters = _stmt.getLong(_columnIndexOfTargetChapters).toInt()
          val _tmpReleaseSchedule: String
          _tmpReleaseSchedule = _stmt.getText(_columnIndexOfReleaseSchedule)
          val _tmpFreeTierChapters: Int
          _tmpFreeTierChapters = _stmt.getLong(_columnIndexOfFreeTierChapters).toInt()
          val _tmpWordMin: Int
          _tmpWordMin = _stmt.getLong(_columnIndexOfWordMin).toInt()
          val _tmpWordMax: Int
          _tmpWordMax = _stmt.getLong(_columnIndexOfWordMax).toInt()
          val _tmpBufferTarget: Int
          _tmpBufferTarget = _stmt.getLong(_columnIndexOfBufferTarget).toInt()
          val _tmpExportProfile: String
          _tmpExportProfile = _stmt.getText(_columnIndexOfExportProfile)
          val _tmpCompiledManuscript: String
          _tmpCompiledManuscript = _stmt.getText(_columnIndexOfCompiledManuscript)
          val _tmpCurrentPhase: String
          _tmpCurrentPhase = _stmt.getText(_columnIndexOfCurrentPhase)
          val _tmpStoryBible: String
          _tmpStoryBible = _stmt.getText(_columnIndexOfStoryBible)
          val _tmpCharacterBible: String
          _tmpCharacterBible = _stmt.getText(_columnIndexOfCharacterBible)
          val _tmpWorldBible: String
          _tmpWorldBible = _stmt.getText(_columnIndexOfWorldBible)
          val _tmpPlotPointsHistory: String
          _tmpPlotPointsHistory = _stmt.getText(_columnIndexOfPlotPointsHistory)
          val _tmpMemoryState: String
          _tmpMemoryState = _stmt.getText(_columnIndexOfMemoryState)
          val _tmpAgentLogs: String
          _tmpAgentLogs = _stmt.getText(_columnIndexOfAgentLogs)
          val _tmpMarketingMaterials: String
          _tmpMarketingMaterials = _stmt.getText(_columnIndexOfMarketingMaterials)
          val _tmpCoverPrompt: String
          _tmpCoverPrompt = _stmt.getText(_columnIndexOfCoverPrompt)
          val _tmpCompletionPercentage: Int
          _tmpCompletionPercentage = _stmt.getLong(_columnIndexOfCompletionPercentage).toInt()
          val _tmpWordsPerMinute: Int
          _tmpWordsPerMinute = _stmt.getLong(_columnIndexOfWordsPerMinute).toInt()
          val _tmpReadabilityScore: String
          _tmpReadabilityScore = _stmt.getText(_columnIndexOfReadabilityScore)
          val _tmpEmotionalIntensity: String
          _tmpEmotionalIntensity = _stmt.getText(_columnIndexOfEmotionalIntensity)
          val _tmpPacingHeatmap: String
          _tmpPacingHeatmap = _stmt.getText(_columnIndexOfPacingHeatmap)
          val _tmpContinuityWarnings: String
          _tmpContinuityWarnings = _stmt.getText(_columnIndexOfContinuityWarnings)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _item = Book(_tmpId,_tmpTitle,_tmpGenre,_tmpSynopsis,_tmpOutline,_tmpPersonaId,_tmpFormat,_tmpTargetWordCount,_tmpNarrativeStructure,_tmpChapterLength,_tmpIsSerialized,_tmpTargetChapters,_tmpReleaseSchedule,_tmpFreeTierChapters,_tmpWordMin,_tmpWordMax,_tmpBufferTarget,_tmpExportProfile,_tmpCompiledManuscript,_tmpCurrentPhase,_tmpStoryBible,_tmpCharacterBible,_tmpWorldBible,_tmpPlotPointsHistory,_tmpMemoryState,_tmpAgentLogs,_tmpMarketingMaterials,_tmpCoverPrompt,_tmpCompletionPercentage,_tmpWordsPerMinute,_tmpReadabilityScore,_tmpEmotionalIntensity,_tmpPacingHeatmap,_tmpContinuityWarnings,_tmpCreatedAt,_tmpLastModifiedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBookById(bookId: Int): Book? {
    val _sql: String = "SELECT * FROM books WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfGenre: Int = getColumnIndexOrThrow(_stmt, "genre")
        val _columnIndexOfSynopsis: Int = getColumnIndexOrThrow(_stmt, "synopsis")
        val _columnIndexOfOutline: Int = getColumnIndexOrThrow(_stmt, "outline")
        val _columnIndexOfPersonaId: Int = getColumnIndexOrThrow(_stmt, "personaId")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfTargetWordCount: Int = getColumnIndexOrThrow(_stmt, "targetWordCount")
        val _columnIndexOfNarrativeStructure: Int = getColumnIndexOrThrow(_stmt, "narrativeStructure")
        val _columnIndexOfChapterLength: Int = getColumnIndexOrThrow(_stmt, "chapterLength")
        val _columnIndexOfIsSerialized: Int = getColumnIndexOrThrow(_stmt, "isSerialized")
        val _columnIndexOfTargetChapters: Int = getColumnIndexOrThrow(_stmt, "targetChapters")
        val _columnIndexOfReleaseSchedule: Int = getColumnIndexOrThrow(_stmt, "releaseSchedule")
        val _columnIndexOfFreeTierChapters: Int = getColumnIndexOrThrow(_stmt, "freeTierChapters")
        val _columnIndexOfWordMin: Int = getColumnIndexOrThrow(_stmt, "wordMin")
        val _columnIndexOfWordMax: Int = getColumnIndexOrThrow(_stmt, "wordMax")
        val _columnIndexOfBufferTarget: Int = getColumnIndexOrThrow(_stmt, "bufferTarget")
        val _columnIndexOfExportProfile: Int = getColumnIndexOrThrow(_stmt, "exportProfile")
        val _columnIndexOfCompiledManuscript: Int = getColumnIndexOrThrow(_stmt, "compiledManuscript")
        val _columnIndexOfCurrentPhase: Int = getColumnIndexOrThrow(_stmt, "currentPhase")
        val _columnIndexOfStoryBible: Int = getColumnIndexOrThrow(_stmt, "storyBible")
        val _columnIndexOfCharacterBible: Int = getColumnIndexOrThrow(_stmt, "characterBible")
        val _columnIndexOfWorldBible: Int = getColumnIndexOrThrow(_stmt, "worldBible")
        val _columnIndexOfPlotPointsHistory: Int = getColumnIndexOrThrow(_stmt, "plotPointsHistory")
        val _columnIndexOfMemoryState: Int = getColumnIndexOrThrow(_stmt, "memoryState")
        val _columnIndexOfAgentLogs: Int = getColumnIndexOrThrow(_stmt, "agentLogs")
        val _columnIndexOfMarketingMaterials: Int = getColumnIndexOrThrow(_stmt, "marketingMaterials")
        val _columnIndexOfCoverPrompt: Int = getColumnIndexOrThrow(_stmt, "coverPrompt")
        val _columnIndexOfCompletionPercentage: Int = getColumnIndexOrThrow(_stmt, "completionPercentage")
        val _columnIndexOfWordsPerMinute: Int = getColumnIndexOrThrow(_stmt, "wordsPerMinute")
        val _columnIndexOfReadabilityScore: Int = getColumnIndexOrThrow(_stmt, "readabilityScore")
        val _columnIndexOfEmotionalIntensity: Int = getColumnIndexOrThrow(_stmt, "emotionalIntensity")
        val _columnIndexOfPacingHeatmap: Int = getColumnIndexOrThrow(_stmt, "pacingHeatmap")
        val _columnIndexOfContinuityWarnings: Int = getColumnIndexOrThrow(_stmt, "continuityWarnings")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: Book?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpGenre: String
          _tmpGenre = _stmt.getText(_columnIndexOfGenre)
          val _tmpSynopsis: String
          _tmpSynopsis = _stmt.getText(_columnIndexOfSynopsis)
          val _tmpOutline: String
          _tmpOutline = _stmt.getText(_columnIndexOfOutline)
          val _tmpPersonaId: String
          _tmpPersonaId = _stmt.getText(_columnIndexOfPersonaId)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpTargetWordCount: Int
          _tmpTargetWordCount = _stmt.getLong(_columnIndexOfTargetWordCount).toInt()
          val _tmpNarrativeStructure: String
          _tmpNarrativeStructure = _stmt.getText(_columnIndexOfNarrativeStructure)
          val _tmpChapterLength: String
          _tmpChapterLength = _stmt.getText(_columnIndexOfChapterLength)
          val _tmpIsSerialized: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSerialized).toInt()
          _tmpIsSerialized = _tmp != 0
          val _tmpTargetChapters: Int
          _tmpTargetChapters = _stmt.getLong(_columnIndexOfTargetChapters).toInt()
          val _tmpReleaseSchedule: String
          _tmpReleaseSchedule = _stmt.getText(_columnIndexOfReleaseSchedule)
          val _tmpFreeTierChapters: Int
          _tmpFreeTierChapters = _stmt.getLong(_columnIndexOfFreeTierChapters).toInt()
          val _tmpWordMin: Int
          _tmpWordMin = _stmt.getLong(_columnIndexOfWordMin).toInt()
          val _tmpWordMax: Int
          _tmpWordMax = _stmt.getLong(_columnIndexOfWordMax).toInt()
          val _tmpBufferTarget: Int
          _tmpBufferTarget = _stmt.getLong(_columnIndexOfBufferTarget).toInt()
          val _tmpExportProfile: String
          _tmpExportProfile = _stmt.getText(_columnIndexOfExportProfile)
          val _tmpCompiledManuscript: String
          _tmpCompiledManuscript = _stmt.getText(_columnIndexOfCompiledManuscript)
          val _tmpCurrentPhase: String
          _tmpCurrentPhase = _stmt.getText(_columnIndexOfCurrentPhase)
          val _tmpStoryBible: String
          _tmpStoryBible = _stmt.getText(_columnIndexOfStoryBible)
          val _tmpCharacterBible: String
          _tmpCharacterBible = _stmt.getText(_columnIndexOfCharacterBible)
          val _tmpWorldBible: String
          _tmpWorldBible = _stmt.getText(_columnIndexOfWorldBible)
          val _tmpPlotPointsHistory: String
          _tmpPlotPointsHistory = _stmt.getText(_columnIndexOfPlotPointsHistory)
          val _tmpMemoryState: String
          _tmpMemoryState = _stmt.getText(_columnIndexOfMemoryState)
          val _tmpAgentLogs: String
          _tmpAgentLogs = _stmt.getText(_columnIndexOfAgentLogs)
          val _tmpMarketingMaterials: String
          _tmpMarketingMaterials = _stmt.getText(_columnIndexOfMarketingMaterials)
          val _tmpCoverPrompt: String
          _tmpCoverPrompt = _stmt.getText(_columnIndexOfCoverPrompt)
          val _tmpCompletionPercentage: Int
          _tmpCompletionPercentage = _stmt.getLong(_columnIndexOfCompletionPercentage).toInt()
          val _tmpWordsPerMinute: Int
          _tmpWordsPerMinute = _stmt.getLong(_columnIndexOfWordsPerMinute).toInt()
          val _tmpReadabilityScore: String
          _tmpReadabilityScore = _stmt.getText(_columnIndexOfReadabilityScore)
          val _tmpEmotionalIntensity: String
          _tmpEmotionalIntensity = _stmt.getText(_columnIndexOfEmotionalIntensity)
          val _tmpPacingHeatmap: String
          _tmpPacingHeatmap = _stmt.getText(_columnIndexOfPacingHeatmap)
          val _tmpContinuityWarnings: String
          _tmpContinuityWarnings = _stmt.getText(_columnIndexOfContinuityWarnings)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _result = Book(_tmpId,_tmpTitle,_tmpGenre,_tmpSynopsis,_tmpOutline,_tmpPersonaId,_tmpFormat,_tmpTargetWordCount,_tmpNarrativeStructure,_tmpChapterLength,_tmpIsSerialized,_tmpTargetChapters,_tmpReleaseSchedule,_tmpFreeTierChapters,_tmpWordMin,_tmpWordMax,_tmpBufferTarget,_tmpExportProfile,_tmpCompiledManuscript,_tmpCurrentPhase,_tmpStoryBible,_tmpCharacterBible,_tmpWorldBible,_tmpPlotPointsHistory,_tmpMemoryState,_tmpAgentLogs,_tmpMarketingMaterials,_tmpCoverPrompt,_tmpCompletionPercentage,_tmpWordsPerMinute,_tmpReadabilityScore,_tmpEmotionalIntensity,_tmpPacingHeatmap,_tmpContinuityWarnings,_tmpCreatedAt,_tmpLastModifiedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getBookByIdFlow(bookId: Int): Flow<Book> {
    val _sql: String = "SELECT * FROM books WHERE id = ?"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfGenre: Int = getColumnIndexOrThrow(_stmt, "genre")
        val _columnIndexOfSynopsis: Int = getColumnIndexOrThrow(_stmt, "synopsis")
        val _columnIndexOfOutline: Int = getColumnIndexOrThrow(_stmt, "outline")
        val _columnIndexOfPersonaId: Int = getColumnIndexOrThrow(_stmt, "personaId")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfTargetWordCount: Int = getColumnIndexOrThrow(_stmt, "targetWordCount")
        val _columnIndexOfNarrativeStructure: Int = getColumnIndexOrThrow(_stmt, "narrativeStructure")
        val _columnIndexOfChapterLength: Int = getColumnIndexOrThrow(_stmt, "chapterLength")
        val _columnIndexOfIsSerialized: Int = getColumnIndexOrThrow(_stmt, "isSerialized")
        val _columnIndexOfTargetChapters: Int = getColumnIndexOrThrow(_stmt, "targetChapters")
        val _columnIndexOfReleaseSchedule: Int = getColumnIndexOrThrow(_stmt, "releaseSchedule")
        val _columnIndexOfFreeTierChapters: Int = getColumnIndexOrThrow(_stmt, "freeTierChapters")
        val _columnIndexOfWordMin: Int = getColumnIndexOrThrow(_stmt, "wordMin")
        val _columnIndexOfWordMax: Int = getColumnIndexOrThrow(_stmt, "wordMax")
        val _columnIndexOfBufferTarget: Int = getColumnIndexOrThrow(_stmt, "bufferTarget")
        val _columnIndexOfExportProfile: Int = getColumnIndexOrThrow(_stmt, "exportProfile")
        val _columnIndexOfCompiledManuscript: Int = getColumnIndexOrThrow(_stmt, "compiledManuscript")
        val _columnIndexOfCurrentPhase: Int = getColumnIndexOrThrow(_stmt, "currentPhase")
        val _columnIndexOfStoryBible: Int = getColumnIndexOrThrow(_stmt, "storyBible")
        val _columnIndexOfCharacterBible: Int = getColumnIndexOrThrow(_stmt, "characterBible")
        val _columnIndexOfWorldBible: Int = getColumnIndexOrThrow(_stmt, "worldBible")
        val _columnIndexOfPlotPointsHistory: Int = getColumnIndexOrThrow(_stmt, "plotPointsHistory")
        val _columnIndexOfMemoryState: Int = getColumnIndexOrThrow(_stmt, "memoryState")
        val _columnIndexOfAgentLogs: Int = getColumnIndexOrThrow(_stmt, "agentLogs")
        val _columnIndexOfMarketingMaterials: Int = getColumnIndexOrThrow(_stmt, "marketingMaterials")
        val _columnIndexOfCoverPrompt: Int = getColumnIndexOrThrow(_stmt, "coverPrompt")
        val _columnIndexOfCompletionPercentage: Int = getColumnIndexOrThrow(_stmt, "completionPercentage")
        val _columnIndexOfWordsPerMinute: Int = getColumnIndexOrThrow(_stmt, "wordsPerMinute")
        val _columnIndexOfReadabilityScore: Int = getColumnIndexOrThrow(_stmt, "readabilityScore")
        val _columnIndexOfEmotionalIntensity: Int = getColumnIndexOrThrow(_stmt, "emotionalIntensity")
        val _columnIndexOfPacingHeatmap: Int = getColumnIndexOrThrow(_stmt, "pacingHeatmap")
        val _columnIndexOfContinuityWarnings: Int = getColumnIndexOrThrow(_stmt, "continuityWarnings")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLastModifiedAt: Int = getColumnIndexOrThrow(_stmt, "lastModifiedAt")
        val _result: Book
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpGenre: String
          _tmpGenre = _stmt.getText(_columnIndexOfGenre)
          val _tmpSynopsis: String
          _tmpSynopsis = _stmt.getText(_columnIndexOfSynopsis)
          val _tmpOutline: String
          _tmpOutline = _stmt.getText(_columnIndexOfOutline)
          val _tmpPersonaId: String
          _tmpPersonaId = _stmt.getText(_columnIndexOfPersonaId)
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpTargetWordCount: Int
          _tmpTargetWordCount = _stmt.getLong(_columnIndexOfTargetWordCount).toInt()
          val _tmpNarrativeStructure: String
          _tmpNarrativeStructure = _stmt.getText(_columnIndexOfNarrativeStructure)
          val _tmpChapterLength: String
          _tmpChapterLength = _stmt.getText(_columnIndexOfChapterLength)
          val _tmpIsSerialized: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSerialized).toInt()
          _tmpIsSerialized = _tmp != 0
          val _tmpTargetChapters: Int
          _tmpTargetChapters = _stmt.getLong(_columnIndexOfTargetChapters).toInt()
          val _tmpReleaseSchedule: String
          _tmpReleaseSchedule = _stmt.getText(_columnIndexOfReleaseSchedule)
          val _tmpFreeTierChapters: Int
          _tmpFreeTierChapters = _stmt.getLong(_columnIndexOfFreeTierChapters).toInt()
          val _tmpWordMin: Int
          _tmpWordMin = _stmt.getLong(_columnIndexOfWordMin).toInt()
          val _tmpWordMax: Int
          _tmpWordMax = _stmt.getLong(_columnIndexOfWordMax).toInt()
          val _tmpBufferTarget: Int
          _tmpBufferTarget = _stmt.getLong(_columnIndexOfBufferTarget).toInt()
          val _tmpExportProfile: String
          _tmpExportProfile = _stmt.getText(_columnIndexOfExportProfile)
          val _tmpCompiledManuscript: String
          _tmpCompiledManuscript = _stmt.getText(_columnIndexOfCompiledManuscript)
          val _tmpCurrentPhase: String
          _tmpCurrentPhase = _stmt.getText(_columnIndexOfCurrentPhase)
          val _tmpStoryBible: String
          _tmpStoryBible = _stmt.getText(_columnIndexOfStoryBible)
          val _tmpCharacterBible: String
          _tmpCharacterBible = _stmt.getText(_columnIndexOfCharacterBible)
          val _tmpWorldBible: String
          _tmpWorldBible = _stmt.getText(_columnIndexOfWorldBible)
          val _tmpPlotPointsHistory: String
          _tmpPlotPointsHistory = _stmt.getText(_columnIndexOfPlotPointsHistory)
          val _tmpMemoryState: String
          _tmpMemoryState = _stmt.getText(_columnIndexOfMemoryState)
          val _tmpAgentLogs: String
          _tmpAgentLogs = _stmt.getText(_columnIndexOfAgentLogs)
          val _tmpMarketingMaterials: String
          _tmpMarketingMaterials = _stmt.getText(_columnIndexOfMarketingMaterials)
          val _tmpCoverPrompt: String
          _tmpCoverPrompt = _stmt.getText(_columnIndexOfCoverPrompt)
          val _tmpCompletionPercentage: Int
          _tmpCompletionPercentage = _stmt.getLong(_columnIndexOfCompletionPercentage).toInt()
          val _tmpWordsPerMinute: Int
          _tmpWordsPerMinute = _stmt.getLong(_columnIndexOfWordsPerMinute).toInt()
          val _tmpReadabilityScore: String
          _tmpReadabilityScore = _stmt.getText(_columnIndexOfReadabilityScore)
          val _tmpEmotionalIntensity: String
          _tmpEmotionalIntensity = _stmt.getText(_columnIndexOfEmotionalIntensity)
          val _tmpPacingHeatmap: String
          _tmpPacingHeatmap = _stmt.getText(_columnIndexOfPacingHeatmap)
          val _tmpContinuityWarnings: String
          _tmpContinuityWarnings = _stmt.getText(_columnIndexOfContinuityWarnings)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLastModifiedAt: Long
          _tmpLastModifiedAt = _stmt.getLong(_columnIndexOfLastModifiedAt)
          _result = Book(_tmpId,_tmpTitle,_tmpGenre,_tmpSynopsis,_tmpOutline,_tmpPersonaId,_tmpFormat,_tmpTargetWordCount,_tmpNarrativeStructure,_tmpChapterLength,_tmpIsSerialized,_tmpTargetChapters,_tmpReleaseSchedule,_tmpFreeTierChapters,_tmpWordMin,_tmpWordMax,_tmpBufferTarget,_tmpExportProfile,_tmpCompiledManuscript,_tmpCurrentPhase,_tmpStoryBible,_tmpCharacterBible,_tmpWorldBible,_tmpPlotPointsHistory,_tmpMemoryState,_tmpAgentLogs,_tmpMarketingMaterials,_tmpCoverPrompt,_tmpCompletionPercentage,_tmpWordsPerMinute,_tmpReadabilityScore,_tmpEmotionalIntensity,_tmpPacingHeatmap,_tmpContinuityWarnings,_tmpCreatedAt,_tmpLastModifiedAt)
        } else {
          error("The query result was empty, but expected a single row to return a NON-NULL object of type 'com.example.`data`.model.Book'.")
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteBook(bookId: Int) {
    val _sql: String = "DELETE FROM books WHERE id = ?"
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
