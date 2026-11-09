package com.example.ui.screens

import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_authr_logo),
                contentDescription = "Authr Logo",
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Authr OS: Comprehensive Application Manual",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "Welcome to the Authr OS Enterprise Documentation. Below you will find detailed guides on every system module, persona constraints, and generation pipeline rules.",
            style = MaterialTheme.typography.bodyMedium
        )

        GuideSection(
            title = "1. Author Personas & Default Narrative Arcs",
            content = "Authr OS supports distinct generative modes that drastically alter the tone and pacing. All personas implicitly include strict AI Voice Optimization constraints (controlled dialogue tags, unpronounceable character removal, rhythm balance) for smooth TTS/Audiobook conversion.\n\n" +
                      "• Standard: The neutral baseline.\n" +
                      "• Weaver: The default specialized narrative engine. Default Arc: Hero's Journey.\n" +
                      "• NightKing: Slow-burn atmospheric dark fantasy/erotica. Default Arc: Save the Cat.\n" +
                      "• Velvet: Smooth, sensual, poetic romance. Default Arc: Romance Beat Sheet.\n" +
                      "• Hitchcock: Suspense and paranoia. Default Arc: Mystery/Thriller Beats.\n" +
                      "• Poe: Gothic horror and psychological dread. Default Arc: Tragedy.\n" +
                      "• BlackMirror: Tech-dystopian, cynical twists. Default Arc: Ironic Reversal.\n" +
                      "• Lovecraft: Cosmic horror, unfathomable odds. Default Arc: Descent into Madness.\n" +
                      "• MeetCute: Lighthearted rom-com style. Default Arc: Classic Rom-Com.\n" +
                      "• Mastram: Regional, culturally grounded, pulp romance. Default Arc: Episodic Tension." +
                      "• Synapse: Cognitive romance architect, neuro-psychological arousal. Default Arc: Cognitive Romance Architecture."
        )

        GuideSection(
            title = "2. Genres & Multi-Genre Selection",
            content = "When utilizing standard genres (e.g., Sci-Fi, Fantasy, Romance), the system tailors the prose. With Multi-Genre selection, Authr OS synthesizes the setting from the primary genre, while seamlessly weaving in tropes, character dynamics, and stakes from the secondary genres. (e.g., 'Cyberpunk' + 'Cozy Mystery' = A neon-lit futuristic setting dealing with low-stakes, localized detective work)."
        )

        GuideSection(
            title = "3. Structured Plot Arcs",
            content = "You can enforce specific 5-part narrative beats that map the chapter pacing:\n\n" +
                      "• Hero's Journey: 1. Call to Adventure, 2. Threshold Crossing, 3. Trials/Abyss, 4. Transformation, 5. Return with Elixir.\n" +
                      "• Save the Cat: 1. Setup/Catalyst, 2. Fun & Games, 3. Bad Guys Close In, 4. All is Lost, 5. Finale.\n" +
                      "• Romance Beat Sheet: 1. Meet Cute, 2. The Pull, 3. The Push/Conflict, 4. Dark Moment, 5. Grand Gesture/HEA.\n" +
                      "• Humiliation to Triumph: 1. Initial Disgrace, 2. Rock Bottom, 3. The Turning Point/Training, 4. The Rematch, 5. Ultimate Validation.\n" +
                      "• Tragedy/Descent: 1. Flawed Ambition, 2. Early Success, 3. Fatal Mistake, 4. Unraveling, 5. Inevitable Doom.\n" +
                      "• Mystery/Thriller: 1. The Crime/Anomaly, 2. Investigation, 3. False Leads, 4. The Twist, 5. Terror Payoff/Resolution."
        )

        GuideSection(
            title = "4. Custom Hooks, Premises, Twists, and Extras",
            content = "• Custom Hook: Use this input alongside the AI Suggestion 'Wand' tool to lock in a high-impact opening sequence. It anchors the first chapter's cold open.\n" +
                      "• Premises & Twists: Directives that the AI must fulfill by the story's climax.\n" +
                      "• Extras: The Extras field allows injecting specific plot constraints, world-building rules, or character secrets that the Continuity Manager will enforce across the manuscript."
        )

        GuideSection(
            title = "5. Master Story Bible & Sequential Continuity",
            content = "Authr OS employs a strict architectural workflow:\n\n" +
                      "1. Bible Generation: Before drafting, the Plot Architect and Character Psychologist agents generate a pre-story 'Bible' to establish indisputable facts, lore, and arcs.\n" +
                      "2. 5-Part Sequential Generation: The Scene Composer and Continuity Manager operate sequentially, referencing the Bible during every chapter to maintain airtight plot and character continuity."
        )

        GuideSection(
            title = "6. Workspace Utility Action Buttons",
            content = "Once the manuscript is complete, use the bottom workspace actions:\n\n" +
                      "• Save Studio: Persists the active session locally into the Room DB for later retrieval.\n" +
                      "• Send to TTS: Routes the text to the Audio/TTS engine, queuing it for high-fidelity text-to-speech rendering and video generation.\n" +
                      "• To Translate: Sends the current script to the Translation Hub for instant, context-aware global localization.\n" +
                      "• To Metadata: Exports the narrative data to the SEO module to automatically generate Amazon KDP descriptions, tags, and promotional blurbs."
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun GuideSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
