package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummaryResponse(
    @SerialName("totalStudents")
    val totalStudents: Int,
    @SerialName("currentlyOutside")
    val currentlyOutside: Int,
    @SerialName("violationsToday")
    val violationsToday: Int,
    @SerialName("recentScans")
    val recentScans: List<AttendanceLogDto>
)
