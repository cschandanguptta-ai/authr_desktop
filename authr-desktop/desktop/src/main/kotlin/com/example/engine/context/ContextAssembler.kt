package com.example.engine.context

import com.example.data.local.SummaryCacheDao
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.data.model.SummaryCache

/**
 * CONTEXT ASSEMBLER (v3.0 port S2, spec ADR-0014 = desktop ADR-0021):
 * replaces the flat "all confirmed chapters concatenated" context with a
 * nine-layer, stage-budgeted prefix (spec engine-enhancements §2.2/2.5).
 *
 * Layers (spec architecture):
 *   1 system            — role instructions for the stage agent
 *   2 voice             — persona card (+ evolution note when active)
 *   3 scene_plan        — beat sheet / chapter worksheet
 *   4 recent_manuscript — freshness-ranked sliding window (N-1 full,
 *                         N-2 medium, N-3..5 brief, N-6+ oneliner)
 *   5 outline           — the book outline
 *   6 worldbible        — bibles + active lorebook keys
 *   7 retrieved         — epistemic-isolation-scoped RAG surface (S3: the
 *                         vector store; today the lorebook + scene-relevant
 *                         canon extracts)
 *   8 editorial         — open editorial issues for this chapter
 *   9 foreshadowing     — unresolved narrative promises
 *
 * Budget: percentage allocation per stage (spec STAGE_ALLOCATIONS),
 * available = maxContext − outputReserve; every layer is truncated to its
 * slice and the assembled prefix is verified against the total (Invariant
 * 16: total ≤ available). Overflow compresses the manuscript layer by
 * level downgrade first (spec compress_overflow doctrine: degrade the
 * largest, cheapest-to-shrink layer before touching the others).
 *
 * The assembler is pure Kotlin: no android imports, DAO optional, so the
 * JVM test routes exercise it Room-free.
 */
