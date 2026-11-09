package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Book
import com.example.data.model.Story
import com.example.ui.components.DistractionFreeManuscriptEditor
import com.example.ui.viewmodel.StudioViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryDashboardScreen(
    viewModel: StudioViewModel,
    onCreateNewBook: () -> Unit,
    onBookSelected: (Book) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val books by viewModel.allBooks.collectAsState()
    val savedStories by viewModel.allStories.collectAsState()
    
    var selectedStoryInLibrary by remember { mutableStateOf<Story?>(null) }
    var editingLibraryStoryPart by remember { mutableStateOf<Pair<Story, Int>?>(null) }
    var activeLibraryTab by remember { mutableIntStateOf(0) } // 0: Wizard Drafts, 1: Autonomous Books

    // If a story is selected, show the full Chapter Review & Edit panel
    if (selectedStoryInLibrary != null) {
        val story = selectedStoryInLibrary!!
        val storyPartsList = listOf(story.part1, story.part2, story.part3, story.part4, story.part5)
        val totalWords = storyPartsList.joinToString(" ").split("\\s+".toRegex()).count { it.isNotEmpty() }
        val completedCount = storyPartsList.count { it.isNotEmpty() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = story.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedStoryInLibrary = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.scheduleEpubExportWorker(context, storyId = story.id)
                                Toast.makeText(context, "Exporting EPUB in background...", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export EPUB")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Stats Summary card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            Text("${storyPartsList.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Box(modifier = Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$completedCount / ${storyPartsList.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(storyPartsList.size) { idx ->
                        val chText = storyPartsList[idx]
                        val isChCompleted = chText.isNotEmpty()
                        val chWordCount = if (isChCompleted) chText.split("\\s+".toRegex()).count { it.isNotEmpty() } else 0
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChCompleted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                                editingLibraryStoryPart = Pair(story, idx)
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
                                        IconButton(
                                            onClick = {
                                                if (isChCompleted) {
                                                    viewModel.enqueueTtsChapter("${story.title} - Chapter ${idx + 1}", chText)
                                                    Toast.makeText(context, "Chapter ${idx + 1} queued for TTS synthesis!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = isChCompleted,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = "Queue for TTS",
                                                tint = if (isChCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (isChCompleted) {
                                    Text(
                                        text = if (chText.length > 250) chText.take(250) + "..." else chText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = MaterialTheme.colorScheme.onSurface
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

                // Bottom Actions (Load Draft, Copy Manuscript, Voice synthesis)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.loadStoryIntoParams(story)
                            viewModel.selectBook(null)
                            viewModel.activeWorkspaceTab.value = 0
                            viewModel.selectTab(1) // Go to Active Workspace tab
                            Toast.makeText(context, "Loaded \"${story.title}\" into creative workspace!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1.1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Draft", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            val full = storyPartsList.filter { it.isNotEmpty() }.joinToString("\n\n---\n\n")
                            if (full.isNotEmpty()) {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Full Manuscript", full)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Full manuscript copied!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Manuscript is empty!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy All", style = MaterialTheme.typography.labelMedium)
                    }

                    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
                    OutlinedButton(
                        onClick = {
                            if (isTtsPlaying) {
                                viewModel.stopSpeaking()
                            } else {
                                val previewText = story.part1.ifEmpty { story.getCombinedStory() }
                                viewModel.audioInputText.value = previewText
                                viewModel.speakPreview()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTtsPlaying) "Stop" else "Quick Play",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Handle inline editor
        if (editingLibraryStoryPart != null) {
            val (editStory, partIdx) = editingLibraryStoryPart!!
            val partText = when (partIdx) {
                0 -> editStory.part1
                1 -> editStory.part2
                2 -> editStory.part3
                3 -> editStory.part4
                else -> editStory.part5
            }
            DistractionFreeManuscriptEditor(
                title = "${editStory.title} - Chapter ${partIdx + 1}",
                initialText = partText,
                onSave = { updatedText ->
                    val updatedStory = when (partIdx) {
                        0 -> editStory.copy(part1 = updatedText)
                        1 -> editStory.copy(part2 = updatedText)
                        2 -> editStory.copy(part3 = updatedText)
                        3 -> editStory.copy(part4 = updatedText)
                        else -> editStory.copy(part5 = updatedText)
                    }
                    viewModel.updateStoryInDb(updatedStory)
                    selectedStoryInLibrary = updatedStory
                    Toast.makeText(context, "Chapter ${partIdx + 1} updated!", Toast.LENGTH_SHORT).show()
                    editingLibraryStoryPart = null
                },
                onDismiss = { editingLibraryStoryPart = null }
            )
        }
    } else {
        Scaffold(
            floatingActionButton = {
                if (activeLibraryTab == 1) {
                    FloatingActionButton(onClick = onCreateNewBook) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Book")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Saved Project Library",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                TabRow(
                    selectedTabIndex = activeLibraryTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeLibraryTab == 0,
                        onClick = { activeLibraryTab = 0 },
                        
                        text = { Text("Wizard Drafts", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = activeLibraryTab == 1,
                        onClick = { activeLibraryTab = 1 },
                        
                        text = { Text("Autonomous Books", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeLibraryTab == 0) {
                        // Display Wizard Drafts list
                        
                        val autosaveTime by viewModel.lastAutosaveTime.collectAsState()
                        val hasAutosave = autosaveTime != null
                        
                        if (savedStories.isEmpty() && !hasAutosave) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No drafts found.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Save stories in the Creative Workspace to view them here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (hasAutosave) {
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    viewModel.activeWorkspaceTab.value = 0
                                                    viewModel.selectTab(1) 
                                                },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Default.BuildCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                        Text("Active Interactive Workspace Draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Last autosaved: $autosaveTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                }
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                                
                                items(savedStories) { story ->
                                    StoryItemCard(
                                        story = story,
                                        onClick = { selectedStoryInLibrary = story },
                                        onLoad = {
                                            viewModel.loadStoryIntoParams(story)
                                            viewModel.selectBook(null)
                                            viewModel.activeWorkspaceTab.value = 0
                                            viewModel.selectTab(1)
                                            Toast.makeText(context, "Loaded \"${story.title}\" into creative workspace!", Toast.LENGTH_LONG).show()
                                        },
                                        onDelete = { viewModel.deleteStory(story) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Display Autonomous Books list
                        if (books.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No books yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Click + or launch background tasks in the Autonomous Book Studio to compile books.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(books) { book ->
                                    BookItemCard(
                                        book = book,
                                        onClick = { onBookSelected(book) },
                                        onDelete = { viewModel.deleteBook(book.id) },
                                        onExportEpub = {
                                            viewModel.scheduleEpubExportWorker(context, bookId = book.id)
                                            Toast.makeText(context, "Exporting EPUB in background...", Toast.LENGTH_SHORT).show()
                                        }
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

@Composable
fun StoryItemCard(
    story: Story,
    onClick: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${story.format} • ${story.genres.replace("|", ", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Draft",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("${story.wordCount} words") }
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (story.persona) {
                                "NightKing" -> "/NightKing"
                                "Mastram" -> "/mastram"
                                "Hitchcock" -> "/hitchcock"
                                "Poe" -> "/poe"
                                "BlackMirror" -> "/blackmirror"
                                "Lovecraft" -> "/lovecraft"
                                "MeetCute" -> "/meet-cute"
                                "Synapse" -> "/synapse"
                                else -> "Standard"
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Saved: ${formatTimestamp(story.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load to Workspace", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chapters & Edit", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun BookItemCard(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExportEpub: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = book.genre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = "Phase: ${book.currentPhase}", style = MaterialTheme.typography.bodySmall)
                if (book.currentPhase != "Completed") {
                    LinearProgressIndicator(
                        progress = { book.completionPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onExportEpub != null) {
                    IconButton(onClick = onExportEpub) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export EPUB",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Book", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
