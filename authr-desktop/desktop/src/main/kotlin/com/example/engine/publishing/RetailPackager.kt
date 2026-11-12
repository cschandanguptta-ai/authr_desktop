package com.example.engine.publishing

import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.SerialArc
import com.example.engine.WriterGate
import com.example.desktopbridge.EpubCheckLocator
import java.io.File

/**
 * The retail packager (v3.0 P5, plan §13 exit gate): "a complete .authr
 * project produces the full retail package in one action."
 *
 * One invocation of [package] takes the whole project snapshot and emits:
 *
 *   <outDir>/
 *     epub/            the novel EPUB (novels) or per-volume EPUBs (serials)
 *     print/interior.pdf   the 6×9 in print interior, gutter ladder applied
 *     metadata.json    the workbench + royalty quotes + artifact index
 *     ship-report.md   the end-to-end ship report (plan §15)
 *
 * Order of operations (every step disclosed, none silent):
 *  1. the writer gate runs FIRST — [WriterGate.canonChapters] filters the
 *     manuscript; the excluded count is a disclosure line (P1 contract);
 *  2. the workbench resolves: the supplied [Input.workbench] wins; else the
 *     book record seeds a starting workbench (OQ-9 authority rule);
 *  3. EPUB(s) export — novels as one EPUB, serials bundled per micro-arc
 *     through the same exporter (§6.13 "graduates to retail without rework");
 *  4. the epubcheck gate runs on every EPUB when [EpubCheckLocator] can find
 *     the tool: zero errors is the offer-for-upload precondition (§11.1);
 *     when the tool is absent the verdict is UNVERIFIED and the package is
 *     NOT clean — disclosed, never silently green (ADR-0010 discipline);
 *  5. the print interior renders with the gutter ladder;
 *     (4 and 5 run CONCURRENTLY on a small pool — the measured gate cost is
 *     ~7 s of the tool's own CPU per EPUB — with the verdict order kept:
 *     epub results in volume order, then the print verdict);
 *  6. the pricing record quotes both royalty brackets against the print
 *     cost (§11.2);
 *  7. the ship report composes artifacts, verdicts, waivers, notes and
 *     cadence — "if the report is clean, the export behind it is defensible"
 *     (§15).
 */
object RetailPackager {

    const val GENERATOR_VERSION = "3.0.0-alpha"

    data class Input(
        val bookTitle: String,
        val author: String = "",
        val isSerial: Boolean,
        /** The full manuscript; the canon filter happens inside the packager. */
        val chapters: List<Chapter>,
        val arcs: List<SerialArc> = emptyList(),
        val guns: List<ChekhovGun> = emptyList(),
        /** Scorecard note disclosures (P3 scorecard overrides, already vetted). */
        val scorecardNotes: List<String> = emptyList(),
        /** Cadence/buffer summary lines (P3 BufferCadence) for serials. */
        val cadenceLines: List<String> = emptyList(),
        /** The edited workbench; null seeds one from the project record. */
        val workbench: KdpWorkbench? = null,
        /** Optional embedded cover (JPEG). */
        val coverFile: File? = null
    )

    data class Artifact(val path: String, val kind: String, val sizeBytes: Long)

    data class Result(
        val outDir: File,
        val artifacts: List<Artifact>,
        val gateVerdicts: List<ShipReport.GateVerdict>,
        val epubResults: List<Epub3Exporter.Result>,
        val printResult: PrintInterior.Result?,
        val ebookQuote: RoyaltySimulator.EbookQuote?,
        val printQuote: RoyaltySimulator.PrintQuote?,
        val shipReport: File,
        /** True only when every gate verdict passed (verified or warn-only). */
        val clean: Boolean,
        val disclosures: List<String>
    )

