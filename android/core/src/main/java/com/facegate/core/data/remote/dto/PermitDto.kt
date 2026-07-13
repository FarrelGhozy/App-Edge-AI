package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("attachmentUrl") val attachmentUrl: String? = null,
    @SerialName("approvedById") val approvedById: String? = null,
    @SerialName("approvedAt") val approvedAt: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    val student: StudentDto? = null
)

@Serializable
data class CreatePermitRequest(
    @SerialName("studentId") val studentId: String,
    val type: String,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null,
    val reason: String? = null
)

@Serializable
data class UpdatePermitStatusRequest(
    val status: String,
    @SerialName("adminId") val adminId: String
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
