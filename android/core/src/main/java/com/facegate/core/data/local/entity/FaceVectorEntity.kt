package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_vectors")
data class FaceVectorEntity(
    @PrimaryKey
    val studentId: String,
    val vector: FloatArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceVectorEntity) return false
        return studentId == other.studentId
    }

    override fun hashCode(): Int = studentId.hashCode()
}
