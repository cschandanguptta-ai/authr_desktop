package com.example.engine.pipeline

import com.example.data.model.Book
import com.example.data.model.Chapter

/**
 * Prompt builders for the nine pipeline stages (ADR-0018).
 *
 * Every prompt embeds the SAME persisted context (via [StageContext] /
 * [EditorialMemoryStore]) so two stages can never silently contradict each
 * other: market findings feed concept; concept feeds architecture;
 * architecture feeds worldbuilding; everything feeds the manuscript prompt;
 * editorial and engagement prompts see the canon manuscript.
 *
 * Output contracts are STRICT JSON (the stages parse defensively and fail
 * loud on drift) and every prompt states the writer-sovereignty rule: the
 * output is a PROPOSAL for writer approval, never final text.
 */
object StagePrompts {

    /** Shared context block prepended to every stage prompt. */
    fun contextBlock(ctx: StageContext, maxChars: Int = 3000): String = buildString {
        appendLine("## Project Context (authoritative — do not contradict)")
        appendLine("Title: ${ctx.book.title}")
        appendLine("Genre: ${ctx.book.genre} | Format: ${ctx.book.format} | Structure: ${ctx.book.narrativeStructure}")
        appendLine("Target length: ${ctx.book.targetWordCount} words; chapter envelope ${ctx.book.wordMin}-${ctx.book.wordMax} words")
        val synopsis = ctx.book.synopsis.trim()
        if (synopsis.isNotBlank()) appendLine("Synopsis: ${synopsis.take(500)}")
        ctx.memory.marketFindings?.let { appendLine("Market findings: ${it.take(400)}") }
        ctx.memory.positioning?.let { appendLine("Positioning: ${it.take(400)}") }
        ctx.memory.readerPersonas?.let { appendLine("Reader personas: ${it.take(300)}") }
        if (ctx.memory.storyBible.isNotBlank()) appendLine("Story bible (excerpt): ${ctx.memory.storyBible.take(600)}")
        if (ctx.memory.characterBible.isNotBlank()) appendLine("Character bible (excerpt): ${ctx.memory.characterBible.take(600)}")
        if (ctx.memory.worldBible.isNotBlank()) appendLine("World bible (excerpt): ${ctx.memory.worldBible.take(600)}")
        ctx.memory.decisions.takeLast(5).forEach {
            appendLine("Editorial decision (${it.stage.label}): ${it.decision} — ${it.rationale.take(200)}")
        }
        if (ctx.memory.openIssues.isNotEmpty()) {
            appendLine("Open editorial issues (respect these):")
            ctx.memory.openIssues.take(5).forEach { appendLine("  - [${it.severity}] ${it.description.take(200)}") }
        }
    }.let { if (it.length > maxChars) it.take(maxChars) else it }

    private const val OUTPUT_RULES = """
## Output Rules
- Return STRICT JSON only — no markdown fences, no commentary outside JSON.
- This output is a PROPOSAL for the writer to approve or reject; it never becomes final automatically.
- Never contradict the Project Context above; if you must extend it, say so in a field named "notes".
"""

    // ------------------------------------------------------------------
    // Stage 1 — Market & Audience Research

    fun marketResearch(ctx: StageContext, extraDirection: String): String = """
You are a publishing market analyst. Research the commercial landscape for this book BEFORE it is written.

${contextBlock(ctx)}

## Author Direction
${extraDirection.ifBlank { "Analyze the market for the stated genre and positioning as given." }}

## Task
Analyze and return JSON with EXACTLY this shape:
{
  "genreExpectations": ["4-8 concrete reader expectations for this genre, each one sentence"],
  "readerDemographics": "2-4 sentences: who reads this genre (age bands, platforms, reading habits, willingness to pay)",
  "comparableTitles": ["3-6 real, well-known comparable titles with author and one-clause positioning note"],
  "marketGaps": ["2-5 specific underserved angles or reader demands visible in the genre"],
  "commercialOpportunities": ["2-5 concrete opportunities this book could exploit"],
  "summary": "3-5 sentence executive summary a publisher could act on"
}
Base claims on widely known genre reality; do NOT invent sales figures or quote specific chart data you cannot know.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 2 — Concept Development

    fun concept(ctx: StageContext, count: Int = 3): String = """
You are a developmental story editor and positioning strategist. Develop ${count}distinct book concepts for this project.

${contextBlock(ctx)}

## Task
Return JSON with EXACTLY this shape:
{
  "concepts": [
    {
      "title": "working title",
      "premise": "2-3 sentence hook-level premise",
      "differentiator": "what makes this unlike existing comparable books — be specific",
      "sellingProposition": "one sentence a reader would use to recommend it to a friend",
      "targetReader": "which reader persona this serves and why",
      "promiseToReader": "the emotional contract with the reader (what feeling they are guaranteed)",
      "avoidList": ["3-6 genre clichés this concept deliberately avoids"],
      "summary": "one-paragraph pitch"
    }
  ],
  "recommendation": "which concept index (0-based) you recommend and why, in 2-3 sentences"
}
Each concept must exploit at least one market gap from the findings and satisfy at least three genre expectations.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 3 — Story Architecture

