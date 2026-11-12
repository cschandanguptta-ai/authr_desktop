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
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.MasteringChain
import com.example.engine.audio.AcxAudit
import com.example.engine.audio.PronunciationPass
import com.example.ui.viewmodel.AudiobookViewModel
import com.example.ui.viewmodel.AudiobookViewModel.EngineOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Audiobook workbench state machine (D-13): the P4 pipeline reachable
 * from the UI — engine catalog with availability, the §7.3 pronunciation
 * scan → approve → stage flow, and one-action render through the real
 * renderer with fake synthesis/mastering/audit (the AudiobookRendererTest
 * fakes), Room-free (the PublishingWorkbenchTest pattern).
 */
class AudiobookWorkbenchTest {

    // ------------------------------------------------------------------
    // Room-free fakes (the PublishingWorkbenchTest pattern)
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

    private class FakeBookDao(initial: List<Book>) : BookDao {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial.associateBy { it.id })
        override fun getAllBooks(): Flow<List<Book>> = flowOf(emptyList())
        override suspend fun getBooksSync(): List<Book> = state.value.values.sortedBy { it.id }
        override suspend fun getBookById(bookId: Int): Book? = state.value[bookId]
        override fun getBookByIdFlow(bookId: Int): Flow<Book> = flowOf(state.value[bookId]!!)
        override suspend fun insertBook(book: Book): Long = book.id.toLong().also { state.value = state.value + (book.id to book) }
        override suspend fun updateBook(book: Book) { state.value = state.value + (book.id to book) }
        override suspend fun deleteBook(bookId: Int) { state.value = state.value - bookId }
    }

    private class FakeChapterDao(initial: List<Chapter>) : ChapterDao {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial.associateBy { it.id })
        override fun getChaptersForBook(bookId: Int): Flow<List<Chapter>> = flowOf(state.value.values.filter { it.bookId == bookId })
        override suspend fun getChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId }.sortedBy { it.chapterNumber }
        override suspend fun getProposedChaptersForBookSync(bookId: Int): List<Chapter> = state.value.values.filter { it.bookId == bookId && it.draftStatus == "PROPOSED" }
        override suspend fun getChapterById(chapterId: Int): Chapter? = state.value[chapterId]
        override suspend fun insertChapter(chapter: Chapter): Long = chapter.id.toLong().also { state.value = state.value + (chapter.id to chapter) }
        override suspend fun insertChapters(chapters: List<Chapter>) { state.value = state.value + chapters.associateBy { it.id } }
        override suspend fun updateChapter(chapter: Chapter) { state.value = state.value + (chapter.id to chapter) }
        override suspend fun deleteChapter(chapterId: Int) { state.value = state.value - chapterId }
        override suspend fun searchChapters(query: String): List<Chapter> = emptyList()
    }

    private class FakeLorebookDao(initial: List<Lorebook>) : LorebookDao {
        val entries = initial.toMutableList()
        override fun getLorebookForBook(bookId: Int): Flow<List<Lorebook>> = flowOf(entries.filter { it.bookId == bookId })
        override suspend fun getLorebookForBookSync(bookId: Int): List<Lorebook> = entries.filter { it.bookId == bookId }
        override suspend fun insertLorebook(lorebook: Lorebook): Long = 1
        override suspend fun insertLorebooks(lorebooks: List<Lorebook>) { entries += lorebooks }
        override suspend fun updateLorebook(lorebook: Lorebook) {}
        override suspend fun deleteLorebook(lorebookId: Int) { entries.removeAll { it.id == lorebookId } }
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
    // Engine fakes (the AudiobookRendererTest pattern)
    // ------------------------------------------------------------------

    /** Fake synthesizer: real WAV files of word-proportional duration. */
    private class FakeSynthesizer : AudiobookRenderer.Synthesizer {
        override val engineLabel = "FakeTTS"
        override val continuityMode = AudiobookRenderer.ContinuityMode.POCKET_STRUCTURAL
        override val temperature = 0.6
        val texts = mutableListOf<String>()
        override fun synthesize(text: String, outWav: File): AudiobookRenderer.SynthesisResult {
            val start = System.currentTimeMillis()
            texts += text
            outWav.parentFile?.mkdirs()
            val words = text.split(Regex("\\s+")).size
            val samples = (words * 0.4 * 22_050).toInt()
            writeToneWav(outWav, samples)
            return AudiobookRenderer.SynthesisResult(true, samples / 22_050.0, System.currentTimeMillis() - start)
        }
    }

    /** Fake mastering: copies the input, records every invocation. */
    private class FakeMastering : MasteringChain() {
        override fun master(
            input: File, output: File, ambientBed: File?, mp3: Boolean, timeoutMs: Long
        ): List<MasteringChain.StageResult> {
            output.parentFile?.mkdirs()
            input.copyTo(output, overwrite = true)
            return listOf(MasteringChain.StageResult("encode", true, output.name, output))
        }

        override fun concatWavs(wavs: List<File>, output: File, timeoutMs: Long): Boolean {
            output.parentFile?.mkdirs()
            java.io.FileOutputStream(output).use { fos -> wavs.forEach { it.inputStream().use { it.copyTo(fos) } } }
            return true
        }

        override fun encodeMp3(input: File, output: File, timeoutMs: Long): Boolean {
            output.parentFile?.mkdirs()
            input.copyTo(output, overwrite = true)
            return true
        }
    }

    /** Green audit fake: answers each measurement command with a passing value. */
    private fun fakeAudit() = AcxAudit(
        ffmpegPath = "/fake/ffmpeg", ffprobePath = "/fake/ffprobe",
        runner = { command, _ ->
            val cmd = command.joinToString(" ")
            val out = when {
                cmd.contains("ffprobe") && cmd.contains("codec_name") ->
                    "codec_name=mp3\nsample_rate=44100\nchannels=1\nbit_rate=192000\n"
                cmd.contains("ffprobe") && cmd.contains("format=duration") -> ""
                cmd.contains("astats=measure_perchannel=none") -> "Overall\nRMS level dB: -18.5\n"
                cmd.contains("ebur128") -> "True peak:\n    Peak:      -3.1 dBFS\n"
                cmd.contains("ametadata") -> "lavfi.astats.1.RMS_level=-72.0\n"
                else -> ""
            }
            Triple(0, out, "")
        }
    )

    /** Deterministic IPA source: coins one term, drops everything else. */
    private class FakePhonemizer : PronunciationPass.Phonemizer {
        override fun toIpa(text: String): String? =
            if (text.equals("Vaeldrin", ignoreCase = true)) "ˈvɛldrɪn" else null
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun audiobookRepo(bookId: Int = 51): Pair<AppRepository, Book> {
        val book = Book(
            id = bookId, title = "The Salt Meridian", genre = "Adventure",
            synopsis = "A surveyor maps a coast that keeps moving.",
            outline = "Two chapters, canon confirmed.", format = "Novel"
        )
        val chapters = (1..2).map { n ->
            Chapter(
                id = n, bookId = bookId, chapterNumber = n, title = "Chapter $n",
                content = "Vaeldrin crossed the salt marsh before dawn.\n\nThe tide turned twice before noon.",
                summary = "Summary $n", status = "Completed", writerConfirmed = true, draftStatus = "CONFIRMED"
            )
        }
        val lore = Lorebook(id = 1, bookId = bookId, key = "Vaeldrin", value = "The surveyor's coined name for the moving coastline")
        val repo = AppRepository(
            FakeStoryDao(), FakeAudioTrackDao(), FakeBookDao(listOf(book)),
            FakeChapterDao(chapters), FakeLorebookDao(listOf(lore)), FakeChekhovGunDao(),
            FakeQuestionDao(), FakeSerialArcDao()
        )
        return repo to book
    }

    private class FakeChekhovGunDao : com.example.data.local.ChekhovGunDao {
        override fun getGunsForBook(bookId: Int): Flow<List<com.example.data.model.ChekhovGun>> = flowOf(emptyList())
        override suspend fun getGunsForBookSync(bookId: Int): List<com.example.data.model.ChekhovGun> = emptyList()
        override suspend fun getUnresolvedGuns(bookId: Int): List<com.example.data.model.ChekhovGun> = emptyList()
        override suspend fun getGunById(gunId: Int): com.example.data.model.ChekhovGun? = null
        override suspend fun insertGun(gun: com.example.data.model.ChekhovGun): Long = 1
        override suspend fun insertGuns(newGuns: List<com.example.data.model.ChekhovGun>) {}
        override suspend fun updateGun(gun: com.example.data.model.ChekhovGun) {}
        override suspend fun deleteGun(gunId: Int) {}
    }

    private fun vmFor(
        repo: AppRepository,
        root: File?,
        pocketAvailable: Boolean = false
    ): AudiobookViewModel {
        val synth = FakeSynthesizer()
        return AudiobookViewModel(
            repository = repo,
            authrRoot = root,
            engineCatalog = listOf(
                EngineOption(
                    id = "pocket", label = "Pocket TTS",
                    continuity = "POCKET_STRUCTURAL",
                    available = pocketAvailable,
                    unavailableNote = if (pocketAvailable) null else "binary + pack required"
                ),
                EngineOption(id = "espeak", label = "espeak-ng", continuity = "ESPEAK_LOCAL", available = true)
            ),
            synthesizerFor = { synth },
            phonemizer = FakePhonemizer(),
            rendererFor = { s -> AudiobookRenderer(s, FakeMastering(), fakeAudit()) },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `scan proposes lorebook terms and approval stages the container entry`() = runTest {
        val (repo, book) = audiobookRepo()
        val root = kotlin.io.path.createTempDirectory("ab-test").toFile()
        try {
            val vm = vmFor(repo, root)
            vm.refreshBooks()
            vm.selectBook(book.id)

            vm.scanPronunciation()
            val candidate = vm.candidates.value.firstOrNull { it.term.equals("Vaeldrin", ignoreCase = true) }
            assertNotNull(candidate, "the lorebook coinage is a candidate: ${vm.candidates.value.map { it.term }}")
            assertEquals(PronunciationPass.Candidate.Source.LOREBOOK, candidate.source)

            vm.approve(candidate)
            assertTrue(vm.approved.value.any { it.term.equals("Vaeldrin", ignoreCase = true) })
            assertTrue(vm.candidates.value.isEmpty(), "approval consumes the candidate")
            vm.persistRulesNow() // the deterministic staging core (the UI path is async)
            val staged = File(root, "authr/${book.id}/audio/pronunciation_rules.json")
            assertTrue(staged.isFile, "approval stages immediately: $staged")
            assertTrue(staged.readText().contains("ˈvɛldrɪn"), "the IPA rides into the container entry")

            // reload on reselect: staged rules are authoritative
            vm.selectBook(book.id)
            assertEquals(1, vm.approved.value.size, "staged rules reload on reselect")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `one-action render runs the P4 pipeline green with approved rules`() = runTest {
        val (repo, book) = audiobookRepo()
        val root = kotlin.io.path.createTempDirectory("ab-render").toFile()
        try {
            val vm = vmFor(repo, root)
            vm.refreshBooks()
            vm.selectBook(book.id)
            vm.scanPronunciation()
            vm.approveAll()
            vm.persistRulesNow() // deterministic staging before the render

            vm.renderNow()
            val report = vm.report.value
            assertNotNull(report, "the render produced a report")
            assertTrue(report.allGreen, report.summary())
            assertEquals(2, report.chapters.size, "both canon chapters rendered")
            assertEquals("FakeTTS", report.engineLabel)
            assertTrue(report.chapters.all { it.mp3?.isFile == true && it.smilFile?.isFile == true })
            assertTrue(report.masterMp3?.isFile == true)
            assertTrue(report.synthesisSpeedFactor > 0.0)
            assertTrue(vm.status.value.contains("ALL GREEN"), vm.status.value)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `engine picker never selects an unavailable engine`() = runTest {
        val (repo, book) = audiobookRepo()
        val vm = vmFor(repo, null, pocketAvailable = false)
        vm.refreshBooks()
        vm.selectBook(book.id)

        vm.selectEngine("pocket")
        assertEquals("espeak", vm.selectedEngineId.value, "the unavailable engine is refused")
        assertTrue(vm.status.value.contains("binary"), "the refusal explains why: ${vm.status.value}")

        vm.selectEngine("espeak")
        assertEquals("espeak", vm.selectedEngineId.value)
    }

    @Test
    fun `render without a data root stays honest instead of writing`() = runTest {
        val (repo, book) = audiobookRepo()
        val vm = vmFor(repo, null)
        vm.refreshBooks()
        vm.selectBook(book.id)
        vm.renderNow()
        assertFalse(vm.rendering.value)
        assertTrue(vm.status.value.contains("data root"), vm.status.value)
    }
}

private fun writeToneWav(file: File, samples: Int) {
    val dataLen = samples * 2
    val out = java.io.DataOutputStream(java.io.FileOutputStream(file).buffered())
    out.writeBytes("RIFF"); out.writeIntLe(36 + dataLen); out.writeBytes("WAVE")
    out.writeBytes("fmt "); out.writeIntLe(16); out.writeShortLe(1); out.writeShortLe(1)
    out.writeIntLe(22_050); out.writeIntLe(44_100); out.writeShortLe(2); out.writeShortLe(16)
    out.writeBytes("data"); out.writeIntLe(dataLen)
    repeat(samples) { out.writeShortLe(if (it % 64 < 32) 9000 else -9000) }
    out.close()
}

private fun java.io.DataOutputStream.writeIntLe(v: Int) {
    write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
}

private fun java.io.DataOutputStream.writeShortLe(v: Int) {
    write(v and 0xFF); write((v shr 8) and 0xFF)
}
