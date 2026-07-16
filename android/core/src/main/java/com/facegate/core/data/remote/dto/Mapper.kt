package com.facegate.core.data.remote.dto

import com.facegate.core.data.local.entity.CampusRuleEntity
import com.facegate.core.data.local.entity.FaceVectorEntity
import com.facegate.core.data.local.entity.StudentEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
    createdAt = parseIsoToMillis(createdAt),
    updatedAt = parseIsoToMillis(updatedAt)
)

fun FaceVectorDto.toEntity() = FaceVectorEntity(
    studentId = studentId,
    pose = pose,
    vector = vector.toFloatArray(),
    updatedAt = parseIsoToMillis(updatedAt)
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
    updatedAt = parseIsoToMillis(updatedAt)
)

fun List<Float>.toFloatArray(): FloatArray {
    val arr = FloatArray(size)
    for (i in indices) arr[i] = this[i]
    return arr
}

private fun parseIsoToMillis(iso: String?): Long {
    if (iso == null) return System.currentTimeMillis()
    return try {
        ZonedDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
