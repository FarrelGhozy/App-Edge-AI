package com.facegate.kioskscanner

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.facegate.kioskscanner.service.KioskInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KioskScannerApp : Application(), Configuration.Provider {
    @Inject lateinit var kioskInitializer: KioskInitializer
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("KioskApp", "onCreate: isInitialized=${WorkManager.isInitialized()} workerFactory=${workerFactory::class.java.simpleName}")
        // Initialize WorkManager with HiltWorkerFactory BEFORE anything enqueues work.
        // Without this, WorkManager may be initialized on-demand with the default
        // WorkerFactory, which cannot instantiate @HiltWorker classes.
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, workManagerConfiguration)
            android.util.Log.d("KioskApp", "WorkManager initialized with ${workerFactory::class.java.simpleName}")
        } else {
            android.util.Log.d("KioskApp", "WorkManager ALREADY initialized before onCreate!")
        }
        kioskInitializer.initialize(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
