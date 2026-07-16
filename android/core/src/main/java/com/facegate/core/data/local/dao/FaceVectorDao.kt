package com.facegate.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facegate.core.data.local.entity.FaceVectorEntity

@Dao
interface FaceVectorDao {
    @Query("SELECT * FROM face_vectors")
    suspend fun getAll(): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE studentId = :studentId ORDER BY pose")
    suspend fun getByStudentId(studentId: String): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE studentId = :studentId AND pose = :pose")
    suspend fun getByStudentIdAndPose(studentId: String, pose: String): FaceVectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<FaceVectorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: FaceVectorEntity)

    @Query("DELETE FROM face_vectors")
    suspend fun deleteAll()

    @Query("DELETE FROM face_vectors WHERE studentId = :studentId")
    suspend fun deleteByStudentId(studentId: String)

    @Query("SELECT COUNT(*) FROM face_vectors")
    suspend fun count(): Int

    @Query("SELECT COUNT(DISTINCT studentId) FROM face_vectors")
    suspend fun countDistinctStudents(): Int
}
