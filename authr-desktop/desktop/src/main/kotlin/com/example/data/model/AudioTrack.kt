package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_tracks")
data class AudioTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storyId: Int? = null,
    val title: String,
    val filePath: String,
    val voiceName: String,
    val language: String,
    val rate: Float,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
