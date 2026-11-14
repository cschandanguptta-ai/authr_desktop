package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VOICE EVOLUTION (ADR-0019, spec schema v2.2 `voice_evolution`): an
 * author-declared intentional voice change over a chapter range. Active
 * evolutions suppress false drift alerts in [VoiceDriftTracker] — the
 * author said the voice SHOULD move, so the tracker must not flag it.
 *
 * Stored as an editorial-memory slot (payload = JSON list of [VoiceEvolutionEntry])
 * rather than a dedicated table: low write volume, per-book scope, and the
 * additive-only doctrine (ADR-0002/0016) lets new entry fields arrive
 * without another migration.
 */
data class VoiceEvolutionEntry(
    /** Which persona/voice this evolution applies to (e.g. "AuthorMain"). */
    val personaId: String,
    /** First chapter of the evolution range (inclusive). */
    val chapterStart: Int,
    /** Last chapter of the range; null = to end of book. */
    val chapterEnd: Int? = null,
    /** Feature targets that intentionally move, e.g. {"sentence_len_mean": 11.0}. */
    val adjustedFeatures: Map<String, Double> = emptyMap(),
    /** Human-readable reason, e.g. "Protagonist descends into grief arc". */
    val reason: String,
    /** Who approved the evolution; writer sovereignty default "author". */
    val approvedBy: String = "author"
) {
    /** True when [chapterNumber] falls inside this evolution's range. */
    fun covers(chapterNumber: Int): Boolean =
        chapterNumber >= chapterStart && (chapterEnd == null || chapterNumber <= chapterEnd)

    companion object {
        /**
         * Feature-level suppression: if an evolution declares a target for a
         * feature, drift on that feature is expected while the range is
         * active. The tracker consults this before flagging.
         */
        fun activeFor(
            entries: List<VoiceEvolutionEntry>,
            chapterNumber: Int,
            personaId: String? = null
        ): List<VoiceEvolutionEntry> = entries.filter {
            it.covers(chapterNumber) && (personaId == null || it.personaId == personaId)
        }
    }
}
