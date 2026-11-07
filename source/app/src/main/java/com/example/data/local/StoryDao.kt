package com.example.data.local

import androidx.room.*
import com.example.data.model.Story
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<Story>>

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getStoryById(id: Int): Story?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story): Long

    @Update
    suspend fun updateStory(story: Story)

    @Delete
    suspend fun deleteStory(story: Story)

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStoryById(id: Int)

    @Query("""
        SELECT stories.* 
        FROM stories 
        JOIN stories_fts ON stories.id = stories_fts.rowid 
        WHERE stories_fts MATCH :query
    """)
    suspend fun searchStories(query: String): List<Story>
}
