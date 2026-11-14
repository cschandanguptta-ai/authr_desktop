package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * VOICE DRIFT LOG (ADR-0019, spec schema v2.2 `voice_drift_log`): one row
 * per chapter-level (or beat-level) drift measurement. The cumulative
 * column carries the exponential moving average (alpha = 0.1) across the
 * book, enabling detection of slow voice collapse (spec Invariant 15).
 *
 * Desktop adaptation of the spec DDL: `beatIndex` nullable (NULL =
 * chapter-level); timestamps are epoch millis (Long) to match the codebase
 * convention instead of SQLite datetime('now'); CHECK constraints live in
 * the Kotlin API surface (Room validates types, the tracker validates
 * profileType values).
 */
@Entity(
    tableName = "voice_drift_log",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "chapterId"])]
)
data class VoiceDriftLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterId: Int,
    /** NULL = chapter-level measurement; INTEGER = beat-level. */
    val beatIndex: Int? = null,
    /** 'author' | 'character' | 'register'. */
    val profileType: String = "author",
    /** e.g. 'AuthorMain' or a character name. */
    val profileName: String? = null,
    /** JSON: full feature vector of the generated text (VoiceFeatureVector). */
    val measuredFeatures: String,
    /** JSON: {feature_name: drift_score} in 0.0..1.0. */
    val perFeatureDrift: String,
    /** Weighted composite drift 0.0..1.0. */
    val compositeDrift: Double,
    /** Running EMA: alpha*composite + (1-alpha)*previous cumulative. */
    val cumulativeDrift: Double,
    /** 1 if composite > 0.30 or cumulative > 0.20. */
    val flagged: Boolean = false,
    /** 1 if an active voice_evolution suppressed the alert. */
    val evolutionApplied: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROFILE_AUTHOR = "author"
        const val PROFILE_CHARACTER = "character"
        const val PROFILE_REGISTER = "register"
        /** Spec thresholds (roadmap acceptance + engine-enhancements §2.4). */
        const val COMPOSITE_FLAG = 0.30
        const val CUMULATIVE_FLAG = 0.20
        /** Spec default EMA smoothing factor (tunable per project). */
        const val DEFAULT_EMA_ALPHA = 0.1
    }
}
