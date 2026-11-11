package com.example.engine.audio

import com.example.container.AuthrContainer
import com.example.data.model.Chapter

/**
 * IPA annotation and pronunciation rules (v3.0 P4, plan §7.3).
 *
 * Narration quality fails quietly on proper nouns: unusual names, coined
 * terms, and lorebook entries get mispronounced by every synthesis engine.
 * This pass makes those failures visible *before* synthesis:
 *
 *  1. [scan] — finds candidate terms in the confirmed manuscript (lorebook
 *     entries always; capitalized tokens that are not common English words;
 *     tokens containing an internal apostrophe or hyphen that are not common
 *     compounds). Each candidate is phonemized to IPA — on the desktop build
 *     via the bundled espeak-ng phonemizer ([EspeakPhonemizer]).
 *  2. [respell] — derives a deterministic, human-readable respelling proposal
 *     from the IPA ("ˈeɪlɪθ" → "AY-lith"). The plan assigns respelling
 *     *proposals* to the LLM and approval to the writer; this deterministic
 *     table is the offline proposal source, and the writer's approval (or
 *     correction) is recorded per project in the container rules.
 *  3. [normalize] — pre-synthesis text normalization: every *approved* rule
 *     term is replaced by its respelling ("Aelyth" → "AY-lith") so every
 *     engine — local or cloud — inherits the same corrections.
 *
 * Approved entries land in the existing pronunciation-rules list with an IPA
 * field (schema v2, `AuthrContainer.PronunciationRules.Rule`, P0).
 */
object PronunciationPass {

    /** A pronunciation candidate found in the manuscript, with its phonemization. */
    data class Candidate(
        val term: String,
        val ipa: String,
        val respelling: String,
        val source: Source
    ) {
        enum class Source(val label: String) {
            LOREBOOK("lorebook entry"),
            PROPER_NOUN("capitalized token"),
            COINED("coined/hyphenated token")
        }
    }

    /**
     * Possessives and contractions are not coinages: the apostrophe marks
     * inflection, and every engine pronounces the base word correctly
     * ("brother's", "couldn't", "you're"). A coined name keeps its internal
     * apostrophe away from the end ("Shea'lyn").
     */
    private fun isPossessiveOrContraction(lower: String): Boolean {
        val suffixes = listOf("'s", "'z", "'n't", "'re", "'ve", "'ll", "'d", "'m", "’s", "’re", "’ve", "’ll", "’d", "’m", "n't")
        return suffixes.any { lower.endsWith(it) && lower.length > it.length }
    }

    /** An approved entry: the shape stored in the container rules list. */
    data class ApprovedRule(val term: String, val ipa: String, val respelling: String)

    /**
     * Phonemizes text to IPA. Implementations must be deterministic for the
     * same input; returns null when phonemization fails (unknown word style,
     * missing binary).
     */
    fun interface Phonemizer {
        fun toIpa(text: String): String?
    }

    /**
     * The desktop phonemizer: espeak-ng `--ipa -q`, text on stdin (safe for
     * arbitrary-length input and shell-hostile characters).
     */
    class EspeakPhonemizer(
        private val binary: String?,
        private val voice: String = "en-us",
        private val timeoutMs: Long = 20_000
    ) : Phonemizer {
        constructor() : this(com.example.desktopbridge.EspeakLocator.espeakPath())

        override fun toIpa(text: String): String? {
            val bin = binary ?: return null
            if (text.isBlank()) return null
            return try {
                val proc = ProcessBuilder(bin, "-q", "--ipa", "-v", voice)
                    .redirectErrorStream(false)
                    .start()
                proc.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                // Drain stderr so espeak warnings can never deadlock the pipe.
                val errDrain = Thread { proc.errorStream.use { s -> s.readBytes() } }
                    .apply { isDaemon = true; start() }
                val out = proc.inputStream.bufferedReader().readText().trim()
                val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                errDrain.join(500)
                if (!finished) { proc.destroyForcibly(); null } else out.ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }
    }

    /** A small offline common-word list; tokens here never become candidates. */
    private val COMMON_WORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "did", "do",
        "for", "from", "had", "has", "have", "he", "her", "here", "hers", "him", "his", "how",
        "i", "if", "in", "into", "is", "it", "its", "just", "me", "my", "no", "not", "of",
        "on", "or", "our", "out", "she", "so", "some", "than", "that", "the", "their", "them",
        "then", "there", "these", "they", "this", "to", "too", "up", "us", "was", "we", "were",
        "what", "when", "where", "which", "who", "why", "will", "with", "would", "you", "your",
        "chapter", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "mr", "mrs", "ms", "dr", "sir", "madam", "lord", "lady", "captain", "general", "sergeant",
        "said", "asked", "replied", "thought", "looked", "turned", "began", "left", "came", "went"
    )

