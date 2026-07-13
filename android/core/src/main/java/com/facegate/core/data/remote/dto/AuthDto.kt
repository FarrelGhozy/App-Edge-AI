package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val `data`: LoginData
)

@Serializable
data class LoginData(
    val token: String,
    val admin: AdminDto
)

@Serializable
data class AdminDto(
    val id: String,
    val username: String,
    @SerialName("displayName")
    val displayName: String,
    val role: String
)

@Serializable
data class RefreshRequest(
    val token: String
)

@Serializable
data class RefreshResponse(
    val success: Boolean,
    val `data`: RefreshData
)

@Serializable
data class RefreshData(
    val token: String
)
