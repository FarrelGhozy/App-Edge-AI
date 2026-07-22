package com.facegate.core.data.local.dao

import androidx.room.*
import com.facegate.core.data.local.entity.HolidayEntity

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays WHERE date = :date AND isActive = 1 LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntity?

    @Query("SELECT * FROM holidays WHERE isActive = 1")
    suspend fun getAll(): List<HolidayEntity>

    @Query("DELETE FROM holidays")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayEntity>)
}
