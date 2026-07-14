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
                    timestamp = entity.timestamp
                )
            }
        )

        val response = apiService.syncAttendance(batch)
        if (response.isSuccessful) {
            val ids = unsynced.map { it.id }
            attendanceLogDao.markManySynced(ids)
            return unsynced.size
        }
        return 0
    }

    private suspend fun downloadFaces(): Int {
        val since = syncMetadata.getLastFaceSync()
        val response = apiService.syncFaces(since)
        if (response.isSuccessful && response.body() != null) {
            val faceSync = response.body()!!
            if (faceSync.data.isNotEmpty()) {
                // Save face vectors
                val vectors = faceSync.data.map { it.toEntity() }
                faceVectorDao.deleteAll()
                faceVectorDao.insertAll(vectors)

                // Save student data from joined query
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

                // Rebuild face index in RAM
                val faceMap = mutableMapOf<String, FloatArray>()
                for (v in vectors) {
                    faceMap[v.studentId] = v.vector
                }
                faceMatcher.buildIndex(faceMap)

                syncMetadata.setLastFaceSync(faceSync.since ?: "")
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
