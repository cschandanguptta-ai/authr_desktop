package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ResearchFact
import com.example.data.model.ResearchSource
import com.example.engine.context.ContextAssembler
import com.example.engine.rag.CitationIntegrityChecker
import com.example.engine.rag.RagEngine
import com.example.engine.serialization.GoldenSerial
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.0 port S3 validation (spec ADR-0012 = desktop ADR-0022): hybrid RAG
 * retrieval with epistemic isolation (Invariant 12) and citation
 * integrity (Invariant 17).
 */
class RagEngineTest {

    private val rag = RagEngine()

    private fun canonChapter(n: Int, content: String, confirmed: Boolean = true): Chapter = Chapter(
        bookId = 1, chapterNumber = n, title = "Chapter $n", content = content,
        summary = "Chapter $n summary.", status = "Completed",
        writerConfirmed = confirmed, draftStatus = if (confirmed) "CONFIRMED" else "PROPOSED"
    )

    // ------------------------------------------------------------------
    // 1. Epistemic isolation (Invariant 12) — the blocking property.

    @Test
    fun `epistemic isolation - corpus for chapter k excludes future and unconfirmed chapters`() {
        val chapters = listOf(
            canonChapter(1, "The harbor lights blinked twice. Mara counted seconds on the pier."),
            canonChapter(2, "Kell hated hospitals. The corridor smelled of lye and old decisions."),
            canonChapter(3, "Rain found every gap in Tomas's coat at the north gate.", confirmed = false),
            canonChapter(4, "Ilse kept one hand on the wall under the city, by the racing river."),
            canonChapter(5, "The archive kept its promise: dust and a ledger with her mother's hand.", confirmed = false)
        )
        val corpus = rag.buildCorpus(chapters, maxChapter = 3)
        // Only chapters 1-2 qualify (≤ 3 AND confirmed).
        assertTrue(corpus.all { it.chapterNumber in 1..2 }, "corpus must contain only confirmed chapters ≤ 3: ${corpus.map { it.chapterNumber }}")
        assertEquals(setOf(1, 2), corpus.map { it.chapterNumber }.toSet())
        // The defensive re-filter in retrieve() also blocks a poisoned corpus.
        val poisoned = rag.buildCorpus(chapters, maxChapter = 5) // includes 4
        // Query terms that match ch.1 AND ch.4: both rank, but only ch.1
        // is retrievable at currentChapter = 2.
        val results = rag.retrieve(poisoned, "harbor lights river wall", currentChapter = 2, maxTokens = 10_000)
        assertTrue(
            results.all { it.chunk.sourceType == "research" || it.chunk.chapterNumber <= 2 },
            "retrieve() must not return chapters > current even from an unfiltered corpus"
        )
        assertTrue(results.isNotEmpty() && results.all { it.chunk.chapterNumber == 1 },
            "the isolated slice is retrievable and the future chapter is excluded")
    }

