package com.facegate.core.domain.model

data class CampusRule(
    val id: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val isRestricted: Boolean = true,
    val appliesToAll: Boolean = true,
    val studyProgram: String? = null,
    val academicYear: String? = null,
    val priority: Int = 0,
    val updatedAt: String? = null
)
