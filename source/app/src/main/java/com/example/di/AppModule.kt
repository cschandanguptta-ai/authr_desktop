package com.example.di

import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.domain.usecase.GetChaptersUseCase
import com.example.domain.usecase.GenerateStoryUseCase
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StoryEditorViewModel
import org.koin.androidx.workmanager.dsl.worker
import com.example.worker.WorldBuildingWorker
import com.example.worker.OutlineWorker
import com.example.worker.DraftingWorker
import com.example.worker.PolishingWorker
import com.example.worker.CompilationWorker
import com.example.worker.StorageCleanupWorker
import com.example.worker.EpubExportWorker


val appModule = module {
    single { AppDatabase.getDatabase(androidApplication()) }
    single { get<AppDatabase>().storyDao() }
    single { get<AppDatabase>().audioTrackDao() }
    single { get<AppDatabase>().bookDao() }
    single { get<AppDatabase>().chapterDao() }
    single { get<AppDatabase>().lorebookDao() }
    
    single { 
        AppRepository(
            get(),
            get(),
            get(),
            get(),
            get()
        ) 
    }

    single { GetChaptersUseCase(get()) }
    single { GenerateStoryUseCase() }
    

    worker { WorldBuildingWorker(get(), get(), get()) }
    worker { OutlineWorker(get(), get(), get(), get(), get()) }
    worker { DraftingWorker(get(), get(), get(), get(), get()) }
    worker { PolishingWorker(get(), get(), get(), get()) }
    worker { CompilationWorker(get(), get(), get(), get()) }
    worker { StorageCleanupWorker(get(), get()) }
    worker { EpubExportWorker(get(), get(), get()) }
    viewModel { StudioViewModel(androidApplication(), get(), get()) }
    viewModel { SettingsViewModel(androidApplication()) }
    viewModel { StoryEditorViewModel(androidApplication(), get(), get(), get()) }
}
