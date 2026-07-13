package com.facegate.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaceVectorDto(
    @SerialName("studentId")
    val studentId: String,
    val vector: List<Float>,
    @SerialName("updatedAt")
    val updatedAt: String? = null
)

@Serializable
data class FaceSyncResponse(
    val data: List<FaceVectorDto>,
    val since: String? = null
)

@Serializable
data class UploadFaceRequest(
    val vector: List<Float>
)
