package com.example

import com.example.data.model.Chapter
import com.example.engine.AnnotationKind
import com.example.engine.DraftState
import com.example.engine.EngineLayer
import com.example.engine.LiveAstAnnotator
import com.example.engine.SixLayerPipeline
import com.example.engine.WriterGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * The six-layer engine layer contract (plan §4, Table 4): every artifact
 * names its producing layer, its owning agent, and its confirmation state;
 * L6 builds ledger entries from canon only; the assertion primitive holds.
 */
class SixLayerPipelineTest {

    private fun chapter(
        number: Int,
        confirmed: Boolean,
        status: String = if (confirmed) DraftState.CONFIRMED else DraftState.PROPOSED
    ) = Chapter(
        bookId = 1, chapterNumber = number, title = "Ch $number",
        content = "She swore an oath. The sealed ledger waited. $number",
        summary = "Beat sheet for chapter $number", status = "Completed",
        writerConfirmed = confirmed, draftStatus = status
    )

    @Test
    fun `every layer result names layer agent and state`() {
        val canon = chapter(1, confirmed = true)
        val proposed = chapter(2, confirmed = false)

        val results = listOf(
            SixLayerPipeline.l1BibleIntake("Mara Vane", confirmed = true),
            SixLayerPipeline.l2StructurePlanner("Hero's Journey", approved = true),
            SixLayerPipeline.l3BeatDecomposer(1, beatsConfirmed = true),
            SixLayerPipeline.l4SceneAutoDrafter(proposed),
            SixLayerPipeline.l5StylePolisher(1, accepted = false),
            SixLayerPipeline.l6ContinuityAuditor(canon, annotations = 9)
        )
        results.forEach { r ->
            assertTrue(r.provenance().startsWith(r.layer.layer), "artifact names its layer: ${r.provenance()}")
            assertTrue(r.layer.owningAgent.isNotBlank(), "artifact names its owning agent")
            assertTrue(r.state in DraftState.ALL, "artifact carries a confirmation state")
        }
        assertEquals(DraftState.PROPOSED, results[3].state, "L4 output is always a proposal on arrival")
        assertEquals(DraftState.PROPOSED, results[4].state, "an unaccepted L5 revision is not canon")
    }

    @Test
    fun `layer agents match the plan layer map`() {
        assertEquals("Scene Composer", EngineLayer.L4.owningAgent)
        assertEquals("Continuity Manager", EngineLayer.L6.owningAgent)
        assertEquals("Executive Orchestrator", EngineLayer.L5.owningAgent)
    }

    @Test
    fun `ledger entries are built from canon only`() {
        val canon = chapter(1, confirmed = true)
        val ast = LiveAstAnnotator.annotate(canon).astOrThrow
        val guns = SixLayerPipeline.buildLedgerEntries(canon, ast)
        assertTrue(guns.isNotEmpty(), "canon chapter yields ledger entries from its promises")
        assertTrue(guns.all { it.introducedChapter == canon.chapterNumber && it.status == "INTRODUCED" })

        val proposed = chapter(2, confirmed = false)
        // A proposal cannot be annotated (the annotator refuses); the ledger
        // build must also refuse regardless of what AST is passed.
        val anyAst = LiveAstAnnotator.annotate(canon).astOrThrow
        assertFalse(WriterGate.isCanon(proposed))
        assertEquals(
            emptyList(), SixLayerPipeline.buildLedgerEntries(proposed, anyAst),
            "no ledger entries from unconfirmed text"
        )
    }

    @Test
    fun `gate assertion catches unconfirmed chapters feeding consumers`() {
        val chapters = listOf(
            chapter(1, confirmed = true),
            chapter(2, confirmed = false),
            chapter(3, confirmed = false, status = DraftState.ARCHIVED)
        )
        val violations = SixLayerPipeline.assertGate(chapters, listOf("chekhov_ledger", "epub_export", "compile"))
        assertEquals(2, violations.size, "chapters 2 and 3 violate the gate")
        assertTrue(SixLayerPipeline.gateHolds(chapters.filter { WriterGate.isCanon(it) }, listOf("chekhov_ledger")))
        assertFalse(SixLayerPipeline.gateHolds(chapters, listOf("export")))
    }

    @Test
    fun `empty consumer surface never flags`() {
        val chapters = listOf(chapter(1, confirmed = false))
        assertEquals(emptyList(), SixLayerPipeline.assertGate(chapters, emptyList()))
    }

    @Test
    fun `l6 result carries canon state only for canon chapters`() {
        val canon = SixLayerPipeline.l6ContinuityAuditor(chapter(1, confirmed = true), 4)
        val nonCanon = SixLayerPipeline.l6ContinuityAuditor(chapter(2, confirmed = false), 0)
        assertEquals(DraftState.CONFIRMED, canon.state)
        assertEquals(DraftState.PROPOSED, nonCanon.state)
    }
}
