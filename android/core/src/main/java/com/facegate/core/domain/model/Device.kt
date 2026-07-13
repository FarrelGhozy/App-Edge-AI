package com.facegate.core.domain.model

data class Device(
    val deviceId: String,
    val name: String,
    val location: String? = null,
    val isActive: Boolean = true,
    val lastPingAt: String? = null,
    val batteryLevel: Double? = null
)
