package com.facegate.core.di

import android.content.Context
import androidx.room.Room
import com.facegate.core.data.local.AppDatabase
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.SessionManager
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.dao.SyncMetadata
import com.facegate.core.data.remote.ApiClient
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.AuthInterceptor
import com.facegate.core.face.AntiSpoofDetector
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.FaceMatcher
import com.facegate.core.face.LivenessDetector
import com.facegate.core.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "facegate.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()

    @Provides
    fun provideFaceVectorDao(db: AppDatabase): FaceVectorDao = db.faceVectorDao()

    @Provides
    fun provideAttendanceLogDao(db: AppDatabase): AttendanceLogDao = db.attendanceLogDao()

    @Provides
    fun provideCampusRuleDao(db: AppDatabase): CampusRuleDao = db.campusRuleDao()

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): AuthInterceptor {
        return AuthInterceptor(sessionManager)
    }

    @Provides
    @Singleton
    fun provideApiService(
        authInterceptor: AuthInterceptor,
        @ApiBaseUrl baseUrl: String
    ): ApiService {
        return ApiClient.create(baseUrl, authInterceptor)
    }

    @Provides
    @Singleton
    fun provideFaceDetector(): FaceDetectorWrapper {
        return FaceDetectorWrapper()
    }

    @Provides
    @Singleton
    fun provideFaceEmbedder(@ApplicationContext context: Context): FaceEmbedder {
        return FaceEmbedder(context)
    }

    @Provides
    @Singleton
    fun provideFaceMatcher(): FaceMatcher {
        return FaceMatcher()
    }

    @Provides
    @Singleton
    fun provideAntiSpoofDetector(@ApplicationContext context: Context): AntiSpoofDetector {
        return AntiSpoofDetector(context)
    }

    @Provides
    @Singleton
    fun provideLivenessDetector(antiSpoofDetector: AntiSpoofDetector): LivenessDetector {
        return LivenessDetector(antiSpoofDetector = antiSpoofDetector)
    }

    @Provides
    @Singleton
    fun provideSyncMetadata(@ApplicationContext context: Context): SyncMetadata {
        return SyncMetadata(context)
    }

    @Provides
    @Singleton
    fun provideDevicePreferences(@ApplicationContext context: Context): DevicePreferences {
        return DevicePreferences(context)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        apiService: ApiService,
        attendanceLogDao: AttendanceLogDao,
        faceVectorDao: FaceVectorDao,
        studentDao: StudentDao,
        campusRuleDao: CampusRuleDao,
        syncMetadata: SyncMetadata,
        faceMatcher: FaceMatcher
    ): SyncManager {
        return SyncManager(
            apiService = apiService,
            attendanceLogDao = attendanceLogDao,
            faceVectorDao = faceVectorDao,
            studentDao = studentDao,
            campusRuleDao = campusRuleDao,
            syncMetadata = syncMetadata,
            faceMatcher = faceMatcher
        )
    }
}
