package com.example.data.model

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Story::class)
@Entity(tableName = "stories_fts")
data class StoryFts(
    val title: String,
    val hook: String,
    val part1: String,
    val part2: String,
    val part3: String,
    val part4: String,
    val part5: String
)
