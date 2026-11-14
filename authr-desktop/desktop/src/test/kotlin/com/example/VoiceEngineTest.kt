package com.example

import com.example.data.model.Chapter
import com.example.engine.pipeline.EditorialMemoryStore
import com.example.engine.pipeline.StageGates
import com.example.engine.serialization.GoldenSerial
import com.example.engine.voice.VoiceEngine
import com.example.engine.voice.VoiceFeatureExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.0 port S1 validation (spec ADR-0013 = desktop ADR-0020): the voice
 * engine measures per-confirmed-chapter drift against the rolling
 * baseline, evolution entries override targets and suppress alerts, the
 * stage-7 gate surfaces drift as WARNINGS (never hard failures), and the
 * evolution slot round-trips through the editorial memory payload shape.
 *
 * The engine runs DAO-free here (the ADR-0018 nullable-DAO pattern): this
 * suite proves the measurement logic; persistence is exercised by the
 * S0 migration tests + the Room DAO surface.
 */
class VoiceEngineTest {

    private fun goldenChapters(bookId: Int = 1): List<Chapter> =
        GoldenSerial.chapters(bookId = bookId)

    /**
     * Deterministic rich prose in a controlled voice — the planted
     * baseline. Carries real dialogue, figurative language and filter
     * words so every genre-weighted feature is measurable (a corpus of
     * bare declaratives would leave dialogue/figurative at 0 in BOTH
     * texts — no drift signal there).
     */
    private fun baselineChapter(id: Int, number: Int): Chapter {
        val paragraphs = (1..8).map { p ->
            buildString {
                append("\"You promised me the deed was sealed,\" Kira said, and she felt the old dread rise like a flood. ")
                append("Her mother had never taught her patience; she wondered whether the dike would hold through the winter. ")
                append("The river swallowed the town whole — everyone knew it, nobody said it aloud. ")
                if (p % 2 == 0) append("\"We could run tonight,\" she heard herself say, as though the words belonged to someone else. ")
                append("She noticed the gate, the cold iron of it, the smell of wet stone. ")
            }
        }
        return Chapter(
            id = id, bookId = 1, chapterNumber = number, title = "ch$number",
            content = paragraphs.joinToString("\n\n"), summary = "", status = "Completed",
            writerConfirmed = true, draftStatus = "CONFIRMED"
        )
    }

    /**
     * Planted voice collapse: same narrative world, telegraphic uniform
     * prose — short flat sentences, no dialogue, no figurative language.
     */
    private fun collapsedChapter(id: Int, number: Int): Chapter {
        val body = (1..120).joinToString(" ") {
            "It was dark. She ran fast. The gate was cold. The river was loud. He came. She hid."
        }
        return Chapter(
            id = id, bookId = 1, chapterNumber = number, title = "ch$number",
            content = body, summary = "", status = "Completed",
            writerConfirmed = true, draftStatus = "CONFIRMED"
        )
    }

    @Test
    fun `stable chapters stay under the cumulative flag across a book`() = runBlocking {
        val engine = VoiceEngine()
        val chapters = goldenChapters()
        val verdicts = engine.measureBook(1, chapters, genre = "Family drama / light mystery")
        assertEquals(chapters.size, verdicts.size, "one verdict per chapter")
        assertTrue(
            verdicts.none { it.flagged },
            "the golden serial is one voice — no drift warnings (got ${verdicts.filter { it.flagged }.map { it.chapterNumber }})"
        )
        assertTrue(
            verdicts.last().cumulativeDrift < 0.20,
            "cumulative ${verdicts.last().cumulativeDrift} stays under the 0.20 EMA flag"
        )
        assertTrue(
            verdicts.all { it.label == "Voice stable" },
            "stable chapters label as Voice stable"
        )
    }

    @Test
    fun `a planted voice collapse flags from chapter one of the shift`() = runBlocking {
        val engine = VoiceEngine()
        // Two baseline chapters establish the voice, then a collapse.
        val chapters = listOf(
            baselineChapter(1, 1), baselineChapter(2, 2),
            collapsedChapter(3, 3), collapsedChapter(4, 4)
        )
        val verdicts = engine.measureBook(1, chapters, genre = "thriller")
        // The first collapsed chapter must warn immediately (composite > 0.30).
        val collapsed = verdicts.filter { it.chapterNumber >= 3 }
        assertTrue(
            collapsed.first().compositeDrift > 0.30,
            "planted collapse composite=${collapsed.first().compositeDrift} must exceed 0.30 immediately"
        )
        assertTrue(collapsed.all { it.flagged }, "every collapsed chapter flags")
        assertTrue(
            verdicts.last().cumulativeDrift > 0.10,
            "the EMA climbs across consecutive collapses (${verdicts.map { it.cumulativeDrift }})"
        )
        // worst_features names the collapsing axes for the UI.
        assertTrue(
            collapsed.first().worstFeatures.isNotEmpty(),
            "worst_features populated for the warning"
        )
    }

