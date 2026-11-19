package com.example

import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.engine.pipeline.ManuscriptQaSuite
import com.example.engine.voice.SceneInvariantsEngine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.0 port S4 validation (spec §1.2 narrative features + §1.5 dialogue
 * sub-engine = desktop ADR-0023): scene-level micro-invariants — POV
 * consistency, tense consistency, dialogue differentiation, opener
 * variety, composite drift — plus their QA-suite wiring as warning
 * gates.
 */
class SceneInvariantsTest {

    // ------------------------------------------------------------------
    // POV consistency

    @Test
    fun `pov - shift inside a scene is flagged, shift across a break is not`() {
        val paras = listOf(
            "Mara saw the harbor lights and counted the seconds.",
            "She checked the rope twice before tying off.",
            "You should not be here at this hour, and the letter proves nothing.", // 2nd-person shift inside the scene
            "The gulls kept their own council overhead."                            // neutral closer — their ≠ POV cue after strip? see note
        )
        val v = SceneInvariantsEngine.povViolations(paras)
        assertEquals(1, v.size, "one in-scene POV shift: $v")
        assertTrue(!v[0].headHop)
        assertEquals(SceneInvariantsEngine.Pov.THIRD_LIMITED, v[0].from)
        assertEquals(SceneInvariantsEngine.Pov.SECOND, v[0].to)

        // Same shift, but after an explicit scene break → legal. The new
        // scene is 2nd-person throughout (opener + closer both 2nd).
        val withBreak = paras.subList(0, 2) + listOf(
            "***",
            "You should not be here at this hour, and the letter proves nothing.",
            "You take the stairs anyway, two at a time."
        )
        val v2 = SceneInvariantsEngine.povViolations(withBreak)
        assertEquals(0, v2.size, "shift after a scene break is craft, not drift: $v2")
    }

    @Test
    fun `pov - head-hop between two viewpoint characters is flagged`() {
        val paras = listOf(
            "Mara watched the tide gauge and worried.",
            "The rope creaked.",
            "Kell realized the knots were wrong and felt his stomach drop." // different viewpoint character, same scene
        )
        val v = SceneInvariantsEngine.povViolations(paras)
        assertEquals(1, v.size, "head-hop detected: $v")
        assertTrue(v[0].headHop)
    }

    @Test
    fun `pov - clean single-viewpoint scene passes`() {
        val paras = listOf(
            "Mara saw the harbor lights.",
            "She counted the seconds on her watch.",
            "The rain came sideways and she felt it sting."
        )
        assertEquals(0, SceneInvariantsEngine.povViolations(paras).size)
    }

    // ------------------------------------------------------------------
    // Tense consistency

    @Test
    fun `tense - narration flip inside a scene is flagged, quoted speech is exempt`() {
        val paras = listOf(
            "Mara walked to the pier and checked the rope.",
            "The gulls cried over the breakwater.",
            "The tide is rising fast and the mooring slips loose.", // present-tense flip in narration
            "The dog barked once and followed."                     // past closes the scene
        )
        val v = SceneInvariantsEngine.tenseViolations(paras)
        assertEquals(1, v.size, "one tense flip: $v")
        assertTrue(v[0].fromPastToPresent)

        // Speech in present tense inside a past scene must NOT flag.
        val clean = listOf(
            "Mara walked to the pier and checked the rope.",
            "\"I am going,\" she said.",
            "The dog barked once and followed."
        )
        assertEquals(0, SceneInvariantsEngine.tenseViolations(clean).size, "quoted tense is exempt")
    }

    @Test
    fun `tense - break cue re-anchors the scene`() {
        val paras = listOf(
            "Mara walked to the pier.",
            "***",
            "The tide is rising now, and Mara runs." // new scene, new tense — legal
        )
        assertEquals(0, SceneInvariantsEngine.tenseViolations(paras).size)
    }

    // ------------------------------------------------------------------
    // Dialogue differentiation

    private val collapsed = listOf(
        "Mara said, \"I really think we should go to the market now because we need the supplies for the winter.\"",
        "Kell replied, \"I really think we should go to the market now because we need the flour for the bread.\"",
        "Mara said, \"I really think the road will be very long and we will need the good boots.\"",
        "Kell replied, \"I really think the road will be very hard and we will need the good coats.\"",
        "Mara said, \"I really think we should start early because the day will be very short.\"",
        "Kell replied, \"I really think we should start early because the night will be very cold.\""
    )

    @Test
    fun `dialogue - two characters with the same voice collapse above 0_70`() {
        val findings = SceneInvariantsEngine.dialogueDifferentiation(collapsed)
        assertEquals(1, findings.size, "one collapsing pair: $findings")
        val f = findings[0]
        assertEquals(setOf("Mara", "Kell"), setOf(f.characterA, f.characterB))
        assertTrue(f.similarity > 0.70, "similarity ${f.similarity} must exceed 0.70")
    }

