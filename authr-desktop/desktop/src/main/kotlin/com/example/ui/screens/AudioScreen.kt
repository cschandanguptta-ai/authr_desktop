package com.example.ui.screens

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.model.AudioTrack
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.components.DistractionFreeManuscriptEditor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Configuration states
    val audioInput by viewModel.audioInputText.collectAsState()
    val storyParts by viewModel.storyParts.collectAsState()
    var showAudioDistractionFreeEditor by remember { mutableStateOf(false) }
    var showSaveScriptDialog by remember { mutableStateOf(false) }
    var scriptSaveTitle by remember { mutableStateOf("") }
    val availableVoices by viewModel.availableVoices.collectAsState()
    val selectedVoice by viewModel.selectedVoiceName.collectAsState()
    val filteredVoices by viewModel.filteredVoices.collectAsState()
    val selectedGenderFilter by viewModel.selectedGenderFilter.collectAsState()
    val selectedResonanceFilter by viewModel.selectedResonanceFilter.collectAsState()
    val rate by viewModel.audioRate.collectAsState()
    val pitch by viewModel.audioPitch.collectAsState()
    val volume by viewModel.audioVolume.collectAsState()
    val selectedEmotion by viewModel.selectedEmotionPreset.collectAsState()
    val split by viewModel.splitChapters.collectAsState()
    val ttsChunkSize by viewModel.ttsChunkSize.collectAsState()
    val ebuNormalizationEnabled by viewModel.ebuNormalizationEnabled.collectAsState()

    // Conversion states
    val isConverting by viewModel.isConverting.collectAsState()
    val isOptimizingAudioScript by viewModel.isOptimizingAudioScript.collectAsState()
    val conversionLogs by viewModel.conversionLogs.collectAsState()
    val progress by viewModel.conversionProgress.collectAsState()
    val elapsedSeconds by viewModel.conversionElapsedSeconds.collectAsState()
    val etaSeconds by viewModel.conversionEtaSeconds.collectAsState()
    val ttsBatchQueue by viewModel.ttsBatchQueue.collectAsState()

    // Character Modulation States
    val characterVoiceConfigs by viewModel.characterVoiceConfigs.collectAsState()
    val isExtractingCharacters by viewModel.isExtractingCharacters.collectAsState()
    val isVoxSherpaActive by viewModel.isVoxSherpaActive.collectAsState()
    val extractionLogs by viewModel.extractionLogs.collectAsState()
    val isAssemblingAudiobook by viewModel.isAssemblingAudiobook.collectAsState()
    val assemblyLogs by viewModel.assemblyLogs.collectAsState()

    // Database state
    val savedAudioTracks by viewModel.allAudioTracks.collectAsState()

    // Merging states
    val isMergingAudio by viewModel.isMergingAudio.collectAsState()
    val mergingError by viewModel.mergingError.collectAsState()
    val selectedAmbientTheme by viewModel.selectedAmbientTheme.collectAsState()
    val ambientVolume by viewModel.ambientVolume.collectAsState()
    var silenceSeconds by remember { mutableStateOf(2.0f) }

    // Local Dropdown state
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    var ambientDropdownExpanded by remember { mutableStateOf(false) }

    // Media playback state
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingTrackId by remember { mutableStateOf<Int?>(null) }
    var showTrackPartsDialog by remember { mutableStateOf<AudioTrack?>(null) }
    var activePlayingPartPath by remember { mutableStateOf<String?>(null) }
    var isPartAudioPaused by remember { mutableStateOf(false) }

    // Dialog for custom title saving
    var showSynthesisTitleDialog by remember { mutableStateOf(false) }
    var synthesisTitleInput by remember { mutableStateOf("") }

    // Clean up MediaPlayer on dispose
    DisposableEffect(Unit) {
        onDispose {
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        }
    }

    fun stopPartAudio() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            activeMediaPlayer = null
            activePlayingPartPath = null
            isPartAudioPaused = false
        }
    }

    // Helper to play synthesized Wav files
    fun playPartAudio(filePath: String) {
        try {
            if (activePlayingPartPath == filePath && isPartAudioPaused) {
                activeMediaPlayer?.start()
                isPartAudioPaused = false
                return
            }

            stopPartAudio()

            val file = File(filePath)
            if (file.exists()) {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        activePlayingPartPath = null
                        isPartAudioPaused = false
                        release()
                    }
                }
                activeMediaPlayer = mp
                activePlayingPartPath = filePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pausePartAudio() {
        if (activeMediaPlayer?.isPlaying == true) {
            activeMediaPlayer?.pause()
            isPartAudioPaused = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Narrative Audio Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isVoxSherpaActive) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "VoxSherpa Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        },
                        
                        modifier = Modifier.testTag("voxsherpa_active_chip")
                    )
                }

                val ttsEngine by viewModel.ttsEngine.collectAsState()
                SuggestionChip(
                    onClick = { viewModel.selectTab(3) },
                    label = { 
                        Text(
                            text = ttsEngine,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    
                    modifier = Modifier.testTag("active_engine_chip")
                )
            }
        }

        if (storyParts.any { it.isNotBlank() }) {
            OutlinedButton(
                onClick = { viewModel.importWizardScriptToAudioStudio() },
                modifier = Modifier.fillMaxWidth().testTag("import_wizard_script_btn"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Import Interactive Wizard Draft into Script Editor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // Quick Option Chips for Genre Presets
        Text(
            text = "Genre Presets",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            genresPresets.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.loadVoiceRecommendation(preset.recommendedVoiceKeyword, preset.rate)
                        Toast.makeText(context, "Preset Applied: ${preset.genreName}", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text(preset.genreName) }
                )
            }
        }


        val audioFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val content = inputStream.bufferedReader().use { r -> r.readText() }
                        viewModel.syncUploadedTextToWorkspace(content)
                        Toast.makeText(context, "Successfully loaded ${content.length} characters", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // High-Fidelity Studio Document Canvas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (audioInput.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (audioInput.isEmpty()) {
                    // Empty state upload area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { audioFilePickerLauncher.launch("*/*") }
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Load External Story Script",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select any .txt or .md file to import instantly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { audioFilePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("upload_audio_txt_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Files", style = MaterialTheme.typography.labelMedium)
                            }

                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            OutlinedButton(
                                onClick = { 
                                    clipboardManager.getText()?.let { 
                                        viewModel.audioInputText.value = it.text
                                        Toast.makeText(context, "Pasted ${it.text.length} characters", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(36.dp).testTag("paste_audio_txt_button"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Script", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    // Document active header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Active Script Canvas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val words = audioInput.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                                val chapterCount = audioInput.split("\n\n").filter { it.trim().isNotEmpty() }.size
                                Text(
                                    text = "${audioInput.length} chars • $words words • $chapterCount segments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showAudioDistractionFreeEditor = true },
                                modifier = Modifier.size(32.dp).testTag("audio_full_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Full-screen Distraction-free Editor",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    val fullText = storyParts.filter { it.isNotEmpty() }.joinToString("\n\n")
                                    if (fullText.isNotEmpty()) {
                                        viewModel.audioInputText.value = fullText
                                        Toast.makeText(context, "Current Story Workspace imported!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Story Workspace is empty.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("audio_import_story_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Import from Story Tab",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    // Remove text in brackets () or [] which are often stage directions
                                    val cleaned = audioInput.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
                                        .lines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .joinToString("\n\n")
                                    viewModel.audioInputText.value = cleaned
                                    Toast.makeText(context, "Cleaned stage directions and normalized pacing.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp).testTag("audio_clean_script_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = "Clean non-dialogue text",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    if (audioInput.isNotEmpty()) {
                                        scriptSaveTitle = "Imported - ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                                        showSaveScriptDialog = true
                                    } else {
                                        Toast.makeText(context, "Nothing to save.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("audio_save_to_library_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save to Library",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { audioFilePickerLauncher.launch("*/*") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Replace File",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.audioInputText.value = "" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_audio_txt_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Input",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Input script field (visible in both states, but expanded with details when loaded)
                OutlinedTextField(
                    value = audioInput,
                    onValueChange = { viewModel.audioInputText.value = it },
                    label = { Text("Script Editor") },
                    placeholder = { Text("Paste or import your novel script here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("audio_input_script"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    maxLines = 12
                )

                if (audioInput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.optimizeAudioInputScript() },
                        enabled = !isOptimizingAudioScript,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("script_optimization_pass_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        if (isOptimizingAudioScript) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Optimizing Script...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Script Optimization Pass")
                        }
                    }
                }
            }
        }

        // Speech Parameters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Filters Group
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filter Voices by Gender", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "Male", "Female").forEach { gender ->
                            val isSelected = selectedGenderFilter == gender
                            StylizedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedGenderFilter.value = gender },
                                label = gender,
                                icon = when (gender) {
                                    "Male" -> Icons.Default.Person
                                    "Female" -> Icons.Default.Person
                                    else -> Icons.Default.Group
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filter Voices by Emotional Resonance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "Narrative", "Dramatic", "Soothing").forEach { resonance ->
                            val isSelected = selectedResonanceFilter == resonance
                            StylizedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedResonanceFilter.value = resonance },
                                label = resonance,
                                icon = when (resonance) {
                                    "Narrative" -> Icons.Default.Book
                                    "Dramatic" -> Icons.Default.FlashOn
                                    "Soothing" -> Icons.Default.Favorite
                                    else -> Icons.Default.Tune
                                }
                            )
                        }
                    }
                }

                // Voice selector
                Text("Vocal Profile Selection", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.fillMaxWidth()) {
                    val displayVoice = availableVoices.find { it.name == selectedVoice }?.displayName ?: "Selecting system voice..."
                    Button(
                        onClick = { voiceDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_select_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(displayVoice)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = voiceDropdownExpanded,
                        onDismissRequest = { voiceDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (filteredVoices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No voices match current filters") },
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

                // Quick horizontal cards selector for filtered voices
                if (filteredVoices.isNotEmpty()) {
                    Text("Quick-Select Voice (${filteredVoices.size} available)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredVoices.forEach { voice ->
                            val isSelected = selectedVoice == voice.name
                            Card(
                                onClick = { viewModel.selectedVoiceName.value = voice.name },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .width(180.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = voice.locale.displayLanguage,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (voice.gender == "Female") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = voice.gender,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (voice.gender == "Female") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = voice.emotionalResonance,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Text(
                                        text = voice.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Speed rate slider
                // Voice Narration Controls & Emotion Presets
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Emotion Preset Dropdown
                        var expandedEmotion by remember { mutableStateOf(false) }
                        val presets = viewModel.getAvailablePresets()
                        
                        ExposedDropdownMenuBox(
                            expanded = expandedEmotion,
                            onExpandedChange = { expandedEmotion = it }
                        ) {
                            OutlinedTextField(
                                value = selectedEmotion,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Emotion Preset") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEmotion) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedEmotion,
                                onDismissRequest = { expandedEmotion = false }
                            ) {
                                presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.name) },
                                        onClick = {
                                            viewModel.selectEmotionPreset(preset.name)
                                            expandedEmotion = false
                                        }
                                    )
                                }
                            }
                        }

                        // Sliders
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Speech Speed", style = MaterialTheme.typography.labelMedium)
                                Text(String.format("%.2fx", rate), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = rate,
                                onValueChange = { 
                                    viewModel.audioRate.value = it 
                                    viewModel.onAudioSettingChanged()
                                },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Pitch", style = MaterialTheme.typography.labelMedium)
                                Text(String.format("%.0f semitones", pitch), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = pitch,
                                onValueChange = { 
                                    viewModel.audioPitch.value = it 
                                    viewModel.onAudioSettingChanged()
                                },
                                valueRange = -12f..12f,
                                steps = 23,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Volume", style = MaterialTheme.typography.labelMedium)
                                Text(String.format("%.0f%%", volume), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = volume,
                                onValueChange = { 
                                    viewModel.audioVolume.value = it 
                                    viewModel.onAudioSettingChanged()
                                },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Synthesis Chunk Size", style = MaterialTheme.typography.labelMedium)
                                Text(String.format("%d chars", ttsChunkSize), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = ttsChunkSize.toFloat(),
                                onValueChange = { 
                                    viewModel.ttsChunkSize.value = it.toInt()
                                },
                                valueRange = 100f..1000f,
                                steps = 17,
                                modifier = Modifier.fillMaxWidth().testTag("chunk_size_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Loudness Normalization", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("Applies broadcast-standard EBU R128 normalization", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = ebuNormalizationEnabled,
                                onCheckedChange = { viewModel.ebuNormalizationEnabled.value = it },
                                modifier = Modifier.testTag("ebu_normalization_toggle")
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.speakPreviewSample() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview Voice")
                                Spacer(Modifier.width(8.dp))
                                Text("Preview Voice")
                            }
                            
                            var showSaveDialog by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Save Preset")
                                Spacer(Modifier.width(8.dp))
                                Text("Save Preset")
                            }
                            
                            if (showSaveDialog) {
                                var presetName by remember { mutableStateOf("My Preset") }
                                AlertDialog(
                                    onDismissRequest = { showSaveDialog = false },
                                    title = { Text("Save Custom Preset") },
                                    text = {
                                        OutlinedTextField(
                                            value = presetName,
                                            onValueChange = { presetName = it },
                                            label = { Text("Preset Name") },
                                            singleLine = true
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (presetName.isNotBlank()) {
                                                viewModel.saveCustomPreset(presetName.trim())
                                                showSaveDialog = false
                                            }
                                        }) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            if (selectedEmotion != "Neutral" && selectedEmotion != "Custom" && viewModel.customEmotionPresets.value.any { it.name == selectedEmotion }) {
                                TextButton(onClick = { viewModel.deleteCustomPreset(selectedEmotion) }) {
                                    Text("Delete preset", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            
                            var showImportExportDialog by remember { mutableStateOf(false) }
                            TextButton(onClick = { showImportExportDialog = true }) {
                                Text("Import/Export JSON")
                            }
                            
                            if (showImportExportDialog) {
                                var jsonText by remember { mutableStateOf(viewModel.exportCustomPresetsToJson()) }
                                AlertDialog(
                                    onDismissRequest = { showImportExportDialog = false },
                                    title = { Text("Import / Export Presets") },
                                    text = {
                                        OutlinedTextField(
                                            value = jsonText,
                                            onValueChange = { jsonText = it },
                                            modifier = Modifier.fillMaxWidth().height(200.dp)
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            viewModel.importCustomPresetsFromJson(jsonText)
                                            showImportExportDialog = false
                                        }) { Text("Import") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showImportExportDialog = false }) { Text("Close") }
                                    }
                                )
                            }
                        }
                    }
                }

                // Options checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = split,
                        onCheckedChange = { viewModel.splitChapters.value = it },
                        modifier = Modifier.testTag("split_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-split chapters/paragraphs into separate audio parts")
                }
            }
        }

        // Character Audio Drama Pipeline Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.RecentActors, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Character Drama Pipeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Text(
                    text = "Assign unique voices to detected characters for a multi-actor audio drama experience. Optimized for scripts generated via Interactive Wizard.",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = { viewModel.extractCharactersFromScript() },
                    modifier = Modifier.fillMaxWidth().testTag("scan_characters_button"),
                    enabled = !isExtractingCharacters && audioInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    if (isExtractingCharacters) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary)
                    } else {
                        Icon(Icons.Default.Troubleshoot, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Script for Characters")
                    }
                }

                if (extractionLogs.isNotEmpty()) {
                    Text(
                        text = extractionLogs,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (characterVoiceConfigs.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    
                    Text("Detected Character Profiles", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        characterVoiceConfigs.forEach { config ->
                            CharacterVoiceConfigItem(
                                config = config,
                                availableVoices = availableVoices,
                                onUpdate = { updated ->
                                    val current = characterVoiceConfigs.toMutableList()
                                    val index = current.indexOfFirst { it.name == updated.name }
                                    if (index != -1) {
                                        current[index] = updated
                                        viewModel.characterVoiceConfigs.value = current
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.assembleAudioDramaFromScript() },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("assemble_audio_drama_button"),
                        enabled = !isAssemblingAudiobook,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isAssemblingAudiobook) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Duo, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Generate Full Audio Drama", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                if (assemblyLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = assemblyLogs,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Pronunciation Dictionary Card
        val pronunciationRules by viewModel.pronunciationRules.collectAsState()
        var originalInput by remember { mutableStateOf("") }
        var phoneticInput by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("pronunciation_dictionary_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pronunciation Dictionary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "Map custom phonetic overrides for specialized sci-fi names, fantasy lore, or complex Biblical terms (e.g., 'Melchizedek' ➔ 'Mel-kiz-uh-dek') to smooth TTS narration.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (pronunciationRules.isNotEmpty()) {
                    Text(
                        text = "Active Phonetic Rules",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pronunciationRules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.original,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "➔ Pronounced as: ${rule.phonetic}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removePronunciationRule(rule.id) },
                                    modifier = Modifier.testTag("delete_rule_${rule.original}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete rule",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Add Custom Rule",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = originalInput,
                        onValueChange = { originalInput = it },
                        label = { Text("Original Word") },
                        placeholder = { Text("e.g., AriaNeural") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronunciation_original_input"),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = phoneticInput,
                        onValueChange = { phoneticInput = it },
                        label = { Text("Phonetic Spelling") },
                        placeholder = { Text("Ah-ree-ah Neural") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronunciation_phonetic_input"),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        if (originalInput.isNotBlank() && phoneticInput.isNotBlank()) {
                            viewModel.addPronunciationRule(originalInput.trim(), phoneticInput.trim())
                            originalInput = ""
                            phoneticInput = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_pronunciation_rule_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    enabled = originalInput.isNotBlank() && phoneticInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Override Rule")
                }
            }
        }

        // Live Preview & Playback Panel
        val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
        val currentTtsParagraphIndex by viewModel.currentTtsParagraphIndex.collectAsState()
        val ttsParagraphs by viewModel.ttsParagraphs.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isTtsPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (isTtsPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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
                    Column {
                        Text(
                            text = "Sequential Narration Player",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isTtsPlaying) "Analyzing & narrating script with dramatic breaks..." else "Ready to preview your voice-optimized script",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    
                    if (isTtsPlaying) {
                        // Tiny pulsing visualizer indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = if (index % 2 == 0) 14.dp else 22.dp)
                                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (isTtsPlaying && ttsParagraphs.isNotEmpty() && currentTtsParagraphIndex in ttsParagraphs.indices) {
                    // Active highlighted paragraph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(6.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paragraph ${currentTtsParagraphIndex + 1} of ${ttsParagraphs.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dramatic Pause (0.5s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = ttsParagraphs[currentTtsParagraphIndex],
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Progress indicator (Interactive Seek Bar)
                    val paragraphsCount = if (ttsParagraphs.isNotEmpty()) ttsParagraphs.size else {
                        audioInput.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.size
                    }
                    
                    if (paragraphsCount > 0) {
                        val currentIndexVal = currentTtsParagraphIndex.coerceAtLeast(0)
                        val displayIndex = if (currentTtsParagraphIndex >= 0) currentTtsParagraphIndex else 0
                        
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paragraph ${displayIndex + 1} of $paragraphsCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val percent = if (paragraphsCount > 0) {
                                    ((displayIndex + 1).toFloat() / paragraphsCount * 100).toInt()
                                } else 0
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Slider(
                                value = displayIndex.toFloat(),
                                onValueChange = { targetFloat ->
                                    viewModel.seekToParagraph(targetFloat.toInt())
                                },
                                valueRange = 0f..(paragraphsCount - 1).toFloat().coerceAtLeast(0f),
                                steps = if (paragraphsCount > 2) paragraphsCount - 2 else 0,
                                modifier = Modifier.fillMaxWidth().testTag("voice_seek_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }

                // Control Buttons
                val isTtsPaused by viewModel.isTtsPaused.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isTtsPlaying) {
                        Button(
                            onClick = { viewModel.speakPreview() },
                            enabled = audioInput.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_preview_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Voice Script")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            // Skip Backward
                            IconButton(
                                onClick = { viewModel.skipBackward() },
                                modifier = Modifier.testTag("voice_skip_backward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Skip Backward",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Play / Pause / Resume
                            if (isTtsPaused) {
                                FilledIconButton(
                                    onClick = { viewModel.resumeSpeaking() },
                                    modifier = Modifier.size(56.dp).testTag("voice_resume_preview_button"),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                FilledIconButton(
                                    onClick = { viewModel.pauseSpeaking() },
                                    modifier = Modifier.size(56.dp).testTag("voice_pause_preview_button"),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Pause",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Skip Forward
                            IconButton(
                                onClick = { viewModel.skipForward() },
                                modifier = Modifier.testTag("voice_skip_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip Forward",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Stop Button
                            FilledTonalIconButton(
                                onClick = { viewModel.stopSpeaking() },
                                modifier = Modifier.size(48.dp).testTag("voice_stop_preview_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Synthesis Start / Progress
        if (isConverting) {
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
                        Text("Synthesizing Audiobook...", fontWeight = FontWeight.Bold)
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                    )

                    // Live Countdown and Elapsed Timers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formatTime = { seconds: Long ->
                            if (seconds < 0) "--:--"
                            else {
                                val m = seconds / 60
                                val s = seconds % 60
                                String.format("%02d:%02d", m, s)
                            }
                        }
                        Text(
                            text = "Elapsed: ${formatTime(elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (etaSeconds >= 0) "ETA: ${formatTime(etaSeconds)} remaining" else "ETA: Estimating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("eta_timer_label")
                        )
                    }

                    // Logs Terminal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Color.Black, MaterialTheme.shapes.small)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = conversionLogs,
                            color = Color.Green,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }

                    Button(
                        onClick = { viewModel.cancelTtsConversion() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_conversion_button")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Synthesis")
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    synthesisTitleInput = viewModel.getDefaultFilenameFromHook("wav")
                    showSynthesisTitleDialog = true
                },
                enabled = audioInput.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_synthesis_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert to Studio Audiobook")
            }
        }

        if (ttsBatchQueue.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                text = "TTS Batch Queue",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ttsBatchQueue.reversed().forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (item.status != "Processing") {
                                    IconButton(onClick = { viewModel.removeTtsQueueItem(item.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Status: ${item.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (item.status) {
                                        "Completed" -> MaterialTheme.colorScheme.primary
                                        "Failed" -> MaterialTheme.colorScheme.error
                                        "Processing" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (item.status == "Processing" && item.progress > 0f) {
                                    Text(
                                        "${(item.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            if (item.status == "Processing") {
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Audio Records History
        Text(
            text = "Completed Audiobook Productions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Quick Option Chips for Genre Presets
        Text(
            text = "Genre Presets",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            genresPresets.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.loadVoiceRecommendation(preset.recommendedVoiceKeyword, preset.rate)
                        Toast.makeText(context, "Preset Applied: ${preset.genreName}", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text(preset.genreName) }
                )
            }
        }


        if (savedAudioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No completed audio projects found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            savedAudioTracks.forEach { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTrackPartsDialog = track }
                        .testTag("audio_track_card_${track.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.Bold)
                            Text(
                                "Voice: ${track.voiceName.take(15)}... • Rate: ${track.rate}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            var isExporting by remember { mutableStateOf(false) }

                            IconButton(
                                onClick = { showTrackPartsDialog = track }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Track Parts", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = {
                                    isExporting = true
                                    viewModel.exportProjectZip(
                                        context = context,
                                        track = track,
                                        onComplete = { filename ->
                                            isExporting = false
                                            Toast.makeText(context, "Project exported: $filename to Downloads folder!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            isExporting = false
                                            Toast.makeText(context, "Export failed: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                enabled = !isExporting,
                                modifier = Modifier.testTag("export_project_button_${track.id}")
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.FileDownload, contentDescription = "Export Unified Project", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteAudioTrack(track) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Audio", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Save synthesis project name dialog
    if (showSynthesisTitleDialog) {
        Dialog(onDismissRequest = { showSynthesisTitleDialog = false }) {
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
                    Text("Audiobook Production Name", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = synthesisTitleInput,
                        onValueChange = { synthesisTitleInput = it },
                        label = { Text("Audio Track Title") },
                        placeholder = { Text("E.g., Arjun Rebirth Chapter 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSynthesisTitleDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.startTtsConversion(synthesisTitleInput)
                                showSynthesisTitleDialog = false
                            },
                            modifier = Modifier.testTag("confirm_synthesis_button")
                        ) {
                            Text("Convert")
                        }
                    }
                }
            }
        }
    }

    // Track Parts Player Dialog
    showTrackPartsDialog?.let { track ->
        Dialog(onDismissRequest = {
            stopPartAudio()
            showTrackPartsDialog = null
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Audiobook Production Console", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    val folder = File(track.filePath)
                    val wavFiles = if (folder.exists() && folder.isDirectory) {
                        folder.listFiles { _, name -> name.endsWith(".wav") && !name.contains("Merged_") }?.sortedBy { file ->
                            val numberPart = file.nameWithoutExtension.removePrefix("Part_").toIntOrNull()
                            numberPart ?: Int.MAX_VALUE
                        }
                    } else null

                    val mergedFile = File(track.filePath, "Merged_Continuous_Audiobook.wav")
                    val hasMergedFile = mergedFile.exists()
                    
                    val cueFile = File(track.filePath, "Merged_Continuous_Audiobook.cue")
                    val txtFile = File(track.filePath, "Merged_Continuous_Audiobook_Plot_Points.txt")
                    val hasManifest = txtFile.exists()
                    var showManifest by remember { mutableStateOf(false) }

                    if (hasMergedFile) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val isPlayingThis = activePlayingPartPath == mergedFile.absolutePath
                                        if (isPlayingThis) {
                                            if (isPartAudioPaused) {
                                                playPartAudio(mergedFile.absolutePath)
                                            } else {
                                                pausePartAudio()
                                            }
                                        } else {
                                            playPartAudio(mergedFile.absolutePath)
                                        }
                                    }
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Column {
                                        Text(
                                            text = "Continuous Full Audiobook",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Play consolidated master track",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (activePlayingPartPath == mergedFile.absolutePath && !isPartAudioPaused) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Play Full Book",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Text("Individual Chapters", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                    if (wavFiles.isNullOrEmpty()) {
                        Text("Audio files not found or deleted.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        wavFiles.forEach { file ->
                            val isPlayingThis = activePlayingPartPath == file.absolutePath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isPlayingThis) {
                                            if (isPartAudioPaused) {
                                                playPartAudio(file.absolutePath)
                                            } else {
                                                pausePartAudio()
                                            }
                                        } else {
                                            playPartAudio(file.absolutePath)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    imageVector = if (isPlayingThis && !isPartAudioPaused) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Play/Pause File",
                                    tint = if (isPlayingThis && !isPartAudioPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    Text("Continuous Audiobook Merging Pipeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Concatenate all individual narration parts sequentially with customizable silence gaps and custom synthesized soundscapes to create a seamless full-length audiobook.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pause Duration: ${String.format("%.1f", silenceSeconds)}s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Slider(
                        value = silenceSeconds,
                        onValueChange = { silenceSeconds = it },
                        valueRange = 0f..5f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("silence_seconds_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Ambient Background Overlay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "Layer procedurally synthesized high-fidelity ambient soundscapes or light musical themes directly over the vocal narrations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { ambientDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ambient_select_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            val ambientIcon = when (selectedAmbientTheme) {
                                "Cosmic Space" -> Icons.Default.AutoAwesome
                                "Rain & Wind" -> Icons.Default.Audiotrack
                                "Enchanted Lofi" -> Icons.Default.MusicNote
                                "Meditation Pad" -> Icons.Default.Spa
                                "Cyberpunk Neon Drone" -> Icons.Default.Bolt
                                "Busy Cafe Atmosphere" -> Icons.Default.Storefront
                                "Deep Forest Wind" -> Icons.Default.Terrain
                                "Dark Dungeon Drone" -> Icons.Default.Warning
                                else -> Icons.AutoMirrored.Filled.VolumeOff
                            }
                            Icon(ambientIcon, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Theme: $selectedAmbientTheme")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = ambientDropdownExpanded,
                            onDismissRequest = { ambientDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val themes = listOf(
                                "None" to "Disable background overlay",
                                "Cosmic Space" to "Low modulated sci-fi drone synth",
                                "Rain & Wind" to "Gentle falling rain & wind noise",
                                "Enchanted Lofi" to "Relaxing slow chord progression",
                                "Meditation Pad" to "Deep beating meditation resonance",
                                "Cyberpunk Neon Drone" to "Dark futuristic synth bass drone",
                                "Busy Cafe Atmosphere" to "Bustling cafe chatter & cup clinks",
                                "Deep Forest Wind" to "Blowing forest wind & soft rustles",
                                "Dark Dungeon Drone" to "Low creepy dungeon resonance pad"
                            )
                            themes.forEach { (themeName, themeDesc) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(themeName, fontWeight = FontWeight.Bold)
                                            Text(themeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                        }
                                    },
                                    leadingIcon = {
                                        val itemIcon = when (themeName) {
                                            "Cosmic Space" -> Icons.Default.AutoAwesome
                                            "Rain & Wind" -> Icons.Default.Audiotrack
                                            "Enchanted Lofi" -> Icons.Default.MusicNote
                                            "Meditation Pad" -> Icons.Default.Spa
                                            "Cyberpunk Neon Drone" -> Icons.Default.Bolt
                                            "Busy Cafe Atmosphere" -> Icons.Default.Storefront
                                            "Deep Forest Wind" -> Icons.Default.Terrain
                                            "Dark Dungeon Drone" -> Icons.Default.Warning
                                            else -> Icons.AutoMirrored.Filled.VolumeOff
                                        }
                                        Icon(itemIcon, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.selectedAmbientTheme.value = themeName
                                        ambientDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Dual-Channel Studio Mixer Deck
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Studio FX Mixing Console",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "MULTI-CHANNEL",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Channel 1: Vocal Narration Volume
                                val narrationVol by viewModel.narrationVolume.collectAsState()
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Vocal Gain (CH1)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        // Visual LED level meter lights simulation
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val barsCount = 8
                                            val activeBars = (narrationVol * barsCount / 1.5f).toInt().coerceIn(1, barsCount)
                                            for (b in 1..barsCount) {
                                                val barColor = if (b <= activeBars) {
                                                    if (b > 6) MaterialTheme.colorScheme.error else if (b > 4) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                }
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(barColor))
                                            }
                                        }

                                        Slider(
                                            value = narrationVol,
                                            onValueChange = { viewModel.narrationVolume.value = it },
                                            valueRange = 0.2f..1.5f,
                                            modifier = Modifier.fillMaxWidth().testTag("voice_volume_slider")
                                        )
                                        
                                        Text(
                                            text = "${(narrationVol * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Channel 2: Ambient Background Volume
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Ambient Gain (CH2)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedAmbientTheme == "None") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                        )

                                        // Visual LED level meter lights simulation
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val barsCount = 8
                                            val activeBars = if (selectedAmbientTheme == "None") 0 else ((ambientVolume / 0.40f) * barsCount).toInt().coerceIn(0, barsCount)
                                            for (b in 1..barsCount) {
                                                val barColor = if (b <= activeBars) {
                                                    if (b > 6) MaterialTheme.colorScheme.error else if (b > 4) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                }
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(barColor))
                                            }
                                        }

                                        Slider(
                                            value = ambientVolume,
                                            onValueChange = { viewModel.ambientVolume.value = it },
                                            valueRange = 0.00f..0.40f,
                                            enabled = selectedAmbientTheme != "None",
                                            modifier = Modifier.fillMaxWidth().testTag("ambient_volume_slider")
                                        )

                                        Text(
                                            text = if (selectedAmbientTheme == "None") "MUTED" else "${(ambientVolume * 250).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedAmbientTheme == "None") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.mergeAudioNovel(
                                context = context,
                                track = track,
                                silenceSeconds = silenceSeconds,
                                onComplete = { filename ->
                                    Toast.makeText(context, "Continuous Audiobook merged: $filename in Downloads!", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Merge failed: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = !isMergingAudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_audiobook_button_${track.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        if (isMergingAudio) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consolidating Byte Stream...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merge & Export Full Audiobook")
                        }
                    }

                    if (mergingError != null) {
                        Text(
                            text = "Error: $mergingError",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isExportingInner by remember { mutableStateOf(false) }

                        TextButton(
                            onClick = {
                                isExportingInner = true
                                viewModel.exportProjectZip(
                                    context = context,
                                    track = track,
                                    onComplete = { filename ->
                                        isExportingInner = false
                                        Toast.makeText(context, "Project exported: $filename to Downloads!", Toast.LENGTH_LONG).show()
                                    },
                                    onError = { err ->
                                        isExportingInner = false
                                        Toast.makeText(context, "Export failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isExportingInner,
                            modifier = Modifier.testTag("dialog_export_project_button")
                        ) {
                            if (isExportingInner) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export ZIP Package", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }

                        Button(onClick = {
                            stopPartAudio()
                            showTrackPartsDialog = null
                        }) {
                            Text("Close Player")
                        }
                    }
                }
            }
        }
    }

    if (showAudioDistractionFreeEditor) {
        DistractionFreeManuscriptEditor(
            title = "Narrative Script Editor",
            initialText = audioInput,
            onSave = { updatedText ->
                viewModel.audioInputText.value = updatedText
                showAudioDistractionFreeEditor = false
            },
            onDismiss = { showAudioDistractionFreeEditor = false }
        )
    }

    if (showSaveScriptDialog) {
        AlertDialog(
            onDismissRequest = { showSaveScriptDialog = false },
            title = { Text("Save Script to Library") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This will create a new Story entry in your library using this script content.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = scriptSaveTitle,
                        onValueChange = { scriptSaveTitle = it },
                        label = { Text("Project Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveAudioScriptToLibrary(scriptSaveTitle) {
                            showSaveScriptDialog = false
                            Toast.makeText(context, "Saved to Library as '$scriptSaveTitle'", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveScriptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StylizedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}


@Composable
fun CharacterVoiceConfigItem(
    config: com.example.ui.viewmodel.CharacterVoiceConfig,
    availableVoices: List<com.example.tts.VoiceItem>,
    onUpdate: (com.example.ui.viewmodel.CharacterVoiceConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${config.role} • ${config.vocalTraits}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Voice Selector
                Text("Assigned Voice", style = MaterialTheme.typography.labelSmall)
                Box(modifier = Modifier.fillMaxWidth()) {
                    val displayVoice = availableVoices.find { it.name == config.voiceName }?.displayName ?: config.voiceName
                    OutlinedButton(
                        onClick = { voiceDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(displayVoice)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = voiceDropdownExpanded,
                        onDismissRequest = { voiceDropdownExpanded = false }
                    ) {
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice.displayName) },
                                onClick = {
                                    config.voiceName = voice.name
                                    onUpdate(config)
                                    voiceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pitch & Rate Sliders
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pitch", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.1f", config.pitch), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = config.pitch,
                            onValueChange = { 
                                config.pitch = it
                                onUpdate(config)
                            },
                            valueRange = -5f..5f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rate", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.1fx", config.rate), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = config.rate,
                            onValueChange = { 
                                config.rate = it
                                onUpdate(config)
                            },
                            valueRange = 0.5f..2.0f
                        )
                    }
                }
            }
        }
    }
}


data class VocalPreset(
    val genreName: String,
    val recommendedVoiceKeyword: String,
    val rate: Float,
    val description: String
)

val genresPresets = listOf(
    VocalPreset("Tragic Love", "en-us-x-sfg", 0.85f, "Slow-paced, deep, emotional Female voice"),
    VocalPreset("Cheating Revenge", "en-gb-x-rjs", 1.15f, "Fast-paced, sharp, articulate and cold Male voice"),
    VocalPreset("Rebirth", "en-us-x-iog", 1.10f, "Energetic, clear, inspiring Female tone"),
    VocalPreset("Billionaire", "en-us-x-tpd", 0.95f, "Deep, highly formal, calm Male voice"),
    VocalPreset("System Power-Up", "en-us-x-adm", 1.20f, "Very fast-paced, enthusiastic, hyped narrator Male voice"),
    VocalPreset("Comedy", "en-us-x-jen", 1.05f, "Playful, bright, warm and cheerful Female voice"),
    VocalPreset("Titillation", "en-us-x-ava", 0.90f, "Slow, warm, whispering or close-proximity Female voice")
)
