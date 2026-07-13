package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudentDto(
    val id: String,
    val nim: String,
    val name: String,
    @SerialName("studyProgram")
    val studyProgram: String,
    @SerialName("academicYear")
    val academicYear: String,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("isActive")
    val isActive: Boolean = true,
    @SerialName("photoUrl")
    val photoUrl: String? = null,
    @SerialName("faceRegistered")
    val faceRegistered: Boolean = false,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null
)

@Serializable
data class StudentListResponse(
    val data: List<StudentDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize")
    val pageSize: Int
)

@Serializable
data class CreateStudentRequest(
    val nim: String,
    val name: String,
    @SerialName("studyProgram")
    val studyProgram: String,
    @SerialName("academicYear")
    val academicYear: String,
    val phone: String? = null,
    val email: String? = null
)
