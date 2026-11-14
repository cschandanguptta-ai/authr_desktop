package com.example

import com.example.data.local.EditorialMemoryDao
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.EditorialMemory
import com.example.engine.pipeline.EditorialMemoryStore
import com.example.engine.pipeline.Finding
import com.example.engine.pipeline.ManuscriptQaSuite
import com.example.engine.pipeline.PipelineStage
import com.example.engine.pipeline.RetentionAnalyzer
import com.example.engine.pipeline.StageGates
import com.example.engine.pipeline.StageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ADR-0018 pipeline engine golden tests: the editorial memory round-trips
 * losslessly, deterministic gates block weak content, weak content never
 * auto-advances, and the retention/QA analyzers measure a planted corpus
 * the way the gates expect (proved, not asserted).
 *
 * JVM-pure: the fake DAO mirrors the Room-free pattern of the other suites
 * (D-06's shared-fixture doctrine, per-class here deliberately — see
 * tech-debt).
 */
class PipelineEngineTest {

    /** In-memory EditorialMemoryDao double. */
    private class FakeEditorialMemoryDao : EditorialMemoryDao {
        val rows = MutableStateFlow<Map<Pair<Int, String>, EditorialMemory>>(emptyMap())

        override suspend fun getSlot(bookId: Int, slot: String): EditorialMemory? = rows.value[bookId to slot]

        override suspend fun getAllForBook(bookId: Int): List<EditorialMemory> =
            rows.value.values.filter { it.bookId == bookId }

        override fun observeAllForBook(bookId: Int): Flow<List<EditorialMemory>> =
            kotlinx.coroutines.flow.flow { emit(rows.value.values.filter { it.bookId == bookId }) }

        override suspend fun upsert(row: EditorialMemory) {
            val map = rows.value.toMutableMap()
            map[row.bookId to row.slot] = row
            rows.value = map
        }

        override suspend fun upsertAll(rows: List<EditorialMemory>) = rows.forEach { upsert(it) }

        override suspend fun clearSlot(bookId: Int, slot: String) {
            val map = rows.value.toMutableMap(); map.remove(bookId to slot); rows.value = map
        }

        override suspend fun clearBook(bookId: Int) {
            val map = rows.value.toMutableMap()
            map.keys.filter { it.first == bookId }.forEach { map.remove(it) }
            rows.value = map
        }
    }

    private fun store() = EditorialMemoryStore(FakeEditorialMemoryDao())

    private fun canonChapter(n: Int, content: String, confirmed: Boolean = true) = Chapter(
        id = n, bookId = 1, chapterNumber = n, title = "Chapter $n",
        content = content, summary = "", status = if (confirmed) "Completed" else "Draft",
        writerConfirmed = confirmed,
        draftStatus = if (confirmed) "CONFIRMED" else "PROPOSED"
    )

    // ------------------------------------------------------------------
    // 1. Editorial memory round-trips losslessly

    @Test
    fun `memory slots round-trip losslessly`() = runBlocking {
        val mem = store()
        mem.writeMarketFindings(1, EditorialMemoryStore.MarketFindings(
            genreExpectations = listOf("magic has a price", "slow-burn romance"),
            readerDemographics = "25-40, mostly female, reads on phones at night",
            comparableTitles = listOf("The Cruel Prince", "Uprooted"),
            marketGaps = listOf("no cozy dark academia with adult protagonists"),
            commercialOpportunities = listOf("series starter with strong audiobook hook"),
            summary = "Adult dark academia is underserved."
        ))
        val back = mem.marketFindings(1)
        assertEquals(2, back.genreExpectations.size)
        assertEquals("Adult dark academia is underserved.", back.summary)
        assertTrue(back.comparableTitles.contains("Uprooted"))
    }

