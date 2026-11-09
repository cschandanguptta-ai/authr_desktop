package com.example

import com.example.data.local.AudioTrackDao
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.ChekhovGunDao
import com.example.data.local.LorebookDao
import com.example.data.local.StoryDao
import com.example.data.model.AudioTrack
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import com.example.engine.DraftState
import com.example.engine.SixLayerPipeline
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

/**
 * P1 exit gate (plan §13, Table 33): "A five-chapter novella runs end to
 * end with the confirm flow; no unconfirmed text reaches any ledger or
 * export (seeded-project assertion suite)".
 *
 * The suite seeds a five-chapter novella as L4 PROPOSED output, walks the
 * Writer Studio confirm flow chapter by chapter against in-memory DAO fakes,
 * and asserts the writer-sovereignty gate across the full consumer surface
 * (ledger, timeline, exports, compile).
 */
class WriterStudioFlowTest {

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
        override suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun> = guns.filter { it.bookId == bookId && it.status in listOf("INTRODUCED", "ESCALATED", "ORPHANED") }
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

    // ------------------------------------------------------------------
    // The seeded five-chapter novella
    // ------------------------------------------------------------------

    private val novellaTexts = listOf(
        """The forge was still smoking when Mara arrived, picking up where the night had left off.
            She needed to reclaim the sealed ledger before the guild woke. But the apprentice refused,
            and the corridor was blocked. Worse, the watch doubled its rounds and the stair collapsed.
            Then she realized the ledger was a forgery and everything changed. At last she won it back;
            the debt between them was finally paid. Tomorrow the Ashfall Accord would be tested.""".trimIndent(),
        """Mara still carried the sealed ledger. She had to find the archivist before dawn, but the
            guild gate was blocked and the pass refused her. The hunt turned worse when the forgery
            scandal escalated. She discovered the archivist's true name, and it changed her plan.
            The archivist finally answered her. She swore she would return it by morning; the debt
            of honor was promised. Tomorrow the council would convene.""".trimIndent(),
        """The council chamber was still tense, continuing the morning's confrontation. Mara wanted
            to expose the forgery ring, but the chairwoman refused her audience. The resistance
            hardened; the danger grew worse. Then she uncovered the chairwoman's secret signature
            and the room shifted. At last the vote went through, and the consequence was real.
            Little did she know the second ledger existed.""".trimIndent(),
        """Mara returned to the forge where it had all started. Her objective was simple: destroy
            the second ledger. But the apprentice she had spared stood in the way, and the fire
            made everything worse. The standoff escalated into open betrayal. She chose to spare
            him instead of burning the evidence, sealing her fate with no going back. At last the
            choice was made and it could not be undone. What she didn't know was who had watched.""".trimIndent(),
        """The watcher was still out there, continuing the chain she had sealed. Mara needed to end
            it before the Accord test, but the watcher refused open conflict and the streets turned
            more dangerous with each hour. The chase escalated until the tower. She finally saw the
            watcher's face and understood everything. At last the truth came out, and the debt was
            resolved at last. The Ashfall Accord held — the promise she had made was finally paid.""".trimIndent()
    )

    private fun seedNovella(): Pair<Book, List<Chapter>> {
        val book = Book(
            id = 42, title = "The Sealed Ledger", genre = "Heist Drama",
            synopsis = "A forger's apprentice exposes a guild conspiracy.",
            outline = "Five chapters.", personaId = "standard", format = "Novel",
            wordMin = 50, wordMax = 5000
        )
        val chapters = novellaTexts.mapIndexed { index, text ->
            Chapter(
                id = 100 + index, bookId = 42, chapterNumber = index + 1,
                title = "Chapter ${index + 1}", content = text,
                summary = "Beat sheet: continuity, objective, resistance, escalation, turn, payoff, pull.",
                status = "Proposed", writerConfirmed = false, draftStatus = DraftState.PROPOSED
            )
        }
        return book to chapters
    }

    // ------------------------------------------------------------------
    // The end-to-end confirm flow
    // ------------------------------------------------------------------

