package com.facegate.core.domain.model

data class Admin(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String = "admin"
)
