# 📖 Authr OS: The Autonomous Author Operating System
## Complete Operator's Manual & Creative Orchestration Guide

Welcome to **Authr OS**, an advanced autonomous professional novelist and narrative-design workstation. Unlike generic chatbots, Authr operates as a coordinated multi-agent pipeline (the **Weaver Pipeline**) that models literary creation like a premium professional publishing house. 

Whether you want to guide a story step-by-step or run full novellas hands-free in the background, this manual will guide you through mastering every aspect of the Authr suite.

---

## 🗺️ System Overview & Architectural Topology

Authr is engineered to prevent narrative drifting, plot-holes, and stylistic flatness through two primary execution modes powered by **Structured Memory Systems** and a **Dynamic LLM Orchestrator Service**:

```
                              [ USER INTERFACE ]
                               /              \
            [ Interactive Wizard ]          [ Autonomous Studio ]
           (Granular Step-by-Step)         (Hands-Free Background)
                               \              /
                          [ Weaver Agent Pipeline ]
                         /           |           \
           [ Executive ]        [ Plotter ]       [ Characterizer ]
           [ Worldbuilder ]     [ Composer ]      [ Continuity Mgr ]
                               \     |     /
                         [ Dynamic LLM Orchestrator ]
                         /           |           \
                 (Gemini API)   (Mistral API)   (Ollama Local API)
                               \     |     /
                        [ Centralized Memory Systems ]
                         - Structured Character Bible
                         - Structured World Bible
                         - Live Historical Plot Timeline
                         - Room-Backed Lorebook
                                     |
                         [ Audio Synthesizer Suite ]
                         - Multi-Engine TTS (Edge, HF, Android)
                         - Cinematic Ambient Overlays
                         - Master WAV/MP3 Audio Exporters
```

---

## 🛠️ The Workspaces: Interactive vs. Autonomous

When you open a project, you are presented with two core workspaces. You can switch between them seamlessly depending on your desired level of control. In both modes, Authr follows a strict **"AI Proposes, Human Approves & Refines"** paradigm. The AI automates all structural, stylistic, and prose drafting steps to provide high-quality, context-aware suggestions, which you can then review, edit, and approve.

---

### 1. Interactive Wizard (The Creator's Easel)
* **Best For:** Writers who want to co-create, steer outlines, review scenes line-by-line, and iterate on style.
* **Core Philosophy:** Granular, human-in-the-loop writing. Rather than letting the system run free, you guide the narrative step-by-step. The AI acts as your professional creative consultant—proposing outlines, characters, lore, and prose suggestions—while you maintain ultimate control to review, edit, and approve every single line before it's saved.

#### 🚶‍♂️ Step-by-Step Interactive Workflow:
1. **Initialize with the Project Wizard:**
   * Select a quick preset (such as **Sci-Fi** or **Noir**) from the top panel on the Story screen, or fill out custom details: **Title, Genre, Protagonist, Premise, Hook**, and **Author Persona** (e.g., *The Weaver* or *Velvet*).
   * Tap **Quick Launch Wizard** to set these parameters into the workspace state.
