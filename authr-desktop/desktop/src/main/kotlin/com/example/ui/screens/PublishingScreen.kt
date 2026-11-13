package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book
import com.example.engine.WriterGate
import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.RoyaltySimulator
import com.example.ui.viewmodel.PublishingViewModel
import com.example.ui.viewmodel.PublishingViewModel.PackageOutcome

/**
 * The Publishing workbench (v3.0 D-15, the P5 report handoff): the retail
 * surface of the OS. Three sections in one scroll:
 *
 *  1. the metadata workbench editor — the KDP fields ([KdpWorkbench]):
 *     subtitle, author, the seven keywords, categories, blurb, ISBN,
 *     publisher, copyright, series, pricing with the royalty option, and
 *     the per-platform blurb variants (§11.2); staged to the container on
 *     save (OQ-9 authority);
 *  2. the one-action package button — the P5 gate order (container export →
 *     import checksum audit → EPUB(s) → epubcheck → print interior →
 *     metadata + ship report), with the clean / NOT-clean banner;
 *  3. the ship-report viewer — verdicts, artifacts and disclosures, so the
 *     writer can read exactly what the packager measured (§15: "if the
 *     report is clean, the export behind it is defensible").
 */
@Composable
fun PublishingScreen(
    viewModel: PublishingViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refreshBooks() }

    val books by viewModel.books.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val workbench by viewModel.workbench.collectAsState()
    val dirty by viewModel.dirty.collectAsState()
    val packaging by viewModel.packaging.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(books) {
        if (selectedBookId == null) books.firstOrNull()?.id?.let { viewModel.selectBook(it) }
    }

    val book = books.firstOrNull { it.id == selectedBookId }
    val wb: KdpWorkbench? = workbench
    val canonCount = remember(chapters) { WriterGate.canonChapters(chapters).size }

    var showShipReport by remember { mutableStateOf(false) }

    // UI-UX desktop pass (2026-11-13): readable centered column.
    com.example.ui.components.DesktopContentPane {
    Column(Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Publishing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        // ── Book picker (every project, novel or serial) ────────────────
        if (books.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                books.take(5).forEach { b ->
                    FilterChip(
                        selected = b.id == selectedBookId,
                        onClick = { viewModel.selectBook(b.id) },
                        label = { Text(b.title.take(22), fontSize = 12.sp) }
                    )
                }
            }
        }
        if (book == null || wb == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (books.isEmpty()) "No projects yet — create one in the Story workspace, then return here to package it."
                    else "Select a project.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@DesktopContentPane
        }

        // ── Body ────────────────────────────────────────────────────────
        LazyColumn(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Metadata workbench (OQ-9 authority)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Project: ${book.title} — ${if (book.isSerialized) "serial" else "novel"} — $canonCount canon chapter(s) will be packaged." +
                                (if (dirty) " Unsaved edits." else " Staged."),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WorkbenchEditor(wb, viewModel)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("One action to retail", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Exports the .authr container, audits it (checksum import), bundles the EPUB(s), runs epubcheck as a blocking external gate, renders the 6×9 in print interior with the gutter ladder, quotes the royalties and writes metadata.json + ship-report.md.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { showShipReport = false; viewModel.packageRetail() },
                                enabled = !packaging
                            ) {
                                Text(if (packaging) "Packaging…" else "Package retail")
                            }
                            if (dirty) Text("Save the workbench first — packaging stages the current edits.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            outcome?.let { oc ->
                item { OutcomeCard(oc, showShipReport, onToggleReport = { showShipReport = !showShipReport }) }
            }
        }
    }
    } // end DesktopContentPane
}

// ----------------------------------------------------------------------
// Workbench editor fields
// ----------------------------------------------------------------------