    @Test
    fun `isolation holds under retrieval pressure - no future leak at any budget`() {
        val chapters = (1..10).map { n ->
            canonChapter(n, "Chapter $n distinctive scene number $n. " +
                (1..40).joinToString(" ") { "beat word $n $it flows onward." })
        }
        for (current in 1..10) {
            for (budget in listOf(50, 200, 1_000, 100_000)) {
                val corpus = rag.buildCorpus(chapters, maxChapter = 10)
                val results = rag.retrieve(corpus, "distinctive scene beat word", current, budget)
                assertTrue(
                    results.all { it.chunk.chapterNumber <= current },
                    "isolation leak at current=$current budget=$budget: ${results.map { it.chunk.chapterNumber }}"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. Hybrid retrieval quality.

    @Test
    fun `bm25 arm ranks the keyword-matching chapter first`() {
        val chapters = listOf(
            canonChapter(1, "The lighthouse keeper counted gulls every morning before the storm."),
            canonChapter(2, "The submarine dove past the thermocline into cold darkness."),
            canonChapter(3, "Margaret taught botany: the ferns unrolled in the greenhouse heat."),
            canonChapter(4, "The courtroom fell silent when the witness described the thermocline readings.")
        )
        val corpus = rag.buildCorpus(chapters)
        val ranked = rag.bm25Rank(corpus, "submarine thermocline")
        assertEquals(2, ranked.first().chapterNumber, "ch.2 is the exact-keyword match")
        assertTrue(ranked.map { it.chapterNumber }.contains(4), "ch.4 shares one term and also ranks")
    }

    @Test
    fun `vector arm catches topical neighbors that keyword search misses`() {
        // No lexical overlap between query and ch.2 — only the vector arm
        // can find it (hashed TF cosine over shared context words).
        val chapters = listOf(
            canonChapter(1, "Ledgers, invoices, receipts: the accountant's day was numbers."),
            canonChapter(2, "The flood came at night. Water rose over the fields and ruined the harvest completely.")
        )
        val corpus = rag.buildCorpus(chapters)
        val query = "flood water harvest fields"
        val fts = rag.bm25Rank(corpus, query)
        val vec = rag.cosineRank(corpus, query)
        assertTrue(vec.isNotEmpty(), "vector arm ranks the topical neighbor")
        assertEquals(2, vec.first().chapterNumber, "topical match wins on the vector arm")
        // The FTS arm may or may not rank it (some shared words); the
        // fused result must surface it.
        val fused = rag.retrieve(corpus, query, currentChapter = 3, maxTokens = 10_000)
        assertTrue(fused.isNotEmpty() && fused.first().chunk.chapterNumber == 2,
            "fused retrieval surfaces the topical match")
    }

    @Test
    fun `rrf fusion prefers chunks ranked well on both arms`() {
        val chapters = listOf(
            canonChapter(1, "The gate ledger and the sealed deed. Kira opened the counter-ledger."),
            canonChapter(2, "Bram kept the gate ledger. The dike gate stayed shut all winter."),
            canonChapter(3, "Unrelated travels through mountains and markets, far from any ledger.")
        )
        val corpus = rag.buildCorpus(chapters)
        val results = rag.retrieve(corpus, "gate ledger dike", currentChapter = 4, maxTokens = 10_000)
        assertTrue(results.isNotEmpty())
        // Ch.2 shares all three query terms; it must outrank ch.3 (no terms).
        val chNums = results.map { it.chunk.chapterNumber }
        assertTrue(!chNums.contains(3) || chNums.indexOf(3) > chNums.indexOf(2), "term-matching chapter outranks the unrelated one")
        // Provenance rides every result.
        results.forEach { r -> assertTrue(r.ftsRank != null || r.vectorRank != null, "arm provenance present") }
    }

    @Test
    fun `token budget bounds the returned surface`() {
        val chapters = (1..6).map { n ->
            canonChapter(n, (1..120).joinToString(" ") { "chapter $n sentence $it about the estate dispute and the dike." })
        }
        val corpus = rag.buildCorpus(chapters)
        val results = rag.retrieve(corpus, "estate dispute dike", currentChapter = 7, maxTokens = 300)
        val tokens = results.sumOf { rag.estimateTokens(it.chunk.text) }
        assertTrue(tokens <= 300 + 8, "retrieval surface ${tokens} exceeds budget 300")
        assertTrue(results.size < corpus.size, "budget must prune: ${results.size} of ${corpus.size} chunks")
    }

    @Test
    fun `chunks are sentence-aligned and near target size`() {
        val long = (1..60).joinToString(" ") { "Sentence number $it advances the plot steadily." }
        val chunks = rag.chunkParagraphs(long, targetWords = 30)
        assertTrue(chunks.size >= 3, "long text splits: ${chunks.size} chunks")
        chunks.forEach { (_, text) ->
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            assertTrue(words in 1..45, "chunk of $words words stays near the 30-word target")
            assertTrue(text.endsWith(".") || text.length < 200, "chunks end at sentence boundaries")
        }
    }

    @Test
    fun `research sources join the corpus and are retrievable`() {
        val source = ResearchSource(
            bookId = 1, sourceType = ResearchSource.TYPE_BOOK_REFERENCE,
            title = "A History of River Dikes (1998)",
            contentSnippet = "The 1998 flood overtopped the northern dike because the gate ledger recorded false maintenance dates."
        )
        val chapters = listOf(canonChapter(1, "Kira surveyed the estate and the old dike road."))
        val corpus = rag.buildCorpus(chapters, researchSources = listOf(source))
        assertEquals(1, corpus.count { it.sourceType == "research" }, "one research chunk")
        val results = rag.retrieve(corpus, "1998 flood dike gate ledger maintenance", currentChapter = 2, maxTokens = 10_000)
        assertTrue(
            results.any { it.chunk.sourceType == "research" },
            "the research fact surface is retrievable alongside canon"
        )
    }

    // ------------------------------------------------------------------
    // 3. Layer-7 wiring in the context assembler.

    @Test
    fun `assembler layer 7 uses rag retrieval when wired`() = runBlocking {
        val book = GoldenSerial.book()
        val chapters = GoldenSerial.chapters(bookId = 301)
        val target = chapters.last()
        val assembler = ContextAssembler(com.example.engine.context.SummaryCacheService(), dao = null)
        val model = ContextAssembler.ModelSpec("test-class", 128_000, 16_000)

        val ctx = assembler.assemble(
            book = book, confirmed = chapters.filter { it.chapterNumber < 15 },
            targetChapter = target,
            stage = ContextAssembler.StageKey.L4_SCENE_COMPOSER,
            model = model,
            personaCard = "PERSONA card",
            lorebook = listOf(com.example.data.model.Lorebook(bookId = 301, key = "dike gate", value = "Iron, cold.")),
            ragEngine = rag
        )
        // The retrieved layer carries provenance-attributed chunks, not
        // the plain lorebook list.
        val retrieved = ctx.layerTokens.getValue(ContextAssembler.Layer.RETRIEVED)
        assertTrue(retrieved > 0, "layer 7 populated")
        assertTrue(ctx.promptPrefix.contains("RETRIEVED]"), "layer header present")
        // Invariant 12 through the whole stack: no chunk from a chapter
        // beyond the target ever rides the assembled prefix.
        assertTrue(
            Regex("""\[chapter ch\.(\d+)""").findAll(ctx.promptPrefix).all { it.groupValues[1].toInt() <= 15 },
            "no future-chapter chunk in the retrieved layer"
        )
    }

    // ------------------------------------------------------------------
    // 4. Citation integrity (Invariant 17).

    private fun fact(id: Int, sourceId: Int, text: String, refs: String?, verified: Boolean, confidence: Double = 0.9) =
        ResearchFact(
            bookId = 1, sourceId = sourceId, factText = text, chapterRefs = refs,
            verified = verified, confidence = confidence
        )

    @Test
    fun `citation gate blocks on unverified cited facts`() {
        val sources = listOf(
            ResearchSource(id = 1, bookId = 1, sourceType = "url", title = "Flood Commission Report", url = "https://example.gov/flood"),
            ResearchSource(id = 2, bookId = 1, sourceType = "book_reference", title = "Contested Memoirs", reliability = ResearchSource.RELIABILITY_DISPUTED)
        )
        val facts = listOf(
            fact(1, 1, "The 1998 flood overtopped the dike on March 3.", "[1,4]", verified = true),
            fact(2, 1, "Maintenance logs were falsified 1995-1997.", "[4]", verified = false),
            fact(3, 2, "The mayor ordered the gate closed.", "[2,4]", verified = true) // disputed source, cited by ch.4
        )
        val report = CitationIntegrityChecker().check(4, facts, sources)
        assertTrue(!report.passed, "unverified + disputed citations block the export gate")
        assertEquals(1, report.verifiedCount)
        // f2 (cited ch.4, unverified) is the unverified count; f3 blocks via
        // the disputed-source check while being fact-verified.
        assertEquals(2, report.unverifiedCount)
        val reasons = report.findings.filter { it.blocking }.map { it.reason }
        assertTrue(reasons.any { it.contains("not verified") })
        assertTrue(reasons.any { it.contains("DISPUTED") })
        // Chapter 1 sees only its own citations: fact 1 is verified there.
        val ch1 = CitationIntegrityChecker().check(1, facts, sources)
        assertTrue(ch1.passed, "ch.1 citations are clean")
    }

    @Test
    fun `citation gate passes clean books and warns on low confidence`() {
        val sources = listOf(
            ResearchSource(bookId = 1, sourceType = "note", title = "Author's field notes")
        )
        val facts = listOf(
            fact(1, 1, "Verified fact.", "[2]", verified = true, confidence = 0.95),
            fact(2, 1, "Shaky verification.", "[3]", verified = true, confidence = 0.3),
            fact(3, 1, "Uncited and unverified — out of scope.", null, verified = false)
        )
        val checker = CitationIntegrityChecker()
        val report = checker.checkBook(facts, sources)
        assertTrue(report.passed, "no blocking findings: the gate passes")
        assertEquals(2, report.verifiedCount, "the cited facts are verified")
        val warnings = report.findings.filter { !it.blocking }
        assertEquals(1, warnings.size, "low-confidence verified fact warns")
        assertTrue(warnings.first().factText.contains("Shaky"))
        // chapterRefs parsing: JSON array, blank, null.
        assertEquals(listOf(1, 4), checker.parseChapterRefs("[1,4]"))
        assertEquals(emptyList(), checker.parseChapterRefs(null))
        assertEquals(emptyList(), checker.parseChapterRefs(""))
    }
}
