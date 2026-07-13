package com.facegate.core.domain.model

data class Permit(
    val id: String,
    val studentId: String,
    val studentName: String? = null,
    val type: String,
    val startDate: String,
    val endDate: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String,
    val reason: String? = null,
    val attachmentUrl: String? = null,
    val approvedById: String? = null,
    val approvedAt: String? = null,
    val createdAt: String? = null
)
