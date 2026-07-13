package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("adminId") val adminId: String? = null,
    val type: String,
    val title: String,
    val message: String,
    @SerialName("isRead") val isRead: Boolean = false,
    @SerialName("linkTo") val linkTo: String? = null,
    @SerialName("createdAt") val createdAt: String
)

@Serializable
data class NotificationListResponse(
    val data: List<NotificationDto>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize") val pageSize: Int
)
