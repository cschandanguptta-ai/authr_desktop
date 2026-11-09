package com.example.engine

/**
 * The cliché cleaner (v3.0, plan §5.1, Table 6) — the deterministic half of
 * the prose-quality bracket around the stochastic engine.
 *
 * 154 patterns across six classes, each with a severity (INFO / WARN /
 * ERROR), a plain-language suggested rewrite, and a prompt hint. Patterns
 * are compiled once (lazy, per project open) and run in three places:
 *
 *  1. Before generation — [promptConstraints] injects the forbidden-pattern
 *     block into the L4 drafting prompt as hard constraints (every provider).
 *  2. During generation — [TokenBiasPolicy] maps the most frequent offender
 *     tokens to a decode-time bias on routes whose API supports it (Table 7).
 *  3. After confirmation — the Live AST Annotator re-scans the frozen text
 *     through [scan] so the Writer Studio rail renders hits inline.
 *
 * The linter, not the bias, is the gate that decides what ships (plan §5.1).
 */
object ClicheCleaner {

    /** The six pattern classes of plan Table 6, with their launch counts. */
    object Classes {
        const val DIALOGUE_TAG = "Dialogue-tag crutches"
        const val FILTER_WORD = "Filter words"
        const val PURPLE_PROSE = "Purple prose"
        const val BODY_CLICHE = "Body-cliché cluster"
        const val PLOT_FORMULA = "Plot-formula phrases"
        const val WEB_NOVEL_FILLER = "Web-novel filler"

        /** Plan Table 6 launch counts: 30 / 32 / 28 / 24 / 18 / 22 = 154. */
        val planCounts: Map<String, Int> = linkedMapOf(
            DIALOGUE_TAG to 30,
            FILTER_WORD to 32,
            PURPLE_PROSE to 28,
            BODY_CLICHE to 24,
            PLOT_FORMULA to 18,
            WEB_NOVEL_FILLER to 22
        )
    }

    data class ClichePattern(
        val regex: Regex,
        val clicheClass: String,
        val severity: Severity,
        val rewrite: String,
        /** Compact surface form for the L4 prompt's forbidden-pattern block. */
        val hint: String
    )

    private fun p(
        pattern: String,
        clicheClass: String,
        severity: Severity,
        rewrite: String,
        hint: String = pattern,
        options: RegexOption = RegexOption.IGNORE_CASE
    ) = ClichePattern(Regex(pattern, options), clicheClass, severity, rewrite, hint)

    // ----------------------------------------------------------------------
    // The matrix — 154 patterns across six classes (plan Table 6)
    // ----------------------------------------------------------------------

