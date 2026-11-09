package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.data.repository.AppRepository
import com.example.engine.WriterGate
import com.example.engine.serialization.BufferCadence
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.MicroArcPlanner
import com.example.engine.serialization.SerialExportProfiles
import com.example.engine.serialization.SerialValidatorSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The serial dashboard state machine (v3.0 P3, plan §6.10 / §6.11).
 *
 * Hosts the publication-side tooling of the serial format: the buffer meter,
 * the cadence tracker, the arc board (micro-arc planner), the overdue
 * question widget, the validator-suite report, the pre-draft worksheet and
 * scorecard editors for the selected chapter, and the paste-ready platform
 * export (§6.13: the application formats, the writer pastes). Deliberately
 * Context-free: every computation runs on engine code and is unit-testable
 * on the JVM; only the export write touches the file system, through the
 * injected [exportDir] (null in tests).
 */
class SerialDashboardViewModel(
    private val repository: AppRepository,
    /** Directory the paste-ready packages are written to; null disables persistence (tests). */
    private val exportDir: java.io.File? = null,
    private val scope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Int?>(null)
    val selectedBookId: StateFlow<Int?> = _selectedBookId.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _arcs = MutableStateFlow<List<SerialArc>>(emptyList())
    val arcs: StateFlow<List<SerialArc>> = _arcs.asStateFlow()

    private val _questions = MutableStateFlow<List<QuestionEntry>>(emptyList())
    val questions: StateFlow<List<QuestionEntry>> = _questions.asStateFlow()

    private val _report = MutableStateFlow<SerialValidatorSuite.Report?>(null)
    val report: StateFlow<SerialValidatorSuite.Report?> = _report.asStateFlow()

    private val _buffer = MutableStateFlow<BufferCadence.BufferReport?>(null)
    val buffer: StateFlow<BufferCadence.BufferReport?> = _buffer.asStateFlow()

    private val _cadence = MutableStateFlow<BufferCadence.CadenceReport?>(null)
    val cadence: StateFlow<BufferCadence.CadenceReport?> = _cadence.asStateFlow()

    private val _selectedChapterId = MutableStateFlow<Int?>(null)
    val selectedChapterId: StateFlow<Int?> = _selectedChapterId.asStateFlow()

    private val _status = MutableStateFlow("Serial dashboard ready.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    private val _exportProfileId = MutableStateFlow("royal_road")
    val exportProfileId: StateFlow<String> = _exportProfileId.asStateFlow()

    val selectedBook: Book? get() = _books.value.firstOrNull { it.id == _selectedBookId.value }
    val selectedChapter: Chapter? get() = _chapters.value.firstOrNull { it.id == _selectedChapterId.value }

    /** Overdue progressive questions for the dashboard widget (§6.7). */
    fun overdueQuestions(): List<QuestionEntry> {
        val current = _chapters.value.filter { WriterGate.isCanon(it) }.maxOfOrNull { it.chapterNumber } ?: 0
        return _questions.value.filter { com.example.engine.QuestionLedger.isOverdue(it, current) }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    fun refreshBooks() {
        scope.launch {
            _books.value = repository.getBooksSync().filter { it.isSerialized }
            if (_selectedBookId.value == null) _books.value.firstOrNull()?.id?.let { selectBook(it) }
        }
    }

    fun selectBook(bookId: Int) {
        _selectedBookId.value = bookId
        _selectedChapterId.value = null
        scope.launch {
            val book = repository.getBookById(bookId) ?: return@launch
            _chapters.value = repository.getChaptersForBookSync(bookId)
            _arcs.value = repository.getArcsForBookSync(bookId)
            _questions.value = repository.getQuestionsForBookSync(bookId)
            _exportProfileId.value = book.exportProfile
            recompute()
            _status.value = "Loaded \"${book.title}\" — ${_chapters.value.size} chapters, ${_arcs.value.size} arc(s)."
        }
    }

    fun selectChapter(chapterId: Int?) {
        _selectedChapterId.value = chapterId
    }

    /** Recomputes every derived report after a data change. */
    fun recompute() {
        val book = selectedBook ?: return
        _buffer.value = BufferCadence.buffer(book, _chapters.value)
        _cadence.value = BufferCadence.cadence(book, _chapters.value)
        _report.value = SerialValidatorSuite.run(SerialValidatorSuite.Input(
            book = book,
            chapters = _chapters.value,
            arcs = _arcs.value,
            questions = _questions.value,
            dismissals = _dismissed.value
        ))
    }

    // ------------------------------------------------------------------
    // Micro-arc planner actions (§6.5)
    // ------------------------------------------------------------------

    /** Lays the default arc plan across the book's targetChapters. */
    fun planArcs() {
        val book = selectedBook ?: return
        scope.launch {
            val existing = repository.getArcsForBookSync(book.id)
            if (existing.isNotEmpty()) {
                _status.value = "Arc plan already exists (${existing.size} arc(s)) — edit it on the board."
                return@launch
            }
            val arcs = MicroArcPlanner.planArcs(book)
            repository.insertArcs(arcs)
            _arcs.value = repository.getArcsForBookSync(book.id)
            recompute()
            _status.value = "Laid ${arcs.size} micro-arc(s) across ${book.targetChapters} chapters — edit conflicts, questions and contributions on the board."
        }
    }

    fun updateArc(arc: SerialArc) {
        scope.launch {
            repository.updateArc(arc)
            _arcs.value = repository.getArcsForBookSync(arc.bookId)
            recompute()
        }
    }

    // ------------------------------------------------------------------
    // Worksheet + scorecard (§6.8, §6.9)
    // ------------------------------------------------------------------

    fun saveWorksheet(chapter: Chapter, worksheet: com.example.engine.serialization.PreDraftWorksheet.Worksheet) {
        scope.launch {
            val updated = chapter.copy(worksheet = worksheet.toJson())
            repository.updateChapter(updated)
            _chapters.value = _chapters.value.map { if (it.id == updated.id) updated else it }
            recompute()
            _status.value = "Worksheet saved for chapter ${updated.chapterNumber} (${if (worksheet.complete) "complete" else "incomplete"})."
        }
    }

    /** The writer sets the final scores (L6 proposes; the writer always decides). */
    fun saveScorecard(chapter: Chapter, scores: List<Int>, rationales: List<String>) {
        if (scores.size != 7 || scores.any { it < 0 || it > 2 }) {
            _status.value = "Scorecard refused: seven criteria, each 0-2."
            return
        }
        scope.launch {
            val scored = com.example.engine.serialization.QcScorecard.Scored(scores, rationales)
            val updated = chapter.copy(
                qcScores = com.example.engine.serialization.QcScorecard.toJson(scored),
                qcTotal = scored.total
            )
            repository.updateChapter(updated)
            _chapters.value = _chapters.value.map { if (it.id == updated.id) updated else it }
            recompute()
            _status.value = "Scorecard ${scored.total}/14 saved for chapter ${updated.chapterNumber}."
        }
    }

    // ------------------------------------------------------------------
    // Release actions (§6.10)
    // ------------------------------------------------------------------

    /** Marks a canon chapter released today against the release schedule. */
    fun releaseChapter(chapterId: Int) {
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: return
        if (!WriterGate.isCanon(chapter)) {
            _status.value = "Release refused: chapter ${chapter.chapterNumber} is not canon (confirm it in the Writer Studio first)."
            return
        }
        scope.launch {
            val updated = chapter.copy(status = BufferCadence.releaseMarker(java.time.LocalDate.now()))
            repository.updateChapter(updated)
            _chapters.value = _chapters.value.map { if (it.id == updated.id) updated else it }
            recompute()
            _status.value = "Chapter ${updated.chapterNumber} marked released — cadence and buffer recomputed."
        }
    }

    fun dismissCheck(key: String) {
        _dismissed.value = _dismissed.value + key
        recompute()
        _status.value = "Warning dismissed with the dashboard's note — dismissed warnings stay disclosed in the report."
    }

    // ------------------------------------------------------------------
    // Platform export (§6.13)
    // ------------------------------------------------------------------

    fun setExportProfile(id: String) {
        _exportProfileId.value = id
        scope.launch {
            selectedBook?.let { repository.updateBook(it.copy(exportProfile = id)) }
        }
    }

    /**
     * Builds the paste-ready package for the selected chapter and writes it
     * to the export directory (the clipboard copy is done by the screen —
     * the ViewModel stays Context-free).
     */
    fun exportChapter(onPackage: (SerialExportProfiles.Package) -> Unit) {
        val book = selectedBook ?: return
        val chapter = selectedChapter ?: run { _status.value = "Select a chapter to export."; return }
        if (!WriterGate.isCanon(chapter)) {
            _status.value = "Export refused: chapter ${chapter.chapterNumber} is not canon."
            return
        }
        val pkg = SerialExportProfiles.buildPackage(
            book = book,
            chapter = chapter,
            profile = SerialExportProfiles.byId(_exportProfileId.value)
        )
        exportDir?.let { dir ->
            runCatching {
                val safe = book.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
                val out = java.io.File(java.io.File(dir, "serial-exports/$safe"), "ch${chapter.chapterNumber}-${pkg.profileId}.txt")
                out.parentFile?.mkdirs()
                out.writeText(pkg.clipboardBundle)
                _status.value = "Exported chapter ${chapter.chapterNumber} (${pkg.platform}) — ${out.absolutePath}"
            }.onFailure { _status.value = "Export write failed: ${it.message}" }
        }
        onPackage(pkg)
    }

    /**
     * The §6.9 export pre-flight: the ten final drafting principles plus the
     * four ultimate rules, quoted verbatim in the export dialog.
     */
    val preFlightChecklist: List<String>
        get() = com.example.engine.serialization.QcScorecard.TEN_FINAL_PRINCIPLES +
            com.example.engine.serialization.QcScorecard.FOUR_ULTIMATE_RULES

    override fun onCleared() {
        super.onCleared()
    }
}
