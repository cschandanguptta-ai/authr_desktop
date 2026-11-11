package com.example

import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.RoyaltySimulator
import com.example.engine.publishing.WorkbenchStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The metadata workbench + pricing simulator (v3.0 P5, plan §11.2).
 *
 * Pinned constants (see RoyaltySimulator doc for sources):
 *  - 70% band on Amazon.com: $2.99–$12.99 (KDP eBook List Price Requirements);
 *  - delivery: per-MB, Amazon.com rate $0.15/MB, minimum $0.01;
 *  - 35% option: no delivery subtraction;
 *  - print cost (US B&W): $0.85 + $0.012 × pages;
 *  - print royalty: 60% × list − print cost.
 */
class PublishingMetadataTest {

    // ---- workbench field discipline -------------------------------------

    @Test
    fun `keywords capped at the KDP seven with blanks dropped`() {
        val wb = KdpWorkbench(
            title = "T",
            keywords = listOf(" a ", "", "b", "c", "d", "e", "f", "g", "h")
        )
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), wb.effectiveKeywords)
        assertEquals(7, wb.effectiveKeywords.size)
    }

    @Test
    fun `categories capped at three`() {
        val wb = KdpWorkbench(title = "T", categories = listOf("x", "y", "z", "w"))
        assertEquals(listOf("x", "y", "z"), wb.effectiveCategories)
    }

    @Test
    fun `fromBook seeds blurb from synopsis and keeps legacy marketing as a variant`() {
        val wb = KdpWorkbench.fromBook("Title", "Author", "A synopsis.", "marketing dump")
        assertEquals("Title", wb.title)
        assertEquals("Author", wb.author)
        assertEquals("A synopsis.", wb.description)
        assertEquals("marketing dump", wb.blurbVariants["legacy_marketing"])
        assertTrue(wb.isEdited)
    }

    @Test
    fun `untouched workbench is not edited`() {
        assertFalse(KdpWorkbench(title = "T").isEdited)
    }

    // ---- eBook royalty brackets (plan §11.2) -----------------------------

    @Test
    fun `seventy bracket inside the band on amazon com`() {
        val q = RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T", listPriceUsd = 4.99), fileSizeMb = 1.0)
        assertEquals(KdpWorkbench.SEVENTY, q.option)
        assertTrue(q.bandCompliant)
        // 70% × 4.99 = 3.493; delivery = 0.15 × ceil(1.0) = 0.15
        assertEquals(3.493 - 0.15, q.royaltyPerUnitUsd, 1e-9)
        assertEquals(0.15, q.deliveryCostUsd!!, 1e-9)
    }

    @Test
    fun `band boundaries two ninety-nine and twelve ninety-nine`() {
        assertTrue(RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T", listPriceUsd = 2.99), 0.5).bandCompliant)
        assertTrue(RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T", listPriceUsd = 12.99), 0.5).bandCompliant)
        assertFalse(RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T", listPriceUsd = 2.98), 0.5).bandCompliant)
        assertFalse(RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T", listPriceUsd = 13.00), 0.5).bandCompliant)
    }

    @Test
    fun `delivery rounds up per megabyte with a one cent minimum`() {
        // 0.3 MB rounds up to 1 MB
        assertEquals(0.15, RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T"), 0.3).deliveryCostUsd!!, 1e-9)
        // 2.1 MB rounds up to 3 MB
        assertEquals(0.45, RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T"), 2.1).deliveryCostUsd!!, 1e-9)
        // zero-size file still carries the minimum delivery
        assertEquals(0.01, RoyaltySimulator.ebookQuote(KdpWorkbench(title = "T"), 0.0).deliveryCostUsd!!, 1e-9)
    }

    @Test
    fun `thirty-five bracket never subtracts delivery`() {
        val q = RoyaltySimulator.ebookQuote(
            KdpWorkbench(title = "T", listPriceUsd = 15.0, royaltyOption = KdpWorkbench.THIRTY_FIVE),
            fileSizeMb = 10.0
        )
        assertEquals(5.25, q.royaltyPerUnitUsd, 1e-9)
        assertNull(q.deliveryCostUsd)
        assertTrue(q.bandCompliant) // any price allowed on 35%
    }

    // ---- print royalty (plan §11.2) --------------------------------------

    @Test
    fun `print cost is fixed plus per page and royalty sixty percent`() {
        val q = RoyaltySimulator.printQuote(pageCount = 300, listPriceUsd = 14.99)
        assertEquals(0.85 + 0.012 * 300, q.printCostUsd, 1e-9) // $4.45
        assertEquals(0.60 * 14.99 - (0.85 + 0.012 * 300), q.royaltyPerUnitUsd, 1e-9)
    }

    @Test
    fun `print breakeven list is cost over rate`() {
        val q = RoyaltySimulator.printQuote(pageCount = 300, listPriceUsd = 5.00)
        assertEquals(q.printCostUsd / 0.60, q.breakEvenListUsd, 1e-9)
        // a list below breakeven clamps royalty to zero
        assertEquals(0.0, q.royaltyPerUnitUsd, 1e-9)
    }

    // ---- container store --------------------------------------------------

    @Test
    fun `structured workbench round-trips through the container entry`() {
        val root = createTempDir("workbench")
        val wb = KdpWorkbench(
            title = "The Iron Vale",
            subtitle = "A serial in two volumes",
            author = "A. Writer",
            description = "Blurb.",
            keywords = List(7) { "kw$it" },
            categories = listOf("Fiction"),
            isbn = "978-3-16-148410-0",
            listPriceUsd = 6.99,
            royaltyOption = KdpWorkbench.SEVENTY,
            blurbVariants = mapOf("royal_road" to "Punchier!")
        )
        WorkbenchStore.write(root, wb)
        val back = WorkbenchStore.read(root)
        assertEquals(wb, back)
        root.deleteRecursively()
    }

    @Test
    fun `legacy string-envelope stub reads as a workbench with the legacy text`() {
        val root = createTempDir("workbench-legacy")
        File(root, "metadata").mkdirs()
        // the v2.3 writer: stringPayload(b.marketingMaterials) → {"data": "..."}
        File(root, KdpWorkbench.CONTAINER_ENTRY).writeText("""{"data": "old marketing dump"}""")
        val back = WorkbenchStore.read(root)
        assertTrue(back != null)
        assertEquals("old marketing dump", back!!.description)
        root.deleteRecursively()
    }

    @Test
    fun `missing workbench reads as null`() {
        assertNull(WorkbenchStore.read(createTempDir("empty")))
    }
}