    private val dialogueTagPatterns: List<ClichePattern> = listOf(
        p("""\b(she|he|it|they|we) (said|asked) (breathily|huskily|sharply|coldly|darkly|sternly)\b""",
            Classes.DIALOGUE_TAG, Severity.WARN, "Replace with an action beat: she slammed the cup down. \u201CAsk me again.\u201D", "\u201Cshe said breathily\u201D adverb tags"),
        p("""\b(she|he|it|they|we) (replied|answered|stated) (breathily|huskily|sharply|coldly|darkly|sternly|warmly|sadly)\b""",
            Classes.DIALOGUE_TAG, Severity.WARN, "Let the line itself carry the tone.", "\u201Che replied coldly\u201D"),
        p("""\b(she|he|it|they|we) (whispered|murmured|muttered) (breathily|shakily|tremulously|huskily)\b""",
            Classes.DIALOGUE_TAG, Severity.WARN, "Ground the whisper in a physical beat.", "\u201Cshe whispered shakily\u201D"),
        p("""\b(he|she|it|they) growled\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag in tense scenes.", "\u201Che growled\u201D"),
        p("""\b(he|she|it|they) hissed\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Hissing is for snakes; use an action beat.", "\u201Cshe hissed\u201D"),
        p("""\b(he|she|it|they) snarled\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che snarled\u201D"),
        p("""\b(he|she|it|they) spat( (out|back))?\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Cshe spat out\u201D"),
        p("""\b(he|she|it|they) rasped\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Convey the rasp through the line's wording.", "\u201Che rasped\u201D"),
        p("""\b(he|she|it|they) croaked\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Convey the croak through the line's wording.", "\u201Cshe croaked\u201D"),
        p("""\b(he|she|it|they) purred\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che purred\u201D"),
        p("""\b(he|she|it|they) drawled\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Convey the drawl through rhythm and word choice.", "\u201Cshe drawled\u201D"),
        p("""\b(he|she|it|they) cooed\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che cooed\u201D"),
        p("""\b(he|she|it|they) crooned\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Cshe crooned\u201D"),
        p("""\b(he|she|it|they) quipped\b""", Classes.DIALOGUE_TAG, Severity.INFO, "Let the quip land; tag it plainly or not at all.", "\u201Che quipped\u201D"),
        p("""\b(he|she|it|they) chortled\b""", Classes.DIALOGUE_TAG, Severity.INFO, "Use plain \u201Csaid\u201D plus an action beat.", "\u201Cshe chortled\u201D"),
        p("""\b(he|she|it|they) guffawed\b""", Classes.DIALOGUE_TAG, Severity.INFO, "Use plain \u201Csaid\u201D plus an action beat.", "\u201Che guffawed\u201D"),
        p("""\b(he|she|it|they) opined\b""", Classes.DIALOGUE_TAG, Severity.INFO, "State the opinion in the line; tag plainly.", "\u201Cshe opined\u201D"),
        p("""\b(he|she|it|they) chided\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che chided\u201D"),
        p("""\b(he|she|it|they) jeered\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Show the jeer in the dialogue itself.", "\u201Cshe jeered\u201D"),
        p("""\b(he|she|it|they) taunted\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Show the taunt in the dialogue itself.", "\u201Che taunted\u201D"),
        p("""\b(he|she|it|they) sneered\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Cshe sneered\u201D"),
        p("""\b(he|she|it|they) barked\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che barked\u201D"),
        p("""\b(he|she|it|they) (bit out|ground out)\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Use an action beat instead of an exotic tag.", "\u201Che bit out / she ground out\u201D"),
        p("""\b(she|he|they) choked out\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Ground the strain in a physical beat.", "\u201Cshe choked out\u201D"),
        p(""": ?\"?(growled|hissed|snarled|spat|rasped|croaked|purred|drawled|cooed|crooned|quipped|chortled|opined|jeered|taunted|sneered|barked) (he|she|it|they)\b""",
            Classes.DIALOGUE_TAG, Severity.WARN, "Inverted exotic tag — use an action beat.", "\u201C\u2026\u201D growled he (inverted tag)"),
        p("""\bin a (shaky|husky|breathless|throaty|trembling) voice\b""", Classes.DIALOGUE_TAG, Severity.INFO, "Convey the voice quality through the line's wording.", "\u201Cin a husky voice\u201D"),
        p("""\b(his|her|their) voice (was|sounded) (shaky|husky|breathless|throaty|trembling|strained)\b""",
            Classes.DIALOGUE_TAG, Severity.INFO, "Convey the strain through the dialogue itself.", "\u201Cher voice sounded strained\u201D"),
        p("""\b(he|she) said with a (growl|hiss|snarl|purr|sneer|scowl)\b""", Classes.DIALOGUE_TAG, Severity.WARN, "Split: an action beat, then plain \u201Csaid\u201D.", "\u201Che said with a growl\u201D"),
        p("""\b(he|she|they) (asked|added|offered) (helpfully|sweetly|innocently|gently)\b""",
            Classes.DIALOGUE_TAG, Severity.INFO, "Delete the adverb; the line carries the tone.", "\u201Cshe offered sweetly\u201D"),
        p("""\b(he|she|they) (said|asked) (in a|with a) (low|dark|dangerous|silky|velvet) (voice|tone|purr)\b""",
            Classes.DIALOGUE_TAG, Severity.WARN, "Replace the voice label with an action beat.", "\u201Cshe said in a silky voice\u201D")
    )

    private val filterWordPatterns: List<ClichePattern> = listOf(
        p("""\bshe felt\b""", Classes.FILTER_WORD, Severity.INFO, "Direct perception: \u201CThe cold seeped through her coat.\u201D", "\u201Cshe felt\u201D"),
        p("""\bhe felt\b""", Classes.FILTER_WORD, Severity.INFO, "Direct perception — cut the filter.", "\u201Che felt\u201D"),
        p("""\b(they|I) felt\b""", Classes.FILTER_WORD, Severity.INFO, "Direct perception — cut the filter.", "\u201Cthey felt\u201D"),
        p("""\b(she|he|they|I) could feel\b""", Classes.FILTER_WORD, Severity.INFO, "Direct perception — cut \u201Ccould feel\u201D.", "\u201Cshe could feel\u201D"),
        p("""\b(she|he|they|I) noticed\b""", Classes.FILTER_WORD, Severity.INFO, "Show the thing itself, not the noticing.", "\u201Cshe noticed\u201D"),
        p("""\b(she|he|they|I) realized\b""", Classes.FILTER_WORD, Severity.INFO, "Dramatize the realization in a beat.", "\u201Cshe realized\u201D"),
        p("""\b(she|he|they|I) saw that\b""", Classes.FILTER_WORD, Severity.INFO, "Show what is seen directly.", "\u201Cshe saw that\u201D"),
        p("""\b(she|he|they|I) watched as\b""", Classes.FILTER_WORD, Severity.INFO, "Cut the filter; render the watched action.", "\u201Cshe watched as\u201D"),
        p("""\b(she|he|they|I) heard\b""", Classes.FILTER_WORD, Severity.INFO, "Render the sound itself.", "\u201Cshe heard\u201D"),
        p("""\b(she|he|they|I) listened as\b""", Classes.FILTER_WORD, Severity.INFO, "Cut the filter; render the sound.", "\u201Cshe listened as\u201D"),
        p("""\b(she|he|they|I) sensed\b""", Classes.FILTER_WORD, Severity.INFO, "Ground the sensation concretely.", "\u201Cshe sensed\u201D"),
        p("""\b(she|he|they) seemed to\b""", Classes.FILTER_WORD, Severity.INFO, "Commit to the observation instead of hedging.", "\u201Cshe seemed to\u201D"),
        p("""\bit seemed (that|like)\b""", Classes.FILTER_WORD, Severity.INFO, "Commit to the observation instead of hedging.", "\u201Cit seemed that\u201D"),
        p("""\bthere seemed to be\b""", Classes.FILTER_WORD, Severity.INFO, "Commit to the observation instead of hedging.", "\u201Cthere seemed to be\u201D"),
        p("""\b(she|he|it|they) appeared to\b""", Classes.FILTER_WORD, Severity.INFO, "Commit to the observation instead of hedging.", "\u201Che appeared to\u201D"),
        p("""\b(she|he|it|they) began to\b""", Classes.FILTER_WORD, Severity.INFO, "Start the action directly: \u201CShe walked.\u201D", "\u201Cshe began to\u201D"),
        p("""\b(she|he|it|they) started to\b""", Classes.FILTER_WORD, Severity.INFO, "Start the action directly: \u201CHe laughed.\u201D", "\u201Che started to\u201D"),
        p("""\b(she|he|it|they) decided to\b""", Classes.FILTER_WORD, Severity.INFO, "Show the decision through the action it produces.", "\u201Cshe decided to\u201D"),
        p("""\b(she|he|they) thought that\b""", Classes.FILTER_WORD, Severity.INFO, "Render the thought directly, without the frame.", "\u201Cshe thought that\u201D"),
        p("""\b(she|he|they) wondered (if|whether)\b""", Classes.FILTER_WORD, Severity.INFO, "Pose the question in free indirect style.", "\u201Cshe wondered if\u201D"),
        p("""\b(she|he|they) knew that\b""", Classes.FILTER_WORD, Severity.INFO, "State the knowledge; the filter dilutes it.", "\u201Che knew that\u201D"),
        p("""\bwas aware that\b""", Classes.FILTER_WORD, Severity.INFO, "State the knowledge directly.", "\u201Cwas aware that\u201D"),
        p("""\b(she|he|they|I) could see( that)?\b""", Classes.FILTER_WORD, Severity.INFO, "Show what is seen directly.", "\u201Cshe could see\u201D"),
        p("""\b(she|he|they|I) could hear\b""", Classes.FILTER_WORD, Severity.INFO, "Render the sound itself.", "\u201Che could hear\u201D"),
        p("""\b(she|he|it|they) tried to\b""", Classes.FILTER_WORD, Severity.INFO, "Render the attempt and its outcome.", "\u201Cshe tried to\u201D"),
        p("""\bwas able to\b""", Classes.FILTER_WORD, Severity.INFO, "Render the outcome directly.", "\u201Cwas able to\u201D"),
        p("""\b(he|she|it|they) managed to\b""", Classes.FILTER_WORD, Severity.INFO, "Render the outcome directly.", "\u201Che managed to\u201D"),
        p("""\bfound (herself|himself|themselves)\b""", Classes.FILTER_WORD, Severity.WARN, "Replace the passive discovery with a choice.", "\u201Cfound herself\u201D"),
        p("""\bsort of\b""", Classes.FILTER_WORD, Severity.INFO, "Commit; delete the hedge.", "\u201Csort of\u201D"),
        p("""\bkind of\b""", Classes.FILTER_WORD, Severity.INFO, "Commit; delete the hedge.", "\u201Ckind of\u201D"),
        p("""\bvery (angry|sad|happy|scared|tired|afraid|excited|nervous)\b""",
            Classes.FILTER_WORD, Severity.INFO, "Show the emotion through behavior, not an intensifier.", "\u201Cvery angry\u201D"),
        p("""\b(extremely|incredibly|unbelievably|utterly) (angry|sad|happy|scared|tired|afraid|excited|nervous|cold|dark)\b""",
            Classes.FILTER_WORD, Severity.INFO, "Show the intensity through concrete detail.", "\u201Cincredibly angry\u201D")
    )

    private val purpleProsePatterns: List<ClichePattern> = listOf(
        p("""\ba symphony of\b""", Classes.PURPLE_PROSE, Severity.WARN, "One concrete image, stated plainly.", "\u201Ca symphony of\u2026\u201D"),
        p("""\bsymphony of (shadows|sound|screams|silence)\b""", Classes.PURPLE_PROSE, Severity.WARN, "One concrete image, stated plainly.", "\u201Csymphony of shadows\u201D"),
        p("""\b(a|the) dance of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Name the movement concretely.", "\u201Cthe dance of dust motes\u201D"),
        p("""\bdust motes danc(e|ed)\b""", Classes.PURPLE_PROSE, Severity.INFO, "State the light plainly.", "\u201Cdust motes danced\u201D"),
        p("""\bshadows danc(e|ed)\b""", Classes.PURPLE_PROSE, Severity.INFO, "State the movement plainly.", "\u201Cshadows danced\u201D"),
        p("""\b(a|the) tapestry of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Ca tapestry of\u2026\u201D"),
        p("""\b(a|the) kaleidoscope of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Ca kaleidoscope of\u2026\u201D"),
        p("""\b(an?|the) orchestra of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Can orchestra of\u2026\u201D"),
        p("""\b(a|the) ballet of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Ca ballet of\u2026\u201D"),
        p("""\bheart of hearts\b""", Classes.PURPLE_PROSE, Severity.INFO, "State the feeling plainly.", "\u201Cheart of hearts\u201D"),
        p("""\btime (stood|seemed to stand) still\b""", Classes.PURPLE_PROSE, Severity.INFO, "Anchor the moment in a concrete sensation.", "\u201Ctime stood still\u201D"),
        p("""\bin the nick of time\b""", Classes.PURPLE_PROSE, Severity.INFO, "Time the rescue with concrete beats.", "\u201Cin the nick of time\u201D"),
        p("""\b(a|the) sea of faces\b""", Classes.PURPLE_PROSE, Severity.INFO, "Pick two concrete faces instead.", "\u201Ca sea of faces\u201D"),
        p("""\ban ocean of\b""", Classes.PURPLE_PROSE, Severity.INFO, "Scale with specifics, not an ocean.", "\u201Can ocean of\u2026\u201D"),
        p("""\b(a|the) river of (stars|light|fire)\b""", Classes.PURPLE_PROSE, Severity.INFO, "State the image plainly.", "\u201Ca river of stars\u201D"),
        p("""\b(a|the) storm (raged|brewed) (in|within) (her|him|them|his|their)\b""",
            Classes.PURPLE_PROSE, Severity.WARN, "Name the feeling without the storm.", "\u201Ca storm raged within her\u201D"),
        p("""\b(a|the) whirlwind of (emotion|emotions|feeling|feelings)\b""", Classes.PURPLE_PROSE, Severity.WARN, "Name one concrete feeling.", "\u201Ca whirlwind of emotions\u201D"),
        p("""\b(a|the) cauldron of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Ca cauldron of\u2026\u201D"),
        p("""\b(a|the) labyrinth of\b""", Classes.PURPLE_PROSE, Severity.WARN, "Replace the metaphor with the specifics it hides.", "\u201Ca labyrinth of\u2026\u201D"),
        p("""\b(his|her|their) world came crashing down\b""", Classes.PURPLE_PROSE, Severity.WARN, "Show the loss through its concrete consequence.", "\u201Cher world came crashing down\u201D"),
        p("""\b(the) weight of the world (on|upon) (his|her|their) shoulders\b""",
            Classes.PURPLE_PROSE, Severity.WARN, "Show the burden through behavior.", "\u201Cthe weight of the world\u201D"),
        p("""\bthe air (was thick with|crackled with)\b""", Classes.PURPLE_PROSE, Severity.INFO, "Name what actually filled the air.", "\u201Cthe air was thick with\u2026\u201D"),
        p("""\belectricity (crackled|charged) (in|through) the air\b""", Classes.PURPLE_PROSE, Severity.INFO, "Name the actual sensation.", "\u201Celectricity crackled in the air\u201D"),
        p("""\b(silence|quiet) (hung|pressed|settled) (heavy|in the air|over)\b""", Classes.PURPLE_PROSE, Severity.INFO, "Ground the silence in one concrete sound it replaces.", "\u201Csilence hung heavy\u201D"),
        p("""\blight (danced|played) (across|over|through|on)\b""", Classes.PURPLE_PROSE, Severity.INFO, "State where the light falls, plainly.", "\u201Clight danced across\u201D"),
        p("""\bthe night (breathed|whispered|watched)\b""", Classes.PURPLE_PROSE, Severity.WARN, "Keep the night inanimate; give the action to a person.", "\u201Cthe night whispered\u201D"),
        p("""\b(as if) fate (wove|wove its|spun)\b""", Classes.PURPLE_PROSE, Severity.INFO, "Drop the cosmic narrator; show the coincidence.", "\u201Cas if fate wove\u2026\u201D"),
        p("""\b(a|the) ghost of a (smile|smirk|grin)\b""", Classes.PURPLE_PROSE, Severity.INFO, "Describe the actual, brief expression.", "\u201Ca ghost of a smile\u201D")
    )

    private val bodyClichePatterns: List<ClichePattern> = listOf(
        p("""\b(her|his|their) eyes widened\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cheyes widened\u201D"),
        p("""\b(her|his|their) eyes went wide\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Ceyes went wide\u201D"),
        p("""\b(her|his|their) breath hitched\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cbreath hitched\u201D"),
        p("""\b(her|his|their) breath caught( (in|at))?""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cbreath caught\u201D"),
        p("""\b(her|his|their) heart (hammered|pounded|raced|thundered|drummed|slammed)\b""",
            Classes.BODY_CLICHE, Severity.WARN, "Situation-specific physical response.", "\u201Cheart hammered\u201D"),
        p("""\b(her|his|their) heart (skipped|missed) a beat\b""", Classes.BODY_CLICHE, Severity.WARN, "Describe the actual cardiac moment.", "\u201Cheart skipped a beat\u201D"),
        p("""\b(her|his|their) knees (buckled|went weak|went weak beneath)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cknees buckled\u201D"),
        p("""\b(her|his|their) blood ran cold\b""", Classes.BODY_CLICHE, Severity.WARN, "Name the actual sensation of dread.", "\u201Cblood ran cold\u201D"),
        p("""\b(her|his|their) spine (tingled|prickled)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cspine tingled\u201D"),
        p("""\b(a) chill (ran|traveled|crept|crawled) (down|up) (her|his|their) spine\b""",
            Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Ca chill ran down her spine\u201D"),
        p("""\b(her|his|their) stomach (dropped|churned|lurched|knotted|twisted)\b""",
            Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cstomach dropped\u201D"),
        p("""\b(her|his|their) jaw (clenched|tightened|dropped|set)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cjaw clenched\u201D"),
        p("""\b(her|his|their) fists (clenched|tightened|curled)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cfists clenched\u201D"),
        p("""\b(her|his|their) hands (trembled|shook)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Chands trembled\u201D"),
        p("""\b(her|his|their) pulse (raced|throbbed|spiked|pounded)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cpulse raced\u201D"),
        p("""\b(her|his|their) throat (tightened|closed|closed up)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cthroat tightened\u201D"),
        p("""\b(her|his|their) chest (tightened|ached|hurt)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cchest tightened\u201D"),
        p("""\b(her|his|their) cheeks (flushed|burned|flamed|heated)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Ccheeks flushed\u201D"),
        p("""\b(her|his|their) face drained (of (all )?color)?""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cface drained of color\u201D"),
        p("""\b(her|his|their) skin (crawled|prickled)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cskin crawled\u201D"),
        p("""\b(her|his|their) hair stood (on end|up)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Chair stood on end\u201D"),
        p("""\b(her|his|their) mouth went dry\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cmouth went dry\u201D"),
        p("""\b(her|his|their) brow (furrowed|knitted|creased)\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cbrow furrowed\u201D"),
        p("""\b(her|his|their) nostrils flared\b""", Classes.BODY_CLICHE, Severity.INFO, "Situation-specific physical response.", "\u201Cnostrils flared\u201D")
    )

    private val plotFormulaPatterns: List<ClichePattern> = listOf(
        p("""\blittle did (he|she|they|I) know\b""", Classes.PLOT_FORMULA, Severity.WARN, "Let the dramatic irony emerge from the story.", "\u201Clittle did she know\u201D"),
        p("""\bunbeknownst to\b""", Classes.PLOT_FORMULA, Severity.WARN, "Delete or dramatize the knowledge gap.", "\u201Cunbeknownst to\u201D"),
        p("""\bupon further inspection\b""", Classes.PLOT_FORMULA, Severity.INFO, "Show the inspection and what it turns up.", "\u201Cupon further inspection\u201D"),
        p("""\bas fate would have it\b""", Classes.PLOT_FORMULA, Severity.WARN, "Drop the cosmic narrator; show the coincidence.", "\u201Cas fate would have it\u201D"),
        p("""\bat the eleventh hour\b""", Classes.PLOT_FORMULA, Severity.WARN, "Time the rescue with concrete beats.", "\u201Cat the eleventh hour\u201D"),
        p("""\bwhen suddenly\b""", Classes.PLOT_FORMULA, Severity.WARN, "Let the turn land without the announcer.", "\u201Cwhen suddenly\u201D"),
        p("""\ball of a sudden\b""", Classes.PLOT_FORMULA, Severity.INFO, "Let the turn land without the announcer.", "\u201Call of a sudden\u201D"),
        p("""\bwithout warning\b""", Classes.PLOT_FORMULA, Severity.INFO, "Let the turn land without the announcer.", "\u201Cwithout warning\u201D"),
        p("""\bout of nowhere\b""", Classes.PLOT_FORMULA, Severity.INFO, "Ground the surprise in a concrete cue.", "\u201Cout of nowhere\u201D"),
        p("""\bagainst all odds\b""", Classes.PLOT_FORMULA, Severity.WARN, "Show the odds being beaten, don't announce them.", "\u201Cagainst all odds\u201D"),
        p("""\bin the blink of an eye\b""", Classes.PLOT_FORMULA, Severity.INFO, "Measure the speed with concrete beats.", "\u201Cin the blink of an eye\u201D"),
        p("""\btime was running out\b""", Classes.PLOT_FORMULA, Severity.INFO, "Make the deadline concrete.", "\u201Ctime was running out\u201D"),
        p("""\bthe clock was ticking\b""", Classes.PLOT_FORMULA, Severity.INFO, "Make the deadline concrete.", "\u201Cthe clock was ticking\u201D"),
        p("""\bfate had other plans\b""", Classes.PLOT_FORMULA, Severity.WARN, "Drop the cosmic narrator.", "\u201Cfate had other plans\u201D"),
        p("""\ball hell broke loose\b""", Classes.PLOT_FORMULA, Severity.WARN, "Show the chaos in concrete events.", "\u201Call hell broke loose\u201D"),
        p("""\beverything changed\b""", Classes.PLOT_FORMULA, Severity.INFO, "Show the change through its first consequence.", "\u201Ceverything changed\u201D"),
        p("""^(it was a dark and stormy night)""", Classes.PLOT_FORMULA, Severity.ERROR, "Open in motion, with the POV character wanting something.", "\u201Cit was a dark and stormy night\u201D (opening)"),
        p("""\band in that moment,? (she|he|they|everything)\b""", Classes.PLOT_FORMULA, Severity.INFO, "Cut the announcer; let the moment speak.", "\u201Cand in that moment\u2026\u201D")
    )

    private val webNovelFillerPatterns: List<ClichePattern> = listOf(
        p("""\bwithout further ado\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Delete the announcer.", "\u201Cwithout further ado\u201D"),
        p("""\bneedless to say\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "If it's needless to say, don't.", "\u201Cneedless to say\u201D"),
        p("""\bit goes without saying\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "If it goes without saying, don't.", "\u201Cit goes without saying\u201D"),
        p("""\bsuffice it to say\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Just say the thing.", "\u201Csuffice it to say\u201D"),
        p("""\bpreviously,? on\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Recaps belong to the optional recap block, not the prose.", "\u201CPreviously, on\u2026\u201D recap opener"),
        p("""\blast time,? on\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Recaps belong to the optional recap block, not the prose.", "\u201CLast time, on\u2026\u201D recap opener"),
        p("""\bto recap[:,]""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Recaps belong to the optional recap block, not the prose.", "\u201CTo recap:\u201D recap opener"),
        p("""\bin the last chapter\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Recaps belong to the optional recap block, not the prose.", "\u201Cin the last chapter\u201D recap opener"),
        p("""\bas you know,?\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "If the reader knows, don't say it.", "\u201Cas you know\u201D"),
        p("""\bas I said before\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Trust the earlier scene; cut the repetition.", "\u201Cas I said before\u201D"),
        p("""\bas mentioned (before|earlier)\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Trust the earlier scene; cut the repetition.", "\u201Cas mentioned earlier\u201D"),
        p("""\bin other words\b""", Classes.WEB_NOVEL_FILLER, Severity.INFO, "Say it once, correctly.", "\u201Cin other words\u201D"),
        p("""\bas expected,?\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201Cas expected,\u201D"),
        p("""\bjust as (she|he|they) (had )?expected\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201Cjust as she expected\u201D"),
        p("""\bjust as (she|he|they) thought\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201Cjust as she thought\u201D"),
        p("""\b(yes|no),? (that'?s|you'?re) right\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201CYes, that's right\u201D"),
        p("""\byes,? exactly\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201CYes, exactly\u201D"),
        p("""\b(he|she|it) was right, of course\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201CHe was right, of course\u201D"),
        p("""\bof course[,!] (it|that|she|he) was\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Mirror-confirmation filler — delete.", "\u201Cof course it was\u201D"),
        p("""\bdear reader\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Address the reader through the story, not around it.", "\u201Cdear reader\u201D"),
        p("""\bgentle reader\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Address the reader through the story, not around it.", "\u201Cgentle reader\u201D"),
        p("""\bif you (recall|remember),?\b""", Classes.WEB_NOVEL_FILLER, Severity.WARN, "Recaps belong to the optional recap block, not the prose.", "\u201Cif you recall\u201D")
    )

    /** The full 154-pattern matrix, compiled once (plan §5.1). */
    val matrix: List<ClichePattern> by lazy {
        dialogueTagPatterns + filterWordPatterns + purpleProsePatterns +
            bodyClichePatterns + plotFormulaPatterns + webNovelFillerPatterns
    }

    /** Per-class counts, asserted against plan Table 6 by the test suite. */
    val patternsByClass: Map<String, List<ClichePattern>> by lazy { matrix.groupBy { it.clicheClass } }

    // ----------------------------------------------------------------------
    // Scanning — the post-confirmation linter pass (AST CLICHE_HIT nodes)
    // ----------------------------------------------------------------------

    /**
     * Scans [text] and returns one annotation per offending span. When two
     * patterns match overlapping spans, the higher-severity (then earlier)
     * pattern wins, so one catch produces exactly one node — the count a
     * writer sees equals the number of distinct offending spans.
     */
    fun scan(text: String): List<AstAnnotation> {
        val hits = matrix.flatMap { pat ->
            pat.regex.findAll(text).map { m ->
                Triple(m.range.first, m.range.last + 1, pat)
            }
        }
        // Span-dedupe: sort by start, severity DESC, class order; keep
        // non-overlapping spans greedily.
        val sorted = hits.sortedWith(
            compareBy({ it.first }, { -it.third.severity.ordinal }, { it.third.clicheClass })
        )
        val kept = mutableListOf<Triple<Int, Int, ClichePattern>>()
        for (hit in sorted) {
            if (kept.none { k -> hit.first < k.second && k.first < hit.second }) {
                kept.add(hit)
            }
        }
        return kept.map { (start, end, pat) ->
            AstAnnotation(
                kind = AnnotationKind.CLICHE_HIT, start = start, end = end,
                excerpt = text.substring(start, end),
                message = "${pat.clicheClass}: \u201C${text.substring(start, end)}\u201D — ${pat.rewrite}",
                severity = pat.severity, suggestedAction = WriterAction.ACCEPT_SUGGESTION,
                payload = mapOf("clicheClass" to pat.clicheClass, "rewrite" to pat.rewrite)
            )
        }
    }

    // ----------------------------------------------------------------------
    // Generation-time suppression — the prompt's hard constraints (§5.1)
    // ----------------------------------------------------------------------

    /**
     * The forbidden-pattern block injected into the L4 drafting prompt as
     * hard constraints for every provider (plan Table 7, all routes).
     */
    fun promptConstraints(): String {
        val classRules = mapOf(
            Classes.DIALOGUE_TAG to "tag dialogue with plain said/asked or an action beat — no said-bookisms (growled, hissed, snarled\u2026), no adverb-laden tags, no \u201Cin a husky voice\u201D",
            Classes.FILTER_WORD to "no perception filters (felt, noticed, realized, saw that, watched as, heard, seemed to, began to, started to, tried to\u2026) and no weak intensifiers (very angry, incredibly cold) — render perception and emotion directly",
            Classes.PURPLE_PROSE to "no overwrought metaphor (a symphony of\u2026, the dance of\u2026, a tapestry of\u2026, world came crashing down\u2026) — one concrete image, stated plainly",
            Classes.BODY_CLICHE to "no stock body reactions (eyes widened, breath hitched, heart hammered, knees buckled, blood ran cold, stomach dropped\u2026) — situation-specific physical responses only",
            Classes.PLOT_FORMULA to "no plot-formula phrases (little did she know, unbeknownst to, when suddenly, out of nowhere, against all odds\u2026) — let turns land without an announcer",
            Classes.WEB_NOVEL_FILLER to "no web-novel filler (needless to say, as you know, \u201CYes, exactly\u201D mirror-confirmation, recap openers) — recaps belong to the optional recap block, not the prose"
        )
        return buildString {
            appendLine("FORBIDDEN CLICHÉ PATTERNS (hard constraints — the text is linted against all 154 patterns before it can ship):")
            Classes.planCounts.forEach { (className, count) ->
                appendLine("- $className ($count patterns): ${classRules[className]}.")
            }
        }.trim()
    }

    // ----------------------------------------------------------------------
    // Offender tokens — input to decode-time biasing (§5.1 / Table 7)
    // ----------------------------------------------------------------------

    /**
     * The most frequent offender tokens in [text], ranked by how often they
     * appear inside cliché matches (then alphabetically for determinism).
     * Feeds [TokenBiasPolicy] where the provider's API supports biasing.
     */
    fun offenderTokens(text: String, limit: Int = 12): List<String> {
        val counts = LinkedHashMap<String, Int>()
        matrix.flatMap { it.regex.findAll(text) }.forEach { m ->
            m.value.lowercase()
                .split(Regex("""[^a-z']+"""))
                .filter { w -> w.length >= 3 && w !in stopWords }
                .forEach { w -> counts[w] = (counts[w] ?: 0) + 1 }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    private val stopWords = setOf("she", "he", "his", "her", "they", "that", "with", "the", "was", "were", "had", "and", "for")
}
