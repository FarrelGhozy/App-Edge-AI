package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_settings")
data class GlobalSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val description: String? = null
)
