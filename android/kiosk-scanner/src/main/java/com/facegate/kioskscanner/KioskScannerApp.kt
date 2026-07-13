package com.facegate.kioskscanner

import android.app.Application
import com.facegate.kioskscanner.service.KioskInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KioskScannerApp : Application() {
    @Inject lateinit var initializer: KioskInitializer

    override fun onCreate() {
        super.onCreate()
        initializer.initialize(this)
    }
}
