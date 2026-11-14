package com.example.engine.pipeline

import android.content.Context
import android.util.Log
import com.example.data.api.LlmOrchestrator
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

import kotlinx.serialization.json.jsonObject

/**
 * The pipeline orchestrator (ADR-0018): runs the nine stages end to end
 * against the editorial memory, with deterministic gates between stages
 * and writer approval checkpoints (stage output is PROPOSED until the
 * writer approves — writer sovereignty, ADR-0001).
 *
 * Stage execution contract:
 *  1. Assemble StageContext from the persisted memory (never from a
 *     previous stage's in-flight output) — stages cannot conflict.
 *  2. Run the stage LLM call (or local analyzer where the stage is local).
 *  3. Store the result PROPOSED in the memory store.
 *  4. Evaluate the deterministic gate. A red gate sets BLOCKED and returns
 *     the verdict — weak content is never auto-advanced (requirement §5).
 *  5. The writer approves in the UI; only then does the stage complete
 *     and the next stage unlock.
 */
class PipelineOrchestrator(
    private val context: Context,
    private val repository: AppRepository,
    private val memory: EditorialMemoryStore
) {

    private val json = Json { ignoreUnknownKeys = true }

    data class StageRunResult(
        val stage: PipelineStage,
        val ok: Boolean,
        val verdict: GateVerdict?,
        /** Raw parsed LLM output for the UI to render as a PROPOSAL. */
        val output: String,
        val error: String? = null
    )

    // ------------------------------------------------------------------
    // Context assembly

    suspend fun assembleContext(bookId: Int): StageContext? = withContext(Dispatchers.IO) {
        val book = repository.getBookById(bookId) ?: return@withContext null
        val chapters = repository.getChaptersForBookSync(bookId)
        val canon = chapters.filter { it.writerConfirmed && it.draftStatus == "CONFIRMED" }
        val proposed = chapters.filter { !it.writerConfirmed || it.draftStatus == "PROPOSED" }
        StageContext(book, canon, proposed, memory.snapshotFor(bookId, book.storyBible, book.characterBible, book.worldBible))
    }

    // ------------------------------------------------------------------
    // Stage runners (LLM stages return PROPOSALS; local stages compute)

    suspend fun runMarketResearch(bookId: Int, extraDirection: String = ""): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.MARKET_RESEARCH, "Book not found.")
        return llmStage(
            PipelineStage.MARKET_RESEARCH,
            LlmOrchestrator.Agent.WORLD,
            StagePrompts.marketResearch(ctx, extraDirection)
        ) { raw ->
            val obj = parseJson(raw) ?: return@llmStage raw
            val findings = EditorialMemoryStore.MarketFindings(
                genreExpectations = obj.strings("genreExpectations"),
                readerDemographics = obj.string("readerDemographics") ?: "",
                comparableTitles = obj.strings("comparableTitles"),
                marketGaps = obj.strings("marketGaps"),
                commercialOpportunities = obj.strings("commercialOpportunities"),
                summary = obj.string("summary") ?: "",
                generatedAt = System.currentTimeMillis()
            )
            memory.writeMarketFindings(bookId, findings)
            memory.appendDecision(bookId, PipelineStage.MARKET_RESEARCH,
                "Market analysis adopted (PROPOSED)", findings.summary.take(200))
            findings
        }.let { result ->
            // Gate evaluates what was actually stored.
            val verdict = StageGates.marketResearch(memory.marketFindings(bookId))
            memory.recordGate(bookId, PipelineStage.MARKET_RESEARCH, verdict)
            memory.setStageStatus(bookId, PipelineStage.MARKET_RESEARCH,
                if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
            result.copy(verdict = verdict)
        }
    }

    suspend fun runConcept(bookId: Int, chosenIndex: Int = 0): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.CONCEPT, "Book not found.")
        return llmStage(
            PipelineStage.CONCEPT,
            LlmOrchestrator.Agent.EXECUTIVE,
            StagePrompts.concept(ctx)
        ) { raw ->
            val obj = parseJson(raw)
            val concepts = obj?.get("concepts")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() } ?: emptyList()
            val rec = obj?.string("recommendation") ?: ""
            // The chosen concept (writer picks the index; default first + LLM recommendation noted).
            val chosen = concepts.getOrNull(chosenIndex) ?: concepts.firstOrNull()
            if (chosen != null) {
                val positioning = EditorialMemoryStore.Positioning(
                    premise = chosen.string("premise") ?: "",
                    differentiator = chosen.string("differentiator") ?: "",
                    sellingProposition = chosen.string("sellingProposition") ?: "",
                    targetReader = chosen.string("targetReader") ?: "",
                    promiseToReader = chosen.string("promiseToReader") ?: "",
                    avoidList = chosen.strings("avoidList"),
                    summary = (chosen.string("summary") ?: "") + if (rec.isNotBlank()) "\nAnalyst recommendation: $rec" else "",
                    generatedAt = System.currentTimeMillis()
                )
                memory.writePositioning(bookId, positioning)
                memory.appendDecision(bookId, PipelineStage.CONCEPT,
                    "Concept candidate ${chosenIndex + 1} selected (PROPOSED)", positioning.premise.take(200))
                positioning
            }
            raw
        }.let { result ->
            val verdict = StageGates.concept(memory.positioning(bookId))
            memory.recordGate(bookId, PipelineStage.CONCEPT, verdict)
            memory.setStageStatus(bookId, PipelineStage.CONCEPT,
                if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
            result.copy(verdict = verdict)
        }
    }

    suspend fun runStoryArchitecture(bookId: Int, chapterCount: Int): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.STORY_ARCHITECTURE, "Book not found.")
        return llmStage(
            PipelineStage.STORY_ARCHITECTURE,
            LlmOrchestrator.Agent.PLOT,
            StagePrompts.storyArchitecture(ctx, chapterCount)
        ) { raw ->
            val obj = parseJson(raw)
            val chapters = obj?.get("chapters")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() } ?: emptyList()
            val outline = buildString {
                appendLine("LOGLINE: ${obj?.string("logline") ?: ""}")
                appendLine("THEME: ${obj?.string("thematicFramework") ?: ""}")
                appendLine()
                appendLine("ACTS:")
                obj?.get("acts")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }?.forEach {
                    appendLine("- ${it.string("name")} (${it.string("chapters") ?: ""}): ${it.string("purpose") ?: ""} → turn: ${it.string("turn") ?: ""}")
                }
                appendLine()
                appendLine("CHAPTER PLAN:")
                chapters.forEach {
                    appendLine("Ch. ${it.int("number")}: ${it.string("title") ?: ""} — ${it.string("summary") ?: ""} [${it.string("function") ?: ""}] ending: ${it.string("cliffhanger") ?: ""}")
                }
                appendLine()
                appendLine("CHARACTER ARCS:")
                obj?.get("characterArcs")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }?.forEach {
                    appendLine("- ${it.string("character") ?: ""}: wants ${it.string("want") ?: ""}, needs ${it.string("need") ?: ""}; ${it.string("startState") ?: ""} → ${it.string("endState") ?: ""}")
                }
                appendLine()
                appendLine("CONTINUITY SEEDS:")
                obj?.strings("continuitySeeds")?.forEach { appendLine("- $it") }
            }
            repository.updateBook(ctx.book.copy(outline = outline))
            memory.appendDecision(bookId, PipelineStage.STORY_ARCHITECTURE,
                "Story architecture generated (PROPOSED)", "Logline: ${obj?.string("logline") ?: ""}".take(200))
            raw
        }.let { result ->
            val book = repository.getBookById(bookId) ?: ctx.book
            val verdict = StageGates.storyArchitecture(book, repository.getChaptersForBookSync(bookId))
            memory.recordGate(bookId, PipelineStage.STORY_ARCHITECTURE, verdict)
            memory.setStageStatus(bookId, PipelineStage.STORY_ARCHITECTURE,
                if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
            result.copy(verdict = verdict)
        }
    }

    suspend fun runWorldbuilding(bookId: Int): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.WORLDBUILDING, "Book not found.")
        return llmStage(
            PipelineStage.WORLDBUILDING,
            LlmOrchestrator.Agent.WORLD,
            StagePrompts.worldbuilding(ctx)
        ) { raw ->
            val obj = parseJson(raw)
            val storyBible = obj?.string("storyBible") ?: ""
            val characterBible = obj?.string("characterBible") ?: ""
            val worldBible = obj?.string("worldBible") ?: ""
            val rules = obj?.strings("narrativeRules") ?: emptyList()
            val relationships = obj?.strings("relationshipMap") ?: emptyList()
            val timeline = obj?.strings("timeline") ?: emptyList()
            repository.updateBook(ctx.book.copy(
                storyBible = storyBible,
                characterBible = characterBible + if (relationships.isNotEmpty()) "\n\nRELATIONSHIPS:\n" + relationships.joinToString("\n") else "",
                worldBible = worldBible + if (timeline.isNotEmpty()) "\n\nTIMELINE:\n" + timeline.joinToString("\n") else ""
                    + if (rules.isNotEmpty()) "\n\nNARRATIVE RULES (never violate):\n" + rules.joinToString("\n") else ""
            ))
            memory.appendDecision(bookId, PipelineStage.WORLDBUILDING,
                "Bibles generated (PROPOSED)", "Rules: ${rules.joinToString("; ").take(200)}")
            raw
        }.let { result ->
            val book = repository.getBookById(bookId) ?: ctx.book
            val verdict = StageGates.worldbuilding(book)
            memory.recordGate(bookId, PipelineStage.WORLDBUILDING, verdict)
            memory.setStageStatus(bookId, PipelineStage.WORLDBUILDING,
                if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
            result.copy(verdict = verdict)
        }
    }

    /** Stage 5 is the EXISTING writer-gated flow — the gate verifies canon exists. */
    suspend fun evaluateManuscriptStage(bookId: Int): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.MANUSCRIPT, "Book not found.")
        val verdict = StageGates.manuscript(ctx.canonChapters, ctx.book)
        memory.recordGate(bookId, PipelineStage.MANUSCRIPT, verdict)
        memory.setStageStatus(bookId, PipelineStage.MANUSCRIPT,
            when {
                !verdict.passed -> StageStatus.BLOCKED
                else -> StageStatus.AWAITING_APPROVAL
            })
        memory.appendVersion(bookId, EditorialMemoryStore.VersionEntry(
            label = "canon-snapshot-${ctx.canonChapters.size}ch",
            scope = "manuscript", stage = PipelineStage.MANUSCRIPT.name,
            chapterNumbers = ctx.canonChapters.map { it.chapterNumber },
            wordCount = ctx.canonChapters.sumOf { StageGates.wordCount(it.content) },
            note = "Auto-snapshot at manuscript-stage evaluation",
            createdAt = System.currentTimeMillis()
        ))
        return StageRunResult(PipelineStage.MANUSCRIPT, verdict.passed, verdict, "${ctx.canonChapters.size} canon chapters")
    }

    suspend fun runEditorialPass(bookId: Int, pass: StagePrompts.EditorialPass, chapterNumber: Int? = null): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.EDITORIAL, "Book not found.")
        val chapter = chapterNumber?.let { n -> ctx.canonChapters.firstOrNull { it.chapterNumber == n } }
        val raw = try {
            LlmOrchestrator.callAgent(context, LlmOrchestrator.Agent.EXECUTIVE, StagePrompts.editorial(ctx, pass, chapter))
        } catch (e: Exception) {
            return failed(PipelineStage.EDITORIAL, e.message ?: "LLM call failed")
        }
        val obj = parseJson(raw)
        val majors = obj?.get("findings")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }
            ?.count { it.string("severity") == "MAJOR" } ?: 0
        val passes = 1 + memory.decisions(bookId).count { it.stage == PipelineStage.EDITORIAL.name && it.decision.startsWith(pass.name) }
        // Persist findings as open issues; MAJOR findings gate the stage.
        obj?.get("findings")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }?.forEach { f ->
            memory.upsertIssue(bookId, EditorialMemoryStore.IssueEntry(
                kind = "EDITORIAL_${pass.name}",
                severity = if (f.string("severity") == "MAJOR") "HARD" else "WARNING",
                description = "[${f.string("location") ?: "?"}] ${f.string("issue") ?: ""}",
                chapterNumber = f.int("chapter") ?: chapterNumber,
                status = "OPEN",
                createdAt = System.currentTimeMillis()
            ))
        }
        memory.appendDecision(bookId, PipelineStage.EDITORIAL,
            "${pass.name} pass recorded (${majors} MAJOR)", (obj?.string("assessment") ?: "").take(200))
        val openMajors = memory.issues(bookId).count { it.status == "OPEN" && it.severity == "HARD" }
        val passesDone = memory.decisions(bookId).count { it.stage == PipelineStage.EDITORIAL.name && it.decision.contains("pass recorded") }
        val verdict = StageGates.editorial(openMajors, passesDone)
        memory.recordGate(bookId, PipelineStage.EDITORIAL, verdict)
        memory.setStageStatus(bookId, PipelineStage.EDITORIAL,
            if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
        return StageRunResult(PipelineStage.EDITORIAL, verdict.passed, verdict, raw)
    }

    /** Stage 7 is LOCAL (deterministic) — no LLM in the measurement. */
    suspend fun runEngagement(bookId: Int): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.ENGAGEMENT, "Book not found.")
        val report = RetentionAnalyzer.analyze(ctx.canonChapters)
        memory.writeEngagement(bookId, EditorialMemoryStore.EngagementSnapshot(
            chapters = report.perChapter.map {
                EditorialMemoryStore.EngagementSnapshot.ChapterEngagement(
                    chapterNumber = it.chapterNumber, hookScore = it.hookScore, pacingScore = it.pacingScore,
                    tensionScore = it.tensionScore, curiosityScore = it.curiosityScore,
                    endingScore = it.endingScore, retentionIndex = it.retentionIndex, notes = it.notes
                )
            },
            overallIndex = report.overallIndex,
            computedAt = System.currentTimeMillis()
        ))
        val verdict = StageGates.engagement(report)
        memory.recordGate(bookId, PipelineStage.ENGAGEMENT, verdict)
        memory.setStageStatus(bookId, PipelineStage.ENGAGEMENT,
            if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
        val summary = "Retention index ${"%.1f".format(report.overallIndex)} (${RetentionAnalyzer.RetentionReport.band(report.overallIndex)}).\n" +
            report.recommendations.joinToString("\n")
        return StageRunResult(PipelineStage.ENGAGEMENT, verdict.passed, verdict, summary)
    }

    /** Stage 8 runs the LOCAL QA suite (trusted) — the LLM audit is a separate writer-visible aid. */
    suspend fun runQualityAssurance(bookId: Int, llmAudit: Boolean = false): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.QUALITY_ASSURANCE, "Book not found.")
        val guns = repository.getGunsForBookSync(bookId)
        val qa = ManuscriptQaSuite.run(ctx.canonChapters, guns)
        qa.findings.forEach { f ->
            memory.upsertIssue(bookId, EditorialMemoryStore.IssueEntry(
                kind = f.kind, severity = f.severity.name, description = f.message,
                chapterNumber = f.chapterNumber, status = if (f.severity == Finding.Severity.HARD) "OPEN" else "NOTED",
                createdAt = System.currentTimeMillis()
            ))
        }
        val verdict = StageGates.qualityAssurance(qa)
        memory.recordGate(bookId, PipelineStage.QUALITY_ASSURANCE, verdict)
        memory.setStageStatus(bookId, PipelineStage.QUALITY_ASSURANCE,
            if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
        return StageRunResult(PipelineStage.QUALITY_ASSURANCE, verdict.passed, verdict,
            qa.summary + "\n" + qa.findings.joinToString("\n") { "[${it.severity}] ${it.message} → ${it.recommendation}" })
    }

    suspend fun runPublicationPrep(bookId: Int): StageRunResult {
        val ctx = assembleContext(bookId) ?: return failed(PipelineStage.PUBLICATION_PREP, "Book not found.")
        return llmStage(
            PipelineStage.PUBLICATION_PREP,
            LlmOrchestrator.Agent.EXECUTIVE,
            StagePrompts.publicationPrep(ctx)
        ) { raw ->
            val obj = parseJson(raw)
            val titles = obj?.get("titles")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }?.size ?: 0
            val blurbWords = StageGates.wordCount(obj?.get("descriptions")?.jsonObjectOrNull()?.string("retailBlurb") ?: "")
            val keywords = obj?.get("metadata")?.jsonObjectOrNull()?.strings("sevenKeywords")?.size ?: 0
            val categories = obj?.get("metadata")?.jsonObjectOrNull()?.strings("threeCategories")?.size ?: 0
            // Store the package for the publishing workbench to consume.
            if (raw.isNotBlank()) {
                memory.writeSlot(bookId, "publication_package", raw)
                memory.appendDecision(bookId, PipelineStage.PUBLICATION_PREP,
                    "Publication package generated (PROPOSED)", "$titles titles, $keywords keywords, $categories categories")
            }
            raw
        }.let { result ->
            val pkg = parseJson(memory.readSlotRaw(bookId, "publication_package") ?: "")
            val titles = pkg?.get("titles")?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }?.size ?: 0
            val blurbWords = StageGates.wordCount(pkg?.get("descriptions")?.jsonObjectOrNull()?.string("retailBlurb") ?: "")
            val keywords = pkg?.get("metadata")?.jsonObjectOrNull()?.strings("sevenKeywords")?.size ?: 0
            val categories = pkg?.get("metadata")?.jsonObjectOrNull()?.strings("threeCategories")?.size ?: 0
            val verdict = StageGates.publicationPrep(titles, blurbWords, keywords, categories)
            memory.recordGate(bookId, PipelineStage.PUBLICATION_PREP, verdict)
            memory.setStageStatus(bookId, PipelineStage.PUBLICATION_PREP,
                if (verdict.passed) StageStatus.AWAITING_APPROVAL else StageStatus.BLOCKED)
            result.copy(verdict = verdict)
        }
    }

    // ------------------------------------------------------------------
    // Writer approval (the human checkpoint — requirement §5)

    suspend fun approveStage(bookId: Int, stage: PipelineStage, note: String = ""): Boolean {
        val states = memory.fullStageStates(bookId)
        val s = states[stage] ?: return false
        if (!s.gatePassed) {
            Log.w("PipelineOrchestrator", "approveStage($bookId, $stage) refused: gate is red.")
            return false
        }
        memory.setStageStatus(bookId, stage, StageStatus.APPROVED)
        memory.appendDecision(bookId, stage, "Writer approved stage", note.ifBlank { "Approved after gate green." })
        return true
    }

    /** Version comparison + rollback surface: latest N versions for a book. */
    suspend fun versions(bookId: Int): List<EditorialMemoryStore.VersionEntry> = memory.versions(bookId)

    /** Advance contract: the next stage is unlocked only when the current is APPROVED + gate green. */
    suspend fun nextUnlockedStage(bookId: Int): PipelineStage? {
        val states = memory.fullStageStates(bookId)
        for (stage in PipelineStage.inOrder()) {
            val s = states[stage] ?: continue
            if (s.status != StageStatus.APPROVED.name || !s.gatePassed) return stage
        }
        return null // all complete
    }

    // ------------------------------------------------------------------

    private suspend fun llmStage(
        stage: PipelineStage,
        agent: LlmOrchestrator.Agent,
        prompt: String,
        store: suspend (String) -> Any
    ): StageRunResult {
        return try {
            val raw = LlmOrchestrator.callAgent(context, agent, prompt)
            store(raw)
            StageRunResult(stage, true, null, raw)
        } catch (e: Exception) {
            Log.w("PipelineOrchestrator", "Stage $stage failed: ${e.message}")
            StageRunResult(stage, false, null, "", e.message ?: "LLM call failed")
        }
    }

    private fun failed(stage: PipelineStage, error: String): StageRunResult =
        StageRunResult(stage, false, null, "", error)

    // ------------------------------------------------------------------
    // JSON helpers (defensive; fail-soft to null so the UI can show raw text)

    private fun parseJson(raw: String): JsonObject? = try {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        Json.parseToJsonElement(trimmed).jsonObject
    } catch (e: Exception) { null }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
}
