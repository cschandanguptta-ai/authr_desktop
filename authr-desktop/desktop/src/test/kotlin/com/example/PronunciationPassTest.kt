package com.example

import com.example.data.model.Chapter
import com.example.engine.audio.PronunciationPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * IPA annotation and pronunciation rules (v3.0 P4, plan §7.3): scan finds
 * lorebook entries and proper nouns but not common words; the deterministic
 * respeller maps IPA to readable respellings (maximal-onset syllabification,
 * stress caps); pre-synthesis normalization applies approved rules only,
 * verbatim; container conversion round-trips.
 */
class PronunciationPassTest {

    /** Fake phonemizer with a fixed dictionary — deterministic, no binaries. */
    private val phon = PronunciationPass.Phonemizer { t ->
        when (t.lowercase()) {
            "aelyth" -> "ˈeɪlɪθ"
            "katharsis" -> "kəˈθɑːrsɪs"
            "vaelmoor" -> "ˈveɪlmʊə"
            "shea'lyn" -> "ˈʃeɪlɪn"
            "iron-vein" -> "ˈaɪərnveɪn"
            else -> null
        }
    }

    private fun ch(n: Int, content: String) = Chapter(
        bookId = 1, chapterNumber = n, title = "c$n", content = content, summary = "s"
    )

    @Test
    fun `scan finds lorebook entries and proper nouns, skips common words`() {
        val chapters = listOf(
            ch(1, "The girl crossed the bridge and Aelyth watched from the ridge."),
            ch(2, "In Katharsis the forges never cooled. The captain said nothing.")
        )
        val cands = PronunciationPass.scan(chapters, listOf("Vaelmoor"), phon)
        val terms = cands.map { it.term }
        assertTrue("Aelyth" in terms, "proper noun found mid-sentence: $terms")
        assertTrue("Katharsis" in terms, "mid-sentence capital found: $terms")
        assertTrue("Vaelmoor" in terms, "lorebook entry found: $terms")
        assertFalse("The" in terms, "common words never candidates")
        assertFalse("captain" in terms, "common lowercase words never candidates")
        assertFalse("ridge" in terms, "ordinary lowercase words never candidates")
        assertEquals(PronunciationPass.Candidate.Source.LOREBOOK, cands.first { it.term == "Vaelmoor" }.source)
        assertEquals(PronunciationPass.Candidate.Source.PROPER_NOUN, cands.first { it.term == "Aelyth" }.source)
    }

    @Test
    fun `sentence-initial-only capitals are ordinary words, not candidates`() {
        // "Bridge" starts its sentence and never appears mid-sentence: an
        // ordinary word, not a name — the scan must not offer it even when
        // the phonemizer could phonemize it.
        val chapters = listOf(ch(1, "Bridge builders worked here at dawn."))
        val terms = PronunciationPass.scan(chapters, emptyList(), phon).map { it.term }
        assertFalse("Bridge" in terms)
        assertFalse("builders" in terms)
    }

    @Test
    fun `coined hyphenated and apostrophe terms are candidates`() {
        val chapters = listOf(
            ch(1, "The iron-vein ran deep. Shea'lyn sang the old names.")
        )
        val cands = PronunciationPass.scan(chapters, emptyList(), phon)
        val terms = cands.map { it.term }
        assertTrue("iron-vein" in terms, "hyphenated coinage found: $terms")
        assertTrue("Shea'lyn" in terms, "apostrophe coinage found: $terms")
        assertEquals(PronunciationPass.Candidate.Source.COINED, cands.first { it.term == "iron-vein" }.source)
    }

    @Test
    fun `respell maps IPA to readable stress-marked respellings`() {
        assertEquals("AY-lith", PronunciationPass.respell("ˈeɪlɪθ"))
        assertEquals("kuh-THAHR-sis", PronunciationPass.respell("kəˈθɑːrsɪs"))
        assertEquals("VAYL-moor", PronunciationPass.respell("ˈveɪlmʊə"))
        assertEquals("SHAY-lin", PronunciationPass.respell("ˈʃeɪlɪn"))
        // espeak en-us uses ɹ/ɾ/ɚ/ᵻ — they must respell, not drop
        assertEquals("PROO", PronunciationPass.respell("pɹˈuː"))
        assertEquals("TOHL-kee-per", PronunciationPass.respell("tˈoʊlkˈiːpɚ"))
    }

    @Test
    fun `normalize applies approved rules verbatim, punctuation preserved`() {
        val rules = listOf(
            PronunciationPass.ApprovedRule("Aelyth", "ˈeɪlɪθ", "AY-lith"),
            PronunciationPass.ApprovedRule("Katharsis", "kəˈθɑːrsɪs", "kuh-THAHR-sis")
        )
        val out = PronunciationPass.normalize(
            "\"Aelyth!\" she said. In Katharsis, aelyth means dawn. The Katharsians cheered.", rules
        )
        assertEquals(
            "\"AY-lith!\" she said. In kuh-THAHR-sis, AY-lith means dawn. The Katharsians cheered.",
            out
        )
        // unapproved rules are ignored — nothing enters synthesis without approval
        val none = PronunciationPass.normalize("Aelyth slept.", emptyList())
        assertEquals("Aelyth slept.", none)
    }

    @Test
    fun `normalize does not replace inside longer words but crosses hyphens`() {
        val rules = listOf(PronunciationPass.ApprovedRule("vein", "veɪn", "vayn"))
        val out = PronunciationPass.normalize("The iron-vein and veins pulsed.", rules)
        // hyphen is a pronunciation boundary: iron-vein → iron-vayn;
        // "veins" is a different word and stays
        assertEquals("The iron-vayn and veins pulsed.", out)
    }

    @Test
    fun `container rules conversion round-trips approved entries`() {
        val approved = listOf(
            PronunciationPass.ApprovedRule("Aelyth", "ˈeɪlɪθ", "AY-lith")
        )
        val container = PronunciationPass.toContainerRules("The Iron Vale", approved)
        assertEquals("The Iron Vale", container.projectTitle)
        assertEquals(1, container.rules.size)
        assertEquals("ˈeɪlɪθ", container.rules[0].ipa)
        assertTrue(container.rules[0].approvedByWriter)
        val back = PronunciationPass.approvedFromContainer(container)
        assertEquals(approved, back)
        // a writer-rejected entry never comes back approved
        val rejected = container.copy(
            rules = container.rules.map { it.copy(approvedByWriter = false) }
        )
        assertTrue(PronunciationPass.approvedFromContainer(rejected).isEmpty())
    }
}
