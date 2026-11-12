package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.ui.screens.PublishingScreen
import com.example.ui.screens.SerialDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoryScreen
import com.example.ui.screens.TranslateScreen
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
        val windowState = rememberWindowState(width = 1440.dp, height = 940.dp)
        Window(
            onCloseRequest = {
                app.onTerminate()
                this@application.exitApplication()
            },
            state = windowState,
            title = "Authr OS ${BuildConfig.VERSION_NAME} — The Autonomous Author Operating System"
        ) {
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
        serialArcDao = database.serialArcDao()
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
    val writerStudioViewModel = WriterStudioViewModel(
        repository = repository,
        authrRoot = app.filesDir,
        draftGenerator = L4LlmDraftGenerator(app)
    )
    // Serial Dashboard (v3.0 P3, plan §6.10/§6.13): buffer, cadence, arcs,
    // validator suite, worksheet + scorecard, paste-ready platform exports.
    val serialDashboardViewModel = SerialDashboardViewModel(
        repository = repository,
        exportDir = app.filesDir
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
                Column(modifier = Modifier.fillMaxSize()) {
                    GlobalWorkspaceBar(
                        currentTab = viewModel.currentTab.collectAsState().value,
                        onSelect = { viewModel.selectTab(it) }
                    )
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
                            else -> StoryScreen(viewModel = viewModel) // 0: Story, 4: recommendations live in the Story workspace
                        }
                    }
                }
            }
        }
    }
}

/**
 * Global workspace bar (v3.0): the six OS workspaces, including the P1
 * Writer Studio. Slim text buttons keep the ported screens untouched.
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
