package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.domain.usecase.GetChaptersUseCase
import com.example.engine.L4LlmDraftGenerator
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.PronunciationPass
import com.example.ui.screens.AudioScreen
import com.example.ui.screens.AudiobookWorkbenchScreen
import com.example.ui.screens.MetadataScreen
import com.example.ui.screens.PipelineWorkbenchScreen
import com.example.ui.screens.PublishingScreen
import com.example.ui.screens.SerialDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoryScreen
import com.example.ui.screens.TranslateScreen
import com.example.ui.viewmodel.PipelineViewModel
import com.example.ui.screens.WriterStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AudiobookViewModel
import com.example.ui.viewmodel.PublishingViewModel
import com.example.ui.viewmodel.SerialDashboardViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.WriterStudioViewModel
import com.example.worker.WorkerRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Authr OS — Desktop Edition (v3.0).
 *
 * Entry point of the Compose Desktop distribution. Roots the desktop Context
 * shim at the OS application-data directory, builds the Room database +
 * repository graph, and hosts the six workspace tabs of the Studio.
 */
fun main() {
    val app = DesktopApplication.instance
    app.onCreate()

    // v3.0 D-01 (OQ-1 RESOLVED): the desktop edition ships the autonomous
    // pipeline as a RUNNING feature. Register the worker factories before
    // the UI exists, so the first enqueue (Autonomous drafting, storage
    // cleanup, EPUB export) finds its factory instead of logging
    // "No factory for …" and stopping. The database is the shared singleton
    // (AppDatabase.getDatabase); the repository built here feeds the
    // EpubExportWorker only — AuthrDesktopApp builds its own instance over
    // the same DAOs (the repository is a stateless facade).
    val wiringDatabase = AppDatabase.getDatabase(app)
    WorkerRegistry.register(
        appContext = app,
        database = wiringDatabase,
        repository = AppRepository(
            storyDao = wiringDatabase.storyDao(),
            audioTrackDao = wiringDatabase.audioTrackDao(),
            bookDao = wiringDatabase.bookDao(),
            chapterDao = wiringDatabase.chapterDao(),
            lorebookDao = wiringDatabase.lorebookDao(),
            chekhovGunDao = wiringDatabase.chekhovGunDao(),
            questionDao = wiringDatabase.questionDao(),
            serialArcDao = wiringDatabase.serialArcDao()
        )
    )

    application {
        // Desktop-style window (UI-UX pass, 2026-11-13): 1280×840 default,
        // a sane minimum so the shell never collapses, and remembering the
        // user's size across launches — the previous 1440×940 phone-stretch
        // default is gone.
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 840.dp
        )
        Window(
            onCloseRequest = {
                app.onTerminate()
                this@application.exitApplication()
            },
            state = windowState,
            title = "Authr OS ${BuildConfig.VERSION_NAME} — The Autonomous Author Operating System"
        ) {
            // Keep the desktop shell usable on small laptop screens: never
            // let the OS resize below the two-pane minimum.
            window.minimumSize = java.awt.Dimension(1024, 680)
            AuthrDesktopApp(app)
        }
    }
}

/** Concrete desktop [android.app.Application] rooted at the OS app-data directory. */
class DesktopApplication private constructor() : android.app.Application() {

    val appContext: android.content.Context = this

    override val filesDir: java.io.File
        get() = android.content.DesktopAppContext.get().filesDir
    override val cacheDir: java.io.File
        get() = android.content.DesktopAppContext.get().cacheDir
    override val externalCacheDir: java.io.File?
        get() = android.content.DesktopAppContext.get().externalCacheDir
    override val noBackupFilesDir: java.io.File
        get() = android.content.DesktopAppContext.get().noBackupFilesDir

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("DesktopApplication", "Authr OS starting; data root: ${android.content.DesktopAppContext.get().dataRoot}")
    }

    companion object {
        val instance: DesktopApplication by lazy { DesktopApplication() }
    }
}

