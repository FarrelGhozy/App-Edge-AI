package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HolidayDto(
    val id: String,
    val name: String,
    val date: String,
    val type: String = "national",
    val description: String? = null,
    @SerialName("createdAt") val createdAt: String? = null
)

@Serializable
data class CreateHolidayRequest(
    val name: String,
    val date: String,
    val type: String? = null,
    val description: String? = null
)

@Serializable
data class UpdateHolidayRequest(
    val name: String? = null,
    val date: String? = null,
    val type: String? = null,
    val description: String? = null
)

@Serializable
data class HolidayListResponse(
    val data: List<HolidayDto>
)

@Serializable
data class TodayHolidayResponse(
    @SerialName("isHoliday") val isHoliday: Boolean,
    val holiday: HolidayDto? = null
)
