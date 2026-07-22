package com.facegate.core.data.local.dao

import androidx.room.*
import com.facegate.core.data.local.entity.GlobalSettingEntity

@Dao
interface GlobalSettingDao {
    @Query("SELECT * FROM global_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): GlobalSettingEntity?

    @Query("SELECT * FROM global_settings")
    suspend fun getAll(): List<GlobalSettingEntity>

    @Query("DELETE FROM global_settings")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<GlobalSettingEntity>)
}
