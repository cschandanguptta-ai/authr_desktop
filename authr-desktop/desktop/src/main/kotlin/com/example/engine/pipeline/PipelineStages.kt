package com.example.engine.pipeline

import com.example.data.model.Book
import com.example.data.model.Chapter

/**
 * The commercial authoring pipeline (ADR-0018): nine stages from market
 * research to publication prep, each with deterministic quality gates
 * between it and the next stage. Weak content is never auto-advanced.
 *
 * Stage contract (plan §2, the nine numbered pipeline stages):
 *   1. MARKET_RESEARCH    — demographics, genre expectations, market gaps
 *   2. CONCEPT            — premise, positioning, differentiated selling prop
 *   3. STORY_ARCHITECTURE — outline, narrative structure, chapter plan, arcs
 *   4. WORLDBUILDING      — character/world/story bibles, continuity rules
 *   5. MANUSCRIPT         — chapter generation (existing L4 flow, unchanged)
 *   6. EDITORIAL          — developmental → structural → line → copy → proof
 *   7. ENGAGEMENT         — pacing, tension, curiosity, chapter endings
 *   8. QUALITY_ASSURANCE  — plot holes, repetition, continuity, weak passages
 *   9. PUBLICATION_PREP   — metadata, descriptions, titles, promo materials
 *
 * The pipeline is CONTEXT-DRIVEN (the editorial intelligence layer,
 * EditorialMemory): every stage reads the relevant persisted context —
 * market findings, positioning, bibles, canon chapters, prior editorial
 * decisions — so stages can never produce conflicting outputs silently.
 *
 * Writer sovereignty (ADR-0001) is preserved end to end: LLM stage output
 * is PROPOSED until the writer approves the stage; gates are deterministic
 * local code (ADR-0004) and never call an LLM.
 */
enum class PipelineStage(val order: Int, val label: String, val description: String) {
    MARKET_RESEARCH(1, "Market & Audience Research",
        "Reader demographics, genre expectations, comparable titles, market gaps, commercial opportunities"),
    CONCEPT(2, "Concept Development",
        "Compelling premises, unique positioning, differentiated selling proposition, target reader personas"),
    STORY_ARCHITECTURE(3, "Story Architecture",
        "Outline, narrative structure, chapter plan, character arcs, thematic framework"),
    WORLDBUILDING(4, "Worldbuilding & Continuity",
        "Story/character/world bibles, timeline, relationship map, narrative rules"),
    MANUSCRIPT(5, "Manuscript Generation",
        "Chapter drafting through the existing L4 Scene Composer (writer-gated, PROPOSED → CONFIRMED)"),
    EDITORIAL(6, "Editorial Intelligence",
        "Developmental, structural, line, copyediting and proofreading passes with tracked revisions"),
    ENGAGEMENT(7, "Reader Engagement Optimization",
        "Opening-hook strength, pacing, tension, curiosity, chapter-ending effectiveness"),
    QUALITY_ASSURANCE(8, "Quality Assurance",
        "Plot holes, repetition, inconsistencies, weak passages, pacing problems, continuity errors"),
    PUBLICATION_PREP(9, "Publication Preparation",
        "Publication-ready manuscript, title/subtitle, description, metadata, promotional materials");

    companion object {
        fun inOrder(): List<PipelineStage> = entries.sortedBy { it.order }
    }
}

/** Lifecycle of one stage instance for one book. Mirrors the PROPOSED/CONFIRMED doctrine. */
enum class StageStatus { NOT_STARTED, RUNNING, AWAITING_APPROVAL, APPROVED, BLOCKED, FAILED }

/**
 * The per-stage state stored in the editorial memory (pipeline_state rows).
 * A stage is complete when [status] == APPROVED — the writer approved the
 * stage output AND its gate is green.
 */
data class StageState(
    val stage: PipelineStage,
    val status: StageStatus,
    /** Gate verdict snapshot when the stage output was last evaluated. */
    val gate: GateVerdict?,
    /** Epoch millis of the last transition. */
    val updatedAt: Long
) {
    fun canAdvanceTo(next: PipelineStage): Boolean =
        next.order == stage.order + 1 &&
            (status == StageStatus.APPROVED || status == StageStatus.NOT_STARTED && stage.order == 0)

    companion object {
        fun fresh(): List<StageState> = PipelineStage.inOrder().map {
            StageState(it, StageStatus.NOT_STARTED, null, 0L)
        }
    }
}

/**
 * A deterministic quality-gate verdict (ADR-0004: local code, never an LLM).
 * [hardFailures] block advancement; [warnings] surface to the writer but do
 * not block; [recommendations] are actionable improvement suggestions.
 */
data class GateVerdict(
    val stage: PipelineStage,
    val passed: Boolean,
    val hardFailures: List<String>,
    val warnings: List<String>,
    val recommendations: List<String>,
    val score: Double,
    val evaluatedAt: Long
) {
    companion object {
        fun green(stage: PipelineStage, score: Double, warnings: List<String> = emptyList()): GateVerdict =
            GateVerdict(stage, true, emptyList(), warnings, emptyList(), score, System.currentTimeMillis())

        fun blocked(stage: PipelineStage, hardFailures: List<String>, score: Double): GateVerdict =
            GateVerdict(stage, false, hardFailures, emptyList(), emptyList(), score, System.currentTimeMillis())
    }
}

/** A single finding produced by a deterministic analyzer (editorial/QA/engagement). */
data class Finding(
    val kind: String,
    val severity: Severity,
    val chapterNumber: Int?,
    val message: String,
    /** Actionable fix suggestion; empty when the finding is informational. */
    val recommendation: String
) {
    enum class Severity { INFO, WARNING, HARD }
}

/** Everything one pipeline stage needs: the persisted project context, assembled. */
data class StageContext(
    val book: Book,
    val canonChapters: List<Chapter>,
    val proposedChapters: List<Chapter>,
    val memory: EditorialMemorySnapshot
)

/** Read-only snapshot of the editorial intelligence layer handed to stages. */
data class EditorialMemorySnapshot(
    val marketFindings: String?,
    val positioning: String?,
    val readerPersonas: String?,
    val storyBible: String,
    val characterBible: String,
    val worldBible: String,
    val decisions: List<EditorialDecisionRecord>,
    val openIssues: List<EditorialIssueRecord>,
    /** ADR-0020 voice-evolution entries (JSON payload; VoiceEngine decodes). */
    val voiceEvolutions: String? = null
)

data class EditorialDecisionRecord(
    val id: Int,
    val stage: PipelineStage,
    val decision: String,
    val rationale: String,
    val createdAt: Long
)

data class EditorialIssueRecord(
    val id: Int,
    val kind: String,
    val severity: String,
    val description: String,
    val chapterNumber: Int?,
    val status: String,
    val createdAt: Long
)
