package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ViolationDto(
    val id: String,
    @SerialName("studentId") val studentId: String,
    @SerialName("studentName") val studentName: String = "",
    val type: String,
    val description: String? = null,
    val action: String? = null,
    val timestamp: String,
    @SerialName("isResolved") val isResolved: Boolean = false,
    @SerialName("resolvedAt") val resolvedAt: String? = null,
    @SerialName("resolvedNote") val resolvedNote: String? = null,
    @SerialName("createdAt") val createdAt: String? = null
)

@Serializable
data class CreateViolationRequest(
    @SerialName("studentId") val studentId: String,
    val type: String,
    val description: String? = null,
    val action: String? = null
)

@Serializable
data class ResolveViolationRequest(
    @SerialName("resolvedNote") val resolvedNote: String? = null
)

@Serializable
data class ViolationListResponse(
    val data: List<ViolationDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize") val pageSize: Int
)