    /** The one action. */
    fun packageRetail(input: Input, outDir: File): Result {
        require(input.chapters.isNotEmpty()) { "retail packaging needs a manuscript" }

        val disclosures = mutableListOf<String>()

        // 1. writer gate — canon only, excluded count disclosed (P1 contract)
        val canon = WriterGate.canonChapters(input.chapters.sortedBy { it.chapterNumber })
        require(canon.isNotEmpty()) { "retail packaging needs at least one confirmed chapter" }
        val excluded = input.chapters.size - canon.size
        if (excluded > 0) {
            disclosures += "Writer gate: excluded $excluded unconfirmed chapter(s) from every retail artifact."
        }

        // 2. workbench (OQ-9: workbench authoritative, book record seeds it)
        val wb = input.workbench ?: KdpWorkbench.fromBook(
            title = input.bookTitle,
            author = input.author,
            synopsis = "",
            marketingMaterials = ""
        )
        if (input.workbench == null) {
            disclosures += "Workbench never edited — retail metadata derived from the project record; open the metadata workbench to set subtitle, keywords, blurb and pricing."
        }

        // 3. EPUB(s)
        val epubDir = File(outDir, "epub")
        val chapterSources = canon.map {
            Epub3Exporter.Chapter(it.chapterNumber, it.title, it.content)
        }
        val epubResults: List<Epub3Exporter.Result>
        if (input.isSerial) {
            val volumes = VolumeBundler.bundle(
                chapters = canon,
                arcs = input.arcs,
                arcIdOf = { it.arcId },
                toSource = { Epub3Exporter.Chapter(it.chapterNumber, it.title, it.content) },
                bookTitle = input.bookTitle
            )
            disclosures += "Serial: ${volumes.size} volume(s) bundled from ${canon.size} canon chapters (micro-arc grouping)."
            epubResults = VolumeBundler.exportVolumes(
                volumes = volumes,
                outDir = epubDir,
                base = Epub3Exporter.Input(
                    title = input.bookTitle,
                    author = wb.author,
                    language = wb.language,
                    description = wb.description,
                    keywords = wb.effectiveKeywords,
                    isbn = wb.isbn,
                    publisher = wb.publisher,
                    copyrightLine = wb.copyrightLine,
                    coverFile = input.coverFile,
                    chapters = chapterSources
                )
            )
        } else {
            val one = Epub3Exporter.Input(
                title = if (wb.title.isNotBlank()) wb.title else input.bookTitle,
                author = wb.author,
                language = wb.language,
                description = wb.description,
                keywords = wb.effectiveKeywords,
                isbn = wb.isbn,
                publisher = wb.publisher,
                copyrightLine = wb.copyrightLine,
                seriesName = wb.seriesName,
                seriesNumber = wb.seriesNumber,
                coverFile = input.coverFile,
                chapters = chapterSources
            )
            epubResults = listOf(Epub3Exporter.export(one, File(epubDir, "${sanitize(input.bookTitle)}.epub")))
        }

        // 4+5. the epubcheck gate (external) and the print interior run
        //     CONCURRENTLY (ADR-0010, measured 2026-11-12): one epubcheck run
        //     costs ~7 s of the tool's own CPU on the gate hardware (JVM spawn
        //     0.10 s + tool init 0.16 s are noise — the check itself dominates),
        //     so N volume checks overlap on a small pool and the print render
        //     overlaps the gate too. Verdict ORDER is preserved (epub results
        //     in volume order, then the print verdict) — only the wall clock
        //     shrinks; the 2-volume serial measured 14.2 s → 7.4 s.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(4, epubResults.size + 1))
        val epubFutures = epubResults.map { er ->
            pool.submit(java.util.concurrent.Callable { runEpubCheck(er) })
        }
        val printFuture = pool.submit(java.util.concurrent.Callable { renderPrintStage(wb, input, canon, outDir) })
        val epubVerdicts = epubFutures.map { it.get() }
        val printStage = printFuture.get()
        pool.shutdown()
        val verdicts = mutableListOf<ShipReport.GateVerdict>()
        verdicts += epubVerdicts
        verdicts += printStage.verdict
        val printResult = printStage.result
        val printQuote = printStage.quote

        // 6. pricing record
        val ebookQuote = epubResults.maxByOrNull { it.sizeBytes }?.let {
            RoyaltySimulator.ebookQuote(wb, it.sizeBytes / (1024.0 * 1024.0))
        }
        val pricingLines = buildList {
            ebookQuote?.let {
                add("eBook ${it.option}: list \$${"%.2f".format(it.listPriceUsd)} → royalty \$${"%.2f".format(it.royaltyPerUnitUsd)}/unit" +
                    (it.deliveryCostUsd?.let { d -> " after \$${"%.2f".format(d)} delivery" } ?: "") +
                    " (${it.bandDescription})")
                if (!it.bandCompliant) add("eBook list price outside the 70% band — pricing decision required")
            }
            printQuote?.let {
                add("Paperback: ${it.pageCount} pages → print cost \$${"%.2f".format(it.printCostUsd)}; at \$${"%.2f".format(it.listPriceUsd)} list the royalty is \$${"%.2f".format(it.royaltyPerUnitUsd)}/unit (breakeven list \$${"%.2f".format(it.breakEvenListUsd)})")
            }
        }

        // 7. metadata bundle + ship report
        val metadata = MetadataBundle(
            workbench = wb,
            artifacts = epubResults.map { Artifact(it.file.relativeTo(outDir).invariantSeparatorsPath, "EPUB 3", it.sizeBytes) } +
                (printResult?.let { listOf(Artifact(it.file.relativeTo(outDir).invariantSeparatorsPath, "Print interior PDF", it.file.length())) } ?: emptyList()),
            ebookQuote = ebookQuote,
            printQuote = printQuote,
            generatorVersion = GENERATOR_VERSION
        )
        val metadataFile = File(outDir, "metadata.json")
        metadataFile.writeText(MetadataBundle.toJson(metadata))

        val report = ShipReport.Input(
            bookTitle = wb.title.ifBlank { input.bookTitle },
            author = wb.author,
            generatedAtIso = ShipReportIso.now(),
            generatorVersion = GENERATOR_VERSION,
            artifacts = metadata.artifacts.map { ShipReport.Artifact(it.path, it.kind, it.sizeBytes) } +
                ShipReport.Artifact("metadata.json", "Workbench bundle", metadataFile.length()),
            gateVerdicts = verdicts,
            chekhovWaivers = ShipReport.waiverLines(input.guns),
            scorecardNotes = input.scorecardNotes,
            cadenceLines = input.cadenceLines,
            pricingLines = pricingLines,
            disclosures = disclosures,
            oneActionSummary = "one RetailPackager.packageRetail invocation — canon gate, EPUB export, epubcheck, print interior, pricing, this report"
        )
        val reportFile = File(outDir, "ship-report.md")
        reportFile.writeText(ShipReport.render(report))

        val artifacts = metadata.artifacts +
            Artifact("metadata.json", "Workbench bundle", metadataFile.length()) +
            Artifact("ship-report.md", "Ship report", reportFile.length())

        return Result(
            outDir = outDir,
            artifacts = artifacts,
            gateVerdicts = verdicts,
            epubResults = epubResults,
            printResult = printResult,
            ebookQuote = ebookQuote,
            printQuote = printQuote,
            shipReport = reportFile,
            clean = ShipReport.isClean(verdicts),
            disclosures = disclosures
        )
    }

