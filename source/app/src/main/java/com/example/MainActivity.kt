package com.example

import androidx.compose.ui.res.painterResource
import com.example.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.StoryEditorViewModel
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModel()
    private val settingsViewModel: com.example.ui.viewmodel.SettingsViewModel by viewModel()
    private val storyEditorViewModel: StoryEditorViewModel by viewModel()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permissionsToRequest = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
        enableEdgeToEdge()
        setContent {
            val appTheme by settingsViewModel.appTheme.collectAsState()
            MyApplicationTheme(appTheme = appTheme) {
                val currentTab by viewModel.currentTab.collectAsState()
                        val snackbarHostState = remember { SnackbarHostState() }
                        
                        LaunchedEffect(Unit) {
                            viewModel.globalErrorEvent.collect { msg ->
                                snackbarHostState.showSnackbar(
                                    message = msg,
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                val showLogBook by viewModel.showLogBook.collectAsState()
                val geminiKey by settingsViewModel.geminiApiKey.collectAsState()
                val provider by settingsViewModel.selectedProvider.collectAsState()
                val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()

                BackHandler(enabled = currentTab != 0) {
                    viewModel.selectTab(0)
                }

                if (!hasSeenOnboarding) {
                    OnboardingScreen(viewModel = viewModel)
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val activeWorkspaceTab by viewModel.activeWorkspaceTab.collectAsState()
                        val isWideScreen = maxWidth >= 600.dp

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isWideScreen && activeWorkspaceTab == 0) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    header = {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_authr_logo),
                                                contentDescription = "Authr Logo",
                                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Authr OS",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    val tabs = listOf(
                                        NavigationItem("Library", Icons.Default.Book, "tab_library"),
                                        NavigationItem("Workspace", Icons.Default.Create, "tab_pipeline")
                                    )

                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        tabs.forEachIndexed { index, item ->
                                            NavigationRailItem(
                                                selected = currentTab == index,
                                                onClick = { viewModel.selectTab(index) },
                                                icon = { Icon(item.icon, contentDescription = item.label) },
                                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier.testTag(item.tag + "_rail")
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }

                            Scaffold(
                                modifier = Modifier.weight(1f),
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                topBar = {
                                    TopAppBar(
                                        title = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (!isWideScreen) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_authr_logo),
                                                        contentDescription = "Authr Logo",
                                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                                val sectionTitle = when (currentTab) {
                                                    0 -> "Project Library"
                                                    1 -> "Active Workspace"
                                                    2 -> "Platform Guide"
                                                    3 -> "System Settings"
                                                    else -> "Authr OS"
                                                }
                                                Text(
                                                    text = sectionTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        actions = {
                                            IconButton(onClick = { viewModel.selectTab(2) }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                    contentDescription = "Guide",
                                                    tint = if (currentTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.showLogBook.value = true },
                                                modifier = Modifier.testTag("bug_report_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.BugReport,
                                                    contentDescription = "System Logs / Bug Report",
                                                    tint = if (showLogBook) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(onClick = { viewModel.selectTab(3) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Settings",
                                                    tint = if (currentTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            // Status indicator
                                            val hasKey = geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY"
                                            AssistChip(
                                                onClick = { viewModel.selectTab(3) }, // go to settings
                                                label = {
                                                    Text(
                                                        text = if (hasKey) "$provider" else "Keys Missing",
                                                        color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (hasKey) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                                                        contentDescription = null,
                                                        tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .testTag("status_chip"),
                                                border = AssistChipDefaults.assistChipBorder(
                                                    enabled = true,
                                                    borderColor = if (hasKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                    )
                                },
                                bottomBar = {
                                    val activeTab by viewModel.activeWorkspaceTab.collectAsState()
                                    if (!isWideScreen && activeTab == 0) {
                                        NavigationBar(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ) {
                                            val tabs = listOf(
                                                NavigationItem("Library", Icons.Default.Book, "tab_library"),
                                                NavigationItem("Workspace", Icons.Default.Create, "tab_pipeline")
                                            )

                                            tabs.forEachIndexed { index, item ->
                                                NavigationBarItem(
                                                    selected = currentTab == index,
                                                    onClick = { viewModel.selectTab(index) },
                                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal) },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    ),
                                                    modifier = Modifier.testTag(item.tag)
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .imePadding()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = if (isWideScreen) {
                                            Modifier
                                                .fillMaxHeight()
                                                .widthIn(max = 1000.dp)
                                                .padding(horizontal = 24.dp)
                                        } else {
                                            Modifier.fillMaxSize()
                                        }
                                    ) {
                                        when (currentTab) {
                                            0 -> StoryDashboardScreen(
                                                viewModel = viewModel,
                                                onCreateNewBook = {
                                                    viewModel.selectBook(null)
                                                    viewModel.activeWorkspaceTab.value = 1
                                                    viewModel.selectTab(1)
                                                },
                                                onBookSelected = { book ->
                                                    viewModel.selectBook(book.id)
                                                    viewModel.activeWorkspaceTab.value = 1
                                                    viewModel.selectTab(1)
                                                }
                                            )
                                            1 -> StoryScreen(viewModel = viewModel)
                                            2 -> GuideScreen()
                                            3 -> SettingsScreen(settingsViewModel = settingsViewModel)
                                            else -> StoryDashboardScreen(
                                                viewModel = viewModel,
                                                onCreateNewBook = {
                                                    viewModel.selectBook(null)
                                                    viewModel.activeWorkspaceTab.value = 1
                                                    viewModel.selectTab(1)
                                                },
                                                onBookSelected = { book ->
                                                    viewModel.selectBook(book.id)
                                                    viewModel.activeWorkspaceTab.value = 1
                                                    viewModel.selectTab(1)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showLogBook) {
                    com.example.ui.components.LogBookDialog(
                        viewModel = viewModel,
                        onDismissRequest = { viewModel.showLogBook.value = false }
                    )
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String
)
