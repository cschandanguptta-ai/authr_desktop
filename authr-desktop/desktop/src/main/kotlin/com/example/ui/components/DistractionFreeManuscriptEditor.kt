package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

enum class EditorTheme { OBSIDIAN, CHARCOAL, SEPIA, CREAM }
enum class FontFamilyChoice { SERIF, SANS_SERIF, MONOSPACE }

data class EditorColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val primary: Color,
    val secondary: Color
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DistractionFreeManuscriptEditor(
    title: String,
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Current draft state
    var currentText by remember { mutableStateOf(initialText) }
    var isEditMode by remember { mutableStateOf(false) }
    
    // Preferences states
    var selectedTheme by remember { mutableStateOf(EditorTheme.OBSIDIAN) }
    var selectedFontFamily by remember { mutableStateOf(FontFamilyChoice.SERIF) }
    var fontSizeSp by remember { mutableStateOf(18f) }
    var lineSpacingMultiplier by remember { mutableStateOf(1.5f) }
    
    // UI visibility states
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showExitWarning by remember { mutableStateOf(false) }
    
    // Search states
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var findResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentResultIndex by remember { mutableStateOf(-1) }
    
    // Undo / Redo states
    val history = remember { mutableStateListOf(initialText) }
    var historyPointer by remember { mutableStateOf(0) }
    
    // Determine colors based on active theme choice
    val editorColors = when (selectedTheme) {
        EditorTheme.OBSIDIAN -> EditorColors(
            background = Color(0xFF0F0F0F), // Black background
            surface = Color(0xFF1E1E1E), // Dark gray surface
            onBackground = Color(0xFFE2E2E2), // Crisp gray text
            onSurface = Color(0xFFF5F5F5), // Light gray header text
            primary = Color(0xFFBB86FC), // Pastel Purple
            secondary = Color(0xFF03DAC6)  // Teal
        )
        EditorTheme.CHARCOAL -> EditorColors(
            background = Color(0xFF1A1C1E), // Slate dark
            surface = Color(0xFF25282B), // Lighter slate surface
            onBackground = Color(0xFFECEFF1), // Near white text
            onSurface = Color(0xFFFFFFFF), // Pure white
            primary = Color(0xFF82B1FF), // Soft blue
            secondary = Color(0xFF448AFF)  // Ocean blue
        )
        EditorTheme.SEPIA -> EditorColors(
            background = Color(0xFFF4ECD8), // Warm beige
            surface = Color(0xFFEADBBE), // Warm brown border/surface
            onBackground = Color(0xFF4E3629), // Rich brown text
            onSurface = Color(0xFF3E2723), // Dark mahogany
            primary = Color(0xFF8D5B4C), // Terracotta primary
            secondary = Color(0xFF5D4037)  // Coffee brown
        )
        EditorTheme.CREAM -> EditorColors(
            background = Color(0xFFFAF9F6), // Alabaster/cream
            surface = Color(0xFFF2F0EA), // Soft gray-cream surface
            onBackground = Color(0xFF2D3748), // Dark charcoal text
            onSurface = Color(0xFF1A202C), // Ink black header text
            primary = Color(0xFF3182CE), // Classic blue
            secondary = Color(0xFF2B6CB0)  // Deep classic blue
        )
    }

    val backgroundColor = editorColors.background
    val surfaceColor = editorColors.surface
    val onBackgroundColor = editorColors.onBackground
    val onSurfaceColor = editorColors.onSurface
    val primaryColor = editorColors.primary
    val secondaryColor = editorColors.secondary

    val font = when (selectedFontFamily) {
        FontFamilyChoice.SERIF -> FontFamily.Serif
        FontFamilyChoice.SANS_SERIF -> FontFamily.SansSerif
        FontFamilyChoice.MONOSPACE -> FontFamily.Monospace
    }

    // Live word & character counters
    val wordCount = remember(currentText) {
        currentText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }
    val charCount = currentText.length
    val readingTimeMinutes = remember(wordCount) {
        kotlin.math.max(1, (wordCount / 225.0).toInt())
    }

    // History tracker function
    val recordHistoryState: (String) -> Unit = { newText ->
        if (newText != currentText) {
            currentText = newText
            val lastHistoryText = history.getOrNull(historyPointer) ?: ""
            if (newText != lastHistoryText) {
                // If the last character is a space/newline or change size is significant, push to history
                val lastChar = newText.lastOrNull()
                if (lastChar == ' ' || lastChar == '\n' || abs(newText.length - lastHistoryText.length) > 12) {
                    while (history.size > historyPointer + 1) {
                        history.removeAt(history.size - 1)
                    }
                    history.add(newText)
                    historyPointer = history.size - 1
                }
            }
        }
    }

    // Force commit current text to history before undo/redo triggers
    val forcePushHistory: () -> Unit = {
        val lastHistoryText = history.getOrNull(historyPointer) ?: ""
        if (currentText != lastHistoryText) {
            while (history.size > historyPointer + 1) {
                history.removeAt(history.size - 1)
            }
            history.add(currentText)
            historyPointer = history.size - 1
        }
    }

    // Find and Replace search function
    val runSearch: () -> Unit = {
        if (findText.isEmpty()) {
            findResults = emptyList()
            currentResultIndex = -1
        } else {
            val results = mutableListOf<Int>()
            var idx = currentText.indexOf(findText, 0, ignoreCase = true)
            while (idx != -1) {
                results.add(idx)
                idx = currentText.indexOf(findText, idx + findText.length, ignoreCase = true)
            }
            findResults = results
            currentResultIndex = if (results.isNotEmpty()) 0 else -1
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentText != initialText) {
                showExitWarning = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("distraction_free_editor_surface"),
            color = backgroundColor
        ) {
            Scaffold(
                containerColor = backgroundColor,
                topBar = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentText != initialText) {
                                            showExitWarning = true
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.testTag("editor_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Editor",
                                        tint = onBackgroundColor
                                    )
                                }
                                Column {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = onSurfaceColor
                                    )
                                    Text(
                                        text = if (isEditMode) "Editing Mode" else "Reading Mode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryColor
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Undo / Redo (Only visible in edit mode)
                                if (isEditMode) {
                                    IconButton(
                                        onClick = {
                                            forcePushHistory()
                                            if (historyPointer > 0) {
                                                historyPointer--
                                                currentText = history[historyPointer]
                                            }
                                        },
                                        enabled = historyPointer > 0,
                                        modifier = Modifier.testTag("editor_undo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = if (historyPointer > 0) onBackgroundColor else onBackgroundColor.copy(alpha = 0.3f)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (historyPointer < history.size - 1) {
                                                historyPointer++
                                                currentText = history[historyPointer]
                                            }
                                        },
                                        enabled = historyPointer < history.size - 1,
                                        modifier = Modifier.testTag("editor_redo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "Redo",
                                            tint = if (historyPointer < history.size - 1) onBackgroundColor else onBackgroundColor.copy(alpha = 0.3f)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showFindReplace = !showFindReplace },
                                        modifier = Modifier.testTag("editor_find_replace_toggle")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Find and Replace",
                                            tint = if (showFindReplace) primaryColor else onBackgroundColor
                                        )
                                    }
                                }

                                // Toggle Reading / Editing mode
                                IconButton(
                                    onClick = { isEditMode = !isEditMode },
                                    modifier = Modifier.testTag("editor_mode_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Book else Icons.Default.Edit,
                                        contentDescription = if (isEditMode) "Switch to Reading" else "Switch to Editing",
                                        tint = primaryColor
                                    )
                                }

                                // Layout Options Menu
                                IconButton(
                                    onClick = { showSettingsMenu = true },
                                    modifier = Modifier.testTag("editor_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Text Settings",
                                        tint = onBackgroundColor
                                    )
                                }

                                // Save Changes
                                Button(
                                    onClick = {
                                        onSave(currentText)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("editor_save_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        tint = backgroundColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save", color = backgroundColor, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        HorizontalDivider(color = onBackgroundColor.copy(alpha = 0.12f))

                        // Find & Replace Panel (Slid-in)
                        if (showFindReplace && isEditMode) {
                            Surface(
                                color = surfaceColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = findText,
                                            onValueChange = {
                                                findText = it
                                                runSearch()
                                            },
                                            label = { Text("Find Text", color = onBackgroundColor.copy(alpha = 0.7f)) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("editor_find_input"),
                                            textStyle = TextStyle(color = onBackgroundColor, fontSize = 14.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = primaryColor,
                                                unfocusedBorderColor = onBackgroundColor.copy(alpha = 0.2f),
                                                focusedLabelColor = primaryColor
                                            ),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = replaceText,
                                            onValueChange = { replaceText = it },
                                            label = { Text("Replace With", color = onBackgroundColor.copy(alpha = 0.7f)) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("editor_replace_input"),
                                            textStyle = TextStyle(color = onBackgroundColor, fontSize = 14.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = primaryColor,
                                                unfocusedBorderColor = onBackgroundColor.copy(alpha = 0.2f),
                                                focusedLabelColor = primaryColor
                                            ),
                                            singleLine = true
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (findText.isNotEmpty()) {
                                            val currentMatch = if (findResults.isNotEmpty()) currentResultIndex + 1 else 0
                                            Text(
                                                text = "Match $currentMatch of ${findResults.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = onBackgroundColor.copy(alpha = 0.7f)
                                            )
                                        } else {
                                            Spacer(Modifier.width(1.dp))
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(
                                                onClick = {
                                                    if (findResults.isNotEmpty()) {
                                                        currentResultIndex = (currentResultIndex + 1) % findResults.size
                                                    }
                                                },
                                                enabled = findResults.isNotEmpty()
                                            ) {
                                                Text("Find Next", color = primaryColor)
                                            }

                                            TextButton(
                                                onClick = {
                                                    if (currentResultIndex in findResults.indices) {
                                                        val startIdx = findResults[currentResultIndex]
                                                        val beforeText = currentText.substring(0, startIdx)
                                                        val afterText = currentText.substring(startIdx + findText.length)
                                                        currentText = beforeText + replaceText + afterText
                                                        runSearch()
                                                    }
                                                },
                                                enabled = findResults.isNotEmpty() && currentResultIndex != -1
                                            ) {
                                                Text("Replace", color = secondaryColor)
                                            }

                                            TextButton(
                                                onClick = {
                                                    if (findText.isNotEmpty()) {
                                                        currentText = currentText.replace(findText, replaceText, ignoreCase = true)
                                                        runSearch()
                                                    }
                                                },
                                                enabled = findText.isNotEmpty()
                                            ) {
                                                Text("Replace All", color = secondaryColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    // Status info bar
                    Surface(
                        color = surfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HorizontalDivider(color = onBackgroundColor.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$wordCount Words",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onBackgroundColor.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "$charCount Chars",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onBackgroundColor.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "~$readingTimeMinutes Mins Reading",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onBackgroundColor.copy(alpha = 0.6f)
                                    )
                                }

                                if (isEditMode) {
                                    Text(
                                        text = "Editing Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Reading Mode Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(backgroundColor),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .widthIn(max = 800.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isEditMode) {
                            // High fidelity, responsive Distraction-Free Text Field
                            TextField(
                                value = currentText,
                                onValueChange = { recordHistoryState(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .testTag("editor_text_input"),
                                textStyle = TextStyle(
                                    color = onBackgroundColor,
                                    fontFamily = font,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                placeholder = {
                                    Text(
                                        text = "Type or modify your manuscript draft...",
                                        fontFamily = font,
                                        fontSize = fontSizeSp.sp,
                                        color = onBackgroundColor.copy(alpha = 0.4f)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                maxLines = Int.MAX_VALUE
                            )
                        } else {
                            // Pristine typographic reading canvas
                            SelectionContainer(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentText.ifEmpty { "The manuscript is currently empty. Switch to Editing mode to start drafting!" },
                                    fontFamily = font,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                                    color = if (currentText.isEmpty()) onBackgroundColor.copy(alpha = 0.4f) else onBackgroundColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .testTag("editor_reading_text")
                                )
                            }
                        }
                    }
                }
            }

            // Typography & Color Theme Controls Modal Bottom Sheet
            if (showSettingsMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsMenu = false },
                    containerColor = surfaceColor,
                    contentColor = onBackgroundColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Aesthetic Styling Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )

                        // 1. Theme picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Canvas Theme Accent",
                                style = MaterialTheme.typography.labelMedium,
                                color = onBackgroundColor.copy(alpha = 0.8f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                EditorTheme.values().forEach { theme ->
                                    val isSelected = selectedTheme == theme
                                    val (chipBg, chipText) = when (theme) {
                                        EditorTheme.OBSIDIAN -> Pair(Color(0xFF0F0F0F), Color(0xFFE2E2E2))
                                        EditorTheme.CHARCOAL -> Pair(Color(0xFF1A1C1E), Color(0xFFECEFF1))
                                        EditorTheme.SEPIA -> Pair(Color(0xFFF4ECD8), Color(0xFF4E3629))
                                        EditorTheme.CREAM -> Pair(Color(0xFFFAF9F6), Color(0xFF2D3748))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .background(chipBg, RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) primaryColor else onBackgroundColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedTheme = theme }
                                            .testTag("theme_chip_${theme.name}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                            color = chipText,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Font chooser
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Typography Pairing",
                                style = MaterialTheme.typography.labelMedium,
                                color = onBackgroundColor.copy(alpha = 0.8f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FontFamilyChoice.values().forEach { family ->
                                    val isSelected = selectedFontFamily == family
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFontFamily = family },
                                        label = {
                                            Text(
                                                text = when (family) {
                                                    FontFamilyChoice.SERIF -> "Serif (Bookish)"
                                                    FontFamilyChoice.SANS_SERIF -> "Sans-Serif (Modern)"
                                                    FontFamilyChoice.MONOSPACE -> "Monospace"
                                                },
                                                fontFamily = when (family) {
                                                    FontFamilyChoice.SERIF -> FontFamily.Serif
                                                    FontFamilyChoice.SANS_SERIF -> FontFamily.SansSerif
                                                    FontFamilyChoice.MONOSPACE -> FontFamily.Monospace
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("font_chip_${family.name}")
                                    )
                                }
                            }
                        }

                        // 3. Font Size Slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Text Font Size",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onBackgroundColor.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${fontSizeSp.toInt()} sp",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            Slider(
                                value = fontSizeSp,
                                onValueChange = { fontSizeSp = it },
                                valueRange = 14f..32f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor,
                                    inactiveTrackColor = onBackgroundColor.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("editor_font_size_slider")
                            )
                        }

                        // 4. Line Spacing Slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Line Height Spacing",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onBackgroundColor.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${lineSpacingMultiplier}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            Slider(
                                value = lineSpacingMultiplier,
                                onValueChange = { lineSpacingMultiplier = it },
                                valueRange = 1.2f..2.2f,
                                steps = 4,
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor,
                                    inactiveTrackColor = onBackgroundColor.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("editor_line_spacing_slider")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Unsaved Changes Safety Confirmation Dialog
            if (showExitWarning) {
                AlertDialog(
                    onDismissRequest = { showExitWarning = false },
                    containerColor = surfaceColor,
                    titleContentColor = onSurfaceColor,
                    textContentColor = onBackgroundColor,
                    title = {
                        Text(
                            "Unsaved Modifications Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            "You have modified the manuscript. Exiting now will permanently discard all your changes. Would you like to save before leaving?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitWarning = false
                                onSave(currentText)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Save & Exit", color = backgroundColor)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showExitWarning = false
                                onDismiss()
                            }
                        ) {
                            Text("Discard Changes", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    }
}
