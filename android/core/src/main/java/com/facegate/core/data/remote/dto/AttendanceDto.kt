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
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AttendanceBatchRequest(
    val logs: List<ScanRequest>
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
