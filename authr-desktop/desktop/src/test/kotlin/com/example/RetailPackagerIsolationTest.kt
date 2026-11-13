package com.example

import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.publishing.PrintInterior
import com.example.engine.publishing.RetailPackager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the CI test-isolation flake (2026-11-13, bug #3).
 *
 * The direct routes run a whole test slice in ONE JVM. Two defects let one
 * test poison another:
 *
 *  1. RetailPackager's fixed pool used a bare shutdown() — idle worker
 *     threads parked for their 60 s keepalive AFTER packageRetail returned,
 *     and those threads read JVM-global state (user.home for the font
 *     search) that a later test had already flipped.
 *  2. PrintInteriorTest staged a font into the SHARED code-source parent
 *     (the tracked build/classes tree every test reads); a crash mid-test
 *     left the staged file behind for every later test.
 *
 * This guard pins the fixed contracts:
 *
 *  - packageRetail leaves NO live "authr-retail-packager" thread behind;
 *  - resolveFontPath's app-dir search honors compose.application.resources.dir
 *    (the property the fixed test uses to point the search at a temp dir —
 *    staging never touches the shared tree).
 */
class RetailPackagerIsolationTest {

    @Test
    fun `packageRetail leaks no pool threads — the flake mechanism`() {
        val before = Thread.getAllStackTraces().keys.count { it.name.startsWith("authr-retail-packager") }
        val out = createTempDir("iso-retail")
        try {
            // A minimal one-action package (epubcheck absent → UNVERIFIED
            // verdicts, which is fine — we only need the pool to have run).
            val input = RetailPackager.Input(
                bookTitle = "Isolation Probe",
                author = "Test",
                isSerial = false,
                chapters = listOf(
                    Chapter(
                        bookId = 1, chapterNumber = 1, title = "One",
                        content = "Short body text for the isolation probe. ".repeat(20),
                        summary = "s", writerConfirmed = true,
                        draftStatus = DraftState.CONFIRMED
                    )
                )
            )
            RetailPackager.packageRetail(input, out)
            Thread.sleep(200) // let a leaked keepalive thread show itself
            val after = Thread.getAllStackTraces().keys.count { it.name.startsWith("authr-retail-packager") }
            assertEquals(
                "packageRetail must not leave live pool threads behind (the CI flake mechanism)",
                before, after
            )
        } finally {
            out.deleteRecursively()
        }
    }

    @Test
    fun `font search honors the resources-dir property without touching shared state`() {
        val src = File("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf")
        org.junit.Assume.assumeTrue("dejavu serif not present", src.isFile)
        val resDir = createTempDir("iso-fonts")
        val staged = File(File(resDir, "fonts"), "DejaVuSerif.ttf")
        staged.parentFile.mkdirs()
        staged.writeBytes(src.readBytes())
        val origHome = System.getProperty("user.home")
        val origRes = System.getProperty("compose.application.resources.dir")
        System.setProperty("user.home", "/nonexistent-authr-home")
        System.setProperty("compose.application.resources.dir", resDir.absolutePath)
        try {
            val resolved = PrintInterior.resolveFontPath(explicitPath = "", bold = false)
            assertTrue(
                "app-dir candidate via the resources-dir property must win before system dirs",
                resolved.startsWith(resDir.absolutePath)
            )
        } finally {
            System.setProperty("user.home", origHome)
            if (origRes == null) System.clearProperty("compose.application.resources.dir")
            else System.setProperty("compose.application.resources.dir", origRes)
            resDir.deleteRecursively()
        }
    }
}