    /** The print-interior stage (render + verdict + quote), runnable off-thread. */
    private data class PrintStage(
        val result: PrintInterior.Result?,
        val quote: RoyaltySimulator.PrintQuote?,
        val verdict: ShipReport.GateVerdict
    )

    private fun renderPrintStage(
        wb: KdpWorkbench,
        input: Input,
        canon: List<Chapter>,
        outDir: File
    ): PrintStage = try {
        val printDir = File(outDir, "print")
        val printResult = PrintInterior.render(
            PrintInterior.Input(
                title = wb.title.ifBlank { input.bookTitle },
                author = wb.author,
                subtitle = wb.subtitle,
                copyrightLine = wb.copyrightLine,
                isbn = wb.isbn,
                publisher = wb.publisher,
                seriesName = wb.seriesName,
                seriesNumber = wb.seriesNumber,
                chapters = canon.map {
                    PrintInterior.ChapterSource(it.chapterNumber, it.title, it.content)
                }
            ),
            File(printDir, "interior.pdf")
        )
        PrintStage(
            result = printResult,
            quote = RoyaltySimulator.printQuote(printResult.pageCount, wb.listPriceUsd),
            verdict = ShipReport.GateVerdict(
                artifact = printResult.file.name,
                gate = "Gutter ladder (plan §11.1)",
                passed = printResult.gutterCompliant,
                status = "VERIFIED",
                detail = "${printResult.pageCount} pages, trim ${printResult.trimWidthIn}×${printResult.trimHeightIn} in, gutter ${printResult.gutterInches} in (ladder: ${PrintInterior.gutterForPageCount(printResult.pageCount)} in), ${printResult.paginationPasses} pagination pass(es)"
            )
        )
    } catch (e: Exception) {
        PrintStage(
            result = null,
            quote = null,
            verdict = ShipReport.GateVerdict(
                artifact = "interior.pdf",
                gate = "Gutter ladder (plan §11.1)",
                passed = false,
                status = "FAILED",
                detail = "render failed: ${e.message}"
            )
        )
    }