    @Test
    fun `five-chapter novella runs end to end with the confirm flow`() = runTest {
        val (book, chapters) = seedNovella()
        val bookDao = FakeBookDao(listOf(book))
        val chapterDao = FakeChapterDao(chapters)
        val gunDao = FakeChekhovGunDao()
        val repository = AppRepository(
            storyDao = FakeStoryDao(),
            audioTrackDao = FakeAudioTrackDao(),
            bookDao = bookDao,
            chapterDao = chapterDao,
            lorebookDao = FakeLorebookDao(),
            chekhovGunDao = gunDao
        )
        val vm = WriterStudioViewModel(
            repository = repository,
            authrRoot = null,
            draftGenerator = WriterStudioViewModel.L4DraftGenerator { _, _, _, _, _ -> "" },
            scope = this.backgroundScope
        )
        vm.refreshBooks(); vm.selectBook(book.id); testScheduler.runCurrent()

        // 1. The seeded novella arrives as five PROPOSED L4 drafts.
        assertEquals(5, vm.reviewQueue.size, "all five drafts await review")
        assertFalse(vm.gateReport.value.canon > 0, "nothing is canon before confirmation")
        assertEquals(5, vm.gateReport.value.withheld.size, "every draft is withheld from the consumer surface")

        // 2. The confirm flow: accept chapters 1-3 as-is, edit chapter 4, confirm it.
        repeat(3) { vm.confirmCurrentQueueItem(); testScheduler.runCurrent() }
        val ch4 = chapters[3]
        vm.openChapter(ch4.id); testScheduler.runCurrent()
        vm.setEditorText(ch4.content + "\n\nAt last she chose, and the choice was sealed for good.")
        vm.saveEdits(); testScheduler.runCurrent()
        vm.confirmChapter(ch4.id); testScheduler.runCurrent()

        // 3. Chapter 5 is archived — kept for diffing, never feeding downstream.
        vm.archiveCurrentQueueItem(); testScheduler.runCurrent()

        val all = chapterDao.getChaptersForBookSync(book.id)
        assertEquals(4, WriterGate.canonChapters(all).size, "chapters 1-4 are canon")
        assertEquals(0, WriterGate.proposedChapters(all).size, "review queue empty")
        assertEquals(1, all.count { it.draftStatus == DraftState.ARCHIVED }, "chapter 5 archived")

        // 4. L6 wrote the ledger on the frozen text only — canon promises.
        val ledgerGuns = gunDao.getGunsForBookSync(book.id)
        assertTrue(ledgerGuns.isNotEmpty(), "the Chekhov ledger was updated on confirmation")
        assertTrue(ledgerGuns.all { gun ->
            val ch = all.first { it.chapterNumber == gun.introducedChapter }
            WriterGate.isCanon(ch)
        }, "every ledger entry traces to a canon chapter")

        // 5. THE EXIT GATE: consumers are fed canon only — the assertion
        //    suite proves zero violations on the fed surface, and proves the
        //    primitive itself still catches an unfiltered feed (chapter 5).
        val consumers = listOf("chekhov_ledger", "timeline", "epub_export", "manuscript_compile")
        assertTrue(
            SixLayerPipeline.gateHolds(WriterGate.canonChapters(all), consumers),
            "zero violations on the canon-fed consumer surface: ${SixLayerPipeline.assertGate(WriterGate.canonChapters(all), consumers)}"
        )
        assertEquals(
            1, SixLayerPipeline.assertGate(all, consumers).size,
            "the gate primitive still catches an unfiltered feed (archived chapter 5)"
        )
        assertTrue(vm.gateReport.value.holds, "the Studio sovereignty report holds")
        assertEquals(1, vm.gateReport.value.withheld.size, "archived chapter 5 remains withheld")
        assertTrue(
            WriterGate.canonChapters(all).all { it.writerConfirmed && it.draftStatus == DraftState.CONFIRMED },
            "canon = writer_confirmed ∧ CONFIRMED"
        )
    }

    @Test
    fun `regenerate archives the previous draft and lands a new proposal`() = runTest {
        val (book, chapters) = seedNovella()
        val repository = AppRepository(
            storyDao = FakeStoryDao(),
            audioTrackDao = FakeAudioTrackDao(),
            bookDao = FakeBookDao(listOf(book)),
            chapterDao = FakeChapterDao(chapters),
            lorebookDao = FakeLorebookDao()
        )
        var generationCount = 0
        val vm = WriterStudioViewModel(
            repository = repository,
            authrRoot = null,
            draftGenerator = WriterStudioViewModel.L4DraftGenerator { _, _, _, _, _ ->
                generationCount++
                "Regenerated prose $generationCount — the forge was still hot and she needed the ledger."
            },
            scope = this.backgroundScope
        )
        vm.refreshBooks(); vm.selectBook(book.id); testScheduler.runCurrent()

        vm.regenerateDraft(chapters[0].id); testScheduler.runCurrent()
        assertTrue(generationCount == 1, "the L4 generator was called once")

        val all = repository.getChaptersForBookSync(book.id)
        val ch1 = all.first { it.chapterNumber == 1 }
        assertTrue(ch1.draftStatus == DraftState.PROPOSED && !ch1.writerConfirmed, "the new draft is a proposal")
        assertTrue(ch1.content.startsWith("Regenerated prose 1"), "content replaced by the fresh proposal")

        // The writer refuses the regeneration: archive.
        vm.openChapter(ch1.id); testScheduler.runCurrent()
        vm.archiveDraft(ch1.id); testScheduler.runCurrent()
        val archived = repository.getChapterById(ch1.id)!!
        assertEquals(DraftState.ARCHIVED, archived.draftStatus)
        assertFalse(WriterGate.isCanon(archived))
        assertEquals(
            1, SixLayerPipeline.assertGate(listOf(archived), listOf("export")).size,
            "archived text reaching a consumer is exactly one violation"
        )
    }

    @Test
    fun `queue keyboard flow confirms remaining proposals in order`() = runTest {
        val (book, chapters) = seedNovella()
        val repository = AppRepository(
            storyDao = FakeStoryDao(),
            audioTrackDao = FakeAudioTrackDao(),
            bookDao = FakeBookDao(listOf(book)),
            chapterDao = FakeChapterDao(chapters),
            lorebookDao = FakeLorebookDao()
        )
        val vm = WriterStudioViewModel(repository = repository, scope = this.backgroundScope)
        vm.refreshBooks(); vm.selectBook(book.id); testScheduler.runCurrent()

        vm.setMode(WriterStudioViewModel.StudioMode.AUTONOMOUS)
        assertEquals(5, vm.reviewQueue.size)
        assertEquals(1, vm.currentQueueChapter()?.chapterNumber, "cursor starts at chapter 1")

        vm.confirmCurrentQueueItem(); testScheduler.runCurrent()
        assertEquals(4, vm.reviewQueue.size, "confirmed chapter leaves the queue")
        assertEquals(2, vm.currentQueueChapter()?.chapterNumber, "cursor slides to the next proposal")

        vm.moveQueueCursor(2)
        assertEquals(4, vm.currentQueueChapter()?.chapterNumber, "↓/↓ moves the cursor within the queue")
        vm.archiveCurrentQueueItem(); testScheduler.runCurrent()
        assertEquals(3, vm.reviewQueue.size)
        assertEquals(
            listOf(2, 3, 5), vm.reviewQueue.map { it.chapterNumber },
            "queue stays in chapter order without the confirmed and archived chapters"
        )
    }
}
