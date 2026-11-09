package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.QuestionEntry
import com.example.data.repository.AppRepository
import com.example.engine.AnnotationKind
import com.example.engine.AstAnnotation
import com.example.engine.ChekhovLedger
import com.example.engine.DraftArchive
import com.example.engine.DraftState
import com.example.engine.LiveAstAnnotator
import com.example.engine.ManuscriptAst
import com.example.engine.QuestionLedger
import com.example.engine.SixLayerPipeline
import com.example.engine.WriterGate
import com.example.engine.serialization.BalanceMeter
import com.example.engine.serialization.OpeningValidators
import com.example.engine.serialization.SerialValidatorSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Writer Studio state machine (v3.0, plan §4.1).
 *
 * Hosts the PROPOSED / CONFIRMED / ARCHIVED chapter workflow: the L4 draft
 * lands as PROPOSED, the writer edits freely, and confirming freezes the
 * text, sets writer_confirmed = 1, and triggers L6 annotation + ledger
 * updates on the frozen text. Regeneration archives the previous draft into
 * the .authr container for diffing; ARCHIVED drafts never feed downstream.
 *
 * Deliberately Context-free: the [L4DraftGenerator] is injected, so the
 * whole confirm flow is unit-testable on the JVM (the P1 exit gate runs as
 * a seeded-project assertion suite against this class).
 */
