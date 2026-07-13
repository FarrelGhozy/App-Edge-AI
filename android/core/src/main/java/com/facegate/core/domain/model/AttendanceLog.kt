package com.facegate.core.domain.model

data class AttendanceLog(
    val id: String,
    val studentId: String,
    val studentName: String,
    val action: String,
    val timestamp: String,
    val confidenceScore: Float? = null,
    val isViolation: Boolean = false,
    val violationType: String? = null,
    val deviceId: String? = null,
    val photoCapture: String? = null,
    val isSynced: Boolean = true
)
