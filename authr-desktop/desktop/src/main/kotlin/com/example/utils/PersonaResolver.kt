package com.example.utils

import android.content.Context
import com.example.data.model.Persona
import com.example.engine.PersonaRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Context-aware persona resolution (the thin android-facing half of the
 * 2026-11-12 persona audit fix; the pure half is `engine/PersonaRegistry`).
 *
 * Responsibilities:
 *  - resolves the container's plugin-persona directory (scaffolded by
 *    PluginRegistry on app start, editable by the user — their edits win),
 *  - holds the 18+ acknowledgment flag (plain "studio_settings" prefs — a
 *    non-secret, non-secure-prefs flag),
 *  - exposes the gated persona/genre lists the pickers render.
 *
 * Every generation path (workers, L4, StudioViewModel) resolves personas
 * THROUGH this object so the adult gate cannot be bypassed by calling
 * PersonaRegistry.resolve directly with default arguments.
 */
object PersonaResolver {

    private val pluginCache = ConcurrentHashMap<String, List<Persona>>()

    /** The plugin root — same directory PluginRegistry.scaffoldDefaults writes (Main.kt). */
    fun containerRoot(context: Context): File = File(context.filesDir, "authr")

    private fun pluginPersonas(context: Context): List<Persona> =
        pluginCache.computeIfAbsent(containerRoot(context).absolutePath) {
            PersonaRegistry.loadPluginPersonas(PersonaRegistry.personasDir(containerRoot(context)))
        }

    /** Drops the plugin cache (after a scaffold or user edit to plugin files). */
    fun refreshPlugins(context: Context) {
        pluginCache.remove(containerRoot(context).absolutePath)
    }

    // ---- 18+ acknowledgment ----

    private const val ADULT_ACK_KEY = "adult_content_acknowledged_18plus"

    fun adultAcknowledged(context: Context): Boolean =
        context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
            .getBoolean(ADULT_ACK_KEY, false)

    fun setAdultContentAcknowledged(context: Context, acknowledged: Boolean) {
        context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(ADULT_ACK_KEY, acknowledged).apply()
    }

    // ---- resolution (gated) ----

    /** The generation-path resolver: workers, L4, StudioViewModel prompts. */
    fun resolve(context: Context, personaId: String): Persona =
        PersonaRegistry.resolve(personaId, pluginPersonas(context), allowAdult = adultAcknowledged(context))

    /** Resolves a legacy story-studio display name ("NightKing") to a persona. */
    fun resolveByName(context: Context, displayName: String): Persona {
        val id = PersonaRegistry.legacyStoryNameToId(displayName) ?: displayName
        return resolve(context, id)
    }

    /** Migrates a stored author-persona value to a persona id (see PersonaRegistry). */
    fun storedAuthorPersonaId(context: Context, stored: String, storyPersonaName: String): String {
        val id = PersonaRegistry.legacyStoredAuthorPersona(stored, storyPersonaName)
        // A stored adult selection with no acknowledgment downgrades to Standard.
        val resolved = PersonaRegistry.resolve(id, pluginPersonas(context), allowAdult = adultAcknowledged(context))
        return resolved.id
    }

    // ---- gated picker lists ----

    /** Story personas the quick-draft picker may show (plugin personas included). */
    fun availableStoryPersonas(context: Context): List<Persona> {
        val all = PersonaRegistry.merge(PersonaRegistry.storyPersonas(), pluginPersonas(context))
        return if (adultAcknowledged(context)) all else all.filter { !it.adult }
    }

    /** Author personas the main-path wizard may show. */
    fun availableAuthorPersonas(context: Context): List<Persona> {
        val all = PersonaRegistry.authorPersonas()
        return if (adultAcknowledged(context)) all else all.filter { !it.adult }
    }

    /** Filters adult genres out of a genre option collection until acknowledged. */
    fun availableGenres(context: Context, genres: Collection<String>): List<String> {
        val filtered = genres.filter { !PersonaRegistry.isAdultGenre(it) }
        return if (adultAcknowledged(context)) genres.toList() else filtered
    }
}
