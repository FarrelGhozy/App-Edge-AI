package com.facegate.kioskscanner

import android.app.Application
import com.facegate.kioskscanner.service.KioskInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KioskScannerApp : Application() {
    @Inject lateinit var kioskInitializer: KioskInitializer

    override fun onCreate() {
        super.onCreate()
        kioskInitializer.initialize(this)
    }
}
