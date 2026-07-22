package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val id: String,
    val date: String,        // "2024-01-01"
    val name: String,
    val isActive: Boolean = true
)