2. **Craft a Structured Outline (AI Suggests, You Approve):**
   * Under the **Interactive Wizard** tab, view the **AI Structured Outline Planner** card.
   * Review your premise, protagonist, and selected dramatic structure (e.g., *Hero's Journey*, *Fichtean Curve*, or *Standard 3-Act*).
   * Click **Generate Structured Outline**. The **Plot Architect** agent will construct a structured, multi-chapter blueprint including specific dramatic twists and character arcs.
   * **Human Review & Edit:** Review the generated outline in the editable text pane. Feel free to copy, tweak, or fully rewrite the outline to inject your custom creative directions. Your edits here redefine the target blueprint that the composition phase will follow.
3. **Step-by-Step Chapter Composition (AI Drafts, You Refine):**
   * Move down to the **Chapter Workspace**. Select Chapter 1.
   * Click **Draft Chapter Scene** to engage the **Scene Composer** agent. The agent will read your active character bible, world bible, lorebook definitions, and outline constraints to write high-fidelity, customized prose.
   * **Human Review & Edit:** Read the drafted text in the central editor. You can manually edit any sentence, replace words, or adjust paragraphs directly. Once you are satisfied, click save to commit and approve the final version of the scene.
4. **Targeted Styling Pass (AI Proposes Alternate Cuts):**
   * If a drafted chapter feels stylistically flat or too wordy, apply a dedicated polishing layer.
   * Provide a custom instruction (e.g., *"Make the atmosphere colder and focus heavily on shadows and ticking clocks"*), and select the **Executive Orchestrator** to perform a style pass.
   * The system will update the scene prose while preserving the plot points. Review the differences, edit as needed, and approve.
5. **Interactive Audio Proofing:**
   * Switch to the **Audio** tab.
   * Rather than exporting the whole book, select Chapter 1, set your voice provider (like **EdgeTTS** or **Cloud HuggingFace TTS**), and select a specific speaker profile.
   * Select an ambient soundscape bed (e.g., *Rain & Wind*), and click **Preview Synth**.
   * Play the audio instantly to review pacing, pronunciation, and the emotional resonance of the voiceover against the ambient background.

---

### 2. Autonomous Studio (The Automated Factory)
* **Best For:** Generating complete, structured novels or audiobooks hands-free based on highly configured creative parameters.
* **Core Philosophy:** Complete orchestration. The system uses a state machine managed via background services and Android WorkManager to auto-coordinate multiple sub-agents. However, even in this autonomous mode, **all AI-generated drafts remain completely non-destructive drafts**. They are saved to local edit panes where you can review, rewrite, run styling passes, and approve the finalized version prior to audio publishing.

#### 🚶‍♂️ Step-by-Step Autonomous Workflow:
1. **Configure Pipeline Routing & Models:**
   * Go to **Settings** and assign specialized sub-agents to specific models depending on their strengths (e.g., routing **Character Psychologist** to *Mistral* for complex dialogues, and **Continuity Manager** to *Gemini* for deep context scanning).
2. **Define Global Novel Directives:**
   * Configure the global settings: total chapters, target word count (Short, Medium, or Epic), and global tone/style constraints.
3. **Trigger the Background Thread:**
   * Open the **Autonomous Studio** tab and click **Initiate Background Draft**.
   * The app registers an incremental compilation service utilizing Android WorkManager. This ensures that even if you close the application or lock your device, the agents continue writing in the background.
4. **Monitor the Live-Feed Agent Chat:**
   * Watch the **Live Agent Communication logs** panel. You will see a real-time feed of agents collaborating:
     * **Executive Orchestrator** starts the cycle.
     * **Character Psychologist** profiles characters, adding detailed emotional profiles to the local database.
     * **World Builder** defines setting laws, registering key elements as custom lorebook entries.
     * **Plot Architect** sets the dramatic progression.
     * **Scene Composer** writes complete chapters sequentially.
     * **Continuity Manager** monitors for plot consistency, summarizing event details into the live timeline.
5. **Review & Approve the Compiled Master Novel:**
   * Once the WorkManager worker signals completion, you will receive a system notification.
   * Reopen the workspace to review the fully compiled multi-chapter book. 
   * **Human Control Gate:** You can manually edit any chapter, modify any character file in the Character Bible, add/remove Lorebook entries, or run style passes on individual chapters before triggering final mastering.
6. **Execute Master Compilation:**
   * Navigate to the **Audio** screen.
   * Click **Compile and Export Entire Novel**.
   * The system auto-synthesizes every chapter sequentially, blends the tracks with your chosen ambient soundscape, applies professional audio fades, and compiles a single master `.wav` file ready for distribution!

---

## 🤖 The Fully Automated Co-Pilot Paradigm: "AI Proposes, Human Approves & Refines"

Authr OS turns standard draft writing into an automated, interactive experience where **the AI automates 100% of the cognitive lifting, while you act as the Editor-in-Chief**. Each phase of the creative pipeline is proposed as an interactive card or text field. You can review, tweak, and explicitly approve each recommendation before it feeds downstream.

### How Each Step of the Workflow is Automated and Approved:

```
Step 1: Project Setup ─────► Step 2: Story Outlining ─────► Step 3: World & Character Bibles ─────► Step 4: Scene Prose ─────► Step 5: Continuous Styling ─────► Step 6: Audio Mix & Master
  [AI Suggests Preset]         [AI Generates Outline]         [AI Profiles Bible Sheets]          [AI Drafts Chapters]       [AI Polishes Passages]        [AI Compiles Soundscapes]
          │                              │                               │                            │                          │                           │
  [Human Tweaks & Saves]      [Human Edits & Approves]       [Human Refines attributes]       [Human Rewrites & Approves]  [Human Approves Style Pass]    [Human Audits Mix Volume]
```

1. **Step 1: Project Initiation & Configuration (AI Recommends, Human Directs)**
   * **The Automation:** Clicking a preset chip (e.g., *Noir*, *Sci-Fi*) instantly loads hand-crafted tone presets, genre-specific hooks, ambient soundscapes, and tailored Author Personas via the **Cinematic Project Wizard**.
   * **Human Approval Gate:** You review the populated inputs (Title, Genre, Hook, Persona) and can type edits or completely replace fields before clicking **Quick Launch Wizard** to save them.

2. **Step 2: Structural Outline Generation (AI Blueprinting, Human Structuring)**
   * **The Automation:** The **Plot Architect** reads your premise, protagonist, and selected archetype to generate a detailed, chapter-by-chapter outline. It populates chapter markers, specific sub-plots, and dramatic twist moments automatically.
   * **Human Approval Gate:** The generated blueprint is placed in an interactive, editable text panel. You read, re-order chapters, add custom elements, or rewrite sections. Clicking **Save / Approve Outline** locks this structure into the local database as the official blueprint.

3. **Step 3: Character & Setting Bibles (AI Synthesis, Human Refinement)**
   * **The Automation:** As soon as background or foreground drafting starts, the **Character Psychologist** and **World Builder** run scans on your premise to auto-profile characters and settings, writing highly specific records (motivations, weaknesses, environment laws, and custom lorebook keys) into your database.
   * **Human Approval Gate:** Under the story bible tabs, you can open individual character profiles or location records. If the AI suggests an attribute you don't like, simply type your correction over the text. The AI respects your modifications in all subsequent chapters.

4. **Step 4: Chapter Scene Prose Composition (AI Prose Drafting, Human Sentence-by-Sentence Control)**
   * **The Automation:** Click **Draft Chapter Scene** (Interactive Mode) or let the **Scene Composer** run sequentially (Autonomous Mode). The agent reads character sheets, lorebook keys, and previous continuity logs to write complete prose scenes automatically.
   * **Human Approval Gate:** The drafted scene is loaded directly into a rich text editor. It does NOT automatically publish or locked. You can review the suggestion, rewrite paragraphs, or correct words. The chapter is only finalized when you click the **Save / Approve Chapter** button.

5. **Step 5: Stylistic and Prose Adjustment (AI Styling Proposals, Human Selection)**
   * **The Automation:** Use the **Styling Panel** to give specific revision guidelines (e.g., *"Make Kira sound more nervous"* or *"shorten sentences for action pacing"*). The **Executive Orchestrator** automatically rewrites and polishes the draft with the selected style.
   * **Human Approval Gate:** The polished prose is presented alongside your previous draft. You compare the changes, perform any final edits, and approve the new style before committing it.

6. **Step 6: Cinematic Synthesis & Mastering (AI Sound Mixing, Human Audio Audit)**
   * **The Automation:** The **Audio Synthesizer Suite** automatically compiles chapter audio files, matches your voice models, overlays background environmental loops, and applies professional fade-in/fade-out parameters.
   * **Human Approval Gate:** Before exporting the master audiobook, use the **Preview Synth** buttons to listen to brief spoken passages. You manually adjust the volume sliders (narrator vs. background ambient level) to craft the perfect balance, ensuring maximum clarity before rendering the master track.

---

## 🧠 Dynamic LLM Orchestrator Service

Authr OS includes a state-of-the-art **Multi-Provider LLM Orchestrator** allowing you to dynamically route the pipeline's sub-agents to different models. You can combine the intelligence of remote frontier models with the speed and cost-efficiency of custom cloud or self-hosted API endpoints.

### Configuring Providers
1. Navigate to the **Settings** screen.
2. Enter your credentials for the available providers:
   * **Gemini API:** High-fidelity reasoning, long-context window, and rapid world-building.
   * **Mistral API:** Highly expressive, poetic prose generation and rich character schemas.
   * **Ollama (Cloud API):** High-speed, secure Ollama cloud API hosting or private endpoint model serving (e.g., `gemma2`, `llama3`).
3. Under **Weaver Agent Configurations**, assign each specialised sub-agent to your preferred model using the selection chips.

### The 6 Weaver Sub-Agents & Responsibilities

| Sub-Agent Icon | Agent Name | Core Responsibilities | Ideal Provider |
| :--- | :--- | :--- | :--- |
| 👑 | **Executive Orchestrator** | Orchestrates final compilation, executes polishing passes, crafts cover art prompts, and generates marketing/launch packages. | **Gemini** (Contextual mastery) |
| 📐 | **Plot Architect** | Constructs structured chapter-by-chapter outlines, plans dramatic tension peaks, and manages the live Historical Plot Timeline. | **Gemini** / **Mistral** |
| 🧠 | **Character Psychologist** | Profiles appearance, motivations, vulnerabilities, cognitive schemas, and custom character dialogue rules. | **Mistral** (Deep emotional subtext) |
| 🌍 | **World Builder** | Establishes the rules of physics, locations, and cultural/technological constraints. Generates and manages local Lorebook assets. | **Gemini** |
| ✍️ | **Scene Composer** | Drafts actual story prose, acts out individual scene goals, and applies unique style instructions based on the active Author Persona. | **Ollama** / **Mistral** / **Gemini** |
| 🛡️ | **Continuity Manager** | Compiles live narrative reports, cross-references lorebook keys, flags contradictions, and resolves logical plot-holes. | **Gemini** (Wide attention scan) |

---

## 🎨 Author Personas & Cognitive Modes

Authr OS implements **Author Personas** which alter the system instructions sent to the Scene Composer, modifying vocabulary, sentence length, sensory focus, and pacing curves:

* **🧵 The Weaver (Cognitive Simulation / Deep Arousal):**
  * Focuses on emotional interiority, vulnerability, and subtext.
  * Uses precise, tactile, kinetic, and thermal sensory details.
  * Paces stories with slow, anticipatory dopamine builds.
* **🌘 The NightKing (Slow-Burn Atmospheric):**
  * Highly restrained, deliberate pacing.
  * Focuses heavily on environmental mood, weather, physical boundaries, and psychological tension.
  * *Aesthetic cues:* Monsoon light, rustling silk, slow ceiling fans, shadows.
* **🍷 The Velvet (Sultry Companion):**
  * Unhurried, deeply intimate, sensory-grounded prose.
  * Focuses on proximity, direct addresses, and delicate physical interactions.
  * *Aesthetic cues:* Warm candlelight, a confidential low whisper in a silent room.
* **🎬 Cinematic Directors (Hitchcock, Lovecraft, Poe, BlackMirror):**
  * Emulate specific directors' narrative styles, from ticking suspense clocks to cosmic dread.

---

## 🪄 The Wizards: Instant Story Generation

Authr features two powerful setup wizards that prepare and initialize your narrative structure:

### 1. Cinematic Project Wizard (Top Panel, Story Screen)
Perfect for short-form, high-performance projects, this wizard loads ready-to-run genre configurations:

* **🚪 Thriller:** Locked-room suspense. Automatically configures tension meters, ticking clock soundscapes, and specific mystery hooks.
* **🚬 Noir:** Rain-slicked cyberpunk cities, hard-boiled dialogue, wet cigarette butts, and slow jazz ambient beds.
* **🔬 Sci-Fi:** Deep speculative what-ifs. Configures high-concept technologies, corporate assistants, and computer-drone sounds.
* **🌫️ Horror:** Cosmic dread, magnetic tape mysteries, static, and deep forest winds.
* **🧁 Cozy Mystery:** Tea-shop heists, friendly local gossip, and busy cafe atmosphere overlays.
* **☕ Rom-Com:** Witty banter, airport terminals, and charming enchanted lofi soundtracks.

### 2. Quick Novel Wizard (Popup Dialog, Project Launcher)
Designed for multi-chapter, long-form novels, this wizard walks you through setting up:
* **The Premise & Hook:** The underlying conflict or initial inciting incident.
* **The Protagonist:** Name, occupation, and key driving force.
* **Structural Archetype:**
  * *Hero's Journey:* Traditional mythological transformation.
  * *Fichtean Curve:* Rapid crisis-and-resolution spikes leading to a major climax.
  * *Standard 3-Act:* Balanced setup, confrontation, and resolution framework.
* **Chapter Length & Word Target:** Options for Short (~1000 words), Medium (~2000 words), or Epic (~4000 words).

---

## 🗄️ Structured Memory Systems (Hallucination Prevention)

To maintain narrative continuity over dozens of chapters, Authr preserves and passes structured context through dedicated SQLite database tables (powered by **Room**):

```
       [ Draft Chapter N ]
               │
               ▼
   [ Continuity Scan & Check ] ◄───► [ Centralized Lorebook Keys ]
               │
               ▼
   [ Update Structured Bibles ]
     ├── Character Bible: Profiling appearance, relationships, dialogue patterns.
     ├── World Bible: Tracking environment laws, locations, cultural facts.
     └── Historical Plot Timeline: chronological list of events for all chapters.
               │
               ▼
       [ Draft Chapter N+1 ]
```

1. **Structured Character Bible:** Profiles character attributes, psychological flaws, and evolving relationships.
2. **Structured World Bible:** Compiles environment constraints, active locations, and political or technical bounds.
3. **Historical Plot Points & Timeline:** Maintains an updated chronological record of all events that have taken place in prior chapters, avoiding forgotten characters or retconned deaths.
4. **Lorebook Continuity Panel:** Houses key term definitions (e.g., custom weapons, technologies, slang). If a word in the lorebook matches the active scene, its definition is injected as strict context into the LLM prompt.

---

## 🎵 Audiobook Soundscape & Synthesizer Suite

Once the text is compiled, turn your novel into a finished cinematic masterpiece using the **Audio Synthesizer Suite**:

* **Multi-Engine TTS (Voice Over):**
  * **EdgeTTS:** Premium, low-latency streaming voices from Microsoft Edge (highly custom accents, natural pacing).
  * **Cloud HuggingFace TTS:** Warm, expressive cloud-based neural speech synthesis with cutting-edge custom voice models (requires internet and API key).
  * **Android Native:** Highly reliable, fully local text-to-speech synthesis.
* **Ambient Soundscapes:** Select from immersive loops including *Rain & Wind*, *Deep Forest Wind*, *Busy Cafe*, and *Cyberpunk Neon Drone*.
* **Cinematic Soundscape Mixer:** Blend the speaking voice with your selected ambient soundtrack. Customize the relative volume to ensure narration is clear while ambient noise sits subtly in the background.
* **Master Audio Exporters:** Compile individual chapters into a single master WAV/MP3 track, complete with smooth fades and professionally layered mixing, ready for publishing or sharing!

---

## 🚀 Step-by-Step Walkthrough Example: Creating a Cyberpunk Noir audiobook

This walkthrough demonstrates how to build a short cyberpunk noir project called **"Vector Zero"** using both the **Interactive Wizard** and **Autonomous Studio**.

### Step 1: Configure APIs and Agent Assignments
1. Go to the **Settings** tab.
2. Provide your API keys (e.g., enter your Gemini key).
3. Under **Weaver Agent Configurations**:
   * Set **Executive Orchestrator** to `Gemini`.
   * Set **Character Psychologist** to `Mistral` (to write moody, conflicted character behaviors).
   * Set **Scene Composer** to `Gemini` (or `Ollama` if running a local llama3).
4. Save settings.

### Step 2: Initialize Project via Cinematic Project Wizard
1. Open the **Story** screen.
2. Click **Cinematic Project Wizard** to expand the top panel.
3. Tap the **Sci-Fi** preset chip.
4. The system automatically populates the Sci-Fi Soundscape (*Cyberpunk Neon Drone*), recommends *BlackMirror* as the Author Persona, and sets a custom high-concept hook:
   > *"A corporate technician installs a memory-sharing chip to relive his late wife's moments, only to uncover memories that are not hers."*
5. Customize the fields if desired, then click **Quick Launch Wizard**.

### Step 3: Run Outline Planner (Interactive Mode)
1. On the left tab, under **Interactive Wizard**, view the **AI Structured Outline Planner** card.
2. Verify the auto-populated Premise, Genre, Protagonist (*Dr. Kira Vance*), and Structure (*Fichtean Curve*).
3. Click **Generate Structured Outline**.
4. The **Plot Architect** agent compiles a detailed plot progression containing specific chapter milestones and twists.
5. Review the outline. Copy or edit it to add your custom creative inputs.

### Step 4: Initiate Background Drafting (Autonomous Mode)
1. Switch to the **Autonomous Studio** tab.
2. Tap **Initiate Background Draft**.
3. Watch the live agent feed as:
   * The **Character Psychologist** profiles *Dr. Kira Vance* and saves her description in the **Character Bible**.
   * The **World Builder** compiles technology parameters for memory chips and saves them to the **World Bible**.
   * The **Scene Composer** writes Chapter 1, adhering to the *BlackMirror* speculative style rules.
   * The **Continuity Manager** scans the prose and writes a plot summary to the **Plot Points History & Timeline**.
4. Once completed, the chapter text is displayed in the central panel.

### Step 5: Master and Mix the Audio Novel
1. Switch to the **Audio** screen.
2. Select **EdgeTTS** or **Cloud HuggingFace TTS** as the voice provider. Choose a fitting deep voice profile (e.g., *en-US-GuyNeural*).
3. Set the background ambient soundscape to **Cyberpunk Neon Drone**.
4. Click **Preview Synth** on Chapter 1 to hear the narrator speak over the atmospheric ambient drone.
5. Adjust the volume slider: Set the voice to `100%` and the neon ambient bed to a subtle `15%`.
6. Click **Compile and Export Entire Novel**.
7. Authr OS merges the tracks, applies fade-ins and fade-outs, and outputs a completed `.wav` file, saving it locally to your device!

---

## 💡 Pro-Tips for Advanced Operators

> 💡 **Tip 1: The Infinite Context Loop**
> Keep your Lorebook detailed! Whenever you write custom names, weapons, or locations, add them to the Lorebook. This acts as a permanent memory anchor, ensuring the AI never "forgets" crucial fictional parameters.

> ⚠️ **Important: Balancing the Mix**
> When mastering audiobooks, always keep ambient soundscapes between **10% and 20%** volume. If set too high, ambient tracks like thunder or cafe noise can overwhelm the spoken narrator.

> 🛠️ **Cloud/Private Endpoint Tip**
> If you have Ollama running on your cloud server or private API endpoint, configure your server settings and token in settings. You can route the heavy **Scene Composer** prose-drafting task to your custom Ollama cloud instance, saving API tokens while keeping world-building and plot outlining on Gemini!

---

*Authr OS puts a professional literary studio directly in your pocket. Go forth and craft masterpieces!*
