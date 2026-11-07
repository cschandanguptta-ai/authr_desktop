package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudioViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import org.koin.core.context.stopKoin
import org.junit.After
import androidx.work.Configuration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var viewModel: StudioViewModel
    private lateinit var settingsViewModel: com.example.ui.viewmodel.SettingsViewModel

    @Before
    fun setUp() {
        stopKoin() // Stop any running Koin instances to avoid KoinAppAlreadyStartedException
        com.example.tts.LocalKittenTtsManager.skipInitForTesting = true
        com.example.ui.viewmodel.StudioViewModel.skipInitForTesting = true
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Safely initialize WorkManager for tests
        try {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                .build()
            androidx.work.WorkManager.initialize(context, config)
        } catch (e: Exception) {
            // Ignore if already initialized
        }

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            db.storyDao(),
            db.audioTrackDao(),
            db.bookDao(),
            db.chapterDao(),
            db.lorebookDao()
        )
        val getChaptersUseCase = com.example.domain.usecase.GetChaptersUseCase(repository)
        viewModel = StudioViewModel(context as Application, repository, getChaptersUseCase)
        settingsViewModel = com.example.ui.viewmodel.SettingsViewModel(context as Application)
    }

    @After
    fun tearDown() {
        stopKoin()
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
    fun settingsScreen_compact_light_sepia_normalText() {
        composeTestRule.setContent {
            MyApplicationTheme(appTheme = "Sepia", darkTheme = false) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_compact_light_sepia_normal.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
    fun settingsScreen_compact_dark_obsidian_normalText() {
        composeTestRule.setContent {
            MyApplicationTheme(appTheme = "Obsidian", darkTheme = true) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_compact_dark_obsidian_normal.png")
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp-320dpi", sdk = [36])
    fun settingsScreen_medium_light_cream_largeText() {
        composeTestRule.setContent {
            val normalDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = normalDensity.density, fontScale = 1.5f)
            ) {
                MyApplicationTheme(appTheme = "Cream", darkTheme = false) {
                    SettingsScreen(settingsViewModel = settingsViewModel)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_medium_light_cream_large.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-240dpi", sdk = [36])
    fun settingsScreen_expanded_dark_charcoal_normalText() {
        composeTestRule.setContent {
            MyApplicationTheme(appTheme = "Charcoal", darkTheme = true) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_expanded_dark_charcoal_normal.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
    fun settingsScreen_compact_dark_standard_hugeText() {
        composeTestRule.setContent {
            val normalDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = normalDensity.density, fontScale = 2.0f)
            ) {
                MyApplicationTheme(appTheme = "Default", darkTheme = true) {
                    SettingsScreen(settingsViewModel = settingsViewModel)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_compact_dark_standard_huge.png")
    }
}
