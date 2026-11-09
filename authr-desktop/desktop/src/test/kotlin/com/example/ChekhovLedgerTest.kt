package com.example

import com.example.data.local.AudioTrackDao
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.ChekhovGunDao
import com.example.data.local.LorebookDao
import com.example.data.local.QuestionDao
import com.example.data.local.StoryDao
import com.example.data.model.AudioTrack
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.QuestionEntry
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import com.example.engine.ChekhovLedger
import com.example.engine.DraftState
import com.example.engine.LiveAstAnnotator
import com.example.engine.QuestionLedger
import com.example.engine.WriterGate
import com.example.ui.viewmodel.WriterStudioViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * P2 exit gates 2 and 3 (plan §13, Table 33): "planted guns in the
 * regression corpus all detected; zero false compile blocks."
 *
 * The regression corpus is a six-chapter seeded serial (final act from
 * chapter 5) walked through the Writer Studio confirm flow exactly like the
 * P1 novella, extended with the P2 ledger machinery: duplicate detection on
 * the confirm-time upsert, escalation on re-emphasis, the automatic ORPHANED
 * sweep at the final-act boundary, and the final-act recovery gate that
 * blocks compile while any confirmed gun is unfired — with waivers disclosed,
 * never silent.
 */
class ChekhovLedgerTest {

