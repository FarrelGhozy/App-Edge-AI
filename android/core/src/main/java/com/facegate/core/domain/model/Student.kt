package com.facegate.core.domain.model

data class Student(
    val id: String,
    val nim: String,
    val name: String,
    val studyProgram: String,
    val academicYear: String,
    val phone: String? = null,
    val email: String? = null,
    val isActive: Boolean = true,
    val photoUrl: String? = null,
    val faceRegistered: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
