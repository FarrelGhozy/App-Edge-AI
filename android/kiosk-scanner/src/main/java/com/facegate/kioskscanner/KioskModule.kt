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

    // ─── RetinaFaceDetector (concrete — for registration flow too) ───
    @Binds
    @Singleton
    abstract fun bindRetinaFaceDetector(
        retinaFaceDetector: RetinaFaceDetector
    ): RetinaFaceDetector

    // ─── OnnxFaceEmbedder (concrete) ───
    @Binds
    @Singleton
    abstract fun bindOnnxFaceEmbedder(
        onnxFaceEmbedder: OnnxFaceEmbedder
    ): OnnxFaceEmbedder

    // ─── VideoMatchEngine ───
    @Binds
    @Singleton
    abstract fun bindVideoMatchEngine(
        videoMatchEngine: VideoMatchEngine
    ): VideoMatchEngine

    companion object {

        @Provides
        @Singleton
        fun provideApiBaseUrl(): ApiBaseUrl {
            return ApiBaseUrl(BuildConfig.API_BASE_URL)
        }

        @Provides
        @Singleton
        fun provideToggleEngine(database: AppDatabase): ToggleEngine {
            return ToggleEngine(database.attendanceLogDao())
        }

        @Provides
        @Singleton
        fun provideSessionTracker(
            database: AppDatabase,
            sessionManager: SessionManager
        ): SessionTracker {
            return SessionTracker(database.attendanceLogDao(), sessionManager)
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
        fun provideFaceMatcher(): FaceMatcher {
            return FaceMatcher(isVideoMode = true) // Video mode untuk kiosk scanner
        }

        @Provides
        @Singleton
        fun provideLivenessDetector(
            antiSpoofDetector: AntiSpoofDetector
        ): LivenessDetector {
            return LivenessDetector(antiSpoofDetector = antiSpoofDetector)
        }

        @Provides
        @Singleton
        fun provideVoiceFeedback(
            @ApplicationContext context: android.content.Context
        ): VoiceFeedback {
            return VoiceFeedback(context)
        }

        @Provides
        @Singleton
        fun provideDevicePreferences(
            @ApplicationContext context: android.content.Context
        ): DevicePreferences {
            return DevicePreferences(context)
        }
    }
}
