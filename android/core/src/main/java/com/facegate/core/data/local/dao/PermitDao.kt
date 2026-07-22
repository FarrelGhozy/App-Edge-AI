package com.facegate.core.data.local.dao

import androidx.room.*
import com.facegate.core.data.local.entity.PermitEntity

@Dao
interface PermitDao {
    @Query("SELECT * FROM permits WHERE studentId = :studentId AND isActiveLocally = 1")
    suspend fun getByStudent(studentId: String): List<PermitEntity>

    @Query("SELECT * FROM permits WHERE studentId = :studentId AND status = 'approved' AND isActiveLocally = 1")
    suspend fun getActiveByStudent(studentId: String): List<PermitEntity>

    @Query("DELETE FROM permits")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permits: List<PermitEntity>)
}
