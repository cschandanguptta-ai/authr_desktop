package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val heroName: String,
    val genres: String,
    val format: String = "Novel",
    val duration: String,
    val hook: String,
    val extras: String,
    val part1: String = "",
    val part2: String = "",
    val part3: String = "",
    val part4: String = "",
    val part5: String = "",
    val wordCount: Int = 0,
    val persona: String = "Standard",
    val ambientTheme: String = "None",
    val ambientVolume: Float = 0.15f,
    val metaTitle: String = "",
    val metaDesc: String = "",
    val metaTags: String = "",
    val metaThumbPrompt: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getCombinedStory(): String {
        return listOf(part1, part2, part3, part4, part5)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
}
