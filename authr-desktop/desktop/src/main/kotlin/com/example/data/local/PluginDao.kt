package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PluginRecord
import kotlinx.coroutines.flow.Flow

/** DAO for the plugin registry (v3.0, plan §8). */
@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY kind, name")
    fun getAllPlugins(): Flow<List<PluginRecord>>

    @Query("SELECT * FROM plugins WHERE kind = :kind AND enabled = 1 ORDER BY name")
    fun getEnabledPlugins(kind: String): Flow<List<PluginRecord>>

    @Query("SELECT * FROM plugins")
    suspend fun getAllPluginsSync(): List<PluginRecord>

    @Query("SELECT * FROM plugins WHERE kind = :kind AND name = :name LIMIT 1")
    suspend fun findPlugin(kind: String, name: String): PluginRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: PluginRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugins(plugins: List<PluginRecord>)

    @Update
    suspend fun updatePlugin(plugin: PluginRecord)

    @Query("DELETE FROM plugins WHERE id = :pluginId")
    suspend fun deletePlugin(pluginId: Int)
}
