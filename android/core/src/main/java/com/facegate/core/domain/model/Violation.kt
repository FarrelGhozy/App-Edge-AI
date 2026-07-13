package com.facegate.core.domain.model

data class Violation(
    val id: String,
    val studentId: String,
    val studentName: String? = null,
    val type: String,
    val description: String? = null,
    val action: String? = null,
    val timestamp: String,
    val isResolved: Boolean = false,
    val resolvedAt: String? = null,
    val resolvedNote: String? = null,
    val createdAt: String? = null
)
