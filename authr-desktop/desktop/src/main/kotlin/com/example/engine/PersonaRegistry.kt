package com.example.engine

import com.example.data.model.Persona
import com.example.data.model.defaultPersonas
import com.example.plugins.PluginSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The single persona source (2026-11-12 audit fix, findings #1/#2/#6).
 *
 * Before this registry existed, persona definitions lived in FIVE places that
 * drifted apart: `defaultPersonas` (11 cards), the hardcoded quick-draft
 * picker descriptions in StoryScreen, a display-name→id `when` map in
 * StudioViewModel, the plugin persona JSONs in ShippedPlugins (22 personas
 * with a different id scheme that NOTHING consumed), and a third hardcoded
 * prompt block in StudioViewModel. Every prompt site now resolves through
 * here, and the plugin personas the plan §8 promised (engine loads them from
 * the container's plugin directory) finally reach generation.
 *
 * Precedence on id collision: built-ins win over plugin files, so a plugin
 * drop-in can never silently replace a shipped card's content.
 *
 * This object is pure (File/JSON only — no android imports) so it stays
 * JVM-testable per docs/testing.md. The Context-aware surface lives in
 * `com.example.utils.PersonaResolver`.
 */
object PersonaRegistry {

    const val STANDARD_ID = "standard"

    /** The autosave sentinel the old "Author Persona Integration" picker stored. */
    const val LEGACY_AUTHOR_SENTINEL = "None (Weaver Agent Default)"

    private val json = Json { ignoreUnknownKeys = true }

    fun storyPersonas(): List<Persona> = defaultPersonas.filter { it.kind == Persona.KIND_STORY }
    fun authorPersonas(): List<Persona> = defaultPersonas.filter { it.kind == Persona.KIND_AUTHOR }
    fun builtIns(): List<Persona> = defaultPersonas

    fun personasDir(containerRoot: File): File = File(containerRoot, "plugins/personas")

    /**
     * Parses one plugin persona JSON (validated against the plan §8 contract).
     * Returns null for invalid files — invalid plugins are never loaded.
     */
    fun parsePluginPersona(text: String): Persona? {
        if (!PluginSchema.validate(text).valid) return null
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return null
            val name = obj["name"]?.jsonPrimitive?.content ?: return null
            val version = obj["version"]?.jsonPrimitive?.content ?: "1.0.0"
            val directive = obj["prompt_directive"]?.jsonPrimitive?.content ?: ""
            val sentenceLength = obj["sentence_length"]?.jsonPrimitive?.content ?: "mixed"
            val sensoryFocus = obj["sensory_focus"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()
            val pacingCurve = obj["pacing_curve"]?.jsonPrimitive?.content ?: ""
            val banned = obj["banned_patterns"]?.jsonArray
                ?.map { it.jsonPrimitive.content }?.filter { it.isNotBlank() } ?: emptyList()
            Persona(
                id = id,
                name = name,
                mode = "Plugin persona v$version",
                pacing = pacingCurve.ifBlank { "Plugin-defined" },
                focus = directive.ifBlank { "Plugin-defined directive" },
                aesthetic = if (sensoryFocus.isEmpty()) "Plugin-defined" else "Sensory focus: ${sensoryFocus.joinToString(", ")}",
                voice = "Sentence length: $sentenceLength",
                kind = Persona.KIND_STORY,
                adult = false,
                bannedPatterns = banned
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Loads and validates every persona plugin file in [personasDir]. */
    fun loadPluginPersonas(personasDir: File): List<Persona> {
        if (!personasDir.isDirectory) return emptyList()
        return personasDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { f ->
                runCatching { parsePluginPersona(f.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    /**
     * Built-ins win id collisions AND name collisions; plugins extend the
     * catalogue. Name dedupe matters because the shipped plugin library uses
     * a different id scheme (the_weaver) for personas whose content the
     * built-in cards (weaver) already carry — without it the pickers would
     * list "The Weaver" twice with drifting descriptions.
     */
    fun merge(builtIns: List<Persona>, plugins: List<Persona>): List<Persona> {
        val ids = builtIns.map { it.id }.toSet()
        val names = builtIns.map { it.name }.toSet()
        return builtIns + plugins.filter { it.id !in ids && it.name !in names }
    }

    /**
     * Resolves [personaId] to a persona: built-ins first, then container
     * plugin personas, falling back to Standard. An adult persona resolves to
     * Standard while [allowAdult] is false — the generation-side half of the
     * 18+ gate (the UI half hides the presets from every picker).
     */
    fun resolve(
        personaId: String,
        plugins: List<Persona> = emptyList(),
        allowAdult: Boolean = true
    ): Persona {
        val merged = merge(builtIns(), plugins)
        val found = merged.firstOrNull { it.id == personaId } ?: merged.first()
        return if (found.adult && !allowAdult) merged.first() else found
    }

    /**
     * The canonical persona card block. Every prompt site renders the persona
     * through this function — the three (five, counting the definition
     * drift) duplicated blocks the 2026-11-12 audit flagged are gone.
     */
    fun cardBody(p: Persona): String = buildString {
        appendLine("Mode: ${p.mode}")
        appendLine("Pacing: ${p.pacing}")
        appendLine("Focus: ${p.focus}")
        appendLine("Aesthetic: ${p.aesthetic}")
        appendLine("Voice: ${p.voice}")
        if (p.bannedPatterns.isNotEmpty()) {
            append("Banned patterns (never use): ${p.bannedPatterns.joinToString("; ")}")
        }
    }.trimEnd()

    /** One-line identity framing used where the prompt addresses the model as the persona. */
    fun authorFraming(p: Persona): String =
        "You are ${p.name} (${p.mode}) — the author persona writing this book."

    // ---- legacy value migration (read-only compat; never written back) ----

    private val legacyStoryNameToIdMap = mapOf(
        "Standard" to "standard",
        "Weaver" to "weaver",
        "NightKing" to "nightking",
        "Velvet" to "velvet",
        "Synapse" to "synapse",
        "Mastram" to "mastram",
        "Hitchcock" to "hitchcock",
        "Poe" to "poe",
        "BlackMirror" to "blackmirror",
        "Lovecraft" to "lovecraft",
        "MeetCute" to "meetcute"
    )

    /** Maps a legacy story-studio display name ("NightKing") to a persona id. */
    fun legacyStoryNameToId(name: String): String? = legacyStoryNameToIdMap[name]

    /**
     * Migrates a stored "Author Persona Integration" value to a persona id.
     * Handles the three legacy shapes the old picker produced — the
     * "None (Weaver Agent Default)" sentinel (which fell back to the story
     * persona selection), the author-role display names (which matched no
     * card and silently degraded to Standard), and already-id values — plus
     * passes through anything unknown (container plugin ids) unchanged.
     * Pure string mapping: safe for JVM tests, never persisted back.
     */
    fun legacyStoredAuthorPersona(stored: String, storyPersonaName: String): String {
        if (stored.isBlank()) return STANDARD_ID
        if (stored == LEGACY_AUTHOR_SENTINEL) return legacyStoryNameToId(storyPersonaName) ?: STANDARD_ID
        authorPersonas().firstOrNull { it.name == stored }?.let { return it.id }
        legacyStoryNameToId(stored)?.let { return it }
        return stored
    }

    // ---- adult content gating ----

    /**
     * A genre name that implies explicit adult content. Used by the pickers
     * (hidden until the 18+ acknowledgment) — "Erotic/Erotica" matches the
     * erotica families; the two explicit-structure subgenres are named
     * exactly.
     */
    fun isAdultGenre(genre: String): Boolean {
        val g = genre.trim()
        return g.contains("Erotic", ignoreCase = true) ||
            g.contains("Erotica", ignoreCase = true) ||
            g.equals("Harem / Reverse Harem", ignoreCase = true) ||
            g.equals("Menage / Polyamorous", ignoreCase = true)
    }
}
