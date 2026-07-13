package com.facegate.core.domain.model

data class Holiday(
    val id: String,
    val name: String,
    val date: String,
    val type: String = "national",
    val description: String? = null,
    val createdAt: String? = null
)
