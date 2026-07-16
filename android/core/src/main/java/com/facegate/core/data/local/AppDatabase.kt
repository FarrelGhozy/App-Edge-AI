package com.facegate.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.data.local.entity.CampusRuleEntity
import com.facegate.core.data.local.entity.FaceVectorEntity
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.local.converter.Converters

@Database(
    entities = [
        StudentEntity::class,
        FaceVectorEntity::class,
        AttendanceLogEntity::class,
        CampusRuleEntity::class,
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun faceVectorDao(): FaceVectorDao
    abstract fun attendanceLogDao(): AttendanceLogDao
    abstract fun campusRuleDao(): CampusRuleDao
}
