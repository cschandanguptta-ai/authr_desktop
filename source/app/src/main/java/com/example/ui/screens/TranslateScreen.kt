package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.components.DistractionFreeManuscriptEditor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val inputText by viewModel.transInputText.collectAsState()
    val outputText by viewModel.transOutputText.collectAsState()
    val selectedLang by viewModel.transSelectedLang.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val useXmlBoundaries by viewModel.transUseXmlBoundaries.collectAsState()
    val outputRaw by viewModel.transOutputRaw.collectAsState()

    var showInputDistractionFreeEditor by remember { mutableStateOf(false) }
    var showOutputDistractionFreeEditor by remember { mutableStateOf(false) }
    var showRawOutputMode by remember { mutableStateOf(false) }

    val availableLangs = listOf(
        "Hindi", "Spanish", "Portuguese", "French", "German", 
        "Japanese", "Mandarin", "Korean", "Telugu", "Tamil", 
        "Kannada", "Malayalam", "Bengali", "Marathi", "Gujarati", 
        "Punjabi", "Odia", "Russian", "Indonesian", "Vietnamese", 
        "Thai", "Tagalog", "Arabic"
    )

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Translated Novel Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "AI Translation Hub",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val transFilePickerLauncher = rememberLauncherForActivityResult(
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

        // High-Fidelity Studio Document Canvas (Translate Hub)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (inputText.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        ) {
    
        var showYoutubeDialog by remember { mutableStateOf(false) }

        if (showYoutubeDialog) {
            var youtubeUrl by remember { mutableStateOf("") }
            val isImporting by viewModel.isImportingYoutube.collectAsState()
            
            AlertDialog(
                onDismissRequest = { if (!isImporting) showYoutubeDialog = false },
                title = { Text("Import from YouTube") },
                text = {
                    Column {
                        Text("Paste a YouTube URL. The AI will use the video's title and description to generate a summary or story (direct transcript extraction is blocked by YouTube).", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = youtubeUrl,
                            onValueChange = { youtubeUrl = it },
                            label = { Text("YouTube URL") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isImporting
                        )
                        if (isImporting) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Importing...")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.importFromYoutube(youtubeUrl)
                            showYoutubeDialog = false
                        },
                        enabled = youtubeUrl.isNotBlank() && !isImporting
                    ) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showYoutubeDialog = false }, enabled = !isImporting) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (inputText.isEmpty()) {
                    // Empty state upload area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { transFilePickerLauncher.launch("*/*") }
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
                                text = "Load Translation Source Text",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Upload translation.txt to translate your story outline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { transFilePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("upload_translate_txt_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Files", style = MaterialTheme.typography.labelMedium)
                            }
                            
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showYoutubeDialog = true },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("import_youtube_button"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("YouTube", style = MaterialTheme.typography.labelMedium)
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
                                    text = "Active Translation Canvas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val words = inputText.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                                Text(
                                    text = "${inputText.length} chars • $words words loaded",
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
                                onClick = { showYoutubeDialog = true },
                                modifier = Modifier.size(32.dp).testTag("import_youtube_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = "Import from YouTube",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showInputDistractionFreeEditor = true },
                                modifier = Modifier.size(32.dp).testTag("source_full_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Full-screen Distraction-free Editor",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { transFilePickerLauncher.launch("*/*") },
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
                                onClick = { viewModel.transInputText.value = "" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_translate_txt_button")
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

                // Input field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.transInputText.value = it },
                    label = { Text("Source English Script") },
                    placeholder = { Text("Paste or import text to translate...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("translate_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    maxLines = 12
                )
            }
        }

        // Language chips
        Text("Target Language Selection", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableLangs.forEach { lang ->
                val isSelected = selectedLang == lang
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.transSelectedLang.value = lang },
                    label = { Text(lang) },
                    modifier = Modifier.testTag("lang_chip_$lang")
                )
            }
        }

        // XML Boundaries Configuration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("xml_boundaries_config_card"),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "XML Boundaries Integration",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Protects structural variables, code blocks, or placeholders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Switch(
                        checked = useXmlBoundaries,
                        onCheckedChange = { viewModel.transUseXmlBoundaries.value = it },
                        modifier = Modifier.testTag("xml_boundaries_switch")
                    )
                }

                if (useXmlBoundaries) {
                    Text(
                        text = "• System wraps literary text inside <text_to_translate> tags.\n" +
                               "• Instructs translation agents to preserve formatting codes and system placeholders like {protagonist}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Action Buttons
        Button(
            onClick = { viewModel.translateText() },
            enabled = inputText.isNotEmpty() && !isTranslating,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_translation_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Translate, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate to $selectedLang")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Translation Output
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                        text = "Translation Output ($selectedLang)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (outputText.isNotEmpty() && !outputText.startsWith("Translating")) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (useXmlBoundaries && outputRaw.isNotEmpty()) {
                                // Toggle for Raw XML vs Parsed/Clean output
                                TextButton(
                                    onClick = { showRawOutputMode = !showRawOutputMode },
                                    modifier = Modifier.testTag("toggle_raw_parsed_chip")
                                ) {
                                    Text(
                                        text = if (showRawOutputMode) "Show Parsed" else "Show Raw XML",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showOutputDistractionFreeEditor = true },
                                modifier = Modifier.testTag("edit_translation_df_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Translation", tint = MaterialTheme.colorScheme.primary)
                            }

                            val textToCopy = if (showRawOutputMode && outputRaw.isNotEmpty()) outputRaw else outputText
                            IconButton(
                                onClick = { copyToClipboard(textToCopy) },
                                modifier = Modifier.testTag("copy_translation_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Translation")
                            }
                        }
                    }
                }

                val textToShow = if (showRawOutputMode && useXmlBoundaries && outputRaw.isNotEmpty()) {
                    outputRaw
                } else {
                    outputText
                }

                Text(
                    text = textToShow.ifEmpty { "Translated content will appear here after clicking translate..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (textToShow.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Send back to Audio tab
        if (outputText.isNotEmpty() && !outputText.startsWith("Translating") && !outputText.startsWith("Error")) {
            val mappedVoice = viewModel.getVoiceItemForLanguage(selectedLang)
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "AI Native Vocal Dubbing Suite",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "DUBBING READY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                    
                    Text(
                        text = "The Dubbing Suite has analyzed your target language ($selectedLang) and mapped it to the optimal native voice model for a high-quality localized release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Voice info badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mappedVoice != null) Icons.Default.Mic else Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (mappedVoice != null) "Mapped Native Voice:" else "Voice Status:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = mappedVoice?.displayName ?: "No native model found (Falling back to system standard)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (mappedVoice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerLocalizedDubbing(outputText, selectedLang)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("auto_dub_localization_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Dub & Localize", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                viewModel.applyTranslationToMainStory()
                                
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("send_translation_to_tts_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply & Save", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveTranslationAsLocalizedStory()
                            Toast.makeText(context, "Saved as localized project: ${selectedLang.uppercase()}_STORY", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_localized_story_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Localized Project (${selectedLang.uppercase()}_STORY)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showInputDistractionFreeEditor) {
        DistractionFreeManuscriptEditor(
            title = "Source English Script",
            initialText = inputText,
            onSave = { updatedText ->
                viewModel.transInputText.value = updatedText
                showInputDistractionFreeEditor = false
            },
            onDismiss = { showInputDistractionFreeEditor = false }
        )
    }

    if (showOutputDistractionFreeEditor) {
        DistractionFreeManuscriptEditor(
            title = "Translation Output ($selectedLang)",
            initialText = outputText,
            onSave = { updatedText ->
                viewModel.transOutputText.value = updatedText
                showOutputDistractionFreeEditor = false
            },
            onDismiss = { showOutputDistractionFreeEditor = false }
        )
    }
}
