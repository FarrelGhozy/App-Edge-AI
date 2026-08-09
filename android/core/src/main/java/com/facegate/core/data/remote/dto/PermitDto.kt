package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PermitMemberDto(
    val id: String,
    @SerialName("permitId") val permitId: String,
    @SerialName("studentId") val studentId: String,
    @SerialName("keluarVerifiedAt") val keluarVerifiedAt: String? = null,
    @SerialName("kembaliVerifiedAt") val kembaliVerifiedAt: String? = null,
    val student: StudentDto? = null
)

@Serializable
data class PermitDto(
    val id: String,
    @SerialName("studentId") val studentId: String,
    val type: String,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null,
    val status: String,
    val reason: String? = null,
    val note: String? = null,
    @SerialName("rejectionReason") val rejectionReason: String? = null,
    @SerialName("attachmentUrl") val attachmentUrl: String? = null,
    @SerialName("approvedById") val approvedById: String? = null,
    @SerialName("approvedAt") val approvedAt: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    val student: StudentDto? = null,
    val members: List<PermitMemberDto> = emptyList()
)

@Serializable
data class CreatePermitRequest(
    @SerialName("studentId") val studentId: String,
    val type: String,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null,
    val reason: String? = null,
    @SerialName("memberIds") val memberIds: List<String>? = null
)

@Serializable
data class CreateKioskPermitRequest(
    @SerialName("memberIds") val memberIds: List<String>,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null,
    val reason: String? = null,
    @SerialName("clientId") val clientId: String? = null
)

@Serializable
data class UpdatePermitStatusRequest(
    val status: String,
    val note: String? = null,
    @SerialName("rejectionReason") val rejectionReason: String? = null,
    @SerialName("startDate") val startDate: String? = null,
    @SerialName("endDate") val endDate: String? = null,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null
)

@Serializable
data class VerifyPermitScanRequest(
    @SerialName("permitId") val permitId: String? = null,
    @SerialName("studentId") val studentId: String,
    @SerialName("confidenceScore") val confidenceScore: Float = 0f,
    @SerialName("deviceId") val deviceId: String? = null,
    val timestamp: Long? = null,
    @SerialName("clientId") val clientId: String? = null
)

@Serializable
data class PermitVerificationBatchRequest(
    val logs: List<VerifyPermitScanRequest>
)

@Serializable
data class KioskPermitListResponse(
    val data: List<PermitDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize") val pageSize: Int
)

@Serializable
data class SyncPermitsResponse(
    val data: List<PermitDto>
)

@Serializable
data class PermitVerifiedData(
    val log: PermitVerifyLogDto? = null,
    val member: PermitMemberVerificationDto? = null,
    val idempotent: Boolean = false
)

@Serializable
data class PermitVerifyLogDto(
    val id: String,
    @SerialName("studentId") val studentId: String,
    @SerialName("studentName") val studentName: String,
    val action: String,
    val timestamp: String
)

@Serializable
data class PermitMemberVerificationDto(
    val id: String,
    @SerialName("keluarVerifiedAt") val keluarVerifiedAt: String? = null,
    @SerialName("kembaliVerifiedAt") val kembaliVerifiedAt: String? = null
)

@Serializable
data class PermitQuotaResponse(
    @SerialName("permitsUsed") val permitsUsed: Int,
    @SerialName("maxPermits") val maxPermits: Int
)

@Serializable
data class PermitListResponse(
    val data: List<PermitDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize") val pageSize: Int
)