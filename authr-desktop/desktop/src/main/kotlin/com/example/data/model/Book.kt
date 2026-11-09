package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val genre: String,
    val synopsis: String,
    val outline: String,
    val personaId: String = "standard",
    val format: String = "Novel",
    val targetWordCount: Int = 50000,
    val narrativeStructure: String = "Hero's Journey",
    val chapterLength: String = "Medium (3000 words)",
    val isSerialized: Boolean = false,
    val targetChapters: Int = 0,
    val releaseSchedule: String = "Mon/Wed/Fri",
    // --- Serialization profile (v3.0 schema v2, plan §6.12 / Table 21; Appendix A) ---
    // NOTE (migration): these columns carry SQL DEFAULT clauses in
    // MIGRATION_14_15, but deliberately declare no @ColumnInfo defaultValue:
    // Room's schema validation strictly enforces entity-declared defaults,
    // while tolerating database-side defaults (TableInfo.equalsCommon). The
    // Kotlin defaults below govern every Room write, so behavior is identical.
    /** Free tier size; conversion gates bound to it. */
    val freeTierChapters: Int = 5,
    /** Advisory chapter envelope, adjustable per genre and platform. */
    val wordMin: Int = 1000,
    val wordMax: Int = 2000,
    /** Buffer meter target (4-8 range, plan §6.10). */
    val bufferTarget: Int = 8,
    /** Default platform export profile (plan §6.13). */
    val exportProfile: String = "royal_road",
    val compiledManuscript: String = "",
    val currentPhase: String = "Outlining",
    val storyBible: String = "",
    val characterBible: String = "",
    val worldBible: String = "",
    val plotPointsHistory: String = "",
    val memoryState: String = "",
    val agentLogs: String = "",
    val marketingMaterials: String = "",
    val coverPrompt: String = "",
    val completionPercentage: Int = 0,
    val wordsPerMinute: Int = 230,
    val readabilityScore: String = "Flesch-Kincaid: Grade 7",
    val emotionalIntensity: String = "Balanced",
    val pacingHeatmap: String = "Moderate",
    val continuityWarnings: String = "No critical warnings",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)
