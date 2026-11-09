package com.example.engine

/**
 * The manuscript AST — the analyzed representation of confirmed text (v3.0,
 * plan §4.1, Table 5).
 *
 * The Live AST Annotator runs entirely locally and deterministically; it
 * annotates confirmed text only, so the ledger and statistics always reflect
 * canon rather than proposals. Every node carries the information a writer
 * needs to act: kind, span, severity, message, and the writer action the
 * margin rail offers.
 */
enum class AnnotationKind {
    /** Coverage of each confirmed beat in the chapter (Table 10, seven-beat model). */
    BEAT,
    /** Every linter match with class, severity, and suggested rewrite. */
    CLICHE_HIT,
    /** Introduced, escalated, and resolved promises tracked by the ledger. */
    CHEKHOV_GUN,
    /** Lorebook keys matched in text; missing definitions proposed inline. */
    LORE_REFERENCE,
    /** Closing passage classified against the six Blueprint 2.0 ending types + QUIET. */
    ENDING_SIGNAL,
    /** Contradictions against the timeline and bibles raised by L6. */
    CONTINUITY_FLAG
}

enum class Severity { INFO, WARN, ERROR }

/** The writer action the margin rail offers for a node (Table 5, third column). */
enum class WriterAction {
    OPEN_BEAT_CARD,
    ACCEPT_SUGGESTION,
    EDIT_MANUALLY,
    WHITELIST_PHRASE,
    MARK_FALSE_POSITIVE,
    ADJUST_SEVERITY,
    SCHEDULE_ESCALATION,
    ACCEPT_LORE_ENTRY,
    DISCARD,
    CONFIRM_DETECTED_TYPE,
    PICK_ANOTHER_TYPE,
    RESOLVE_IN_TEXT,
    DISMISS_WITH_NOTE
}

/**
 * One AST node. [start]/[end] are character offsets into the annotated text;
 * [excerpt] is the matched span (or the closing passage for ENDING_SIGNAL).
 * [payload] carries action data, e.g. {"beat": "Turning point",
 * "clicheClass": "Dialogue-tag crutches", "loreKey": "Ashfall Accord"}.
 */
data class AstAnnotation(
    val kind: AnnotationKind,
    val start: Int,
    val end: Int,
    val excerpt: String,
    val message: String,
    val severity: Severity,
    val suggestedAction: WriterAction,
    val payload: Map<String, String> = emptyMap()
)

/**
 * The annotated representation of one chapter's confirmed text.
 *
 * [textHash] pins the AST to the frozen text: if the chapter is edited, the
 * hash changes and the rail re-annotates. [annotated] is false for PROPOSED
 * or ARCHIVED drafts — the annotator refuses non-canon text (plan §4.1:
 * "the ledger and statistics always reflect canon rather than proposals").
 */
data class ManuscriptAst(
    val chapterId: Int,
    val chapterNumber: Int,
    val textHash: String,
    val annotations: List<AstAnnotation>,
    val annotatedAt: Long = System.currentTimeMillis()
) {
    val annotated: Boolean get() = annotations.isNotEmpty() || textHash.isNotEmpty()
    fun ofKind(kind: AnnotationKind): List<AstAnnotation> = annotations.filter { it.kind == kind }
}

/** The seven chapter beats of Blueprint 2.0 (plan §6.3, Table 10). */
enum class ChapterBeat(val index: Int, val function: String) {
    OPENING_CONTINUITY(1, "Opening continuity"),
    IMMEDIATE_OBJECTIVE(2, "Immediate objective"),
    RESISTANCE(3, "Resistance"),
    ESCALATION(4, "Escalation"),
    TURNING_POINT(5, "Turning point"),
    CHAPTER_PAYOFF(6, "Chapter payoff"),
    FORWARD_PULL(7, "Forward pull");

    companion object {
        fun byName(name: String): ChapterBeat? = entries.firstOrNull { it.function.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
    }
}

/** The six Blueprint 2.0 ending types plus the explicit QUIET ending (plan §6.4). */
object EndingTypes {
    const val DANGER = "DANGER"
    const val EMOTIONAL_REVELATION = "EMOTIONAL_REVELATION"
    const val MYSTERY_DISCOVERY = "MYSTERY_DISCOVERY"
    const val IRREVERSIBLE_CHOICE = "IRREVERSIBLE_CHOICE"
    const val REVERSAL = "REVERSAL"
    const val ANTICIPATION = "ANTICIPATION"
    const val QUIET = "QUIET"

    val ALL = listOf(DANGER, EMOTIONAL_REVELATION, MYSTERY_DISCOVERY, IRREVERSIBLE_CHOICE, REVERSAL, ANTICIPATION, QUIET)
}
