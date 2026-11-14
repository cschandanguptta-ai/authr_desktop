package com.example.engine.voice

import com.example.data.local.VoiceDriftDao
import com.example.data.model.Chapter
import com.example.data.model.VoiceDriftLog
import com.example.data.model.VoiceEvolutionEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * VOICE ENGINE (v3.0 port S1, spec ADR-0013 = desktop ADR-0020): the
 * persistence-and-orchestration layer over the drift tracker.
 *
 * Responsibilities (spec §1.3/§1.4):
 *  - measure drift for a confirmed chapter against the book's voice target
 *    (persona numeric DNA when present — S4 — else the rolling baseline of
 *    earlier confirmed chapters), evolution overrides applied to the target;
 *  - persist every measurement to voice_drift_log (append-only; the EMA
 *    reads its seed from the last row — get_latest_cumulative_drift);
 *  - expose the per-book drift report for the engagement analytics and the
 *    UI (complementary axis: retention measures craft signals, drift
 *    measures voice fidelity — two separate systems, never merged).
 *
 * The DAO is OPTIONAL (nullable) following the ADR-0018 repository pattern:
 * JVM tests and the pipeline run Room-free; without a DAO the engine still
 * computes and returns verdicts, it just does not persist them.
 */
class VoiceEngine(
    private val dao: VoiceDriftDao? = null
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val featureMapSerializer =
        MapSerializer(String.serializer(), Double.serializer())

    private fun evolutionListSerializer() =
        ListSerializer(VoiceEvolutionDto.serializer())

    /** Serializable mirror of VoiceEvolutionEntry (entity stays Room-pure). */
    @kotlinx.serialization.Serializable
    data class VoiceEvolutionDto(
        val personaId: String = "AuthorMain",
        val chapterStart: Int,
        val chapterEnd: Int? = null,
        val adjustedFeatures: Map<String, Double> = emptyMap(),
        val reason: String = "",
        val approvedBy: String = "author"
    ) {
        fun covers(chapterNumber: Int): Boolean =
            chapterNumber >= chapterStart && (chapterEnd == null || chapterNumber <= chapterEnd)

        fun toEntry(): VoiceEvolutionEntry = VoiceEvolutionEntry(
            personaId = personaId, chapterStart = chapterStart, chapterEnd = chapterEnd,
            adjustedFeatures = adjustedFeatures, reason = reason, approvedBy = approvedBy
        )

        companion object {
            fun from(e: VoiceEvolutionEntry): VoiceEvolutionDto = VoiceEvolutionDto(
                personaId = e.personaId, chapterStart = e.chapterStart, chapterEnd = e.chapterEnd,
                adjustedFeatures = e.adjustedFeatures, reason = e.reason, approvedBy = e.approvedBy
            )
        }
    }

    /** One chapter's voice verdict, as consumed by stage-7 analytics. */
    data class ChapterVoiceVerdict(
        val chapterNumber: Int,
        val compositeDrift: Double,
        val cumulativeDrift: Double,
        val flagged: Boolean,
        val evolutionApplied: Boolean,
        val worstFeatures: List<Pair<String, Double>>
    ) {
        /** UI/analytics line (spec: "intentional shift" instead of "drift warning"). */
        val label: String
            get() = when {
                evolutionApplied -> "Intentional voice shift (evolution active)"
                flagged -> "Voice drift warning"
                else -> "Voice stable"
            }
    }

    /**
     * Measure one confirmed chapter. The baseline strategy (spec):
     * explicit persona targets win; otherwise the rolling mean of earlier
     * confirmed chapters; the FIRST confirmed chapter of a book has no
     * baseline and is recorded with zero drift (it becomes the baseline).
     */
    suspend fun measureChapter(
        bookId: Int,
        chapter: Chapter,
        genre: String,
        personaTargets: Map<String, Double> = emptyMap(),
        evolutions: List<VoiceEvolutionDto> = emptyList(),
        sweepState: SweepState? = null
    ): ChapterVoiceVerdict {
        val measured = VoiceFeatureExtractor.extract(chapter.content).toMap()

        // Evolution coverage (spec check_evolution_coverage): the adjusted
        // features of an entry covering this chapter override the target.
        val active = evolutions.filter { it.covers(chapter.chapterNumber) }
        val overrides = LinkedHashMap<String, Double>()
        active.forEach { e -> e.adjustedFeatures.forEach { (k, v) -> overrides[k] = v } }

        // Baseline: persona targets, else rolling history mean. The sweep
        // state (current sweep) wins over the persisted log — it is always
        // fresher; the log covers cross-sweep continuation.
        val history = sweepState?.history ?: historyFeatures(bookId)
        val baseline = when {
            personaTargets.isNotEmpty() -> personaTargets
            history.isNotEmpty() -> VoiceDriftTracker.rollingBaseline(history)
            else -> emptyMap() // first chapter: no baseline yet
        }

        // EMA seed: the sweep's running EMA first, else the persisted log
        // (spec get_latest_cumulative_drift).
        val prevCumulative = sweepState?.cumulative
            ?: dao?.latestForChapter(bookId, chapter.id)?.cumulativeDrift
            ?: dao?.forBook(bookId)?.lastOrNull()?.cumulativeDrift
            ?: 0.0

        val weights = when (val key = VoiceDriftTracker.genreKey(genre)) {
            "default" -> VoiceDriftTracker.DEFAULT_WEIGHTS
            else -> VoiceDriftTracker.GENRE_WEIGHTS.getValue(key)
        }

        val result: VoiceDriftTracker.DriftResult
        if (baseline.isEmpty() && overrides.isEmpty()) {
            // First chapter, no persona targets: record the opening voice
            // (zero drift) so later chapters have a baseline.
            result = VoiceDriftTracker.DriftResult(
                perFeatureDrift = emptyMap(), compositeDrift = 0.0,
                cumulativeDrift = prevCumulative, flagged = false,
                evolutionApplied = false, worstFeatures = emptyList()
            )
        } else {
            result = VoiceDriftTracker.computeDrift(
                measured = measured,
                targetFeatures = baseline,
                previousCumulative = prevCumulative,
                weights = weights,
                evolutionOverrides = overrides
            )
        }

        // The measured features join the sweep history (the rolling
        // baseline for later chapters) and the EMA rolls forward.
        sweepState?.let {
            it.history += measured
            it.cumulative = result.cumulativeDrift
        }

        return persist(bookId, chapter, measured, result)
    }

    /** Full-book sweep (stage 7 runs this over all confirmed chapters). */
    suspend fun measureBook(
        bookId: Int,
        confirmedChapters: List<Chapter>,
        genre: String,
        personaTargets: Map<String, Double> = emptyMap(),
        evolutions: List<VoiceEvolutionDto> = emptyList()
    ): List<ChapterVoiceVerdict> {
        val state = SweepState()
        return confirmedChapters
            .sortedBy { it.chapterNumber }
            .map { measureChapter(bookId, it, genre, personaTargets, evolutions, state) }
    }

    /**
     * In-sweep accumulator: the measured-feature history and the running
     * EMA across one measureBook pass — the in-memory mirror of what the
     * DAO persists, so the engine behaves identically with and without a
     * DAO inside a sweep.
     */
    class SweepState {
        val history = mutableListOf<Map<String, Double>>()
        var cumulative: Double = 0.0
    }

    /** The persisted drift series (empty without a DAO). */
    suspend fun driftLog(bookId: Int): List<VoiceDriftLog> =
        dao?.forBook(bookId) ?: emptyList()

    /** Active evolutions covering a chapter (UI + gate surface). */
    fun activeEvolutions(
        evolutions: List<VoiceEvolutionDto>,
        chapterNumber: Int
    ): List<VoiceEvolutionDto> = evolutions.filter { it.covers(chapterNumber) }

    // ------------------------------------------------------------------
    // Evolution slot (de)serialization — the editorial-memory slot payload

    fun encodeEvolutions(entries: List<VoiceEvolutionDto>): String =
        json.encodeToString(evolutionListSerializer(), entries)

    fun decodeEvolutions(payload: String): List<VoiceEvolutionDto> = try {
        json.decodeFromString(evolutionListSerializer(), payload)
    } catch (e: Exception) {
        // Fail loud per the memory-store doctrine: payload drift is a bug.
        throw IllegalStateException("voice_evolution slot failed to parse: ${e.message}", e)
    }

    // ------------------------------------------------------------------

    /** Feature history from the persisted log (JSON column, defensive). */
    private suspend fun historyFeatures(bookId: Int): List<Map<String, Double>> =
        driftLog(bookId).mapNotNull { row ->
            try { json.decodeFromString(featureMapSerializer, row.measuredFeatures) }
            catch (e: Exception) { null } // corrupt row: skip, never crash the sweep
        }

    private suspend fun persist(
        bookId: Int,
        chapter: Chapter,
        measured: Map<String, Double>,
        result: VoiceDriftTracker.DriftResult
    ): ChapterVoiceVerdict {
        val row = VoiceDriftLog(
            bookId = bookId,
            chapterId = chapter.id,
            beatIndex = null, // chapter-level measurement
            profileType = VoiceDriftLog.PROFILE_AUTHOR,
            profileName = "AuthorMain",
            measuredFeatures = json.encodeToString(featureMapSerializer, measured),
            perFeatureDrift = json.encodeToString(featureMapSerializer, result.perFeatureDrift),
            compositeDrift = result.compositeDrift,
            cumulativeDrift = result.cumulativeDrift,
            flagged = result.flagged,
            evolutionApplied = result.evolutionApplied
        )
        dao?.insert(row)
        return ChapterVoiceVerdict(
            chapterNumber = chapter.chapterNumber,
            compositeDrift = result.compositeDrift,
            cumulativeDrift = result.cumulativeDrift,
            flagged = result.flagged,
            evolutionApplied = result.evolutionApplied,
            worstFeatures = result.worstFeatures
        )
    }
}
