package com.example

import com.example.data.local.AudioTrackDao
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.LorebookDao
import com.example.data.local.QuestionDao
import com.example.data.local.SerialArcDao
import com.example.data.local.StoryDao
import com.example.data.model.AudioTrack
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import com.example.engine.DraftState
import com.example.engine.serialization.BufferCadence
import com.example.engine.serialization.GoldenSerial
import com.example.engine.serialization.PreDraftWorksheet
import com.example.engine.serialization.QcScorecard
import com.example.engine.serialization.SerialValidatorSuite
import com.example.ui.viewmodel.SerialDashboardViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Serial Dashboard's state machine over Room-free fakes: arc planning,
 * worksheet + scorecard persistence, the release action driving buffer and
 * cadence, check dismissal, and the suite verdict.
 */
class SerialDashboardTest {

    // ------------------------------------------------------------------
    // Room-free fakes (the ChekhovLedgerTest pattern)
    // ------------------------------------------------------------------

    private class FakeStoryDao : StoryDao {
        override fun getAllStories(): Flow<List<Story>> = flowOf(emptyList())
        override suspend fun getStoryById(id: Int): Story? = null
        override suspend fun insertStory(story: Story): Long = 1
        override suspend fun updateStory(story: Story) {}
        override suspend fun deleteStory(story: Story) {}
        override suspend fun deleteStoryById(id: Int) {}
        override suspend fun searchStories(query: String): List<Story> = emptyList()
    }

    private class FakeAudioTrackDao : AudioTrackDao {
        override fun getAllAudioTracks(): Flow<List<AudioTrack>> = flowOf(emptyList())
        override suspend fun insertAudioTrack(audioTrack: AudioTrack): Long = 1
        override suspend fun deleteAudioTrack(audioTrack: AudioTrack) {}
        override suspend fun deleteAudioTrackById(audioTrackId: Int) {}
    }

    class FakeBookDao(initial: List<Book>) : BookDao {
        val state = MutableStateFlow(initial.associateBy { it.id })
        override fun getAllBooks(): Flow<List<Book>> = flowOf(emptyList())
        override suspend fun getBooksSync(): List<Book> = state.value.values.sortedBy { it.id }
        override suspend fun getBookById(bookId: Int): Book? = state.value[bookId]
        override fun getBookByIdFlow(bookId: Int): Flow<Book> = flowOf(state.value[bookId]!!)
        override suspend fun insertBook(book: Book): Long = book.id.toLong().also { state.value = state.value + (book.id to book) }
        override suspend fun updateBook(book: Book) { state.value = state.value + (book.id to book) }
        override suspend fun deleteBook(bookId: Int) { state.value = state.value - bookId }
    }

