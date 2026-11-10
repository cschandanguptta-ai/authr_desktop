package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.SerialArc
import com.example.engine.WriterGate
import com.example.engine.serialization.BufferCadence
import com.example.engine.serialization.MicroArcPlanner
import com.example.engine.serialization.PreDraftWorksheet
import com.example.engine.serialization.QcScorecard
import com.example.engine.serialization.SerialExportProfiles
import com.example.engine.serialization.SerialValidatorSuite
import com.example.ui.viewmodel.SerialDashboardViewModel
import java.time.LocalDate

/**
 * The Serial Dashboard — the v3.0 P3 publication surface (plan §6.10/§6.11).
 *
 * Both workspaces consume the same engine here in one place: the buffer
 * meter and cadence tracker, the arc board (micro-arc planner), the overdue
 * question widget, the validator-suite report with dismissal, the worksheet
 * and scorecard editors for the selected chapter, the release action, and
 * the paste-ready platform export (§6.13: the application formats, the
 * writer pastes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialDashboardScreen(
    viewModel: SerialDashboardViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refreshBooks() }

    val books by viewModel.books.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val arcs by viewModel.arcs.collectAsState()
    val buffer by viewModel.buffer.collectAsState()
    val cadence by viewModel.cadence.collectAsState()
    val report by viewModel.report.collectAsState()
    val status by viewModel.status.collectAsState()
    val selectedChapterId by viewModel.selectedChapterId.collectAsState()
    val exportProfileId by viewModel.exportProfileId.collectAsState()

    LaunchedEffect(books) {
        if (selectedBookId == null) books.firstOrNull()?.id?.let { viewModel.selectBook(it) }
    }

    val book = books.firstOrNull { it.id == selectedBookId }
    val selectedChapter = chapters.firstOrNull { it.id == selectedChapterId }
    var showPreFlight by remember { mutableStateOf(false) }
    var clipboardPreview by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Serial Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        // Book picker (serialized projects only)
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
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (books.isEmpty()) "No serialized projects yet — toggle the serial format on a project to open its dashboard."
                    else "Select a serial.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Row(Modifier.fillMaxSize().weight(1f)) {
            // ── Left column: gauges, arcs, suite report ─────────────────
            Column(
                Modifier.weight(0.58f).fillMaxSize().padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            ) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Buffer + cadence gauges (§6.10)
                    item {
                        Card {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Publication mechanics (§6.10)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                buffer?.let { BufferGauge(it) }
                                cadence?.let { c ->
                                    Text(c.message, fontSize = 12.sp)
                                    Text("Streak: ${c.streak} on-schedule release(s).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // Arc board (§6.5)
                    item {
                        Card {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Arc board (§6.5 micro-arc planner)", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.planArcs() }) { Text("Lay arcs", fontSize = 12.sp) }
                                }
                                if (arcs.isEmpty()) Text(
                                    "No arc plan yet — the micro-arc is the macro planning unit (10-15 chapter cycles).",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                arcs.forEach { arc -> ArcCard(arc, chapters, viewModel) }
                            }
                        }
                    }
                    // Overdue questions (§6.7 widget)
                    item {
                        Card {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Question ledger — overdue (§6.7)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val overdue = viewModel.overdueQuestions()
                                if (overdue.isEmpty()) Text("No overdue progressive questions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                overdue.forEach { q ->
                                    Text("⏰ ${q.text}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    // Validator suite report
                    item {
                        Card {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Validator suite (Blueprint 2.0)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val r = report
                                if (r == null) {
                                    Text("Run the suite over the selected serial.", fontSize = 12.sp)
                                } else {
                                    val verdict = if (r.exitGateGreen) "GREEN — ${r.passedCount} checks pass, ${r.warnings.size} dismissable warning(s)"
                                    else if (!r.passes) "BLOCKED — ${r.hardBlocks.size} hard gate(s)"
                                    else "AMBER — ${r.warnings.size} warnings (dismissable)"
                                    Text(verdict, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (r.exitGateGreen) Color(0xFF2E7D32) else if (!r.passes) MaterialTheme.colorScheme.error else Color(0xFFB26A00))
                                }
                            }
                        }
                    }
                    items(report?.checks ?: emptyList()) { check ->
                        SuiteCheckRow(check) { viewModel.dismissCheck(check.key) }
                    }
                }
            }

            // ── Right column: chapter cards, worksheet, scorecard, export ──
            Column(
                Modifier.weight(0.42f).fillMaxSize().padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            ) {
                Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chapters.sortedBy { it.chapterNumber }) { ch ->
                        ChapterRow(
                            chapter = ch,
                            selected = ch.id == selectedChapterId,
                            onClick = { viewModel.selectChapter(ch.id) }
                        )
                    }
                }
                HorizontalDivider()
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedChapter != null) {
                        Text("Chapter ${selectedChapter.chapterNumber} — pre-publication (§6.8/§6.9)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val ws = remember(selectedChapter.id, selectedChapter.worksheet) {
                            PreDraftWorksheet.of(selectedChapter)
                        }
                        var wsText by remember(selectedChapter.id, selectedChapter.worksheet) {
                            mutableStateOf(ws.objective + "\n" + ws.characterDevelopment + "\n" + ws.plotDevelopment + "\n" + ws.readerExperience + "\n" + ws.chapterEnding)
                        }
                        OutlinedTextField(
                            value = wsText,
                            onValueChange = { wsText = it },
                            label = { Text("Worksheet A-E (one section per line)") },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val lines = wsText.lines().map { it.trim() }.plus(List(5) { "" }).take(5)
                                viewModel.saveWorksheet(selectedChapter, PreDraftWorksheet.Worksheet(lines[0], lines[1], lines[2], lines[3], lines[4]))
                            }) { Text("Save worksheet", fontSize = 12.sp) }
                            val band = selectedChapter.qcTotal?.let { QcScorecard.Band.of(it) }
                            if (band != null) {
                                AssistChip(onClick = {}, label = { Text("${selectedChapter.qcTotal}/14 ${band.name}", fontSize = 11.sp) })
                            } else {
                                AssistChip(onClick = {}, label = { Text("unscored", fontSize = 11.sp) })
                            }
                        }
                        ScorecardEditor(selectedChapter, viewModel)
                        HorizontalDivider()
                        // Export profile picker + paste-ready package (§6.13)
                        Text("Platform export (§6.13 — paste-ready)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SerialExportProfiles.PROFILES.take(4).forEach { p ->
                                FilterChip(
                                    selected = p.id == exportProfileId,
                                    onClick = { viewModel.setExportProfile(p.id) },
                                    label = { Text(p.platform.split(" ").first(), fontSize = 11.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                viewModel.exportChapter { pkg -> clipboardPreview = pkg.clipboardBundle.take(600) }
                            }) { Text("Build paste-ready package", fontSize = 12.sp) }
                            TextButton(onClick = { viewModel.releaseChapter(selectedChapter.id) }) { Text("Mark released", fontSize = 12.sp) }
                        }
                        clipboardPreview?.let {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                Text(it, fontSize = 10.sp, modifier = Modifier.padding(8.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                        TextButton(onClick = { showPreFlight = true }) { Text("Export pre-flight checklist (§6.9)", fontSize = 12.sp) }
                    } else {
                        Text("Select a chapter to edit its worksheet, scorecard and export.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showPreFlight) {
        AlertDialog(
            onDismissRequest = { showPreFlight = false },
            confirmButton = { TextButton(onClick = { showPreFlight = false }) { Text("Acknowledge") } },
            title = { Text("Export pre-flight (§6.9)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The ten final drafting principles:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    viewModel.preFlightChecklist.take(10).forEachIndexed { i, p -> Text("${i + 1}. $p", fontSize = 11.sp) }
                    Spacer(Modifier.height(6.dp))
                    Text("The four ultimate rules (verbatim):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    viewModel.preFlightChecklist.drop(10).forEach { p -> Text("• $p", fontSize = 11.sp) }
                }
            }
        )
    }
}

@Composable
private fun BufferGauge(report: BufferCadence.BufferReport) {
    val color = when (report.level) {
        BufferCadence.BufferLevel.GREEN -> Color(0xFF2E7D32)
        BufferCadence.BufferLevel.AMBER -> Color(0xFFB26A00)
        BufferCadence.BufferLevel.RED -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
            Text(
                " ${report.level.name} buffer: ${report.confirmedUnreleased} unreleased / target ${report.target} ",
                color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    Text(report.message, fontSize = 12.sp)
}

@Composable
private fun ArcCard(arc: SerialArc, chapters: List<Chapter>, viewModel: SerialDashboardViewModel) {
    val span = MicroArcPlanner.arcSpan(arc)
    var editing by remember { mutableStateOf(false) }
    var contribution by remember(arc.id, arc.contribution) { mutableStateOf(arc.contribution) }
    var conflict by remember(arc.id, arc.conflict) { mutableStateOf(arc.conflict) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Arc ${arc.arcIndex} — chapters ${span.first}-${span.last}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (editing) {
            OutlinedTextField(value = conflict, onValueChange = { conflict = it }, label = { Text("Localized conflict") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = contribution, onValueChange = { contribution = it }, label = { Text("Contribution to the larger novel") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    viewModel.updateArc(arc.copy(conflict = conflict, contribution = contribution))
                    editing = false
                }) { Text("Save", fontSize = 12.sp) }
                TextButton(onClick = { editing = false }) { Text("Cancel", fontSize = 12.sp) }
            }
        } else {
            Text("Conflict: ${arc.conflict}", fontSize = 11.sp)
            Text(
                if (arc.contribution.isNotBlank()) "Contribution: ${arc.contribution}" else "Contribution not declared (the completion requirement needs one).",
                fontSize = 11.sp,
                color = if (arc.contribution.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
            Text("Question ref: ${arc.questionRef ?: "—"} · answered ref: ${arc.answeredQuestionRef ?: "—"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { editing = true }) { Text("Edit arc", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, selected: Boolean, onClick: () -> Unit) {
    val released = BufferCadence.isReleased(chapter)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ch. ${chapter.chapterNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            Text(chapter.title.take(30), fontSize = 12.sp, modifier = Modifier.weight(1f))
            val chip = when {
                !WriterGate.isCanon(chapter) -> chapter.draftStatus to MaterialTheme.colorScheme.error
                released -> "released" to Color(0xFF2E7D32)
                else -> "buffer" to Color(0xFFB26A00)
            }
            Text(chip.first, fontSize = 10.sp, color = chip.second, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuiteCheckRow(check: SerialValidatorSuite.Check, onDismiss: () -> Unit) {
    val (icon, color) = when (check.severity) {
        SerialValidatorSuite.Severity.PASS -> "✓" to Color(0xFF2E7D32)
        SerialValidatorSuite.Severity.INFO -> "ℹ" to MaterialTheme.colorScheme.onSurfaceVariant
        SerialValidatorSuite.Severity.WARN -> "⚠" to Color(0xFFB26A00)
        SerialValidatorSuite.Severity.HARD -> "⛔" to MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
        Text(icon, color = color, fontSize = 12.sp, modifier = Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(check.message, fontSize = 11.sp)
            if (check.dismissable) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("Dismiss with note", fontSize = 10.sp)
                }
            }
        }
    }
}

/** The seven-criteria scorecard editor (§6.9): 0-2 each; the writer sets finals. */
@Composable
private fun ScorecardEditor(chapter: Chapter, viewModel: SerialDashboardViewModel) {
    val current = remember(chapter.id, chapter.qcScores) {
        QcScorecard.of(chapter)?.scores ?: List(7) { 1 }
    }
    var scores by remember(chapter.id, chapter.qcScores) { mutableStateOf(current) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Scorecard (7 × 0-2, max 14 — L6 proposes, you decide)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        QcScorecard.CRITERIA.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name.removePrefix("${i + 1}. "), fontSize = 10.sp, modifier = Modifier.weight(1f))
                (0..2).forEach { v ->
                    FilterChip(
                        selected = scores[i] == v,
                        onClick = { scores = scores.toMutableList().also { it[i] = v } },
                        label = { Text("$v", fontSize = 9.sp) },
                        modifier = Modifier.padding(0.dp).height(26.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total: ${scores.sum()}/14", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { viewModel.saveScorecard(chapter, scores, List(7) { "" }) }) { Text("Save scores", fontSize = 11.sp) }
            if (scores[1] == 0 || scores[5] == 0) {
                Text("⚠ zero-trigger active (criterion 2 or 6)", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
