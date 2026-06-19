package com.facegate.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facegate.core.data.local.entity.CampusRuleEntity

@Dao
interface CampusRuleDao {
    @Query("SELECT * FROM campus_rules ORDER BY dayOfWeek ASC, priority DESC")
    suspend fun getAll(): List<CampusRuleEntity>

    @Query("SELECT * FROM campus_rules WHERE dayOfWeek = :dayOfWeek ORDER BY priority DESC")
    suspend fun getByDay(dayOfWeek: Int): List<CampusRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CampusRuleEntity>)

    @Query("DELETE FROM campus_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM campus_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
