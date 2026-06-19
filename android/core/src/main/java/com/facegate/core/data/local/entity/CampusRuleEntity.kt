package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campus_rules")
data class CampusRuleEntity(
    @PrimaryKey
    val id: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val isRestricted: Boolean = true,
    val appliesToAll: Boolean = true,
    val studyProgram: String? = null,
    val academicYear: String? = null,
    val priority: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
