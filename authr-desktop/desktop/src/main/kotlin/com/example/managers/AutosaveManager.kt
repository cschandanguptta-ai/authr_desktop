package com.example.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.autosaveDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_autosave_ds")

class AutosaveManager(private val context: Context) {

    suspend fun saveDraft(
        heroName: String,
        genres: String,
        plotArc: String,
        allowHybridPlotArcs: Boolean,
        authorPersona: String,
        duration: String,
        hook: String,
        extras: String,
        persona: String,
        wizardOutline: String,
        quickTitle: String,
        quickGenres: String,
        quickProtagonist: String,
        quickHook: String,
        quickFormat: String,
        quickWordTarget: Int,
        quickPersona: String,
        quickStructure: String,
        quickChapterLength: String,
        quickAutoExport: Boolean,
        quickPrevBookId: Int,
        quickExtras: String,
        quickIsSerialized: Boolean,
        quickTargetChapters: Int,
        quickReleaseSchedule: String,
        parts: List<String>,
        timeStr: String
    ) {
        context.autosaveDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("has_draft")] = true
            prefs[stringPreferencesKey("hero_name")] = heroName
            prefs[stringPreferencesKey("genres")] = genres
            prefs[stringPreferencesKey("plot_arc")] = plotArc
            prefs[booleanPreferencesKey("allow_hybrid_plot_arcs")] = allowHybridPlotArcs
            prefs[stringPreferencesKey("author_persona")] = authorPersona
            prefs[stringPreferencesKey("duration")] = duration
            prefs[stringPreferencesKey("hook")] = hook
            prefs[stringPreferencesKey("extras")] = extras
            prefs[stringPreferencesKey("persona")] = persona
            prefs[stringPreferencesKey("wizard_outline")] = wizardOutline

            prefs[stringPreferencesKey("quick_title")] = quickTitle
            prefs[stringPreferencesKey("quick_genres")] = quickGenres
            prefs[stringPreferencesKey("quick_protagonist")] = quickProtagonist
            prefs[stringPreferencesKey("quick_hook")] = quickHook
            prefs[stringPreferencesKey("quick_format")] = quickFormat
            prefs[intPreferencesKey("quick_word_target")] = quickWordTarget
            prefs[stringPreferencesKey("quick_persona")] = quickPersona
            prefs[stringPreferencesKey("quick_structure")] = quickStructure
            prefs[stringPreferencesKey("quick_chapter_length")] = quickChapterLength
            prefs[booleanPreferencesKey("quick_auto_export")] = quickAutoExport
            prefs[intPreferencesKey("quick_prev_book_id")] = quickPrevBookId

            prefs[intPreferencesKey("parts_count")] = parts.size
            for (i in parts.indices) {
                prefs[stringPreferencesKey("part_$i")] = parts[i]
            }

            prefs[stringPreferencesKey("last_save_time")] = timeStr
        }
    }

    suspend fun loadDraft(): AutosaveData? {
        val prefs = context.autosaveDataStore.data.first()
        val hasDraft = prefs[booleanPreferencesKey("has_draft")] ?: false
        if (!hasDraft) return null

        val partsCount = prefs[intPreferencesKey("parts_count")] ?: 5
        val parts = MutableList(partsCount) { i ->
            prefs[stringPreferencesKey("part_$i")] ?: ""
        }

        return AutosaveData(
            heroName = prefs[stringPreferencesKey("hero_name")] ?: "Arjun",
            genres = prefs[stringPreferencesKey("genres")] ?: "System Power-Up",
            plotArc = prefs[stringPreferencesKey("plot_arc")] ?: "Automatic (Recommended)",
            allowHybridPlotArcs = prefs[booleanPreferencesKey("allow_hybrid_plot_arcs")] ?: false,
            // Registry id default (audit #3): legacy sentinel values still load —
            // StudioViewModel's migration maps them on read.
            authorPersona = prefs[stringPreferencesKey("author_persona")] ?: com.example.engine.PersonaRegistry.STANDARD_ID,
            duration = prefs[stringPreferencesKey("duration")] ?: "30 min",
            hook = prefs[stringPreferencesKey("hook")] ?: "",
            extras = prefs[stringPreferencesKey("extras")] ?: "",
            persona = prefs[stringPreferencesKey("persona")] ?: "Standard",
            wizardOutline = prefs[stringPreferencesKey("wizard_outline")] ?: "",
            quickTitle = prefs[stringPreferencesKey("quick_title")] ?: "",
            quickGenres = prefs[stringPreferencesKey("quick_genres")] ?: "Fantasy",
            quickProtagonist = prefs[stringPreferencesKey("quick_protagonist")] ?: "",
            quickHook = prefs[stringPreferencesKey("quick_hook")] ?: "",
            quickFormat = prefs[stringPreferencesKey("quick_format")] ?: "Novel",
            quickWordTarget = prefs[intPreferencesKey("quick_word_target")] ?: 50000,
            quickPersona = prefs[stringPreferencesKey("quick_persona")] ?: "standard",
            quickStructure = prefs[stringPreferencesKey("quick_structure")] ?: "Standard 3-Act",
            quickChapterLength = prefs[stringPreferencesKey("quick_chapter_length")] ?: "Medium (~2000 words)",
            quickAutoExport = prefs[booleanPreferencesKey("quick_auto_export")] ?: true,
            quickPrevBookId = prefs[intPreferencesKey("quick_prev_book_id")] ?: -1,
            quickExtras = prefs[stringPreferencesKey("quick_extras")] ?: "",
            quickIsSerialized = prefs[booleanPreferencesKey("quick_is_serialized")] ?: false,
            quickTargetChapters = prefs[intPreferencesKey("quick_target_chapters")] ?: 100,
            quickReleaseSchedule = prefs[stringPreferencesKey("quick_release_schedule")] ?: "",
            parts = parts,
            lastSaveTime = prefs[stringPreferencesKey("last_save_time")]
        )
    }

    suspend fun clearDraft() {
        context.autosaveDataStore.edit { it.clear() }
    }
}

data class AutosaveData(
    val heroName: String,
    val genres: String,
    val plotArc: String,
    val allowHybridPlotArcs: Boolean,
    val authorPersona: String,
    val duration: String,
    val hook: String,
    val extras: String,
    val persona: String,
    val wizardOutline: String,
    val quickTitle: String,
    val quickGenres: String,
    val quickProtagonist: String,
    val quickHook: String,
    val quickFormat: String,
    val quickWordTarget: Int,
    val quickPersona: String,
    val quickStructure: String,
    val quickChapterLength: String,
    val quickAutoExport: Boolean,
    val quickPrevBookId: Int,
    val quickExtras: String,
    val quickIsSerialized: Boolean,
    val quickTargetChapters: Int,
    val quickReleaseSchedule: String,
    val parts: List<String>,
    val lastSaveTime: String?
)
