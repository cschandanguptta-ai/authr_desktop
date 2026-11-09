package com.example.engine

import com.example.data.model.Chapter

/**
 * The six-layer drafting engine — writer-sovereignty gates (v3.0, plan §4).
 *
 * The layer contract makes the pipeline auditable: every generated artifact
 * names its producing layer, its owning agent, and its confirmation state.
 * Rev 2's core decision (SCR-1) lives here as data: the Level 4 Scene
 * Auto-Drafter stays the productivity core, and its drafts land as PROPOSED,
 * becoming canon only when the writer confirms them.
 */
object DraftState {
    /** L4 output is a proposal; never canon until confirmed. */
    const val PROPOSED = "PROPOSED"
    /** The writer opened, edited or accepted the draft and tapped Confirm. */
    const val CONFIRMED = "CONFIRMED"
    /** Archived drafts are kept for diffing but never feed downstream layers. */
    const val ARCHIVED = "ARCHIVED"

    val ALL = setOf(PROPOSED, CONFIRMED, ARCHIVED)
}

/** The six Weaver layers (plan §4, Table 4). */
enum class EngineLayer(val layer: String, val owningAgent: String) {
    L1("L1", "World Builder, Character Psychologist"),
    L2("L2", "Plot Architect"),
    L3("L3", "Plot Architect"),
    L4("L4", "Scene Composer"),
    L5("L5", "Executive Orchestrator"),
    L6("L6", "Continuity Manager")
}

/**
 * WriterGate — the sovereignty contract enforced as code (plan §2, Principle 1).
 *
 * The P1 exit gate is expressed by [assertOnlyConfirmedTextFeeds]: no text
 * reaches any ledger, export, or downstream layer unless writerConfirmed is
 * true and draftStatus is CONFIRMED. Seeded-project assertion suites call
 * this against every pipeline hand-off.
 */
object WriterGate {

    fun isCanon(chapter: Chapter): Boolean =
        chapter.writerConfirmed && chapter.draftStatus == DraftState.CONFIRMED

    /** Chapters eligible for L5/L6 processing, ledgers, and exports. */
    fun canonChapters(chapters: List<Chapter>): List<Chapter> =
        chapters.filter { isCanon(it) }

    /** Chapters waiting in the Writer Studio review queue. */
    fun proposedChapters(chapters: List<Chapter>): List<Chapter> =
        chapters.filter { it.draftStatus == DraftState.PROPOSED && !it.writerConfirmed }

    /**
     * The sovereignty assertion: returns every violation found when feeding
     * [consumers] (ledger / export / downstream layers) with [chapters].
     * An empty list proves the gate held.
     */
    fun violations(chapters: List<Chapter>, consumers: List<String>): List<String> =
        chapters.filter { !isCanon(it) && consumers.isNotEmpty() }
            .map { "Unconfirmed chapter ${it.chapterNumber} reached consumer(s): ${consumers.joinToString()}" }
}
