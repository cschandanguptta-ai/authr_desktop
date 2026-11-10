package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.serialization.SerialExportProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Serial platform export profiles (v3.0 P3, plan §6.13, Table 20):
 * paste-ready chapter packages formatted per platform — the application
 * formats, the writer pastes. No platform APIs; profiles are versioned data.
 */
class SerialExportProfilesTest {

    private fun book(profile: String = "royal_road", wordMin: Int = 1000, wordMax: Int = 2000) =
        Book(id = 61, title = "The Estate of Fallingwater Dike", genre = "g", synopsis = "s", outline = "o",
            isSerialized = true, exportProfile = profile, wordMin = wordMin, wordMax = wordMax)

    private fun canon(n: Int, note: String? = null, bookId: Int = 61) =
        Chapter(bookId = bookId, chapterNumber = n, title = "The Broken Seal",
            content = "First paragraph of chapter $n.\n\nSecond paragraph with \"spoken words\" inside.\n\nThird paragraph.",
            summary = "s", writerConfirmed = true, draftStatus = DraftState.CONFIRMED, ctaNote = note)

    @Test
    fun `the four platform families ship with the blueprint's profile behaviors`() {
        val royalRoad = SerialExportProfiles.byId("royal_road")
        assertEquals("Royal Road", royalRoad.platform)
        assertFalse(royalRoad.recapByDefault, "no recap by default")
        assertTrue(royalRoad.allowLinksInNote, "author note appended; links allowed")

        val wattpad = SerialExportProfiles.byId("wattpad")
        assertTrue(wattpad.paragraphGap.length > royalRoad.paragraphGap.length, "looser paragraph spacing conventions")

        val goodnovel = SerialExportProfiles.byId("goodnovel")
        assertTrue(goodnovel.recapByDefault, "pay-per-chapter storefronts: optional recap header block")
        assertFalse(goodnovel.allowLinksInNote, "author note without external links")

        val tapas = SerialExportProfiles.byId("tapas")
        assertTrue(tapas.supportsPolls, "Tapas supports poll payloads")
        assertTrue(tapas.lengthGuidance != null, "episode-length guidance against the envelope")
        assertEquals(6, SerialExportProfiles.PROFILES.size, "royal_road, wattpad, goodnovel, dreame, webnovel, tapas")
    }

    @Test
    fun `the package carries title, body in the platform's spacing, and the author note`() {
        val pkg = SerialExportProfiles.buildPackage(
            book(), canon(1, "Thanks for reading — poll next week!"), SerialExportProfiles.byId("royal_road")
        )
        assertTrue(pkg.chapterBlock.startsWith("Chapter 1: The Broken Seal"))
        assertTrue(pkg.chapterBlock.contains("First paragraph"))
        assertTrue(pkg.chapterBlock.contains("[AUTHOR'S NOTE]"))
        assertTrue(pkg.chapterBlock.contains("Thanks for reading"))
        assertEquals(pkg.chapterBlock, pkg.clipboardBundle, "with no poll payload the clipboard bundle is the chapter block itself")
    }

    @Test
    fun `recap blocks export only where the profile defaults them on`() {
        val withRecap = SerialExportProfiles.buildPackage(book(), canon(2), SerialExportProfiles.byId("goodnovel"), recapBlock = "Kira reached the archive.")
        assertTrue(withRecap.chapterBlock.contains("[PREVIOUSLY ON]"), "pay-per-chapter storefronts carry the recap header")
        val withoutRecap = SerialExportProfiles.buildPackage(book(), canon(2), SerialExportProfiles.byId("royal_road"), recapBlock = "Kira reached the archive.")
        assertFalse(withoutRecap.chapterBlock.contains("[PREVIOUSLY ON]"), "Royal Road: no recap by default")
    }

    @Test
    fun `polls export only where the platform supports them — and the note discards links where policy forbids`() {
        val tapas = SerialExportProfiles.byId("tapas")
        val tapasPkg = SerialExportProfiles.buildPackage(book(), canon(3), tapas, pollText = "Which character do you trust?")
        assertTrue(tapasPkg.clipboardBundle.contains("[POLL]"), "Tapas exports the poll payload")

        val royal = SerialExportProfiles.byId("royal_road")
        val royalPkg = SerialExportProfiles.buildPackage(book(), canon(3), royal, pollText = "Which character do you trust?")
        assertFalse(royalPkg.clipboardBundle.contains("[POLL]"))
        assertTrue(royalPkg.warnings.any { it.contains("does not support polls") }, "the poll stays a writer-visible decision")

        val goodnovel = SerialExportProfiles.byId("goodnovel")
        val linked = SerialExportProfiles.buildPackage(book(), canon(4, "Read more at https://example.com/notes"), goodnovel)
        assertFalse(linked.chapterBlock.contains("https://example.com"), "author note without external links")
        assertTrue(linked.chapterBlock.contains("[link removed — platform policy]"))
    }

    @Test
    fun `episode-length guidance warns outside the envelope on tapas`() {
        val tapas = SerialExportProfiles.byId("tapas")
        val long = SerialExportProfiles.buildPackage(book().copy(wordMax = 10), canon(5), tapas)
        assertTrue(long.warnings.any { it.contains("Episode-length guidance") }, "the envelope warning carries the guidance text")
        val royal = SerialExportProfiles.buildPackage(book().copy(wordMax = 10), canon(5), SerialExportProfiles.byId("royal_road"))
        assertTrue(royal.warnings.isEmpty(), "no episode guidance on non-episode profiles")
    }

    @Test
    fun `batch export walks the release queue in a chosen profile`() = runTest {
        val book = book(profile = "goodnovel")
        val queue = listOf(canon(1), canon(2), canon(3))
        val packages = SerialExportProfiles.buildBatch(book, queue, "goodnovel") { ch -> "Recap of chapter ${ch.chapterNumber}" }
        assertEquals(3, packages.size)
        assertEquals(listOf(1, 2, 3), packages.map { it.chapterNumber })
        assertTrue(packages.all { it.profileId == "goodnovel" })
        assertTrue(packages.all { it.chapterBlock.contains("[PREVIOUSLY ON]") && it.chapterBlock.contains("Recap of chapter") })
    }

    @Test
    fun `unknown profile ids fall back to the first shipped profile`() {
        assertEquals("royal_road", SerialExportProfiles.byId("nonexistent").id)
    }
}
