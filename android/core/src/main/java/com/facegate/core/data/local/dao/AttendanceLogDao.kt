package com.facegate.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.facegate.core.data.local.entity.AttendanceLogEntity

@Dao
interface AttendanceLogDao {
    @Query("SELECT * FROM attendance_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs WHERE studentId = :studentId ORDER BY timestamp DESC")
    suspend fun getByStudentId(studentId: String): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs WHERE studentId = :studentId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByStudentId(studentId: String): AttendanceLogEntity?

    @Query("""
        SELECT * FROM attendance_logs 
        WHERE studentId = :studentId AND timestamp >= :since 
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun getLatestByStudentIdSince(studentId: String, since: Long): AttendanceLogEntity?

    @Query("SELECT * FROM attendance_logs WHERE studentId = :studentId AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getByStudentIdSince(studentId: String, since: Long): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<AttendanceLogEntity>

    @Insert
    suspend fun insert(log: AttendanceLogEntity)

    @Insert
    suspend fun insertAll(logs: List<AttendanceLogEntity>)

    @Query("UPDATE attendance_logs SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE attendance_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markManySynced(ids: List<Long>)

    @Query("DELETE FROM attendance_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM attendance_logs WHERE isSynced = 0")
    suspend fun countUnsynced(): Int
}