    private val differentiated = listOf(
        "Mara said, \"No. We go now. The tide waits for nobody.\"",
        "Kell replied, \"Well, I suppose, if you really believe that, darling, we might perhaps consider going a little later, when the light is kinder.\"",
        "Mara said, \"Later means never. Move.\"",
        "Kell replied, \"One does so admire your decisiveness, my dear, though one might gently observe that the rocks are rather slippery at this hour.\"",
        "Mara said, \"Rocks. Slippery. Noted. Now climb.\"",
        "Kell replied, \"Of course, of course — though I do hope you will forgive me if I proceed with the utmost, the very utmost, caution, as is my lifelong habit.\""
    )

    @Test
    fun `dialogue - distinct voices stay below the flag`() {
        val findings = SceneInvariantsEngine.dialogueDifferentiation(differentiated)
        assertEquals(0, findings.size, "distinct voices must not flag: $findings")
    }

    @Test
    fun `dialogue - short samples below 25 words are skipped`() {
        val paras = listOf(
            "Mara said, \"Go.\"",
            "Kell replied, \"Stay.\"",
            "Mara said, \"Now.\"",
            "Kell replied, \"Wait.\""
        )
        assertEquals(0, SceneInvariantsEngine.dialogueDifferentiation(paras).size)
    }

    // ------------------------------------------------------------------
    // Opener variety

    @Test
    fun `openers - three in a row flags, two does not`() {
        val mono = listOf(
            "She walked to the pier.",
            "She checked the rope.",
            "She counted the gulls."
        ).joinToString(" ")
        val v = SceneInvariantsEngine.openerViolations(mono)
        assertEquals(1, v.size, "run of 3 flags: $v")
        assertEquals("she", v[0].opener)

        val varied = listOf(
            "She walked to the pier.",
            "The rope needed checking.",
            "She counted the gulls."
        ).joinToString(" ")
        assertEquals(0, SceneInvariantsEngine.openerViolations(varied).size)
    }

    // ------------------------------------------------------------------
    // Composite drift

    @Test
    fun `composite drift - clean scene scores low, broken scene scores high`() {
        val clean = listOf(
            "Mara saw the harbor lights and tied off the rope.",
            "She counted the seconds while the gulls argued.",
            "The rain came sideways, and she felt it sting."
        )
        assertTrue(SceneInvariantsEngine.compositeDrift(clean) < 0.30, "clean scene under the warning line")

        val broken = listOf(
            "She saw the harbor lights and tied off the rope.",
            "She counted the seconds while the gulls argued.",
            "She watched the ferry leave without her.",
            "You are late, she thought.",               // POV shift (3rd → 2nd)
            "The tide is rising and the rope slips.",   // tense flip
            "She runs. She slips. She falls."           // opener run (len 3)
        )
        assertTrue(SceneInvariantsEngine.compositeDrift(broken) >= 0.30, "broken scene at or above the warning line")
    }

    // ------------------------------------------------------------------
    // QA-suite wiring (the five v3 warning gates)

    private fun chapter(n: Int, content: String): Chapter = Chapter(
        bookId = 1, chapterNumber = n, title = "Chapter $n", content = content,
        summary = "s", status = "Completed", writerConfirmed = true, draftStatus = "CONFIRMED"
    )

    @Test
    fun `qa suite - scene gates surface as warning findings`() {
        val broken = (collapsed + listOf(
            "She saw the harbor lights from the pier.",
            "You are late and the tide is turning.",    // in-scene POV shift (3rd → 2nd)
            "The rope frays and the mooring slips loose." // in-scene tense flip (past → present)
        )).joinToString("\n\n")
        val report = ManuscriptQaSuite.run(listOf(chapter(1, broken)))
        val kinds = report.findings.map { it.kind }.toSet()
        assertTrue("DIALOGUE" in kinds, "dialogue gate fires: ${report.findings.map { it.kind }}")
        assertTrue("POV" in kinds, "pov gate fires")
        assertTrue("TENSE" in kinds, "tense gate fires")
        assertTrue(report.findings.all { it.severity == FindingSeverity.WARNING },
            "scene gates are warnings, not blockers")
        // Findings carry chapter numbers for the writer's queue.
        assertTrue(report.findings.all { it.chapterNumber == 1 })
    }

    @Test
    fun `qa suite - clean chapter produces no scene findings`() {
        val clean = listOf(
            "Mara saw the harbor lights and tied off the rope.",
            "She counted the seconds while the gulls argued overhead.",
            "\"We go now,\" she said. \"The tide waits for nobody.\"",
            "The dog barked once and followed her down the pier."
        ).joinToString("\n\n")
        val report = ManuscriptQaSuite.run(listOf(chapter(1, clean)))
        val sceneKinds = setOf("POV", "TENSE", "DIALOGUE", "OPENER", "COMPOSITE_DRIFT")
        assertTrue(
            report.findings.none { it.kind in sceneKinds },
            "clean scene yields no scene findings: ${report.findings.map { it.kind to it.message }}"
        )
    }
}

/** Local alias so the assertion above reads clearly. */
private object FindingSeverity {
    val WARNING = com.example.engine.pipeline.Finding.Severity.WARNING
}
