package com.example

import com.example.engine.audio.SmilExporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EPUB 3 Media Overlays (SMIL) read-along export (v3.0 P4, plan §7.6): every
 * fragment pairs with its audio segment (text src ↔ audio clipBegin/End),
 * the paired XHTML content doc carries the matching fragment ids, and the
 * timing sanity check accepts contiguous full coverage and rejects gaps,
 * overlaps and a mismatched final clip.
 */
class SmilExporterTest {

    private fun frag(id: String, begin: Double, end: Double) =
        SmilExporter.Fragment(id, "text of $id", begin, end)

    @Test
    fun `fragmentText splits paragraphs and long paragraphs by sentence`() {
        val content = "First paragraph.\n\nSecond paragraph here.\n\n\n" +
            "A long paragraph. It has several sentences. Enough to split. More words follow here now. " +
            "The final sentence closes the group."
        // long paragraph: 5 sentences, > 20 words → split at maxWords=20
        val frags = SmilExporter.fragmentText(content, maxWords = 20)
        assertTrue(frags.size >= 4, "paragraph boundaries + sentence splits: ${frags.size}")
        assertEquals("First paragraph.", frags[0])
        assertEquals("Second paragraph here.", frags[1])
        val joined = frags.drop(2).joinToString(" ")
        assertTrue(joined.startsWith("A long paragraph."), "long paragraph text preserved")
        assertTrue(frags.none { it.isBlank() }, "blank paragraphs dropped")
    }

    @Test
    fun `fragmentsWithTimings lays fragments end to end weighted by words`() {
        val texts = listOf("one two three four", "five six", "seven eight nine")
        val frags = SmilExporter.fragmentsWithTimings(texts, 10.0)
        assertEquals(3, frags.size)
        assertEquals(0.0, frags[0].clipBeginSec, 1e-9)
        assertEquals(4.444, frags[0].clipEndSec, 0.01, "4/9 words of 10s")
        assertEquals(4.444, frags[1].clipBeginSec, 0.01)
        assertEquals(6.667, frags[1].clipEndSec, 0.01, "6/9 words of 10s")
        assertEquals(6.667, frags[2].clipBeginSec, 0.01)
        assertEquals(10.0, frags[2].clipEndSec, 1e-9, "last fragment ends exactly at chapter duration")
        assertTrue(frags.zipWithNext().all { (a, b) -> a.clipEndSec == b.clipBeginSec })
    }

    @Test
    fun `smil document pairs every text fragment with its audio segment`() {
        val fragments = listOf(frag("frag-1", 0.0, 2.5), frag("frag-2", 2.5, 6.125))
        val smil = SmilExporter.smilDocument(3, "ch03.xhtml", "audio/ch03.mp3", fragments)
        assertTrue(smil.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(smil.contains("<smil xmlns=\"http://www.w3.org/ns/SMIL\""))
        assertTrue(smil.contains("""<seq epub:textref="ch03.xhtml" id="ch03">"""))
        assertTrue(smil.contains("""<text src="ch03.xhtml#frag-1"/>"""))
        assertTrue(smil.contains("""<audio src="audio/ch03.mp3" clipBegin="0.000s" clipEnd="2.500s"/>"""))
        assertTrue(smil.contains("""<audio src="audio/ch03.mp3" clipBegin="2.500s" clipEnd="6.125s"/>"""))
        assertEquals(2, Regex("""<par id="frag-\d+">""").findAll(smil).count())
        // every par pairs exactly one text and one audio element
        val pars = Regex("<par[\\s\\S]*?</par>").findAll(smil).map { it.value }.toList()
        assertEquals(2, pars.size)
        pars.forEach { par ->
            assertEquals(1, Regex("""<text\s""").findAll(par).count(), par)
            assertEquals(1, Regex("""<audio\s""").findAll(par).count(), par)
        }
    }

    @Test
    fun `xhtml document carries the matching fragment ids`() {
        val fragments = listOf(
            SmilExporter.Fragment("frag-1", "She said <nothing> & left.", 0.0, 1.0)
        )
        val xhtml = SmilExporter.xhtmlDocument(1, "The <Title>", fragments)
        assertTrue(xhtml.contains("""<p id="frag-1">She said &lt;nothing&gt; &amp; left.</p>"""))
        assertTrue(xhtml.contains("Chapter 1: The &lt;Title&gt;"), "titles are XML-escaped")
        assertTrue(xhtml.contains("""epub:type="chapter""""))
    }

    @Test
    fun `timing sanity accepts contiguous full coverage`() {
        val fragments = listOf(frag("frag-1", 0.0, 2.0), frag("frag-2", 2.0, 5.0), frag("frag-3", 5.0, 9.5))
        val check = SmilExporter.timingSanity(fragments, 9.5)
        assertTrue(check.ok, check.summary())
        assertEquals(3, check.fragments)
        assertEquals(9.5, check.coveredSec, 1e-9)
        assertTrue(check.summary().contains("OK"))
    }

    @Test
    fun `timing sanity rejects gaps, overlaps and a mismatched final clip`() {
        val gap = listOf(frag("frag-1", 0.0, 2.0), frag("frag-2", 2.2, 4.0))
        assertFalse(SmilExporter.timingSanity(gap, 4.0).ok, "gap after frag-1")

        val overlap = listOf(frag("frag-1", 0.0, 2.5), frag("frag-2", 2.0, 4.0))
        assertFalse(SmilExporter.timingSanity(overlap, 4.0).ok, "overlap")

        val shortEnd = listOf(frag("frag-1", 0.0, 2.0), frag("frag-2", 2.0, 3.8))
        assertFalse(SmilExporter.timingSanity(shortEnd, 4.0).ok, "last clipEnd != duration")

        val inverted = listOf(frag("frag-1", 0.0, 2.0), frag("frag-2", 2.0, 1.5))
        assertFalse(SmilExporter.timingSanity(inverted, 1.5).ok, "non-increasing clip")

        val empty = SmilExporter.timingSanity(emptyList(), 10.0)
        assertFalse(empty.ok)
        assertTrue(empty.problems.contains("no fragments"))
    }
}
