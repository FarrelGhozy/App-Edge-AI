package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    @SerialName("deviceId") val deviceId: String,
    val name: String,
    val location: String? = null,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("lastPingAt") val lastPingAt: String? = null,
    @SerialName("batteryLevel") val batteryLevel: Double? = null
)

@Serializable
data class DeviceRegisterRequest(
    @SerialName("deviceId") val deviceId: String,
    val name: String,
    val location: String? = null
)

@Serializable
data class DevicePingRequest(
    @SerialName("batteryLevel") val batteryLevel: Double? = null
)
