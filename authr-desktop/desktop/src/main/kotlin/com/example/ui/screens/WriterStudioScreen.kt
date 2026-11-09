package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.QuestionEntry
import com.example.engine.AnnotationKind
import com.example.engine.AstAnnotation
import com.example.engine.ChekhovLedger
import com.example.engine.DraftState
import com.example.engine.QuestionLedger
import com.example.engine.Severity
import com.example.engine.WriterGate
import com.example.ui.viewmodel.WriterStudioViewModel

/**
 * Writer Studio — the v3.0 editing surface (plan §4.1).
 *
 * Interactive Mode: per-chapter editor with the live margin rail rendering
 * the manuscript AST of confirmed text. Autonomous Mode: a batch review
 * queue of PROPOSED chapters with accept/edit/confirm actions and
 * keyboard-first navigation (↑/↓ move, ⏎ confirm, E edit, A archive,
 * R regenerate).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriterStudioScreen(
    viewModel: WriterStudioViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refreshBooks() }

    val books by viewModel.books.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val openChapterId by viewModel.openChapterId.collectAsState()
    val editorText by viewModel.editorText.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val status by viewModel.status.collectAsState()
    val gate by viewModel.gateReport.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val ledgerCounts by viewModel.ledgerCounts.collectAsState()
    val guns by viewModel.guns.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val compileGate by viewModel.compileGate.collectAsState()

    LaunchedEffect(books) {
        if (selectedBookId == null && books.isNotEmpty()) viewModel.selectBook(books.first().id)
    }

    val openChapter = chapters.firstOrNull { it.id == openChapterId }
    val reviewQueue = WriterGate.proposedChapters(chapters)

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // ---- Header: mode switch, book picker, sovereignty gate chip ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Story") }
            Text("Writer Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = mode == WriterStudioViewModel.StudioMode.INTERACTIVE,
                    onClick = { viewModel.setMode(WriterStudioViewModel.StudioMode.INTERACTIVE) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Interactive") }
                SegmentedButton(
                    selected = mode == WriterStudioViewModel.StudioMode.AUTONOMOUS,
                    onClick = { viewModel.setMode(WriterStudioViewModel.StudioMode.AUTONOMOUS) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Autonomous") }
            }
            Spacer(Modifier.weight(1f))
            BookPicker(books = books, selectedId = selectedBookId, onSelect = { viewModel.selectBook(it) })
        }

        Spacer(Modifier.height(6.dp))

        GateChip(gate = gate, onRefresh = { viewModel.refreshGateReport() })

        Spacer(Modifier.height(6.dp))

        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(6.dp))

        when (mode) {
            WriterStudioViewModel.StudioMode.INTERACTIVE -> {
                Row(modifier = Modifier.fillMaxSize().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ---- Chapter list ----
                    ChapterList(
                        chapters = chapters,
                        openChapterId = openChapterId,
                        ledgerCounts = ledgerCounts,
                        onOpen = { viewModel.openChapter(it) },
                        modifier = Modifier.width(220.dp).fillMaxHeight()
                    )
                    // ---- Editor ----
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (openChapter == null) {
                            EmptyStudioHint(modifier = Modifier.fillMaxSize())
                        } else {
                            EditorHeader(
                                chapter = openChapter,
                                book = viewModel.selectedBook,
                                text = editorText,
                                isGenerating = isGenerating,
                                onConfirm = { viewModel.confirmChapter(openChapter.id) },
                                onArchive = { viewModel.archiveDraft(openChapter.id) },
                                onRegenerate = { viewModel.regenerateDraft(openChapter.id) },
                                onSave = { viewModel.saveEdits() }
                            )
                            OutlinedTextField(
                                value = editorText,
                                onValueChange = { viewModel.setEditorText(it) },
                                modifier = Modifier.fillMaxSize().weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Serif, lineHeight = 22.sp
                                ),
                                enabled = openChapter.draftStatus != DraftState.ARCHIVED
                            )
                        }
                    }
                    // ---- Margin rail + ledger panel (v3.0 P2) ----
                    Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                        MarginRail(
                            annotations = annotations,
                            chapter = openChapter,
                            modifier = Modifier.fillMaxWidth().weight(1.2f)
                        )
                        Spacer(Modifier.height(6.dp))
                        LedgerPanel(
                            guns = guns,
                            questions = questions,
                            compileGate = compileGate,
                            currentChapter = openChapter?.chapterNumber ?: chapters.size,
                            finalActStart = viewModel.finalActStartChapter(),
                            resolutionEnabled = openChapter?.let { WriterGate.isCanon(it) } ?: false,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onResolve = { gun ->
                                openChapter?.let { viewModel.resolveGun(gun.id, it, gun.quote.ifBlank { openChapter.content.takeLast(200) }) }
                            },
                            onWaive = { gun, note -> viewModel.waiveGun(gun.id, note) },
                            onAnswer = { q -> viewModel.answerQuestion(q.id, openChapter?.chapterNumber ?: q.openedChapter) }
                        )
                    }
                }
            }

            WriterStudioViewModel.StudioMode.AUTONOMOUS -> {
                ReviewQueue(
                    queue = reviewQueue,
                    cursor = queueIndex,
                    isGenerating = isGenerating,
                    onMove = { viewModel.moveQueueCursor(it) },
                    onConfirm = { viewModel.confirmCurrentQueueItem() },
                    onEdit = { viewModel.editCurrentQueueItem() },
                    onArchive = { viewModel.archiveCurrentQueueItem() },
                    onRegenerate = { viewModel.regenerateCurrentQueueItem() },
                    onOpenManually = { viewModel.openChapter(it) },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Header pieces
// ----------------------------------------------------------------------

@Composable
private fun BookPicker(books: List<Book>, selectedId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = books.firstOrNull { it.id == selectedId }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current?.title ?: "Select a project", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            books.forEach { book ->
                DropdownMenuItem(
                    text = { Text(book.title) },
                    onClick = { expanded = false; onSelect(book.id) }
                )
            }
        }
    }
}

@Composable
private fun GateChip(gate: WriterStudioViewModel.GateReport, onRefresh: () -> Unit) {
    val (color, label) = if (gate.holds) {
        Color(0xFF1B5E20) to
            "Writer gate holds — ${gate.canon} canon · ${gate.proposed} proposed · ${gate.archived} archived · ${gate.withheld.size} withheld from consumers"
    } else {
        Color(0xFFB71C1C) to "GATE VIOLATIONS: ${gate.violations.size}"
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onRefresh() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (!gate.holds) {
                gate.violations.take(3).forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Interactive Mode
// ----------------------------------------------------------------------

private fun statusColor(status: String): Color = when (status) {
    DraftState.PROPOSED -> Color(0xFFEF6C00)
    DraftState.CONFIRMED -> Color(0xFF2E7D32)
    DraftState.ARCHIVED -> Color(0xFF616161)
    else -> Color(0xFF1565C0)
}

@Composable
private fun ChapterList(
    chapters: List<Chapter>,
    openChapterId: Int?,
    ledgerCounts: Map<Int, Int>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        if (chapters.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No chapters in this project.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                items(chapters, key = { it.id }) { chapter ->
                    val isCanon = WriterGate.isCanon(chapter)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (chapter.id == openChapterId) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onOpen(chapter.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${chapter.chapterNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            val ledger = ledgerCounts[chapter.chapterNumber]?.let { " · $it promises" } ?: ""
                            Text(
                                "${chapter.draftStatus}${if (isCanon) " ✓" else ""}$ledger",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor(chapter.draftStatus)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorHeader(
    chapter: Chapter,
    book: Book?,
    text: String,
    isGenerating: Boolean,
    onConfirm: () -> Unit,
    onArchive: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit
) {
    val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
    val envelope = book?.let { "${it.wordMin}–${it.wordMax}" } ?: "—"
    val inEnvelope = book == null || (words in book.wordMin..book.wordMax)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Ch ${chapter.chapterNumber}: ${chapter.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1
        )
        AssistChip(
            onClick = {},
            label = { Text("$words words (envelope $envelope)") },
            leadingIcon = {
                Box(
                    Modifier.size(8.dp).background(
                        if (inEnvelope) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                        RoundedCornerShape(4.dp)
                    )
                )
            }
        )
        TextButton(onClick = onSave, enabled = !isGenerating) { Text("Save") }
        TextButton(onClick = onRegenerate, enabled = !isGenerating) {
            Text(if (isGenerating) "Drafting…" else "Regenerate")
        }
        TextButton(onClick = onArchive, enabled = !isGenerating) { Text("Archive") }
        Button(onClick = onConfirm, enabled = !isGenerating) { Text("Confirm Chapter ✓") }
    }
}

@Composable
private fun EmptyStudioHint(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Open a chapter from the list.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "L4 drafts land as PROPOSED; confirming freezes the text into canon (plan §4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------------------------
// Margin rail — the Live AST Annotator surface (Table 5)
// ----------------------------------------------------------------------

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.ERROR -> Color(0xFFB71C1C)
    Severity.WARN -> Color(0xFFEF6C00)
    Severity.INFO -> Color(0xFF1565C0)
}

private fun kindLabel(kind: AnnotationKind): String = when (kind) {
    AnnotationKind.BEAT -> "Beat"
    AnnotationKind.CLICHE_HIT -> "Cliché"
    AnnotationKind.CHEKHOV_GUN -> "Chekhov"
    AnnotationKind.LORE_REFERENCE -> "Lore"
    AnnotationKind.ENDING_SIGNAL -> "Ending"
    AnnotationKind.CONTINUITY_FLAG -> "Continuity"
}

@Composable
private fun MarginRail(
    annotations: List<AstAnnotation>,
    chapter: Chapter?,
    modifier: Modifier = Modifier
) {
    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Margin rail — Live AST", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${annotations.size} nodes", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                when {
                    chapter == null -> "Open a chapter to see its AST."
                    !WriterGate.isCanon(chapter) ->
                        "Annotates confirmed text only — chapter ${chapter.chapterNumber} is ${chapter.draftStatus}."
                    else -> "Chapter ${chapter.chapterNumber} (canon)."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (annotations.isEmpty()) {
                Text(
                    if (chapter != null && WriterGate.isCanon(chapter)) "No AST nodes — clean text."
                    else "",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(annotations, key = { "${it.kind}-${it.start}-${it.end}-${it.excerpt.hashCode()}" }) { a ->
                        RailNode(annotation = a)
                    }
                }
            }
        }
    }
}

@Composable
private fun RailNode(annotation: AstAnnotation) {
    val color = severityColor(annotation.severity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                kindLabel(annotation.kind),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.weight(1f))
            Text(annotation.severity.name, style = MaterialTheme.typography.labelSmall, color = color)
        }
        if (annotation.excerpt.isNotBlank()) {
            Text(
                "“${annotation.excerpt.take(80)}${if (annotation.excerpt.length > 80) "…" else ""}”",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Serif,
                maxLines = 2
            )
        }
        Text(annotation.message, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        Text(
            "→ ${annotation.suggestedAction.name.lowercase().replace('_', ' ')}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ----------------------------------------------------------------------
// Autonomous Mode — the batch review queue (keyboard-first)
// ----------------------------------------------------------------------

@Composable
private fun ReviewQueue(
    queue: List<Chapter>,
    cursor: Int,
    isGenerating: Boolean,
    onMove: (Int) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenManually: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(cursor, queue.size) {
        if (queue.isNotEmpty()) listState.animateScrollToItem(cursor.coerceIn(0, queue.size - 1))
    }
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Batch review queue — ${queue.size} PROPOSED draft(s). Keyboard: ↑/↓ move · ⏎ confirm · E edit · A archive · R regenerate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    when (e.key) {
                        Key.DirectionUp -> { onMove(-1); true }
                        Key.DirectionDown -> { onMove(1); true }
                        Key.Enter, Key.NumPadEnter -> { onConfirm(); true }
                        Key.E -> { onEdit(); true }
                        Key.A -> { onArchive(); true }
                        Key.R -> { onRegenerate(); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(onClick = { onMove(-1) }, enabled = queue.isNotEmpty()) { Text("↑") }
            TextButton(onClick = { onMove(1) }, enabled = queue.isNotEmpty()) { Text("↓") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onConfirm, enabled = queue.isNotEmpty() && !isGenerating) { Text("Confirm (⏎)") }
            TextButton(onClick = onEdit, enabled = queue.isNotEmpty()) { Text("Edit (E)") }
            TextButton(onClick = onRegenerate, enabled = queue.isNotEmpty() && !isGenerating) {
                Text(if (isGenerating) "Drafting…" else "Regenerate (R)")
            }
            TextButton(onClick = onArchive, enabled = queue.isNotEmpty() && !isGenerating) { Text("Archive (A)") }
        }
        Spacer(Modifier.height(6.dp))
        Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxSize()) {
            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Queue clear — no PROPOSED drafts await review.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    itemsIndexed(queue, key = { _, c -> c.id }) { index, chapter ->
                        val selected = index == cursor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onOpenManually(chapter.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${chapter.chapterNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chapter.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    chapter.content.split("\\s+".toRegex()).count { it.isNotBlank() }.toString() + " words · " +
                                        chapter.content.take(100).replace("\n", " ") + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Text(
                                "PROPOSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor(DraftState.PROPOSED),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Ledger panel (v3.0 P2) — Chekhov guns + question ledger, one surface
// (plan §6.7: "one ledger UI, two entry kinds")
// ----------------------------------------------------------------------

@Composable
private fun gunStatusColor(status: String): Color = when (status) {
    ChekhovLedger.INTRODUCED -> Color(0xFF1565C0)
    ChekhovLedger.ESCALATED -> Color(0xFF6A1B9A)
    ChekhovLedger.RESOLVED -> Color(0xFF2E7D32)
    ChekhovLedger.ORPHANED -> Color(0xFFC62828)
    ChekhovLedger.WAIVED -> Color(0xFF6D4C41)
    else -> Color.Gray
}

@Composable
private fun LedgerPanel(
    guns: List<ChekhovGun>,
    questions: List<QuestionEntry>,
    compileGate: ChekhovLedger.GateResult,
    currentChapter: Int,
    finalActStart: Int,
    modifier: Modifier = Modifier,
    /** Resolution happens in confirmed text only (Table 8 RESOLVED guard). */
    resolutionEnabled: Boolean = false,
    onResolve: (ChekhovGun) -> Unit,
    onWaive: (ChekhovGun, String) -> Unit,
    onAnswer: (QuestionEntry) -> Unit
) {
    var waiveTarget by remember { mutableStateOf<ChekhovGun?>(null) }
    var waiveNote by remember { mutableStateOf("") }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continuity Ledger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(
                        if (compileGate.blocked) "COMPILE BLOCKED" else "compile clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (compileGate.blocked) Color(0xFFC62828) else Color(0xFF2E7D32)
                    ) }
                )
            }
            Text(
                "Final act starts at ch. $finalActStart — unfired guns orphan automatically.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(guns, key = { _, g -> "gun-${g.id}" }) { _, gun ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            gun.name, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            "ch.${gun.introducedChapter}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            gun.status, style = MaterialTheme.typography.labelSmall,
                            color = gunStatusColor(gun.status), fontWeight = FontWeight.Bold
                        )
                        if (gun.status in ChekhovLedger.UNRESOLVED) {
                            TextButton(onClick = { onResolve(gun) }, enabled = resolutionEnabled) {
                                Text("Resolve", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { waiveTarget = gun; waiveNote = "" }) {
                                Text("Waive", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (gun.status == ChekhovLedger.WAIVED && gun.waiveNote.isNotBlank()) {
                            Text(
                                "note: ${gun.waiveNote.take(40)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                    }
                }
                itemsIndexed(questions, key = { _, q -> "q-${q.id}" }) { _, q ->
                    val overdue = QuestionLedger.isOverdue(q, currentChapter)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            q.text, style = MaterialTheme.typography.bodySmall,
                            color = if (q.answeredChapter != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f), maxLines = 2
                        )
                        Text(
                            when {
                                q.answeredChapter != null -> "answered ch.${q.answeredChapter}"
                                overdue -> "OVERDUE (due ch.${q.dueChapter})"
                                q.dueChapter != null -> "due ch.${q.dueChapter}"
                                else -> q.level
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                        )
                        if (q.answeredChapter == null) {
                            TextButton(onClick = { onAnswer(q) }) {
                                Text("Answer", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (waiveTarget != null) {
        AlertDialog(
            onDismissRequest = { waiveTarget = null },
            title = { Text("Waive \u201C${waiveTarget?.name}\u201D", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text("Waivers are never silent — your note is disclosed in the ship report.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = waiveNote,
                        onValueChange = { waiveNote = it },
                        placeholder = { Text("Mandatory note (e.g. sequel hook)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = waiveNote.isNotBlank(),
                    onClick = {
                        waiveTarget?.let { onWaive(it, waiveNote) }
                        waiveTarget = null
                    }
                ) { Text("Waive") }
            },
            dismissButton = { TextButton(onClick = { waiveTarget = null }) { Text("Cancel") } }
        )
    }
}
