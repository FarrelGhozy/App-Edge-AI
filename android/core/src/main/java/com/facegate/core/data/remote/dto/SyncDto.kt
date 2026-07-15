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
    @SerialName("syncType")
    val syncType: String = "manual",
    @SerialName("logsCount")
    val logsCount: Int = 0,
    @SerialName("facesCount")
    val facesCount: Int = 0
)

@Serializable
data class StatusResponse(
    val success: Boolean
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)
