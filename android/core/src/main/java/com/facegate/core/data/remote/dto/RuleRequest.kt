package com.facegate.core.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body request untuk create/update CampusRule.
 *
 * #137: DTO @Serializable — endpoint ini tadinya memakai `Map<String, Any>`
 * yang TIDAK bisa diserialisasi oleh kotlinx-serialization (`Any` tanpa
 * serializer) → setiap createRule/updateRule dari admin-app selalu gagal
 * dengan "Gagal terhubung ke server" (exception sebelum request terkirim).
 */
@Serializable
data class RuleRequest(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val isRestricted: Boolean = true,
    val appliesToAll: Boolean = true,
    val studyProgram: String? = null,
    val academicYear: String? = null,
    val priority: Int = 0
)
