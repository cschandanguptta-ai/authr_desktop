package com.example.engine.publishing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The metadata workbench (v3.0 P5, plan §11.2).
 *
 * "Retail metadata is a first-class editing surface, not a form at upload
 * time." The workbench holds the KDP fields — title, subtitle, the seven
 * keywords, the description/blurb, categories, and pricing — plus
 * per-platform blurb variants stored beside the KDP canonical (plan §11.2:
 * "a serial can carry a punchier storefront blurb than its retail
 * counterpart without maintaining two documents").
 *
 * Authority rule (resolves open question OQ-9): the workbench is the
 * authoritative source for retail metadata. The Book entity's title/synopsis
 * feed it as defaults when the workbench has never been edited; the ISBN
 * placeholder baked into the compiled manuscript (D-05) is NOT carried into
 * retail exports — the workbench ISBN (or none, leaving KDP to assign one)
 * wins.
 *
 * Container storage (schema v2, additive — mirrors the P4 pronunciation
 * rules change): `metadata/kdp_workbench.json` inside the .authr container.
 * The v2.3-era file held a StringEnvelope stub ({"data": marketingText});
 * the structured form carries a schema marker and every KDP field. The
 * reader accepts both forms, so older containers open unchanged.
 */
@Serializable
data class KdpWorkbench(
    /** Schema marker for the structured form (legacy string stubs lack it). */
    val schema: String = SCHEMA,
    val title: String,
    val subtitle: String = "",
    val author: String = "",
    val language: String = "en",
    /** The description/blurb shown on the retail page. */
    val description: String = "",
    /** The seven KDP keywords — at most seven are exported (KDP cap). */
    val keywords: List<String> = emptyList(),
    /** KDP categories (browse nodes), at most [MAX_CATEGORIES]. */
    val categories: List<String> = emptyList(),
    /** Author-owned ISBN; empty means KDP assigns one. */
    val isbn: String = "",
    val publisher: String = "",
    val copyrightLine: String = "",
    val seriesName: String = "",
    val seriesNumber: Int? = null,
    /** eBook list price in USD (the canonical marketplace of the simulator). */
    val listPriceUsd: Double = 4.99,
    /** "SEVENTY" or "THIRTY_FIVE" (KDP eBook royalty options). */
    val royaltyOption: String = SEVENTY,
    /** Per-platform blurb variants, e.g. "royal_road" → punchier storefront text. */
    val blurbVariants: Map<String, String> = emptyMap()
) {
    /** The workbench edited flag: any field a writer would have set. */
    val isEdited: Boolean
        get() = subtitle.isNotBlank() || author.isNotBlank() || description.isNotBlank() ||
            keywords.isNotEmpty() || categories.isNotEmpty() || isbn.isNotBlank() ||
            publisher.isNotBlank() || copyrightLine.isNotBlank() || seriesName.isNotBlank() ||
            blurbVariants.isNotEmpty()

    /** Keywords trimmed to the KDP seven, blank entries dropped. */
    val effectiveKeywords: List<String> get() = keywords.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_KEYWORDS)

    /** Categories trimmed to the KDP cap, blank entries dropped. */
    val effectiveCategories: List<String> get() = categories.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_CATEGORIES)

    companion object {
        const val SCHEMA = "kdp_workbench/1"
        const val SEVENTY = "SEVENTY"
        const val THIRTY_FIVE = "THIRTY_FIVE"
        const val MAX_KEYWORDS = 7
        const val MAX_CATEGORIES = 3
        const val CONTAINER_ENTRY = "metadata/kdp_workbench.json"

        /** Derives the starting workbench from the project record (writer edits from here). */
        fun fromBook(title: String, author: String, synopsis: String, marketingMaterials: String): KdpWorkbench =
            KdpWorkbench(
                title = title,
                author = author,
                // the blurb starts from the synopsis; the v2.3 marketing-materials
                // dump is kept as a blurb variant so nothing is lost
                description = synopsis,
                blurbVariants = if (marketingMaterials.isNotBlank()) {
                    mapOf("legacy_marketing" to marketingMaterials)
                } else emptyMap()
            )
    }
}

/**
 * The pricing simulator (plan §11.2): "shows the 70% and 35% royalty
 * brackets against the estimated print cost."
 *
 * Constants are the Amazon.com (US marketplace) terms, pinned to sources:
 *  - eBook 70% band on Amazon.com: $2.99–$12.99 (KDP help, "eBook List
 *    Price Requirements", expanded from $9.99 — FACT, kdp.amazon.com).
 *  - eBook 70% royalty: 70% × list − delivery; delivery is charged per MB
 *    (KDP help "eBook Royalties": "70% of your list price ... less delivery
 *    costs"; the per-MB model is the documented delivery basis — the
 *    Amazon.com rate constant is DELIVERY_US_PER_MB).
 *  - eBook 35% royalty: 35% × list, no delivery subtraction (KDP help
 *    "eBook Royalties" — FACT).
 *  - Print cost (US black-ink regular trim): fixed cost + per-page cost —
 *    $0.85 + $0.012 × page count (KDP community/calculator formula — FACT).
 *  - Print royalty: 60% × list − printing cost (KDP help "Paperback
 *    Royalty" example: (0.60 × $9.99) − printing cost — FACT; KDP also
 *    offers a 50% tier depending on list price, exposed as [printRate]).
 */
object RoyaltySimulator {