    class FakeChapterDao(initial: List<Chapter>) : ChapterDao {
        val state = MutableStateFlow(initial.associateBy { it.id })
        override fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> = flowOf(state.value.values.filter { it.bookId == bookId })
        override suspend fun getChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId }.sortedBy { it.chapterNumber }
        override suspend fun getProposedChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId && it.draftStatus == DraftState.PROPOSED }
        override suspend fun getChapterById(chapterId: Int): Chapter? = state.value[chapterId]
        override suspend fun insertChapter(chapter: Chapter): Long = chapter.id.toLong().also { state.value = state.value + (chapter.id to chapter) }
        override suspend fun insertChapters(chapters: List<Chapter>) { state.value = state.value + chapters.associateBy { it.id } }
        override suspend fun updateChapter(chapter: Chapter) { state.value = state.value + (chapter.id to chapter) }
        override suspend fun deleteChapter(chapterId: Int) { state.value = state.value - chapterId }
        override suspend fun searchChapters(query: String): List<Chapter> = emptyList()
    }

    private class FakeLorebookDao : LorebookDao {
        override fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> = flowOf(emptyList())
        override suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook> = emptyList()
        override suspend fun insertLorebook(lorebook: Lorebook): Long = 1
        override suspend fun insertLorebooks(lorebooks: List<Lorebook>) {}
        override suspend fun updateLorebook(lorebook: Lorebook) {}
        override suspend fun deleteLorebook(lorebookId: Int) {}
    }

    private class FakeQuestionDao : QuestionDao {
        val questions = mutableListOf<QuestionEntry>()
        override fun getQuestionsForBook(bookId: Int): Flow<List<QuestionEntry>> = flowOf(questions.filter { it.bookId == bookId })
        override suspend fun getQuestionsForBookSync(bookId: Int): List<QuestionEntry> = questions.filter { it.bookId == bookId }
        override suspend fun getOverdueProgressiveQuestions(bookId: Int, currentChapter: Int): List<QuestionEntry> =
            questions.filter { com.example.engine.QuestionLedger.isOverdue(it, currentChapter) }
        override suspend fun getQuestionById(questionId: Int): QuestionEntry? = questions.firstOrNull { it.id == questionId }
        override suspend fun insertQuestion(question: QuestionEntry): Long = questions.size.toLong().also { questions.add(question.copy(id = questions.size)) }
        override suspend fun insertQuestions(newQuestions: List<QuestionEntry>) { newQuestions.forEach { questions.add(it.copy(id = questions.size + 1)) } }
        override suspend fun updateQuestion(question: QuestionEntry) { val i = questions.indexOfFirst { it.id == question.id }; if (i >= 0) questions[i] = question }
        override suspend fun deleteQuestion(questionId: Int) { questions.removeAll { it.id == questionId } }
    }

    private class FakeSerialArcDao : SerialArcDao {
        val arcs = mutableListOf<SerialArc>()
        override fun getArcsForBook(bookId: Int): Flow<List<SerialArc>> = flowOf(arcs.filter { it.bookId == bookId })
        override suspend fun getArcsForBookSync(bookId: Int): List<SerialArc> = arcs.filter { it.bookId == bookId }.sortedBy { it.arcIndex }
        override suspend fun getArcById(arcId: Int): SerialArc? = arcs.firstOrNull { it.id == arcId }
        override suspend fun insertArc(arc: SerialArc): Long = arcs.size.toLong().also { arcs.add(arc.copy(id = arcs.size + 1)) }
        override suspend fun insertArcs(newArcs: List<SerialArc>) { newArcs.forEach { arcs.add(it.copy(id = arcs.size + 1)) } }
        override suspend fun updateArc(arc: SerialArc) { val i = arcs.indexOfFirst { it.id == arc.id }; if (i >= 0) arcs[i] = arc }
        override suspend fun deleteArc(arcId: Int) { arcs.removeAll { it.id == arcId } }
        override suspend fun deleteArcsForBook(bookId: Int) { arcs.removeAll { it.bookId == bookId } }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun fixture(bookId: Int): Triple<Book, List<Chapter>, List<QuestionEntry>> {
        val book = GoldenSerial.book().copy(id = bookId)
        val chapters = GoldenSerial.chapters(bookId).mapIndexed { i, ch -> ch.copy(id = 900 + i) }
        val questions = GoldenSerial.seedQuestions(bookId, chapters).mapIndexed { i, q -> q.copy(id = i + 1) }
        return Triple(book, chapters, questions)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `the dashboard recomputes buffer, cadence and the suite for a loaded serial`() = runTest {
        val (book, chapters, questions) = fixture(81)
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(chapters), FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, exportDir = null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)

        val buffer = vm.buffer.value
        assertNotNull(buffer)
        assertEquals(4, buffer.confirmedUnreleased, "15 canon - 11 released")
        assertEquals(BufferCadence.BufferLevel.AMBER, buffer.level)
        val report = vm.report.value
        assertNotNull(report)
        assertTrue(report.passes, "hard gates green on the golden serial: ${report.hardBlocks.map { it.message }}")
        assertTrue(report.warnings.size <= 2, "at most two dismissable warnings, got ${report.warnings.map { it.message }}")
    }

    @Test
    fun `planArcs lays the default plan and the arc board lists it`() = runTest {
        val (book, chapters, questions) = fixture(82)
        val arcDao = FakeSerialArcDao()
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(chapters), FakeLorebookDao(), null,
            FakeQuestionDao().apply { insertQuestions(questions) }, arcDao)
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        vm.planArcs()
        assertEquals(1, arcDao.arcs.size, "15 chapters → one 15-chapter arc")
        assertEquals(1, vm.arcs.value.size)
        vm.updateArc(vm.arcs.value.first().copy(contribution = "advance the central story"))
        assertEquals("advance the central story", arcDao.arcs.first().contribution)
    }

    @Test
    fun `saving a scorecard and worksheet persists to the chapter and recomputes`() = runTest {
        val (book, chapters, questions) = fixture(83)
        val chapterDao = FakeChapterDao(chapters)
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            chapterDao, FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        val target = vm.chapters.value.first()
        vm.selectChapter(target.id)

        // A reconsider-band scorecard (4/14) blocks the release queue.
        vm.saveScorecard(target, listOf(1, 0, 1, 0, 1, 0, 1), List(7) { "" })
        val scored = chapterDao.state.value[target.id]!!
        assertEquals(4, scored.qcTotal)
        val gate = QcScorecard.gate(scored)
        assertFalse(gate.releasable, "0-5 blocks")
        val report = vm.report.value!!
        assertTrue(report.hardBlocks.any { it.chapter == 1 && it.id == "scorecard" }, "the suite hard-blocks the reconsider band")

        // Restoring a strong scorecard clears it.
        vm.saveScorecard(scored, listOf(2, 2, 2, 2, 2, 2, 1), List(7) { "" })
        assertFalse(vm.report.value!!.hardBlocks.any { it.id == "scorecard" && it.chapter == 1 })

        // Worksheet persistence.
        vm.saveWorksheet(scored, PreDraftWorksheet.Worksheet(
            objective = "o", characterDevelopment = "b", plotDevelopment = "c", readerExperience = "d", chapterEnding = "e"
        ))
        assertTrue(PreDraftWorksheet.of(chapterDao.state.value[target.id]!!).complete)
    }

    @Test
    fun `releasing a chapter moves it out of the buffer and onto the cadence`() = runTest {
        val (book, chapters, questions) = fixture(84)
        val chapterDao = FakeChapterDao(chapters)
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            chapterDao, FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        val target = vm.chapters.value.first { it.chapterNumber == 12 } // the first buffer chapter
        vm.selectChapter(target.id)
        vm.releaseChapter(target.id)

        val released = chapterDao.state.value[target.id]!!
        assertTrue(BufferCadence.isReleased(released))
        assertEquals(3, vm.buffer.value!!.confirmedUnreleased, "12 released, 3 unreleased — RED")
        assertEquals(BufferCadence.BufferLevel.RED, vm.buffer.value!!.level)
        assertTrue(vm.report.value!!.warnings.any { it.id == "buffer" }, "a red buffer is a dismissable warning")
    }

    @Test
    fun `release is refused for non-canon chapters`() = runTest {
        val (book, chapters, questions) = fixture(85)
        val proposed = chapters.map { if (it.chapterNumber == 12) it.copy(writerConfirmed = false, draftStatus = DraftState.PROPOSED, id = it.id) else it }
        val chapterDao = FakeChapterDao(proposed)
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            chapterDao, FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        val target = vm.chapters.value.first { it.chapterNumber == 12 }
        vm.selectChapter(target.id)
        vm.releaseChapter(target.id)
        assertFalse(BufferCadence.isReleased(chapterDao.state.value[target.id]!!), "the sovereignty gate refuses the release")
        assertTrue(vm.status.value.contains("not canon"))
    }

    @Test
    fun `dismissed warnings leave the budget but stay disclosed`() = runTest {
        val (book, chapters, questions) = fixture(86)
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(chapters), FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        // Force a warning: release buffer chapters down to red.
        vm.chapters.value.filter { it.chapterNumber >= 12 }.forEach { vm.releaseChapter(it.id) }
        val bufferWarning = vm.report.value!!.warnings.first { it.id == "buffer" }
        vm.dismissCheck(bufferWarning.key)
        assertFalse(vm.report.value!!.warnings.any { it.id == "buffer" }, "dismissed from the active budget")
        assertEquals(setOf(bufferWarning.key), vm.dismissed.value, "the dismissal key is disclosed in state")
    }

    @Test
    fun `the export profile rides the book row`() = runTest {
        val (book, chapters, questions) = fixture(87)
        val bookDao = FakeBookDao(listOf(book))
        val repo = AppRepository(FakeStoryDao(), FakeAudioTrackDao(), bookDao,
            FakeChapterDao(chapters), FakeLorebookDao(), null, FakeQuestionDao().apply { insertQuestions(questions) }, FakeSerialArcDao())
        val vm = SerialDashboardViewModel(repo, null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        vm.refreshBooks()
        vm.selectBook(book.id)
        vm.setExportProfile("tapas")
        assertEquals("tapas", bookDao.state.value[book.id]!!.exportProfile, "the profile persists on the book (§6.12)")
        assertEquals("tapas", vm.exportProfileId.value)
    }
}