    // ------------------------------------------------------------------
    // In-memory DAO fakes (Room-free, JVM-pure)
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
        override suspend fun deleteAudioTrackById(id: Int) {}
    }

    private class FakeBookDao(initial: List<Book>) : BookDao {
        private val state = MutableStateFlow(initial.associateBy { it.id })
        override fun getAllBooks(): Flow<List<Book>> = flowOf(emptyList())
        override suspend fun getBooksSync(): List<Book> = state.value.values.sortedBy { it.id }
        override suspend fun getBookById(bookId: Int): Book? = state.value[bookId]
        override fun getBookByIdFlow(bookId: Int): Flow<Book> = flowOf(state.value[bookId]!!)
        override suspend fun insertBook(book: Book): Long = book.id.toLong().also {
            state.value = state.value + (book.id to book)
        }
        override suspend fun updateBook(book: Book) { state.value = state.value + (book.id to book) }
        override suspend fun deleteBook(bookId: Int) { state.value = state.value - bookId }
    }

    private class FakeChapterDao(initial: List<Chapter>) : ChapterDao {
        private val state = MutableStateFlow(initial.associateBy { it.id })
        override fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> = flowOf(state.value.values.filter { it.bookId == bookId }.sortedBy { it.chapterNumber })
        override suspend fun getChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId }.sortedBy { it.chapterNumber }
        override suspend fun getProposedChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId && it.draftStatus == DraftState.PROPOSED }.sortedBy { it.chapterNumber }
        override suspend fun getChapterById(chapterId: Int): Chapter? = state.value[chapterId]
        override suspend fun insertChapter(chapter: Chapter): Long = chapter.id.toLong().also {
            state.value = state.value + (chapter.id to chapter)
        }
        override suspend fun insertChapters(chapters: List<Chapter>) {
            state.value = state.value + chapters.associateBy { it.id }
        }
        override suspend fun updateChapter(chapter: Chapter) { state.value = state.value + (chapter.id to chapter) }
        override suspend fun deleteChapter(chapterId: Int) { state.value = state.value - chapterId }
        override suspend fun searchChapters(query: String): List<Chapter> = emptyList()
    }

    private class FakeLorebookDao : LorebookDao {
        private val entries = mutableListOf<Lorebook>()
        override fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> = flowOf(entries.filter { it.bookId == bookId })
        override suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook> = entries.filter { it.bookId == bookId }
        override suspend fun insertLorebook(lorebook: Lorebook): Long = entries.size.toLong().also { entries.add(lorebook) }
        override suspend fun insertLorebooks(lorebooks: List<Lorebook>) { entries.addAll(lorebooks) }
        override suspend fun updateLorebook(lorebook: Lorebook) {}
        override suspend fun deleteLorebook(lorebookId: Int) {}
    }

    private class FakeChekhovGunDao : ChekhovGunDao {
        val guns = mutableListOf<ChekhovGun>()
        override fun getGunsForBook(bookId: Int): Flow<List<ChekhovGun>> = flowOf(guns.filter { it.bookId == bookId })
        override suspend fun getGunsForBookSync(bookId: Int): List<ChekhovGun> = guns.filter { it.bookId == bookId }
        override suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun> = guns.filter { it.bookId == bookId && it.status in ChekhovLedger.UNRESOLVED }
        override suspend fun getGunById(gunId: Int): ChekhovGun? = guns.firstOrNull { it.id == gunId }
        override suspend fun insertGun(gun: ChekhovGun): Long = guns.size.toLong().also { guns.add(gun.copy(id = guns.size)) }
        override suspend fun insertGuns(newGuns: List<ChekhovGun>) {
            newGuns.forEach { guns.add(it.copy(id = guns.size + 1)) }
        }
        override suspend fun updateGun(gun: ChekhovGun) {
            val idx = guns.indexOfFirst { it.id == gun.id }
            if (idx >= 0) guns[idx] = gun
        }
        override suspend fun deleteGun(gunId: Int) { guns.removeAll { it.id == gunId } }
    }

    private class FakeQuestionDao : QuestionDao {
        val questions = mutableListOf<QuestionEntry>()
        override fun getQuestionsForBook(bookId: Int): Flow<List<QuestionEntry>> = flowOf(questions.filter { it.bookId == bookId })
        override suspend fun getQuestionsForBookSync(bookId: Int): List<QuestionEntry> = questions.filter { it.bookId == bookId }
        override suspend fun getOverdueProgressiveQuestions(bookId: Int, currentChapter: Int): List<QuestionEntry> =
            questions.filter { QuestionLedger.isOverdue(it, currentChapter) }
        override suspend fun getQuestionById(questionId: Int): QuestionEntry? = questions.firstOrNull { it.id == questionId }
        override suspend fun insertQuestion(question: QuestionEntry): Long = questions.size.toLong().also {
            questions.add(question.copy(id = questions.size))
        }
        override suspend fun insertQuestions(newQuestions: List<QuestionEntry>) {
            newQuestions.forEach { questions.add(it.copy(id = questions.size + 1)) }
        }
        override suspend fun updateQuestion(question: QuestionEntry) {
            val idx = questions.indexOfFirst { it.id == question.id }
            if (idx >= 0) questions[idx] = question
        }
        override suspend fun deleteQuestion(questionId: Int) { questions.removeAll { it.id == questionId } }
    }

    // ------------------------------------------------------------------
    // The regression corpus — a six-chapter seeded serial
    // ------------------------------------------------------------------

    private val chapterTexts = listOf(
        // ch 1 — plants five promises
        """The boundary stones had moved again, and no one knew who had moved them.
            Mara swore she would return the blade by midwinter, no matter the toll.
            The sealed ledger lay open on the ferryman's desk, its clasp broken.
            He owed a debt of honor to the ferryman who had carried his mother.
            She never forgave the quartermaster for the winter of the stamp tax.""",
        // ch 2 — re-emphasizes the vow (escalation) and plants a distinct new vow
        """She swore she would return the blade by midwinter — this time she swore it at the altar.
            The ferryman vowed to carry them free until the ice broke.
            The sealed ledger's broken clasp was the talk of the crossing.""",
        // ch 3 — the vow pays off (writer resolves it from the rail)
        """At the shrine Mara laid the blade on the stone and the oath was finally paid.
            The debt of honor still hung over the crossing like weather.""",
        // ch 4 — escalates the grudge
        """She never forgave the quartermaster; the grudge deepened when she found his name in the ledger.
            The ferryman's promise still stood.""",
        // ch 5 — final act begins here (targetChapters 6 → final act at 5)
        """The quartermaster arrived with the tide. Mara chose to face him instead of fleeing,
            and the choice was sealed. The debt was finally paid at the crossing.""",
        // ch 6 — the last chapter
        """The ice broke early. The sealed ledger burned cleanly, and the crossing stood empty."""
    )

    private fun seedSerial(): Pair<Book, List<Chapter>> {
        val book = Book(
            id = 7, title = "The Boundary Stones", genre = "Folk Drama",
            synopsis = "A ferry crossing, a moved stone, and an unpaid debt.",
            outline = "Six chapters.", personaId = "standard", format = "Serial",
            wordMin = 50, wordMax = 5000, targetChapters = 6
        )
        val chapters = chapterTexts.mapIndexed { index, text ->
            Chapter(
                id = 200 + index, bookId = 7, chapterNumber = index + 1,
                title = "Chapter ${index + 1}", content = text,
                summary = "Beat sheet: continuity, objective, resistance, escalation, turn, payoff, pull.",
                status = "Proposed", writerConfirmed = false, draftStatus = DraftState.PROPOSED
            )
        }
        return book to chapters
    }

    // ------------------------------------------------------------------
    // Gate 2: planted guns in the regression corpus are all detected
    // ------------------------------------------------------------------

    @Test
    fun `every planted gun in the regression corpus is detected`() = runTest {
        val (book, chapters) = seedSerial()
        val gunDao = FakeChekhovGunDao()
        val vm = studioVm(book, chapters, gunDao, FakeQuestionDao(), this.backgroundScope)
        testScheduler.runCurrent()

        // Walk the whole serial through the confirm flow.
        repeat(chapters.size) { vm.confirmCurrentQueueItem(); testScheduler.runCurrent() }

        val ledger = gunDao.getGunsForBookSync(book.id)
        val byName = ledger.groupBy { it.name }

        // The five planted ch.1 promises + the ferryman's distinct vow.
        assertTrue(byName.containsKey("Vow or promise"), "vows detected")
        assertTrue(byName.containsKey("Debt or owed favor"), "debt detected")
        assertTrue(byName.containsKey("Detailed weapon or artifact"), "detailed artifact detected")
        assertTrue(byName.containsKey("Named grudge"), "grudge detected")
        assertTrue(byName.containsKey("Open question"), "open question detected")

        val vows = byName["Vow or promise"].orEmpty()
        assertTrue(vows.any { it.introducedChapter == 1 }, "Mara's vow entered the ledger at ch. 1")
        assertTrue(vows.any { it.introducedChapter == 2 },
            "the ferryman's distinct vow is its own entry, not a false duplicate: ${vows.map { it.introducedChapter }}")
        val canonNumbers = vm.chapters.value.filter { WriterGate.isCanon(it) }.map { it.chapterNumber }.toSet()
        assertTrue(ledger.all { gun -> gun.introducedChapter in canonNumbers },
            "every ledger entry traces to a canon chapter")
    }

    // ------------------------------------------------------------------
    // The ledger state machine (plan §5.2, Table 8)
    // ------------------------------------------------------------------

    @Test
    fun `re-emphasis escalates the existing gun instead of duplicating it`() = runTest {
        val (book, chapters) = seedSerial()
        val gunDao = FakeChekhovGunDao()
        val vm = studioVm(book, chapters, gunDao, FakeQuestionDao(), this.backgroundScope)
        testScheduler.runCurrent()
        vm.confirmChapter(chapters[0].id); testScheduler.runCurrent()  // ch1: vow planted
        vm.confirmChapter(chapters[1].id); testScheduler.runCurrent()  // ch2: vow re-emphasized

        val vows = gunDao.getGunsForBookSync(book.id).filter { it.name == "Vow or promise" }
        val maras = vows.filter { it.introducedChapter == 1 }
        assertEquals(1, maras.size, "the re-emphasized promise stays ONE ledger entry")
        val gun = maras.single()
        assertEquals(ChekhovLedger.ESCALATED, gun.status, "status moved to ESCALATED")
        assertTrue(gun.escalationChapters.contains("2"), "escalation history keeps the chapter list: ${gun.escalationChapters}")
    }

    @Test
    fun `escalate and resolve guards hold`() = runTest {
        val gun = ChekhovGun(bookId = 1, name = "Vow or promise", introducedChapter = 1, quote = "she swore she would return the blade")
        // ESCALATE: guard — later chapter only.
        assertNull(ChekhovLedger.escalate(gun, 1), "cannot escalate in the chapter that introduced it")
        assertNull(ChekhovLedger.escalate(gun, 0), "cannot escalate backwards")
        val escalated = ChekhovLedger.escalate(gun, 2)!!
        assertEquals(ChekhovLedger.ESCALATED, escalated.status)
        // RESOLVE: guard — canon chapter + archived quote mandatory.
        val canon = Chapter(bookId = 1, chapterNumber = 3, title = "C3", content = "paid",
            summary = "", status = "Completed", writerConfirmed = true, draftStatus = DraftState.CONFIRMED)
        val proposed = canon.copy(writerConfirmed = false, draftStatus = DraftState.PROPOSED)
        assertNull(ChekhovLedger.resolve(gun, proposed, "quote"), "resolution must occur in confirmed text")
        assertNull(ChekhovLedger.resolve(gun, canon, ""), "a resolution needs its payoff quote")
        val resolved = ChekhovLedger.resolve(escalated, canon, "the blade crossed the stone")!!
        assertEquals(ChekhovLedger.RESOLVED, resolved.status)
        assertEquals(3, resolved.resolvedChapter)
        assertEquals("the blade crossed the stone", resolved.quote, "the payoff quote is archived")
        assertNull(ChekhovLedger.resolve(resolved, canon, "again"), "a resolved gun is closed")
    }

    @Test
    fun `waivers require a mandatory note and are never silent`() = runTest {
        val gun = ChekhovGun(bookId = 1, name = "Open question", introducedChapter = 2, quote = "no one knew who moved the stones")
        assertNull(ChekhovLedger.waive(gun, ""), "a blank note is refused — waivers are never silent")
        assertNull(ChekhovLedger.waive(gun, "   "), "a whitespace note is refused")
        val waived = ChekhovLedger.waive(gun, "sequel hook — the stones carry into book two")!!
        assertEquals(ChekhovLedger.WAIVED, waived.status)
        assertTrue(waived.waiveNote.isNotBlank(), "the note is archived with the entry")
        // The gate discloses every waiver in its report.
        val gate = ChekhovLedger.recoveryGate(listOf(waived))
        assertFalse(gate.blocked, "a waived gun does not block compile")
        assertTrue(gate.report.any { it.contains("sequel hook") },
            "the waiver note appears in the gate report: ${gate.report}")
    }

    @Test
    fun `the orphan sweep fires at the final-act boundary`() {
        val introduced = ChekhovGun(bookId = 1, name = "G", introducedChapter = 1, quote = "q")
        val resolved = introduced.copy(status = ChekhovLedger.RESOLVED, resolvedChapter = 3)
        val waived = introduced.copy(status = ChekhovLedger.WAIVED, waiveNote = "n")
        val lateGun = introduced.copy(introducedChapter = 5) // made inside the final act

        val finalActStart = ChekhovLedger.finalActStartChapter(6)
        assertEquals(5, finalActStart, "a six-chapter book enters the final act at chapter 5 (last quarter)")

        // Before the boundary: nothing changes.
        val before = ChekhovLedger.orphanSweep(listOf(introduced, resolved, waived, lateGun), 4, finalActStart)
        assertEquals(ChekhovLedger.INTRODUCED, before[0].status)
        // At the boundary: unfired guns orphan automatically; resolved and
        // waived entries are untouched; guns made inside the final act get
        // their own chance and are not swept.
        val after = ChekhovLedger.orphanSweep(listOf(introduced, resolved, waived, lateGun), 5, finalActStart)
        assertEquals(ChekhovLedger.ORPHANED, after[0].status)
        assertEquals(ChekhovLedger.RESOLVED, after[1].status)
        assertEquals(ChekhovLedger.WAIVED, after[2].status)
        assertEquals(ChekhovLedger.INTRODUCED, after[3].status, "a gun made inside the final act is not swept")
    }

    // ------------------------------------------------------------------
    // Gate 3: zero false compile blocks
    // ------------------------------------------------------------------

    @Test
    fun `the recovery gate blocks only unresolved guns`() {
        val open = ChekhovGun(bookId = 1, name = "G", introducedChapter = 1, quote = "q")
        val escalated = open.copy(status = ChekhovLedger.ESCALATED, escalationChapters = "[2]")
        val orphaned = open.copy(status = ChekhovLedger.ORPHANED)
        val resolved = open.copy(status = ChekhovLedger.RESOLVED, resolvedChapter = 3)
        val waived = open.copy(status = ChekhovLedger.WAIVED, waiveNote = "sequel hook")

        assertTrue(ChekhovLedger.recoveryGate(emptyList()).let { !it.blocked && it.report.isEmpty() },
            "an empty ledger compiles clean — no false block")
        assertTrue(ChekhovLedger.recoveryGate(listOf(resolved)).let { !it.blocked },
            "an all-resolved ledger compiles clean — no false block")
        assertTrue(ChekhovLedger.recoveryGate(listOf(resolved, waived)).let { !it.blocked && it.waiverCount == 1 },
            "resolved + waived compiles clean, waiver disclosed — no false block")
        listOf(open, escalated, orphaned).forEach { g ->
            val gate = ChekhovLedger.recoveryGate(listOf(g))
            assertTrue(gate.blocked, "${g.status} blocks the compile step")
            assertTrue(gate.report.any { it.contains(g.name) }, "the report names the blocking gun")
        }
    }

    @Test
    fun `end to end - blocked serial recovers through resolve and waive`() = runTest {
        val (book, chapters) = seedSerial()
        val gunDao = FakeChekhovGunDao()
        val questionDao = FakeQuestionDao()
        val vm = studioVm(book, chapters, gunDao, questionDao, this.backgroundScope)
        testScheduler.runCurrent()
        repeat(chapters.size) { vm.confirmCurrentQueueItem(); testScheduler.runCurrent() }

        // At the final act the sweep has run: the gate is BLOCKED.
        vm.refreshCompileGate()
        assertTrue(vm.compileGate.value.blocked, "unfired promises block compilation")
        assertTrue(vm.compileGate.value.blocking.isNotEmpty())

        // The writer resolves or waives every blocking gun.
        val canon = vm.chapters.value // the VM state carries the confirmed copies
        vm.compileGate.value.blocking.toList().forEachIndexed { index, gun ->
            if (index % 2 == 0) vm.resolveGun(gun.id, canon[gun.introducedChapter - 1], "payoff quote: ${gun.quote.take(30)}")
            else vm.waiveGun(gun.id, "deliberate ambiguity — sequel hook ${gun.name}")
            testScheduler.runCurrent()
        }
        assertFalse(vm.compileGate.value.blocked, "after resolve/waive the gate clears — zero false compile blocks")
        assertTrue(vm.compileGate.value.waivers.isNotEmpty(), "waivers remain listed in the report")
        assertTrue(vm.compileGate.value.report.isNotEmpty(), "the ship report is not silent about waivers")
    }

    // ------------------------------------------------------------------
    // Shared VM factory
    // ------------------------------------------------------------------

    private fun studioVm(
        book: Book,
        chapters: List<Chapter>,
        gunDao: ChekhovGunDao,
        questionDao: QuestionDao,
        scope: kotlinx.coroutines.CoroutineScope
    ): WriterStudioViewModel {
        val repository = AppRepository(
            storyDao = FakeStoryDao(),
            audioTrackDao = FakeAudioTrackDao(),
            bookDao = FakeBookDao(listOf(book)),
            chapterDao = FakeChapterDao(chapters),
            lorebookDao = FakeLorebookDao(),
            chekhovGunDao = gunDao,
            questionDao = questionDao
        )
        return WriterStudioViewModel(
            repository = repository,
            authrRoot = null,
            draftGenerator = WriterStudioViewModel.L4DraftGenerator { _, _, _, _, _ -> "" },
            scope = scope
        ).also {
            it.refreshBooks(); it.selectBook(book.id)
        }
    }
}
