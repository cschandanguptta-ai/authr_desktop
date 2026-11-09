package com.example.plugins

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Plugin schema validation (v3.0, plan §8).
 *
 * Structures and personas are JSON plugins loaded from the plugin directory
 * inside the .authr container (plugins/structures, plugins/personas) and
 * validated against this published contract before registration. The drafting
 * prompt assembles from these fields; the cliché cleaner reads bannedPatterns
 * as an extra project-local pattern class.
 */
object PluginSchema {

    private val json = Json { ignoreUnknownKeys = true }

    const val KIND_STRUCTURE = "structure"
    const val KIND_PERSONA = "persona"

    /** The complete persona contract (plan §8, verbatim field set). */
    val PERSONA_FIELDS = listOf(
        "id", "kind", "name", "version", "prompt_directive", "sentence_length",
        "sensory_focus", "pacing_curve", "cliffhanger_lean", "banned_patterns"
    )

    /** The complete structure contract (plan §8, Table 26 shapes). */
    val STRUCTURE_FIELDS = listOf(
        "id", "kind", "name", "version", "description", "best_fit", "beats", "serial_native"
    )

    val SENTENCE_LENGTHS = setOf("short", "medium", "long", "mixed")
    val CLIFFHANGER_LEANS = setOf(
        "danger", "emotional_revelation", "mystery_discovery",
        "irreversible_choice", "reversal", "anticipation", "quiet"
    )
    val VERSION_REGEX = Regex("""\d+\.\d+\.\d+""")
    val ID_REGEX = Regex("""[a-z0-9_]+""")

    data class ValidationResult(val valid: Boolean, val errors: List<String>)

    fun validate(text: String): ValidationResult {
        val errors = mutableListOf<String>()
        val obj: JsonObject = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: return ValidationResult(false, listOf("Plugin root must be a JSON object"))
        } catch (e: Exception) {
            return ValidationResult(false, listOf("Invalid JSON: ${e.message}"))
        }

        val kind = obj["kind"]?.jsonPrimitive?.content
        when (kind) {
            KIND_PERSONA -> validatePersona(obj, errors)
            KIND_STRUCTURE -> validateStructure(obj, errors)
            else -> errors += "kind must be 'structure' or 'persona'"
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun requireFields(obj: JsonObject, fields: List<String>, errors: MutableList<String>) {
        fields.forEach { f ->
            if (obj[f] == null) errors += "Missing required field '$f'"
        }
    }

    private fun validatePersona(obj: JsonObject, errors: MutableList<String>) {
        requireFields(obj, PERSONA_FIELDS, errors)
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        if (!ID_REGEX.matches(id)) errors += "id '$id' must match [a-z0-9_]+"
        obj["version"]?.jsonPrimitive?.content?.let {
            if (!VERSION_REGEX.matches(it)) errors += "version '$it' must be semver (x.y.z)"
        }
        obj["sentence_length"]?.jsonPrimitive?.content?.let {
            if (it !in SENTENCE_LENGTHS) errors += "sentence_length '$it' must be one of $SENTENCE_LENGTHS"
        }
        obj["sensory_focus"]?.jsonArray?.forEachIndexed { i, e ->
            if (e.jsonPrimitive.content.isBlank()) errors += "sensory_focus[$i] must not be blank"
        }
        obj["banned_patterns"]?.jsonArray?.forEachIndexed { i, e ->
            if (e.jsonPrimitive.content.isBlank()) errors += "banned_patterns[$i] must not be blank"
        }
        obj["cliffhanger_lean"]?.jsonPrimitive?.content?.let {
            if (it !in CLIFFHANGER_LEANS) errors += "cliffhanger_lean '$it' must be one of $CLIFFHANGER_LEANS"
        }
        if ((obj["prompt_directive"]?.jsonPrimitive?.content ?: "").isBlank()) {
            errors += "prompt_directive must not be blank"
        }
    }

    private fun validateStructure(obj: JsonObject, errors: MutableList<String>) {
        requireFields(obj, STRUCTURE_FIELDS, errors)
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        if (!ID_REGEX.matches(id)) errors += "id '$id' must match [a-z0-9_]+"
        obj["version"]?.jsonPrimitive?.content?.let {
            if (!VERSION_REGEX.matches(it)) errors += "version '$it' must be semver (x.y.z)"
        }
        if ((obj["description"]?.jsonPrimitive?.content ?: "").isBlank()) {
            errors += "description must not be blank"
        }
        val beats = obj["beats"]?.jsonArray
        if (beats == null || beats.isEmpty()) errors += "beats must be a non-empty array"
        else beats.forEachIndexed { i, e ->
            if (e.jsonPrimitive.content.isBlank()) errors += "beats[$i] must not be blank"
        }
    }
}
