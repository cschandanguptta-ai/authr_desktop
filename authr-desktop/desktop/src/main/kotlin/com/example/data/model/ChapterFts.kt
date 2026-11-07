package com.example.data.model

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Chapter::class)
@Entity(tableName = "chapters_fts")
data class ChapterFts(
    val bookId: Int,
    val title: String,
    val content: String,
    val summary: String
)
