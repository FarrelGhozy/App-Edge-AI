package com.facegate.kioskscanner

import com.facegate.core.data.local.AppDatabase
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.SessionManager
import com.facegate.core.data.remote.ApiClient
import com.facegate.core.data.remote.ApiService
import com.facegate.core.di.ApiBaseUrl
import com.facegate.core.engine.SessionTracker
import com.facegate.core.engine.ToggleEngine
import com.facegate.core.engine.ViolationDetector
import com.facegate.core.face.*
import com.facegate.core.sync.SyncManager
import com.facegate.kioskscanner.matching.VideoMatchEngine
import com.facegate.kioskscanner.scanner.VoiceFeedback
import com.facegate.kioskscanner.service.KioskInitializer
import com.facegate.kioskscanner.sync.DevicePingWorker
import com.facegate.kioskscanner.sync.SyncWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KioskModule {

    // ─── Face Detection Provider (primary: RetinaFace ONNX, fallback: ML Kit) ───
    @Binds
    @Singleton
    abstract fun bindFaceDetectorProvider(
        retinaFaceDetector: RetinaFaceDetector
    ): FaceDetectorProvider

    // ─── Face Embedder Provider (primary: ONNX, fallback: TFLite) ───
    @Binds
    @Singleton
    abstract fun bindFaceEmbedderProvider(
        onnxFaceEmbedder: OnnxFaceEmbedder
    ): FaceEmbedderProvider

    companion object {

        @Provides
        @Singleton
        @ApiBaseUrl
        fun provideApiBaseUrl(): String {
            return BuildConfig.API_BASE_URL
        }

        @Provides
        @Singleton
        fun provideToggleEngine(database: AppDatabase): ToggleEngine {
            return ToggleEngine(database.attendanceLogDao())
        }

        @Provides
        @Singleton
        fun provideSessionTracker(
            database: AppDatabase
        ): SessionTracker {
            return SessionTracker(database.attendanceLogDao())
        }

        @Provides
        @Singleton
        fun provideViolationDetector(database: AppDatabase): ViolationDetector {
            return ViolationDetector(database.campusRuleDao())
        }

        @Provides
        @Singleton
        fun provideRetinaFaceDetector(
            @ApplicationContext context: android.content.Context
        ): RetinaFaceDetector {
            return RetinaFaceDetector(context)
        }

        @Provides
        @Singleton
        fun provideOnnxFaceEmbedder(
            @ApplicationContext context: android.content.Context
        ): OnnxFaceEmbedder {
            return OnnxFaceEmbedder(context)
        }

        @Provides
        @Singleton
        fun provideVoiceFeedback(
            @ApplicationContext context: android.content.Context
        ): VoiceFeedback {
            return VoiceFeedback(context)
        }


    }
}
