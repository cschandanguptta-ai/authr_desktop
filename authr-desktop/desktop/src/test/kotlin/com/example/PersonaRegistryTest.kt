package com.example

import com.example.data.model.Persona
import com.example.engine.PersonaRegistry
import com.example.plugins.ShippedPlugins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards for the 2026-11-12 persona audit fix: ONE registry-backed persona
 * source, plugin personas actually consumed, canonical card, legacy value
 * migration, and the 18+ gate on resolution.
 */
class PersonaRegistryTest {

    @Test
    fun `the canonical card carries every persona trait plus banned patterns`() {
        val synapse = PersonaRegistry.builtIns().first { it.id == "synapse" }
        val card = PersonaRegistry.cardBody(synapse)
        listOf("Mode:", "Pacing:", "Focus:", "Aesthetic:", "Voice:").forEach { field ->
            assertTrue(card.contains(field), "card must carry $field")
        }
        assertTrue(card.contains("Banned patterns (never use): touch (as emotion)"),
            "banned patterns ride the card so every prompt site inherits the persona's lexicon")
        // A persona without banned patterns renders no banned line.
        assertFalse(PersonaRegistry.cardBody(PersonaRegistry.builtIns().first { it.id == "standard" })
            .contains("Banned patterns"))
    }

    @Test
    fun `resolve falls back to standard for an unknown id and never throws`() {
        assertEquals("standard", PersonaRegistry.resolve("progression_fantasy").id)
        assertEquals("standard", PersonaRegistry.resolve("").id)
    }

    @Test
    fun `an adult persona resolves to standard while the 18+ gate is closed`() {
        val nightking = PersonaRegistry.builtIns().first { it.id == "nightking" }
        assertTrue(nightking.adult, "NightKing ships flagged adult")
        assertEquals("standard", PersonaRegistry.resolve("nightking", allowAdult = false).id)
        assertEquals("nightking", PersonaRegistry.resolve("nightking", allowAdult = true).id)
    }

    @Test
    fun `exactly the five v2-3 erotica personas are flagged adult`() {
        val adult = PersonaRegistry.builtIns().filter { it.adult }.map { it.id }.toSet()
        assertEquals(setOf("weaver", "nightking", "velvet", "synapse", "mastram"), adult)
    }

    @Test
    fun `shipped plugin personas parse into registry personas and built-ins win collisions`() {
        val plugin = PersonaRegistry.parsePluginPersona(ShippedPlugins.PERSONAS.first())
        assertTrue(plugin != null, "the shipped the_weaver plugin persona parses")
        assertEquals("the_weaver", plugin!!.id)

        // The shipped the_weaver plugin repeats the built-in weaver card under
        // a different id scheme — name-collision dedupe keeps the pickers
        // single-sourced.
        val merged = PersonaRegistry.merge(PersonaRegistry.builtIns(), listOf(plugin))
        assertTrue(merged.none { it.id == "the_weaver" },
            "a plugin file can never add a second 'The Weaver' beside the shipped card")

        // A genuinely NEW plugin persona (distinct id AND name) extends the list.
        val extension = Persona(
            id = "author_kate", name = "Author Kate", mode = "", pacing = "", focus = "", aesthetic = "", voice = ""
        )
        val extended = PersonaRegistry.merge(PersonaRegistry.builtIns(), listOf(extension))
        assertTrue(extended.any { it.id == "author_kate" })
    }

    @Test
    fun `an invalid plugin persona file is never loaded`() {
        assertNull(PersonaRegistry.parsePluginPersona("{ not json "))
        assertNull(PersonaRegistry.parsePluginPersona("{\"id\":\"x\",\"kind\":\"structure\"}"))
    }

    @Test
    fun `legacy story-studio display names map to registry ids`() {
        assertEquals("nightking", PersonaRegistry.legacyStoryNameToId("NightKing"))
        assertEquals("weaver", PersonaRegistry.legacyStoryNameToId("Weaver"))
        assertEquals(null, PersonaRegistry.legacyStoryNameToId("Not A Persona"))
    }

    @Test
    fun `stored author-persona values migrate to ids without losing the old semantics`() {
        // The sentinel fell back to the story persona selection.
        assertEquals("nightking",
            PersonaRegistry.legacyStoredAuthorPersona("None (Weaver Agent Default)", "NightKing"))
        assertEquals("standard",
            PersonaRegistry.legacyStoredAuthorPersona("None (Weaver Agent Default)", "Unknown Name"))
        // Author-role display names used to resolve to NOTHING (silent Standard);
        // they now map to their real persona ids.
        assertEquals("classical_epic_author",
            PersonaRegistry.legacyStoredAuthorPersona("Classical Epic Author", "Weaver"))
        // Ids (old or plugin) pass through untouched.
        assertEquals("progression_fantasy",
            PersonaRegistry.legacyStoredAuthorPersona("progression_fantasy", "Weaver"))
        assertEquals("standard", PersonaRegistry.legacyStoredAuthorPersona("", "Weaver"))
    }

    @Test
    fun `the ten author roles are real registry personas`() {
        assertEquals(10, PersonaRegistry.authorPersonas().size)
        assertTrue(PersonaRegistry.authorPersonas().all { it.kind == Persona.KIND_AUTHOR })
        assertTrue(PersonaRegistry.authorPersonas().none { it.adult })
    }

    @Test
    fun `adult genres are classified for the picker gate`() {
        assertTrue(PersonaRegistry.isAdultGenre("Contemporary Erotica"))
        assertTrue(PersonaRegistry.isAdultGenre("BDSM Erotica"))
        assertTrue(PersonaRegistry.isAdultGenre("Harem / Reverse Harem"))
        assertFalse(PersonaRegistry.isAdultGenre("Locked-Room Thriller"))
        assertFalse(PersonaRegistry.isAdultGenre("Cozy Mystery"))
    }
}
