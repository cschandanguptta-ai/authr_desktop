package com.example.engine.context

import com.example.engine.pipeline.EditorialMemoryStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable

/**
 * TOKEN SPEND LEDGER (v3.0 port S2, spec ADR-0014 TokenBudgetEngine =
 * desktop ADR-0021): records actual token consumption per invocation and
 * per-project totals, and estimates stage cost before a run.
 *
 * The spec stores token_spend_ledger as a TABLE; the desktop port keeps
 * it in the editorial-memory slot reserved in schema v16/v17
 * (SLOT_TOKEN_SPEND) — same fields, one JSON payload, no migration.
 *
 * MODEL_COSTS: per-1M-token (input, output) USD, from the spec table.
 * Local Ollama generation is free; provider names here are the cost
 * lookup keys, not provider wiring.
 */
class TokenSpendLedger(private val memory: EditorialMemoryStore?) {

    @Serializable
    data class SpendEntry(
        val bookId: Int,
        val stage: String,
        val model: String,
        val provider: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val estimatedCostUsd: Double,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val totalTokens: Int get() = promptTokens + completionTokens
    }

    /** Spec MODEL_COSTS: per 1M tokens (input, output). */
    val modelCosts: Map<String, Pair<Double, Double>> = mapOf(
        "gpt-4o" to (2.50 to 10.00),
        "gpt-4o-mini" to (0.15 to 0.60),
        "gemini-1.5-flash" to (0.075 to 0.30),
        "gemini-1.5-pro" to (1.25 to 5.00),
        "ollama/local" to (0.0 to 0.0)
    )

    fun computeCost(model: String, promptTokens: Int, completionTokens: Int): Double {
        val (inCost, outCost) = modelCosts[model] ?: (0.0 to 0.0)
        return promptTokens / 1_000_000.0 * inCost + completionTokens / 1_000_000.0 * outCost
    }

    /** Record actual consumption (spec log_spend). Memory-null = compute only. */
    suspend fun logSpend(entry: SpendEntry) {
        val computed = entry.copy(estimatedCostUsd = computeCost(entry.model, entry.promptTokens, entry.completionTokens))
        memory ?: return
        val current = decode(memory.readSlotRaw(computed.bookId, slotName(computed.bookId)) ?: "[]")
        memory.writeSlot(computed.bookId, slotName(computed.bookId),
            json.encodeToString(ListSerializer(SpendEntry.serializer()), current + computed))
    }

    /** Spec get_project_spend: per-stage totals + project totals. */
    suspend fun projectSpend(bookId: Int): ProjectSpend {
        val entries = decode(memory?.readSlotRaw(bookId, slotName(bookId)) ?: "[]")
        val byStage = entries.groupBy { it.stage }.map { (stage, rows) ->
            StageSpend(
                stage = stage,
                tokens = rows.sumOf { it.totalTokens },
                costUsd = rows.sumOf { it.estimatedCostUsd },
                invocations = rows.size
            )
        }.sortedBy { it.stage }
        return ProjectSpend(
            byStage = byStage,
            totalTokens = entries.sumOf { it.totalTokens },
            totalCostUsd = entries.sumOf { it.estimatedCostUsd },
            invocations = entries.size
        )
    }

    /**
     * Spec estimate_stage_cost: pre-run estimate from the book's average
     * context/output tokens for the stage (or defaults for a first run).
     */
    suspend fun estimateStageCost(bookId: Int, stage: String, chapterCount: Int = 1): StageEstimate {
        val entries = decode(memory?.readSlotRaw(bookId, slotName(bookId)) ?: "[]")
            .filter { it.stage == stage }
        val avgContext = if (entries.isEmpty()) DEFAULT_AVG_CONTEXT
            else entries.map { it.promptTokens }.average().toInt()
        val avgOutput = if (entries.isEmpty()) DEFAULT_AVG_OUTPUT
            else entries.map { it.completionTokens }.average().toInt()
        val model = preferredModel(stage)
        return StageEstimate(
            model = model,
            estimatedInputTokens = avgContext * chapterCount,
            estimatedOutputTokens = avgOutput * chapterCount,
            estimatedCostUsd = computeCost(model, avgContext * chapterCount, avgOutput * chapterCount)
        )
    }

    /** Spec STAGE_MODEL_PREFERENCE (quality/economy/balanced). */
    private fun preferredModel(stage: String): String = when (stage) {
        "L4_SCENE_COMPOSER", "L5_STYLE_POLISHER" -> "gpt-4o"
        "SUMMARIZER" -> "gpt-4o-mini"
        else -> "gemini-1.5-flash"
    }

    private fun slotName(@Suppress("UNUSED_PARAMETER") bookId: Int) =
        com.example.data.model.EditorialMemory.SLOT_TOKEN_SPEND

    private fun decode(payload: String): List<SpendEntry> = try {
        json.decodeFromString(ListSerializer(SpendEntry.serializer()), payload)
    } catch (e: Exception) {
        emptyList() // corrupt payload: an empty ledger, never a crash mid-generation
    }

    @Serializable
    data class StageSpend(val stage: String, val tokens: Int, val costUsd: Double, val invocations: Int)

    @Serializable
    data class ProjectSpend(
        val byStage: List<StageSpend> = emptyList(),
        val totalTokens: Int = 0,
        val totalCostUsd: Double = 0.0,
        val invocations: Int = 0
    )

    @Serializable
    data class StageEstimate(
        val model: String,
        val estimatedInputTokens: Int,
        val estimatedOutputTokens: Int,
        val estimatedCostUsd: Double
    )

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        /** First-run averages before any ledger history exists. */
        const val DEFAULT_AVG_CONTEXT = 6_000
        const val DEFAULT_AVG_OUTPUT = 1_800
    }
}
