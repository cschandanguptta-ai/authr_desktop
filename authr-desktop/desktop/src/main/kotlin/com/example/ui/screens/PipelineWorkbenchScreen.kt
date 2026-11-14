package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.pipeline.EditorialMemoryStore
import com.example.engine.pipeline.PipelineStage
import com.example.engine.pipeline.StagePrompts
import com.example.engine.pipeline.StageStatus
import com.example.ui.viewmodel.PipelineViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * The Pipeline Workbench — the ADR-0018 commercial authoring pipeline
 * surface. Nine stages from market research to publication prep, each with
 * a deterministic gate, writer approval checkpoint, and the persistent
 * editorial memory (decisions, issues, versions, engagement analytics)
 * rendered beside the stage tracker.
 *
 * Writer sovereignty: Run buttons produce PROPOSALS; Approve is the only
 * path to stage completion and is refused while the gate is red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineWorkbenchScreen(
    viewModel: PipelineViewModel,
    scope: CoroutineScope
) {
    LaunchedEffect(Unit) { viewModel.load(scope) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Authoring Pipeline", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Concept → publication through nine gated stages. Weak content is never auto-advanced; every stage needs your approval.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        // Book picker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Project: ", fontWeight = FontWeight.Medium)
            viewModel.books.forEach { book ->
                FilterChip(
                    selected = viewModel.bookId == book.id,
                    onClick = { viewModel.selectBook(scope, book.id) },
                    label = { Text(book.title, maxLines = 1) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        viewModel.message?.let {
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )) {
                Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxSize()) {
            // Left: stage tracker
            LazyColumn(Modifier.weight(1.2f).padding(end = 12.dp)) {
                items(PipelineStage.inOrder()) { stage ->
                    StageRow(viewModel, scope, stage)
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    MemoryPanel(viewModel, scope)
                }
            }

            // Right: last output + verdict
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Stage Output (proposal)", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val verdict = viewModel.lastVerdict
                if (verdict != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (verdict.passed) Color(0xFF1B3A1F) else Color(0xFF3A1B1B)
                        )
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "Gate: ${if (verdict.passed) "GREEN" else "RED"} — score ${"%.0f".format(verdict.score)}/100",
                                color = if (verdict.passed) Color(0xFF8FD694) else Color(0xFFE08A8A),
                                fontWeight = FontWeight.Bold
                            )
                            verdict.hardFailures.take(6).forEach {
                                Text("• $it", color = Color(0xFFE08A8A), style = MaterialTheme.typography.bodySmall)
                            }
                            verdict.warnings.take(6).forEach {
                                Text("• $it", color = Color(0xFFE0C58A), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Card {
                    Text(
                        viewModel.lastOutput.ifBlank { "Run a stage to see its proposal here." },
                        Modifier.padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                EngagementPanel(viewModel)
            }
        }
    }
}

@Composable
private fun StageRow(viewModel: PipelineViewModel, scope: CoroutineScope, stage: PipelineStage) {
    val state = viewModel.stageStates[stage]
    val status = state?.let { runCatching { StageStatus.valueOf(it.status) }.getOrNull() } ?: StageStatus.NOT_STARTED
    val gateGreen = state?.gatePassed == true
    val busy = viewModel.busyStage == stage
    val isFrontier = viewModel.frontierStage() == stage

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                status == StageStatus.APPROVED -> MaterialTheme.colorScheme.surfaceVariant
                isFrontier -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${stage.order}. ${stage.label}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    when (status) {
                        StageStatus.NOT_STARTED -> "not started"
                        StageStatus.RUNNING -> "running…"
                        StageStatus.AWAITING_APPROVAL -> if (gateGreen) "awaiting approval" else "gate RED"
                        StageStatus.APPROVED -> "approved"
                        StageStatus.BLOCKED -> "blocked"
                        StageStatus.FAILED -> "failed"
                    },
                    color = when {
                        status == StageStatus.APPROVED -> Color(0xFF8FD694)
                        status == StageStatus.BLOCKED || status == StageStatus.FAILED -> Color(0xFFE08A8A)
                        status == StageStatus.AWAITING_APPROVAL && !gateGreen -> Color(0xFFE08A8A)
                        status == StageStatus.AWAITING_APPROVAL -> Color(0xFFE0C58A)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(stage.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (state != null && (state.gateHardFailures.isNotEmpty() || state.gateWarnings.isNotEmpty())) {
                state.gateHardFailures.take(3).forEach {
                    Text("✖ $it", color = Color(0xFFE08A8A), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
                state.gateWarnings.take(2).forEach {
                    Text("△ $it", color = Color(0xFFE0C58A), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
            Row {
                when (stage) {
                    PipelineStage.MARKET_RESEARCH -> Button(
                        onClick = { viewModel.runMarketResearch(scope) }, enabled = !busy
                    ) { Text(if (busy) "Running…" else "Run research") }
                    PipelineStage.CONCEPT -> Button(
                        onClick = { viewModel.runConcept(scope) }, enabled = !busy
                    ) { Text(if (busy) "Running…" else "Develop concepts") }
                    PipelineStage.STORY_ARCHITECTURE -> {
                        var chapterCount by remember { mutableStateOf("12") }
                        OutlinedTextField(
                            value = chapterCount, onValueChange = { chapterCount = it.filter(Char::isDigit).take(3) },
                            label = { Text("Chapters") }, modifier = Modifier.width(90.dp),
                            singleLine = true, textStyle = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { viewModel.runStoryArchitecture(scope, chapterCount.toIntOrNull() ?: 12) },
                            enabled = !busy, modifier = Modifier.padding(start = 6.dp)
                        ) { Text(if (busy) "Running…" else "Design") }
                    }
                    PipelineStage.WORLDBUILDING -> Button(
                        onClick = { viewModel.runWorldbuilding(scope) }, enabled = !busy
                    ) { Text(if (busy) "Running…" else "Build bibles") }
                    PipelineStage.MANUSCRIPT -> Button(
                        onClick = { viewModel.evaluateManuscript(scope) }, enabled = !busy
                    ) { Text(if (busy) "Evaluating…" else "Evaluate canon") }
                    PipelineStage.EDITORIAL -> {
                        StagePrompts.EditorialPass.entries.forEach { pass ->
                            TextButton(
                                onClick = { viewModel.runEditorialPass(scope, pass) }, enabled = !busy
                            ) { Text(pass.label, fontSize = 11.sp) }
                        }
                    }
                    PipelineStage.ENGAGEMENT -> Button(
                        onClick = { viewModel.runEngagement(scope) }, enabled = !busy
                    ) { Text(if (busy) "Analyzing…" else "Analyze retention") }
                    PipelineStage.QUALITY_ASSURANCE -> Button(
                        onClick = { viewModel.runQualityAssurance(scope) }, enabled = !busy
                    ) { Text(if (busy) "Auditing…" else "Run QA audit") }
                    PipelineStage.PUBLICATION_PREP -> Button(
                        onClick = { viewModel.runPublicationPrep(scope) }, enabled = !busy
                    ) { Text(if (busy) "Running…" else "Prepare package") }
                }
                if (status == StageStatus.AWAITING_APPROVAL && gateGreen) {
                    Button(
                        onClick = { viewModel.approveStage(scope, stage) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(start = 6.dp)
                    ) { Text("Approve") }
                }
            }
        }
    }
}

@Composable
private fun MemoryPanel(viewModel: PipelineViewModel, scope: CoroutineScope) {
    Text("Editorial Memory", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Decisions (${viewModel.decisions.size})", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            viewModel.decisions.takeLast(8).reversed().forEach {
                Text("• [${it.stage}] ${it.decision} — ${it.rationale.take(120)}",
                    style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            val open = viewModel.issues.filter { it.status == "OPEN" }
            Text("Open issues (${open.size})", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            open.take(8).forEach { issue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "• [${issue.severity}] ${issue.description.take(140)}",
                        color = if (issue.severity == "HARD") Color(0xFFE08A8A) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall, fontSize = 10.sp,
                        modifier = Modifier.weight(1f), maxLines = 2
                    )
                    TextButton(onClick = { viewModel.resolveIssue(scope, issue) }) { Text("Resolve", fontSize = 10.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Versions (${viewModel.versions.size})", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            viewModel.versions.takeLast(6).reversed().forEach {
                Text("• ${it.label}: ${it.scope}, ${it.wordCount} words, ${it.chapterNumbers.size} chapters — ${it.note.take(80)}",
                    style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun EngagementPanel(viewModel: PipelineViewModel) {
    val eng = viewModel.engagement ?: return
    Text("Retention Analytics (measured, chapter-level)", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Card {
        Column(Modifier.padding(10.dp)) {
            Text(
                "Overall retention index: ${"%.1f".format(eng.overallIndex)}/100",
                fontWeight = FontWeight.Bold,
                color = when {
                    eng.overallIndex >= 75 -> Color(0xFF8FD694)
                    eng.overallIndex >= 55 -> Color(0xFFE0C58A)
                    else -> Color(0xFFE08A8A)
                }
            )
            Spacer(Modifier.height(6.dp))
            eng.chapters.take(20).forEach { c ->
                Text(
                    "Ch. ${c.chapterNumber}: idx ${c.retentionIndex} (hook ${c.hookScore}, tension ${c.tensionScore}, curiosity ${c.curiosityScore}, ending ${c.endingScore})",
                    style = MaterialTheme.typography.bodySmall, fontSize = 10.sp
                )
                c.notes.take(2).forEach {
                    Text("   △ $it", color = Color(0xFFE0C58A), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
        }
    }
}
