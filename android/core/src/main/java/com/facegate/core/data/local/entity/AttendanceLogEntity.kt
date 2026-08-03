package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: String,
    val studentName: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Float = 0f,
    val isViolation: Boolean = false,
    val violationType: String? = null,
    val deviceId: String? = null,
    val photoCapture: String? = null,
    // #119: idempotency key — UUID unik per log offline, dikirim ke server saat
    // batch sync. Retry tidak membuat duplikat (server dedup by clientId).
    val clientId: String? = null,
    val isSynced: Boolean = false
)