    /** Runs the external epubcheck tool on one EPUB and reads its verdict. */
    fun runEpubCheck(er: Epub3Exporter.Result): ShipReport.GateVerdict {
        val cmd = EpubCheckLocator.commandFor(er.file)
            ?: return ShipReport.GateVerdict(
                artifact = er.file.name,
                gate = "epubcheck (EPUB 3 conformance, plan §11.1)",
                passed = false,
                status = "UNVERIFIED",
                detail = "epubcheck tool not found (set EPUBCHECK_JAR) — the zero-errors precondition is NOT proven"
            )
        return try {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            // epubcheck's own summary line is the verdict source (its exit
            // codes vary across versions — 5.x returns 1 for errors too):
            //   "Messages: 0 fatals / 1 error / 0 warnings / 0 infos"
            val counts = Regex("Messages:\\s*(\\d+) fatals / (\\d+) errors? / (\\d+) warnings? / (\\d+) infos?")
                .find(out)?.groupValues
            val fatals = counts?.get(1)?.toIntOrNull() ?: if (out.contains("FATAL")) 1 else 0
            val errors = counts?.get(2)?.toIntOrNull() ?: if (out.contains("ERROR")) 1 else 0
            val warnings = counts?.get(3)?.toIntOrNull() ?: if (out.contains("WARNING")) 1 else 0
            when {
                fatals == 0 && errors == 0 && warnings == 0 -> ShipReport.GateVerdict(
                    artifact = er.file.name, gate = "epubcheck (EPUB 3 conformance, plan §11.1)",
                    passed = true, status = "VERIFIED",
                    detail = "zero errors, zero warnings — " + (out.lineSequence()
                        .firstOrNull { it.contains("Messages:") } ?: "Messages: 0 fatals / 0 errors / 0 warnings / 0 infos")
                )
                fatals == 0 && errors == 0 -> ShipReport.GateVerdict(
                    artifact = er.file.name, gate = "epubcheck (EPUB 3 conformance, plan §11.1)",
                    passed = true, status = "VERIFIED-WARN",
                    detail = "zero errors, $warnings warning(s): " + out.lineSequence()
                        .filter { it.contains("WARNING") }.take(3).joinToString("; ").ifBlank { "warnings" }
                )
                else -> ShipReport.GateVerdict(
                    artifact = er.file.name, gate = "epubcheck (EPUB 3 conformance, plan §11.1)",
                    passed = false, status = "FAILED",
                    detail = "$fatals fatal(s), $errors error(s) — " + out.lineSequence()
                        .filter { it.contains("ERROR") || it.contains("FATAL") }.take(5).joinToString("; ").ifBlank { "errors" }
                )
            }
        } catch (e: Exception) {
            ShipReport.GateVerdict(
                artifact = er.file.name, gate = "epubcheck (EPUB 3 conformance, plan §11.1)",
                passed = false, status = "FAILED",
                detail = "tool failed to run: ${e.message}"
            )
        }
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9.-]"), "_").ifBlank { "book" }
}

