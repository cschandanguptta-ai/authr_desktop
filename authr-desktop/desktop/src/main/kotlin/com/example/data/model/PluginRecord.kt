package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plugin registry record (v3.0, plan §8, Appendix A).
 *
 * Plot structures and author personas are JSON plugins loaded from
 * plugins/structures and plugins/personas inside the .authr container, each
 * validated against the published plugin schema before registration. The
 * shipped library (16 structures, 22 personas) is the floor, not the ceiling.
 */
@Entity(
    tableName = "plugins",
    indices = [Index(value = ["kind", "name"], unique = true)]
)
data class PluginRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** "structure" | "persona" */
    val kind: String,
    val name: String,
    /** Path relative to the .authr container root. */
    val path: String,
    val version: String = "1.0.0",
    val enabled: Boolean = true
)
