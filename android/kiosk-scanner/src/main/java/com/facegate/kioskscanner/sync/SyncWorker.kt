package com.facegate.kioskscanner.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.dao.SyncMetadata
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceBatchRequest
import com.facegate.core.data.remote.dto.ScanRequest
import com.facegate.core.data.remote.dto.SyncCompleteRequest
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
    private val faceMatcher: FaceMatcher,
    private val syncMetadata: SyncMetadata,
    private val devicePreferences: DevicePreferences
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
            syncUnsyncedLogs()

            val isRequested = checkSyncRequested()
            if (isRequested) {
                syncFaces()
                syncRules()
                notifySyncComplete()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    /** Check if admin requested a manual sync. */
    private suspend fun checkSyncRequested(): Boolean {
        return try {
            val response = apiService.checkSyncRequested()
            response.isSuccessful && response.body()?.requested == true
        } catch (e: Exception) {
            false
        }
    }

    /** Notify backend that sync is complete. */
    private suspend fun notifySyncComplete() {
        try {
            val deviceId = devicePreferences.getDeviceId() ?: "unknown"
            apiService.completeSync(
                SyncCompleteRequest(
                    deviceId = deviceId,
                    status = "success",
                    logsCount = 0,
                    facesCount = 0
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify sync complete", e)
        }
    }

    private suspend fun syncUnsyncedLogs() {
        val unsynced = attendanceLogDao.getUnsynced()
        if (unsynced.isEmpty()) return

        Log.d(TAG, "Uploading ${unsynced.size} logs")

        val requests = unsynced.map { log ->
            ScanRequest(
                studentId = log.studentId,
                action = log.action,
                confidenceScore = log.confidenceScore,
                isViolation = log.isViolation,
                violationType = log.violationType,
                deviceId = log.deviceId,
                photoCapture = log.photoCapture,
                timestamp = log.timestamp
            )
        }

        val response = apiService.syncAttendance(AttendanceBatchRequest(requests))
        if (response.isSuccessful) {
            val ids = unsynced.map { it.id }
            attendanceLogDao.markManySynced(ids)
            Log.d(TAG, "Uploaded ${ids.size} logs successfully")
        }
    }

    private suspend fun syncFaces() {
        val since = syncMetadata.getLastFaceSync()
        val response = apiService.syncFaces(since = since)

        if (response.isSuccessful && response.body() != null) {
            val syncData = response.body()!!
            val faces = syncData.data

            if (faces.isNotEmpty()) {
                val vectors = faces.map { dto ->
                    com.facegate.core.data.local.entity.FaceVectorEntity(
                        studentId = dto.studentId,
                        vector = dto.vector.toFloatArray()
                    )
                }

                for (fv in vectors) {
                    val existing = faceVectorDao.getByStudentId(fv.studentId)
                    if (existing == null) {
                        faceVectorDao.insert(fv)
                    }
                }

                val allVectors = faceVectorDao.getAll()
                faceMatcher.buildIndex(allVectors.associate { it.studentId to it.vector })
                Log.d(TAG, "Synced ${vectors.size} new/updated faces")
            }

            val s = syncData.since
            if (s != null) {
                syncMetadata.setLastFaceSync(s)
            }
        }
    }

    private suspend fun syncRules() {
        val response = apiService.syncRules()
        if (response.isSuccessful && response.body() != null) {
            val rules = response.body()!!.map { dto ->
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
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(size)
        for (i in indices) arr[i] = this[i]
        return arr
    }
}
