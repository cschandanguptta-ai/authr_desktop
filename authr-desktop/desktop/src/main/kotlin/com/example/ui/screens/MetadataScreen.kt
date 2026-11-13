package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun MetadataScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val inputText by viewModel.metaInputText.collectAsState()
    val title by viewModel.metaTitle.collectAsState()
    val desc by viewModel.metaDesc.collectAsState()
    val tags by viewModel.metaTags.collectAsState()
    val thumbPrompt by viewModel.metaThumbPrompt.collectAsState()
    val isGenerating by viewModel.isGeneratingMeta.collectAsState()

    val artBase64 by viewModel.metaGeneratedArtBase64.collectAsState()
    val isGeneratingArt by viewModel.isGeneratingArt.collectAsState()
    val artError by viewModel.artGenerationError.collectAsState()

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
    }

    // UI-UX desktop pass (2026-11-13): readable centered column instead of
    // the stretched phone-form.
    com.example.ui.components.DesktopContentPane(modifier = modifier) {
    Column(
        modifier = Modifier
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
                text = "AI Publishing SEO Studio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { 
                    viewModel.exportProjectZip(context, null, inputText, {
                        Toast.makeText(context, "Exported: $it", Toast.LENGTH_LONG).show()
                    }, {
                        Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
                    })
                },
                modifier = Modifier.testTag("export_metadata_icon")
            ) {
                Icon(Icons.Default.Download, contentDescription = "Export Project")
            }
        }

        val metaFilePickerLauncher = rememberLauncherForActivityResult(
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

        // High-Fidelity Studio Document Canvas (Publishing SEO Hub)
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
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (inputText.isEmpty()) {
                    // Empty state upload area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { metaFilePickerLauncher.launch("*/*") }
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
                                text = "Load Novel Story Outline",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Upload story.txt to analyze and generate rich metadata",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        Button(
                            onClick = { metaFilePickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("upload_metadata_txt_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Files", style = MaterialTheme.typography.labelMedium)
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
                                    text = "Active SEO Novel Canvas",
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
                                onClick = { metaFilePickerLauncher.launch("*/*") },
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
                                onClick = { viewModel.metaInputText.value = "" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_metadata_txt_button")
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
                    onValueChange = { viewModel.metaInputText.value = it },
                    label = { Text("Source Novel Text") },
                    placeholder = { Text("Paste or import story outline to analyze...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("metadata_source_field"),
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

        // Generate Metadata Button
        Button(
            onClick = { viewModel.generateMetadata() },
            enabled = inputText.isNotEmpty() && !isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_metadata_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate SEO Campaign & Art Prompt")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Title Row Output
        if (title.isNotEmpty()) {
            Text("Generated Marketing Campaign Items", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            // YouTube Title Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("YouTube-Optimized Video Title", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { copyToClipboard("Title", title) }, modifier = Modifier.testTag("copy_meta_title")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    }
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SEO Search Description & Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { copyToClipboard("Description", desc) }, modifier = Modifier.testTag("copy_meta_desc")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    }
                    Text(desc, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Tags Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("High-Reach Metadata Tags", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { copyToClipboard("Tags", tags) }, modifier = Modifier.testTag("copy_meta_tags")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    }
                    Text(tags, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Thumbnail Visual Art Prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Midjourney / Stable Diffusion Art Prompt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                        IconButton(onClick = { copyToClipboard("Art Prompt", thumbPrompt) }, modifier = Modifier.testTag("copy_meta_art")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    }
                    Text(thumbPrompt, style = MaterialTheme.typography.bodyMedium)

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    Text("Rendered Cover Art", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)

                    val imageBitmap = remember(artBase64) {
                        artBase64?.let { base64Str ->
                            try {
                                val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }

                    if (imageBitmap != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Generated Cover Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    } else if (isGeneratingArt) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text("Rendering visual masterpiece...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Artwork not yet generated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (artError != null) {
                        Text(
                            text = "Error: $artError",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.generateCoverArt() },
                        enabled = thumbPrompt.isNotEmpty() && !isGeneratingArt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_art_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        if (isGeneratingArt) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                        } else {
                            Icon(Icons.Default.Palette, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (imageBitmap != null) "Regenerate Cover Art" else "Generate Cover Art")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting source text to analyze...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
    } // end DesktopContentPane
}