@Composable
private fun AuthrDesktopApp(app: DesktopApplication) {
    val database = AppDatabase.getDatabase(app)
    val repository = AppRepository(
        storyDao = database.storyDao(),
        audioTrackDao = database.audioTrackDao(),
        bookDao = database.bookDao(),
        chapterDao = database.chapterDao(),
        lorebookDao = database.lorebookDao(),
        chekhovGunDao = database.chekhovGunDao(),
        questionDao = database.questionDao(),
        serialArcDao = database.serialArcDao(),
        editorialMemoryDao = database.editorialMemoryDao()
    )
    val viewModel = StudioViewModel(
        application = app,
        repository = repository,
        getChaptersUseCase = GetChaptersUseCase(repository)
    )
    val settingsViewModel = SettingsViewModel(app)
    // Writer Studio (v3.0 P1, plan §4.1): the PROPOSED/CONFIRMED/ARCHIVED
    // workflow. The L4 generator assembles prompts from confirmed material
    // only; drafts land as proposals until the writer confirms them.
    // v3.0 S2 (ADR-0021): the nine-layer ContextAssembler with stage
    // budgets and freshness-decayed manuscript summaries feeds the L4
    // prompt; the token spend ledger records every invocation.
    val summaryCacheService = com.example.engine.context.SummaryCacheService(database.summaryCacheDao())
    val contextAssembler = com.example.engine.context.ContextAssembler(summaryCacheService, database.summaryCacheDao())
    val pipelineMemoryEarly = com.example.engine.pipeline.EditorialMemoryStore(
        requireNotNull(database.editorialMemoryDao()) { "editorial_memory DAO missing (schema v16)" }
    )
    val spendLedger = com.example.engine.context.TokenSpendLedger(pipelineMemoryEarly)
    val writerStudioViewModel = WriterStudioViewModel(
        repository = repository,
        authrRoot = app.filesDir,
        draftGenerator = L4LlmDraftGenerator(
            app,
            assembler = contextAssembler,
            spendLedger = spendLedger,
            // Gemini 2.0 Flash class window (1M) with a conservative
            // working slice + output reserve; smaller providers still fit
            // because every layer truncates to its share.
            modelSpec = com.example.engine.context.ContextAssembler.ModelSpec(
                name = "flash-class",
                maxContext = 128_000,
                outputReserve = 16_000
            )
        )
    )
    // Serial Dashboard (v3.0 P3, plan §6.10/§6.13): buffer, cadence, arcs,
    // validator suite, worksheet + scorecard, paste-ready platform exports.
    val serialDashboardViewModel = SerialDashboardViewModel(
        repository = repository,
        exportDir = app.filesDir
    )
    // Pipeline Workbench (ADR-0018): the nine-stage commercial authoring
    // pipeline — market research → concept → architecture → worldbuilding
    // → manuscript → editorial → engagement → QA → publication prep, with
    // deterministic gates, writer approval checkpoints, and the persistent
    // editorial memory (decisions, issues, versions, engagement analytics).
    val pipelineMemory = com.example.engine.pipeline.EditorialMemoryStore(
        requireNotNull(database.editorialMemoryDao()) { "editorial_memory DAO missing (schema v16)" }
    )
    val pipelineViewModel = PipelineViewModel(
        context = app,
        repository = repository,
        memory = pipelineMemory,
        // ADR-0020: the voice drift axis of stage 7, persisted to
        // voice_drift_log (schema v17).
        voiceEngine = com.example.engine.voice.VoiceEngine(database.voiceDriftDao())
    )
    // Audiobook workbench (v3.0 D-13, the P4 report handoff): engine
    // picker → pronunciation review → one-action render → ACX report.
    // The engine catalog is probed HERE (Context territory) — Pocket TTS
    // needs the binary + voice pack, espeak-ng the bundled binary, the
    // phonemizer the espeak voice; the VM stays Context-free.
    val ffmpeg = com.example.desktopbridge.FfmpegLocator.ffmpegPath()
    val ffprobe = com.example.desktopbridge.FfmpegLocator.ffprobePath()
    val espeak = com.example.desktopbridge.EspeakLocator.espeakPath()
    val pocketReady = com.example.engine.audio.PocketSynthesizer.isReady(app)
    val audiobookViewModel = AudiobookViewModel(
        repository = repository,
        authrRoot = app.filesDir,
        engineCatalog = listOf(
            AudiobookViewModel.EngineOption(
                id = "pocket",
                label = "Pocket TTS",
                continuity = "POCKET_STRUCTURAL — fixed voice pack, identity identical across chapters",
                available = pocketReady,
                unavailableNote = if (pocketReady) null else "install the pocket-tts binary + a .safetensors voice pack (see config-and-env)"
            ),
            AudiobookViewModel.EngineOption(
                id = "espeak",
                label = "espeak-ng",
                continuity = "ESPEAK_LOCAL — bundled offline engine, deterministic fixed voice",
                available = espeak != null,
                unavailableNote = if (espeak != null) null else "espeak-ng not found"
            )
        ),
        synthesizerFor = { engineId ->
            if (engineId == "pocket") com.example.engine.audio.PocketSynthesizer(
                packsDir = com.example.tts.PocketTtsManager.voicePacksDir(app)
            ) else com.example.engine.audio.EspeakSynthesizer(espeak)
        },
        phonemizer = espeak?.let { PronunciationPass.EspeakPhonemizer(it) },
        rendererFor = { synth ->
            AudiobookRenderer(
                synthesizer = synth,
                mastering = com.example.engine.audio.MasteringChain(ffmpegPath = ffmpeg),
                audit = com.example.engine.audio.AcxAudit(ffmpegPath = ffmpeg, ffprobePath = ffprobe)
            )
        }
    )

    // Publishing workbench (v3.0 D-15, the P5 report handoff): metadata
    // workbench editor + one-action retail packaging + ship-report viewer.
    val publishingViewModel = PublishingViewModel(
        repository = repository,
        authrRoot = app.filesDir
    )

    // Scaffold the .authr plugin library on first launch (P0, plan §8):
    // the shipped 16 structures + 22 personas land in the data root's
    // default container plugin directory.
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            runCatching {
                val containerRoot = java.io.File(app.filesDir, "authr")
                com.example.plugins.PluginRegistry.scaffoldDefaults(containerRoot)
                // Pick up user-dropped/edited plugin persona files on this
                // launch (plan §8: the user-extensible directory is authoritative).
                com.example.utils.PersonaResolver.refreshPlugins(app)
            }.onFailure { android.util.Log.w("AuthrDesktopApp", "Plugin scaffold failed", it) }
        }
    }

    MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Provide the desktop Context shim to all composables
            // (StoryScreen & friends read LocalContext.current, mirroring Android).
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContext provides app
            ) {
                // Desktop-style shell (UI-UX pass, 2026-11-13): permanent left
                // navigation rail + framed content pane, replacing the phone-style
                // top tab row that stretched Android screens edge-to-edge.
                DesktopWorkspaceShell(
                    currentTab = viewModel.currentTab.collectAsState().value,
                    onSelect = { viewModel.selectTab(it) },
                    versionLabel = "v${BuildConfig.VERSION_NAME}"
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val currentTab by viewModel.currentTab.collectAsState()
                        when (currentTab) {
                            1 -> AudioScreen(viewModel = viewModel)
                            2 -> TranslateScreen(viewModel = viewModel)
                            3 -> MetadataScreen(viewModel = viewModel)
                            5 -> SettingsScreen(settingsViewModel = settingsViewModel)
                            6 -> WriterStudioScreen(
                                viewModel = writerStudioViewModel,
                                onBack = { viewModel.selectTab(0) }
                            )
                            7 -> SerialDashboardScreen(
                                viewModel = serialDashboardViewModel,
                                onBack = { viewModel.selectTab(0) }
                            )
                            8 -> PublishingScreen(
                                viewModel = publishingViewModel,
                                onBack = { viewModel.selectTab(0) }
                            )
                            9 -> AudiobookWorkbenchScreen(
                                viewModel = audiobookViewModel,
                                onBack = { viewModel.selectTab(0) }
                            )
                            10 -> PipelineWorkbenchScreen(
                                viewModel = pipelineViewModel,
                                scope = rememberCoroutineScope()
                            )
                            else -> StoryScreen(viewModel = viewModel) // 0: Story, 4: recommendations live in the Story workspace
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop-style workspace shell (UI-UX pass, 2026-11-13): a permanent left
 * navigation rail — the standard desktop-app pattern (VS Code / Slack /
 * IntelliJ sidebars) — instead of the phone-style top tab row the app
 * shipped with. The rail groups the nine workspaces into Create / Produce /
 * System sections, shows the workspace name under the icon, and frames the
 * content in its own surface so the active workspace is unmistakable.
 */
@Composable
private fun DesktopWorkspaceShell(
    currentTab: Int,
    onSelect: (Int) -> Unit,
    versionLabel: String,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        WorkspaceNavRail(currentTab, onSelect, versionLabel)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

private data class RailDestination(
    val tab: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val section: String
)

@Composable
private fun WorkspaceNavRail(
    currentTab: Int,
    onSelect: (Int) -> Unit,
    versionLabel: String
) {
    val destinations = listOf(
        RailDestination(0, "Story", Icons.Filled.AutoStories, "Create"),
        RailDestination(10, "Pipeline", Icons.Filled.AccountTree, "Create"),
        RailDestination(6, "Writer Studio", Icons.Filled.EditNote, "Create"),
        RailDestination(7, "Serial", Icons.Filled.ViewTimeline, "Create"),
        RailDestination(1, "Audio", Icons.Filled.GraphicEq, "Produce"),
        RailDestination(9, "Audiobook", Icons.Filled.Headphones, "Produce"),
        RailDestination(8, "Publishing", Icons.Filled.UploadFile, "Produce"),
        RailDestination(2, "Translate", Icons.Filled.Translate, "Produce"),
        RailDestination(3, "Metadata", Icons.Filled.Tune, "System"),
        RailDestination(5, "Settings", Icons.Filled.Settings, "System")
    )
    androidx.compose.material3.NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Brand header
            Text(
                "Authr OS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            val sections = listOf("Create", "Produce", "System")
            sections.forEach { section ->
                val items = destinations.filter { it.section == section }
                if (items.isNotEmpty()) {
                    Text(
                        section.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp)
                    )
                    items.forEach { dest ->
                        androidx.compose.material3.NavigationRailItem(
                            selected = currentTab == dest.tab,
                            onClick = { onSelect(dest.tab) },
                            icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                versionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Global workspace bar — REPLACED by [WorkspaceNavRail] in the UI-UX desktop
 * pass (2026-11-13). The phone-style top tab row stretched the Android
 * screens edge-to-edge; the rail keeps navigation pinned left and lets the
 * content frame own its width. Kept briefly for reference; remove after the
 * shell stabilizes.
 */
@Composable
private fun GlobalWorkspaceBar(currentTab: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        0 to "Story",
        1 to "Audio",
        2 to "Translate",
        3 to "Metadata",
        6 to "Writer Studio",
        7 to "Serial",
        8 to "Publishing",
        9 to "Audiobook",
        5 to "Settings"
    )
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Authr OS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp)
            )
            tabs.forEach { (index, label) ->
                TextButton(onClick = { onSelect(index) }) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentTab == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
