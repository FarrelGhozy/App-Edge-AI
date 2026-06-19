package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CampusRuleDto(
    val id: String,
    @SerialName("dayOfWeek")
    val dayOfWeek: Int,
    @SerialName("startTime")
    val startTime: String,
    @SerialName("endTime")
    val endTime: String,
    @SerialName("isRestricted")
    val isRestricted: Boolean = true,
    @SerialName("appliesToAll")
    val appliesToAll: Boolean = true,
    @SerialName("studyProgram")
    val studyProgram: String? = null,
    @SerialName("academicYear")
    val academicYear: String? = null,
    val priority: Int = 0,
    @SerialName("updatedAt")
    val updatedAt: String? = null
)
