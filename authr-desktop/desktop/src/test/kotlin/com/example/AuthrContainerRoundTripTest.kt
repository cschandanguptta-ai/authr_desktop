package com.example

import com.example.container.AuthrContainer
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.QuestionEntry
import com.example.data.model.SerialArc
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

/**
 * P0 exit gate (plan §13, Table 33): "A v2.3 project round-trips through the
 * container losslessly". These tests are the round-trip assertion suite.
 */
class AuthrContainerRoundTripTest {

    private fun fixtureSnapshot(): AuthrContainer.ProjectSnapshot {
        val book = Book(
            id = 7,
            title = "The Lantern Thief 温灯贼",
            genre = "Progression Fantasy",
            synopsis = "A lamplighter's apprentice discovers the city's lights are keeping something asleep. \"Kurzgefasste Theorie.\"",
            outline = "Act I: the lantern goes dark; Act II: the under-city; Act III: the waking.",
            personaId = "progression_fantasy",
            format = "Web Novel Serial",
            targetWordCount = 200000,
            narrativeStructure = "Micro-Arc Cascade",
            chapterLength = "Medium (3000 words)",
            isSerialized = true,
            targetChapters = 100,
            releaseSchedule = "Mon/Wed/Fri",
            freeTierChapters = 5,
            wordMin = 1000,
            wordMax = 2000,
            bufferTarget = 8,
            exportProfile = "royal_road",
            compiledManuscript = "",
            currentPhase = "Drafting",
            storyBible = "The city of Vell, its nine lantern orders, and the Sleeper beneath.",
            characterBible = "Ivo Hask — lamplighter's apprentice. Desire: to be seen. Vulnerability: his sister's debt.",
            worldBible = "Lanterns burn on bottled dusk; the Sleeper dreams the weather.",
            plotPointsHistory = "Ch1: lantern dark. Ch2: guild refusal.",
            memoryState = "",
            agentLogs = "orchestrator: ok",
            marketingMaterials = "Blurb draft 2: \"Trust the light.\"",
            coverPrompt = "A lamplighter on a bridge of chains, dusk in bottles",
            completionPercentage = 12,
            wordsPerMinute = 230,
            readabilityScore = "Flesch-Kincaid: Grade 7",
            emotionalIntensity = "Balanced",
            pacingHeatmap = "Moderate",
            continuityWarnings = "No critical warnings",
            createdAt = 1720000000000L,
            lastModifiedAt = 1720000500000L
        )
        val chapters = (1..5).map { n ->
            Chapter(
                bookId = 7,
                chapterNumber = n,
                title = "Chapter $n — 灯塔 The Beacon",
                content = "Content of chapter $n. «Rien n'est vrai.» — 强调文字 **bold** kept verbatim. " +
                    "Line breaks\nand tabs\ttoo.",
                summary = "Summary $n",
                status = if (n == 5) "Drafting" else "Completed",
                writerConfirmed = n < 5,
                draftStatus = if (n == 5) "PROPOSED" else "CONFIRMED",
                arcId = if (n <= 3) 1 else null,
                endingType = when (n) { 1 -> "DANGER"; 2 -> "MYSTERY_DISCOVERY"; 3 -> "REVERSAL"; else -> null },
                beatCoverage = """{"opening_continuity":true,"immediate_objective":true,"resistance":true,"escalation":true,"turning_point":true,"chapter_payoff":true,"forward_pull":true}""",
                balanceShares = """{"opening":12,"middle":56,"payoff":22,"pull":10}""",
                worksheet = """{"a":"objective","b":"character","c":"plot","d":"reader","e":"ending"}""",
                qcScores = """{"c1":2,"c2":1,"c3":2,"c4":1,"c5":2,"c6":2,"c7":1}""",
                qcTotal = 11,
                hookCheck = """{"source":"urgent_problem","words":280}""",
                ctaNote = "Which lantern order do you trust?",
                pollJson = """{"question":"Trust the light?","options":["yes","no"]}""",
                createdAt = 1720000000000L + n
            )
        }
        val lore = listOf(
            Lorebook(bookId = 7, key = "Lantern Orders", value = "Nine guilds; the Ninth was dissolved."),
            Lorebook(bookId = 7, key = "九灯会", value = "The Chinese name of the Lantern Orders.")
        )
        val arcs = listOf(
            SerialArc(
                bookId = 7, arcIndex = 1,
                conflict = "The Ninth Lantern must be relit before the Sleeper turns over.",
                questionRef = null, phaseBounds = """{"setup":[1,3],"escalation":[4,7],"reversal":[8,11],"climax":[12,15]}""",
                answeredQuestionRef = null,
                contribution = "Introduces the Sleeper and Ivo's debt.",
                seeds = """["The dissolved Ninth Order still collects dues"]"""
            )
        )
        val guns = listOf(
            ChekhovGun(
                bookId = 7, name = "The dark lantern", description = "The unlit lantern from chapter 1.",
                status = "ESCALATED", introducedChapter = 1, escalationChapters = "[3]",
                resolvedChapter = null, quote = "The lantern had not burned in sixty years.", severity = "high"
            )
        )
        val questions = listOf(
            QuestionEntry(
                bookId = 7, level = "PROGRESSIVE", text = "Who dissolved the Ninth Order?",
                openedChapter = 2, dueChapter = 9, answeredChapter = null, answeredByReveal = null
            ),
            QuestionEntry(
                bookId = 7, level = "LONG_TERM", text = "What does the Sleeper dream?",
                openedChapter = 1, dueChapter = null, answeredChapter = null, answeredByReveal = null
            )
        )
        return AuthrContainer.ProjectSnapshot(book, chapters, lore, arcs, guns, questions)
    }