    @Test
    fun `decisions append in order and issues resolve`() = runBlocking {
        val mem = store()
        mem.appendDecision(1, PipelineStage.MARKET_RESEARCH, "Market analysis adopted", "underserved gap")
        mem.appendDecision(1, PipelineStage.CONCEPT, "Concept 2 selected", "strongest differentiator")
        assertEquals(2, mem.decisions(1).size)
        assertEquals("Concept 2 selected", mem.decisions(1)[1].decision)

        mem.upsertIssue(1, EditorialMemoryStore.IssueEntry(kind = "PLOT_HOLE", severity = "HARD", description = "gun never fires"))
        mem.upsertIssue(1, EditorialMemoryStore.IssueEntry(kind = "REPETITION", severity = "WARNING", description = "echo phrase"))
        assertEquals(2, mem.issues(1).filter { it.status == "OPEN" }.size)
        mem.resolveIssue(1, "PLOT_HOLE", "gun never fires")
        assertEquals(1, mem.issues(1).filter { it.status == "OPEN" }.size)
        assertEquals(1, mem.issues(1).filter { it.status == "RESOLVED" }.size)
    }

    @Test
    fun `corrupt payload fails loud instead of silently degrading`() = runBlocking {
        val dao = FakeEditorialMemoryDao()
        val mem = EditorialMemoryStore(dao)
        dao.upsert(EditorialMemory(bookId = 1, slot = "market_findings", payload = "{not json"))
        val threw = try { mem.marketFindings(1); false } catch (e: IllegalStateException) { true }
        assertTrue(threw, "payload drift must fail loud (invariant: no silent degradation)")
    }

    // ------------------------------------------------------------------
    // 2. Stage lifecycle: gate red blocks, approval only after green

    @Test
    fun `stage states default to NOT_STARTED and fill gaps`() = runBlocking {
        val mem = store()
        val states = mem.fullStageStates(1)
        assertEquals(9, states.size)
        assertTrue(states.values.all { it.status == "NOT_STARTED" })
    }

    @Test
    fun `a red gate records failure and blocks advancement`() = runBlocking {
        val mem = store()
        // Empty findings → hard failures
        val verdict = StageGates.marketResearch(null)
        assertFalse(verdict.passed)
        assertTrue(verdict.hardFailures.isNotEmpty())

        mem.recordGate(1, PipelineStage.MARKET_RESEARCH, verdict)
        mem.setStageStatus(1, PipelineStage.MARKET_RESEARCH, StageStatus.BLOCKED)
        assertFalse(mem.isStageComplete(1, PipelineStage.MARKET_RESEARCH))
        // Approval must refuse while the gate is red — this is the store-level
        // contract the orchestrator's approveStage enforces.
        val s = mem.fullStageStates(1)[PipelineStage.MARKET_RESEARCH]!!
        assertFalse(s.gatePassed)
    }

    @Test
    fun `a green gate plus approval completes the stage`() = runBlocking {
        val mem = store()
        mem.writeMarketFindings(1, EditorialMemoryStore.MarketFindings(
            genreExpectations = listOf("e1", "e2", "e3", "e4"),
            marketGaps = listOf("g1", "g2"),
            comparableTitles = listOf("c1", "c2"),
            commercialOpportunities = listOf("o1"),
            summary = "solid summary"
        ))
        val verdict = StageGates.marketResearch(mem.marketFindings(1))
        assertTrue(verdict.passed, "well-formed findings must pass: ${verdict.hardFailures}")
        mem.recordGate(1, PipelineStage.MARKET_RESEARCH, verdict)
        mem.setStageStatus(1, PipelineStage.MARKET_RESEARCH, StageStatus.APPROVED)
        assertTrue(mem.isStageComplete(1, PipelineStage.MARKET_RESEARCH))
    }

    // ------------------------------------------------------------------
    // 3. Gate criteria: each gate blocks its characteristic weakness

    @Test
    fun `concept gate blocks empty differentiator`() {
        val verdict = StageGates.concept(EditorialMemoryStore.Positioning(
            premise = "A librarian discovers the books whisper back",
            differentiator = "", // the hole
            sellingProposition = "sp", promiseToReader = "pr", avoidList = listOf("chosen one")
        ))
        assertFalse(verdict.passed)
        assertTrue(verdict.hardFailures.any { it.contains("differentiator", ignoreCase = true) })
    }

