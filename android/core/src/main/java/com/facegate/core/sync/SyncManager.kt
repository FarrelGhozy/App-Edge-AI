package com.facegate.core.sync

import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.dao.SyncMetadata
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceBatchRequest
import com.facegate.core.data.remote.dto.ScanRequest
import com.facegate.core.data.remote.dto.SyncCompleteRequest
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.remote.dto.toEntity
import com.facegate.core.face.FaceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val logsUploaded: Int = 0,
    val facesDownloaded: Int = 0,
    val rulesDownloaded: Int = 0,
    val error: String? = null
)

@Singleton
class SyncManager @Inject constructor(
    private val apiService: ApiService,
    private val attendanceLogDao: AttendanceLogDao,
    private val faceVectorDao: FaceVectorDao,
    private val studentDao: StudentDao,
    private val campusRuleDao: CampusRuleDao,
    private val syncMetadata: SyncMetadata,
    private val faceMatcher: FaceMatcher
) {
    suspend fun syncAll(deviceId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val logsResult = uploadUnsyncedLogs()
            val facesResult = downloadFaces()
            val rulesResult = downloadRules()
            markSyncComplete(deviceId, logsResult, facesResult)

            SyncResult(
                success = true,
                logsUploaded = logsResult,
                facesDownloaded = facesResult,
                rulesDownloaded = rulesResult
            )
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Sync failed")
        }
    }

    suspend fun syncLogsOnly(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val count = uploadUnsyncedLogs()
            SyncResult(success = true, logsUploaded = count)
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Sync logs failed")
        }
    }

    /**
     * Wipe total data lokal kiosk (factory reset data sync) lalu pull ulang
     * semuanya dari server:
     * 1. Upload dulu log yang belum ter-sync agar tidak hilang dari server.
     * 2. Hapus semua face vectors, students, rules, dan log lokal.
     * 3. Reset watermark sync → request berikutnya tanpa `since` → server
     *    mengembalikan SEMUA data (full pull).
     * 4. Kosongkan index in-memory (jangan ada match pakai vektor lama).
     */
    suspend fun wipeAndResync(deviceId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            uploadUnsyncedLogs()
            attendanceLogDao.deleteAll()
            faceVectorDao.deleteAll()
            studentDao.deleteAll()
            campusRuleDao.deleteAll()
            syncMetadata.clear()
            faceMatcher.clear()
            syncAll(deviceId)
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Wipe & resync failed")
        }
    }

    private suspend fun uploadUnsyncedLogs(): Int {
        val unsynced = attendanceLogDao.getUnsynced()
        if (unsynced.isEmpty()) return 0

        val batch = AttendanceBatchRequest(
            logs = unsynced.map { entity ->
                ScanRequest(
                    studentId = entity.studentId,
                    action = entity.action,
                    confidenceScore = entity.confidenceScore,
                    isViolation = entity.isViolation,
                    violationType = entity.violationType,
                    deviceId = entity.deviceId,
                    photoCapture = entity.photoCapture,
                    clientId = entity.clientId, // #119: idempotency key
                    timestamp = entity.timestamp
                )
            }
        )

        val response = apiService.syncAttendance(batch)
        if (response.isSuccessful && response.body() != null) {
            // #114: jangan tandai SEMUA synced. Log yang di-skip server (mis.
            // student tidak ditemukan) harus TETAP di antrean offline agar tidak
            // hilang tanpa jejak — mark hanya yang benar-benar diterima.
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
            android.util.Log.d("SyncManager", "Uploaded ${unsynced.size} logs: ${syncedIds.size} synced, ${skippedIds.size} skipped (kept queued)")
            return syncedIds.size
        }
        return 0
    }

    private suspend fun downloadFaces(): Int {
        val since = syncMetadata.getLastFaceSync()
        val response = apiService.syncFaces(since)
        if (response.isSuccessful && response.body() != null) {
            val faceSync = response.body()!!
            // #137: buang vektor dimensi lama (192-d era TFLite) dari DB lokal —
            // kalau ikut di-index, dotProduct 512×192 crash → match selalu gagal.
            faceVectorDao.deleteInvalidDimension(512 * 4)
            // #138: buang vektor & student lokal yang sudah tidak ada di server
            // (student dihapus / wajah dihapus di admin → vektor lama nyangkut →
            // jadi "runner-up" dari wajah sama → gap kecil → match ditolak).
            val pruned = pruneStaleLocalData()
            if (faceSync.data.isNotEmpty()) {
                // #133: FaceVector PK baru id-auto → REPLACE conflict strategy
                // TIDAK bisa dipakai sebagai upsert (selalu insert baru → duplikasi
                // antar sync). Pola replace-set per santri: hapus vektor santri yang
                // ada di delta, lalu insert vektor-vektornya (sama pola backend).
                // Santri lain tidak disentuh (#78/#112).
                val facesByStudent = faceSync.data.groupBy { it.studentId }
                facesByStudent.forEach { (sid, dtos) ->
                    faceVectorDao.deleteByStudentId(sid)
                    faceVectorDao.insertAll(dtos.map { it.toEntity() })
                }

                // Save student data from joined query (upsert)
                val students = faceSync.data.mapNotNull { dto ->
                    if (dto.studentName != null && dto.nim != null) {
                        StudentEntity(
                            id = dto.studentId,
                            nim = dto.nim,
                            name = dto.studentName,
                            studyProgram = dto.studyProgram ?: "",
                            academicYear = dto.academicYear ?: ""
                        )
                    } else null
                }
                if (students.isNotEmpty()) {
                    studentDao.insertAll(students)
                }

                // Rebuild index from the FULL local store — the response may be a
                // partial delta, so the index must reflect all cached vectors.
                val allLocal = faceVectorDao.getAll()
                faceMatcher.buildIndex(allLocal.map { it.toIndexEntry() })
            } else if (pruned) {
                // Delta kosong tapi prune menghapus vektor stale → index perlu
                // dibangun ulang supaya vektor yang dihapus tidak ikut di-match.
                val allLocal = faceVectorDao.getAll()
                faceMatcher.buildIndex(allLocal.map { it.toIndexEntry() })
            }

            // Advance watermark to the server's max(updated_at), never to "" —
            // an empty watermark forces a full re-download every sync.
            if (faceSync.since != null && faceSync.since.isNotBlank()) {
                syncMetadata.setLastFaceSync(faceSync.since)
            }
            return faceSync.data.size
        }
        return 0
    }

    private suspend fun downloadRules(): Int {
        val response = apiService.syncRules()
        if (response.isSuccessful && response.body() != null) {
            val rules = response.body()!!.map { it.toEntity() }
            campusRuleDao.deleteAll()
            campusRuleDao.insertAll(rules)
            return rules.size
        }
        return 0
    }

    /**
     * #138: bersihkan data lokal (student + face vector) yang tidak ada lagi
     * di server. Sinkronisasi hanya menambah/memperbarui (delta by updated_at);
     * data yang DIHAPUS di server tidak pernah muncul di delta → nyangkut
     * selamanya di kiosk. Vektor stale dari wajah yang sama menjadi runner-up
     * dengan gap kecil → adaptive threshold naik → wajah asli ditolak.
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
                android.util.Log.w("SyncManager", "Pruned $prunedStudents stale students + $prunedFaces stale face vectors from local cache")
            }
            return prunedFaces > 0
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "Prune stale data failed: ${e.message}")
            return false
        }
    }

    suspend fun checkSyncRequested(deviceId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkSyncRequested(deviceId)
            response.isSuccessful && response.body()?.requested == true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun markSyncComplete(
        deviceId: String,
        logsCount: Int,
        facesCount: Int
    ) {
        try {
            apiService.completeSync(
                SyncCompleteRequest(
                    deviceId = deviceId,
                    status = if (logsCount > 0 || facesCount > 0) "success" else "no_changes",
                    logsCount = logsCount,
                    facesCount = facesCount
                )
            )
        } catch (_: Exception) { }
    }
}
