package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScanRequest(
    @SerialName("studentId")
    val studentId: String,
    val action: String,
    @SerialName("confidenceScore")
    val confidenceScore: Float,
    @SerialName("isViolation")
    val isViolation: Boolean = false,
    @SerialName("violationType")
    val violationType: String? = null,
    @SerialName("deviceId")
    val deviceId: String? = null,
    @SerialName("photoCapture")
    val photoCapture: String? = null,
    @SerialName("clientId")
    val clientId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AttendanceBatchRequest(
    val logs: List<ScanRequest>
)

/** #114: response batch sync. Bila ada log yang di-skip server (mis. student
 *  tidak ditemukan), client HARUS tidak menandainya synced → antrean offline
 *  tidak hilang tanpa jejak (data loss). */
@Serializable
data class SyncBatchResponse(
    val success: Boolean,
    val data: SyncBatchData? = null
)

@Serializable
data class SyncBatchData(
    val synced: Int = 0,
    val skipped: Int = 0,
    @SerialName("skippedLogs")
    val skippedLogs: List<SkippedLog> = emptyList()
)

@Serializable
data class SkippedLog(
    @SerialName("studentId")
    val studentId: String,
    val reason: String? = null
)

@Serializable
data class AttendanceLogDto(
    val id: String,
    @SerialName("studentId")
    val studentId: String,
    @SerialName("studentName")
    val studentName: String,
    val action: String,
    val timestamp: String,
    @SerialName("confidenceScore")
    val confidenceScore: Float,
    @SerialName("isViolation")
    val isViolation: Boolean,
    @SerialName("violationType")
    val violationType: String? = null,
    @SerialName("deviceId")
    val deviceId: String? = null
)

@Serializable
data class AttendanceListResponse(
    val data: List<AttendanceLogDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize")
    val pageSize: Int
)
