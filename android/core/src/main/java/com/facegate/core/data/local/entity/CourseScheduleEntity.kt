package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "course_schedules")
data class CourseScheduleEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val courseName: String,
    val dayOfWeek: Int,          // 0=Sunday, 1=Monday, etc.
    val startTime: String,       // "08:00"
    val endTime: String,         // "09:30"
    val room: String? = null,
    val lecturer: String? = null,
    val isActive: Boolean = true
)
