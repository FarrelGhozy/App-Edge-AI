package com.facegate.kioskscanner.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.PermitDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.dao.SyncMetadata
import com.facegate.core.data.local.entity.PermitEntity
import com.facegate.core.data.local.entity.PermitMemberEntity
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceBatchRequest
import com.facegate.core.data.remote.dto.CreateKioskPermitRequest
import com.facegate.core.data.remote.dto.PermitVerificationBatchRequest
import com.facegate.core.data.remote.dto.ScanRequest
import com.facegate.core.data.remote.dto.SyncCompleteRequest
import com.facegate.core.data.remote.dto.VerifyPermitScanRequest
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
    private val permitDao: PermitDao,
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

            // 1b. #135: unggah antrean izin offline (pengajuan + verifikasi)
            syncPermitQueues()

            if (isFullSync) {
                // Midnight full sync — always download regardless of flag
                Log.d(TAG, "Midnight full sync — downloading faces + rules + permits")
                syncFaces()
                syncRules()
                syncPermits()
            } else {
                // Polling — check flag first (lightweight: boolean only)
                val isRequested = checkSyncRequested()
                if (isRequested) {
                    Log.d(TAG, "Change detected — downloading faces + rules + permits")
                    syncFaces()
                    syncRules()
                    syncPermits()
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
     * #135: download izin mandiri/kelompok + anggota (full-replace). Kiosk
     * menampilkan daftar izin & status verifikasi dari data lokal ini.
     */
    private suspend fun syncPermits() {
        try {
            val response = apiService.syncPermits()
            if (response.isSuccessful && response.body() != null) {
                val permits = response.body()!!.data
                permitDao.clearMembers()
                permitDao.clearPermits()
                val entities = permits.map { dto ->
                    PermitEntity(
                        id = dto.id,
                        type = dto.type,
                        startDate = dto.startDate.parseIso(),
                        endDate = dto.endDate.parseIso(),
                        startTime = dto.startTime,
                        endTime = dto.endTime,
                        status = dto.status,
                        reason = dto.reason,
                        note = dto.note,
                        rejectionReason = dto.rejectionReason,
                        createdAt = dto.createdAt?.parseIso() ?: System.currentTimeMillis()
                    )
                }
                val members = permits.flatMap { dto ->
                    dto.members.map { m ->
                        PermitMemberEntity(
                            permitId = dto.id,
                            studentId = m.studentId,
                            name = m.student?.name ?: "",
                            nim = m.student?.nim ?: "",
                            keluarVerifiedAt = m.keluarVerifiedAt?.parseIsoOrNull(),
                            kembaliVerifiedAt = m.kembaliVerifiedAt?.parseIsoOrNull()
                        )
                    }
                }
                if (entities.isNotEmpty()) permitDao.insertAllPermits(entities)
                if (members.isNotEmpty()) permitDao.insertAllMembers(members)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncPermits failed", e)
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

    /**
     * #135: upload antrean izin offline — pengajuan (create) & verifikasi scan.
     * Dipanggil SETIAP doWork (ringan) supaya form tertunda terkirim saat
     * internet kembali tanpa menunggu flag sync.
     */
    private suspend fun syncPermitQueues() {
        syncPendingPermitRequests()
        syncPendingVerifications()
    }

    private suspend fun syncPendingPermitRequests() {
        val pending = permitDao.getUnsyncedRequests()
        if (pending.isEmpty()) return
        val syncedIds = mutableListOf<Long>()
        for (req in pending) {
            try {
                val memberIds = req.memberIds.split(",").filter { it.isNotBlank() }
                val response = apiService.createKioskPermit(
                    CreateKioskPermitRequest(
                        memberIds = memberIds,
                        startDate = req.startDate,
                        endDate = req.endDate,
                        startTime = req.startTime,
                        endTime = req.endTime,
                        reason = req.reason,
                        clientId = req.clientId
                    )
                )
                if (response.isSuccessful && response.body() != null && response.body()!!.data != null) {
                    // Simpan hasil server ke lokal supaya langsung tampil di
                    // "Daftar Izin" tanpa menunggu sync berikutnya.
                    val dto = response.body()!!.data!!
                    insertLocalPermit(dto)
                    syncedIds.add(req.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "createKioskPermit failed: ${e.message}")
            }
        }
        if (syncedIds.isNotEmpty()) {
            permitDao.markRequestsSynced(syncedIds)
            permitDao.deleteRequests(syncedIds)
        }
    }

    /** Simpan hasil create dari server ke tabel lokal (permit + members). */
    private suspend fun insertLocalPermit(dto: com.facegate.core.data.remote.dto.PermitDto) {
        try {
            val entity = PermitEntity(
                id = dto.id,
                type = dto.type,
                startDate = dto.startDate.parseIso(),
                endDate = dto.endDate.parseIso(),
                startTime = dto.startTime,
                endTime = dto.endTime,
                status = dto.status,
                reason = dto.reason,
                note = dto.note,
                rejectionReason = dto.rejectionReason,
                createdAt = dto.createdAt?.parseIso() ?: System.currentTimeMillis()
            )
            val members = dto.members.map { m ->
                PermitMemberEntity(
                    permitId = dto.id,
                    studentId = m.studentId,
                    name = m.student?.name ?: "",
                    nim = m.student?.nim ?: "",
                    keluarVerifiedAt = m.keluarVerifiedAt?.parseIsoOrNull(),
                    kembaliVerifiedAt = m.kembaliVerifiedAt?.parseIsoOrNull()
                )
            }
            permitDao.insertAllPermits(listOf(entity))
            if (members.isNotEmpty()) permitDao.insertAllMembers(members)
        } catch (e: Exception) {
            Log.w(TAG, "insertLocalPermit failed: ${e.message}")
        }
    }

    private suspend fun syncPendingVerifications() {
        val pending = permitDao.getUnsyncedVerifications()
        if (pending.isEmpty()) return

        val batch = PermitVerificationBatchRequest(
            logs = pending.map { v ->
                VerifyPermitScanRequest(
                    permitId = v.permitId,
                    studentId = v.studentId,
                    confidenceScore = v.confidenceScore,
                    deviceId = v.deviceId,
                    timestamp = v.timestamp,
                    clientId = v.clientId
                )
            }
        )
        try {
            val response = apiService.syncPermitVerifications(batch)
            if (response.isSuccessful && response.body() != null) {
                val skipped = response.body()!!.data?.skippedLogs.orEmpty()
                val syncedLogs = response.body()!!.data?.syncedLogs.orEmpty()
                // #135-fix: mark synced HANYA yang benar-benar diterima server
                // (syncedLogs atau ALREADY_VERIFIED). Sisanya tetap di-antre —
                // jangan hapus diam-diam (anti data-loss #114).
                val accepted = buildSet {
                    syncedLogs.forEach { add(Pair(it.permitId, it.studentId)) }
                    skipped
                        .filter { it.reason == "ALREADY_VERIFIED" }
                        .forEach { add(Pair(it.permitId, it.studentId)) }
                }
                val syncedIds = pending
                    .filter { v -> Pair(v.permitId, v.studentId) in accepted }
                    .map { it.id }
                if (syncedIds.isNotEmpty()) {
                    permitDao.markVerificationsSynced(syncedIds)
                    permitDao.deleteVerifications(syncedIds)
                }
                Log.d(TAG, "Permit verifications uploaded: ${pending.size} (${syncedIds.size} synced, ${skipped.size} skipped)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncPendingVerifications failed", e)
        }
    }

    private fun String.parseIso(): Long {
        return parseIsoOrNull() ?: System.currentTimeMillis()
    }

    private fun String.parseIsoOrNull(): Long? {
        return try {
            java.time.Instant.parse(this).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(this)
                    .atZone(java.time.ZoneId.of("Asia/Jakarta"))
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(size)
        for (i in indices) arr[i] = this[i]
        return arr
    }
}
