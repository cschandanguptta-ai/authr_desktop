import com.example.data.model.ResearchFact
import com.example.data.model.ResearchSource
import com.example.engine.rag.CitationIntegrityChecker
import com.example.engine.rag.RagEngine
import com.example.data.model.Chapter

fun canonChapter(n: Int, content: String, confirmed: Boolean = true): Chapter = Chapter(
    bookId = 1, chapterNumber = n, title = "Chapter $n", content = content,
    summary = "Chapter $n summary.", status = "Completed",
    writerConfirmed = confirmed, draftStatus = if (confirmed) "CONFIRMED" else "PROPOSED"
)

fun main() {
    // --- Citation probe
    val sources = listOf(
        ResearchSource(id = 1, bookId = 1, sourceType = "url", title = "Flood Commission Report", url = "https://example.gov/flood"),
        ResearchSource(id = 2, bookId = 1, sourceType = "book_reference", title = "Contested Memoirs", reliability = ResearchSource.RELIABILITY_DISPUTED)
    )
    val facts = listOf(
        ResearchFact(bookId = 1, sourceId = 1, factText = "f1", chapterRefs = "[1,4]", verified = true, confidence = 0.9),
        ResearchFact(bookId = 1, sourceId = 1, factText = "f2", chapterRefs = "[4]", verified = false, confidence = 0.9),
        ResearchFact(bookId = 1, sourceId = 2, factText = "f3", chapterRefs = "[2]", verified = true, confidence = 0.9)
    )
    val report = CitationIntegrityChecker().check(4, facts, sources)
    println("CIT: passed=${report.passed} verified=${report.verifiedCount} unverified=${report.unverifiedCount}")
    report.findings.forEach { println("  -> [${if (it.blocking) "BLOCK" else "warn"}] ${it.factText}: ${it.reason}") }

    // --- Retrieval probe
    val chapters = listOf(
        canonChapter(1, "The harbor lights blinked twice. Mara counted seconds on the pier."),
        canonChapter(2, "Kell hated hospitals. The corridor smelled of lye and old decisions."),
        canonChapter(3, "Rain found every gap in Tomas's coat at the north gate.", confirmed = false),
        canonChapter(4, "Ilse kept one hand on the wall under the city, by the racing river."),
        canonChapter(5, "The archive kept its promise: dust and a ledger with her mother's hand.", confirmed = false)
    )
    val rag = RagEngine()
    val poisoned = rag.buildCorpus(chapters, maxChapter = 5)
    println("POISONED corpus chapters: ${poisoned.map { it.chapterNumber }}")
    val results = rag.retrieve(poisoned, "river wall city Ilse", currentChapter = 2, maxTokens = 10_000)
    println("RESULTS: ${results.map { it.chunk.chapterNumber to it.chunk.title }}")
    val fts = rag.bm25Rank(poisoned, "river wall city Ilse")
    println("FTS RANK: ${fts.map { it.chapterNumber }}")
    val vec = rag.cosineRank(poisoned, "river wall city Ilse")
    println("VEC RANK: ${vec.map { it.chapterNumber }}")
}
