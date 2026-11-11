package com.example.container

import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.WorkbenchStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The .authr project container (v3.0, plan §10 / Table 29).
 *
 * A .authr file is a zip archive — one file, whole project, portable across
 * devices and survives application updates. Layout (schema v2):
 *
 *   /manifest.json        container + app versions, format, checksums, flags
 *   /manuscript/          confirmed chapters + archived L4 proposals + draft history
 *   /bibles/              character bible, world bible, relationships, attachment panel
 *   /lorebook/            key definitions injected into drafting prompts
 *   /ledger/              chekhov.json (guns), questions.json, timeline.json
 *   /structure/           outline.json (L2), beats.json (L3), arcs.json (micro-arcs)
 *   /audio/               pronunciation rules (IPA), voice packs, segments, masters
 *   /plugins/             structures/ + personas/ JSON plugin files (user-extensible)
 *   /exports/             epub/, print/, serial/, audio/ — regenerable outputs
 *   /metadata/            book.json (full project record) + KDP workbench data
 *
 * v3.0 P5 (plan §11.2, additive like the P4 pronunciation rules):
 * metadata/kdp_workbench.json upgrades from the v2.3 StringEnvelope stub to
 * the structured KdpWorkbench when the snapshot carries one. The reader
 * accepts BOTH forms (openers migrate forward, never downgrade); the
 * workbench is the authoritative retail-metadata source (OQ-9).
 *
 * Guarantees required by the plan (§10, risk register):
 *   - openers migrate forward on load, never silently downgrade;
 *   - a backup of the original container is always written before migrating;
 *   - saves are atomic (temp file + rename) so a crash mid-save can never
 *     corrupt a project.
 *
 * Losslessness contract (P0 exit gate): every field of the project record is
 * carried verbatim in /metadata/book.json; the per-directory files are the
 * human- and tool-readable projections of the same data.
 */
object AuthrContainer {

    const val SCHEMA_VERSION = 2
    const val APP_VERSION = "3.0.0-alpha"
    const val MANIFEST_ENTRY = "authr/manifest.json"
    const val BOOK_ENTRY = "authr/metadata/book.json"

    // encodeDefaults = true is REQUIRED for the interchange contract: the
    // manifest must always carry schemaVersion/appVersion explicitly so
    // openers can detect the container generation without guessing.
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------------------------------------------------------------------
    // Manifest
    // ---------------------------------------------------------------------

    @Serializable
    data class Manifest(
        val schemaVersion: Int = SCHEMA_VERSION,
        val appVersion: String = APP_VERSION,
        val format: String = "Novel",
        val createdAt: Long,
        val exportedAt: Long,
        val generator: String = "AuthrOS-$APP_VERSION",
        /** sha256 of every data payload, keyed by container-relative path. */
        val checksums: Map<String, String> = emptyMap(),
        /** Feature flags the writing app may toggle (P1+). */
        val featureFlags: Map<String, Boolean> = emptyMap(),
        /** Human-readable note when this container was migrated forward. */
        val migrationNote: String? = null
    )

    /** Result of [import]. */
    data class ImportResult(
        val manifest: Manifest,
        val snapshot: ProjectSnapshot,
        /** Set when the container was older than schema v2 and migrated forward. */
        val migratedFrom: Int?,
        /** Path of the pre-migration backup copy (always written when migrating). */
        val backupPath: String?,
        /** Checksum audit results (reported, never silent). */
        val checksumWarnings: List<String>
    )

    // ---------------------------------------------------------------------
    // Snapshot: the lossless data contract between the app DB and the container
    // ---------------------------------------------------------------------

    data class ProjectSnapshot(
        val book: com.example.data.model.Book,
        val chapters: List<com.example.data.model.Chapter>,
        val lorebook: List<com.example.data.model.Lorebook>,
        val arcs: List<com.example.data.model.SerialArc> = emptyList(),
        val guns: List<com.example.data.model.ChekhovGun> = emptyList(),
        val questions: List<com.example.data.model.QuestionEntry> = emptyList(),
        val plugins: List<com.example.data.model.PluginRecord> = emptyList(),
        /** v3.0 P5: the structured metadata workbench; null = legacy stub. */
        val publishing: KdpWorkbench? = null
    )

