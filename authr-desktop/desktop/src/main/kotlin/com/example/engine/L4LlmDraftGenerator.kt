package com.example.engine

import android.content.Context
import com.example.data.api.LlmOrchestrator
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.ui.viewmodel.WriterStudioViewModel

/**
 * Production L4 draft generator — assembles its prompt from confirmed
 * material only (plan §4, "Level 4 in detail"):
 *
 *  - the chapter's beat sheet (summary card),
 *  - the active character sheets (book.characterBible),
 *  - matching lorebook keys (keyword injection),
 *  - the continuity summaries of all CONFIRMED chapters,
 *  - the selected persona card,
 *  - the format's word envelope (book.wordMin / book.wordMax).
 *
 * The produced text lands in the chapter workspace as PROPOSED via the
 * Writer Studio state machine; it becomes canon only on Confirm.
 */
class L4LlmDraftGenerator(private val context: Context) : WriterStudioViewModel.L4DraftGenerator {

    override suspend fun generate(
        book: Book,
        chapter: Chapter,
        canonContext: List<Chapter>,
        lorebook: List<Lorebook>,
        instruction: String
    ): String {
        val loreContext = if (lorebook.isEmpty()) "None" else lorebook.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val continuitySummaries = if (canonContext.isEmpty()) {
            "This is the first confirmed chapter."
        } else {
            canonContext.joinToString("\n") { "Chapter ${it.chapterNumber} (${it.title}): ${it.summary}" }
        }
        val wordEnvelope = "${book.wordMin}–${book.wordMax} words"

        // P2 prose-quality bracket (plan §5.1): the forbidden-pattern block
        // rides the prompt on EVERY provider route; the decode-time bias
        // payload applies only where the route's API supports logit_bias
        // (plan Table 7 — the linter, not the bias, is the compliance gate).
        val canonText = canonContext.joinToString("\n") { it.content }
        val provider = LlmOrchestrator.getProviderForAgent(context, LlmOrchestrator.Agent.SCENE)
        val biasPlan = TokenBiasPolicy.enforcementPlan(provider, canonText)
        if (biasPlan.logitBias.isNotEmpty()) {
            android.util.Log.i("L4LlmDraftGenerator",
                "Token-bias policy (${provider}): suppressing ${biasPlan.logitBias.size} offender tokens at decode time (-5).")
        }

        val prompt = """
            You are the Scene Composer (L4) writing chapter ${chapter.chapterNumber} of "${book.title}" (${book.format}, ${book.genre}).

            Chapter beat sheet:
            ${chapter.summary.ifBlank { "Write the chapter outlined below." }}

            Character sheets (active):
            ${book.characterBible.ifBlank { "None" }}

            World bible (rules):
            ${book.worldBible.ifBlank { "None" }}

            Story bible:
            ${book.storyBible.ifBlank { "None" }}

            Lorebook (continuity keys):
            $loreContext

            Continuity summaries of all confirmed chapters:
            $continuitySummaries

            Author persona: ${book.personaId}
            Narrative structure: ${book.narrativeStructure}
            ${if (instruction.isNotBlank()) "Writer instruction for this draft:\n$instruction" else ""}

            Instructions:
            Write the complete prose scene for chapter ${chapter.chapterNumber} ("${chapter.title}") in the
            target word envelope of $wordEnvelope. Show, don't tell; keep continuity with the
            confirmed chapters only.

            ${biasPlan.promptConstraints}

            Return ONLY the chapter content — no labels, no commentary.
        """.trimIndent()

        return LlmOrchestrator.callAgent(context, LlmOrchestrator.Agent.SCENE, prompt, logitBias = biasPlan.logitBias)
    }
}