    /**
     * Scans the confirmed chapters for pronunciation candidates. Lorebook
     * entries are always candidates; a capitalized token is a candidate when
     * it is not a common word and not merely sentence-initial (proper nouns
     * surface mid-sentence somewhere — an ordinary word is capitalized only
     * at sentence start); a coined token (internal hyphen, or an apostrophe
     * that is not a possessive/contraction ending) is a candidate when not
     * common. Candidates are deduped case-insensitively, ordered lorebook-first then
     * by first appearance. Chapters are used as given — the renderer gates
     * canon (AGENTS.md hard rule 1).
     */
    fun scan(
        chapters: List<Chapter>,
        lorebookTerms: Collection<String> = emptyList(),
        phonemizer: Phonemizer
    ): List<Candidate> {
        val seen = LinkedHashMap<String, Candidate>()
        val commonLower = COMMON_WORDS

        fun offer(termRaw: String, source: Candidate.Source) {
            val term = termRaw.trim().trim('.', ',', ';', ':', '!', '?', '"', '\'', '“', '”', '‘', '’', '—', '(', ')')
            if (term.length < 2 || term.contains(' ')) return
            val lower = term.lowercase()
            if (lower in commonLower) return
            if (isPossessiveOrContraction(lower)) return
            if (seen.containsKey(lower)) return
            val ipa = phonemizer.toIpa(term) ?: return
            seen[lower] = Candidate(term, ipa, respell(ipa), source)
        }

        // lorebook entries first — deliberate coinages by definition
        lorebookTerms.forEach { offer(it, Candidate.Source.LOREBOOK) }

        // First pass: collect capitalized tokens seen at NON-INITIAL sentence
        // positions. Proper nouns appear mid-sentence sooner or later; an
        // ordinary word is capitalized only at sentence start ("Metal
        // remembers..."), so sentence-initial-only capitals are not offered
        // unless the lorebook lists them.
        val midSentenceCapitals = HashSet<String>()
        chapters.forEach { ch ->
            ch.content.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
                val tokens = Regex("[A-Za-zÀ-ɏ'’-]+").findAll(sentence).map { it.value }.toList()
                tokens.drop(1).forEach { token ->
                    val stripped = token.trim('\'', '’', '-')
                    if (stripped.any { it.isUpperCase() }) midSentenceCapitals += stripped
                }
            }
        }

