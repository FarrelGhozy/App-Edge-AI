package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequestResponse(
    val requested: Boolean,
    @SerialName("requestedAt")
    val requestedAt: String? = null
)

@Serializable
data class SyncCompleteRequest(
    @SerialName("deviceId")
    val deviceId: String,
    val status: String,
    @SerialName("logsCount")
    val logsCount: Int,
    @SerialName("facesCount")
    val facesCount: Int
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)