class WriterStudioViewModel(
    private val repository: AppRepository,
    /** Container root for draft archives; null disables persistence (tests). */
    private val authrRoot: File? = null,
    /** L4 draft producer; the production implementation calls the model. */
    private val draftGenerator: L4DraftGenerator = L4DraftGenerator.NONE,
    /** Injected scope keeps JVM tests synchronous-friendly. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    /** Produces an L4 prose draft from confirmed material only (plan §4). */
    fun interface L4DraftGenerator {
        suspend fun generate(
            book: Book,
            chapter: Chapter,
            canonContext: List<Chapter>,
            lorebook: List<Lorebook>,
            instruction: String
        ): String

        companion object {
            val NONE = L4DraftGenerator { _, _, _, _, _ -> "" }
        }
    }

    enum class StudioMode { INTERACTIVE, AUTONOMOUS }

    data class GateReport(
        val canon: Int,
        val proposed: Int,
        val archived: Int,
        /** Zero when the pipeline feeds canon only (the P1 exit gate). */
        val violations: List<String>,
        /** Chapters the gate is withholding from every consumer. */
        val withheld: List<String>
    ) {
        val holds: Boolean get() = violations.isEmpty()
        val total: Int get() = canon + proposed + archived
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Int?>(null)
    val selectedBookId: StateFlow<Int?> = _selectedBookId.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _openChapterId = MutableStateFlow<Int?>(null)
    val openChapterId: StateFlow<Int?> = _openChapterId.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    /** Margin rail: AST annotations of the open chapter's *confirmed* text. */
    private val _annotations = MutableStateFlow<List<AstAnnotation>>(emptyList())
    val annotations: StateFlow<List<AstAnnotation>> = _annotations.asStateFlow()

    private val _annotatedAst = MutableStateFlow<ManuscriptAst?>(null)
    val annotatedAst: StateFlow<ManuscriptAst?> = _annotatedAst.asStateFlow()

    private val _mode = MutableStateFlow(StudioMode.INTERACTIVE)
    val mode: StateFlow<StudioMode> = _mode.asStateFlow()

    /** Keyboard-first navigation index in the Autonomous review queue. */
    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _status = MutableStateFlow("Writer Studio ready.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _gateReport = MutableStateFlow(
        GateReport(canon = 0, proposed = 0, archived = 0, violations = emptyList(), withheld = emptyList())
    )
    val gateReport: StateFlow<GateReport> = _gateReport.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** The serialization validator suite report for the selected book (v3.0 P3, plan §6). */
    private val _serialReport = MutableStateFlow<SerialValidatorSuite.Report?>(null)
    val serialReport: StateFlow<SerialValidatorSuite.Report?> = _serialReport.asStateFlow()

    private val _lorebook = MutableStateFlow<List<Lorebook>>(emptyList())
    val lorebook: StateFlow<List<Lorebook>> = _lorebook.asStateFlow()

    /** Ledger entry counts written by L6, by chapter number (rail summary). */
    private val _ledgerCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val ledgerCounts: StateFlow<Map<Int, Int>> = _ledgerCounts.asStateFlow()

    /** The Chekhov ledger for the selected book (v3.0 P2, plan §5.2). */
    private val _guns = MutableStateFlow<List<ChekhovGun>>(emptyList())
    val guns: StateFlow<List<ChekhovGun>> = _guns.asStateFlow()

    /** The question ledger for the selected book (v3.0 P2, plan §6.7). */
    private val _questions = MutableStateFlow<List<QuestionEntry>>(emptyList())
    val questions: StateFlow<List<QuestionEntry>> = _questions.asStateFlow()

    /** The final-act recovery gate verdict for the current ledger (§5.2). */
    private val _compileGate = MutableStateFlow(ChekhovLedger.GateResult(
        blocked = false, blocking = emptyList(), waivers = emptyList(), report = emptyList()
    ))
    val compileGate: StateFlow<ChekhovLedger.GateResult> = _compileGate.asStateFlow()

    val selectedBook: Book? get() = _books.value.firstOrNull { it.id == _selectedBookId.value }
    val openChapter: Chapter? get() = _chapters.value.firstOrNull { it.id == _openChapterId.value }

    /** The Autonomous Mode batch review queue: PROPOSED drafts in chapter order. */
    val reviewQueue: List<Chapter> get() = WriterGate.proposedChapters(_chapters.value)

    val canonChapters: List<Chapter> get() = WriterGate.canonChapters(_chapters.value)

    // ------------------------------------------------------------------
    // Book / chapter selection
    // ------------------------------------------------------------------

    fun refreshBooks() {
        scope.launch {
            _books.value = repository.getBooksSync()
            _lorebook.value = _selectedBookId.value?.let { repository.getLorebookForBookSync(it) } ?: emptyList()
        }
    }

    fun selectBook(bookId: Int) {
        _selectedBookId.value = bookId
        _openChapterId.value = null
        _editorText.value = ""
        _annotations.value = emptyList()
        _annotatedAst.value = null
        scope.launch {
            val chapters = repository.getChaptersForBookSync(bookId)
            _chapters.value = chapters
            _lorebook.value = repository.getLorebookForBookSync(bookId)
            _guns.value = repository.getGunsForBookSync(bookId)
            _questions.value = repository.getQuestionsForBookSync(bookId)
            refreshGateReport()
            refreshSerialReport()
            _status.value = "Loaded \"${_books.value.firstOrNull { it.id == bookId }?.title ?: bookId}\" — " +
                "${WriterGate.canonChapters(chapters).size} canon, ${WriterGate.proposedChapters(chapters).size} proposed."
        }
    }

    /** Recomputes the serialization validator suite for the selected book (P3). */
    fun refreshSerialReport() {
        val book = selectedBook ?: return
        _serialReport.value = if (book.isSerialized)
            SerialValidatorSuite.run(SerialValidatorSuite.Input(
                book = book,
                chapters = _chapters.value,
                arcs = emptyList(), // the Serial Dashboard runs the arc-aware suite
                questions = _questions.value
            ))
        else null
    }

    fun setMode(mode: StudioMode) {
        _mode.value = mode
        _queueIndex.value = 0
        _status.value = when (mode) {
            StudioMode.INTERACTIVE -> "Interactive Mode — the Studio opens per chapter from the drafting card."
            StudioMode.AUTONOMOUS -> "Autonomous Mode — batch review queue: ⏎ confirm · E edit · A archive · R regenerate."
        }
    }

    /** Opens a chapter in the editor. Annotations render for canon text only. */
    fun openChapter(chapterId: Int) {
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: return
        _openChapterId.value = chapterId
        _editorText.value = chapter.content
        reannotate(chapter)
        _status.value = when {
            WriterGate.isCanon(chapter) -> "Chapter ${chapter.chapterNumber} (canon) — margin rail shows ${_annotations.value.size} AST nodes."
            chapter.draftStatus == DraftState.PROPOSED ->
                "Chapter ${chapter.chapterNumber} (PROPOSED) — edit freely, then Confirm to freeze into canon."
            else -> "Chapter ${chapter.chapterNumber} is ARCHIVED — kept for diffing; it never feeds downstream layers."
        }
    }

    fun setEditorText(text: String) {
        _editorText.value = text
    }

    /** Persists the edited text in place (draft state unchanged). */
    fun saveEdits() {
        val chapter = openChapter ?: return
        scope.launch {
            val updated = chapter.copy(content = _editorText.value)
            repository.updateChapter(updated)
            _chapters.value = _chapters.value.map { if (it.id == updated.id) updated else it }
            reannotate(updated)
            _status.value = "Saved chapter ${updated.chapterNumber} (${wordCount(_editorText.value)} words)."
        }
    }

    // ------------------------------------------------------------------
    // The confirm flow — the heart of writer sovereignty (plan §4)
    // ------------------------------------------------------------------

    /**
     * Confirm Chapter: freezes the text, sets writer_confirmed = 1, and
     * triggers L6 annotation + Chekhov ledger updates on the frozen text.
     * Returns true when the chapter became canon.
     */
    fun confirmChapter(chapterId: Int, onDone: (Boolean) -> Unit = {}) {
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: run { onDone(false); return }
        scope.launch {
            val text = if (chapterId == _openChapterId.value) _editorText.value else chapter.content
            val frozen = chapter.copy(
                content = text,
                writerConfirmed = true,
                draftStatus = DraftState.CONFIRMED,
                status = "Completed (${wordCount(text)} words)",
                // P3 (plan §6.12): persist the serialization artifacts on the
                // frozen text — advised ending type, beat coverage, balance
                // shares, and (free tier) the hook check.
                endingType = LiveAstAnnotator.classifyEnding(text).first,
                beatCoverage = LiveAstAnnotator.beatCoverage(text).firstOrNull()
                    ?.payload?.get("coverage")?.let { cov -> "{\"coverage\":\"$cov\"}" },
                balanceShares = BalanceMeter.measure(chapter.copy(content = text)).let { r ->
                    "{" + r.shares.joinToString(",") { s ->
                        "\"" + s.band + "\":" + String.format("%.1f", s.measuredPct)
                    } + "}"
                },
                hookCheck = if (chapter.chapterNumber <= (selectedBook?.freeTierChapters ?: 0)) {
                    val hook = OpeningValidators.validateHook(chapter.copy(content = text))
                    val sources = hook.sourcesFound.joinToString(",") { "\"" + it + "\"" }
                    "{\"scannedWords\":" + hook.scannedWords + ",\"sources\":[" + sources + "]}"
                } else null
            )
            repository.updateChapter(frozen)
            _chapters.value = _chapters.value.map { if (it.id == frozen.id) frozen else it }
            _editorText.value = text

            // L6 runs on the frozen text only: annotation + ledger upserts.
            val ast = LiveAstAnnotator.annotate(frozen, _lorebook.value).ast
            if (ast != null) {
                _annotatedAst.value = ast
                _annotations.value = ast.annotations
                persistAnnotation(frozen, ast)

                // P2 (plan §5.2): duplicate detection on name + description
                // similarity — a re-emphasized promise escalates the existing
                // gun instead of inserting a second entry.
                val ops = ChekhovLedger.planUpsert(_guns.value, frozen, ast)
                var ledgerWrites = 0
                for (op in ops) {
                    when (op) {
                        is ChekhovLedger.Op.Insert -> {
                            repository.insertGun(op.gun)
                            ledgerWrites++
                        }
                        is ChekhovLedger.Op.Escalate -> {
                            ChekhovLedger.escalate(op.gun, op.atChapter)?.let { repository.updateGun(it); ledgerWrites++ }
                        }
                        is ChekhovLedger.Op.SkipDuplicate -> Unit
                    }
                }
                if (ledgerWrites > 0) {
                    _ledgerCounts.value = _ledgerCounts.value + (frozen.chapterNumber to ledgerWrites)
                    _guns.value = repository.getGunsForBookSync(frozen.bookId)
                }

                // P2 (plan §6.7 foundations): open questions in confirmed
                // text enter the question ledger with expected-answer windows.
                val newQuestions = QuestionLedger.detectOpenQuestions(
                    text, frozen.bookId, frozen.chapterNumber
                ).filter { q -> _questions.value.none { it.text == q.text } }
                if (newQuestions.isNotEmpty()) {
                    repository.insertQuestions(newQuestions)
                    _questions.value = repository.getQuestionsForBookSync(frozen.bookId)
                }

                // P2 (plan §5.2): the automatic ORPHANED sweep — once the
                // writing crosses the final-act boundary, unfired guns orphan.
                val totalChapters = selectedBook?.targetChapters?.takeIf { it > 0 }
                    ?: _chapters.value.size
                val finalActStart = ChekhovLedger.finalActStartChapter(totalChapters)
                if (frozen.chapterNumber >= finalActStart) {
                    val swept = ChekhovLedger.orphanSweep(_guns.value, frozen.chapterNumber, finalActStart)
                    swept.zip(_guns.value).filter { it.first.status != it.second.status }
                        .forEach { repository.updateGun(it.first) }
                    _guns.value = repository.getGunsForBookSync(frozen.bookId)
                }
            }
            refreshGateReport()
            refreshSerialReport()
            _status.value = "Confirmed chapter ${frozen.chapterNumber} — writer_confirmed = 1; " +
                "L6 annotated ${_annotations.value.size} nodes; ${_ledgerCounts.value[frozen.chapterNumber] ?: 0} ledger promises recorded; " +
                "ending ${frozen.endingType ?: "—"}."
            onDone(true)
        }
    }

    /**
     * Regenerate: archives the current draft into the container (diffing)
     * and produces a fresh PROPOSED draft from confirmed material only.
     */
    fun regenerateDraft(chapterId: Int, instruction: String = "", onDone: (Boolean) -> Unit = {}) {
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: run { onDone(false); return }
        val book = selectedBook ?: run { _status.value = "Select a book first."; onDone(false); return }
        scope.launch {
            _isGenerating.value = true
            _status.value = "Regenerating chapter ${chapter.chapterNumber} (L4)…"
            try {
                authrRoot?.let { root ->
                    runCatching { DraftArchive(File(root, "authr")).archive(book, chapter, "Regenerated; previous draft kept for diffing") }
                }
                val canonContext = WriterGate.canonChapters(_chapters.value)
                val draft = draftGenerator.generate(book, chapter, canonContext, _lorebook.value, instruction)
                if (draft.isBlank()) {
                    _status.value = "Regeneration failed: the L4 generator returned no text."
                    onDone(false)
                    return@launch
                }
                val proposed = chapter.copy(
                    content = draft.trim(),
                    writerConfirmed = false,
                    draftStatus = DraftState.PROPOSED,
                    status = "Proposed (${wordCount(draft)} words)"
                )
                repository.updateChapter(proposed)
                _chapters.value = _chapters.value.map { if (it.id == proposed.id) proposed else it }
                if (_openChapterId.value == chapterId) {
                    _editorText.value = proposed.content
                    reannotate(proposed)
                }
                refreshGateReport()
                _status.value = "Chapter ${proposed.chapterNumber} re-proposed (${wordCount(draft)} words) — awaiting confirmation."
                onDone(true)
            } catch (e: Throwable) {
                _status.value = "Regeneration error: ${e.message ?: e.javaClass.simpleName}"
                onDone(false)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Archive: the draft is kept in the container for diffing but never
     * feeds downstream layers (plan §4).
     */
    fun archiveDraft(chapterId: Int, onDone: (Boolean) -> Unit = {}) {
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: run { onDone(false); return }
        val book = selectedBook ?: run { _status.value = "Select a book first."; onDone(false); return }
        scope.launch {
            authrRoot?.let { root ->
                runCatching { DraftArchive(File(root, "authr")).archive(book, chapter, "Writer archived draft") }
            }
            val archived = chapter.copy(writerConfirmed = false, draftStatus = DraftState.ARCHIVED)
            repository.updateChapter(archived)
            _chapters.value = _chapters.value.map { if (it.id == archived.id) archived else it }
            if (_openChapterId.value == chapterId) reannotate(archived)
            refreshGateReport()
            _status.value = "Archived chapter ${archived.chapterNumber} — kept for diffing in the container."
            onDone(true)
        }
    }

    // ------------------------------------------------------------------
    // Ledger operations — the Chekhov + question ledgers (v3.0 P2)
    // ------------------------------------------------------------------

    /** The final-act boundary for the selected book (last quarter). */
    fun finalActStartChapter(): Int {
        val total = selectedBook?.targetChapters?.takeIf { it > 0 } ?: _chapters.value.size
        return ChekhovLedger.finalActStartChapter(total)
    }

    /** Writer confirms an escalation from the rail: the promise re-landed. */
    fun escalateGun(gunId: Int, atChapter: Int) {
        scope.launch {
            val gun = repository.getGunById(gunId) ?: return@launch
            ChekhovLedger.escalate(gun, atChapter)?.let {
                repository.updateGun(it)
                _guns.value = repository.getGunsForBookSync(it.bookId)
                refreshCompileGate()
                _status.value = "Gun \"${it.name}\" escalated at chapter $atChapter."
            } ?: run { _status.value = "Escalation refused: the chapter must be later canon than chapter ${gun.introducedChapter}." }
        }
    }

    /** Writer marks the payoff from the AST rail; the quote is archived. */
    fun resolveGun(gunId: Int, chapter: Chapter, quote: String) {
        scope.launch {
            val gun = repository.getGunById(gunId) ?: return@launch
            ChekhovLedger.resolve(gun, chapter, quote)?.let {
                repository.updateGun(it)
                _guns.value = repository.getGunsForBookSync(it.bookId)
                refreshCompileGate()
                _status.value = "Gun \"${it.name}\" resolved in chapter ${chapter.chapterNumber} — payoff quote archived."
            } ?: run {
                _status.value = if (quote.isBlank()) "Resolution refused: a payoff quote is required."
                else "Resolution refused: chapter ${chapter.chapterNumber} is not canon."
            }
        }
    }

    /** Writer accepts the orphan with a mandatory note — waivers are never silent. */
    fun waiveGun(gunId: Int, note: String) {
        scope.launch {
            val gun = repository.getGunById(gunId) ?: return@launch
            ChekhovLedger.waive(gun, note)?.let {
                repository.updateGun(it)
                _guns.value = repository.getGunsForBookSync(it.bookId)
                refreshCompileGate()
                _status.value = "Gun \"${it.name}\" waived — the note will be disclosed in the ship report."
            } ?: run { _status.value = "Waive refused: a note is mandatory (waivers are never silent)." }
        }
    }

    /** Writer answers a question; the chapter of the answer is recorded. */
    fun answerQuestion(questionId: Int, answeredChapter: Int, answeredByReveal: Int? = null) {
        scope.launch {
            val q = _questions.value.firstOrNull { it.id == questionId } ?: return@launch
            repository.updateQuestion(QuestionLedger.answer(q, answeredChapter, answeredByReveal))
            _questions.value = repository.getQuestionsForBookSync(q.bookId)
            _status.value = "Question answered in chapter $answeredChapter."
        }
    }

    /** Re-arms a progressive question's expected-answer window. */
    fun extendQuestionWindow(questionId: Int, windowChapters: Int) {
        scope.launch {
            val q = _questions.value.firstOrNull { it.id == questionId } ?: return@launch
            repository.updateQuestion(QuestionLedger.withWindow(q, windowChapters))
            _questions.value = repository.getQuestionsForBookSync(q.bookId)
        }
    }

    /** Overdue progressive questions for the dashboard (§6.7, Table 15). */
    fun overdueQuestions(currentChapter: Int): List<QuestionEntry> =
        _questions.value.filter { QuestionLedger.isOverdue(it, currentChapter) }

    /** Recomputes the final-act recovery gate verdict for the current ledger. */
    fun refreshCompileGate() {
        _compileGate.value = ChekhovLedger.recoveryGate(_guns.value)
    }

    // ------------------------------------------------------------------
    // Autonomous Mode queue actions (keyboard-first, plan §4.1)
    // ------------------------------------------------------------------

    fun moveQueueCursor(delta: Int) {
        val queue = reviewQueue
        if (queue.isEmpty()) return
        _queueIndex.value = (_queueIndex.value + delta).coerceIn(0, queue.size - 1)
    }

    fun currentQueueChapter(): Chapter? = reviewQueue.getOrNull(_queueIndex.value)

    fun confirmCurrentQueueItem(onDone: (Boolean) -> Unit = {}) {
        currentQueueChapter()?.let { confirmChapter(it.id, onDone) }
    }

    fun archiveCurrentQueueItem(onDone: (Boolean) -> Unit = {}) {
        currentQueueChapter()?.let { archiveDraft(it.id, onDone) }
    }

    fun regenerateCurrentQueueItem(onDone: (Boolean) -> Unit = {}) {
        currentQueueChapter()?.let { regenerateDraft(it.id, onDone = onDone) }
    }

    /** Opens the queue item for editing (switches to Interactive Mode). */
    fun editCurrentQueueItem() {
        currentQueueChapter()?.let {
            _mode.value = StudioMode.INTERACTIVE
            openChapter(it.id)
        }
    }

    // ------------------------------------------------------------------
    // Gate assertion surface (P1 exit gate)
    // ------------------------------------------------------------------

    /**
     * Recomputes the sovereignty report for the selected book. The pipeline
     * feeds canon chapters only, so [GateReport.violations] is empty unless
     * the gate itself is broken; everything unconfirmed is [GateReport.withheld]
     * from the consumer surface (ledger, timeline, exports, compile).
     */
    fun refreshGateReport() {
        val chapters = _chapters.value
        val consumers = listOf("chekhov_ledger", "timeline", "export", "compile")
        val canon = WriterGate.canonChapters(chapters)
        _gateReport.value = GateReport(
            canon = canon.size,
            proposed = WriterGate.proposedChapters(chapters).size,
            archived = chapters.count { it.draftStatus == DraftState.ARCHIVED },
            violations = SixLayerPipeline.assertGate(canon, consumers),
            withheld = chapters.filter { !WriterGate.isCanon(it) }
                .map { "Chapter ${it.chapterNumber} (${it.draftStatus}) withheld from: ${consumers.joinToString()}" }
        )
        refreshCompileGate()
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun reannotate(chapter: Chapter) {
        if (!WriterGate.isCanon(chapter)) {
            _annotations.value = emptyList()
            _annotatedAst.value = null
            return
        }
        val ast = LiveAstAnnotator.annotate(chapter, _lorebook.value).ast
        _annotatedAst.value = ast
        _annotations.value = ast?.annotations ?: emptyList()
    }

    private fun persistAnnotation(chapter: Chapter, ast: ManuscriptAst) {
        val root = authrRoot ?: return
        runCatching {
            val dir = File(root, "authr/${chapter.bookId}/annotations").apply { mkdirs() }
            val json = buildString {
                appendLine("{\"chapterId\":${chapter.id},\"chapterNumber\":${chapter.chapterNumber}," +
                    "\"textHash\":\"${ast.textHash}\",\"annotatedAt\":${ast.annotatedAt},")
                appendLine(" \"annotations\":[")
                append(ast.annotations.joinToString(",\n") { a ->
                    """  {"kind":"${a.kind}","severity":"${a.severity}","start":${a.start},"end":${a.end},""" +
                        """"message":${org.json.JSONObject.quote(a.message)},"excerpt":${org.json.JSONObject.quote(a.excerpt.take(120))}}"""
                })
                appendLine()
                appendLine(" ]}")
            }
            File(dir, "chapter-${chapter.chapterNumber}.json").writeText(json)
        }
    }

    private fun wordCount(text: String): Int = text.split("\\s+".toRegex()).count { it.isNotBlank() }

    override fun onCleared() {
        super.onCleared()
    }
}
