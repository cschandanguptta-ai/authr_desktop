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