class ContextAssembler(
    private val summaries: SummaryCacheService = SummaryCacheService(),
    private val dao: SummaryCacheDao? = null
) {

    /** The nine layers, in spec order (assembly + budget log sequence). */
    enum class Layer {
        SYSTEM, VOICE, SCENE_PLAN, RECENT_MANUSCRIPT, OUTLINE,
        WORLDBIBLE, RETRIEVED, EDITORIAL, FORESHADOWING
    }

    /** Which pipeline stage the assembly serves (spec allocation key). */
    enum class StageKey { L4_SCENE_COMPOSER, L5_STYLE_POLISHER, L6_CONTINUITY_MANAGER }

    /**
     * Spec STAGE_ALLOCATIONS, percentage of the available window per
     * layer, buffer included. Rows sum to 1.00.
     */
    val stageAllocations: Map<StageKey, Map<Layer, Double>> = mapOf(
        StageKey.L4_SCENE_COMPOSER to mapOf(
            Layer.SYSTEM to 0.05, Layer.VOICE to 0.05, Layer.SCENE_PLAN to 0.08,
            Layer.RECENT_MANUSCRIPT to 0.25, Layer.OUTLINE to 0.10, Layer.WORLDBIBLE to 0.15,
            Layer.RETRIEVED to 0.12, Layer.EDITORIAL to 0.05, Layer.FORESHADOWING to 0.05
        ),
        StageKey.L5_STYLE_POLISHER to mapOf(
            Layer.SYSTEM to 0.05, Layer.VOICE to 0.10, Layer.SCENE_PLAN to 0.03,
            Layer.RECENT_MANUSCRIPT to 0.15, Layer.OUTLINE to 0.05, Layer.WORLDBIBLE to 0.05,
            Layer.RETRIEVED to 0.07, Layer.EDITORIAL to 0.05, Layer.FORESHADOWING to 0.05
        ),
        StageKey.L6_CONTINUITY_MANAGER to mapOf(
            Layer.SYSTEM to 0.05, Layer.VOICE to 0.03, Layer.SCENE_PLAN to 0.03,
            Layer.RECENT_MANUSCRIPT to 0.35, Layer.OUTLINE to 0.05, Layer.WORLDBIBLE to 0.20,
            Layer.RETRIEVED to 0.10, Layer.EDITORIAL to 0.10, Layer.FORESHADOWING to 0.05
        )
    )

    /** Spec buffer: share of available held back (10% L4, 40% L5, 4% L6). */
    private val stageBuffer: Map<StageKey, Double> = mapOf(
        StageKey.L4_SCENE_COMPOSER to 0.10,
        StageKey.L5_STYLE_POLISHER to 0.40,
        StageKey.L6_CONTINUITY_MANAGER to 0.04
    )

    data class ModelSpec(
        val name: String,
        val maxContext: Int,
        val outputReserve: Int
    ) {
        val available: Int get() = maxContext - outputReserve
    }

    /** One assembly result: the prefix, the accounting, the log rows. */
    data class AssembledContext(
        val promptPrefix: String,
        val tokenCount: Int,
        val budgetUtilization: Double,
        /** Tokens per layer AFTER truncation (the budget log body). */
        val layerTokens: Map<Layer, Int>,
        /** Layer budgets in tokens (context_budget_log). */
        val layerBudgets: Map<Layer, Int>,
        val stage: StageKey,
        val model: ModelSpec,
        /** Chapters whose summary level was downgraded by overflow. */
        val downgrades: List<String>
    )

    /**
     * Assemble the context prefix for one invocation (spec
     * assemble_context). [confirmed] is the writer-confirmed canon BEFORE
     * the target chapter (ascending); [targetChapter] is the chapter
     * being written/checked — its beat sheet is the scene-plan layer and
     * its content never rides the manuscript window.
     *
     * v3.0 S3 (ADR-0022): when a [ragEngine] is provided, layer 7 is the
     * hybrid RAG retrieval over the confirmed corpus (epistemically
     * isolated: chapter ≤ k AND confirmed, Invariant 12) + research
     * sources; without one it stays the lorebook keyword surface (the
     * S2 fallback).
     */
    suspend fun assemble(
        book: Book,
        confirmed: List<Chapter>,
        targetChapter: Chapter,
        stage: StageKey,
        model: ModelSpec,
        personaCard: String = "",
        systemInstructions: String = defaultSystemInstructions(stage),
        lorebook: List<Lorebook> = emptyList(),
        editorialIssues: List<String> = emptyList(),
        openPromises: List<String> = emptyList(),
        evolutionNote: String = "",
        ragEngine: com.example.engine.rag.RagEngine? = null,
        researchSources: List<com.example.data.model.ResearchSource> = emptyList()
    ): AssembledContext {
        val budgets = allocateBudget(stage, model)
        val layers = LinkedHashMap<Layer, String>()

        layers[Layer.SYSTEM] = truncate(systemInstructions, budgets.getValue(Layer.SYSTEM))
        layers[Layer.VOICE] = truncate(
            listOfNotNull(personaCard.ifBlank { null }, evolutionNote.ifBlank { null }).joinToString("\n"),
            budgets.getValue(Layer.VOICE)
        )
        // The scene plan is the TARGET chapter's beat sheet (its summary
        // card), not part of the confirmed window.
        layers[Layer.SCENE_PLAN] = truncate(targetChapter.summary, budgets.getValue(Layer.SCENE_PLAN))
        layers[Layer.RECENT_MANUSCRIPT] = assembleRecentManuscript(
            book.id, confirmed, targetChapter.chapterNumber, budgets.getValue(Layer.RECENT_MANUSCRIPT)
        )
        layers[Layer.OUTLINE] = truncate(book.outline, budgets.getValue(Layer.OUTLINE))
        layers[Layer.WORLDBIBLE] = truncate(
            listOfNotNull(
                book.storyBible.ifBlank { null },
                book.characterBible.ifBlank { null },
                book.worldBible.ifBlank { null }
            ).joinToString("\n\n"),
            budgets.getValue(Layer.WORLDBIBLE)
        )
        // Layer 7: RAG retrieval when wired (ADR-0022), lorebook surface
        // otherwise. The retrieval query is the spec's build_retrieval_query:
        // beat sheet + lorebook keys + writer instruction.
        layers[Layer.RETRIEVED] = if (ragEngine != null) {
            val corpus = ragEngine.buildCorpus(confirmed, researchSources, targetChapter.chapterNumber)
            val query = ragEngine.buildRetrievalQuery(targetChapter.summary, lorebook)
            ragEngine.formatRetrieved(
                ragEngine.retrieve(corpus, query, targetChapter.chapterNumber, budgets.getValue(Layer.RETRIEVED))
            )
        } else {
            truncate(
                lorebook.joinToString("\n") { "- ${it.key}: ${it.value}" },
                budgets.getValue(Layer.RETRIEVED)
            )
        }
        layers[Layer.EDITORIAL] = truncate(
            editorialIssues.joinToString("\n") { "- $it" },
            budgets.getValue(Layer.EDITORIAL)
        )
        layers[Layer.FORESHADOWING] = truncate(
            openPromises.joinToString("\n") { "- $it" },
            budgets.getValue(Layer.FORESHADOWING)
        )

        // Invariant 16: total ≤ available. Overflow first downgrades the
        // manuscript layer (the largest, cheapest to shrink), then drops
        // the oldest oneliners.
        var downgrades = mutableListOf<String>()
        var total = layers.values.sumOf { summaries.estimateTokens(it) }
        if (total > model.available) {
            val trimmed = assembleRecentManuscript(
                book.id, confirmed, targetChapter.chapterNumber,
                (budgets.getValue(Layer.RECENT_MANUSCRIPT) * 0.5).toInt(),
                downgradeAll = true
            )
            layers[Layer.RECENT_MANUSCRIPT] = trimmed
            downgrades += "recent_manuscript"
            total = layers.values.sumOf { summaries.estimateTokens(it) }
        }

        val prefix = layers.entries
            .filter { it.value.isNotBlank() }
            .joinToString("\n\n---\n\n") { "[${layerName(it.key)}]\n${it.value}" }

        return AssembledContext(
            promptPrefix = prefix,
            tokenCount = total,
            budgetUtilization = if (model.available > 0) total.toDouble() / model.available else 0.0,
            layerTokens = layers.mapValues { summaries.estimateTokens(it.value) },
            layerBudgets = budgets,
            stage = stage,
            model = model,
            downgrades = downgrades
        )
    }

    /**
     * Freshness-ranked manuscript window (spec assemble_recent_manuscript):
     * N-1 full, N-2 medium, N-3..N-5 brief, N-6+ oneliner; each chapter
     * downgrades one level further if its slice overflows the remaining
     * budget (spec LEVEL_DOWNGRADE path).
     */
    suspend fun assembleRecentManuscript(
        bookId: Int,
        confirmed: List<Chapter>,
        targetChapterNumber: Int,
        maxTokens: Int,
        downgradeAll: Boolean = false
    ): String {
        val earlier = confirmed
            .filter { it.chapterNumber < targetChapterNumber }
            .sortedByDescending { it.chapterNumber } // nearest first
        val parts = mutableListOf<String>()
        var remaining = maxTokens

        for ((idx, chapter) in earlier.withIndex()) {
            val offset = idx + 1 // 1 = previous chapter
            val level = when {
                downgradeAll -> SummaryCache.LEVEL_ONELINER
                offset == 1 -> SummaryCache.LEVEL_FULL
                offset == 2 -> SummaryCache.LEVEL_MEDIUM
                offset <= 5 -> SummaryCache.LEVEL_BRIEF
                else -> SummaryCache.LEVEL_ONELINER
            }
            val summary = summaries.getOrCreate(
                bookId, "chapter:${chapter.chapterNumber}", level, chapter.content
            )
            if (summary.tokenCount <= remaining) {
                parts += "--- Chapter ${chapter.chapterNumber} ($level) ---\n${summary.summaryText}"
                remaining -= summary.tokenCount
            } else {
                val shorter = SummaryCache.LEVEL_DOWNGRADE[level]
                if (shorter != null) {
                    val downgraded = summaries.getOrCreate(
                        bookId, "chapter:${chapter.chapterNumber}", shorter, chapter.content
                    )
                    if (downgraded.tokenCount <= remaining) {
                        parts += "--- Chapter ${chapter.chapterNumber} ($shorter) ---\n${downgraded.summaryText}"
                        remaining -= downgraded.tokenCount
                    }
                }
            }
            if (remaining <= 0) break
        }
        return parts.joinToString("\n\n")
    }

    /** Spec allocate_token_budget: available × pct, buffer held back. */
    fun allocateBudget(stage: StageKey, model: ModelSpec): Map<Layer, Int> {
        val alloc = stageAllocations.getValue(stage)
        val bufferPct = stageBuffer.getValue(stage)
        // The spec table's layer percentages are of the AVAILABLE window
        // with the buffer carved out first (rows + buffer sum to 1.00 in
        // the spec's stage tables).
        val layerPool = (model.available * (1.0 - bufferPct)).toInt()
        return Layer.entries.associateWith { layer ->
            (layerPool * alloc.getValue(layer)).toInt()
        }
    }

    /** Spec prompt_prefix format: [LAYER_NAME] headers. */
    fun layerName(layer: Layer): String = layer.name

    fun defaultSystemInstructions(stage: StageKey): String = when (stage) {
        StageKey.L4_SCENE_COMPOSER ->
            "You are the Scene Composer (L4): write the chapter in the author's voice, " +
                "show don't tell, keep continuity with the confirmed chapters only, " +
                "respect every world rule, land the beat sheet."
        StageKey.L5_STYLE_POLISHER ->
            "You are the Style Polisher (L5): polish the prose for rhythm and precision " +
                "without changing plot, facts or voice."
        StageKey.L6_CONTINUITY_MANAGER ->
            "You are the Continuity Manager (L6): check the text against the bibles, " +
                "the outline and the confirmed chapters; report every contradiction."
    }

    private fun truncate(text: String, maxTokens: Int): String {
        if (text.isBlank()) return ""
        if (summaries.estimateTokens(text) <= maxTokens) return text
        // Token-approximate truncation at the word level; keep sentence
        // boundaries where possible (never mid-word).
        val maxWords = (maxTokens * 3 / 4).coerceAtLeast(1)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cut = words.take(maxWords).joinToString(" ")
        // Snap back to the last sentence boundary in the second half (keeps
        // complete sentences; never mid-word).
        val boundary = Regex("[.!?]").findAll(cut).lastOrNull()?.range?.last
        val end = if (boundary != null && boundary + 1 > cut.length / 2) boundary + 1 else cut.length
        return cut.substring(0, end).trim()
    }

    // ------------------------------------------------------------------
    // Budget log (spec context_budget_log; the editorial-memory slot is
    // written by the caller — the assembler stays UI/DB-free).

    /** Serializable budget-log entry shape (slot payload element). */
    @kotlinx.serialization.Serializable
    data class BudgetLogEntry(
        val stage: String,
        val chapterNumber: Int,
        val model: String,
        val totalTokens: Int,
        val availableTokens: Int,
        val utilization: Double,
        val layerTokens: Map<String, Int>,
        val createdAt: Long = System.currentTimeMillis()
    )

    fun toBudgetLogEntry(ctx: AssembledContext, chapterNumber: Int): BudgetLogEntry =
        BudgetLogEntry(
            stage = ctx.stage.name,
            chapterNumber = chapterNumber,
            model = ctx.model.name,
            totalTokens = ctx.tokenCount,
            availableTokens = ctx.model.available,
            utilization = ctx.budgetUtilization,
            layerTokens = ctx.layerTokens.mapKeys { it.key.name }
        )
}
