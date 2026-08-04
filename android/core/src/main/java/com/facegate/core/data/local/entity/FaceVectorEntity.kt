package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.facegate.core.face.IndexEntry

@Entity(tableName = "face_vectors")
data class FaceVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val pose: String = "",
    val vector: FloatArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceVectorEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    fun toIndexEntry(): IndexEntry = IndexEntry(studentId, vector)
}