    // ---------------------------------------------------------------------
    // Export (project snapshot -> .authr file)
    // ---------------------------------------------------------------------

    fun export(snapshot: ProjectSnapshot, target: File) {
        val staging = File.createTempFile(
            "authr_staging_${System.currentTimeMillis()}", ".tmp", target.parentFile ?: File(".")
        )
        staging.delete(); staging.mkdirs()
        try {
            writeLayout(staging, snapshot)

            val payloads = staging.walkTopDown().filter { it.isFile }.toList()
            val manifest = Manifest(
                format = snapshot.book.format,
                createdAt = snapshot.book.createdAt,
                exportedAt = System.currentTimeMillis(),
                checksums = payloads.associate {
                    it.relativeTo(staging).invariantSeparatorsPath to sha256(it)
                },
                featureFlags = mapOf(
                    "writer_confirmed_gates" to true,
                    "pocket_tts_local" to true,
                    "serialization_format" to snapshot.book.isSerialized
                )
            )
            File(staging, "manifest.json").writeText(json.encodeToString(Manifest.serializer(), manifest))

            // Atomic save: zip to a .part sibling, then rename into place.
            val parent = target.parentFile ?: File(".")
            val part = File(parent, target.name + ".part")
            ZipOutputStream(part.outputStream().buffered()).use { zos ->
                staging.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(staging).path }.forEach { f ->
                    zos.putNextEntry(ZipEntry("authr/" + f.relativeTo(staging).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            val replaced = if (target.exists()) {
                val r = File(parent, target.name + ".replaced")
                target.copyTo(r, overwrite = true)
                r
            } else null
            if (!part.renameTo(target)) {
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    replaced?.renameTo(target) // restore previous container
                    throw java.io.IOException("Atomic rename failed for ${target.absolutePath}")
                }
            }
            replaced?.delete()
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Materializes the schema-v2 layout for [snapshot] into [root]. */
    private fun writeLayout(root: File, snapshot: ProjectSnapshot) {
        val b = snapshot.book

        // 1. Full project record — the lossless source of truth.
        dir(root, "metadata").also { meta ->
            File(meta, "book.json").writeText(json.encodeToString(BookPayload.serializer(), b.toPayload()))
            // v3.0 P5: the structured workbench wins when present; otherwise
            // the v2.3 StringEnvelope stub is written unchanged.
            val wb = snapshot.publishing
            if (wb != null) {
                WorkbenchStore.write(root, wb)
            } else {
                File(meta, "kdp_workbench.json").writeText(stringPayload(b.marketingMaterials))
            }
            File(meta, "cover_prompt.json").writeText(stringPayload(b.coverPrompt))
        }

        // 2. Manuscript: per-chapter JSON payloads + the human-readable canon.
        dir(root, "manuscript").also { manuscript ->
            snapshot.chapters.forEach { ch ->
                val file = File(manuscript, "chapter_%03d.json".format(ch.chapterNumber))
                file.writeText(json.encodeToString(ChapterPayload.serializer(), ch.toPayload()))
            }
            File(manuscript, "manuscript.md").writeText(buildString {
                appendLine("# ${b.title}")
                appendLine()
                snapshot.chapters.filter { it.writerConfirmed && it.draftStatus == "CONFIRMED" }
                    .sortedBy { it.chapterNumber }
                    .forEach { ch ->
                        appendLine("## Chapter ${ch.chapterNumber} — ${ch.title}")
                        appendLine()
                        appendLine(ch.content)
                        appendLine()
                    }
            })
        }

        // 3. Bibles (L1 records).
        dir(root, "bibles").also {
            File(it, "story_bible.json").writeText(stringPayload(b.storyBible))
            File(it, "character_bible.json").writeText(stringPayload(b.characterBible))
            File(it, "world_bible.json").writeText(stringPayload(b.worldBible))
            File(it, "relationships.json").writeText(stringPayload(b.plotPointsHistory))
        }

        // 4. Lorebook.
        dir(root, "lorebook").also {
            File(it, "lorebook.json").writeText(
                json.encodeToString(LorebookList.serializer(), LorebookList(snapshot.lorebook.map { it.toPayload() }))
            )
        }

        // 5. Ledgers (L6 continuity accounting).
        dir(root, "ledger").also {
            File(it, "chekhov.json").writeText(
                json.encodeToString(GunList.serializer(), GunList(snapshot.guns.map { it.toPayload() }))
            )
            File(it, "questions.json").writeText(
                json.encodeToString(QuestionList.serializer(), QuestionList(snapshot.questions.map { it.toPayload() }))
            )
            File(it, "timeline.json").writeText(stringPayload(b.continuityWarnings))
        }

        // 6. Structure (L2 outline, L3 beats, serial micro-arcs).
        dir(root, "structure").also {
            File(it, "outline.json").writeText(stringPayload(b.outline))
            File(it, "beats.json").writeText(stringPayload(b.plotPointsHistory))
            File(it, "arcs.json").writeText(
                json.encodeToString(ArcList.serializer(), ArcList(snapshot.arcs.map { it.toPayload() }))
            )
        }

        // 7. Audio (voice packs / segments / masters are created empty until P4 work lands).
        dir(root, "audio").also {
            File(it, "pronunciation_rules.json").writeText(
                json.encodeToString(
                    PronunciationRules.serializer(),
                    PronunciationRules(projectTitle = b.title, rules = emptyList())
                )
            )
            File(it, "voice_packs").mkdirs()
            File(it, "segments").mkdirs()
            File(it, "masters").mkdirs()
        }

        // 8. Plugins (scaffolded from the shipped library by PluginRegistry).
        dir(root, "plugins").also {
            dir(it, "structures")
            dir(it, "personas")
        }

        // 9. Exports (regenerable outputs, stamped with generator version).
        dir(root, "exports").also { ex ->
            listOf("epub", "print", "serial", "audio").forEach { dir(ex, it) }
            File(ex, ".generator_version").writeText(APP_VERSION)
        }
    }

    // ---------------------------------------------------------------------
    // Import (.authr file -> project snapshot), forward migration + backup
    // ---------------------------------------------------------------------

    fun import(file: File): ImportResult {
        require(file.exists() && file.length() > 0) { "Container missing or empty: ${file.absolutePath}" }
        val extractDir = File.createTempFile("authr_import_${System.currentTimeMillis()}", ".tmp", file.parentFile)
        extractDir.delete(); extractDir.mkdirs()
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                require(entries.any { it.name == MANIFEST_ENTRY }) {
                    "Not a .authr container (manifest.json missing): ${file.name}"
                }
                entries.forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val rel = entry.name.removePrefix("authr/")
                    if (rel.startsWith("/") || rel.contains("..")) {
                        throw SecurityException("Container entry escapes root: ${entry.name}")
                    }
                    val out = File(extractDir, rel)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }

            val manifest = json.decodeFromString(
                Manifest.serializer(), File(extractDir, "manifest.json").readText()
            )

            var effective = manifest
            var migratedFrom: Int? = null
            var backupPath: String? = null

            if (manifest.schemaVersion < SCHEMA_VERSION) {
                // Never silently downgrade; always backup before migrating.
                val backup = File(
                    file.parentFile,
                    file.nameWithoutExtension + ".backup-${System.currentTimeMillis()}.authr"
                )
                file.copyTo(backup, overwrite = true)
                backupPath = backup.absolutePath
                effective = migrateForward(extractDir, manifest)
                migratedFrom = manifest.schemaVersion
                File(extractDir, "manifest.json").writeText(json.encodeToString(Manifest.serializer(), effective))
            }

            // Checksum audit: report every mismatch, never silently ignore.
            val warnings = mutableListOf<String>()
            effective.checksums.forEach { (path, expected) ->
                val f = File(extractDir, path)
                when {
                    !f.isFile -> warnings += "Missing payload declared in manifest: $path"
                    sha256(f) != expected -> warnings += "Checksum mismatch: $path"
                }
            }

            return ImportResult(effective, readSnapshot(extractDir, effective), migratedFrom, backupPath, warnings)
        } finally {
            extractDir.deleteRecursively()
        }
    }

    /**
     * v1 -> v2 migration: schema v1 prototype containers carried manuscript +
     * bibles but lacked ledger/, structure/arcs.json, plugins/ and exports/.
     * Additive only — no payload is dropped or rewritten.
     */
    private fun migrateForward(root: File, manifest: Manifest): Manifest {
        listOf("ledger", "structure", "plugins", "exports").forEach { dir(root, it) }
        if (!File(root, "ledger/chekhov.json").exists()) File(root, "ledger/chekhov.json").writeText("{\"guns\":[]}")
        if (!File(root, "ledger/questions.json").exists()) File(root, "ledger/questions.json").writeText("{\"questions\":[]}")
        if (!File(root, "structure/arcs.json").exists()) File(root, "structure/arcs.json").writeText("{\"arcs\":[]}")
        return manifest.copy(
            schemaVersion = SCHEMA_VERSION,
            appVersion = APP_VERSION,
            migrationNote = "Migrated from schema v${manifest.schemaVersion} to v$SCHEMA_VERSION on import; original container backed up."
        )
    }

    private fun readSnapshot(root: File, manifest: Manifest): ProjectSnapshot {
        val bookFile = File(root, "metadata/book.json")
        val book: com.example.data.model.Book = if (bookFile.exists()) {
            json.decodeFromString(BookPayload.serializer(), bookFile.readText()).toEntity()
        } else {
            // v1 prototype containers: fall back to fragments.
            com.example.data.model.Book(
                id = 0,
                title = readManuscriptTitle(root) ?: "Untitled",
                genre = "",
                synopsis = "",
                outline = "",
                format = manifest.format,
                isSerialized = manifest.featureFlags["serialization_format"] == true,
                createdAt = manifest.createdAt
            )
        }

        val chapters = File(root, "manuscript")
            .listFiles { f -> f.name.matches(Regex("chapter_\\d+\\.json")) }
            ?.sortedBy { it.name }
            ?.map { f -> json.decodeFromString(ChapterPayload.serializer(), f.readText()).toEntity() }
            ?: emptyList()

        val lorebook = File(root, "lorebook/lorebook.json").takeIf { it.exists() }
            ?.let { json.decodeFromString(LorebookList.serializer(), it.readText()).lorebook.map { p -> p.toEntity() } }
            ?: emptyList()

        val arcs = File(root, "structure/arcs.json").takeIf { it.exists() }
            ?.let { json.decodeFromString(ArcList.serializer(), it.readText()).arcs.map { p -> p.toEntity() } }
            ?: emptyList()

        val guns = File(root, "ledger/chekhov.json").takeIf { it.exists() }
            ?.let { json.decodeFromString(GunList.serializer(), it.readText()).guns.map { p -> p.toEntity() } }
            ?: emptyList()

        val questions = File(root, "ledger/questions.json").takeIf { it.exists() }
            ?.let { json.decodeFromString(QuestionList.serializer(), it.readText()).questions.map { p -> p.toEntity() } }
            ?: emptyList()

        return ProjectSnapshot(
            book = book,
            chapters = chapters,
            lorebook = lorebook,
            arcs = arcs,
            guns = guns,
            questions = questions,
            publishing = WorkbenchStore.read(root)
        )
    }

    // ---------------------------------------------------------------------
    // Payload DTOs + entity mapping (lossless)
    // ---------------------------------------------------------------------

    @Serializable
    data class BookPayload(
        val id: Int, val title: String, val genre: String, val synopsis: String,
        val outline: String, val personaId: String, val format: String,
        val targetWordCount: Int, val narrativeStructure: String, val chapterLength: String,
        val isSerialized: Boolean, val targetChapters: Int, val releaseSchedule: String,
        val freeTierChapters: Int, val wordMin: Int, val wordMax: Int,
        val bufferTarget: Int, val exportProfile: String,
        val compiledManuscript: String, val currentPhase: String,
        val storyBible: String, val characterBible: String, val worldBible: String,
        val plotPointsHistory: String, val memoryState: String, val agentLogs: String,
        val marketingMaterials: String, val coverPrompt: String,
        val completionPercentage: Int, val wordsPerMinute: Int,
        val readabilityScore: String, val emotionalIntensity: String,
        val pacingHeatmap: String, val continuityWarnings: String,
        val createdAt: Long, val lastModifiedAt: Long
    )

    @Serializable
    data class ChapterPayload(
        val chapterNumber: Int,
        val title: String,
        val content: String,
        val summary: String,
        val status: String,
        val writerConfirmed: Boolean,
        val draftStatus: String,
        val arcId: Int?,
        val endingType: String?,
        val beatCoverage: String?,
        val balanceShares: String?,
        val worksheet: String?,
        val qcScores: String?,
        val qcTotal: Int?,
        val hookCheck: String?,
        val ctaNote: String?,
        val pollJson: String?,
        val createdAt: Long
    )

    @Serializable
    data class LorebookPayload(val key: String, val value: String)

    @Serializable
    data class ArcPayload(
        val arcIndex: Int, val conflict: String, val questionRef: Int?,
        val phaseBounds: String, val answeredQuestionRef: Int?,
        val contribution: String, val seeds: String
    )

    @Serializable
    data class GunPayload(
        val name: String, val description: String, val status: String,
        val introducedChapter: Int, val escalationChapters: String, val resolvedChapter: Int?,
        val quote: String, val severity: String, val waiveNote: String
    )

    @Serializable
    data class QuestionPayload(
        val level: String, val text: String, val openedChapter: Int,
        val dueChapter: Int?, val answeredChapter: Int?, val answeredByReveal: Int?
    )

    @Serializable
    data class PronunciationRules(val projectTitle: String, val rules: List<Rule>) {
        @Serializable
        data class Rule(val term: String, val ipa: String, val respelling: String, val approvedByWriter: Boolean = true)
    }

    @Serializable data class LorebookList(val lorebook: List<LorebookPayload>)
    @Serializable data class ArcList(val arcs: List<ArcPayload>)
    @Serializable data class GunList(val guns: List<GunPayload>)
    @Serializable data class QuestionList(val questions: List<QuestionPayload>)

    private fun com.example.data.model.Book.toPayload() = BookPayload(
        id = id, title = title, genre = genre, synopsis = synopsis, outline = outline,
        personaId = personaId, format = format, targetWordCount = targetWordCount,
        narrativeStructure = narrativeStructure, chapterLength = chapterLength,
        isSerialized = isSerialized, targetChapters = targetChapters,
        releaseSchedule = releaseSchedule, freeTierChapters = freeTierChapters,
        wordMin = wordMin, wordMax = wordMax, bufferTarget = bufferTarget,
        exportProfile = exportProfile, compiledManuscript = compiledManuscript,
        currentPhase = currentPhase, storyBible = storyBible,
        characterBible = characterBible, worldBible = worldBible,
        plotPointsHistory = plotPointsHistory, memoryState = memoryState,
        agentLogs = agentLogs, marketingMaterials = marketingMaterials,
        coverPrompt = coverPrompt, completionPercentage = completionPercentage,
        wordsPerMinute = wordsPerMinute, readabilityScore = readabilityScore,
        emotionalIntensity = emotionalIntensity, pacingHeatmap = pacingHeatmap,
        continuityWarnings = continuityWarnings, createdAt = createdAt,
        lastModifiedAt = lastModifiedAt
    )

    private fun BookPayload.toEntity() = com.example.data.model.Book(
        id = 0, // Room re-assigns on import; container identity is title + createdAt
        title = title, genre = genre, synopsis = synopsis, outline = outline,
        personaId = personaId, format = format, targetWordCount = targetWordCount,
        narrativeStructure = narrativeStructure, chapterLength = chapterLength,
        isSerialized = isSerialized, targetChapters = targetChapters,
        releaseSchedule = releaseSchedule, freeTierChapters = freeTierChapters,
        wordMin = wordMin, wordMax = wordMax, bufferTarget = bufferTarget,
        exportProfile = exportProfile, compiledManuscript = compiledManuscript,
        currentPhase = currentPhase, storyBible = storyBible,
        characterBible = characterBible, worldBible = worldBible,
        plotPointsHistory = plotPointsHistory, memoryState = memoryState,
        agentLogs = agentLogs, marketingMaterials = marketingMaterials,
        coverPrompt = coverPrompt, completionPercentage = completionPercentage,
        wordsPerMinute = wordsPerMinute, readabilityScore = readabilityScore,
        emotionalIntensity = emotionalIntensity, pacingHeatmap = pacingHeatmap,
        continuityWarnings = continuityWarnings, createdAt = createdAt,
        lastModifiedAt = lastModifiedAt
    )

    private fun com.example.data.model.Chapter.toPayload() = ChapterPayload(
        chapterNumber = chapterNumber, title = title, content = content,
        summary = summary, status = status, writerConfirmed = writerConfirmed,
        draftStatus = draftStatus, arcId = arcId, endingType = endingType,
        beatCoverage = beatCoverage, balanceShares = balanceShares, worksheet = worksheet,
        qcScores = qcScores, qcTotal = qcTotal, hookCheck = hookCheck,
        ctaNote = ctaNote, pollJson = pollJson, createdAt = createdAt
    )

    private fun ChapterPayload.toEntity() = com.example.data.model.Chapter(
        bookId = 0, // re-linked to the imported book's id by the caller (AppRepository)
        chapterNumber = chapterNumber, title = title, content = content,
        summary = summary, status = status, writerConfirmed = writerConfirmed,
        draftStatus = draftStatus, arcId = arcId, endingType = endingType,
        beatCoverage = beatCoverage, balanceShares = balanceShares, worksheet = worksheet,
        qcScores = qcScores, qcTotal = qcTotal, hookCheck = hookCheck,
        ctaNote = ctaNote, pollJson = pollJson, createdAt = createdAt
    )

    private fun com.example.data.model.Lorebook.toPayload() = LorebookPayload(key, value)
    private fun LorebookPayload.toEntity() = com.example.data.model.Lorebook(bookId = 0, key = key, value = value)

    private fun com.example.data.model.SerialArc.toPayload() = ArcPayload(
        arcIndex, conflict, questionRef, phaseBounds, answeredQuestionRef, contribution, seeds
    )
    private fun ArcPayload.toEntity() = com.example.data.model.SerialArc(
        bookId = 0, arcIndex = arcIndex, conflict = conflict, questionRef = questionRef,
        phaseBounds = phaseBounds, answeredQuestionRef = answeredQuestionRef,
        contribution = contribution, seeds = seeds
    )

    private fun com.example.data.model.ChekhovGun.toPayload() = GunPayload(
        name, description, status, introducedChapter, escalationChapters, resolvedChapter, quote, severity, waiveNote
    )
    private fun GunPayload.toEntity() = com.example.data.model.ChekhovGun(
        bookId = 0, name = name, description = description, status = status,
        introducedChapter = introducedChapter, escalationChapters = escalationChapters,
        resolvedChapter = resolvedChapter, quote = quote, severity = severity, waiveNote = waiveNote
    )

    private fun com.example.data.model.QuestionEntry.toPayload() = QuestionPayload(
        level, text, openedChapter, dueChapter, answeredChapter, answeredByReveal
    )
    private fun QuestionPayload.toEntity() = com.example.data.model.QuestionEntry(
        bookId = 0, level = level, text = text, openedChapter = openedChapter,
        dueChapter = dueChapter, answeredChapter = answeredChapter, answeredByReveal = answeredByReveal
    )

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    @Serializable private data class StringEnvelope(val data: String)

    private fun stringPayload(content: String): String =
        json.encodeToString(StringEnvelope.serializer(), StringEnvelope(content))

    private fun readStringPayload(f: File?): String =
        f?.takeIf { it.exists() }
            ?.let { json.decodeFromString(StringEnvelope.serializer(), it.readText()).data }
            ?: ""

    private fun readManuscriptTitle(root: File): String? =
        File(root, "manuscript/manuscript.md").takeIf { it.exists() }
            ?.useLines { lines -> lines.firstOrNull { it.startsWith("# ") } }
            ?.removePrefix("# ")?.trim()

    private fun dir(parent: File, name: String): File =
        File(parent, name).apply { mkdirs() }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf); if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