    @Test
    fun `worldbuilding gate blocks empty bibles`() {
        val verdict = StageGates.worldbuilding(Book(title = "T", genre = "G", synopsis = "", outline = ""))
        assertFalse(verdict.passed)
        assertEquals(3, verdict.hardFailures.size)
    }

    @Test
    fun `manuscript gate blocks empty canon and empty chapters`() {
        val book = Book(title = "T", genre = "G", synopsis = "", outline = "o", wordMin = 1000, wordMax = 2000)
        val empty = StageGates.manuscript(emptyList(), book)
        assertFalse(empty.passed)

        val blankChapter = canonChapter(1, "")
        val withBlank = StageGates.manuscript(listOf(blankChapter), book)
        assertFalse(withBlank.passed)
        assertTrue(withBlank.hardFailures.any { it.contains("empty content") })
    }

    @Test
    fun `editorial gate blocks unresolved MAJOR findings and zero passes`() {
        val zero = StageGates.editorial(majorFindingsOpen = 0, passesRecorded = 0)
        assertFalse(zero.passed)

        val majors = StageGates.editorial(majorFindingsOpen = 2, passesRecorded = 3)
        assertFalse(majors.passed)

        val good = StageGates.editorial(majorFindingsOpen = 0, passesRecorded = 5)
        assertTrue(good.passed)
    }

    @Test
    fun `publication prep gate enforces the KDP package shape`() {
        val bad = StageGates.publicationPrep(titles = 0, blurbWords = 40, keywords = 3, categories = 0)
        assertFalse(bad.passed)
        assertTrue(bad.hardFailures.size >= 3)

        val good = StageGates.publicationPrep(titles = 3, blurbWords = 180, keywords = 7, categories = 3)
        assertTrue(good.passed)
    }

    // ------------------------------------------------------------------
    // 4. Retention analyzer: planted corpus measures as designed

    @Test
    fun `retention analyzer rewards a strong chapter over a weak one`() {
        val strong = canonChapter(1, """
            "Get down!" Mara screamed, slamming the door as the arrow splintered the frame.
            Why had the archive led them here? She ran, knife in hand, while the tower burned behind them.
            "The ledger was a trap," Kell said. "Someone knew we would come." Nobody knew who.
            If they stopped now, everything her mother died for would burn with the records.
            "Then we find them first." The floor collapsed, and they fell into the dark.
        """.trimIndent() + " " + (1..120).joinToString(" ") { "\"Dialogue beat $it,\" she said — and the danger grew." })

        val weak = canonChapter(1, (1..200).joinToString(" ") { "It was a quiet morning in the village where nothing happened." })

        val strongScore = RetentionAnalyzer.analyzeChapter(strong)
        val weakScore = RetentionAnalyzer.analyzeChapter(weak)
        assertTrue(strongScore.retentionIndex > weakScore.retentionIndex,
            "strong=${strongScore.retentionIndex} weak=${weakScore.retentionIndex}")
        assertTrue(strongScore.hookScore > weakScore.hookScore)
        assertTrue(strongScore.tensionScore > weakScore.tensionScore)
    }

    @Test
    fun `engagement gate blocks a manuscript below the retention floor`() {
        val weakChapters = (1..5).map { n ->
            canonChapter(n, (1..150).joinToString(" ") { "It was a quiet morning in the village where nothing happened at all." })
        }
        val report = RetentionAnalyzer.analyze(weakChapters)
        val verdict = StageGates.engagement(report)
        assertFalse(verdict.passed, "quiet filler must not pass the engagement gate (index=${report.overallIndex})")
    }

    // ------------------------------------------------------------------
    // 5. QA suite: planted defects are found; clean text passes

    @Test
    fun `qa suite flags unresolved chekhov guns as HARD`() {
        val chapters = (1..4).map { n -> canonChapter(n, "Chapter $n content. " + "Story text with dialogue \"beats\" and motion.".repeat(10)) }
        val gun = com.example.data.model.ChekhovGun(
            bookId = 1, name = "the silver ledger", description = "planted in ch.1",
            status = "INTRODUCED", introducedChapter = 1,
            quote = "q", severity = "MAJOR"
        )
        val report = ManuscriptQaSuite.run(chapters, listOf(gun))
        assertFalse(report.passed)
        assertTrue(report.findings.any { it.kind == "PLOT_HOLE" && it.severity == Finding.Severity.HARD && it.message.contains("silver ledger") })
    }

