package com.example

import com.example.container.AuthrContainer
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
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import com.example.desktopbridge.EpubCheckLocator
import com.example.engine.DraftState
import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.WorkbenchStore
import com.example.engine.serialization.GoldenSerial
import com.example.ui.viewmodel.PublishingViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Publishing workbench state machine (D-15): workbench seeding from the
 * project record (OQ-9), staging into the extracted container layout,
 * reload-on-reselect, and the ONE-ACTION retail package driven end to end
 * from Room-free fakes — container export → import audit → packageRetail,
 * exactly the PublishingGate order, now reachable from the UI.
 */
class PublishingWorkbenchTest {

    // ------------------------------------------------------------------
    // Room-free fakes (the SerialDashboardTest pattern)
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
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial.associateBy { it.id })
        override fun getAllBooks(): Flow<List<Book>> = flowOf(emptyList())
        override suspend fun getBooksSync(): List<Book> = state.value.values.sortedBy { it.id }
        override suspend fun getBookById(bookId: Int): Book? = state.value[bookId]
        override fun getBookByIdFlow(bookId: Int): Flow<Book> = flowOf(state.value[bookId]!!)
        override suspend fun insertBook(book: Book): Long = book.id.toLong().also { state.value = state.value + (book.id to book) }
        override suspend fun updateBook(book: Book) { state.value = state.value + (book.id to book) }
        override suspend fun deleteBook(bookId: Int) { state.value = state.value - bookId }
    }

    class FakeChapterDao(initial: List<Chapter>) : ChapterDao {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial.associateBy { it.id })
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

    private class FakeChekhovGunDao : com.example.data.local.ChekhovGunDao {
        val guns = mutableListOf<ChekhovGun>()
        override fun getGunsForBook(bookId: Int): Flow<List<ChekhovGun>> = flowOf(guns.filter { it.bookId == bookId })
        override suspend fun getGunsForBookSync(bookId: Int): List<ChekhovGun> = guns.filter { it.bookId == bookId }
        override suspend fun getUnresolvedGuns(bookId: Int): List<ChekhovGun> =
            guns.filter { it.bookId == bookId && it.status in listOf("INTRODUCED", "ESCALATED", "ORPHANED") }
        override suspend fun getGunById(gunId: Int): ChekhovGun? = guns.firstOrNull { it.id == gunId }
        override suspend fun insertGun(gun: ChekhovGun): Long = guns.size.toLong().also { guns.add(gun.copy(id = guns.size + 1)) }
        override suspend fun insertGuns(newGuns: List<ChekhovGun>) { newGuns.forEach { guns.add(it.copy(id = guns.size + 1)) } }
        override suspend fun updateGun(gun: ChekhovGun) { val i = guns.indexOfFirst { it.id == gun.id }; if (i >= 0) guns[i] = gun }
        override suspend fun deleteGun(gunId: Int) { guns.removeAll { it.id == gunId } }
    }

    private class FakeQuestionDao : QuestionDao {
        override fun getQuestionsForBook(bookId: Int): Flow<List<QuestionEntry>> = flowOf(emptyList())
        override suspend fun getQuestionsForBookSync(bookId: Int): List<QuestionEntry> = emptyList()
        override suspend fun getOverdueProgressiveQuestions(bookId: Int, currentChapter: Int): List<QuestionEntry> = emptyList()
        override suspend fun getQuestionById(questionId: Int): QuestionEntry? = null
        override suspend fun insertQuestion(question: QuestionEntry): Long = 1
        override suspend fun insertQuestions(newQuestions: List<QuestionEntry>) {}
        override suspend fun updateQuestion(question: QuestionEntry) {}
        override suspend fun deleteQuestion(questionId: Int) {}
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

    private fun novelRepo(bookId: Int = 41): Pair<AppRepository, Book> {
        val book = Book(
            id = bookId, title = "The Copper Meridian", genre = "Adventure",
            synopsis = "A cartographer charts a coastline that keeps moving.",
            outline = "Four chapters, canon confirmed.", format = "Novel"
        )
        val chapters = (1..4).map { n ->
            Chapter(
                id = n, bookId = bookId, chapterNumber = n, title = "Chapter $n",
                content = "The meridian shifted again. Paragraph two of chapter $n keeps the render honest.",
                summary = "Summary $n", status = "Completed", writerConfirmed = true, draftStatus = "CONFIRMED"
            )
        }
        val repo = AppRepository(
            FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(chapters), FakeLorebookDao(), FakeChekhovGunDao(),
            FakeQuestionDao(), FakeSerialArcDao()
        )
        return repo to book
    }

    private fun vmFor(repo: AppRepository, root: File?): PublishingViewModel =
        PublishingViewModel(repo, authrRoot = root,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `workbench seeds from the project record when never staged`() = runTest {
        val (repo, book) = novelRepo()
        val vm = vmFor(repo, null)
        vm.refreshBooks()
        vm.selectBook(book.id)

        val wb = vm.workbench.value
        assertNotNull(wb)
        assertEquals(book.title, wb.title, "the seed title comes from the project record")
        assertEquals(book.synopsis, wb.description, "the blurb starts from the synopsis")
        assertTrue(wb.keywords.isEmpty(), "the seed carries no invented keywords")
        assertTrue(vm.status.value.contains("seeded"), "the seed is disclosed, not silent")
    }

    @Test
    fun `edited workbench stages into the container layout and survives reselection`() = runTest {
        val (repo, book) = novelRepo()
        val root = createTempDir("pw-test")
        try {
            val vm = vmFor(repo, root)
            vm.refreshBooks()
            vm.selectBook(book.id)
            vm.editWorkbench {
                it.copy(
                    subtitle = "Charts of the Moving Coast",
                    keywords = listOf("cartography", "adventure", "sea fantasy"),
                    listPriceUsd = 6.99,
                    isbn = "978-1-11111-111-1",
                    blurbVariants = mapOf("royal_road" to "The coast refuses to stay still.")
                )
            }
            assertTrue(vm.dirty.value, "edits mark the workbench dirty")
            vm.saveWorkbenchNow()
            assertFalse(vm.dirty.value, "saving clears the dirty flag")

            // OQ-9 location: authr/<bookId>/metadata/kdp_workbench.json
            val staged = File(File(root, "authr"), book.id.toString())
            val reloaded = WorkbenchStore.read(staged)
            assertNotNull(reloaded)
            assertEquals("Charts of the Moving Coast", reloaded.subtitle)
            assertEquals(listOf("cartography", "adventure", "sea fantasy"), reloaded.keywords)
            assertEquals(6.99, reloaded.listPriceUsd)
            assertTrue(reloaded.blurbVariants.containsKey("royal_road"), "the platform variant stages too")

            // reselect reloads the staged workbench (not a fresh seed)
            vm.selectBook(book.id)
            assertEquals("978-1-11111-111-1", vm.workbench.value?.isbn, "reselection loads the staged workbench")
            assertTrue(vm.status.value.contains("loaded from the staged container"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `one action packages the novel end to end with the container audit`() = runTest {
        val (repo, book) = novelRepo()
        val root = createTempDir("pw-pkg")
        try {
            val vm = vmFor(repo, root)
            vm.refreshBooks()
            vm.selectBook(book.id)
            vm.editWorkbench { it.copy(author = "V. Author", listPriceUsd = 5.99) }
            vm.saveWorkbenchNow()
            vm.packageRetailNow()

            val oc = vm.outcome.value
            assertNotNull(oc, "the one action produced an outcome")
            assertTrue(oc.containerFile.name.endsWith(".authr"))
            assertTrue(oc.containerBytes > 0, "the container is a real file")
            assertEquals(2, oc.importedSchemaVersion, "the container imports at schema v2")

            val result = oc.result
            assertNotNull(result.shipReport)
            assertTrue(result.shipReport.exists() && result.shipReport.length() > 0, "ship-report.md written")
            assertTrue(File(result.outDir, "metadata.json").exists(), "metadata.json written")
            assertTrue(result.epubResults.isNotEmpty() && result.epubResults[0].chapterCount == 4, "the EPUB carries the four canon chapters")
            assertNotNull(result.printResult, "the print interior rendered")

            if (EpubCheckLocator.isAvailable()) {
                assertTrue(result.clean, "with epubcheck present the package is clean: ${result.gateVerdicts.filter { !it.passed }}")
                assertTrue(vm.status.value.contains("CLEAN"))
            } else {
                assertFalse(result.clean, "without epubcheck the package is honestly NOT clean")
                assertTrue(result.gateVerdicts.any { it.status == "UNVERIFIED" }, "the UNVERIFIED verdict is disclosed")
            }
            assertTrue(result.disclosures.none { it.contains("Workbench never edited") },
                "an edited workbench is never disclosed as derived-from-record")
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `serial package rides the arc volumes and cadence disclosure`() = runTest {
        val book = GoldenSerial.book().copy(id = 82)
        // GoldenSerial leaves entity ids at 0 — re-id like SerialDashboardTest,
        // or the FakeChapterDao's associateBy(id) collapses the 15 chapters to 1.
        val chapters = GoldenSerial.chapters(book.id).mapIndexed { i, ch -> ch.copy(id = 300 + i) }
        val arcDao = FakeSerialArcDao()
        arcDao.insertArc(SerialArc(bookId = book.id, arcIndex = 1, conflict = "arc one", phaseBounds = "{}"))
        arcDao.insertArc(SerialArc(bookId = book.id, arcIndex = 2, conflict = "arc two", phaseBounds = "{}"))
        val withArcs = chapters.map { ch -> if (ch.chapterNumber <= 7) ch.copy(arcId = 1) else ch.copy(arcId = 2) }
        val repo = AppRepository(
            FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(withArcs), FakeLorebookDao(), FakeChekhovGunDao(),
            FakeQuestionDao(), arcDao
        )
        val root = createTempDir("pw-serial")
        try {
            val vm = vmFor(repo, root)
            vm.refreshBooks()
            vm.selectBook(book.id)
            vm.packageRetailNow()

            val oc = vm.outcome.value
            assertNotNull(oc)
            if (EpubCheckLocator.isAvailable()) {
                assertTrue(oc.result.clean, "serial package clean: ${oc.result.gateVerdicts.filter { !it.passed }}")
            }
            assertEquals(2, oc.result.epubResults.size, "two micro-arc volumes bundled from the staged arcs")
            assertTrue(
                oc.result.disclosures.any { it.contains("volume") && it.contains("micro-arc") },
                "the volume bundling is disclosed"
            )
            val report = oc.result.shipReport.readText()
            assertTrue(report.contains("Buffer:"), "the ship report carries the buffer line for serials")
        } finally { root.deleteRecursively() }
    }
}
