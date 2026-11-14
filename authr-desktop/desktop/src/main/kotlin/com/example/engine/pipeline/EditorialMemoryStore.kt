package com.example.engine.pipeline

import com.example.data.local.EditorialMemoryDao
import com.example.data.model.EditorialMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The persistent editorial intelligence layer (ADR-0018).
 *
 * One typed façade over the editorial_memory table. Every pipeline stage
 * reads its context through this store, so stages can never silently
 * disagree: market findings, positioning, personas, decisions, issues,
 * versions, stage states and engagement analytics all live here, per book.
 *
 * Design rules (from the invariants doc):
 *  - Payloads are JSON with [Serializable] DTOs — never raw prose parsed by
 *    regex; drift fails loud (SerializationException propagates).
 *  - Writes are slot-scoped REPLACE upserts; reads are single-row.
 *  - No LLM call ever happens inside the store (ADR-0004: guardrails local).
 */
class EditorialMemoryStore(private val dao: EditorialMemoryDao) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------
    // Payload DTOs

    @Serializable
    data class MarketFindings(
        val genreExpectations: List<String> = emptyList(),
        val readerDemographics: String = "",
        val comparableTitles: List<String> = emptyList(),
        val marketGaps: List<String> = emptyList(),
        val commercialOpportunities: List<String> = emptyList(),
        val summary: String = "",
        val generatedAt: Long = 0L
    )

    @Serializable
    data class Positioning(
        val premise: String = "",
        val differentiator: String = "",
        val sellingProposition: String = "",
        val targetReader: String = "",
        val promiseToReader: String = "",
        val avoidList: List<String> = emptyList(),
        val summary: String = "",
        val generatedAt: Long = 0L
    )

    @Serializable
    data class ReaderPersonas(
        val personas: List<ReaderPersona> = emptyList()
    ) {
        @Serializable
        data class ReaderPersona(
            val name: String = "",
            val demographics: String = "",
            val motivations: String = "",
            val dislikes: String = "",
            val platformHabits: String = ""
        )
    }

    @Serializable
    data class DecisionEntry(
        val stage: String = "",
        val decision: String = "",
        val rationale: String = "",
        val createdAt: Long = 0L
    )

    @Serializable
    data class IssueEntry(
        val kind: String = "",
        val severity: String = "",
        val description: String = "",
        val chapterNumber: Int? = null,
        val status: String = "OPEN",
        val createdAt: Long = 0L
    )

    @Serializable
    data class VersionEntry(
        val label: String = "",
        val scope: String = "",
        val stage: String = "",
        val chapterNumbers: List<Int> = emptyList(),
        val wordCount: Int = 0,
        val note: String = "",
        val createdAt: Long = 0L
    )

    @Serializable
    data class StageStateEntry(
        val stage: String = "",
        val status: String = "NOT_STARTED",
        val gatePassed: Boolean = false,
        val gateScore: Double = 0.0,
        val gateHardFailures: List<String> = emptyList(),
        val gateWarnings: List<String> = emptyList(),
        val updatedAt: Long = 0L
    )

    @Serializable
    data class EngagementSnapshot(
        val chapters: List<ChapterEngagement> = emptyList(),
        val overallIndex: Double = 0.0,
        val computedAt: Long = 0L
    ) {
        @Serializable
        data class ChapterEngagement(
            val chapterNumber: Int = 0,
            val hookScore: Double = 0.0,
            val pacingScore: Double = 0.0,
            val tensionScore: Double = 0.0,
            val curiosityScore: Double = 0.0,
            val endingScore: Double = 0.0,
            val retentionIndex: Double = 0.0,
            val notes: List<String> = emptyList()
        )
    }

    // ------------------------------------------------------------------
    // Slot readers (suspend + snapshot)

    suspend fun marketFindings(bookId: Int): MarketFindings =
        readDto(bookId, EditorialMemory.SLOT_MARKET, MarketFindings())

    suspend fun positioning(bookId: Int): Positioning =
        readDto(bookId, EditorialMemory.SLOT_POSITIONING, Positioning())

    suspend fun readerPersonas(bookId: Int): ReaderPersonas =
        readDto(bookId, EditorialMemory.SLOT_PERSONAS, ReaderPersonas())

    suspend fun decisions(bookId: Int): List<DecisionEntry> =
        readListDto(bookId, EditorialMemory.SLOT_DECISIONS, DecisionEntry.serializer())

    suspend fun issues(bookId: Int): List<IssueEntry> =
        readListDto(bookId, EditorialMemory.SLOT_ISSUES, IssueEntry.serializer())

    suspend fun versions(bookId: Int): List<VersionEntry> =
        readListDto(bookId, EditorialMemory.SLOT_VERSIONS, VersionEntry.serializer())

    suspend fun stageStates(bookId: Int): List<StageStateEntry> =
        readListDto(bookId, EditorialMemory.SLOT_STAGE_STATE, StageStateEntry.serializer())

    suspend fun engagement(bookId: Int): EngagementSnapshot =
        readDto(bookId, EditorialMemory.SLOT_ENGAGEMENT, EngagementSnapshot())

    /** Live observation of all memory rows for a book (UI wiring). */
    fun observeBook(bookId: Int): Flow<List<EditorialMemory>> = dao.observeAllForBook(bookId)

    /** Raw slot payload read (publication_package is free-form JSON kept verbatim). */
    suspend fun readSlotRaw(bookId: Int, slot: String): String? =
        dao.getSlot(bookId, slot)?.payload

    /** Raw slot payload write (additive slots beyond the typed set). */
    suspend fun writeSlot(bookId: Int, slot: String, payload: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getSlot(bookId, slot)
        dao.upsert(
            EditorialMemory(
                id = existing?.id ?: 0, bookId = bookId, slot = slot, payload = payload,
                createdAt = existing?.createdAt ?: now, lastModifiedAt = now
            )
        )
    }

    private inline fun <reified T : Any> readDto(
        bookId: Int,
        slot: String,
        default: T,
        serializer: KSerializer<T> = serializer<T>()
    ): T {
        val row = kotlinx.coroutines.runBlocking { dao.getSlot(bookId, slot) } ?: return default
        return try {
            json.decodeFromString(serializer, row.payload)
        } catch (e: Exception) {
            throw IllegalStateException(
                "editorial_memory slot '$slot' for book $bookId failed to parse (payload drift); " +
                    "raw=${row.payload.take(120)}… cause=${e.message}", e
            )
        }
    }

    private fun <T> readListDto(bookId: Int, slot: String, elementSerializer: KSerializer<T>): List<T> {
        val row = kotlinx.coroutines.runBlocking { dao.getSlot(bookId, slot) } ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(elementSerializer), row.payload)
        } catch (e: Exception) {
            throw IllegalStateException(
                "editorial_memory slot '$slot' for book $bookId failed to parse (payload drift); " +
                    "raw=${row.payload.take(120)}… cause=${e.message}", e
            )
        }
    }

    // ------------------------------------------------------------------
    // Slot writers

    suspend fun writeMarketFindings(bookId: Int, value: MarketFindings) =
        writeDto(bookId, EditorialMemory.SLOT_MARKET, value, MarketFindings.serializer())

    suspend fun writePositioning(bookId: Int, value: Positioning) =
        writeDto(bookId, EditorialMemory.SLOT_POSITIONING, value, Positioning.serializer())

    suspend fun writeReaderPersonas(bookId: Int, value: ReaderPersonas) =
        writeDto(bookId, EditorialMemory.SLOT_PERSONAS, value, ReaderPersonas.serializer())

    suspend fun appendDecision(bookId: Int, stage: PipelineStage, decision: String, rationale: String) {
        val current = decisions(bookId).toMutableList()
        current += DecisionEntry(stage.name, decision, rationale, System.currentTimeMillis())
        writeDto(bookId, EditorialMemory.SLOT_DECISIONS, current, ListSerializer(DecisionEntry.serializer()))
    }

    suspend fun upsertIssue(bookId: Int, issue: IssueEntry) {
        val current = issues(bookId).toMutableList()
        val idx = current.indexOfFirst { it.kind == issue.kind && it.description == issue.description }
        if (idx >= 0) current[idx] = issue else current += issue
        writeDto(bookId, EditorialMemory.SLOT_ISSUES, current, ListSerializer(IssueEntry.serializer()))
    }

    suspend fun resolveIssue(bookId: Int, kind: String, description: String) {
        val current = issues(bookId)
        writeDto(
            bookId, EditorialMemory.SLOT_ISSUES,
            current.map { if (it.kind == kind && it.description == description) it.copy(status = "RESOLVED") else it },
            ListSerializer(IssueEntry.serializer())
        )
    }

    suspend fun appendVersion(bookId: Int, entry: VersionEntry) {
        val current = versions(bookId).toMutableList()
        current += entry
        writeDto(bookId, EditorialMemory.SLOT_VERSIONS, current, ListSerializer(VersionEntry.serializer()))
    }

    suspend fun writeStageStates(bookId: Int, states: List<StageStateEntry>) =
        writeDto(bookId, EditorialMemory.SLOT_STAGE_STATE, states, ListSerializer(StageStateEntry.serializer()))

    suspend fun writeEngagement(bookId: Int, value: EngagementSnapshot) =
        writeDto(bookId, EditorialMemory.SLOT_ENGAGEMENT, value, EngagementSnapshot.serializer())

    private suspend fun <T> writeDto(bookId: Int, slot: String, value: T, serializer: KSerializer<T>) {
        val now = System.currentTimeMillis()
        val existing = dao.getSlot(bookId, slot)
        dao.upsert(
            EditorialMemory(
                id = existing?.id ?: 0,
                bookId = bookId,
                slot = slot,
                payload = json.encodeToString(serializer, value),
                createdAt = existing?.createdAt ?: now,
                lastModifiedAt = now
            )
        )
    }

    // ------------------------------------------------------------------
    // Stage-state helpers (the pipeline progress contract)

    /** Full stage map, gaps filled with NOT_STARTED defaults. */
    suspend fun fullStageStates(bookId: Int): Map<PipelineStage, StageStateEntry> {
        val stored = stageStates(bookId).associateBy { stageByName(it.stage) }
        return PipelineStage.inOrder().associateWith { stored[it] ?: StageStateEntry(stage = it.name) }
    }

    suspend fun setStageStatus(bookId: Int, stage: PipelineStage, status: StageStatus) {
        val map = fullStageStates(bookId).toMutableMap()
        map[stage] = (map[stage] ?: StageStateEntry(stage = stage.name)).copy(status = status.name, updatedAt = System.currentTimeMillis())
        writeStageStates(bookId, map.values.toList())
    }

    suspend fun recordGate(bookId: Int, stage: PipelineStage, verdict: GateVerdict) {
        val map = fullStageStates(bookId).toMutableMap()
        map[stage] = (map[stage] ?: StageStateEntry(stage = stage.name)).copy(
            gatePassed = verdict.passed,
            gateScore = verdict.score,
            gateHardFailures = verdict.hardFailures,
            gateWarnings = verdict.warnings,
            updatedAt = System.currentTimeMillis()
        )
        writeStageStates(bookId, map.values.toList())
    }

    /** The writer-approved + gate-green contract: a stage is DONE only when both hold. */
    suspend fun isStageComplete(bookId: Int, stage: PipelineStage): Boolean {
        val s = fullStageStates(bookId)[stage] ?: return false
        return s.status == StageStatus.APPROVED.name && s.gatePassed
    }

    suspend fun snapshotFor(bookId: Int, storyBible: String, characterBible: String, worldBible: String): EditorialMemorySnapshot =
        EditorialMemorySnapshot(
            marketFindings = marketFindings(bookId).takeIf { it.summary.isNotBlank() }?.summary,
            positioning = positioning(bookId).takeIf { it.summary.isNotBlank() }?.summary,
            readerPersonas = readerPersonas(bookId).takeIf { it.personas.isNotEmpty() }?.let { p ->
                p.personas.joinToString("; ") { "${it.name}: ${it.motivations}" }
            },
            storyBible = storyBible,
            characterBible = characterBible,
            worldBible = worldBible,
            decisions = decisions(bookId).map {
                EditorialDecisionRecord(0, stageByName(it.stage), it.decision, it.rationale, it.createdAt)
            },
            openIssues = issues(bookId).filter { it.status == "OPEN" }.map {
                EditorialIssueRecord(0, it.kind, it.severity, it.description, it.chapterNumber, it.status, it.createdAt)
            },
            voiceEvolutions = readSlotRaw(bookId, EditorialMemory.SLOT_VOICE_EVOLUTION)
        )

    // ------------------------------------------------------------------
    // ADR-0020 voice evolution (spec §1.4): author-declared intentional
    // voice shifts, stored in the voice_evolution slot as a JSON list of
    // VoiceEngine.VoiceEvolutionDto. Slot reads are raw here; the engine
    // owns (de)serialization so the DTO stays engine-local.

    suspend fun writeVoiceEvolutions(bookId: Int, payloadJson: String) =
        writeSlot(bookId, EditorialMemory.SLOT_VOICE_EVOLUTION, payloadJson)

    suspend fun voiceEvolutionsRaw(bookId: Int): String? =
        readSlotRaw(bookId, EditorialMemory.SLOT_VOICE_EVOLUTION)

    companion object {
        fun stageByName(name: String): PipelineStage =
            PipelineStage.entries.firstOrNull { it.name == name } ?: PipelineStage.MARKET_RESEARCH
    }
}