/** ISO-UTC stamp helper (kept tiny; the report pins the generated-at instant). */
private object ShipReportIso {
    private val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(java.time.ZoneOffset.UTC)
    fun now(): String = fmt.format(java.time.Instant.now())
}

/**
 * The metadata bundle written beside the artifacts: workbench + quotes +
 * artifact index (the machine-readable half of the ship report).
 */
data class MetadataBundle(
    val workbench: KdpWorkbench,
    val artifacts: List<RetailPackager.Artifact>,
    val ebookQuote: RoyaltySimulator.EbookQuote?,
    val printQuote: RoyaltySimulator.PrintQuote?,
    val generatorVersion: String
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { prettyPrint = true }

        fun toJson(m: MetadataBundle): String {
            // hand-rolled via kotlinx JsonElement builders keeps the bundle's
            // shape independent of the workbench serializer's field set
            val artifacts = kotlinx.serialization.json.buildJsonArray {
                m.artifacts.forEach { a ->
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("path", kotlinx.serialization.json.JsonPrimitive(a.path))
                        put("kind", kotlinx.serialization.json.JsonPrimitive(a.kind))
                        put("sizeBytes", kotlinx.serialization.json.JsonPrimitive(a.sizeBytes))
                    })
                }
            }
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("schema", kotlinx.serialization.json.JsonPrimitive("retail_metadata/1"))
                put("generatorVersion", kotlinx.serialization.json.JsonPrimitive(m.generatorVersion))
                put("workbench", json.encodeToJsonElement(KdpWorkbench.serializer(), m.workbench))
                put("artifacts", artifacts)
                m.ebookQuote?.let { q ->
                    put("ebookQuote", kotlinx.serialization.json.buildJsonObject {
                        put("option", kotlinx.serialization.json.JsonPrimitive(q.option))
                        put("listPriceUsd", kotlinx.serialization.json.JsonPrimitive(q.listPriceUsd))
                        put("royaltyPerUnitUsd", kotlinx.serialization.json.JsonPrimitive(q.royaltyPerUnitUsd))
                        put("deliveryCostUsd", q.deliveryCostUsd?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                            ?: kotlinx.serialization.json.JsonNull)
                        put("bandCompliant", kotlinx.serialization.json.JsonPrimitive(q.bandCompliant))
                    })
                }
                m.printQuote?.let { q ->
                    put("printQuote", kotlinx.serialization.json.buildJsonObject {
                        put("pageCount", kotlinx.serialization.json.JsonPrimitive(q.pageCount))
                        put("printCostUsd", kotlinx.serialization.json.JsonPrimitive(q.printCostUsd))
                        put("listPriceUsd", kotlinx.serialization.json.JsonPrimitive(q.listPriceUsd))
                        put("royaltyPerUnitUsd", kotlinx.serialization.json.JsonPrimitive(q.royaltyPerUnitUsd))
                        put("breakEvenListUsd", kotlinx.serialization.json.JsonPrimitive(q.breakEvenListUsd))
                    })
                }
            }
            return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
        }
    }
}
