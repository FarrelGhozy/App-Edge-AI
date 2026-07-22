package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_metrics")
data class ScanMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val studentId: String? = null,
    val predictedStudentId: String? = null,
    val topSimilarity: Float? = null,
    val gap: Float? = null,
    val confidence: Float? = null,
    val decision: String? = null,      // "CONFIDENT"/"MEDIUM"/"WEAK"/"NO_MATCH"
    val detectionConfidence: Float? = null,
    val livenessScore: Float? = null,
    val responseTimeMs: Long? = null,
    val deviceId: String? = null,
    val isSynced: Boolean = false
)
