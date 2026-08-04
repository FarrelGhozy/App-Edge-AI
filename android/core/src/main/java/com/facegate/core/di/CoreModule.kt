package com.facegate.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.facegate.core.BuildConfig
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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.facegate.core.face.AntiSpoofDetector
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.FaceMatcher
import com.facegate.core.face.LivenessDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    /**
     * #88: migration eksplisit 1 → 2 (face_vectors: primary key tunggal studentId
     * menjadi composite (studentId, pose) + kolom baru `pose`). Pengganti
     * fallbackToDestructiveMigration() yang dulu menghapus SEMUA tabel offline
     * (termasuk attendance_logs yang isSynced=false → data hilang tak ter-upload).
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Buat tabel baru dgn composite key + kolom pose, salin data, ganti yang lama.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `face_vectors_new` (" +
                    "`studentId` TEXT NOT NULL, " +
                    "`pose` TEXT NOT NULL DEFAULT '', " +
                    "`vector` BLOB NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`studentId`, `pose`))"
            )
            db.execSQL(
                "INSERT INTO face_vectors_new (studentId, pose, vector, updatedAt) " +
                    "SELECT studentId, '', vector, updatedAt FROM face_vectors"
            )
            db.execSQL("DROP TABLE face_vectors")
            db.execSQL("ALTER TABLE face_vectors_new RENAME TO face_vectors")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "facegate.db"
        ).addMigrations(MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
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
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        // #89: BODY logging mengumbar Authorization: Bearer + vektor wajah.
        // Aktifkan lengkap HANYA di debug; release → BASIC (NONE dari body).
        val logLevel = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = logLevel
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            // #89: readTimeout(0) = timeout tak hingga → request bisa gantung
            // selamanya. Batasi 30s supaya kiosk tidak beku saat jaringan drop.
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(
        okHttpClient: OkHttpClient,
        @ApiBaseUrl baseUrl: String
    ): ApiService {
        return ApiClient.create(baseUrl, okHttpClient)
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
        return FaceMatcher(isVideoMode = false) // Default mode for registration/admin
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
}
