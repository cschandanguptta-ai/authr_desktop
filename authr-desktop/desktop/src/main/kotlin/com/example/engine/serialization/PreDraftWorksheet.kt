package com.example.engine.serialization

import com.example.data.model.Chapter
import org.json.JSONObject

/**
 * The chapter drafting worksheet — Blueprint 2.0 Part 8 (v3.0, plan §6.8,
 * Table 16).
 *
 * Blueprint 2.0 requires a completed worksheet before drafting every
 * chapter. Implemented as a pre-draft card in the Interactive Wizard and as
 * an auto-filled, writer-editable template in the Autonomous Studio run: the
 * L4 drafter consumes the worksheet as prompt context, so the chapter is
 * drafted toward an explicit purpose instead of a word count.
 *
 * Sections A-E feed: the L4 prompt; beats 2-3 coverage; the attachment
 * panel; the question ledger; scorecard criteria 1-7; the ending advisor.
 * The section-D payoff link is mandatory (Table 16, reader-experience row).
 */
object PreDraftWorksheet {

    data class Worksheet(
        /** A. Narrative objective — main purpose, what the protagonist wants, what prevents it. */
        val objective: String = "",
        /** B. Character development — who develops, what emotional conflict, relationship changes. */
        val characterDevelopment: String = "",
        /** C. Plot development — new information revealed, complication introduced, what changes. */
        val plotDevelopment: String = "",
        /** D. Reader experience — why care, expected question, satisfying payoff, why continue. */
        val readerExperience: String = "",
        /** E. Chapter ending — consistent, compelling, anticipation without artificial interruption. */
        val chapterEnding: String = ""
    ) {
        /** Section D's payoff link is mandatory before drafting (Table 16). */
        val complete: Boolean get() =
            objective.isNotBlank() && characterDevelopment.isNotBlank() &&
                plotDevelopment.isNotBlank() && readerExperience.isNotBlank() &&
                chapterEnding.isNotBlank()

        fun missingSections(): List<String> = buildList {
            if (objective.isBlank()) add("A. Narrative objective")
            if (characterDevelopment.isBlank()) add("B. Character development")
            if (plotDevelopment.isBlank()) add("C. Plot development")
            if (readerExperience.isBlank()) add("D. Reader experience (the payoff link is mandatory)")
            if (chapterEnding.isBlank()) add("E. Chapter ending")
        }

        fun toJson(): String = JSONObject().apply {
            put("A_objective", objective)
            put("B_character", characterDevelopment)
            put("C_plot", plotDevelopment)
            put("D_reader", readerExperience)
            put("E_ending", chapterEnding)
        }.toString()

        /** Prompt context block for the L4 drafter (§6.8: drafted toward an explicit purpose). */
        fun promptBlock(): String = buildString {
            appendLine("=== PRE-DRAFT WORKSHEET (Blueprint 2.0, Part 8) ===")
            appendLine("A. Narrative objective: $objective")
            appendLine("B. Character development: $characterDevelopment")
            appendLine("C. Plot development: $plotDevelopment")
            appendLine("D. Reader experience: $readerExperience")
            appendLine("E. Chapter ending: $chapterEnding")
        }

        companion object {
            fun fromJson(json: String?): Worksheet {
                if (json.isNullOrBlank()) return Worksheet()
                return runCatching {
                    val o = JSONObject(json)
                    Worksheet(
                        objective = o.optString("A_objective", ""),
                        characterDevelopment = o.optString("B_character", ""),
                        plotDevelopment = o.optString("C_plot", ""),
                        readerExperience = o.optString("D_reader", ""),
                        chapterEnding = o.optString("E_ending", "")
                    )
                }.getOrDefault(Worksheet())
            }
        }
    }

    /** Loads the chapter's stored worksheet JSON (§6.12 field). */
    fun of(chapter: Chapter): Worksheet = Worksheet.fromJson(chapter.worksheet)

    /**
     * Auto-fill for the Autonomous Studio run (Table 12, worksheet row):
     * prefills from the arc plan's conflict and the question ledger's open
     * questions; the writer edits before the run. Nothing here is final —
     * every section is a proposal until the writer confirms the card.
     */
    fun autoFill(
        arcConflict: String?,
        openQuestions: List<String>,
        previousEnding: String?
    ): Worksheet = Worksheet(
        objective = if (arcConflict.isNullOrBlank()) "" else "Advance the chapter toward resolving: $arcConflict",
        characterDevelopment = "",
        plotDevelopment = if (openQuestions.isEmpty()) "" else "Move toward answering: " + openQuestions.take(2).joinToString("; "),
        readerExperience = "",
        chapterEnding = previousEnding?.let { "Avoid repeating the $it ending; arise naturally from this chapter's events." } ?: ""
    )

    /**
     * The §6.8 validation: section A feeds beats 2-3 coverage, D's payoff
     * link is mandatory, E feeds the ending advisor. Returns the missing
     * sections; drafting may proceed on a partial card (advisory) but the
     * missing sections surface on the card and in the run report.
     */
    fun validate(ws: Worksheet): List<String> = ws.missingSections()
}
