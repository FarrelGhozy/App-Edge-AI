package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey
    val id: String,
    val nim: String,
    val name: String,
    val studyProgram: String,
    val academicYear: String,
    val phone: String? = null,
    val email: String? = null,
    val isActive: Boolean = true,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