    fun storyArchitecture(ctx: StageContext, chapterCount: Int): String = """
You are a plot architect. Design the complete story architecture for this book.

${contextBlock(ctx)}

## Task
Return JSON with EXACTLY this shape:
{
  "logline": "one-sentence story statement",
  "thematicFramework": "2-4 sentences: theme, argument, and how it resolves",
  "acts": [
    {"name": "act name", "chapters": "chapter range", "purpose": "the dramatic job of this act", "turn": "the irreversible turn that ends it"}
  ],
  "characterArcs": [
    {"character": "name", "want": "external goal", "need": "internal need", "startState": "...", "endState": "...", "keyMoments": ["chapter-level beats"]}
  ],
  "chapters": [
    {"number": 1, "title": "chapter title", "summary": "2-3 sentence chapter plan", "function": "the story job this chapter does", "cliffhanger": "how the chapter ends"}
  ],
  "continuitySeeds": ["3-8 promises planted early that MUST pay off later (Chekhov's guns)"]
}
Provide exactly $chapterCount chapters with rising escalation and a climax that fires every continuity seed.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 4 — Worldbuilding & Continuity

    fun worldbuilding(ctx: StageContext): String = """
You are a world builder and continuity manager. Produce the bibles for this book.

${contextBlock(ctx)}

## Task
Return JSON with EXACTLY this shape:
{
  "storyBible": "the narrative contract: tone, POV, tense, structural rules, series plan if any (4-8 paragraphs as one string)",
  "characterBible": "for each named character: identity, motivation, voice markers, speech patterns, relationships, secrets, arc. One block per character.",
  "worldBible": "settings, rules of the world, magic/tech constraints, timeline, geography, social structures, naming conventions",
  "relationshipMap": ["A ↔ B: the nature and tension of the relationship"],
  "timeline": ["ordered story-time events with chapter anchors"],
  "narrativeRules": ["3-8 invariants this story must never violate (e.g. 'magic always costs memory')"]
}
Every entry must be consistent with the story architecture and the project context above.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 6 — Editorial Intelligence (five passes)

    enum class EditorialPass(val label: String, val focus: String) {
        DEVELOPMENTAL("Developmental Edit", "story-level: premise strength, structure, character arcs, stakes, theme execution, emotional payoff"),
        STRUCTURAL("Structural Edit", "chapter order, scene necessity, pacing architecture, act turns, redundancy between chapters"),
        LINE("Line Edit", "prose style per scene: voice consistency, show-don't-tell, dialogue quality, sentence rhythm, cliché elimination"),
        COPYEDIT("Copyedit", "grammar, punctuation, spelling, tense consistency, POV slips, factual consistency within the manuscript"),
        PROOFREAD("Proofread", "typographical errors, formatting, dialogue punctuation, remaining surface defects after copyedit")
    }