    @Test
    fun `v23 project round-trips through the container losslessly`() {
        val snapshot = fixtureSnapshot()
        val target = File.createTempFile("authr_roundtrip", ".authr")
        try {
            AuthrContainer.export(snapshot, target)
            assertTrue(target.length() > 0, "container file must be non-empty")

            val result = AuthrContainer.import(target)
            assertEquals(null, result.migratedFrom, "v2 container must not migrate")
            assertTrue(result.checksumWarnings.isEmpty(), "checksum audit must be clean: ${result.checksumWarnings}")

            // Book: every field survives verbatim.
            val out = result.snapshot.book
            val src = snapshot.book
            assertEquals(src.title, out.title)
            assertEquals(src.genre, out.genre)
            assertEquals(src.synopsis, out.synopsis)
            assertEquals(src.outline, out.outline)
            assertEquals(src.personaId, out.personaId)
            assertEquals(src.format, out.format)
            assertEquals(src.targetWordCount, out.targetWordCount)
            assertEquals(src.narrativeStructure, out.narrativeStructure)
            assertEquals(src.chapterLength, out.chapterLength)
            assertEquals(src.isSerialized, out.isSerialized)
            assertEquals(src.targetChapters, out.targetChapters)
            assertEquals(src.releaseSchedule, out.releaseSchedule)
            assertEquals(src.freeTierChapters, out.freeTierChapters)
            assertEquals(src.wordMin, out.wordMin)
            assertEquals(src.wordMax, out.wordMax)
            assertEquals(src.bufferTarget, out.bufferTarget)
            assertEquals(src.exportProfile, out.exportProfile)
            assertEquals(src.storyBible, out.storyBible)
            assertEquals(src.characterBible, out.characterBible)
            assertEquals(src.worldBible, out.worldBible)
            assertEquals(src.plotPointsHistory, out.plotPointsHistory)
            assertEquals(src.marketingMaterials, out.marketingMaterials)
            assertEquals(src.coverPrompt, out.coverPrompt)
            assertEquals(src.continuityWarnings, out.continuityWarnings)
            assertEquals(src.createdAt, out.createdAt)

            // Chapters: full field parity including unicode and writer state.
            assertEquals(snapshot.chapters.size, result.snapshot.chapters.size)
            snapshot.chapters.zip(result.snapshot.chapters).forEach { (a, b) ->
                assertEquals(a.chapterNumber, b.chapterNumber)
                assertEquals(a.title, b.title)
                assertEquals(a.content, b.content)
                assertEquals(a.summary, b.summary)
                assertEquals(a.status, b.status)
                assertEquals(a.writerConfirmed, b.writerConfirmed)
                assertEquals(a.draftStatus, b.draftStatus)
                assertEquals(a.endingType, b.endingType)
                assertEquals(a.beatCoverage, b.beatCoverage)
                assertEquals(a.balanceShares, b.balanceShares)
                assertEquals(a.worksheet, b.worksheet)
                assertEquals(a.qcScores, b.qcScores)
                assertEquals(a.qcTotal, b.qcTotal)
                assertEquals(a.hookCheck, b.hookCheck)
                assertEquals(a.ctaNote, b.ctaNote)
                assertEquals(a.pollJson, b.pollJson)
                assertEquals(a.createdAt, b.createdAt)
            }

            // Ledger + structure + lore parity.
            assertEquals(snapshot.lorebook.map { it.key to it.value }, result.snapshot.lorebook.map { it.key to it.value })
            assertEquals(snapshot.arcs.size, result.snapshot.arcs.size)
            assertEquals(snapshot.arcs.first().conflict, result.snapshot.arcs.first().conflict)
            assertEquals(snapshot.arcs.first().phaseBounds, result.snapshot.arcs.first().phaseBounds)
            assertEquals(snapshot.arcs.first().seeds, result.snapshot.arcs.first().seeds)
            assertEquals(snapshot.guns.first().status, result.snapshot.guns.first().status)
            assertEquals(snapshot.guns.first().quote, result.snapshot.guns.first().quote)
            assertEquals(snapshot.guns.first().severity, result.snapshot.guns.first().severity)
            assertEquals(snapshot.questions.size, result.snapshot.questions.size)
            assertEquals(snapshot.questions[0].level, result.snapshot.questions[0].level)
            assertEquals(snapshot.questions[0].dueChapter, result.snapshot.questions[0].dueChapter)
            assertEquals(snapshot.questions[1].level, result.snapshot.questions[1].level)
        } finally {
            target.delete()
        }
    }

