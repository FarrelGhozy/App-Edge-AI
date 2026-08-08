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
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceBatchRequest
import com.facegate.core.data.remote.dto.ScanRequest
import com.facegate.core.data.remote.dto.SyncCompleteRequest
import com.facegate.core.face.FaceMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Named

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val attendanceLogDao: AttendanceLogDao,
    private val faceVectorDao: FaceVectorDao,
    private val studentDao: StudentDao,
    private val campusRuleDao: CampusRuleDao,
    @Named("video") private val faceMatcher: FaceMatcher,
    private val syncMetadata: SyncMetadata,
    private val devicePreferences: DevicePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val UNIQUE_NAME_MIDNIGHT = "midnight_sync"
        const val UNIQUE_NAME_POLLING = "sync_polling"
        const val POLLING_INTERVAL_SECONDS = 10L
        const val KEY_FULL_SYNC = "full_sync"

        fun scheduleMidnight(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(Data.Builder().putBoolean(KEY_FULL_SYNC, true).build())
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME_MIDNIGHT, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePolling(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(Data.Builder().putBoolean(KEY_FULL_SYNC, false).build())
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
        val isFullSync = inputData.getBoolean(KEY_FULL_SYNC, false)

        return try {
            // 1. Always upload unsynced attendance logs (lightweight)
            syncUnsyncedLogs()

            if (isFullSync) {
                // Midnight full sync — always download regardless of flag
                Log.d(TAG, "Midnight full sync — downloading faces + rules")
                syncFaces()
                syncRules()
            } else {
                // Polling — check flag first (lightweight: boolean only)
                val isRequested = checkSyncRequested()
                if (isRequested) {
                    Log.d(TAG, "Change detected — downloading faces + rules")
                    syncFaces()
                    syncRules()
                    notifySyncComplete()
                }
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
            val deviceId = devicePreferences.getDeviceId()
            val response = apiService.checkSyncRequested(deviceId)
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
                clientId = log.clientId, // #119: idempotency key
                timestamp = log.timestamp
            )
        }

        val response = apiService.syncAttendance(AttendanceBatchRequest(requests))
        if (response.isSuccessful && response.body() != null) {
            // #114: log yang di-skip server (student tidak ditemukan) harus
            // TETAP di antrean offline — jangan tandai synced (anti data loss).
            val skippedIds = response.body()!!
                .data?.skippedLogs.orEmpty()
                .map { it.studentId }
                .toSet()
            val syncedIds = unsynced
                .filter { it.studentId !in skippedIds }
                .map { it.id }
            if (syncedIds.isNotEmpty()) {
                attendanceLogDao.markManySynced(syncedIds)
            }
            Log.d(TAG, "Uploaded ${unsynced.size} logs: ${syncedIds.size} synced, ${skippedIds.size} skipped (kept queued)")
        }
    }

    private suspend fun syncFaces() {
        try {
            val since = syncMetadata.getLastFaceSync()
            val response = apiService.syncFaces(since = since)

            if (response.isSuccessful && response.body() != null) {
                val syncData = response.body()!!
                // #137: buang vektor dimensi lama (192-d era TFLite) dari DB
                // lokal — kalau ikut di-index, dotProduct 512×192 crash → match
                // selalu gagal ("Wajah tidak dikenal").
                faceVectorDao.deleteInvalidDimension(512 * 4)
                // #138: buang vektor & student lokal yang sudah tidak ada di server
                // (student dihapus / wajah dihapus di admin).
                val pruned = pruneStaleLocalData()
                val faces = syncData.data

                if (faces.isNotEmpty()) {
                    // #133: FaceVector PK baru id-auto → REPLACE conflict strategy
                    // TIDAK bisa dipakai sebagai upsert (id=0 selalu insert baru →
                    // duplikasi antar sync). Pola replace-set per santri: hapus semua
                    // vektor santri yang ada di delta, lalu insert vektor-vektornya
                    // (sama dengan replace per-student di backend batchUploadFaces).
                    // Santri yang TIDAK ada di delta tidak disentuh (#112).
                    val facesByStudent = faces.groupBy { it.studentId }
                    facesByStudent.forEach { (sid, dtos) ->
                        faceVectorDao.deleteByStudentId(sid)
                        val entities = dtos.map { dto ->
                            com.facegate.core.data.local.entity.FaceVectorEntity(
                                studentId = dto.studentId,
                                pose = dto.pose,
                                vector = dto.vector.toFloatArray()
                            )
                        }
                        faceVectorDao.insertAll(entities)
                    }

                    // Save student data from joined query
                    val studentEntities = faces.mapNotNull { dto ->
                        val name = dto.studentName
                        val nimVal = dto.nim
                        if (name != null && nimVal != null) {
                            StudentEntity(
                                id = dto.studentId,
                                nim = nimVal,
                                name = name,
                                studyProgram = dto.studyProgram ?: "",
                                academicYear = dto.academicYear ?: ""
                            )
                        } else null
                    }
                    if (studentEntities.isNotEmpty()) {
                        studentDao.insertAll(studentEntities)
                        Log.d(TAG, "Saved ${studentEntities.size} students")
                    }

                    // #112: rebuild index dari SELURUH store lokal (bukan delta)
                    // — index RAM harus mencerminkan semua vektor yang ter-cache.
                    val allLocal = faceVectorDao.getAll()
                    faceMatcher.buildIndex(allLocal.map { it.toIndexEntry() })
                    Log.d(TAG, "Face index rebuilt: ${allLocal.size} vectors for ${allLocal.map { it.studentId }.distinct().size} students (from full store)")

                    Log.d(TAG, "Synced ${faces.size} faces for ${facesByStudent.size} students + ${studentEntities.size} students")
                } else if (pruned) {
                    // Delta kosong tapi prune menghapus vektor stale → rebuild index.
                    val allLocal = faceVectorDao.getAll()
                    faceMatcher.buildIndex(allLocal.map { it.toIndexEntry() })
                    Log.d(TAG, "Face index rebuilt after prune: ${allLocal.size} vectors")
                }

                val s = syncData.since
                if (s != null) {
                    syncMetadata.setLastFaceSync(s)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncFaces failed", e)
        }
    }

    private suspend fun syncRules() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "syncRules failed", e)
        }
    }

    /**
     * #138: bersihkan data lokal (student + face vector) yang tidak ada lagi
     * di server. Delta sync tidak pernah menghapus — student yang dihapus di
     * server nyangkut di kiosk dan jadi "runner-up" dari wajah yang sama
     * (gap kecil → adaptive threshold naik → wajah asli ditolak).
     *
     * Kasus:
     * - Student dihapus di admin → hapus row student + vektornya.
     * - Wajah dihapus di admin (student tetap) → hapus vektornya (cek faceRegistered).
     *
     * @return true jika ada vektor lokal yang dihapus (index perlu rebuild).
     */
    private suspend fun pruneStaleLocalData(): Boolean {
        try {
            // id → faceRegistered (apakah student masih punya wajah di server)
            val serverFaces = mutableMapOf<String, Boolean>()
            var page = 1
            while (true) {
                val resp = apiService.getStudents(page = page, pageSize = 100)
                val body = resp.body()
                if (!resp.isSuccessful || body == null || body.data.isEmpty()) break
                for (s in body.data) serverFaces[s.id] = s.faceRegistered
                if (body.total <= page * body.pageSize) break
                page++
            }
            if (serverFaces.isEmpty()) return false

            val local = studentDao.getAllActive()
            var prunedStudents = 0
            var prunedFaces = 0
            for (student in local) {
                val registered = serverFaces[student.id]
                if (registered == null) {
                    // Student tidak ada di server → hapus vektor + row
                    prunedFaces += faceVectorDao.getByStudentId(student.id).size
                    faceVectorDao.deleteByStudentId(student.id)
                    studentDao.deleteById(student.id)
                    prunedStudents++
                } else if (!registered) {
                    // Student ada tapi wajahnya sudah dihapus di admin → hapus vektor lokal
                    prunedFaces += faceVectorDao.getByStudentId(student.id).size
                    faceVectorDao.deleteByStudentId(student.id)
                }
            }
            if (prunedFaces > 0) {
                Log.w(TAG, "Pruned $prunedStudents stale students + $prunedFaces stale face vectors from local cache")
            }
            return prunedFaces > 0
        } catch (e: Exception) {
            Log.w(TAG, "Prune stale data failed: ${e.message}")
            return false
        }
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(size)
        for (i in indices) arr[i] = this[i]
        return arr
    }
}