@Composable
private fun WorkbenchEditor(wb: KdpWorkbench, viewModel: PublishingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Subtitle", wb.subtitle, Modifier.weight(1f)) { v -> viewModel.editWorkbench { it.copy(subtitle = v) } }
            Field("Author", wb.author, Modifier.weight(1f)) { v -> viewModel.editWorkbench { it.copy(author = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Publisher", wb.publisher, Modifier.weight(1f)) { v -> viewModel.editWorkbench { it.copy(publisher = v) } }
            Field("ISBN (empty = KDP assigns)", wb.isbn, Modifier.weight(1f)) { v -> viewModel.editWorkbench { it.copy(isbn = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Series name", wb.seriesName, Modifier.weight(1f)) { v -> viewModel.editWorkbench { it.copy(seriesName = v) } }
            Field("Series number", wb.seriesNumber?.toString() ?: "", Modifier.weight(0.4f)) { v ->
                viewModel.editWorkbench { it.copy(seriesNumber = v.trim().toIntOrNull()) }
            }
            Field("Language", wb.language, Modifier.weight(0.4f)) { v -> viewModel.editWorkbench { it.copy(language = v.ifBlank { "en" }) } }
        }
        Field("Blurb (retail description)", wb.description, singleLine = false, minLines = 3) { v ->
            viewModel.editWorkbench { it.copy(description = v) }
        }
        Field("Keywords (comma-separated, KDP cap 7)", wb.keywords.joinToString(", ")) { v ->
            viewModel.editWorkbench { it.copy(keywords = v.split(",").map { k -> k.trim() }.filter { k -> k.isNotEmpty() }) }
        }
        Text(
            "Effective keywords: ${wb.effectiveKeywords.size}/7 — ${wb.effectiveKeywords.joinToString("; ")}",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Field("Categories (comma-separated, KDP cap 3)", wb.categories.joinToString(", ")) { v ->
            viewModel.editWorkbench { it.copy(categories = v.split(",").map { c -> c.trim() }.filter { c -> c.isNotEmpty() }) }
        }
        Field("Copyright line", wb.copyrightLine) { v -> viewModel.editWorkbench { it.copy(copyrightLine = v) } }

        // Pricing (§11.2): list price + royalty option, with the band note
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("List price (USD)", if (wb.listPriceUsd == wb.listPriceUsd.toInt().toDouble()) wb.listPriceUsd.toInt().toString() else wb.listPriceUsd.toString(), Modifier.weight(0.4f)) { v ->
                viewModel.editWorkbench { it.copy(listPriceUsd = v.trim().toDoubleOrNull() ?: it.listPriceUsd) }
            }
            Text("Royalty option:", fontSize = 12.sp)
            FilterChip(
                selected = wb.royaltyOption == KdpWorkbench.SEVENTY,
                onClick = { viewModel.editWorkbench { it.copy(royaltyOption = KdpWorkbench.SEVENTY) } },
                label = { Text("70%", fontSize = 12.sp) }
            )
            FilterChip(
                selected = wb.royaltyOption == KdpWorkbench.THIRTY_FIVE,
                onClick = { viewModel.editWorkbench { it.copy(royaltyOption = KdpWorkbench.THIRTY_FIVE) } },
                label = { Text("35%", fontSize = 12.sp) }
            )
        }
        val quote = remember(wb.listPriceUsd, wb.royaltyOption) {
            RoyaltySimulator.ebookQuote(wb, fileSizeMb = 1.0)
        }
        Text(
            buildString {
                append("Quote preview: ${quote.bandDescription}. ")
                append("Delivery is estimated at 1 MB here; the ship report quotes the MEASURED EPUB size.")
            },
            fontSize = 11.sp, color = if (quote.bandCompliant) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )

        // Per-platform blurb variants (§11.2)
        Text("Blurb variants (per-platform, e.g. royal_road)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        wb.blurbVariants.forEach { (name, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = { viewModel.editWorkbench { it.copy(blurbVariants = it.blurbVariants - name) } }) {
                    Text("Remove", fontSize = 11.sp)
                }
            }
        }
        var variantName by remember { mutableStateOf("") }
        var variantText by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Platform", variantName, Modifier.width(110.dp), onValue = { variantName = it })
            Field("Variant blurb", variantText, Modifier.weight(1f), onValue = { variantText = it })
            TextButton(
                onClick = {
                    if (variantName.isNotBlank() && variantText.isNotBlank()) {
                        viewModel.editWorkbench { it.copy(blurbVariants = it.blurbVariants + (variantName.trim() to variantText)) }
                        variantName = ""; variantText = ""
                    }
                }
            ) { Text("Add") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveWorkbench() }) { Text("Save workbench") }
        }
    }
}

/** One labeled editor row; the two-value overloads cover the plain and multiline cases. */
@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Field(label, value, modifier = modifier, onValueChange = onValue)
}

// ----------------------------------------------------------------------
// Outcome: verdict banner + artifacts + ship report viewer
// ----------------------------------------------------------------------

@Composable
private fun OutcomeCard(oc: PackageOutcome, showReport: Boolean, onToggleReport: () -> Unit) {
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (oc.result.clean) "SHIP REPORT: CLEAN — every gate verdict passed."
                else "SHIP REPORT: NOT CLEAN — fix the red verdicts before uploading.",
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = if (oc.result.clean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                "Package: ${oc.result.outDir.absolutePath} — .authr container ${oc.containerFile.name} (${oc.containerBytes} bytes, schema v${oc.importedSchemaVersion}) imported back for the audit before packaging.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            oc.result.gateVerdicts.forEach { v ->
                Text(
                    buildString {
                        append(if (v.passed) "PASS " else "RED  ")
                        append("[${v.status}] ${v.artifact} — ${v.gate}: ")
                        append(v.detail)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (v.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().background(
                        if (v.passed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(4.dp)
                    ).padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (oc.result.disclosures.isNotEmpty()) {
                Text("Disclosures", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                oc.result.disclosures.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("Artifacts", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            oc.result.artifacts.forEach { a ->
                Text("• ${a.path} — ${a.kind}, ${a.sizeBytes} bytes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onToggleReport) {
                Text(if (showReport) "Hide ship report" else "View ship report (ship-report.md)")
            }
            if (showReport) {
                Text(
                    oc.result.shipReport.readText(),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)
                )
            }
        }
    }
}
