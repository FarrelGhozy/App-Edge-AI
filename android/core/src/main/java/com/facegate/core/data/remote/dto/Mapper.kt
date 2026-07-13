package com.facegate.core.data.remote.dto

import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.data.local.entity.CampusRuleEntity
import com.facegate.core.data.local.entity.FaceVectorEntity
import com.facegate.core.data.local.entity.StudentEntity

fun StudentDto.toEntity() = StudentEntity(
    id = id,
    nim = nim,
    name = name,
    studyProgram = studyProgram,
    academicYear = academicYear,
    phone = phone,
    email = email,
    isActive = isActive,
    photoUrl = photoUrl,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: ""
)

fun FaceVectorDto.toEntity() = FaceVectorEntity(
    studentId = studentId,
    vector = vector.toFloatArray(),
    updatedAt = updatedAt
)

fun CampusRuleDto.toEntity() = CampusRuleEntity(
    id = id,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    isRestricted = isRestricted,
    appliesToAll = appliesToAll,
    studyProgram = studyProgram,
    academicYear = academicYear,
    priority = priority,
    updatedAt = updatedAt ?: ""
)

fun List<Float>.toFloatArray(): FloatArray {
    val arr = FloatArray(size)
    for (i in indices) arr[i] = this[i]
    return arr
}