    const val BAND_MIN_USD = 2.99
    const val BAND_MAX_USD = 12.99
    const val DELIVERY_US_PER_MB = 0.15
    const val DELIVERY_MIN_USD = 0.01
    const val PRINT_FIXED_USD = 0.85
    const val PRINT_PER_PAGE_USD = 0.012
    const val PRINT_RATE = 0.60

    data class EbookQuote(
        val listPriceUsd: Double,
        val option: String,
        /** true when the list price sits inside the 70% band (SEVENTY only). */
        val bandCompliant: Boolean,
        val bandDescription: String,
        /** Delivery cost subtracted (SEVENTY only; null for THIRTY_FIVE). */
        val deliveryCostUsd: Double?,
        val royaltyPerUnitUsd: Double,
        val notes: List<String>
    )

    data class PrintQuote(
        val pageCount: Int,
        val printCostUsd: Double,
        val listPriceUsd: Double,
        val royaltyRate: Double,
        val royaltyPerUnitUsd: Double,
        /** List price at which print royalty reaches zero (must price above). */
        val breakEvenListUsd: Double,
        val notes: List<String>
    )

    /** The eBook quote for [workbench] at an estimated [fileSizeMb] EPUB size. */
    fun ebookQuote(workbench: KdpWorkbench, fileSizeMb: Double): EbookQuote {
        val price = workbench.listPriceUsd
        return if (workbench.royaltyOption == KdpWorkbench.SEVENTY) {
            val inBand = price >= BAND_MIN_USD && price <= BAND_MAX_USD
            val mb = kotlin.math.ceil(fileSizeMb.coerceAtLeast(0.0)).toInt()
            val delivery = maxOf(DELIVERY_MIN_USD, DELIVERY_US_PER_MB * mb)
            val royalty = (price * 0.70 - delivery).coerceAtLeast(0.0)
            EbookQuote(
                listPriceUsd = price,
                option = KdpWorkbench.SEVENTY,
                bandCompliant = inBand,
                bandDescription = "70% band on Amazon.com: \$${"%.2f".format(BAND_MIN_USD)}–\$${"%.2f".format(BAND_MAX_USD)}",
                deliveryCostUsd = delivery,
                royaltyPerUnitUsd = royalty,
                notes = buildList {
                    add("70% × list − delivery (KDP eBook Royalties)")
                    if (!inBand) add("List price outside the 70% band — KDP will apply the 35% terms unless the price moves inside the band")
                    add("Delivery estimated at \$${"%.2f".format(DELIVERY_US_PER_MB)}/MB × ${maxOf(mb, 1)} MB (Amazon.com rate, per-MB model)")
                }
            )
        } else {
            EbookQuote(
                listPriceUsd = price,
                option = KdpWorkbench.THIRTY_FIVE,
                bandCompliant = true,
                bandDescription = "35% option: any list price, no delivery subtraction",
                deliveryCostUsd = null,
                royaltyPerUnitUsd = price * 0.35,
                notes = listOf("35% × list, no delivery costs subtracted (KDP eBook Royalties)")
            )
        }
    }

    /** The paperback quote for [pageCount] interior pages at [listPriceUsd]. */
    fun printQuote(pageCount: Int, listPriceUsd: Double, royaltyRate: Double = PRINT_RATE): PrintQuote {
        val cost = PRINT_FIXED_USD + PRINT_PER_PAGE_USD * pageCount
        val royalty = (royaltyRate * listPriceUsd - cost).coerceAtLeast(0.0)
        return PrintQuote(
            pageCount = pageCount,
            printCostUsd = cost,
            listPriceUsd = listPriceUsd,
            royaltyRate = royaltyRate,
            royaltyPerUnitUsd = royalty,
            breakEvenListUsd = cost / royaltyRate,
            notes = listOf(
                "Print cost = \$${"%.2f".format(PRINT_FIXED_USD)} + \$${"%.3f".format(PRINT_PER_PAGE_USD)} × $pageCount pages (US black-ink, regular trim)",
                "Royalty = ${"%.0f".format(royaltyRate * 100)}% × list − print cost (KDP Paperback Royalty)"
            )
        )
    }
}

/**
 * Container store for the workbench: reads and writes
 * `metadata/kdp_workbench.json` inside an extracted .authr layout.
 *
 * Two forms are accepted on read (openers never downgrade, plan §10):
 *  - structured: JSON with [KdpWorkbench.SCHEMA] marker (this and later versions);
 *  - legacy: the v2.3 StringEnvelope stub {"data": "<marketing text>"} —
 *    returned as a workbench whose description carries the legacy text.
 */
object WorkbenchStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Reads the workbench from an extracted container [root]; null when absent. */
    fun read(root: File): KdpWorkbench? {
        val f = File(root, KdpWorkbench.CONTAINER_ENTRY)
        if (!f.exists()) return null
        val text = f.readText().trim()
        if (text.isEmpty()) return null
        return try {
            json.decodeFromString(KdpWorkbench.serializer(), text)
        } catch (e: Exception) {
            tryLegacy(text)
        }
    }

    /** Legacy StringEnvelope: {"data": "..."} — not a structured workbench. */
    private fun tryLegacy(text: String): KdpWorkbench? = try {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text)
        val data = (obj as? kotlinx.serialization.json.JsonObject)?.get("data")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        data?.let { KdpWorkbench(title = "", description = it) }
    } catch (e: Exception) {
        null
    }

    /** Writes [workbench] into an extracted container [root] (structured form). */
    fun write(root: File, workbench: KdpWorkbench) {
        val f = File(root, KdpWorkbench.CONTAINER_ENTRY)
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(KdpWorkbench.serializer(), workbench))
    }
}
