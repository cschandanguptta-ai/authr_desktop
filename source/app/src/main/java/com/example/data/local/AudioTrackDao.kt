package com.example.data.local

import androidx.room.*
import com.example.data.model.AudioTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks ORDER BY timestamp DESC")
    fun getAllAudioTracks(): Flow<List<AudioTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioTrack(audioTrack: AudioTrack): Long

    @Delete
    suspend fun deleteAudioTrack(audioTrack: AudioTrack)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteAudioTrackById(id: Int)
}
