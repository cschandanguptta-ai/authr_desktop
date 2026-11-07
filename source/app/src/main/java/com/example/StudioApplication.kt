package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin
import org.koin.androidx.workmanager.koin.workManagerFactory

class StudioApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()

    override fun onCreate() {
        super.onCreate()
        
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            try {
                startKoin {
                    androidContext(this@StudioApplication)
                    try {
                        workManagerFactory()
                    } catch (e: Exception) {
                        // WorkManager might already be initialized in tests
                    }
                    modules(appModule)
                }
            } catch (e: Exception) {
                // Ignore Koin start exceptions
            }
        }
    }
}
