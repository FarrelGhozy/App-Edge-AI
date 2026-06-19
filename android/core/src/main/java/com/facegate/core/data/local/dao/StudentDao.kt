package com.facegate.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facegate.core.data.local.entity.StudentEntity

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllActive(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE nim = :nim")
    suspend fun getByNim(nim: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM students WHERE isActive = 1")
    suspend fun count(): Int
}