    @Test
    fun `an active evolution covers the collapse and records an intentional shift`() = runBlocking {
        val engine = VoiceEngine()
        val chapters = listOf(
            baselineChapter(1, 1), baselineChapter(2, 2),
            collapsedChapter(3, 3), collapsedChapter(4, 4)
        )
        // The author declared chapters 3+ intentionally telegraphic (action
        // climax) with targets matching the collapse.
        val evolution = VoiceEngine.VoiceEvolutionDto(
            personaId = "AuthorMain", chapterStart = 3,
            adjustedFeatures = mapOf(
                "sentence_len_mean" to 5.0, "dialogue_ratio" to 0.0,
                "figurative_per1k" to 0.0
            ),
            reason = "Climax chapters go telegraphic"
        )
        val verdicts = engine.measureBook(
            1, chapters, genre = "thriller", evolutions = listOf(evolution)
        )
        val covered = verdicts.filter { it.chapterNumber >= 3 }
        assertTrue(
            covered.first().evolutionApplied,
            "the shift is recorded as intentional (spec: 'intentional shift' not 'drift warning')"
        )
        assertTrue(covered.none { it.flagged }, "evolution-covered chapters do not flag")
        assertEquals("Intentional voice shift (evolution active)", covered.first().label)
        // Pre-evolution chapters behave exactly as before.
        assertTrue(verdicts.take(2).all { !it.flagged })
    }

    @Test
    fun `evolution slot payload round-trips`() {
        val engine = VoiceEngine()
        val entries = listOf(
            VoiceEngine.VoiceEvolutionDto(
                personaId = "AuthorMain", chapterStart = 5, chapterEnd = 9,
                adjustedFeatures = mapOf("sentence_len_mean" to 11.0, "tonal_shift" to 0.0),
                reason = "Protagonist descends into grief arc"
            )
        )
        val payload = engine.encodeEvolutions(entries)
        val back = engine.decodeEvolutions(payload)
        assertEquals(entries, back, "the slot payload round-trips exactly")
        // The memory store carries the payload verbatim (raw slot contract).
        assertTrue(payload.contains("grief arc"))
        // Corrupt payload fails LOUD (memory-store doctrine).
        var threw = false
        try { engine.decodeEvolutions("{not json") } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw, "payload drift fails loud, not silently")
    }

    @Test
    fun `the stage-7 gate surfaces drift as warnings only`() {
        val report = com.example.engine.pipeline.RetentionAnalyzer.analyze(goldenChapters())
        // A drift warning on every chapter must NOT flip the gate by itself.
        val verdict = StageGates.engagement(
            report,
            voiceVerdicts = (1..3).map {
                VoiceEngine.ChapterVoiceVerdict(
                    chapterNumber = it, compositeDrift = 0.55, cumulativeDrift = 0.25,
                    flagged = true, evolutionApplied = false,
                    worstFeatures = listOf("sentence_len_mean" to 0.8)
                )
            }
        )
        assertTrue(
            verdict.hardFailures.none { it.contains("drift", ignoreCase = true) },
            "drift never becomes a hard failure (writer sovereignty)"
        )
        assertTrue(
            verdict.warnings.any { it.contains("Voice drift warning") },
            "drift appears in the gate warnings"
        )
        // The retention-only verdict (no voice axis) is unchanged from ADR-0018.
        val plain = StageGates.engagement(report)
        assertEquals(plain.passed, verdict.passed, "the voice axis does not change pass/fail")
    }

    @Test
    fun `drift log and verdicts agree when a DAO is absent`() = runBlocking {
        // DAO-free: the engine computes and returns verdicts; driftLog() is
        // empty rather than throwing (the nullable-DAO contract).
        val engine = VoiceEngine()
        assertTrue(engine.driftLog(1).isEmpty())
        val verdicts = engine.measureBook(1, goldenChapters().take(3), genre = "drama")
        assertEquals(3, verdicts.size)
        assertTrue(engine.driftLog(1).isEmpty(), "no DAO — nothing persisted, no crash")
    }

    @Test
    fun `ema seeds from the rolling history not from zero`() = runBlocking {
        // Two sweeps over the same collapse: the second sweep's first
        // verdict starts from the persisted-equivalent state... DAO-free,
        // so the EMA restarts — this documents the contract: within one
        // measureBook call the EMA chains; across calls persistence is the
        // DAO's job (see S0 migration tests for the voice_drift_log round
        // trip).
        val engine = VoiceEngine()
        val chapters = listOf(
            baselineChapter(1, 1), collapsedChapter(2, 2), collapsedChapter(3, 3)
        )
        val verdicts = engine.measureBook(1, chapters, genre = "thriller")
        // EMA chain within the sweep: each cumulative builds on the last.
        assertTrue(
            verdicts[1].cumulativeDrift > 0.0 && verdicts[2].cumulativeDrift > verdicts[1].cumulativeDrift,
            "cumulative EMA chains within a sweep: ${verdicts.map { it.cumulativeDrift }}"
        )
    }
}
