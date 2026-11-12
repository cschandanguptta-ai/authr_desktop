@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.content.Context
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.components.DistractionFreeManuscriptEditor
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.data.model.Story
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
        val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Config parameters
    var heroNameVal by remember { mutableStateOf(viewModel.heroName.value) }
    val selectedGenresVal by viewModel.selectedGenres.collectAsState()
    val selectedPlotArcVal by viewModel.selectedPlotArc.collectAsState()
    val allowHybridPlotArcsVal by viewModel.allowHybridPlotArcs.collectAsState()
    val selectedAuthorPersonaVal by viewModel.selectedAuthorPersona.collectAsState()
    val durationVal by viewModel.selectedDuration.collectAsState()
    val selectedPersonaVal by viewModel.selectedPersona.collectAsState()
    val hookVal by viewModel.customHook.collectAsState()
    val extrasVal by viewModel.customExtras.collectAsState()
    val chapterLengthVal by viewModel.selectedChapterLength.collectAsState()
    val selectedNumberOfChaptersVal by viewModel.selectedNumberOfChapters.collectAsState()
    val wordTargetVal by viewModel.selectedWordTarget.collectAsState()
    val selectedFormatVal by viewModel.selectedFormat.collectAsState()

    // Story status
    val parts by viewModel.storyParts.collectAsState()
    val currentPartIdx by viewModel.currentPartIndex.collectAsState()
    val isGenerating by viewModel.isGeneratingStory.collectAsState()
    val isOptimizing by viewModel.isOptimizingStory.collectAsState()
    val genStatus by viewModel.storyGenStatus.collectAsState()
    val suggestingField by viewModel.suggestingField.collectAsState()

    // Dialogs
    var showSaveDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var storyTitleInput by remember { mutableStateOf("") }
    var isEditingOutline by remember { mutableStateOf(false) }
    val activeWorkspaceTab by viewModel.activeWorkspaceTab.collectAsState()

    LaunchedEffect(heroNameVal) {
        viewModel.heroName.value = heroNameVal
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = activeWorkspaceTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeWorkspaceTab == 0,
                    onClick = { viewModel.setActiveWorkspaceTab(0) },
                    
                    text = { Text("Interactive Wizard", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_interactive_wizard")
                )
                Tab(
                    selected = activeWorkspaceTab == 1,
                    onClick = { viewModel.setActiveWorkspaceTab(1) },
                    
                    text = { Text("Autonomous Studio", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("tab_autonomous_studio")
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (activeWorkspaceTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
        // Hero Parameters Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Creative Workspace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$selectedFormatVal Project Settings",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Live Autosave badge
                val isAutosaved by viewModel.isDraftAutosaved.collectAsState()
                val autosaveTime by viewModel.lastAutosaveTime.collectAsState()
                if (autosaveTime != null) {
                    Text(
                        text = if (isAutosaved) "Draft autosaved at $autosaveTime" else "Draft has unsaved changes...",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutosaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Auto-save active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
            }


        }

        // Project Setup Wizard Card
        var isWizardExpanded by remember { mutableStateOf(false) }
        var selectedPreset by remember { mutableStateOf<String?>(null) }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("setup_wizard_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isWizardExpanded = !isWizardExpanded }
                        .testTag("toggle_wizard_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Cinematic Project Wizard",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Set up high-performance short-form projects in seconds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isWizardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Wizard",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isWizardExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                    Text(
                        text = "Select a High-Performance Genre Target:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 2026-11-12 audit (#4 follow-up): the Sultry preset
                    // auto-selects the ADULT Velvet persona — it stays hidden
                    // behind the 18+ acknowledgment like every other adult
                    // entry point.
                    val presetAdultAck = remember { com.example.utils.PersonaResolver.adultAcknowledged(context) }
                    val presets = buildList {
                        if (presetAdultAck) add("Sultry" to Triple("Titillation", "Rain & Wind", "Velvet"))
                        add("Thriller" to Triple("Locked-Room Thriller", "Dark Dungeon Drone", "Hitchcock"))
                        add("Noir" to Triple("Noir Detective", "Rain & Wind", "Poe"))
                        add("Sci-Fi" to Triple("Speculative Sci-Fi", "Cyberpunk Neon Drone", "BlackMirror"))
                        add("Horror" to Triple("Cosmic Horror", "Deep Forest Wind", "Lovecraft"))
                        add("Cozy Mystery" to Triple("Cozy Mystery", "Busy Cafe Atmosphere", "Standard"))
                        add("Rom-Com" to Triple("Meet-Cute Rom-Com", "Enchanted Lofi", "MeetCute"))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (label, data) ->
                            val (genre, soundscape, persona) = data
                            val isSelected = selectedPreset == label
                            val cardBg = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                            val borderCol = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }

                            Card(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        selectedPreset = label
                                        // Automatically load corresponding soundscape setting and narrative structure guidelines
                                        viewModel.selectedGenres.value = setOf(genre)
                                        viewModel.selectedAmbientTheme.value = soundscape
                                        viewModel.selectedPersona.value = persona
                                        
                                        // Set specific recommended settings and structural guidelines
                                        when (label) {
                                            "Thriller" -> {
                                                viewModel.selectedDuration.value = "60 min"
                                                viewModel.customHook.value = "Six strangers awaken at a high-stakes escape room dinner party, realizing one of them is a killer holding a detonator."
                                                viewModel.customExtras.value = "A ticking physical clock mechanism sounds louder with each passing minute; an unreliable host delivers demands through an intercom."
                                            }
                                            "Noir" -> {
                                                viewModel.selectedDuration.value = "45 min"
                                                viewModel.customHook.value = "A hard-boiled detective gets a visit from a client who claims she was murdered yesterday, holding a ticket to her own funeral."
                                                viewModel.customExtras.value = "The investigation centers around a single wet cigarette casing and a code scratched into a public telephone booth."
                                            }
                                            "Sci-Fi" -> {
                                                viewModel.selectedDuration.value = "60 min"
                                                viewModel.customHook.value = "A corporate technician installs a memory-sharing chip to relive his late wife's moments, only to uncover memories that are not hers."
                                                viewModel.customExtras.value = "A rogue AI assistant continually optimizes the client's memory stream, subtly altering historical truth to increase happiness indexes."
                                            }
                                            "Horror" -> {
                                                viewModel.selectedDuration.value = "90 min"
                                                viewModel.customHook.value = "An audio archivist restores an old magnetic tape from 1974, only to find the speaker is warning them by name."
                                                viewModel.customExtras.value = "Unexplained auditory hallucinations; the descent into madness as the archivist discovers their own voice already recorded on the B-side."
                                            }
                                            "Cozy Mystery" -> {
                                                viewModel.selectedDuration.value = "45 min"
                                                viewModel.customHook.value = "An eccentric tea-shop owner in a peaceful seaside village discovers a secret recipe book containing clues to a historical heist."
                                                viewModel.customExtras.value = "Friendly local gossip, a warm tea-serving ambiance, and a mischievous tabby cat that keeps knocking over key clues."
                                            }
                                            "Rom-Com" -> {
                                                viewModel.selectedDuration.value = "30 min"
                                                viewModel.customHook.value = "Two rival travel bloggers are assigned adjacent seats on an overseas flight, but a blizzard grounds them in a remote terminal."
                                                viewModel.customExtras.value = "They share warm coffee and split a single blanket, engaging in fast-paced witty banter before realizing their flight is being boarded."
                                            }
                                        }
                                    }
                                    .testTag("wizard_preset_$label"),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val emoji = when (label) {
                                        "Thriller" -> "🚪"
                                        "Noir" -> "🚬"
                                        "Sci-Fi" -> "🔬"
                                        "Horror" -> "🌫️"
                                        "Cozy Mystery" -> "🧁"
                                        "Rom-Com" -> "☕"
                                        else -> "📚"
                                    }
                                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (label) {
                                            "Sultry" -> "Intimate Slow-Burn"
                                            "Thriller" -> "Suspense"
                                            "Noir" -> "Investigate"
                                            "Sci-Fi" -> "What If"
                                            "Horror" -> "Eerie Ambient"
                                            "Cozy Mystery" -> "Tea & Clues"
                                            "Rom-Com" -> "Charming Dialogue"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    selectedPreset?.let { preset ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📖 $preset Narrative Structure & Guidelines",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Preset Loaded") }
                                    )
                                }

                                val details = when (preset) {
                                    "Thriller" -> listOf(
                                        "🚀 Focus" to "High-stakes claustrophobic puzzle. Minimal subplots, maximum real-time tension.",
                                        "⏳ Recommended" to "60-minute single commuting window listen (Approx. 7,800 words).",
                                        "🚪 0–10 mins" to "Establish urgent claustrophobic hook.",
                                        "⚙️ 10–50 mins" to "Ticking-clock frantic race against time or psychological game.",
                                        "💥 50–60 mins" to "The Climax: Confrontation with the killer or the truth.",
                                        "🎭 60–70 mins" to "A jaw-dropping final twist recontextualizing the entire story."
                                    )
                                    "Noir" -> listOf(
                                        "🚀 Focus" to "Singular gritty investigation. One detective, 2-3 clues, atmospheric interrogation.",
                                        "⏳ Recommended" to "45-minute True Crime podcast style runtime (Approx. 5,800 words).",
                                        "🕵️ 0–15 mins" to "The crime is established. First moody investigation scene.",
                                        "⚙️ 15–60 mins" to "Gritty clues followed under rain-soaked windows.",
                                        "💥 60–80 mins" to "High-stakes dramatic interrogation.",
                                        "🚬 80–90 mins" to "Hard-boiled cynical resolution with absolute closure."
                                    )
                                    "Sci-Fi" -> listOf(
                                        "🚀 Focus" to "Black Mirror style hard tech dilemma. Introduce a profound 'What if?' immediately.",
                                        "⏳ Recommended" to "60-minute mind-bending speculative journey (Approx. 7,800 words).",
                                        "🔬 0–15 mins" to "Introduce one powerful sci-fi tech or social credit system.",
                                        "⚙️ 15–60 mins" to "Rapidly escalate the physical and psychological consequences.",
                                        "💥 60–80 mins" to "The reality check: A deep corporate/personal crisis.",
                                        "👁️ 80–90 mins" to "A chilling, twist ending that leaves the listener pondering."
                                    )
                                    "Horror" -> listOf(
                                        "🚀 Focus" to "Atmosphere and audio design driven terror. Slower pace to build profound dread.",
                                        "⏳ Recommended" to "90-minute bedtime or isolation listening session (Approx. 11,500 words).",
                                        "🌫️ 0–15 mins" to "A normal event decays. Mysterious anomalous occurrence / tape found.",
                                        "⚙️ 15–70 mins" to "Grueling struggle to survive in the wilderness, haunted by sounds.",
                                        "💥 70–90 mins" to "Descent into auditory madness and final terrifying encounter."
                                    )
                                    "Cozy Mystery" -> listOf(
                                        "🚀 Focus" to "Lighthearted, character-driven puzzle solving in a warm community setting.",
                                        "⏳ Recommended" to "45-minute cozy listening session (Approx. 5,800 words).",
                                        "🧁 0–15 mins" to "Introduce the eccentric protagonist, peaceful village, and low-stakes mystery.",
                                        "🔍 15–60 mins" to "Witty tea-shop chatter and lighthearted clue-hunting around town.",
                                        "🐈 60–80 mins" to "Clever deduction with the help of a local patron or animal companion.",
                                        "🍵 80–90 mins" to "Warm, highly satisfying resolution over cupcakes and fresh tea."
                                    )
                                    "Rom-Com" -> listOf(
                                        "🚀 Focus" to "High-density witty banter and character chemistry. Pure dialogue focus.",
                                        "⏳ Recommended" to "30-minute hyper-specific forced-proximity window (Approx. 3,900 words).",
                                        "☕ 0–15 mins" to "Intimate proximity setup (stuck in a snowed-in terminal, shared seat).",
                                        "⚙️ 15–60 mins" to "Rapid-fire banter. Sharing deep, intimate secrets they share with no one else.",
                                        "💥 60–80 mins" to "Departures conflict or sudden reality check.",
                                        "❤️ 80–90 mins" to "A satisfying, warm, complete feeling of closure."
                                    )
                                    else -> emptyList()
                                }

                                details.forEach { (timeframe, desc) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "$timeframe:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(90.dp),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚡ Automations Applied: Soundscape set to \"${viewModel.selectedAmbientTheme.value}\" & Persona set to \"/${viewModel.selectedPersona.value.lowercase()}\".",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Story Import Accordion Deck
        var isImportExpanded by remember { mutableStateOf(false) }
        var importOption by remember { mutableStateOf(0) } // 0: Paste Raw Text, 1: Choose .txt File
        var rawTextTitle by remember { mutableStateOf("") }
        var rawTextContent by remember { mutableStateOf("") }
        var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileName by remember { mutableStateOf("") }
        var fileCustomTitle by remember { mutableStateOf("") }
        var isImporting by remember { mutableStateOf(false) }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                selectedFileUri = uri
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            selectedFileName = cursor.getString(index)
                            if (fileCustomTitle.isEmpty()) {
                                val nameWithoutExt = if (selectedFileName.contains(".")) {
                                    selectedFileName.substring(0, selectedFileName.lastIndexOf('.'))
                                } else {
                                    selectedFileName
                                }
                                fileCustomTitle = nameWithoutExt
                            }
                        }
                    }
                } finally {
                    cursor?.close()
                }
                if (selectedFileName.isEmpty()) {
                    selectedFileName = uri.lastPathSegment ?: "selected_file.txt"
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("story_import_accordion_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isImportExpanded = !isImportExpanded }
                        .testTag("toggle_import_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Story Import & Ingestion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Ingest plain-text manuscripts block-by-block on paragraph breaks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isImportExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Import Section",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                if (isImportExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))

                    TabRow(
                        selectedTabIndex = importOption,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Tab(
                            selected = importOption == 0,
                            onClick = { importOption = 0 },
                            text = { Text("Paste Text", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("import_tab_paste")
                        )
                        Tab(
                            selected = importOption == 1,
                            onClick = { importOption = 1 },
                            text = { Text("Upload File", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("import_tab_file")
                        )
                    }

                    if (importOption == 0) {
                        OutlinedTextField(
                            value = rawTextTitle,
                            onValueChange = { rawTextTitle = it },
                            label = { Text("Manuscript Title") },
                            placeholder = { Text("e.g., My Awesome Novel Draft") },
                            modifier = Modifier.fillMaxWidth().testTag("import_raw_title_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = rawTextContent,
                            onValueChange = { rawTextContent = it },
                            label = { Text("Paste Plain Text Manuscript") },
                            placeholder = { Text("Paste your screenplay, novel draft, or notes here...\n\nParagraph breaks (double newlines) will define chapter slices.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .testTag("import_raw_content_input"),
                            maxLines = 15
                        )

                        Button(
                            onClick = {
                                if (rawTextTitle.trim().isEmpty()) {
                                    Toast.makeText(context, "Please provide a title for the manuscript", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (rawTextContent.trim().isEmpty()) {
                                    Toast.makeText(context, "Please paste some manuscript content to ingest", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isImporting = true
                                viewModel.importStoryFromRawText(
                                    title = rawTextTitle,
                                    text = rawTextContent,
                                    onSuccess = { story ->
                                        isImporting = false
                                        viewModel.loadStoryIntoParams(story)
                                        Toast.makeText(context, "Ingested successfully! Loaded \"${story.title}\" into creative workspace", Toast.LENGTH_LONG).show()
                                        rawTextContent = ""
                                        rawTextTitle = ""
                                    },
                                    onFailure = { err ->
                                        isImporting = false
                                        Toast.makeText(context, "Failed to ingest manuscript: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth().testTag("import_raw_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Slice & Ingest Raw Text", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("text/plain") },
                                modifier = Modifier.weight(1f).testTag("import_file_select_button")
                            ) {
                                Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose .txt File")
                            }

                            if (selectedFileUri != null) {
                                Text(
                                    text = selectedFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Text(
                                    text = "No file selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (selectedFileUri != null) {
                            OutlinedTextField(
                                value = fileCustomTitle,
                                onValueChange = { fileCustomTitle = it },
                                label = { Text("Custom Title (Optional)") },
                                placeholder = { Text("Defaults to file name") },
                                modifier = Modifier.fillMaxWidth().testTag("import_file_title_input"),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    val uri = selectedFileUri ?: return@Button
                                    isImporting = true
                                    viewModel.importStoryFromTxtUri(
                                        context = context,
                                        uri = uri,
                                        customTitle = fileCustomTitle.ifBlank { null },
                                        onSuccess = { story ->
                                            isImporting = false
                                            viewModel.loadStoryIntoParams(story)
                                            Toast.makeText(context, "File ingested successfully! Loaded \"${story.title}\" into creative workspace", Toast.LENGTH_LONG).show()
                                            selectedFileUri = null
                                            selectedFileName = ""
                                            fileCustomTitle = ""
                                        },
                                        onFailure = { err ->
                                            isImporting = false
                                            Toast.makeText(context, "Failed to ingest file: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                enabled = !isImporting,
                                modifier = Modifier.fillMaxWidth().testTag("import_file_submit_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                } else {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Process & Ingest .txt File", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Config Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Project Format Selection
                Text("Project Format", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Novel", "Short Story", "Audio Drama", "Screenplay", "Non-fiction").forEach { format ->
                        val selected = selectedFormatVal == format
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectedFormat.value = format },
                            label = { Text(format) },
                            modifier = Modifier.testTag("format_chip_$format")
                        )
                    }
                }

                // Protagonist Name
                OutlinedTextField(
                    value = heroNameVal,
                    onValueChange = { heroNameVal = it },
                    label = { Text("Protagonist Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_name_input"),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )

                // Story Duration Target
                val isAudioDurationEnabled = selectedFormatVal != "Novel" && selectedFormatVal != "Short Story"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Audiobook Duration", style = MaterialTheme.typography.labelMedium)
                    if (!isAudioDurationEnabled) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Prose Mode (Auto-managed via Word Count)", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("30 min", "45 min", "60 min", "90 min").forEach { dur ->
                        val selected = durationVal == dur && isAudioDurationEnabled
                        FilterChip(
                            selected = selected,
                            enabled = isAudioDurationEnabled,
                            onClick = { viewModel.selectedDuration.value = dur },
                            label = { Text(dur) },
                            modifier = Modifier.testTag("duration_chip_$dur")
                        )
                    }
                }

                // Author Style Persona
                Text("Author Style Persona", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 2026-11-12 audit (#1/#4 follow-up): the chip row renders the
                    // registry (gated by the 18+ acknowledgment) instead of a
                    // hardcoded display-name list — adult personas stay hidden here.
                    val personaChips = remember(context) {
                        com.example.utils.PersonaResolver.availableStoryPersonas(context)
                    }
                    personaChips.forEach { persona ->
                        val selected = selectedPersonaVal == persona.name
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectedPersona.value = persona.name },
                            label = { Text("/${persona.id} (${persona.mode.substringBefore(" (")})") },
                            modifier = Modifier.testTag("persona_chip_${persona.id}")
                        )
                    }
                }

                if (selectedPersonaVal != "Standard") {
                    // The detail card reads the SAME registry card the prompts use
                    // (audit #1: no more parallel hardcoded copies).
                    val activePersona = remember(selectedPersonaVal, context) {
                        com.example.utils.PersonaResolver.resolveByName(context, selectedPersonaVal)
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Active Style: ${activePersona.name} (${activePersona.mode})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            val details = listOf(
                                "✨ Mode" to activePersona.mode,
                                "⏳ Pacing" to activePersona.pacing,
                                "👁️ Focus" to activePersona.focus,
                                "🌧️ Aesthetic" to activePersona.aesthetic,
                                "🗣️ Voice" to activePersona.voice
                            )
                            details.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$label: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Chapter Count
                Text("Number of Chapters: $selectedNumberOfChaptersVal", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = selectedNumberOfChaptersVal.toFloat(),
                    onValueChange = { 
                        viewModel.selectedNumberOfChapters.value = it.toInt()
                        viewModel.updateNumberOfChapters(it.toInt())
                    },
                    valueRange = 5f..50f,
                    steps = 45
                )
                Text(
                    text = "AI will generate a distinct outline and content plan for each of these $selectedNumberOfChaptersVal chapters based on your provided instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )


                // Chapter Length
                Text("Chapter Length", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Short (~1,500 words)", "Medium (~3,000 words)", "Long (~5,000 words)").forEach { length ->
                        val selected = chapterLengthVal == length
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectedChapterLength.value = length },
                            label = { Text(length) }
                        )
                    }
                }

                // Word Target
                Text("Word Target", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Novella (~30,000 words)", "Novel (~60,000 words)", "Epic (~100,000 words)").forEach { target ->
                        val selected = wordTargetVal == target
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectedWordTarget.value = target },
                            label = { Text(target) }
                        )
                    }
                }

                // Genres Focus Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Genre Focus Selection", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (selectedGenresVal.isNotEmpty()) {
                        Text(
                            text = "${selectedGenresVal.size} Selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                var genreTabState by remember { mutableIntStateOf(0) }
                // 2026-11-12 audit (#4): the erotica genre browser (and the adult
                // persona presets) stay hidden until the 18+ acknowledgment in
                // Settings; PersonaResolver also gates every generation path.
                val adultAck = remember { com.example.utils.PersonaResolver.adultAcknowledged(context) }
                val eroticaCategories = remember {
                    mapOf(
                        "Core Romance" to listOf(
                            "Erotic Romance", "Contemporary Erotica", "New Adult (NA) Erotica",
                            "Billionaire / Elite Erotica", "Harem / Reverse Harem", "Menage / Polyamorous"
                        ),
                        "Speculative Fiction" to listOf(
                            "Paranormal Erotica", "Monster Erotica", "Erotic Fantasy / Romantasy",
                            "Sci-Fi / Alien Erotica", "Cyberpunk / Dystopian Erotica"
                        ),
                        "Power Dynamics" to listOf(
                            "BDSM Erotica", "Femdom", "Maledom", "Workplace / Taboo Power Dynamics"
                        ),
                        "Gritty & Realistic" to listOf(
                            "Dark Erotica", "Mafia / Crime Erotica", "MC (Motorcycle Club) Erotica", "Historical Erotica"
                        ),
                        "Literary & Specialized" to listOf(
                            "Literary Erotica", "LGBTQ+ / Queer Erotica", "Taboo / Forbidden Fiction"
                        )
                    )
                }
                var selectedEroticaCategory by remember { mutableStateOf("Core Romance") }

                TabRow(
                    selectedTabIndex = genreTabState,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (genreTabState < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[genreTabState]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = genreTabState == 0,
                        onClick = { genreTabState = 0 },
                        text = { Text("Standard Genres", style = MaterialTheme.typography.labelSmall) }
                    )
                    if (adultAck) {
                        Tab(
                            selected = genreTabState == 1,
                            onClick = { genreTabState = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌶️", style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Special Erotica", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (genreTabState == 0) {
                    val availableGenres = listOf(
                        "Tragic Love", "Cheating Revenge", "Rebirth",
                        "Billionaire Hidden Identity", "System Power-Up", "Comedy", "Titillation",
                        "Locked-Room Thriller", "Noir Detective", "Speculative Sci-Fi", "Cosmic Horror", "Meet-Cute Rom-Com", "Cozy Mystery",
                        "Cognitive Romance", "Neuro-Romance", "Psychological Romance"
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableGenres.forEach { genre ->
                            val isSelected = selectedGenresVal.contains(genre)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val current = selectedGenresVal.toMutableSet()
                                    if (isSelected) {
                                        if (current.size > 1) current.remove(genre)
                                    } else {
                                        current.add(genre)
                                    }
                                    viewModel.selectedGenres.value = current
                                },
                                label = { Text(genre) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("genre_chip_$genre")
                            )
                        }
                    }
                } else if (adultAck) {
                    // Erotica Section (18+ acknowledged only)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Selection
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            eroticaCategories.keys.forEach { category ->
                                val isCatSelected = selectedEroticaCategory == category
                                FilterChip(
                                    selected = isCatSelected,
                                    onClick = { selectedEroticaCategory = category },
                                    label = { Text(category) },
                                    modifier = Modifier.testTag("erotica_category_$category")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subgenres
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            eroticaCategories[selectedEroticaCategory]?.forEach { subgenre ->
                                val isSelected = selectedGenresVal.contains(subgenre)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val current = selectedGenresVal.toMutableSet()
                                        if (isSelected) {
                                            if (current.size > 1) current.remove(subgenre)
                                        } else {
                                            current.add(subgenre)
                                        }
                                        viewModel.selectedGenres.value = current
                                    },
                                    label = { Text(subgenre) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("erotica_subgenre_$subgenre")
                                )
                            }
                        }
                    }
                }

                // Structured Plot Arc
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("Structured Plot Arc", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                val plotArcs = listOf(
                    "Automatic (Recommended)", "Hero's Journey", "Three-Act Structure", "Save the Cat",
                    "Seven-Point Story Structure", "Story Circle", "Five-Act Shakespearean Structure",
                    "Fichtean Curve", "Snowflake Method", "Kishōtenketsu", "Dan Harmon Story Circle",
                    "Romance Beat Structure", "Mystery Investigation Structure", "Thriller Escalation Structure",
                    "Epic Fantasy Campaign Structure", "Horror Descent Structure", "Literary Character Arc",
                    "Nonlinear Narrative", "Experimental Narrative", "Cognitive Romance Architecture"
                )
                
                var expandedPlotArc by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedPlotArcVal,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedPlotArc = !expandedPlotArc }) {
                                Icon(if (expandedPlotArc) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expandedPlotArc = true }
                    )
                    DropdownMenu(
                        expanded = expandedPlotArc,
                        onDismissRequest = { expandedPlotArc = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        plotArcs.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    viewModel.selectedPlotArc.value = selectionOption
                                    expandedPlotArc = false
                                }
                            )
                        }
                    }
                }

                // Advanced Option: Allow Hybrid Plot Arcs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allowHybridPlotArcsVal,
                        onCheckedChange = { viewModel.allowHybridPlotArcs.value = it }
                    )
                    Text("Allow Hybrid Plot Arcs (e.g. Hero's Journey + Save the Cat)", style = MaterialTheme.typography.bodySmall)
                }
                
                // The author-integration roles apply to the Weaver-family voices
                // (adult personas — the row is only reachable behind the 18+ gate).
                if (selectedPersonaVal == "Weaver") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Author Persona Integration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    // 2026-11-12 audit (#3/#6): options come from the persona
                    // registry and store ids (the old display-name strings
                    // matched no card and silently degraded to Standard);
                    // adult presets are hidden until the 18+ acknowledgment.
                    val context = LocalContext.current
                    val authorPersonas = remember(context) {
                        com.example.utils.PersonaResolver.availableAuthorPersonas(context)
                    }

                    var expandedAuthorPersona by remember { mutableStateOf(false) }
                    // Display the persona NAME, never the raw registry id the
                    // picker now stores (audit #3 follow-up).
                    val selectedAuthorPersonaDisplay = remember(selectedAuthorPersonaVal, context) {
                        com.example.utils.PersonaResolver.resolve(context, selectedAuthorPersonaVal).name
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedAuthorPersonaDisplay,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedAuthorPersona = !expandedAuthorPersona }) {
                                    Icon(if (expandedAuthorPersona) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { expandedAuthorPersona = true }
                        )
                        DropdownMenu(
                            expanded = expandedAuthorPersona,
                            onDismissRequest = { expandedAuthorPersona = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Standard (Balanced, Plot-Driven)") },
                                onClick = {
                                    viewModel.selectedAuthorPersona.value = com.example.engine.PersonaRegistry.STANDARD_ID
                                    expandedAuthorPersona = false
                                }
                            )
                            authorPersonas.forEach { persona ->
                                DropdownMenuItem(
                                    text = { Text("${persona.name} — ${persona.mode}") },
                                    onClick = {
                                        viewModel.selectedAuthorPersona.value = persona.id
                                        expandedAuthorPersona = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Custom Hook suggestions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hookVal,
                onValueChange = { viewModel.customHook.value = it },
                label = { Text("Opening Hook / Premise") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("hook_input"),
                placeholder = { Text("Enter or suggest opening hook") }
            )

            Button(
                onClick = { viewModel.suggestHook() },
                modifier = Modifier.testTag("suggest_hook_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (suggestingField == "hook") {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI")
            }
        }

        // Custom Narrative Extras/Twists suggestions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = extrasVal,
                onValueChange = { viewModel.customExtras.value = it },
                label = { Text("Narrative Twist / Extra") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("extras_input"),
                placeholder = { Text("Enter or suggest unique twists") }
            )

            Button(
                onClick = { viewModel.suggestExtras() },
                modifier = Modifier.testTag("suggest_extras_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (suggestingField == "extras") {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI")
            }
        }

        var showVoiceTips by remember { mutableStateOf(false) }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // AI Voice Optimization Tips Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVoiceTips = !showVoiceTips }
                        .testTag("toggle_voice_tips_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Voice Optimization Guide",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = if (showVoiceTips) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Guide",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (showVoiceTips) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Standard AI voices lack spontaneous emotional range. Write emotional cues into the text to guide performance:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val tips = listOf(
                            "🎭 Write Emotion, Don't Expect Acting" to "Instead of '\"I hate you,\" she said' (sounds flat), use '\"I hate you,\" she whispered, her voice breaking with pain.'",
                            "❄️ Describe Intensity, Don't Shout" to "NEVER use ALL CAPS in dialogue. Use '\"Get out,\" she said, her voice cold with fury.'",
                            "✍️ Use Emotional Dialogue Tags" to "Guide voice inflections using tags like murmured, quietly, choked out, said through gritted teeth, gasped, hissed.",
                            "⏳ Pacing via Line Breaks" to "Use frequent paragraph/line breaks. Every break gives the AI narrator a natural, dramatic pause.",
                            "📉 Avoid Sudden Tonal Shifts" to "Build suspense or emotion gradually so the voice transition is smooth and natural."
                        )
                        tips.forEach { (title, description) ->
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Core AI Structured Outline Planner ---
        val wizardOutlineVal by viewModel.wizardOutline.collectAsState()
        val isGeneratingOutlineVal by viewModel.isGeneratingWizardOutline.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth().testTag("ai_outline_planner_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Structured Outline Planner",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (isGeneratingOutlineVal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        SuggestionChip(
                            onClick = {
                                viewModel.generateWizardOutline(
                                    premise = hookVal,
                                    format = selectedFormatVal,
                                    genre = selectedGenresVal.joinToString(", "),
                                    protagonist = heroNameVal,
                                    structure = selectedPlotArcVal
                                )
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text("Generate Outline")
                                }
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("generate_wizard_outline_button")
                        )
                    }
                }

                Text(
                    text = "Generate a comprehensive blueprint based on your protagonist, genres, opening hook/premise, and narrative structure. Includes chapter titles, plot points, and character arcs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (wizardOutlineVal.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Story Outline & Character Arcs",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            IconButton(
                                onClick = {
                                    if (isEditingOutline) {
                                        isEditingOutline = false
                                    } else {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Story Outline", wizardOutlineVal)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Outline copied!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(if (isEditingOutline) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = if (isEditingOutline) "Save Outline" else "Copy Outline", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { isEditingOutline = !isEditingOutline },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Outline", modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (isEditingOutline) {
                                OutlinedTextField(
                                    value = wizardOutlineVal,
                                    onValueChange = { viewModel.updateOutline(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            } else {
                                Text(
                                    text = wizardOutlineVal,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Part Navigation progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Project Chapters/Parts Sequential Writing (${parts.size} Chapters)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // "Review Manuscript" quick link icon
            IconButton(
                onClick = { showReviewDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RateReview,
                    contentDescription = "Review Draft Chapters",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in parts.indices) {
                val isSelected = currentPartIdx == i
                val isFilled = parts.getOrNull(i)?.isNotEmpty() == true
                val bubbleColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFilled -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(bubbleColor)
                        .clickable { viewModel.selectStoryPart(i) }
                        .testTag("part_bubble_$i"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${i + 1}",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Dynamic chapter action chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuggestionChip(
                onClick = { viewModel.addNewChapter() },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Add Chapter")
                    }
                },
                modifier = Modifier.testTag("add_chapter_chip")
            )
            
            SuggestionChip(
                onClick = { viewModel.deleteChapterAt(currentPartIdx) },
                enabled = parts.size > 1,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Delete Chapter")
                    }
                },
                modifier = Modifier.testTag("delete_chapter_chip")
            )

            Spacer(modifier = Modifier.weight(1f))

            FilledTonalButton(
                onClick = { showReviewDialog = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp).testTag("open_review_manuscript_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Review Manuscript", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Selected Chapter Editor Box
        val selectedPartContent = parts[currentPartIdx]
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PART ${currentPartIdx + 1} Content Editor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedPartContent.isNotEmpty()) {
                        val wordCount = selectedPartContent.split("\\s+".toRegex()).count { it.isNotEmpty() }
                        Text(
                            text = "$wordCount words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (selectedPartContent.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Part ${currentPartIdx + 1} is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.generateStoryPart(currentPartIdx) },
                                modifier = Modifier.testTag("generate_part_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate with AI")
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedPartContent,
                        onValueChange = { viewModel.updateStoryPartLocally(currentPartIdx, it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("part_editor_input"),
                        placeholder = { Text("Edit generated text here") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        Button(
                            onClick = { viewModel.optimizeStoryPartForVoice(currentPartIdx) },
                            enabled = !isOptimizing && !isGenerating,
                            modifier = Modifier.testTag("optimize_voice_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Optimize for Voice")
                        }

                        Button(
                            onClick = { viewModel.generateStoryPart(currentPartIdx) },
                            enabled = !isOptimizing && !isGenerating,
                            modifier = Modifier.testTag("regenerate_part_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                    }
                }

                Text(
                    text = "Status: $genStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // General Workspace Utilities
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    storyTitleInput = viewModel.getDefaultFilenameFromHook("txt")
                    showSaveDialog = true
                },
                enabled = parts.any { it.isNotEmpty() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_story_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Studio")
            }

            Button(
                onClick = {
                    val full = parts.filter { it.isNotEmpty() }.joinToString("\n\n")
                    viewModel.audioInputText.value = full
                    viewModel.selectTab(1) // Go to Audio Conversion tab
                },
                enabled = parts.any { it.isNotEmpty() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_audio_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Headset, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send to TTS")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val full = parts.filter { it.isNotEmpty() }.joinToString("\n\n")
                    viewModel.transInputText.value = full
                    viewModel.selectTab(2) // Go to Translate tab
                },
                enabled = parts.any { it.isNotEmpty() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_translate_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("To Translate")
            }

            Button(
                onClick = {
                    val full = parts.filter { it.isNotEmpty() }.joinToString("\n\n")
                    viewModel.metaInputText.value = full
                    viewModel.selectTab(3) // Go to Metadata tab
                },
                enabled = parts.any { it.isNotEmpty() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_meta_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Sell, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("To Metadata")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Manuscript Draft Review Dialog
    if (showReviewDialog) {
        Dialog(onDismissRequest = { showReviewDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Title and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Manuscript Draft Review",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        IconButton(onClick = { showReviewDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Stats Summary card (Styled like Final Compiled Manuscript statistics card)
                    val totalWords = parts.joinToString(" ").split("\\s+".toRegex()).count { it.isNotEmpty() }
                    val completedCount = parts.count { it.isNotEmpty() }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Chapters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${parts.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Box(modifier = Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$completedCount / ${parts.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Box(modifier = Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Total Words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$totalWords", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Scrollable list of chapters
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(parts.size) { idx ->
                                val chText = parts[idx]
                                val isChCompleted = chText.isNotEmpty()
                                val chWordCount = if (isChCompleted) chText.split("\\s+".toRegex()).count { it.isNotEmpty() } else 0
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChCompleted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Chapter ${idx + 1}",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = if (isChCompleted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = RoundedCornerShape(50)
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isChCompleted) "Drafted" else "Empty",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isChCompleted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (isChCompleted) {
                                                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("Chapter ${idx + 1}", chText)
                                                            clipboardManager.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Chapter ${idx + 1} copied!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = isChCompleted,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Chapter",
                                                        tint = if (isChCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        viewModel.selectStoryPart(idx)
                                                        showReviewDialog = false
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Chapter",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isChCompleted) {
                                            Text(
                                                text = if (chText.length > 250) chText.take(250) + "..." else chText,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                                )
                                            )
                                            Text(
                                                text = "$chWordCount words",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            )
                                        } else {
                                            Text(
                                                text = "No draft written yet for this chapter. Switch back to the workspace to generate or write.",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dialog actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val fullManuscript = parts.mapIndexed { idx, text ->
                                    if (text.isNotEmpty()) "### Chapter ${idx + 1}\n\n$text" else ""
                                }.filter { it.isNotEmpty() }.joinToString("\n\n---\n\n")
                                
                                if (fullManuscript.isNotEmpty()) {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Full Manuscript", fullManuscript)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Full manuscript copied!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Manuscript is empty!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = completedCount > 0,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Manuscript", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                viewModel.saveCurrentStoryToDb(storyTitleInput.ifEmpty { "Manuscript Draft" })
                                Toast.makeText(context, "Manuscript saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = completedCount > 0,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Draft", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }

    // Save Story Dialog
    if (showSaveDialog) {
        Dialog(onDismissRequest = { showSaveDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Save Audio Novel Draft", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = storyTitleInput,
                        onValueChange = { storyTitleInput = it },
                        label = { Text("Studio Project Name") },
                        placeholder = { Text("My Audio Novel - Arjun") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveCurrentStoryToDb(storyTitleInput)
                                showSaveDialog = false
                            },
                            modifier = Modifier.testTag("confirm_save_story_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }


                } else {
                    AutonomousStudioView(viewModel = viewModel)
                }
            }
        }
        
    // Floating Scroll Navigation Panel
        if (scrollState.value > 100 || scrollState.maxValue > 500) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (scrollState.value > 150) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("scroll_to_top_button")
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                    }
                }
                
                if (scrollState.value < scrollState.maxValue - 150) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("scroll_to_editor_button")
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom")
                    }
                }
            }
        }


    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
private fun AutonomousStudioView(viewModel: StudioViewModel) {
    val allBooks by viewModel.allBooks.collectAsState()
    val selectedBookId by viewModel.activeBookId.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    
    if (selectedBookId == null) {
        // Books List Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Hero banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Autonomous Book Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showGuide = !showGuide }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(if (showGuide) "Hide Guide" else "View Guide", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(
                        "Configure, launch, and compile complete books. The AI engine automatically executes all 4 phases of authorship in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Expandable In-App User & Workflow Guide
            if (showGuide) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📖 Authr OS Quick Start Guide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text("1. Dual Workspaces Explained", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "• Interactive Wizard (Left Pane): Perfect for hands-on, granular crafting. Generate an outline, then write and preview chapter scenes step-by-step.\n" +
                            "• Autonomous Studio (This Pane): Hands-free background compilation. Choose a theme and watch the multi-agent system write entire novels for you.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text("2. The 4 Autonomous Production Phases", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "• Phase 1: Blueprint & Continuity. The AI designs a detailed chapter outline and auto-generates core Lorebook rules.\n" +
                            "• Phase 2: Prose Drafting. The AI writes each chapter successively in the background. (View live progress under the Chapters tab!).\n" +
                            "• Phase 3: Editorial Polish. The agent re-reads drafts to enhance flow, pacing, and sensory prose.\n" +
                            "• Phase 4: Compilation. All polished segments are assembled into a single, comprehensive manuscript ready for copy-pasting.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text("3. Guaranteeing Narrative Consistency", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "• Click on any active book's project workspace, go to the 'Outline & Lore' tab, and add custom key terms (e.g. character bios, weapon rules, locations) to your Lorebook. The AI agent consults these rules before drafting each scene.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text("4. Interactive Structured Outlines", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "• In the Interactive Wizard, use the 'AI Structured Outline Planner' to construct comprehensive blueprints before writing. The AI will output structured chapter titles, specific plot points, and detailed character arcs based on your custom premise, protagonist, and selected narrative structure.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Launcher buttons
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 2
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1.2f).testTag("quick_launch_wizard_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Quick Launch Wizard", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedButton(
                    onClick = { viewModel.startAutonomousDraft(context) },
                    modifier = Modifier.weight(1f).testTag("launch_new_book_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use Left Preset", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Your Autonomous Library", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            if (allBooks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                        Text("No full-length books yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Click 'Quick Launch Wizard' to configure and write your first background book run in seconds!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                allBooks.forEach { book ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectBook(book.id) }
                            .testTag("book_card_${book.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${book.format} • ${book.genre}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(onClick = { viewModel.deleteBook(book.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Book", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            // Phase Progress indicator
                            val (phaseLabel, progressVal) = when (book.currentPhase) {
                                "Outlining", "WorldBuilding" -> "Phase 1/4: Outlining & Worldbuilding..." to 0.25f
                                "Drafting" -> "Phase 2/4: Chapter-by-Chapter Drafting..." to 0.50f
                                "Polishing" -> "Phase 3/4: Editorial Polish & Refinement..." to 0.75f
                                "Compiling" -> "Phase 4/4: Manuscript Compilation..." to 0.90f
                                "Completed" -> "Phase 4/4: Completed & Polished! 🎉" to 1.0f
                                "Paused" -> "Drafting Paused ⏸️" to (book.completionPercentage / 100f).coerceAtLeast(0.1f)
                                "Stopped" -> "Drafting Stopped ⏹️" to (book.completionPercentage / 100f).coerceAtLeast(0.1f)
                                "Failed" -> "Drafting Failed ❌" to (book.completionPercentage / 100f).coerceAtLeast(0.1f)
                                else -> "Queueing..." to 0.1f
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(phaseLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${(progressVal * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
progress = { progressVal },
modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (book.currentPhase == "Completed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.selectBook(book.id) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Open Workspace")
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Detailed Project Workspace for the selected book
        val book = allBooks.find { it.id == selectedBookId }
        if (book == null) {
            viewModel.selectBook(null)
        } else {
            LaunchedEffect(selectedBookId) {
                viewModel.initializeBookContext(selectedBookId!!)
            }
            BookWorkspaceView(book = book, onBack = { viewModel.selectBook(null) }, viewModel = viewModel)
        }
    }

    if (showCreateDialog) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        val draftTitle by viewModel.quickDraftTitle.collectAsState()
        val draftGenres by viewModel.quickDraftGenres.collectAsState()
        val draftProtagonist by viewModel.quickDraftProtagonist.collectAsState()
        val draftHook by viewModel.quickDraftHook.collectAsState()
        val draftFormat by viewModel.quickDraftFormat.collectAsState()
        val draftWordTarget by viewModel.quickDraftWordTarget.collectAsState()
        val draftPersona by viewModel.quickDraftPersona.collectAsState()
        val draftStructure by viewModel.quickDraftStructure.collectAsState()
        val draftChapterLength by viewModel.quickDraftChapterLength.collectAsState()
        val draftAutoExport by viewModel.quickDraftAutoExport.collectAsState()
        val draftPreviousBookId by viewModel.quickDraftPreviousBookId.collectAsState()
        val draftExtras by viewModel.quickDraftExtras.collectAsState()
        val draftIsSerialized by viewModel.quickDraftIsSerialized.collectAsState()
        val draftTargetChapters by viewModel.quickDraftTargetChapters.collectAsState()
        val draftReleaseSchedule by viewModel.quickDraftReleaseSchedule.collectAsState()

        Dialog(
            onDismissRequest = { showCreateDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(12.dp)
                ) {
                    // Title Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Quick Novel Wizard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                viewModel.quickDraftTitle.value = ""
                                viewModel.quickDraftGenres.value = emptySet()
                                viewModel.quickDraftProtagonist.value = ""
                                viewModel.quickDraftHook.value = ""
                                viewModel.quickDraftExtras.value = ""
                                viewModel.quickDraftPreviousBookId.value = null
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Wizard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showCreateDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // PRESET TEMPLATES GRID/ROW
                        Text("Instant Creative Presets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Preset 1: Fantasy
                            ElevatedCard(
                                onClick = {
                                    viewModel.quickDraftTitle.value = "The Whispering Sigil"
                                    viewModel.quickDraftGenres.value = setOf("Fantasy")
                                    viewModel.quickDraftProtagonist.value = "Eldrin the Scribe"
                                    viewModel.quickDraftHook.value = "A young scribe discovers a forgotten spell-sigil that commands ancient stone defenders, igniting a war with the neighboring steel kingdom."
                                    viewModel.quickDraftFormat.value = "Novel"
                                    viewModel.quickDraftWordTarget.value = 50000
                                    viewModel.quickDraftPersona.value = "weaver"
                                    viewModel.quickDraftStructure.value = "Hero's Journey"
                                    viewModel.quickDraftChapterLength.value = "Medium (~2000 words)"
                                },
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(6.dp).width(130.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🧝 Fantasy Quest", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Sigil Golems, Scribes", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }

                            // Preset 2: Sci-Fi
                            ElevatedCard(
                                onClick = {
                                    viewModel.quickDraftTitle.value = "Vector Zero"
                                    viewModel.quickDraftGenres.value = setOf("Sci-Fi")
                                    viewModel.quickDraftProtagonist.value = "Dr. Kira Vance"
                                    viewModel.quickDraftHook.value = "A lone astrophysicist on a deep-space outpost receives a repeating distress signal originating from inside her own station's server core."
                                    viewModel.quickDraftFormat.value = "Novella"
                                    viewModel.quickDraftWordTarget.value = 30000
                                    viewModel.quickDraftPersona.value = "standard"
                                    viewModel.quickDraftStructure.value = "Fichtean Curve"
                                    viewModel.quickDraftChapterLength.value = "Short (~1000 words)"
                                },
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(6.dp).width(130.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🚀 Sci-Fi Mystery", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    Text("Deep space distress", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }

                            // Preset 3: Noir Thriller
                            ElevatedCard(
                                onClick = {
                                    viewModel.quickDraftTitle.value = "The Neon Rain"
                                    viewModel.quickDraftGenres.value = setOf("Noir", "Thriller")
                                    viewModel.quickDraftProtagonist.value = "Silas Cole"
                                    viewModel.quickDraftHook.value = "A cynical detective in a rain-drenched cyberpunk megalopolis is hired to find a synthetic heiress, only to realize her memory files have been loaded into his own brain."
                                    viewModel.quickDraftFormat.value = "Novel"
                                    viewModel.quickDraftWordTarget.value = 50000
                                    viewModel.quickDraftPersona.value = "nightking"
                                    viewModel.quickDraftStructure.value = "Standard 3-Act"
                                    viewModel.quickDraftChapterLength.value = "Long (~4000 words)"
                                },
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(6.dp).width(130.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🕵️ Cyberpunk Noir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    Text("Neon memories, rain", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // PREVIOUS BOOK SELECTION (Optional Sequence)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Continue Series (Based on Previous Book)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            var expandedPrevBook by remember { mutableStateOf(false) }
                            Box {
                                OutlinedTextField(
                                    value = allBooks.find { it.id == draftPreviousBookId }?.title ?: "None (Standalone Project)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Previous Book") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandedPrevBook = true })
                                DropdownMenu(
                                    expanded = expandedPrevBook,
                                    onDismissRequest = { expandedPrevBook = false },
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None (Standalone Project)") },
                                        onClick = { 
                                            viewModel.quickDraftPreviousBookId.value = null
                                            expandedPrevBook = false 
                                        }
                                    )
                                    allBooks.forEach { book ->
                                        DropdownMenuItem(
                                            text = { Text(book.title) },
                                            onClick = { 
                                                viewModel.quickDraftPreviousBookId.value = book.id
                                                expandedPrevBook = false 
                                            }
                                        )
                                    }
                                }
                            }

                            // Added suggest sequel idea button
                            if (draftPreviousBookId != null) {
                                var isSuggesting by remember { mutableStateOf(false) }
                                val prevBook = allBooks.find { it.id == draftPreviousBookId }
                                Button(
                                    onClick = {
                                        if (prevBook != null) {
                                            isSuggesting = true
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val prompt = """
                                                        You are a creative writing assistant. Based on this previous book, suggest a title, a hook, and a protagonist for the NEXT book in the series.
                                                        Previous Book Title: ${prevBook.title}
                                                        Previous Book Synopsis: ${prevBook.synopsis}
                                                        Previous Book Protagonist: ${draftProtagonist.takeIf{it.isNotBlank()} ?: "Main character from previous book"}
                                                        
                                                        Respond ONLY in JSON format:
                                                        {
                                                            "title": "Sequel Title",
                                                            "protagonist": "Protagonist Name",
                                                            "hook": "A compelling 1-2 sentence hook for the new plot."
                                                        }
                                                    """.trimIndent()
                                                    val response = com.example.data.api.LlmOrchestrator.callAgent(context, com.example.data.api.LlmOrchestrator.Agent.PLOT, prompt)
                                                    val startIndex = response.indexOf('{')
                                                    val endIndex = response.lastIndexOf('}')
                                                    if (startIndex != -1 && endIndex != -1) {
                                                        val jsonStr = response.substring(startIndex, endIndex + 1)
                                                        val json = org.json.JSONObject(jsonStr)
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            viewModel.quickDraftTitle.value = json.optString("title", "")
                                                            viewModel.quickDraftProtagonist.value = json.optString("protagonist", "")
                                                            viewModel.quickDraftHook.value = json.optString("hook", "")
                                                        }
                                                    }
                                                } catch(e: Exception) {
                                                    // Ignore
                                                } finally {
                                                    isSuggesting = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSuggesting,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isSuggesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Brainstorming Sequel...")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Suggest Sequel Premise")
                                    }
                                }
                            }
                        }

                        // TITLE INPUT
                        OutlinedTextField(
                            value = draftTitle,
                            onValueChange = { viewModel.quickDraftTitle.value = it },
                            label = { Text("Book Title") },
                            placeholder = { Text("e.g. Chronicles of Eldoria") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_title_input")
                        )

                        // GENRE CHIPS SELECTOR + CUSTOM TEXT INPUT
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Genre Focus Selection", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                if (draftGenres.isNotEmpty()) {
                                    Text(
                                        text = "${draftGenres.size} Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            var wizardGenreTabState by remember { mutableIntStateOf(0) }
                            // 18+ gate (2026-11-12 audit #4): same acknowledgment as the main path.
                            val wizardAdultAck = remember { com.example.utils.PersonaResolver.adultAcknowledged(context) }
                            val eroticaCategories = remember {
                                mapOf(
                                    "Core Romance" to listOf(
                                        "Erotic Romance", "Contemporary Erotica", "New Adult (NA) Erotica",
                                        "Billionaire / Elite Erotica", "Harem / Reverse Harem", "Menage / Polyamorous"
                                    ),
                                    "Speculative Fiction" to listOf(
                                        "Paranormal Erotica", "Monster Erotica", "Erotic Fantasy / Romantasy",
                                        "Sci-Fi / Alien Erotica", "Cyberpunk / Dystopian Erotica"
                                    ),
                                    "Power Dynamics" to listOf(
                                        "BDSM Erotica", "Femdom", "Maledom", "Workplace / Taboo Power Dynamics"
                                    ),
                                    "Gritty & Realistic" to listOf(
                                        "Dark Erotica", "Mafia / Crime Erotica", "MC (Motorcycle Club) Erotica", "Historical Erotica"
                                    ),
                                    "Literary & Specialized" to listOf(
                                        "Literary Erotica", "LGBTQ+ / Queer Erotica", "Taboo / Forbidden Fiction"
                                    )
                                )
                            }
                            var wizardSelectedEroticaCategory by remember { mutableStateOf("Core Romance") }

                            TabRow(
                                selectedTabIndex = wizardGenreTabState,
                                containerColor = Color.Transparent,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (wizardGenreTabState < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[wizardGenreTabState]),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Tab(
                                    selected = wizardGenreTabState == 0,
                                    onClick = { wizardGenreTabState = 0 },
                                    text = { Text("Standard Genres", style = MaterialTheme.typography.labelSmall) }
                                )
                                if (wizardAdultAck) {
                                    Tab(
                                        selected = wizardGenreTabState == 1,
                                        onClick = { wizardGenreTabState = 1 },
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🌶️", style = MaterialTheme.typography.labelSmall)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Special Erotica", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (wizardGenreTabState == 0) {
                                val availableGenres = listOf(
                                    "Tragic Love", "Cheating Revenge", "Rebirth",
                                    "Billionaire Hidden Identity", "System Power-Up", "Comedy", "Titillation",
                                    "Locked-Room Thriller", "Noir Detective", "Speculative Sci-Fi", "Cosmic Horror", "Meet-Cute Rom-Com", "Cozy Mystery",
                                    "Cognitive Romance", "Neuro-Romance", "Psychological Romance", "Fantasy", "Adventure"
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableGenres.forEach { genreOption ->
                                        val isSelected = draftGenres.contains(genreOption)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { 
                                                val current = draftGenres.toMutableSet()
                                                if (isSelected) current.remove(genreOption) else current.add(genreOption)
                                                viewModel.quickDraftGenres.value = current
                                            },
                                            label = { Text(genreOption) }
                                        )
                                    }
                                }
                            } else if (wizardAdultAck) {
                                // Erotica Section (18+ acknowledged only)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Category Selection
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        eroticaCategories.keys.forEach { category ->
                                            val isCatSelected = wizardSelectedEroticaCategory == category
                                            FilterChip(
                                                selected = isCatSelected,
                                                onClick = { wizardSelectedEroticaCategory = category },
                                                label = { Text(category) },
                                                modifier = Modifier.testTag("wizard_erotica_category_$category")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Subgenres
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        eroticaCategories[wizardSelectedEroticaCategory]?.forEach { subgenre ->
                                            val isSelected = draftGenres.contains(subgenre)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val current = draftGenres.toMutableSet()
                                                    if (isSelected) current.remove(subgenre) else current.add(subgenre)
                                                    viewModel.quickDraftGenres.value = current
                                                },
                                                label = { Text(subgenre) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null,
                                                modifier = Modifier.testTag("wizard_erotica_subgenre_$subgenre")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // PROTAGONIST NAME
                        OutlinedTextField(
                            value = draftProtagonist,
                            onValueChange = { viewModel.quickDraftProtagonist.value = it },
                            label = { Text("Key Protagonist / Hero") },
                            placeholder = { Text("e.g. Eldrin") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_protagonist_input")
                        )

                        // HOOK / CORE PREMISE
                        var isSuggestingHook by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = draftHook,
                            onValueChange = { viewModel.quickDraftHook.value = it },
                            label = { Text("Core Hook / Premise") },
                            placeholder = { Text("e.g. A young scribe discovers a spell...") },
                            trailingIcon = {
                                if (isSuggestingHook) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = {
                                            isSuggestingHook = true
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val prompt = "You are a creative writing assistant. Suggest a compelling 1-2 sentence core hook or premise for a book. " + 
                                                        (if (draftTitle.isNotBlank()) "Title: $draftTitle. " else "") + 
                                                        (if (draftGenres.isNotEmpty()) "Genres: ${draftGenres.joinToString()}. " else "") + 
                                                        (if (draftProtagonist.isNotBlank()) "Protagonist: $draftProtagonist. " else "") + 
                                                        "Respond with ONLY the hook."
                                                    val response = com.example.data.api.LlmOrchestrator.callAgent(context, com.example.data.api.LlmOrchestrator.Agent.PLOT, prompt)
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        viewModel.quickDraftHook.value = response.trim().removeSurrounding("\"")
                                                    }
                                                } catch(e: Exception) {
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        Toast.makeText(context, "Failed to suggest hook", Toast.LENGTH_SHORT).show()
                                                    }
                                                } finally {
                                                    isSuggestingHook = false
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Suggest Hook", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4
                        )

                        var isSuggestingExtras by remember { mutableStateOf(false) }
                        // AI SUGGESTED EXTRAS
                        OutlinedTextField(
                            value = draftExtras,
                            onValueChange = { viewModel.quickDraftExtras.value = it },
                            label = { Text("AI Suggested Extras (Interactive Wizard)") },
                            placeholder = { Text("e.g. Tone, additional constraints, subplots...") },
                            trailingIcon = {
                                if (isSuggestingExtras) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = {
                                            isSuggestingExtras = true
                                            scope.launch {
                                                viewModel.quickDraftExtras.value = viewModel.suggestExtras(draftGenres.joinToString(","), draftHook)
                                                isSuggestingExtras = false
                                            }
                                        }
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.AutoAwesome, contentDescription = "Suggest Extras", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4
                        )

                        // AUTHOR PERSONA
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Author Persona & Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            // 2026-11-12 audit (#1/#2/#4): the picker renders the
                            // persona registry (built-ins + the container's plugin
                            // personas the plan §8 promised) instead of a hardcoded
                            // list, and adult presets stay hidden until the 18+
                            // acknowledgment in Settings.
                            val pickerContext = LocalContext.current
                            val personaOptions = remember(pickerContext) {
                                com.example.utils.PersonaResolver.availableStoryPersonas(pickerContext)
                                    .map { p ->
                                        p.id to "${p.name} (${p.mode})\n${p.focus}"
                                    }
                            }

                            personaOptions.forEach { (id, desc) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.quickDraftPersona.value = id }.padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = (draftPersona == id),
                                        onClick = { viewModel.quickDraftPersona.value = id }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (draftPersona == id) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // TECHNICAL SPECS
                        Text("Technical Target Specs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 2) {
                            // Format
                            var expandedFormat by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = draftFormat,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Format") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandedFormat = true })
                                DropdownMenu(expanded = expandedFormat, onDismissRequest = { expandedFormat = false }) {
                                    listOf("Novel", "Novella", "Short Story", "Screenplay", "Audio Drama").forEach { fmt ->
                                        DropdownMenuItem(text = { Text(fmt) }, onClick = { 
                                            viewModel.quickDraftFormat.value = fmt
                                            viewModel.quickDraftWordTarget.value = when(fmt) {
                                                "Novel" -> 80000
                                                "Novella" -> 30000
                                                "Short Story" -> 7500
                                                "Screenplay" -> 20000
                                                "Audio Drama" -> 10000
                                                else -> 50000
                                            }
                                            expandedFormat = false 
                                        })
                                    }
                                }
                            }

                            // Structure
                            var expandedStructure by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = draftStructure,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Structure") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandedStructure = true })
                                DropdownMenu(expanded = expandedStructure, onDismissRequest = { expandedStructure = false }) {
                                    listOf(
                                        "Automatic (Recommended)", "Hero's Journey", "Standard 3-Act", "Three-Act Structure", "Save the Cat",
                                        "Seven-Point Story Structure", "Story Circle", "Five-Act Shakespearean Structure",
                                        "Fichtean Curve", "Snowflake Method", "Kishōtenketsu", "Dan Harmon Story Circle",
                                        "Romance Beat Structure", "Mystery Investigation Structure", "Thriller Escalation Structure",
                                        "Epic Fantasy Campaign Structure", "Horror Descent Structure", "Literary Character Arc",
                                        "Nonlinear Narrative", "Experimental Narrative", "Cognitive Romance Architecture", "In Medias Res"
                                    ).forEach { st ->
                                        DropdownMenuItem(text = { Text(st) }, onClick = { viewModel.quickDraftStructure.value = st; expandedStructure = false })
                                    }
                                }
                            }
                        }

                        // Word count & Chapter length
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 2) {
                            var targetString by remember { mutableStateOf(draftWordTarget.toString()) }
                            
                            LaunchedEffect(draftWordTarget) {
                                if (draftWordTarget != targetString.toIntOrNull()) {
                                    targetString = draftWordTarget.toString()
                                }
                            }
                            
                            OutlinedTextField(
                                value = targetString,
                                onValueChange = { 
                                    targetString = it
                                    it.toIntOrNull()?.let { num -> viewModel.quickDraftWordTarget.value = num }
                                },
                                label = { Text("Target Words") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            
                            var expandedLength by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = draftChapterLength,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Chapter Length") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandedLength = true })
                                DropdownMenu(expanded = expandedLength, onDismissRequest = { expandedLength = false }) {
                                    listOf("Short (~1000 words)", "Medium (~2000 words)", "Long (~4000 words)", "Epic (~6000 words)").forEach { len ->
                                        DropdownMenuItem(text = { Text(len) }, onClick = { viewModel.quickDraftChapterLength.value = len; expandedLength = false })
                                    }
                                }
                            }
                        }

                        // Auto Export Toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = draftAutoExport, onCheckedChange = { viewModel.quickDraftAutoExport.value = it })
                            Text("Auto-compile EPUB/PDF upon completion", style = MaterialTheme.typography.bodyMedium)
                        }

                    }

                    // Bottom Actions
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (draftTitle.isBlank()) {
                                    Toast.makeText(context, "Please enter a book title", Toast.LENGTH_SHORT).show()
                                } else {
                                    val synopsisFull = "Protagonist: $draftProtagonist. Hook: $draftHook" + if (draftExtras.isNotBlank()) ". Extras: $draftExtras" else ""
                                    viewModel.launchCustomAutonomousBook(
                                        context = context,
                                        title = draftTitle,
                                        genre = draftGenres.joinToString(", "),
                                        synopsis = synopsisFull,
                                        personaId = draftPersona,
                                        format = draftFormat,
                                        targetWordCount = draftWordTarget,
                                        narrativeStructure = draftStructure,
                                        chapterLength = draftChapterLength,
                                        autoExport = draftAutoExport,
                                        previousBookId = draftPreviousBookId,
                                        isSerialized = draftIsSerialized,
                                        targetChapters = draftTargetChapters,
                                        releaseSchedule = draftReleaseSchedule
                                    )
                                    showCreateDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch AI Writer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookWorkspaceView(
    book: Book,
    onBack: () -> Unit,
    viewModel: StudioViewModel
) {
    val context = LocalContext.current
        val scope = rememberCoroutineScope()
    val chapters by viewModel.getChaptersForBook(book.id).collectAsState(initial = emptyList())
    val lorebook by viewModel.getLorebookForBook(book.id).collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf(0) } // 0: Bible & Blueprint, 1: Production Queue, 2: Final Manuscript, 3: Weaver OS Logs, 4: Publishing & Marketing

    // Inline dialog for reading chapters
    var readingChapterId by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Back Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${book.format} • ${book.genre}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            val draftingStates by viewModel.draftingStates.collectAsState()
            val state = draftingStates[book.id] ?: when (book.currentPhase) {
                "Paused" -> "Paused"
                "Stopped", "Completed", "Failed", "" -> "Stopped"
                else -> "Running"
            }

            if (book.currentPhase != "Completed" && book.currentPhase != "Compiling") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state == "Running") {
                        Button(
                            onClick = { viewModel.pauseAutonomousDraft(context, book.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("drafting_pause_btn")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.resumeAutonomousDraft(context, book.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("drafting_resume_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (state == "Running" || state == "Paused") {
                        Button(
                            onClick = { viewModel.stopAutonomousDraft(context, book.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("drafting_stop_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Stepper for active phase status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val phases = listOf("Outline", "Drafting", "Polishing", "Manuscript")
            val currentIdx = when (book.currentPhase) {
                "Outlining" -> 0
                "Drafting" -> 1
                "Polishing" -> 2
                "Compiling" -> 3
                "Completed" -> 4
                else -> 0
            }

            phases.forEachIndexed { idx, name ->
                val isDone = currentIdx > idx
                val isActive = currentIdx == idx
                val color = when {
                    isDone -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDone) "✓ $name" else name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDone || isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Granular Chapter Live Progress Chip
        val totalChapters = chapters.size
        val completedChaptersCount = chapters.count { it.status == "Completed" || it.status == "Polished" || it.status.startsWith("Completed") }
        val currentActiveChapter = chapters.find { it.status.contains("Drafting") || it.status.contains("In Progress") }
        val totalManuscriptWords = chapters.sumOf { ch -> ch.content.split("\\s+".toRegex()).count { it.isNotBlank() } }

        if (totalChapters > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentActiveChapter != null) {
                                "✍️ Chapter ${currentActiveChapter.chapterNumber} of $totalChapters: Drafting ('${currentActiveChapter.title}')"
                            } else if (completedChaptersCount == totalChapters && totalChapters > 0) {
                                "🎉 All $totalChapters Chapters Drafted & Polished!"
                            } else {
                                "📊 Production: $completedChaptersCount of $totalChapters Chapters Completed"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manuscript Total: ${String.format("%,d", totalManuscriptWords)} words • Phase: ${book.currentPhase.ifEmpty { "Ready" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "${if (totalChapters > 0) (completedChaptersCount * 100 / totalChapters) else 0}%",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        // Sub-tabs for book workspace sections using ScrollableTabRow to prevent crowding
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 16.dp
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("📖 Bible & Blueprint", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("🎬 Chapters (${chapters.size})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("📜 Final Manuscript", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("🤖 Weaver OS Logs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("🎨 Publishing & Marketing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("🌍 Global Release & Audiobooks", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) })
        }

        // Content panel
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> {
                    OutlineBibleTab(book = book, lorebook = lorebook, viewModel = viewModel)
                }
                1 -> {
                    // Chapter Drafts (Phases 2 & 3)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Chapter-by-Chapter Production Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Click on any completed/polished chapter to read the AI text.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                        if (chapters.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator()
                                    Text("Generating chapter nodes...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            chapters.forEach { chap ->
                                val (statusIcon, statusColor, statusLabel) = when (chap.status) {
                                    "Drafting" -> Triple(Icons.Default.Edit, MaterialTheme.colorScheme.primary, "Drafting ✍️")
                                    "Completed" -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondary, "Completed (Draft) ✅")
                                    "Polished" -> Triple(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiary, "Editorial Polished ✨")
                                    "Failed" -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Failed ❌")
                                    else -> Triple(Icons.Default.Pending, MaterialTheme.colorScheme.outline, "Pending Queue")
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = chap.content.isNotEmpty()) { readingChapterId = chap.id }
                                        .testTag("chapter_card_${chap.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (chap.status == "Drafting") MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Chapter ${chap.chapterNumber}: ${chap.title}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                if (book.isSerialized && chap.status == "Completed") {
                                                    IconButton(onClick = { viewModel.analyzeCliffhanger(chap) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(androidx.compose.material.icons.Icons.Default.Analytics, contentDescription = "Analyze Cliffhanger", modifier = Modifier.size(16.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(onClick = { viewModel.exportEpisode(context, chap, book) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Export Episode", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            Text(chap.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                                                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (chap.content.isNotEmpty()) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Final Compiled Manuscript (Phase 4)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (book.currentPhase != "Completed" && book.compiledManuscript.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text("Writing, polishing, and compiling is currently in progress...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Current Phase: ${book.currentPhase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                    Text("This tab will load automatically once compiling (Phase 4) starts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            // Display statistics and final compiled book text
                            val totalWords = book.compiledManuscript.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            val estReadTime = totalWords / 200 // ~200 WPM
                            val context = androidx.compose.ui.platform.LocalContext.current

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Words", style = MaterialTheme.typography.labelSmall)
                                        Text("$totalWords", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Est. Read Time", style = MaterialTheme.typography.labelSmall)
                                        Text("$estReadTime min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Chapters", style = MaterialTheme.typography.labelSmall)
                                        Text("${chapters.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Compiled Book Text", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Compiled Manuscript", book.compiledManuscript)
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy")
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                                                val safeTitle = book.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                                val exportDir = java.io.File(docsDir, safeTitle)
                                                exportDir.mkdirs()
                                                val file = java.io.File(exportDir, "${safeTitle}_Full_Manuscript.txt")
                                                file.writeText(book.compiledManuscript)
                                                android.widget.Toast.makeText(context, "Exported to Documents/$safeTitle/", android.widget.Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Export Failed", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export Documents")
                                    }
                                    Button(
                                        onClick = {
                                            val zipPath = com.example.managers.EpubManager.exportBookToZipPackage(
                                                context = context,
                                                bookTitle = book.title,
                                                manuscript = book.compiledManuscript,
                                                bible = book.storyBible,
                                                outline = book.outline
                                            )
                                            if (zipPath != null) {
                                                android.widget.Toast.makeText(context, "Exported compressed ZIP to Downloads/Weaver_Studio/", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Export Failed", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export .ZIP")
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = book.compiledManuscript,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Weaver OS Live Logs & Analytics
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Weaver 2.0 Autonomous Analytics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 2) {
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("Production Progress", style = MaterialTheme.typography.bodySmall)
                                    Text("${book.completionPercentage}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("Readability Level", style = MaterialTheme.typography.bodySmall)
                                    Text(book.readabilityScore, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        
                        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 2) {
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("Emotional Intensity", style = MaterialTheme.typography.bodySmall)
                                    Text(book.emotionalIntensity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("Pacing Heatmap", style = MaterialTheme.typography.bodySmall)
                                    Text(book.pacingHeatmap, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f))) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Continuity & Plot Hole Warnings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Text(book.continuityWarnings, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Text("Collaborative Agent Terminal (Live)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(6.dp).fillMaxSize()) {
                                Text("Sub-Agent Discussion & Worklogs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (book.agentLogs.isBlank()) {
                                        Text("Initializing multi-agent pipeline logs. Awaiting action launch...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    } else {
                                        book.agentLogs.lines().filter { it.isNotBlank() }.forEach { line ->
                                            val color = when {
                                                line.contains("[Executive Orchestrator]") -> MaterialTheme.colorScheme.primary
                                                line.contains("[Plot Architect]") -> MaterialTheme.colorScheme.secondary
                                                line.contains("[Character Psychologist]") -> MaterialTheme.colorScheme.tertiary
                                                line.contains("[World Builder]") -> MaterialTheme.colorScheme.error
                                                line.contains("[Continuity Manager]") -> MaterialTheme.colorScheme.primary
                                                line.contains("[Dialogue Writer]") -> MaterialTheme.colorScheme.secondary
                                                line.contains("[Emotion Engine]") -> MaterialTheme.colorScheme.tertiary
                                                line.contains("[Style Editor]") -> MaterialTheme.colorScheme.primary
                                                line.contains("[Publishing Agent]") -> MaterialTheme.colorScheme.secondary
                                                line.contains("[Marketing Agent]") -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = color
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // Publishing & Marketing Studio
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Story Memory State Tracker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (book.memoryState.isEmpty()) {
                                    Text("Story memory is empty. Weaver 2.0 Continuity Manager updates this persistent state after every scene draft to track rules, locations, character health, and foreshadowing promises.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    Text(book.memoryState, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }
                        }

                        Text("Cover Design Proposal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        val context = LocalContext.current
                        var coverValidationResult by remember { mutableStateOf<com.example.managers.ImageValidationResult?>(null) }
                        val coverPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null && book.id > 0) {
                                val saved = com.example.managers.EpubManager.saveCoverImage(context, book.id, uri)
                                if (saved) {
                                    coverValidationResult = com.example.managers.EpubManager.validateCoverImage(context, book.id)
                                    Toast.makeText(context, "Cover uploaded successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save cover image.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (book.coverPrompt.isEmpty()) {
                                    Text("Cover designs and DALL-E mood board prompts will generate automatically during compilation (Phase 4).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    Text(book.coverPrompt, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = { coverPickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload Finished Cover Art")
                                }
                                
                                coverValidationResult?.let { result ->
                                    if (result.isValid) {
                                        Text(
                                            "Cover is valid (${result.width}x${result.height}). ${result.warningMessage ?: ""}", 
                                            style = MaterialTheme.typography.bodySmall, 
                                            color = if (result.warningMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text("Invalid cover: ${result.warningMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Text("Marketing Launch Campaign", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (book.marketingMaterials.isEmpty()) {
                                    Text("Promotional social launch campaigns, newsletters, and trailer scripts will generate automatically during compilation (Phase 4).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    Text(book.marketingMaterials, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    // Global Release & Audiobooks
                    val context = LocalContext.current
        val scope = rememberCoroutineScope()
                    val isBookTranslating by viewModel.isBookTranslating.collectAsState()
                    val bookTranslationProgress by viewModel.bookTranslationProgress.collectAsState()
                    val bookTranslationLogs by viewModel.bookTranslationLogs.collectAsState()

                    val availableVoices by viewModel.availableVoices.collectAsState()
                    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState()
                    val filteredVoices by viewModel.filteredVoices.collectAsState()
                    val ttsEngine by viewModel.ttsEngine.collectAsState()
                    val hfApiKey by viewModel.hfApiKey.collectAsState()
                    val hfModel by viewModel.hfModel.collectAsState()
                    val isConverting by viewModel.isConverting.collectAsState()
                    val conversionProgress by viewModel.conversionProgress.collectAsState()
                    val conversionLogs by viewModel.conversionLogs.collectAsState()

                    var targetLanguage by remember { mutableStateOf("Hindi") }
                    var languageDropdownExpanded by remember { mutableStateOf(false) }
                    var engineDropdownExpanded by remember { mutableStateOf(false) }
                    var voiceDropdownExpanded by remember { mutableStateOf(false) }
                    var isHfKeyVisible by remember { mutableStateOf(false) }

                    val languages = listOf("Hindi", "Telugu", "Tamil", "Kannada", "Spanish", "French", "German", "Japanese", "Portuguese")

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Section 1: Introduction Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🌍", style = MaterialTheme.typography.headlineLarge)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Global Distribution & Narration Studio",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Translate your completed book into standard world languages, select your vocal narrators, and run professional multi-chapter audiobook synthesis with automated background soundscapes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 2: Global Release Localization
                        Text("1. Global Release Localization (Translation)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Translate this full manuscript (synopsis, outline, and all generated chapters) into a new localized book edition.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        Button(
                                            onClick = { languageDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                        ) {
                                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Target: $targetLanguage")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }

                                        DropdownMenu(
                                            expanded = languageDropdownExpanded,
                                            onDismissRequest = { languageDropdownExpanded = false }
                                        ) {
                                            languages.forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang) },
                                                    onClick = {
                                                        targetLanguage = lang
                                                        languageDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (chapters.isEmpty()) {
                                                Toast.makeText(context, "Please generate chapters under the 'Chapters' tab first!", Toast.LENGTH_LONG).show()
                                            } else {
                                                viewModel.translateBook(book, targetLanguage) { translatedEdition ->
                                                    Toast.makeText(context, "Successfully created: ${translatedEdition.title}!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isBookTranslating,
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        if (isBookTranslating) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Translating...")
                                        } else {
                                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Localize Book")
                                        }
                                    }
                                }

                                if (isBookTranslating || bookTranslationLogs.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (isBookTranslating) {
                                        LinearProgressIndicator(
progress = { bookTranslationProgress },
modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Pipeline Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text("${(bookTranslationProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        val isTransPaused by viewModel.isTranslationPaused.collectAsState()
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (isTransPaused) {
                                                        viewModel.resumeLocalization()
                                                    } else {
                                                        viewModel.pauseLocalization()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).testTag("localize_pause_resume_btn"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isTransPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isTransPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                    contentDescription = if (isTransPaused) "Resume" else "Pause"
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isTransPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelSmall)
                                            }

                                            Button(
                                                onClick = { viewModel.stopLocalization() },
                                                modifier = Modifier.weight(1f).testTag("localize_stop_btn"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop"
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Stop", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    Text("Localization Pipeline Terminal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                                    ) {
                                        Box(modifier = Modifier.padding(6.dp).fillMaxSize()) {
                                            Text(
                                                text = bookTranslationLogs,
                                                color = Color.Green,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                modifier = Modifier.verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 3: Narrator Audiobook Studio
                        Text("2. Narrator Audiobook Studio (TTS Synthesis)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Narrate this book using a premium AI Text-To-Speech pipeline. This generates high-fidelity audio chunks for each chapter which can then be combined with soundscapes in the 'Audio' workspace.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Detailed Engine Selector
                                Text("Text-to-Speech Engine Pipeline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Android", "Pocket TTS", "Cloud HuggingFace TTS").forEach { engine ->
                                        FilterChip(
                                            selected = ttsEngine == engine,
                                            onClick = { viewModel.setTtsEngine(engine) },
                                            label = { Text(engine, style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = if (ttsEngine == engine) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("global_release_engine_chip_$engine")
                                        )
                                    }
                                }

                                // Engine Info Row
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        val (infoIcon, infoText) = when (ttsEngine) {
                                            "Android" -> Icons.Default.Android to "Uses your device's built-in Text-to-Speech system. Works completely offline, fast, and light on resources."
                                            "Pocket TTS" -> Icons.Default.PhonelinkRing to "Runs the local Pocket TTS engine: 100% offline synthesis in real time on CPU, private, with narrator voice cloning from a short WAV reference."
                                            else -> Icons.Default.SettingsApplications to "Uses Hugging Face inference endpoints. Highly customizable with cutting-edge neural speech models (requires internet and API key)."
                                        }
                                        Icon(
                                            imageVector = infoIcon,
                                            contentDescription = "Engine Info",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = infoText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (ttsEngine == "Cloud HuggingFace TTS") {
                                    OutlinedTextField(
                                        value = hfApiKey,
                                        onValueChange = { viewModel.setHfApiKey(it) },
                                        label = { Text("Hugging Face Access Token") },
                                        placeholder = { Text("Enter your HF Token (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        visualTransformation = if (isHfKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { isHfKeyVisible = !isHfKeyVisible }) {
                                                Icon(
                                                    imageVector = if (isHfKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Key Visibility"
                                                )
                                            }
                                        }
                                    )

                                    OutlinedTextField(
                                        value = hfModel,
                                        onValueChange = { viewModel.setHfModel(it) },
                                        label = { Text("Hugging Face Model ID") },
                                        placeholder = { Text("e.g. facebook/mms-tts-eng") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Voice Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Voice Profile:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                    Box(modifier = Modifier.weight(1.5f)) {
                                        val displayVoice = availableVoices.find { it.name == selectedVoiceName }?.displayName ?: "Default Voice"
                                        Button(
                                            onClick = { voiceDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                        ) {
                                            Text(displayVoice, maxLines = 1)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }

                                        DropdownMenu(
                                            expanded = voiceDropdownExpanded,
                                            onDismissRequest = { voiceDropdownExpanded = false }
                                        ) {
                                            if (filteredVoices.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("No voices matching current filters") },
                                                    onClick = { voiceDropdownExpanded = false },
                                                    enabled = false
                                                )
                                            } else {
                                                filteredVoices.forEach { voice ->
                                                    DropdownMenuItem(
                                                        text = { Text(voice.displayName) },
                                                        onClick = {
                                                            viewModel.selectedVoiceName.value = voice.name
                                                            voiceDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (chapters.isEmpty()) {
                                            Toast.makeText(context, "Generate chapters first!", Toast.LENGTH_LONG).show()
                                        } else {
                                            // Construct full text with chapter titles and bodies
                                            val fullText = chapters.sortedBy { it.chapterNumber }.joinToString("\n\n") { chap ->
                                                "Chapter ${chap.chapterNumber}: ${chap.title}\n\n${chap.content}"
                                            }
                                            viewModel.audioInputText.value = fullText
                                            viewModel.startTtsConversion(book.title)
                                            Toast.makeText(context, "Synthesis Pipeline Launched in Foreground Service!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isConverting,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isConverting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Synthesizing Audiobook Parts...")
                                    } else {
                                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Narrate & Compile Audiobook")
                                    }
                                }

                                if (isConverting || conversionLogs.contains("Starting Synthesis Process")) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (isConverting) {
                                        LinearProgressIndicator(
progress = { conversionProgress },
modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Synthesis Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text("${(conversionProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Text("Audiobook Synthesis Terminal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                                    ) {
                                        Box(modifier = Modifier.padding(6.dp).fillMaxSize()) {
                                            Text(
                                                text = conversionLogs,
                                                color = Color.Green,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                modifier = Modifier.verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }

                                    if (isConverting) {
                                        val isTtsPaused by viewModel.isTtsPaused.collectAsState()
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (isTtsPaused) {
                                                        viewModel.resumeTtsConversion()
                                                    } else {
                                                        viewModel.pauseTtsConversion()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).testTag("tts_pause_resume_btn"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isTtsPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isTtsPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                    contentDescription = if (isTtsPaused) "Resume" else "Pause"
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isTtsPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelSmall)
                                            }

                                            Button(
                                                onClick = { viewModel.cancelTtsConversion() },
                                                modifier = Modifier.weight(1f).testTag("tts_stop_btn"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop"
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Stop", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }                        
                        // Section 4: Character-Modulated Audiobook Drama Assembly
                        Text("3. Multi-Character Audio Drama Assembly", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("audiobook_assembly_pipeline_panel"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Character-Modulated Audiobook Pipeline",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = "Parse book chapters into separate narration and dialogue blocks, and dynamically modulate voice pitch, rate, and vocal presets per character based on their Cognitive Personality profiles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val isExtracting by viewModel.isExtractingCharacters.collectAsState()
                                val isAssembling by viewModel.isAssemblingAudiobook.collectAsState()
                                val extractionLogs by viewModel.extractionLogs.collectAsState()
                                val assemblyLogs by viewModel.assemblyLogs.collectAsState()
                                val assemblyProgress by viewModel.assemblyProgress.collectAsState()
                                val characterVoiceConfigs by viewModel.characterVoiceConfigs.collectAsState()

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                                // Step 1: Cognitive Character Extraction
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1. Cognitive Personality Profiles",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )

                                    Button(
                                        onClick = { viewModel.extractCharactersFromBible(book.id) },
                                        enabled = !isExtracting && !isAssembling && book.storyBible.isNotEmpty(),
                                        modifier = Modifier.testTag("extract_personas_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        if (isExtracting) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                        } else {
                                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Extract Profiles")
                                    }
                                }

                                if (book.storyBible.isEmpty()) {
                                    Text(
                                        text = "⚠️ Story Bible is empty. Run a scene draft on this book first to automatically generate the Story Bible.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (extractionLogs.isNotEmpty()) {
                                    Text(
                                        text = extractionLogs,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (characterVoiceConfigs.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        characterVoiceConfigs.forEachIndexed { charIdx, config ->
                                            var charVoiceDropdownExpanded by remember { mutableStateOf(false) }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = config.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        SuggestionChip(
                                                            onClick = {},
                                                            label = { Text(config.role, style = MaterialTheme.typography.labelSmall) }
                                                        )
                                                    }

                                                    Text(
                                                        text = "Traits: ${config.vocalTraits}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    // Voice Assignment
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Voice:",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Box {
                                                            val dispName = availableVoices.find { it.name == config.voiceName }?.displayName ?: config.voiceName
                                                            InputChip(
                                                                selected = true,
                                                                onClick = { charVoiceDropdownExpanded = true },
                                                                label = { Text(dispName.take(24) + "...") },
                                                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                                            )

                                                            DropdownMenu(
                                                                expanded = charVoiceDropdownExpanded,
                                                                onDismissRequest = { charVoiceDropdownExpanded = false }
                                                            ) {
                                                                availableVoices.forEach { voice ->
                                                                    DropdownMenuItem(
                                                                        text = { Text(voice.displayName) },
                                                                        onClick = {
                                                                            val updatedList = characterVoiceConfigs.toMutableList()
                                                                            updatedList[charIdx] = updatedList[charIdx].copy(voiceName = voice.name)
                                                                            viewModel.characterVoiceConfigs.value = updatedList
                                                                            charVoiceDropdownExpanded = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Pitch modulation slider
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Pitch Shift:", style = MaterialTheme.typography.labelSmall)
                                                            Text(String.format("%.1f semitones", config.pitch), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                        }
                                                        Slider(
                                                            value = config.pitch,
                                                            onValueChange = { newPitch ->
                                                                val updatedList = characterVoiceConfigs.toMutableList()
                                                                updatedList[charIdx] = updatedList[charIdx].copy(pitch = newPitch)
                                                                viewModel.characterVoiceConfigs.value = updatedList
                                                            },
                                                            valueRange = -5f..5f,
                                                            modifier = Modifier.height(24.dp)
                                                        )
                                                    }

                                                    // Rate modulation slider
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Narrative Speed:", style = MaterialTheme.typography.labelSmall)
                                                            Text(String.format("%.2fx", config.rate), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                        }
                                                        Slider(
                                                            value = config.rate,
                                                            onValueChange = { newRate ->
                                                                val updatedList = characterVoiceConfigs.toMutableList()
                                                                updatedList[charIdx] = updatedList[charIdx].copy(rate = newRate)
                                                                viewModel.characterVoiceConfigs.value = updatedList
                                                            },
                                                            valueRange = 0.5f..2.0f,
                                                            modifier = Modifier.height(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                                // Step 2: Assemble Pipeline Launcher
                                Button(
                                    onClick = { viewModel.assembleAudiobookWithModulation(book.id) },
                                    enabled = !isExtracting && !isAssembling,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("assemble_audiobook_pipeline_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Headset, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("2. Assemble Character-Modulated Audiobook")
                                }

                                if (isAssembling || assemblyLogs.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Pipeline Progress & Console Output",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isAssembling) {
                                            LinearProgressIndicator(
progress = { assemblyProgress },
modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Terminal logs
                                        Card(
                                            modifier = Modifier.fillMaxWidth().height(180.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                                        ) {
                                            Box(modifier = Modifier.padding(6.dp).fillMaxSize()) {
                                                Text(
                                                    text = assemblyLogs,
                                                    color = Color.Green,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    // Cliffhanger Analysis Modal
    val cliffhangerAnalysis by viewModel.cliffhangerAnalysis.collectAsState()
    if (cliffhangerAnalysis != null) {
        Dialog(onDismissRequest = { viewModel.cliffhangerAnalysis.value = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cliffhanger Analyzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.rememberScrollState().let { scrollState ->
                        Text(cliffhangerAnalysis!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(scrollState).weight(1f, fill = false))
                    }
                    TextButton(onClick = { viewModel.cliffhangerAnalysis.value = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Read/Edit Chapter Overlay / Dialog
    if (readingChapterId != null) {
        val chapterToRead = chapters.find { it.id == readingChapterId }
        if (chapterToRead != null) {
            DistractionFreeManuscriptEditor(
                title = "Chapter ${chapterToRead.chapterNumber}: ${chapterToRead.title}",
                initialText = chapterToRead.content,
                onSave = { updatedText ->
                    viewModel.updateChapterContent(chapterToRead, updatedText)
                    Toast.makeText(context, "Chapter ${chapterToRead.chapterNumber} updated successfully!", Toast.LENGTH_SHORT).show()
                    readingChapterId = null
                },
                onDismiss = { readingChapterId = null }
            )
        }
    }
}
}
}
