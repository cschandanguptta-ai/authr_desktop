package com.example.engine

import android.content.Context
import com.example.data.api.LlmOrchestrator
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.engine.context.ContextAssembler
import com.example.engine.context.TokenSpendLedger
import com.example.ui.viewmodel.WriterStudioViewModel

/**
 * Production L4 draft generator — assembles its prompt from confirmed
 * material only (plan §4, "Level 4 in detail"):
 *
 *  - the chapter's beat sheet (summary card),
 *  - the active character sheets (book.characterBible),
 *  - matching lorebook keys (keyword injection),
 *  - the continuity summaries of all CONFIRMED chapters,
 *  - the selected persona card, resolved through the persona registry
 *    (built-ins + container plugin personas; the 2026-11-12 audit fix made
 *    this real — the generator used to send only the raw persona id string,
 *    which the model could not interpret),
 *  - the format's word envelope (book.wordMin / book.wordMax).
 *
 * v3.0 S2 (ADR-0021): the flat "all confirmed chapters concatenated"
 * context is replaced by the nine-layer ContextAssembler with stage
 * budgets and freshness-ranked manuscript summaries (N-1 full, N-2
 * medium, N-3..5 brief, N-6+ oneliner). CONTEXT_MODE keeps the legacy
 * flat path selectable — the QC pass-rate comparison (S2 acceptance)
 * measures both, and writers on local models may prefer the smaller
 * predictable flat prompt.
 *
 * The produced text lands in the chapter workspace as PROPOSED via the
 * Writer Studio state machine; it becomes canon only on Confirm.
 */
class L4LlmDraftGenerator(
    private val context: Context,
    /** ADR-0021: budgeted nine-layer context (null = legacy flat mode). */
    private val assembler: ContextAssembler? = null,
    /** ADR-0021: token spend ledger (null = generation untracked). */
    private val spendLedger: TokenSpendLedger? = null,
    /** Context window of the configured provider route. */
    private val modelSpec: ContextAssembler.ModelSpec? = null,
    /** ADR-0022: hybrid RAG retrieval for layer 7 (null = lorebook surface). */
    private val ragEngine: com.example.engine.rag.RagEngine? = null
) : WriterStudioViewModel.L4DraftGenerator {

    override suspend fun generate(
        book: Book,
        chapter: Chapter,
        canonContext: List<Chapter>,
        lorebook: List<Lorebook>,
        instruction: String
    ): String {
        val loreContext = if (lorebook.isEmpty()) "None" else lorebook.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val wordEnvelope = "${book.wordMin}–${book.wordMax} words"

        // 2026-11-12 audit (#6): the persona card rides the prompt in full —
        // the name-only `Author persona: ${book.personaId}` line sent an
        // opaque id ("nightking") the model could not interpret.
        val persona = com.example.utils.PersonaResolver.resolve(context, book.personaId)
        val personaBlock = PersonaRegistry.cardBody(persona)

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

        // --------------------------------------------------------------
        // ADR-0021: budgeted nine-layer context when an assembler is
        // wired; legacy flat canon otherwise (QC before/after baseline).
        val contextBlock: String = if (assembler != null && modelSpec != null) {
            val assembled = assembler.assemble(
                book = book,
                confirmed = canonContext.filter { it.chapterNumber != chapter.chapterNumber },
                targetChapter = chapter,
                stage = ContextAssembler.StageKey.L4_SCENE_COMPOSER,
                model = modelSpec,
                personaCard = personaBlock,
                lorebook = lorebook,
                ragEngine = ragEngine
            )
            android.util.Log.i("L4LlmDraftGenerator",
                "ContextAssembler(L4): ${assembled.tokenCount}/${modelSpec.available} tokens " +
                    "(${"%.0f".format(assembled.budgetUtilization * 100)}% utilization)" +
                    if (assembled.downgrades.isNotEmpty()) " downgrades: ${assembled.downgrades}" else "")
            assembled.promptPrefix
        } else {
            val continuitySummaries = if (canonContext.isEmpty()) {
                "This is the first confirmed chapter."
            } else {
                canonContext.joinToString("\n") { "Chapter ${it.chapterNumber} (${it.title}): ${it.summary}" }
            }
            buildString {
                appendLine("Character sheets (active):")
                appendLine(book.characterBible.ifBlank { "None" })
                appendLine()
                appendLine("World bible (rules):")
                appendLine(book.worldBible.ifBlank { "None" })
                appendLine()
                appendLine("Story bible:")
                appendLine(book.storyBible.ifBlank { "None" })
                appendLine()
                appendLine("Lorebook (continuity keys):")
                appendLine(loreContext)
                appendLine()
                appendLine("Continuity summaries of all confirmed chapters:")
                appendLine(continuitySummaries)
            }.trim()
        }

        val prompt = """
            You are the Scene Composer (L4) writing chapter ${chapter.chapterNumber} of "${book.title}" (${book.format}, ${book.genre}).

            $contextBlock

            Chapter beat sheet:
            ${chapter.summary.ifBlank { "Write the chapter outlined below." }}

            Author persona — ${persona.name} (${persona.mode}):
            $personaBlock
            Narrative structure: ${book.narrativeStructure}
            ${if (instruction.isNotBlank()) "Writer instruction for this draft:\n$instruction" else ""}

            Instructions:
            Write the complete prose scene for chapter ${chapter.chapterNumber} ("${chapter.title}") in the
            target word envelope of $wordEnvelope. Show, don't tell; keep continuity with the
            confirmed chapters only.

            ${biasPlan.promptConstraints}

            Return ONLY the chapter content — no labels, no commentary.
        """.trimIndent()

        val result = LlmOrchestrator.callAgent(context, LlmOrchestrator.Agent.SCENE, prompt, logitBias = biasPlan.logitBias)

        // ADR-0021 spend ledger: record the invocation (prompt tokens from
        // the assembled/flat context, completion from the returned draft).
        spendLedger?.let { ledger ->
            val promptTokens = (contextBlock + prompt).split(Regex("\\s+")).count { it.isNotBlank() } * 4 / 3
            val completionTokens = result.split(Regex("\\s+")).count { it.isNotBlank() } * 4 / 3
            ledger.logSpend(
                TokenSpendLedger.SpendEntry(
                    bookId = book.id,
                    stage = "L4_SCENE_COMPOSER",
                    model = provider,
                    provider = provider,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    estimatedCostUsd = 0.0 // computed by the ledger
                )
            )
        }

        return result
    }
}