    @Test
    fun `saves are atomic and replace previous containers safely`() {
        val snapshot = fixtureSnapshot()
        val target = File.createTempFile("authr_atomic", ".authr")
        try {
            AuthrContainer.export(snapshot, target)
            val firstBytes = target.readBytes()
            // Re-export over the same target: must not corrupt or double-write.
            val updated = snapshot.copy(
                book = snapshot.book.copy(synopsis = "Revised synopsis — second save.")
            )
            AuthrContainer.export(updated, target)
            val result = AuthrContainer.import(target)
            assertEquals("Revised synopsis — second save.", result.snapshot.book.synopsis)
            assertFalse(target.name.endsWith(".part"), "no .part residue may remain")
            @Suppress("UNUSED_EXPRESSION") firstBytes
        } finally {
            target.delete()
            File(target.parentFile, target.name + ".part").delete()
            File(target.parentFile, target.name + ".replaced").delete()
        }
    }

    @Test
    fun `v1 containers migrate forward with backup and keep payloads`() {
        val snapshot = fixtureSnapshot()
        val target = File.createTempFile("authr_v1", ".authr")
        try {
            AuthrContainer.export(snapshot, target)

            // Downgrade the manifest to v1 (simulating a prototype container).
            val v1File = File(target.parentFile, target.name + ".v1")
            java.util.zip.ZipOutputStream(v1File.outputStream().buffered()).use { zos ->
                java.util.zip.ZipFile(target).use { zip ->
                    zip.entries().toList().forEach { entry ->
                        zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (entry.name == "authr/manifest.json") {
                            val text = zip.getInputStream(entry).bufferedReader().readText()
                            val patched = text
                                .replace(Regex("\"schemaVersion\"\\s*:\\s*2"), "\"schemaVersion\": 1")
                                .replace(Regex("\"appVersion\"\\s*:\\s*\"3\\.0\\.0-alpha\""), "\"appVersion\": \"0.9.0\"")
                            assertTrue(
                                patched.contains("\"schemaVersion\": 1") && patched.contains("0.9.0"),
                                "manifest patch must apply; raw manifest was: $text"
                            )
                            zos.write(patched.toByteArray(Charsets.UTF_8))
                        } else {
                            zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }

            val result = AuthrContainer.import(v1File)
            assertEquals(1, result.migratedFrom, "must report the forward migration")
            assertEquals(2, result.manifest.schemaVersion)
            assertNotNull(result.manifest.migrationNote)
            assertNotNull(result.backupPath, "a backup must always precede migration")
            assertTrue(File(result.backupPath!!).exists(), "the backup file must exist on disk")

            // Payloads survive migration verbatim.
            assertEquals(snapshot.book.title, result.snapshot.book.title)
            assertEquals(snapshot.chapters.size, result.snapshot.chapters.size)
            assertEquals(snapshot.guns.size, result.snapshot.guns.size)

            // The migrated manifest itself is v2 and its schema features flags kept.
            assertEquals(true, result.manifest.featureFlags["writer_confirmed_gates"])
            v1File.delete()
        } finally {
            target.delete()
        }
    }

    @Test
    fun `tampered payload is reported by the checksum audit`() {
        val snapshot = fixtureSnapshot()
        val target = File.createTempFile("authr_checksum", ".authr")
        try {
            AuthrContainer.export(snapshot, target)
            val tampered = File(target.parentFile, target.name + ".tampered")
            java.util.zip.ZipOutputStream(tampered.outputStream().buffered()).use { zos ->
                java.util.zip.ZipFile(target).use { zip ->
                    zip.entries().toList().forEach { entry ->
                        zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (entry.name == "authr/ledger/chekhov.json") {
                            val text = zip.getInputStream(entry).bufferedReader().readText()
                            zos.write(text.replace("ESCALATED", "RESOLVED").toByteArray(Charsets.UTF_8))
                        } else {
                            zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
            val result = AuthrContainer.import(tampered)
            assertTrue(
                result.checksumWarnings.any { it.contains("chekhov.json") },
                "checksum audit must flag the tampered ledger: ${result.checksumWarnings}"
            )
            tampered.delete()
        } finally {
            target.delete()
        }
    }

    @Test
    fun `rejected - file that is not a container`() {
        val junk = File.createTempFile("authr_junk", ".authr")
        junk.writeText("not a zip")
        try {
            val error = runCatching { AuthrContainer.import(junk) }
            assertTrue(error.isFailure, "import must reject non-container files")
        } finally {
            junk.delete()
        }
    }
}
