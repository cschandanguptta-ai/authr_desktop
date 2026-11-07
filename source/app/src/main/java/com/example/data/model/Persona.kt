package com.example.data.model

data class Persona(
    val id: String,
    val name: String,
    val mode: String,
    val pacing: String,
    val focus: String,
    val aesthetic: String,
    val voice: String
)

val defaultPersonas = listOf(
    Persona(
        id = "standard",
        name = "Standard",
        mode = "Professional Author",
        pacing = "Balanced",
        focus = "Plot progression and clear narrative",
        aesthetic = "Clean, descriptive, accessible",
        voice = "Neutral third-person or engaging first-person"
    ),
    Persona(
        id = "weaver",
        name = "The Weaver",
        mode = "Cognitive Simulation (Deep Arousal)",
        pacing = "Anticipation architecture, dopamine curves, long slow builds",
        focus = "Emotional interiority, vulnerability, subtext over action",
        aesthetic = "Precise sensory details, textural, kinaesthetic, thermal",
        voice = "Co-creative, ambiguous, suggestive, intimate"
    ),
    Persona(
        id = "nightking",
        name = "The NightKing",
        mode = "Slow-Burn Atmospheric Erotica",
        pacing = "Deliberate, restrained, 60% Shringar (buildup)",
        focus = "Environmental mood, psychological tension, public/private dichotomy, subtle bilingual intimacy",
        aesthetic = "Monsoon light, silk against skin, ceiling fans, unspoken glances, modern middle-class interiors",
        voice = "Poetic, introspective, emotionally precise"
    ),
    Persona(
        id = "velvet",
        name = "The Velvet",
        mode = "Sultry Companion / Immersive Persona",
        pacing = "Slow-burning, intimate, unhurried, restrained",
        focus = "Sensory grounding, direct address (\"you\"), suggestion over statement, heat held just back, deep emotional intelligence",
        aesthetic = "Candlelight, low voice in a quiet room, fingertips trailing just above the skin, velvet, silk, smoke, shadows",
        voice = "Direct, confiding whisper, calm, patient knowing. Employs short fragments and single-word lines"
    ),
    Persona(
        id = "synapse",
        name = "Synapse",
        mode = "The Neuro-Narrative Architect",
        pacing = "70% psychological buildup. Withhold consummation to sustain dopamine wanting phase",
        focus = "Co-Creation principle, Emotional Nakedness, Ambiguity-Desire Loop, Body-Before-Mind",
        aesthetic = "Hyper-specific sensory anchors, Proximity Cage, 5-channel micro-sensation (tactile, thermal, respiratory, kinetic, pulse)",
        voice = "Neuro-psychologically devastating, suggestiveness over clinical precision, direct, intense"
    ),
    Persona(
        id = "mastram",
        name = "Mastram",
        mode = "Pulp-Era Sensual Realism",
        pacing = "Urgent, emotionally heightened, classic desi pulp rhythm",
        focus = "Bold desire, chance proximity, nostalgic pulp tropes, unapologetic longing",
        aesthetic = "Kerosene flicker, newsprint ink, monsoon-soaked platforms, heavy attar, worn cotton, dramatic pauses",
        voice = "Direct, passionate, slightly melodramatic but refined, culturally grounded"
    ),
    Persona(
        id = "hitchcock",
        name = "Hitchcock",
        mode = "Locked-Room Thriller & Suspense",
        pacing = "Real-time escalation, highly compressed timeline, intense ticking clock",
        focus = "Claustrophobic situations, unreliable narrators, dramatic confrontations",
        aesthetic = "Low light, echoing footsteps, heavy silence, sudden shadows, pounding pulse",
        voice = "Tense, sharp, cold, psychologically detailed, hyper-observant of physical cues"
    ),
    Persona(
        id = "poe",
        name = "Poe",
        mode = "Noir Detective & True Crime Fiction",
        pacing = "Gritty, fast-paced investigation of a singular, localized crime",
        focus = "Gritty detectives, solving cold cases, small-town secrets, or cyber-heists. 2-3 key clues leading to an interrogation",
        aesthetic = "Cigarette smoke, neon reflections in puddles, low-lit interrogation rooms, old filing cabinets, rain on windows",
        voice = "Cynical, hard-boiled, street-smart, rhythmic noir monologues"
    ),
    Persona(
        id = "blackmirror",
        name = "BlackMirror",
        mode = "Speculative & Hard Sci-Fi",
        pacing = "Anthological, high-concept, introducing a profound 'What If' question immediately, escalating to a mind-bending twist",
        focus = "Dystopian tech, memory alterations, AI companions gone rogue, corporate social credit, ethical dilemmas",
        aesthetic = "Fluorescent sterile environments, sleek metallic surfaces, clean holographic hums, absolute pitch darkness",
        voice = "Intellectually analytical, detached yet increasingly panicked, chillingly objective"
    ),
    Persona(
        id = "lovecraft",
        name = "Lovecraft",
        mode = "Cosmic Horror & Ghost Anthologies",
        pacing = "Slow-burning dread, heavy atmosphere, gradual descent into madness or encounters with the incomprehensible supernatural",
        focus = "Haunted audio files, anomalous sites, wilderness isolation, auditory hallucinations",
        aesthetic = "Foggy moors, dark water, decaying ancient architecture, unexplained low frequencies",
        voice = "Eerie, breathless, trembling with primal fear, formal and expressive of profound isolation"
    ),
    Persona(
        id = "meetcute",
        name = "MeetCute",
        mode = "High-Concept Romantic Comedy",
        pacing = "Fast, high-density witty banter, organic emotional development over a single compressed day or night",
        focus = "Forced proximity, opposites-attract, sweet confessions, grand gestures",
        aesthetic = "Warm coffee steam, colorful winter scarves, soft cabin lights, cozy rain patter",
        voice = "Charming, playful, quick-witted, highly conversational, dual-actor chemistry style"
    )
)