    @Test
    fun `qa suite flags cross-chapter repetition`() {
        val repeated = "She narrowed her eyes at the shadow in the doorway and said nothing at all about it."
        val chapters = (1..4).map { n ->
            canonChapter(n, "Chapter $n. " + repeated + " " + (1..80).joinToString(" ") { "varied narrative beat number $it moves along." })
        }
        val report = ManuscriptQaSuite.run(chapters)
        assertTrue(
            report.findings.any { it.kind == "REPETITION" },
            "the same 5-gram in 4 chapters must be flagged"
        )
    }

    @Test
    fun `qa suite passes healthy canon`() {
        // Genuinely distinct chapter text: every chapter uses different
        // sentences (the planted-corpus discipline from GoldenSerial).
        val chapterTexts = listOf(
            "The harbor lights blinked twice, then died. Mara counted the seconds in her head while the rope went slack. \"Now,\" she breathed, and the boat slid out under the pier. Far above, someone began to scream.",
            "Kell hated hospitals the way other men hated snakes. The corridor smelled of lye and old decisions. He passed the chapel without slowing; whatever he needed, it was not forgiveness. At the nurses' station, a bell rang three times.",
            "Rain found every gap in Tomas's coat. By the time he reached the gate, the letter inside his jacket was ruined — which meant the seal was unreadable, which meant, officially, it had never existed. The guard waved him through.",
            "Under the city, the river moved faster than the trams above it. Ilse kept one hand on the wall and one on her knife. Somewhere ahead, the passage forked, and one branch would be a door, and the other would be a mouth.",
            "The archive kept its promise: dust, silence, and a ledger with her mother's handwriting. Each page turned like a held breath. At the last entry the ink changed color. Mara read the date twice, then sat down on the cold floor.",
            "Dawn came up the color of an old bruise. The survivors gathered in the mill yard, counting heads, counting reasons. Nobody said the captain's name. The boats would burn at noon, and after that, the valley would have to be enough."
        )
        val chapters = chapterTexts.mapIndexed { n, text -> canonChapter(n + 1, text) }
        val report = ManuscriptQaSuite.run(chapters)
        assertTrue(report.passed, "expected green, got: ${report.findings.joinToString { it.message }}")
    }

    // ------------------------------------------------------------------
    // 6. Versioned memory: snapshots append and read back

    @Test
    fun `versions append and the latest is last`() = runBlocking {
        val mem = store()
        mem.appendVersion(1, EditorialMemoryStore.VersionEntry(label = "v1", scope = "manuscript", wordCount = 1000))
        mem.appendVersion(1, EditorialMemoryStore.VersionEntry(label = "v2", scope = "manuscript", wordCount = 2400))
        val v = mem.versions(1)
        assertEquals(listOf("v1", "v2"), v.map { it.label })
        assertEquals(2400, v.last().wordCount)
    }

    // ------------------------------------------------------------------
    // 7. Engagement snapshot persistence

    @Test
    fun `engagement snapshot round-trips with per-chapter detail`() = runBlocking {
        val mem = store()
        mem.writeEngagement(1, EditorialMemoryStore.EngagementSnapshot(
            chapters = listOf(
                EditorialMemoryStore.EngagementSnapshot.ChapterEngagement(
                    chapterNumber = 1, hookScore = 80.0, pacingScore = 60.0, tensionScore = 70.0,
                    curiosityScore = 65.0, endingScore = 95.0, retentionIndex = 76.5,
                    notes = listOf("note one")
                )
            ),
            overallIndex = 76.5
        ))
        val back = mem.engagement(1)
        assertEquals(1, back.chapters.size)
        assertEquals(76.5, back.overallIndex)
        assertEquals(listOf("note one"), back.chapters[0].notes)
        assertNotNull(back.computedAt)
        Unit
    }
}
