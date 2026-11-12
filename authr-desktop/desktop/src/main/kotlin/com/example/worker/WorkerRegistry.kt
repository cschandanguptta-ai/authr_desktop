package com.example.worker

import android.content.Context
import androidx.work.DesktopWorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.BookDao
import com.example.data.local.ChapterDao
import com.example.data.local.ChekhovGunDao
import com.example.data.local.LorebookDao
import com.example.data.local.QuestionDao
import com.example.data.local.SerialArcDao
import com.example.data.repository.AppRepository

/**
 * Worker factory registry for the desktop edition (v3.0 D-01, OQ-1).
 *
 * DECIDED (2026-11-12, OQ-1): the desktop edition SHIPS the autonomous
 * pipeline — WorldBuilding → Outline → Drafting → Polishing → Compilation —
 * as a running feature, not an engine-only port. The Android original
 * registered these factories through its Koin DI module; the desktop shim
 * takes a plain registry ([DesktopWorkManager.registerWorker]), and until
 * this object existed ZERO call sites registered anything, so every enqueue
 * from `StudioViewModel`/`StoryEditorViewModel` logged "No factory for …"
 * and stopped. This registry is that DI module's desktop replacement: a
 * Koin-free registry, constructed once from the app's own database +
 * repository graph (the D-01 suggested direction).
 *
 * Idempotent: `register` may be called more than once (recomposition,
 * relaunch); the map is overwritten with equivalent factories.
 *
 * Testability: takes the DAOs directly (not the [AppDatabase] singleton),
 * so [WorkerWiringTest] proves the wiring with fake DAOs — no Room, no
 * Context shim filesystem.
 */
object WorkerRegistry {

    /** Every worker class the app enqueues (grep-verified call sites: StudioViewModel, StoryEditorViewModel, SettingsViewModel, StoryDashboardScreen). */
    val WORKER_CLASSES: List<Class<*>> = listOf(
        WorldBuildingWorker::class.java,
        OutlineWorker::class.java,
        DraftingWorker::class.java,
        PolishingWorker::class.java,
        CompilationWorker::class.java,
        EpubExportWorker::class.java,
        StorageCleanupWorker::class.java
    )

    @Volatile var registered: Boolean = false
        private set

    /**
     * Registers a factory for every worker the app enqueues. [appContext] is
     * captured per worker instance (the shim's `WorkerParameters` carries the
     * input data and run attempt); the DAOs/repository come from the caller's
     * shared database instance.
     */
    fun register(
        appContext: Context,
        bookDao: BookDao,
        chapterDao: ChapterDao,
        lorebookDao: LorebookDao,
        chekhovGunDao: ChekhovGunDao?,
        questionDao: QuestionDao?,
        serialArcDao: SerialArcDao?,
        repository: AppRepository
    ) {
        DesktopWorkManager.registerWorker(WorldBuildingWorker::class) { p ->
            WorldBuildingWorker(appContext, p, bookDao)
        }
        DesktopWorkManager.registerWorker(OutlineWorker::class) { p ->
            OutlineWorker(appContext, p, bookDao, chapterDao, lorebookDao)
        }
        DesktopWorkManager.registerWorker(DraftingWorker::class) { p ->
            DraftingWorker(appContext, p, bookDao, chapterDao, lorebookDao)
        }
        DesktopWorkManager.registerWorker(PolishingWorker::class) { p ->
            PolishingWorker(appContext, p, bookDao, chapterDao)
        }
        DesktopWorkManager.registerWorker(CompilationWorker::class) { p ->
            CompilationWorker(appContext, p, bookDao, chapterDao, chekhovGunDao, serialArcDao, questionDao)
        }
        DesktopWorkManager.registerWorker(EpubExportWorker::class) { p ->
            EpubExportWorker(appContext, p, repository)
        }
        DesktopWorkManager.registerWorker(StorageCleanupWorker::class) { p ->
            StorageCleanupWorker(appContext, p)
        }
        registered = true
    }

    /** Production adapter: registers from the app's own database + repository graph. */
    fun register(appContext: Context, database: AppDatabase, repository: AppRepository) = register(
        appContext = appContext,
        bookDao = database.bookDao(),
        chapterDao = database.chapterDao(),
        lorebookDao = database.lorebookDao(),
        chekhovGunDao = database.chekhovGunDao(),
        questionDao = database.questionDao(),
        serialArcDao = database.serialArcDao(),
        repository = repository
    )
}
