package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SUMMARY CACHE (ADR-0020, spec schema v2.2 `summary_cache`): pre-computed
 * chapter/manuscript summaries at four compression levels for the
 * hierarchical context engine. Invalidation is driven by [sourceHash]
 * comparison — if the source content changes, the cached summary is stale
 * and must be regenerated (SHA-256 of the source text).
 */
@Entity(
    tableName = "summary_cache",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "scope", "level"], unique = true)]
)
data class SummaryCache(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    /** 'chapter:5' | 'chapters:1-10' | 'full_manuscript' | 'worldbible' | 'character:Elena'. */
    val scope: String,
    /** 'full' | 'medium' | 'brief' | 'oneliner'. */
    val level: String,
    val summaryText: String,
    /** Estimated token count of the summary text (calibration factor applied). */
    val tokenCount: Int,
    /** SHA-256 hex of the source content — the invalidation trigger. */
    val sourceHash: String,
    /** Which model generated this summary (null = deterministic/rule-based). */
    val modelUsed: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val LEVEL_FULL = "full"
        const val LEVEL_MEDIUM = "medium"
        const val LEVEL_BRIEF = "brief"
        const val LEVEL_ONELINER = "oneliner"
        /** All levels, most detailed first; must stay append-only. */
        val LEVELS = listOf(LEVEL_FULL, LEVEL_MEDIUM, LEVEL_BRIEF, LEVEL_ONELINER)
        /** Spec context-engineering table: token share of the budget per level. */
        val LEVEL_TOKEN_SHARE = mapOf(
            LEVEL_FULL to 1.00,
            LEVEL_MEDIUM to 0.15,
            LEVEL_BRIEF to 0.05,
            LEVEL_ONELINER to 0.02
        )
        /** Spec LEVEL_DOWNGRADE map used when a layer overflows its budget. */
        val LEVEL_DOWNGRADE = mapOf(
            LEVEL_FULL to LEVEL_MEDIUM,
            LEVEL_MEDIUM to LEVEL_BRIEF,
            LEVEL_BRIEF to LEVEL_ONELINER
        )

        fun chapterScope(chapterNumber: Int): String = "chapter:$chapterNumber"
        fun chapterRangeScope(from: Int, to: Int): String = "chapters:$from-$to"
        const val SCOPE_FULL_MANUSCRIPT = "full_manuscript"
        const val SCOPE_WORLDBIBLE = "worldbible"
        fun characterScope(name: String): String = "character:$name"
    }
}
