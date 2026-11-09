package com.example

import com.example.plugins.PluginRegistry
import com.example.plugins.PluginSchema
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * P0 gate for the plugin scaffold (plan §8): the shipped library is the
 * floor — 16 structures + 22 personas — and every plugin file in the
 * container validates against the published schema before registration.
 */
class PluginRegistryTest {

    @Test
    fun `shipped library has exactly 16 structures and 22 personas`() {
        assertEquals(16, com.example.plugins.ShippedPlugins.STRUCTURES.size, "Table 26: 16 structures")
        assertEquals(22, com.example.plugins.ShippedPlugins.PERSONAS.size, "Table 27: 22 personas")
    }

    @Test
    fun `every shipped plugin validates against the schema`() {
        val failures = mutableListOf<String>()
        com.example.plugins.ShippedPlugins.STRUCTURES.forEach { text ->
            val r = PluginSchema.validate(text)
            if (!r.valid) failures += "structure ${r.errors.joinToString("; ")}"
        }
        com.example.plugins.ShippedPlugins.PERSONAS.forEach { text ->
            val r = PluginSchema.validate(text)
            if (!r.valid) failures += "persona ${r.errors.joinToString("; ")}"
        }
        assertTrue(failures.isEmpty(), "all shipped plugins must validate: $failures")
    }

    @Test
    fun `registry loads the scaffolded library with zero invalid files`() {
        val container = File.createTempFile("authr_plugins_container", ".tmp")
        container.delete(); container.mkdirs()
        try {
            val written = PluginRegistry.scaffoldDefaults(container)
            assertEquals(38, written, "scaffold must write 16 structures + 22 personas")

            val report = PluginRegistry.loadFromContainer(container)
            assertEquals(38, report.plugins.size)
            assertEquals(38, report.totalScanned)
            assertTrue(report.invalidFiles.isEmpty(), "no invalid files expected: ${report.invalidFiles}")

            val structures = report.plugins.filter { it.kind == PluginSchema.KIND_STRUCTURE }
            val personas = report.plugins.filter { it.kind == PluginSchema.KIND_PERSONA }
            assertEquals(16, structures.size)
            assertEquals(22, personas.size)
            // Serial-native structures must be present (SCR-4).
            assertTrue(structures.any { it.name == "micro_arc_cascade" })
            assertTrue(structures.any { it.name == "serial_seven_beat" })
            // The plan's example persona (§8) must be present.
            assertTrue(personas.any { it.name == "progression_fantasy" })
        } finally {
            container.deleteRecursively()
        }
    }

    @Test
    fun `scaffold never overwrites user edits`() {
        val container = File.createTempFile("authr_plugins_edit", ".tmp")
        container.delete(); container.mkdirs()
        try {
            PluginRegistry.scaffoldDefaults(container)
            val personaFile = File(container, "plugins/personas/progression_fantasy.json")
            personaFile.writeText(personaFile.readText().replace("Progression Fantasy", "My Custom Voice"))
            val written = PluginRegistry.scaffoldDefaults(container)
            assertEquals(0, written, "second scaffold must write nothing")
            assertTrue(personaFile.readText().contains("My Custom Voice"), "user edits must win")
        } finally {
            container.deleteRecursively()
        }
    }

    @Test
    fun `schema rejects malformed plugins`() {
        val badPersona = """{"id":"bad_persona","kind":"persona","name":"Bad","version":"1.0","prompt_directive":"",
            "sentence_length":"very-long","sensory_focus":[""],"pacing_curve":"x","cliffhanger_lean":"explosion","banned_patterns":[]}"""
        val result = PluginSchema.validate(badPersona)
        assertFalse(result.valid, "malformed persona must fail validation")
        assertTrue(result.errors.any { it.contains("version") })
        assertTrue(result.errors.any { it.contains("sentence_length") })
        assertTrue(result.errors.any { it.contains("cliffhanger_lean") })
        assertTrue(result.errors.any { it.contains("prompt_directive") })

        assertFalse(PluginSchema.validate("{\"kind\":\"persona\"}").valid, "missing fields must fail")
        assertFalse(PluginSchema.validate("not json at all").valid, "invalid JSON must fail")
        assertFalse(
            PluginSchema.validate("""{"id":"s","kind":"structure","name":"S","version":"1.0.0","description":"","best_fit":"","beats":[]}""").valid,
            "empty beats must fail"
        )
    }

    @Test
    fun `registry reports invalid dropped-in files without loading them`() {
        val container = File.createTempFile("authr_plugins_bad", ".tmp")
        container.delete(); container.mkdirs()
        try {
            PluginRegistry.scaffoldDefaults(container)
            File(container, "plugins/personas/broken.json").writeText("{ not valid json")
            val report = PluginRegistry.loadFromContainer(container)
            assertEquals(38, report.plugins.size, "valid plugins still load")
            assertTrue(report.invalidFiles.any { it.startsWith("broken.json") }, "invalid file must be reported")
        } finally {
            container.deleteRecursively()
        }
    }
}
