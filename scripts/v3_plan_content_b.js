// Section 6: Web Novel Serialization Format (both modes) — the Rev 2 headline feature
"use strict";
const path = require("path");
const { h1, h2, p, tbl } = require("./v3_plan_lib");

function build() {
  const out = [];

  out.push(h1("6. Web Novel Serialization Format (Both Modes)"));
  out.push(p(
    "Serialized fiction is a distinct economic product: readers pay per chapter in a micro-transaction loop, discover the story through a free opening tier, and stay through cadence and habit. The Serialized Fiction Blueprint supplied with this revision specifies the structural, pacing, and retention mechanics of that model in implementable detail; v3.0 encodes the blueprint as a **project format** (section 9) with a **validator suite**, a **cliffhanger rotation tracker**, a **micro-arc planner**, and a **retention toolkit**, all available in **both workspaces**. The serialization surface is deterministic and local: the LLM drafts chapters, but every structural rule in this section is checked by code, not by the model's discretion."
  ));
  out.push(p(
    "The format extends what v2.3 already seeds. The Book entity carries isSerialized, targetChapters (default 100), and releaseSchedule (default \u201cMon/Wed/Fri\u201d), and the Outline and Drafting workers already branch on isSerialized. Section 6.7 defines the full field set; Appendix A carries the DDL."
  ));

  out.push(h2("6.1 Free Tier and the Hook (Chapters 1–5)"));
  out.push(p(
    "The free tier has one objective: **conversion velocity**. The writer must build a large information deficit before the reader reaches the paywall, and the application must measure that deficit mechanically. Three structural benchmarks and two continuous pacing checks are enforced on free-tier chapters; each produces a warning in the Studio rail and a blocking error only at chapter export if the writer has not addressed or dismissed it with a note."
  ));
  out.push(...tbl(
    "Free-tier structural benchmarks and their validators",
    ["Benchmark", "Rule (blueprint)", "Validator implementation", "Failure signal"],
    [
      ["The 200-word hook", "The inciting disruption or acute tension must be established within the first 200 words of Chapter 1; extended world-building up front is a high churn risk", "Hook detector scans the first 200 words for tension markers (threat verbs, stakes nouns, conflicting dialogue); a text dominated by setting description flags **high churn risk**", "Churn-risk badge on Chapter 1; export warning until a tension marker is present or the writer dismisses with a note"],
      ["Inciting incident window", "The inciting incident must occur squarely in Chapter 1 — serialization cannot delay it the way a novel may", "Beat-map check on the approved L2 outline: Chapter 1 must contain the inciting beat", "Outline cannot be approved for a serial without an inciting beat in Chapter 1"],
      ["Paywall cliffhanger placement", "The final free chapter must end precisely one beat **before** a structural payoff (a revealed answer, a collision, an impact)", "Paywall linter: the last free chapter must contain a cliffhanger signature in its final 50 words and must **not** contain the payoff beat marked in the outline", "Blocking warning at chapter export if the payoff beat is detected inside the free tier"],
      ["Exposition pacing warning", "Chapters 1–2 must not run more than 300 consecutive words of exposition or narration without dialogue or action", "Paragraph-run analyzer over confirmed text; counts consecutive narration words between dialogue/action beats", "Inline pacing warning in the Studio rail with the offending run highlighted"],
      ["Cliffhanger signature check", "Chapters 4–5 should carry a high-density cliffhanger signature approaching the paywall", "Final-50-words density check: question marks, interrupted dialogue (em-dash cutoffs), abrupt action verbs", "Missing-signature info hint on chapters 4–5 of the free tier"],
    ],
    [16, 26, 32, 26]
  ));

  out.push(h2("6.2 Premium Chapter Anatomy: the 10/70/20 Cycle"));
  out.push(p(
    "Once a reader pays, each chapter is a self-contained unit of persuasion. The blueprint fixes the internal economy of a premium chapter: **10% immediate resolution, 70% progressive complication, 20% the new horizon**, inside a word envelope of **1,000–2,000 words** — sized for mobile reading in short breaks, where long chapters read as an exhausting investment and very short ones as low value. The L4 drafter receives the anatomy as a structural prompt (it drafts toward the cycle), and the anatomy meter verifies the result on confirmed text."
  ));
  out.push(...tbl(
    "Serial chapter anatomy (10/70/20) and its validators",
    ["Segment", "Share", "Function (blueprint)", "Validator on confirmed text"],
    [
      ["Immediate resolution", "10%", "Resolve the previous chapter's cliffhanger tension while inheriting its underlying consequences", "Opening-segment checker: the prior chapter's dangling beat must be referenced within the first tenth of the word count"],
      ["Progressive complication", "70%", "Advance plot or subplot with new friction, new data points, or shifting interpersonal dynamics", "Beat coverage: at least one new complication beat confirmed in the chapter's beat card; the linter warns when the middle span contains no conflict marker"],
      ["The new horizon", "20%", "Pivot the scene toward a sudden obstacle or revelation that builds the next cliffhanger", "Final-fifth signature check: a cliffhanger signal must be present in the last fifth of the chapter"],
      ["Word envelope", "—", "1,000–2,000 words per chapter", "Hard prompt constraint at L4; export-time warning outside 900–2,200; the meter shows the deviation"],
    ],
    [18, 8, 34, 40]
  ));

  out.push(h2("6.3 The Four-Tier Cliffhanger Rotation Matrix"));
  out.push(p(
    "Repetitive formulaic endings fatigue readers. The blueprint requires rotating through four specific emotional and structural triggers at chapter ends. v3.0 implements the rotation as a **tracked plan**: each serial chapter carries an intended tier, the Annotator's cliffhanger-signal check verifies the tier's signature in the final 50 words, and the rotation tracker suggests the least-recently-used tier when the writer plans the next chapter. Repeating the same tier within three chapters raises a **fatigue warning** — dismissable, because the writer, not the tracker, owns the ending. The suggested default wheel order is Danger → Emotion → Mystery → Cost, but any order the writer plans is legal; only repetition is flagged."
  ));
  out.push(...tbl(
    "Cliffhanger rotation matrix (tier catalogue)",
    ["Tier", "Trigger", "Mechanism (blueprint)", "Example ending from the blueprint", "Signature signals"],
    [
      ["DANGER", "Physical", "A character in immediate life-threatening jeopardy or trapped in an escalating tactical conflict", "\u201cThe floorboards gave way, and before he could reach for the ledge, darkness swallowed him whole.\u201d", "Impact verbs, falling/motion chains, sensory blackout"],
      ["EMOTION", "Interpersonal", "A sudden realization, romantic breakthrough, or devastating betrayal that upends character dynamics", "\u201cShe looked into the eyes of her protector and realized he was the one who signed her execution warrant.\u201d", "Realization verbs, relationship nouns, betrayal vocabulary"],
      ["MYSTERY", "Informational", "A hidden artifact, an inexplicable clue, or a revelation that invalidates prior beliefs", "\u201cThe DNA results were clear. He wasn't tracking his brother's killer; he was tracking himself.\u201d", "Discovery nouns, question forms, paradigm-shift phrasing"],
      ["COST", "Dilemma", "An agonizing, irreversible choice under a ticking clock, with execution deferred to the next chapter", "\u201cHe had ten seconds to choose: drop the antidote to save his sister, or inject it to save the city.\u201d", "Choice frames, countdown/timeline markers, stakes pairs"],
    ],
    [10, 12, 26, 32, 20]
  ));

  out.push(h2("6.4 Macro-Arc Architecture: the 10–15 Chapter Micro-Arc"));
  out.push(p(
    "Traditional three-act structure fails in serialized ecosystems because the gratification loop is too long. Serials instead chain **rapid, cascading micro-arks** of ten to fifteen chapters. In v3.0 an arc is a first-class object: the micro-arc planner lays out arcs over the book's target chapter count (the v2.3 targetChapters field), each arc declares its localized conflict, and the planner verifies that every arc's bridge plants at least one seed of the next arc's conflict. The structure library (section 8) ships the arc pattern as a plot structure — **Micro-Arc Cascade** — so L2 can plan an entire serial as an arc chain in one pass."
  ));
  out.push(...tbl(
    "Micro-arc phases (10–15 chapters per arc)",
    ["Phase", "Chapters", "Function (blueprint)", "Planner checks"],
    [
      ["Setup", "1–3", "Introduce a localized conflict: escaping a specific dungeon, surviving a gala, winning a street race", "Arc conflict statement present; conflict introduced by chapter 3 of the arc"],
      ["Escalation", "4–8", "Complications, rivals, and shifting parameters that raise the stakes of the localized conflict", "At least two escalation beats confirmed across the span"],
      ["Micro-climax", "9–12", "High-octane resolution of the localized conflict", "Climax beat marked; cliffhanger tiers avoided immediately before the climax beat"],
      ["Bridge", "13–15", "Handle fallout, reward characters (micro-payoff), and immediately plant the next localized conflict", "Micro-payoff beat present; at least one next-arc seed planted before arc close"],
    ],
    [14, 12, 40, 34]
  ));

  out.push(h2("6.5 Retention Engineering: Cadence, Buffer, and Community"));
  out.push(p(
    "Pacing keeps readers inside a chapter; retention mechanics keep them returning week after week. The blueprint fixes three standards and two community mechanics, all of which become local tooling with zero platform dependency."
  ));
  out.push(...tbl(
    "Retention mechanics and their v3.0 features",
    ["Mechanic", "Standard (blueprint)", "v3.0 feature"],
    [
      ["Velocity", "Minimum 2–3 updates per week; daily during promotional pushes", "Cadence tracker validates the release schedule against released chapters; streak and slip warnings appear on the serial dashboard"],
      ["Buffer", "Maintain a 4–8 chapter production buffer before and during the run to protect the schedule against burnout or disruption", "Buffer meter: confirmed-but-unreleased chapters count toward the buffer; green at 8+, amber at 4–7, red below 4; Autonomous Studio accepts a \u201cwrite to buffer\u201d target that drafts until the meter is green"],
      ["Author-note CTAs", "Capitalize on the reader's emotional residue at the bottom of each chapter with a call to action", "Author-note composer attached to every chapter export, with CTA templates (follow, vote, next-chapter teaser) the writer can edit or disable"],
      ["Native polls", "Deploy mini-polls in end-of-chapter notes (\u201cWho sent the letter? Vote now.\u201d) to gamify community investment without surrendering creative control", "Poll builder: question plus two to four options, stored per chapter; exported inside the author note on platforms that support polls, rendered as a plain-text question elsewhere"],
      ["Schedule protection", "The buffer exists to protect the schedule, not to replace it", "Release queue: the writer queues confirmed chapters against releaseSchedule dates; the queue view shows what releases when, and warns when the queue runs dry"],
    ],
    [14, 34, 52]
  ));

  out.push(h2("6.6 Serialization in Both Modes"));
  out.push(p(
    "Every serialization capability ships in **both workspaces**, with mode-appropriate surfaces. In the **Interactive Wizard**, each capability is a card the writer steps through per chapter — validate the hook, pick the cliffhanger tier with the rotation suggestion beside it, watch the anatomy meter fill as the draft confirms. In the **Autonomous Studio**, the same capabilities become run configuration: the writer sets the arc plan, the intended tier rotation, and the buffer target once, and the background run drafts serial chapters against those parameters with every validator executing on each PROPOSED chapter before it enters the review queue. Nothing serial-related is gated to one mode."
  ));
  out.push(...tbl(
    "Serialization capability mapping across workspaces",
    ["Capability", "Interactive Wizard surface", "Autonomous Studio surface"],
    [
      ["Hook validation", "Free-tier card with the 200-word scan result and churn-risk badge", "Automatic hook scan on chapters 1–5 of any new serial run; failures surface in the run report"],
      ["Anatomy meter (10/70/20)", "Per-chapter meter card with segment highlights", "Anatomy check on every PROPOSED chapter; off-envelope drafts are re-drafted once, then flagged"],
      ["Cliffhanger rotation", "Tier picker with least-recently-used suggestion and signature check", "Rotation plan set at run configuration; deviations logged per chapter"],
      ["Micro-arc planner", "Arc board: drag chapters into phases, declare conflicts and seeds", "Arc plan generated by L2 from the Micro-Arc Cascade structure, editable before the run starts"],
      ["Buffer meter", "Serial dashboard gauge", "\u201cWrite to buffer\u201d target; run pauses at the configured buffer level"],
      ["Cadence and streaks", "Release queue view against releaseSchedule", "Same tracker, plus run report lists upcoming release dates"],
      ["Polls and author notes", "Per-chapter composer with CTA templates", "Note templates applied at batch confirm; editable in the review queue"],
      ["Platform export", "Chapter export card with platform profile picker (section 6.8)", "Batch export of the release queue in a chosen platform profile"],
    ],
    [20, 40, 40]
  ));

  out.push(h2("6.7 Serialization Data Model"));
  out.push(p(
    "The format extends the existing Book and Chapter entities rather than forking them, so a serial project is a normal project with the serialization profile attached. Non-serial projects ignore every field below; defaults apply only when isSerialized is set. The full DDL, including the serial_arcs table, is in Appendix A."
  ));
  out.push(...tbl(
    "Serialization field set (extends v2.3 Book and Chapter)",
    ["Field", "Entity", "Type / default", "Purpose"],
    [
      ["isSerialized", "Book", "Boolean / false (exists in v2.3)", "Switches the project into the serial profile"],
      ["targetChapters", "Book", "Integer / 100 (exists in v2.3)", "Planned chapter count; the arc planner distributes arcs across it"],
      ["releaseSchedule", "Book", "Text / \u201cMon/Wed/Fri\u201d (exists in v2.3)", "Cadence the tracker validates against"],
      ["freeTierChapters", "Book", "Integer / 5", "Size of the free tier; paywall placement checks bound to it"],
      ["wordMin / wordMax", "Book", "Integer / 1000 / 2000", "Premium chapter envelope"],
      ["bufferTarget", "Book", "Integer / 8", "Buffer meter target (4–8 range recommended)"],
      ["exportProfile", "Book", "Text / \u201croyal_road\u201d", "Default platform export profile (section 6.8)"],
      ["arcId", "Chapter", "Integer / null", "Owning micro-arc; null only in standalone chapters"],
      ["cliffhangerTier", "Chapter", "Text / null (DANGER, EMOTION, MYSTERY, COST)", "Intended tier; verified against the signature check"],
      ["anatomyRatios", "Chapter", "JSON / null", "Measured 10/70/20 segmentation at confirm time"],
      ["hookOk", "Chapter", "Boolean / null", "Free-tier hook validation result for chapters 1–5"],
      ["ctaNote / pollJson", "Chapter", "Text / null", "Author note and poll payload exported with the chapter"],
    ],
    [18, 10, 30, 42]
  ));

  out.push(h2("6.8 Platform Export Profiles"));
  out.push(p(
    "v3.0 ships chapter-level exports as **paste-ready packages** formatted per platform, not automated platform APIs — the target platforms' terms of service make unapproved automated posting a liability, so the contract is: the application formats, the writer pastes. Each profile produces a chapter block (title, optional recap block, body paragraphs in the platform's spacing conventions, author note with CTA, and poll text where the platform supports polls) plus a plain-text clipboard bundle. Volumes bundle into EPUB 3 through the same pipeline as novels (section 11), so a completed serial graduates to retail without rework."
  ));
  out.push(...tbl(
    "Serial platform export profiles",
    ["Platform", "Model", "Export profile highlights"],
    [
      ["Royal Road", "Free serial; patronage monetization", "Clean paragraph spacing, horizontal-rule scene breaks, author note appended; no recap by default"],
      ["Wattpad", "Free serial; ad/program monetization", "Looser paragraph spacing conventions, media placeholders for inline art slots"],
      ["GoodNovel / Dreame / Webnovel", "Pay-per-chapter storefronts", "Paste-ready chapter body, optional recap header block (from the recap tool), strict paragraph discipline, author note without external links"],
      ["Tapas", "Episode model", "Episode-length chunking guidance against the 1,000–2,000 word envelope, note-style CTA"],
    ],
    [22, 26, 52]
  ));

  return out;
}

module.exports = { build };
