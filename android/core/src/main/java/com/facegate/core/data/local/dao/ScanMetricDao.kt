package com.facegate.core.data.local.dao

import androidx.room.*
import com.facegate.core.data.local.entity.ScanMetricEntity

@Dao
interface ScanMetricDao {
    @Query("SELECT * FROM scan_metrics WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<ScanMetricEntity>

    @Query("SELECT COUNT(*) FROM scan_metrics WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Insert
    suspend fun insert(metric: ScanMetricEntity)

    @Insert
    suspend fun insertAll(metrics: List<ScanMetricEntity>)

    @Query("UPDATE scan_metrics SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM scan_metrics WHERE isSynced = 1")
    suspend fun deleteSynced()

    @Query("DELETE FROM scan_metrics")
    suspend fun deleteAll()
}
