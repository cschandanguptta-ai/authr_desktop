package com.example.ui.screens

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
import com.example.engine.audio.AudiobookRenderer
import com.example.ui.viewmodel.AudiobookViewModel
import com.example.ui.viewmodel.AudiobookViewModel.EngineOption

/**
 * The Audiobook workbench (v3.0 D-13, the P4 report handoff): the audio
 * production surface of the OS, until now engine-only. Three sections in
 * one scroll:
 *
 *  1. the engine picker — Pocket TTS (POCKET_STRUCTURAL, the production
 *     engine) when the binary + voice pack resolve, espeak-ng
 *     (ESPEAK_LOCAL, the bundled offline engine) as the deterministic
 *     fallback; unavailable engines show their reason, never a silent
 *     cross-engine mix (the continuity label must stay honest);
 *  2. the pronunciation review (plan §7.3) — scan the confirmed chapters
 *     for lorebook/coined/proper terms, review the IPA → respelling
 *     proposals, approve/revoke; approvals stage into the container
 *     entry a real .authr export writes;
 *  3. the one-action render + the report — per-chapter audio seconds,
 *     ACX verdicts, SMIL timing, master audit and the synthesis-speed
 *     factor (the P4 gate's own evidence, visible where the writer works).
 */
@Composable
fun AudiobookWorkbenchScreen(
    viewModel: AudiobookViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refreshBooks() }

    val books by viewModel.books.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val approved by viewModel.approved.collectAsState()
    val rendering by viewModel.rendering.collectAsState()
    val report by viewModel.report.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(books) {
        if (selectedBookId == null) books.firstOrNull()?.id?.let { viewModel.selectBook(it) }
    }

    val book = books.firstOrNull { it.id == selectedBookId }
    val canonCount = remember(chapters) { WriterGate.canonChapters(chapters).size }

    // UI-UX desktop pass (2026-11-13): readable centered column.
    com.example.ui.components.DesktopContentPane {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Audiobook workbench", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "P4 pipeline in the app — engine, pronunciation review, render, ACX report",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
        }

        // --- Book picker -------------------------------------------------
        item {
            BookPicker(books = books, selectedBookId = selectedBookId, onSelect = { viewModel.selectBook(it) })
        }

        if (book != null) {
            // --- 1. Engine picker ----------------------------------------
            item {
                SectionCard(title = "1 · Engine (plan §7.4 — continuity)") {
                    engines.forEach { engine ->
                        EngineRow(
                            engine = engine,
                            selected = engine.id == selectedEngineId,
                            onSelect = { viewModel.selectEngine(engine.id) }
                        )
                    }
                    Text(
                        "The renderer never mixes engines mid-render: a missing Pocket binary or pack fails loud instead of silently degrading to Edge — the continuity label must stay honest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- 2. Pronunciation review ---------------------------------
            item {
                SectionCard(title = "2 · Pronunciation review (plan §7.3)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { viewModel.scanPronunciation() }) { Text("Scan confirmed chapters") }
                        if (candidates.isNotEmpty()) {
                            TextButton(onClick = { viewModel.approveAll() }) { Text("Approve all") }
                        }
                    }
                    Text(
                        "$canonCount confirmed chapter(s) in canon; ${approved.size} approved rule(s) staged at authr/$selectedBookId/audio/pronunciation_rules.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    candidates.take(12).forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${c.term} — ${c.source.label}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${c.ipa} → ${c.respelling}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.approve(c) }) { Text("Approve", fontSize = 12.sp) }
                        }
                    }
                    if (candidates.size > 12) {
                        Text("… ${candidates.size - 12} more candidate(s)", fontSize = 11.sp)
                    }
                    approved.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${rule.term} → ${rule.respelling}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(onClick = { viewModel.revoke(rule.term) }) { Text("Revoke", fontSize = 12.sp) }
                        }
                    }
                }
            }

            // --- 3. Render + report --------------------------------------
            item {
                SectionCard(title = "3 · Render + ACX report (the P4 gate evidence)") {
                    Button(onClick = { viewModel.renderAudiobook() }, enabled = !rendering && canonCount > 0) {
                        Text(if (rendering) "Rendering…" else "Render audiobook")
                    }
                }
            }
            report?.let { rep ->
                item { ReportCard(rep) }
            }
        }

        item {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
    } // end DesktopContentPane
}

@Composable
private fun BookPicker(books: List<Book>, selectedBookId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = books.firstOrNull { it.id == selectedBookId }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.title ?: if (books.isEmpty()) "No books yet — create one in the Story tab" else "Select a book")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            books.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.title) },
                    onClick = { onSelect(b.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EngineRow(engine: EngineOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = if (engine.available) onSelect else null)
            Column {
                Text(engine.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    engine.continuity + (engine.unavailableNote?.let { " — $it" } ?: ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!engine.available) {
            AssistChip(onClick = {}, label = { Text("unavailable", fontSize = 10.sp) })
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
private fun ReportCard(rep: AudiobookRenderer.RenderReport) {
    Surface(
        color = if (rep.allGreen) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (rep.allGreen) "Render ALL GREEN" else "Render — findings",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "Engine: ${rep.engineLabel} · ${rep.continuityMode.label.substringBefore(" —")} · temp ${rep.temperature} · %.2f× real time".format(
                    rep.synthesisSpeedFactor
                ),
                fontSize = 11.sp
            )
            rep.chapters.forEach { ch ->
                Text(
                    "ch ${ch.chapterNumber.toString().padStart(2, '0')} ${ch.title.take(28)} — " +
                        "%.1fs audio · ACX ${if (ch.audit?.allGreen == true) "GREEN" else "RED"} · SMIL ${if (ch.timing.ok) "OK" else "PROBLEMS"}".format(ch.chapterAudioSec),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            rep.masterMp3?.let {
                Text(
                    "master ${it.name} · ${if (rep.masterGreen) "GREEN" else "RED"} · total %.1fs".format(rep.totalAudioSec),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (rep.failureNotes.isNotEmpty()) {
                rep.failureNotes.take(8).forEach { Text("NOTE: $it", fontSize = 10.sp) }
            }
        }
    }
}
