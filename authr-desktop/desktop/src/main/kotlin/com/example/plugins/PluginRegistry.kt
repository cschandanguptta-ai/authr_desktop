package com.example.plugins

import com.example.data.model.PluginRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Plugin registry (v3.0, plan §8).
 *
 * Loads structure/persona plugins from the plugin directory inside the .authr
 * container, validates every file against [PluginSchema] before registration,
 * and offers the shipped library as the scaffold for new containers. The
 * user-extensible directory is authoritative: dropped-in files are picked up
 * on the next project open.
 */
object PluginRegistry {

    const val STRUCTURES_DIR = "plugins/structures"
    const val PERSONAS_DIR = "plugins/personas"

    private val json = Json { ignoreUnknownKeys = true }

    data class LoadReport(
        val plugins: List<PluginRecord>,
        val invalidFiles: List<String>,
        val totalScanned: Int
    )

    fun structuresDir(containerRoot: File): File = File(containerRoot, STRUCTURES_DIR)
    fun personasDir(containerRoot: File): File = File(containerRoot, PERSONAS_DIR)

    /**
     * Scaffolds the shipped library into a container's plugin directory.
     * Existing files are never overwritten (user edits win).
     */
    fun scaffoldDefaults(containerRoot: File): Int {
        var written = 0
        ShippedPlugins.STRUCTURES.forEach { text ->
            if (writeIfAbsent(structuresDir(containerRoot), text)) written++
        }
        ShippedPlugins.PERSONAS.forEach { text ->
            if (writeIfAbsent(personasDir(containerRoot), text)) written++
        }
        return written
    }

    private fun writeIfAbsent(dir: File, pluginJson: String): Boolean {
        dir.mkdirs()
        val id = runCatching {
            json.parseToJsonElement(pluginJson).jsonObject["id"]?.jsonPrimitive?.content
        }.getOrNull() ?: return false
        val f = File(dir, "$id.json")
        if (f.exists()) return false
        f.writeText(pluginJson)
        return true
    }

    /**
     * Scans the container's plugin directory, validates every JSON file and
     * returns the registry records. Invalid files are reported, never loaded.
     */
    fun loadFromContainer(containerRoot: File): LoadReport {
        val records = mutableListOf<PluginRecord>()
        val invalid = mutableListOf<String>()
        var scanned = 0

        listOf(
            PluginSchema.KIND_STRUCTURE to structuresDir(containerRoot),
            PluginSchema.KIND_PERSONA to personasDir(containerRoot)
        ).forEach { (kind, dir) ->
            dir.listFiles { f -> f.isFile && f.extension == "json" }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    scanned++
                    val text = runCatching { file.readText() }.getOrNull() ?: ""
                    val result = PluginSchema.validate(text)
                    if (result.valid) {
                        val id = runCatching {
                            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content
                        }.getOrNull()
                        val name = runCatching {
                            json.parseToJsonElement(text).jsonObject["name"]?.jsonPrimitive?.content
                        }.getOrNull()
                        val version = runCatching {
                            json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.content
                        }.getOrNull()
                        if (id == null) {
                            invalid += "${file.name}: missing id"
                        } else {
                            records += PluginRecord(
                                id = records.size + 1,
                                kind = kind,
                                name = id,
                                path = file.relativeTo(containerRoot).invariantSeparatorsPath,
                                version = version ?: "1.0.0",
                                enabled = true
                            )
                        }
                    } else {
                        invalid += "${file.name}: ${result.errors.joinToString("; ")}"
                    }
                }
        }
        return LoadReport(records, invalid, scanned)
    }

    /** Reads one plugin's payload back (validated) for the editor UI. */
    fun readPlugin(file: File): String? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return if (PluginSchema.validate(text).valid) text else null
    }
}
