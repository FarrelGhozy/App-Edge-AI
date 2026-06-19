package com.facegate.kioskscanner.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.remote.ApiService
import com.facegate.core.face.FaceMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val attendanceLogDao: AttendanceLogDao,
    private val faceVectorDao: FaceVectorDao,
    private val studentDao: StudentDao,
    private val campusRuleDao: CampusRuleDao,
    private val faceMatcher: FaceMatcher
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val UNIQUE_NAME_MIDNIGHT = "midnight_sync"
        const val UNIQUE_NAME_POLLING = "sync_polling"
        const val POLLING_INTERVAL_MINUTES = 10L

        fun scheduleMidnight(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME_MIDNIGHT, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePolling(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                POLLING_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_NAME_POLLING,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        private fun calculateDelayToMidnight(): Long {
            val now = System.currentTimeMillis()
            val zone = java.time.ZoneId.of("Asia/Jakarta")
            val tomorrow = java.time.LocalDate.now(zone).plusDays(1)
            val midnight = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
            return midnight - now
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val unsyncedLogs = attendanceLogDao.getUnsynced()
            if (unsyncedLogs.isNotEmpty()) {
                Log.d(TAG, "Uploading ${unsyncedLogs.size} logs")
            }

            val faceSync = apiService.syncFaces(since = null)
            if (faceSync.isSuccessful && faceSync.body() != null) {
                val faces = faceSync.body()!!.data
                val vectors = faces.map { dto ->
                    com.facegate.core.data.local.entity.FaceVectorEntity(
                        studentId = dto.studentId,
                        vector = dto.vector.toFloatArray()
                    )
                }
                faceVectorDao.deleteAll()
                faceVectorDao.insertAll(vectors)
                faceMatcher.buildIndex(vectors.associate { it.studentId to it.vector })
                Log.d(TAG, "Synced ${vectors.size} faces")
            }

            val rulesSync = apiService.syncRules()
            if (rulesSync.isSuccessful && rulesSync.body() != null) {
                val rules = rulesSync.body()!!.map { dto ->
                    com.facegate.core.data.local.entity.CampusRuleEntity(
                        id = dto.id,
                        dayOfWeek = dto.dayOfWeek,
                        startTime = dto.startTime,
                        endTime = dto.endTime,
                        isRestricted = dto.isRestricted,
                        appliesToAll = dto.appliesToAll,
                        studyProgram = dto.studyProgram,
                        academicYear = dto.academicYear,
                        priority = dto.priority,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                campusRuleDao.deleteAll()
                campusRuleDao.insertAll(rules)
                Log.d(TAG, "Synced ${rules.size} rules")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(size)
        for (i in indices) arr[i] = this[i]
        return arr
    }
}
