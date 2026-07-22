package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permits")
data class PermitEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val type: String,              // "izin_harian" / "pengajuan_izin"
    val startDate: String,
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String,            // "approved" / "pending" / "rejected"
    val reason: String? = null,
    val isActiveLocally: Boolean = true
)
