package com.facegate.core.data.local.entity

import androidx.room.Entity
import com.facegate.core.face.IndexEntry

@Entity(tableName = "face_vectors", primaryKeys = ["studentId", "pose"])
data class FaceVectorEntity(
    val studentId: String,
    val pose: String = "",
    val vector: FloatArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceVectorEntity) return false
        return studentId == other.studentId && pose == other.pose
    }

    override fun hashCode(): Int = 31 * studentId.hashCode() + pose.hashCode()

    fun toIndexEntry(): IndexEntry = IndexEntry(studentId, vector)
}
