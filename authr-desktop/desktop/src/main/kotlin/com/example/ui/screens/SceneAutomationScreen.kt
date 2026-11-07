package com.example.ui.screens
import androidx.compose.animation.core.animateFloat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneAutomationScreen(viewModel: StudioViewModel) {
    val context = LocalContext.current
    val videoImages by viewModel.videoImages.collectAsState()
    val focalPoints by viewModel.videoImageFocalPoints.collectAsState()
    val imageDuration by viewModel.videoImageDuration.collectAsState()
    val kenBurnsEnabled by viewModel.videoKenBurnsEnabled.collectAsState()
    val isGenerating by viewModel.isGeneratingVideo.collectAsState()
    val logs by viewModel.videoGenerationLogs.collectAsState()

    var showFocalPointModal by remember { mutableStateOf<Uri?>(null) }
    val eqEnabled by viewModel.videoEqualizerEnabled.collectAsState()
    val eqStyle by viewModel.videoEqualizerStyle.collectAsState()
    val aspectRatioStr by viewModel.videoAspectRatio.collectAsState()
    
    
    

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addVideoImages(uris)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Scene Automation & Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Configuration Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("YouTube Shorts / Long-form Audiobook Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Automatically compile generated audio and uploaded images into a vertical 1080x1920 video with Smart Looping.", style = MaterialTheme.typography.bodySmall)

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Display Time per Image (s): ${imageDuration.toInt()}")
                        Slider(
                            value = imageDuration,
                            onValueChange = { viewModel.videoImageDuration.value = it },
                            valueRange = 1f..30f,
                            modifier = Modifier.width(150.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ken Burns Effect (Slow Zoom)")
                            Text("Adds subtle motion to static images", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = kenBurnsEnabled,
                            onCheckedChange = { viewModel.videoKenBurnsEnabled.value = it }
                        )
                    }
                    
                    HorizontalDivider()
                    Text("Video Export Quality", fontWeight = FontWeight.Bold)
                    val resolution by viewModel.videoResolution.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("144p", "240p", "360p").forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { viewModel.videoResolution.value = res },
                                label = { Text(res) }
                            )
                        }
                    }

                    Text("Production Mode", fontWeight = FontWeight.Bold)
                    val prodMode by viewModel.videoProductionMode.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Short", "Long").forEach { mode ->
                            FilterChip(
                                selected = prodMode == mode,
                                onClick = { viewModel.videoProductionMode.value = mode },
                                label = { Text(if (mode == "Short") "Short (≤3 min)" else "Long (Series)") }
                            )
                        }
                    }

                    Text("Aspect Ratio", fontWeight = FontWeight.Bold)

                    

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                        listOf("16:9", "9:16", "1:1").forEach { ratio ->

                            FilterChip(

                                selected = aspectRatioStr == ratio,

                                onClick = { viewModel.videoAspectRatio.value = ratio },

                                label = { Text(ratio) }

                            )

                        }

                    }

                    

                    Text("Animated Waveform / Soundbar Overlay", fontWeight = FontWeight.Bold)

                    

                    

                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {

                        Text("Enable Equalizer Overlay")

                        Switch(checked = eqEnabled, onCheckedChange = { viewModel.videoEqualizerEnabled.value = it })

                    }

                    if (eqEnabled) {

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {

                            listOf("Neon Bars", "Spectrum Wave", "Minimal Dots").forEach { style ->

                                FilterChip(

                                    selected = eqStyle == style,

                                    onClick = { viewModel.videoEqualizerStyle.value = style },

                                    label = { Text(style) }

                                )

                            }

                        }

                    }

                    

                    Text("SRT Subtitle File Export", fontWeight = FontWeight.Bold)

                    val srtEnabled by viewModel.videoExportSrt.collectAsState()

                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {

                        Column {

                            Text("Auto-generate Captions")

                            Text("Export .srt next to video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        }

                        Switch(checked = srtEnabled, onCheckedChange = { viewModel.videoExportSrt.value = it })

                    }

                    val allAudioTracks by viewModel.allAudioTracks.collectAsState(initial = emptyList())
                    val selectedTrack by viewModel.videoSelectedTrack.collectAsState()
                    var trackDropdownExpanded by remember { mutableStateOf(false) }

                    Text("Source Audiobook (Optional)", fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { trackDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedTrack?.title ?: "Select Audiobook (or None)")
                        }
                        DropdownMenu(
                            expanded = trackDropdownExpanded,
                            onDismissRequest = { trackDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Images Only)") },
                                onClick = { 
                                    viewModel.videoSelectedTrack.value = null
                                    trackDropdownExpanded = false 
                                }
                            )
                            allAudioTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = { Text(track.title) },
                                    onClick = {
                                        viewModel.videoSelectedTrack.value = track
                                        trackDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val autoSplit by viewModel.videoAutoSplit.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Story Segmentation")
                            Text("Split into multiple parts if over 3 minutes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSplit,
                            onCheckedChange = { viewModel.videoAutoSplit.value = it }
                        )
                    }

                    Button(
                        onClick = { pickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Random Images")
                    }
                }
            }

            // Focal Point Utility
            if (videoImages.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Focal Point Image Cropper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Click on any thumbnail to set a precise focal point for the Ken Burns effect and vertical crop.", style = MaterialTheme.typography.labelSmall)
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(200.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(videoImages) { uri ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(when(aspectRatioStr) { "16:9" -> 16f/9f; "1:1" -> 1f; else -> 9f/16f })
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showFocalPointModal = uri }
                                        .border(2.dp, if (focalPoints.containsKey(uri)) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (eqEnabled) {

                                        Box(

                                            modifier = Modifier

                                                .fillMaxWidth()

                                                .fillMaxHeight(0.3f)

                                                .align(Alignment.BottomCenter)

                                                .background(Color.Black.copy(alpha = 0.4f)),

                                            contentAlignment = Alignment.Center

                                        ) {

                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(24.dp), verticalAlignment = Alignment.Bottom) {

                                                repeat(8) { i ->

                                                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()

                                                    val height by infiniteTransition.animateFloat(

                                                        initialValue = 0.2f, targetValue = 1f,

                                                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(

                                                            animation = androidx.compose.animation.core.tween(300 + (i % 4) * 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),

                                                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse

                                                        )

                                                    )

                                                    Box(modifier = Modifier.width(when(eqStyle) { "Minimal Dots" -> 4.dp; else -> 6.dp }).fillMaxHeight(if(eqStyle == "Minimal Dots") 0.3f else height).clip(RoundedCornerShape(2.dp)).background(when(eqStyle) { "Neon Bars" -> Color.Cyan; "Spectrum Wave" -> Color.Magenta; else -> Color.White }))

                                                }

                                            }

                                        }

                                    }
                                    if (focalPoints.containsKey(uri)) {
                                        Icon(
                                            Icons.Default.FilterCenterFocus,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.clearVideoImages() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Images")
                        }
                    }
                }
            }

            // Generation Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.generateVideo(context) },
                            enabled = !isGenerating,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MovieCreation, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Video")
                            }
                        }
                        
                        if (isGenerating) {
                            Button(
                                onClick = { viewModel.cancelVideoGeneration() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cancel")
                            }
                        }
                    }

                    Text(
                        text = logs,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(6.dp),
                        color = Color.Green
                    )
                    if (!isGenerating && logs.contains("Output saved to:")) {
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
                                intent.type = "video/*"
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Videos Folder")
                        }
                    }
                }
            }
        }
    }

    // SRT Preview Modal

    val generatedSrt by viewModel.videoGeneratedSrtContent.collectAsState()

    if (generatedSrt != null) {

        Dialog(onDismissRequest = { viewModel.videoGeneratedSrtContent.value = null }) {

            Card(modifier = Modifier.fillMaxWidth().padding(6.dp)) {

                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

                    Text("Generated Subtitles (SRT)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    Text("You can copy these subtitles to clipboard for YouTube upload.", style = MaterialTheme.typography.bodySmall)

                    val scrollState = rememberScrollState()

                    Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().background(Color.Black.copy(alpha=0.8f)).padding(6.dp).verticalScroll(scrollState)) {

                        Text(generatedSrt ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Green)

                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {

                        val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                        Button(onClick = {

                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SRT Subtitles", generatedSrt))

                            android.widget.Toast.makeText(context, "Copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()

                        }) {

                            Text("Copy to Clipboard")

                        }

                        TextButton(onClick = { viewModel.videoGeneratedSrtContent.value = null }) {

                            Text("Close")

                        }

                    }

                }

            }

        }

    }

    // Focal Point Modal
    showFocalPointModal?.let { uri ->
        Dialog(onDismissRequest = { showFocalPointModal = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Focal Point", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var imageSize by remember { mutableStateOf(IntSize.Zero) }
                    val currentFocalPoint = focalPoints[uri]
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.Black)
                            .onSizeChanged { imageSize = it }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val x = (offset.x / imageSize.width).coerceIn(0f, 1f)
                                    val y = (offset.y / imageSize.height).coerceIn(0f, 1f)
                                    viewModel.setFocalPoint(uri, Offset(x, y))
                                }
                            }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (currentFocalPoint != null && imageSize != IntSize.Zero) {
                            Icon(
                                Icons.Default.FilterCenterFocus,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier
                                    .offset(
                                        x = (currentFocalPoint.x * imageSize.width).dp / LocalContext.current.resources.displayMetrics.density - 12.dp,
                                        y = (currentFocalPoint.y * imageSize.height).dp / LocalContext.current.resources.displayMetrics.density - 12.dp
                                    )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showFocalPointModal = null }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
