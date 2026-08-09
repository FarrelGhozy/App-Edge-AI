package com.facegate.kioskscanner

import com.facegate.core.data.local.AppDatabase
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.SessionManager
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.PermitDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.dao.SyncMetadata
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

    // NOTE: concrete instances (RetinaFaceDetector, OnnxFaceEmbedder) are
    // provided via @Provides below — no self-referential @Binds (Dagger cycle).

    // ─── VideoMatchEngine ───
    // NOTE: VideoMatchEngine has @Inject constructor — no binding needed here.
    // A previous self-referential @Binds caused a Dagger dependency cycle.

    companion object {

        @Provides
        @Singleton
        @com.facegate.core.di.ApiBaseUrl
        fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL + "/"

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
        fun provideOnnxFaceEmbedder(@ApplicationContext context: android.content.Context): OnnxFaceEmbedder {
            return OnnxFaceEmbedder(context)
        }

        @Provides
        @Singleton
        @javax.inject.Named("video")
        fun provideVideoFaceMatcher(): FaceMatcher {
            return FaceMatcher(isVideoMode = true) // Video mode untuk kiosk scanner
        }

        // #113: SyncManager (dipakai kiosk: KioskInitializer & ScannerViewModel)
        // HARUS memakai matcher video yang SAMA dengan VideoMatchEngine — dulu
        // di-inject FaceMatcher default (non-video) dari CoreModule sehingga index
        // yang dibangun SyncManager tak pernah dipakai scanner (index kosong →
        // wajah tak pernah cocok di jalur sync manual). Provider dipindah dari
        // CoreModule ke KioskModule karena hanya kiosk yang butuh SyncManager.
        @Provides
        @Singleton
        fun provideSyncManager(
            apiService: ApiService,
            attendanceLogDao: AttendanceLogDao,
            faceVectorDao: FaceVectorDao,
            studentDao: StudentDao,
            campusRuleDao: CampusRuleDao,
            permitDao: PermitDao,
            syncMetadata: SyncMetadata,
            @javax.inject.Named("video") faceMatcher: FaceMatcher
        ): SyncManager {
            return SyncManager(
                apiService = apiService,
                attendanceLogDao = attendanceLogDao,
                faceVectorDao = faceVectorDao,
                studentDao = studentDao,
                campusRuleDao = campusRuleDao,
                permitDao = permitDao,
                syncMetadata = syncMetadata,
                faceMatcher = faceMatcher
            )
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