        // Second pass: candidates are lorebook entries, mid-sentence capitals,
        // and coined terms (internal hyphen/apostrophe) wherever they appear.
        chapters.forEach { ch ->
            ch.content.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
                val tokens = Regex("[A-Za-zÀ-ɏ'’-]+").findAll(sentence).map { it.value }.toList()
                tokens.forEachIndexed { idx, token ->
                    val stripped = token.trim('\'', '’', '-')
                    val hasInternal = stripped.length > 2 &&
                        stripped.drop(1).any { it == '\'' || it == '’' || it == '-' }
                    when {
                        stripped.any { it.isUpperCase() } &&
                            (idx > 0 || stripped in midSentenceCapitals) &&
                            stripped.length > 2 ->
                            offer(stripped, Candidate.Source.PROPER_NOUN)
                        hasInternal && stripped.length > 3 ->
                            offer(stripped, Candidate.Source.COINED)
                    }
                }
            }
        }
        return seen.values.toList()
    }

    /**
     * Deterministic IPA → readable respelling. The primary-stress marker ˈ
     * marks the stressed syllable (rendered in caps); vowel and consonant
     * symbols map through a fixed table. Syllable boundaries follow the
     * maximal-onset principle: a consonant run between two vowels keeps only
     * its legal-English onset in the next syllable, the rest closes the
     * current one. Symbols with no table entry are dropped, so the result is
     * always readable ASCII. Examples:
     * "ˈeɪlɪθ" → "AY-lith", "kəˈθɑːrsɪs" → "kuh-THAHRS-is",
     * "ˈveɪlmʊə" → "VAYL-moor".
     */
    fun respell(ipa: String): String {
        val syllables = syllabify(ipa)
        if (syllables.isEmpty()) return ""
        // the stressed syllable = the one whose onset/vowel follows the ˈ marker;
        // equivalently: the number of vowel symbols before the marker
        val marker = ipa.indexOf('ˈ')
        val stressIndex = if (marker < 0) -1 else ipa.substring(0, marker).count { it in IPA_VOWELS }
        return syllables.mapIndexed { idx, s ->
            if (idx == stressIndex) s.uppercase() else s
        }.joinToString("-")
    }

    private val IPA_VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'æ', 'ɛ', 'ɪ', 'ɒ', 'ʊ', 'ʌ', 'ə', 'ɑ', 'ɔ', 'ɜ')

    /** Legal two-consonant English onsets (ASCII respelling space). */
    private val LEGAL_ONSET_PAIRS = setOf(
        "pl", "pr", "tr", "tw", "kr", "kl", "kw", "bl", "br", "dr", "gr", "gl", "gw",
        "fl", "fr", "sp", "st", "sk", "sl", "sm", "sn", "sw", "vy", "by", "dy", "py",
        "my", "ny", "ly", "ry", "hy", "fy", "ky", "ty", "gy", "sh", "th", "ch", "zh"
    )

    /** Splits IPA into (onset, nucleus, coda) syllables, respelled in ASCII. */
    internal fun syllabify(ipa: String): List<String> {
        val vowels = mapOf(
            "eɪ" to "ay", "aɪ" to "eye", "aʊ" to "ow", "ɔɪ" to "oy", "oʊ" to "oh",
            "ɪə" to "eer", "eə" to "air", "ʊə" to "oor", "ɪʊ" to "ew", "ɛə" to "air",
            "iː" to "ee", "uː" to "oo", "ɑː" to "ah", "ɔː" to "aw", "ɜː" to "er",
            "æ" to "a", "ɛ" to "e", "ɪ" to "i", "ɒ" to "o", "ʊ" to "u", "ʌ" to "u", "ə" to "uh",
            "ɚ" to "er", "ɝ" to "ur", "ᵻ" to "i", "ᵿ" to "u",
            "i" to "ee", "u" to "oo", "ɑ" to "ah", "ɔ" to "aw", "ɜ" to "er", "a" to "a", "e" to "e", "o" to "o"
        )
        val consonants = mapOf(
            "tʃ" to "ch", "dʒ" to "j", "θ" to "th", "ð" to "th", "ʃ" to "sh", "ʒ" to "zh",
            "ŋ" to "ng", "j" to "y", "w" to "w", "r" to "r", "l" to "l",
            "b" to "b", "d" to "d", "f" to "f", "g" to "g", "h" to "h", "k" to "k",
            "m" to "m", "n" to "n", "p" to "p", "s" to "s", "t" to "t", "v" to "v", "z" to "z",
            "ɹ" to "r", "ɾ" to "t"
        )

        // 1. tokenize: each symbol tagged vowel nucleus / consonant (longest match first)
        val core = ipa.replace("ˈ", "").replace("ˌ", "")
        val isVowel = ArrayList<Boolean>()
        val mapped = ArrayList<String>()
        var i = 0
        while (i < core.length) {
            val two = if (i + 1 < core.length) core.substring(i, i + 2) else ""
            val v = vowels[two]
            if (v != null) { mapped += v; isVowel += true; i += 2; continue }
            val v1 = vowels[core[i].toString()]
            if (v1 != null) { mapped += v1; isVowel += true; i += 1; continue }
            val c = consonants[two]
            if (c != null) { mapped += c; isVowel += false; i += 2; continue }
            val c1 = consonants[core[i].toString()]
            if (c1 != null) { mapped += c1; isVowel += false; i += 1; continue }
            i++ // length marks already consumed by two-char vowel keys; unknown dropped
        }
        val nucleiIdx = mapped.indices.filter { isVowel[it] }
        if (nucleiIdx.isEmpty()) return emptyList()

        // 2. group into syllables: consonant runs between nuclei split by the
        //    maximal-onset principle (a legal English onset goes to the next
        //    syllable, the rest closes the current one)
        val out = ArrayList<String>()
        for (k in nucleiIdx.indices) {
            val nIdx = nucleiIdx[k]
            val runStart = if (k == 0) 0 else nucleiIdx[k - 1] + 1
            val run = (runStart until nIdx).map { mapped[it] } // ASCII respellings
            val onsetCount = if (k == 0) run.size else when {
                run.size >= 2 && (run[run.size - 2] + run[run.size - 1]) in LEGAL_ONSET_PAIRS -> 2
                run.isNotEmpty() -> 1
                else -> 0
            }
            val onset = run.takeLast(onsetCount).joinToString("")
            val coda = run.dropLast(onsetCount).joinToString("")
            if (k > 0) out[k - 1] = out[k - 1] + coda
            val trailing = if (k == nucleiIdx.lastIndex)
                ((nIdx + 1) until mapped.size).joinToString("") { mapped[it] }
            else ""
            out += onset + mapped[nIdx] + trailing
        }
        return out
    }

    /**
     * Pre-synthesis text normalization (plan §7.3): replaces every approved
     * rule's term with its respelling, case-insensitively, on word
     * boundaries (a hyphen is a boundary — hyphenated compounds pronounce
     * their parts; an apostrophe is not). The respelling applies verbatim:
     * its stress capitals are part of what the writer approved.
     * Unapproved rules are ignored — nothing enters synthesis until the
     * writer approves.
     */
    fun normalize(text: String, rules: List<ApprovedRule>): String {
        var out = text
        rules.filter { it.respelling.isNotBlank() }.forEach { rule ->
            val pattern = Regex(
                "(?<![A-Za-zÀ-ɏ'’])${Regex.escape(rule.term)}(?![A-Za-zÀ-ɏ'’])",
                RegexOption.IGNORE_CASE
            )
            out = out.replace(pattern, rule.respelling)
        }
        return out
    }

    /** Converts container rules (with the P0 schema-v2 IPA field) to engine rules. */
    fun approvedFromContainer(rules: AuthrContainer.PronunciationRules): List<ApprovedRule> =
        rules.rules.filter { it.approvedByWriter }
            .map { ApprovedRule(it.term, it.ipa, it.respelling) }

    /** Converts writer-approved candidates to the container rules shape. */
    fun toContainerRules(projectTitle: String, approved: List<ApprovedRule>): AuthrContainer.PronunciationRules =
        AuthrContainer.PronunciationRules(
            projectTitle = projectTitle,
            rules = approved.map { AuthrContainer.PronunciationRules.Rule(it.term, it.ipa, it.respelling, approvedByWriter = true) }
        )
}
