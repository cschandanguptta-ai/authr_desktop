package com.example.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.repository.AppRepository
import com.example.engine.pipeline.EditorialMemoryStore
import com.example.engine.pipeline.Finding
import com.example.engine.pipeline.GateVerdict
import com.example.engine.pipeline.ManuscriptQaSuite
import com.example.engine.pipeline.PipelineOrchestrator
import com.example.engine.pipeline.PipelineStage
import com.example.engine.pipeline.RetentionAnalyzer
import com.example.engine.pipeline.StagePrompts
import com.example.engine.pipeline.StageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pipeline workbench VM (ADR-0018): the nine-stage commercial authoring
 * pipeline with quality gates and writer approval checkpoints.
 *
 * The VM is deliberately thin: all policy lives in PipelineOrchestrator /
 * StageGates / the memory store; this class only holds UI state and forwards
 * writer actions. Writer sovereignty holds: approveStage is the ONLY path
 * that marks a stage APPROVED, and the orchestrator refuses when the gate
 * is red.
 */
class PipelineViewModel(
    private val context: Context,
    private val repository: AppRepository,
    private val memory: EditorialMemoryStore,
    /** ADR-0020 voice engine (stage-7 drift axis); null keeps stage 7 retention-only. */
    voiceEngine: com.example.engine.voice.VoiceEngine? = null
) {
    private val orchestrator = PipelineOrchestrator(context, repository, memory, voiceEngine)

    var bookId by mutableStateOf<Int?>(null)
        private set
    var books by mutableStateOf<List<com.example.data.model.Book>>(emptyList())
        private set
    var stageStates by mutableStateOf<Map<PipelineStage, EditorialMemoryStore.StageStateEntry>>(emptyMap())
        private set
    var busyStage by mutableStateOf<PipelineStage?>(null)
        private set
    var lastOutput by mutableStateOf<String>("")
        private set
    var lastVerdict by mutableStateOf<GateVerdict?>(null)
        private set
    var decisions by mutableStateOf<List<EditorialMemoryStore.DecisionEntry>>(emptyList())
        private set
    var issues by mutableStateOf<List<EditorialMemoryStore.IssueEntry>>(emptyList())
        private set
    var engagement by mutableStateOf<EditorialMemoryStore.EngagementSnapshot?>(null)
        private set
    var versions by mutableStateOf<List<EditorialMemoryStore.VersionEntry>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            books = repository.getBooksSync()
            val id = bookId ?: books.firstOrNull()?.id
            bookId = id
            if (id != null) refresh(id)
        }
    }

    fun selectBook(scope: CoroutineScope, id: Int) {
        bookId = id
        scope.launch(Dispatchers.IO) { refresh(id) }
    }

    private suspend fun refresh(id: Int) {
        stageStates = memory.fullStageStates(id)
        decisions = memory.decisions(id)
        issues = memory.issues(id)
        engagement = memory.engagement(id).takeIf { it.chapters.isNotEmpty() }
        versions = memory.versions(id)
    }

    private fun run(scope: CoroutineScope, stage: PipelineStage, block: suspend () -> PipelineOrchestrator.StageRunResult) {
        scope.launch(Dispatchers.IO) {
            busyStage = stage
            message = null
            try {
                val result = block()
                lastOutput = result.output
                lastVerdict = result.verdict
                message = when {
                    result.error != null -> "Stage ${stage.label} failed: ${result.error}"
                    result.verdict?.passed == true -> "Stage ${stage.label} gate GREEN — awaiting your approval."
                    result.verdict != null -> "Stage ${stage.label} gate RED — fix the hard findings before advancing."
                    else -> "Stage ${stage.label} ran."
                }
                bookId?.let { refresh(it) }
            } catch (e: Exception) {
                message = "Stage ${stage.label} error: ${e.message}"
            } finally {
                busyStage = null
            }
        }
    }

    fun runMarketResearch(scope: CoroutineScope, direction: String = "") =
        run(scope, PipelineStage.MARKET_RESEARCH) { orchestrator.runMarketResearch(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.MARKET_RESEARCH, false, null, "", "no book")) }

    fun runConcept(scope: CoroutineScope) =
        run(scope, PipelineStage.CONCEPT) { orchestrator.runConcept(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.CONCEPT, false, null, "", "no book")) }

    fun runStoryArchitecture(scope: CoroutineScope, chapterCount: Int) =
        run(scope, PipelineStage.STORY_ARCHITECTURE) { orchestrator.runStoryArchitecture(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.STORY_ARCHITECTURE, false, null, "", "no book"), chapterCount) }

    fun runWorldbuilding(scope: CoroutineScope) =
        run(scope, PipelineStage.WORLDBUILDING) { orchestrator.runWorldbuilding(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.WORLDBUILDING, false, null, "", "no book")) }

    fun evaluateManuscript(scope: CoroutineScope) =
        run(scope, PipelineStage.MANUSCRIPT) { orchestrator.evaluateManuscriptStage(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.MANUSCRIPT, false, null, "", "no book")) }

    fun runEditorialPass(scope: CoroutineScope, pass: StagePrompts.EditorialPass, chapterNumber: Int? = null) =
        run(scope, PipelineStage.EDITORIAL) { orchestrator.runEditorialPass(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.EDITORIAL, false, null, "", "no book"), pass, chapterNumber) }

    fun runEngagement(scope: CoroutineScope) =
        run(scope, PipelineStage.ENGAGEMENT) { orchestrator.runEngagement(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.ENGAGEMENT, false, null, "", "no book")) }

    fun runQualityAssurance(scope: CoroutineScope) =
        run(scope, PipelineStage.QUALITY_ASSURANCE) { orchestrator.runQualityAssurance(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.QUALITY_ASSURANCE, false, null, "", "no book")) }

    fun runPublicationPrep(scope: CoroutineScope) =
        run(scope, PipelineStage.PUBLICATION_PREP) { orchestrator.runPublicationPrep(bookId ?: return@run PipelineOrchestrator.StageRunResult(PipelineStage.PUBLICATION_PREP, false, null, "", "no book")) }

    /** The writer checkpoint: refused by the orchestrator when the gate is red. */
    fun approveStage(scope: CoroutineScope, stage: PipelineStage, note: String = "") {
        scope.launch(Dispatchers.IO) {
            val ok = orchestrator.approveStage(bookId ?: return@launch, stage, note)
            message = if (ok) "Stage ${stage.label} APPROVED — next stage unlocked."
            else "Approval refused: the ${stage.label} gate is red."
            bookId?.let { refresh(it) }
        }
    }

    fun resolveIssue(scope: CoroutineScope, issue: EditorialMemoryStore.IssueEntry) {
        scope.launch(Dispatchers.IO) {
            memory.resolveIssue(bookId ?: return@launch, issue.kind, issue.description)
            bookId?.let { refresh(it) }
        }
    }

    /** Convenience for the UI: which stage is the current frontier. */
    fun frontierStage(): PipelineStage? =
        PipelineStage.inOrder().firstOrNull { s -> stageStates[s]?.status != StageStatus.APPROVED.name }

    fun statusColor(status: StageStatus): String = when (status) {
        StageStatus.NOT_STARTED -> "gray"
        StageStatus.RUNNING -> "blue"
        StageStatus.AWAITING_APPROVAL -> "amber"
        StageStatus.APPROVED -> "green"
        StageStatus.BLOCKED -> "red"
        StageStatus.FAILED -> "red"
    }
}
