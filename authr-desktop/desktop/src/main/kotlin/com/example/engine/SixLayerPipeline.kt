package com.example.engine

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import java.io.File

/**
 * The six-layer drafting engine — layer contract orchestration (v3.0, plan
 * §4, Table 4).
 *
 * Each layer consumes the previous layer's confirmed output, and every
 * artifact names its producing layer, its owning agent, and its confirmation
 * state. The engine is writer-sovereign by construction: the pipeline's L6
 * stage feeds only canon into the ledger, and [assertGate] is the seeded-
 * project assertion primitive proving "no unconfirmed text reaches any
 * ledger or export" (the P1 exit gate, plan Table 33).
 */
object SixLayerPipeline {

    /** Artifact handed from one layer to the next, with its full provenance. */
    data class LayerResult(
        val layer: EngineLayer,
        val artifact: String,
        /** PROPOSED / CONFIRMED / ARCHIVED — canon state of the artifact. */
        val state: String,
        val writerConfirmed: Boolean
    ) {
        fun provenance(): String = "${layer.layer} (${layer.owningAgent}) — $artifact [$state]"
    }

    // ------------------------------------------------------------------
    // Layer contract
    // ------------------------------------------------------------------

    /** L1 artifacts are always writer-edited bible records; confirmed via intake. */
    fun l1BibleIntake(recordName: String, confirmed: Boolean) = LayerResult(
        EngineLayer.L1, "Bible record: $recordName",
        if (confirmed) DraftState.CONFIRMED else DraftState.PROPOSED, confirmed
    )

    /** L2 produces the approved outline; the Save/Approve flow is the gate. */
    fun l2StructurePlanner(outlineName: String, approved: Boolean) = LayerResult(
        EngineLayer.L2, "Structure: $outlineName",
        if (approved) DraftState.CONFIRMED else DraftState.PROPOSED, approved
    )

    /** L3 beat cards are confirmed per chapter before L4 may draft. */
    fun l3BeatDecomposer(chapterNumber: Int, beatsConfirmed: Boolean) = LayerResult(
        EngineLayer.L3, "Beat sheet: chapter $chapterNumber",
        if (beatsConfirmed) DraftState.CONFIRMED else DraftState.PROPOSED, beatsConfirmed
    )

    /**
     * L4 — the Scene Auto-Drafter (SCR-1 core). Its draft always lands as
     * PROPOSED; only the writer's Confirm Chapter promotes it to canon.
     */
    fun l4SceneAutoDrafter(chapter: Chapter) = LayerResult(
        EngineLayer.L4, "Prose draft: chapter ${chapter.chapterNumber}",
        chapter.draftStatus, chapter.writerConfirmed
    )

    /** L5 styles confirmed drafts; accept/edit/reject is the gate. */
    fun l5StylePolisher(chapterNumber: Int, accepted: Boolean) = LayerResult(
        EngineLayer.L5, "Styled revision: chapter $chapterNumber",
        if (accepted) DraftState.CONFIRMED else DraftState.PROPOSED, accepted
    )

    /** L6 annotates confirmed text into ledgers; flags are dismissable with a note. */
    fun l6ContinuityAuditor(chapter: Chapter, annotations: Int) = LayerResult(
        EngineLayer.L6, "Continuity annotations: chapter ${chapter.chapterNumber} ($annotations nodes)",
        if (WriterGate.isCanon(chapter)) DraftState.CONFIRMED else DraftState.PROPOSED,
        WriterGate.isCanon(chapter)
    )

    // ------------------------------------------------------------------
    // L6 — ledger update on the frozen text
    // ------------------------------------------------------------------

    /**
     * Upserts the annotator's CHEKHOV_GUN nodes into the Chekhov ledger.
     * Returns the guns written. Only canon chapters produce ledger entries —
     * callers that pass a PROPOSED/ARCHIVED chapter get an empty list.
     */
    fun buildLedgerEntries(chapter: Chapter, ast: ManuscriptAst): List<ChekhovGun> {
        if (!WriterGate.isCanon(chapter)) return emptyList()
        return ast.ofKind(AnnotationKind.CHEKHOV_GUN).map { node ->
            ChekhovGun(
                bookId = chapter.bookId,
                name = node.payload["gun"] ?: "Promise",
                description = node.message,
                status = "INTRODUCED",
                introducedChapter = chapter.chapterNumber,
                quote = node.payload["quote"] ?: node.excerpt,
                severity = "normal"
            )
        }
    }

    // ------------------------------------------------------------------
    // The P1 exit-gate primitive
    // ------------------------------------------------------------------

    /**
     * The sovereignty assertion over a full consumer surface. Returns every
     * violation ("Unconfirmed chapter N reached consumer(s) …"); an empty
     * list proves the gate held. Consumers named in the plan: chekhov_ledger,
     * timeline, export, compile, downstream layers (L5/L6).
     */
    fun assertGate(chapters: List<Chapter>, consumers: List<String>): List<String> =
        WriterGate.violations(chapters, consumers)

    /** Convenience for the seeded assertion suite: gate holds iff no violations. */
    fun gateHolds(chapters: List<Chapter>, consumers: List<String>): Boolean =
        assertGate(chapters, consumers).isEmpty()
}

/**
 * Draft archive — the container-side store for archived and regenerated L4
 * drafts (plan §4, "archived drafts are kept in the container for diffing
 * but never feed downstream layers").
 *
 * Layout: <authrRoot>/<bookId>/draft-archive/chapter-<n>-<timestamp>.txt
 * Best-effort by design: a failed archive write never blocks the workflow.
 */
class DraftArchive(private val authrRoot: File) {

    fun archive(book: Book, chapter: Chapter, reason: String): File? = runCatching {
        val dir = File(authrRoot, "${book.id}/draft-archive").apply { mkdirs() }
        val stamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val safeTitle = chapter.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val file = File(dir, "chapter-${chapter.chapterNumber}-$stamp-$safeTitle.txt")
        file.writeText(buildString {
            appendLine("# Archived draft — chapter ${chapter.chapterNumber} \"${chapter.title}\"")
            appendLine("# Book: ${book.title} (id=${book.id})")
            appendLine("# Reason: $reason")
            appendLine("# Archived at: $stamp — never feeds downstream layers (plan §4).")
            appendLine()
            append(chapter.content)
        })
        file
    }.getOrNull()

    /** Lists archived drafts for one chapter of one book (for the diff UI). */
    fun list(bookId: Int, chapterNumber: Int): List<File> = runCatching {
        File(authrRoot, "$bookId/draft-archive")
            .listFiles { f -> f.isFile && f.name.startsWith("chapter-$chapterNumber-") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
