package com.facegate.core.data.local.dao

import androidx.room.*
import com.facegate.core.data.local.entity.CourseScheduleEntity

@Dao
interface CourseScheduleDao {
    @Query("SELECT * FROM course_schedules WHERE studentId = :studentId AND dayOfWeek = :dayOfWeek AND isActive = 1")
    suspend fun getByStudentAndDay(studentId: String, dayOfWeek: Int): List<CourseScheduleEntity>

    @Query("DELETE FROM course_schedules")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<CourseScheduleEntity>)
}