    fun editorial(ctx: StageContext, pass: EditorialPass, chapter: Chapter?): String = """
You are a professional ${pass.label.lowercase()} editor. Perform a rigorous ${pass.label.lowercase()} on this manuscript.

${contextBlock(ctx)}

## Manuscript Under Review
${if (chapter != null) "Chapter ${chapter.chapterNumber}: ${chapter.title}\n\n${chapter.content.take(14000)}" else ctx.canonChapters.joinToString("\n\n") { "Chapter ${it.chapterNumber}: ${it.title}\n${it.content.take(4000)}" }.take(14000)}

## Your Focus
${pass.focus}

## Task
Return JSON with EXACTLY this shape:
{
  "assessment": "3-6 sentence overall editorial assessment",
  "findings": [
    {"severity": "MAJOR|MINOR|POLISH", "location": "chapter number + nearest quote or paragraph reference", "issue": "what is wrong", "fix": "the concrete revision to make"}
  ],
  "strengths": ["what is working and must be preserved"],
  "revisionPlan": ["ordered, concrete revision steps the writer should apply"]
}
Be specific: quote the manuscript, name chapters, and give fixes a copyeditor could apply directly.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 7 — Reader Engagement Optimization

    fun engagement(ctx: StageContext): String = """
You are a reader-retention analyst for commercial fiction. Analyze this manuscript for engagement and completion likelihood.

${contextBlock(ctx)}

## Manuscript
${ctx.canonChapters.joinToString("\n\n") { "Chapter ${it.chapterNumber}: ${it.title}\n${it.content.take(3500)}" }.take(14000)}

## Task
Analyze, per chapter and overall: opening-hook strength, pacing, narrative tension, curiosity gaps, emotional investment, character attachment, chapter-ending effectiveness, momentum into the next chapter, readability.
Return JSON with EXACTLY this shape:
{
  "perChapter": [
    {"chapter": 1, "hook": "assessment of the opening lines", "tension": "tension arc through the chapter", "ending": "ending effectiveness and why a reader continues", "risk": "the biggest drop-off risk in this chapter"}
  ],
  "overall": {
    "momentum": "chapter-to-chapter momentum assessment",
    "curiosityEngine": "are open questions opened and answered at the right cadence?",
    "characterAttachment": "will readers care about the protagonist by chapter 3? evidence",
    "completionLikelihood": "what helps and hurts a reader finishing this book",
    "readability": "sentence-level accessibility assessment"
  },
  "recommendations": ["ordered, concrete changes ranked by retention impact"]
}
Base every judgment on measurable textual evidence you quote; no unfounded claims about reader behavior.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 8 — Quality Assurance

    fun qualityAssurance(ctx: StageContext): String = """
You are a manuscript QA auditor. Find every defect a reviewer would flag.

${contextBlock(ctx)}

## Manuscript
${ctx.canonChapters.joinToString("\n\n") { "Chapter ${it.chapterNumber}: ${it.title}\n${it.content.take(3500)}" }.take(14000)}

## Task
Audit for: plot holes, unresolved setups, continuity errors (names, dates, physical facts, POV), repetition (scenes, phrases, descriptions), inconsistencies with the bibles, weak passages, pacing problems, timeline contradictions.
Return JSON with EXACTLY this shape:
{
  "defects": [
    {"kind": "PLOT_HOLE|CONTINUITY|REPETITION|INCONSISTENCY|WEAK_PASSAGE|PACING|TIMELINE", "severity": "HARD|WARNING", "chapter": 1, "description": "the defect, quoting the manuscript", "recommendation": "the concrete fix"}
  ],
  "verificationChecklist": ["checks a human should manually verify"]
}
Every defect must quote evidence. Do not speculate about intent; report what the text shows.
$OUTPUT_RULES
""".trim()

    // ------------------------------------------------------------------
    // Stage 9 — Publication Preparation

    fun publicationPrep(ctx: StageContext): String = """
You are a publishing package specialist. Prepare the retail package for this finished manuscript.

${contextBlock(ctx)}

## Manuscript
${ctx.canonChapters.joinToString("\n\n") { "Chapter ${it.chapterNumber}: ${it.title}\n${it.content.take(2000)}" }.take(10000)}

## Task
Return JSON with EXACTLY this shape:
{
  "titles": [
    {"title": "title candidate", "subtitle": "subtitle or empty", "rationale": "why it sells"}
  ],
  "descriptions": {
    "shortBlurb": "1-2 sentence hook (tagline level)",
    "retailBlurb": "150-250 word store description following genre conventions (hook, stakes, voice, CTA — no spoilers)",
    "backCover": "80-120 word back-cover copy"
  },
  "metadata": {
    "sevenKeywords": ["exactly 7 KDP keyword phrases, no duplication with title/genre"],
    "threeCategories": ["3 specific store categories"],
    "contentWarnings": ["any content advisories justified by the manuscript"],
    "audienceStatement": "who this is for, one sentence"
  },
  "promotionalMaterials": {
    "announcementPost": "social announcement post, platform-neutral",
    "readerNewsletter": "launch newsletter paragraph",
    "trailerScript": "30-second book trailer script with visual beats"
  },
  "readinessNotes": ["anything a publisher should verify before going live"]
}
Everything must be grounded in the actual manuscript above — no invented plot details.
$OUTPUT_RULES
""".trim()
}
