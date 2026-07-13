package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyReportResponse(
    val date: String,
    @SerialName("totalLogs") val totalLogs: Int,
    @SerialName("studentCount") val studentCount: Int,
    @SerialName("keluarCount") val keluarCount: Int,
    @SerialName("kembaliCount") val kembaliCount: Int,
    @SerialName("stillOutsideCount") val stillOutsideCount: Int,
    val logs: List<DailyReportLog> = emptyList()
)

@Serializable
data class DailyReportLog(
    val id: String,
    @SerialName("studentId") val studentId: String,
    @SerialName("studentName") val studentName: String,
    val action: String,
    val timestamp: String,
    @SerialName("isViolation") val isViolation: Boolean = false
)

@Serializable
data class MonthlyReportResponse(
    val month: Int,
    val year: Int,
    @SerialName("totalStudents") val totalStudents: Int,
    val stats: List<StudentMonthlyStat>
)

@Serializable
data class StudentMonthlyStat(
    @SerialName("studentId") val studentId: String,
    val nim: String,
    val name: String,
    @SerialName("studyProgram") val studyProgram: String,
    @SerialName("keluarCount") val keluarCount: Int,
    @SerialName("totalDurationHours") val totalDurationHours: Double,
    @SerialName("violationCount") val violationCount: Int,
    @SerialName("permitCount") val permitCount: Int
)

@Serializable
data class ViolationReportResponse(
    val from: String,
    val to: String,
    val total: Int,
    val violations: List<ViolationDto>
)

@Serializable
data class OutsideNowResponse(
    val count: Int,
    val students: List<OutsideStudent>
)

@Serializable
data class OutsideStudent(
    val id: String,
    val nim: String,
    val name: String,
    @SerialName("studyProgram") val studyProgram: String,
    @SerialName("keluarSince") val keluarSince: String? = null
)
