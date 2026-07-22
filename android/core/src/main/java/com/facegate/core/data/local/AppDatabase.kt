package com.facegate.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.facegate.core.data.local.dao.*
import com.facegate.core.data.local.entity.*
import com.facegate.core.data.local.converter.Converters

@Database(
    entities = [
        StudentEntity::class,
        FaceVectorEntity::class,
        AttendanceLogEntity::class,
        CampusRuleEntity::class,
        PermitEntity::class,
        HolidayEntity::class,
        CourseScheduleEntity::class,
        GlobalSettingEntity::class,
        ScanMetricEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun faceVectorDao(): FaceVectorDao
    abstract fun attendanceLogDao(): AttendanceLogDao
    abstract fun campusRuleDao(): CampusRuleDao
    abstract fun permitDao(): PermitDao
    abstract fun holidayDao(): HolidayDao
    abstract fun courseScheduleDao(): CourseScheduleDao
    abstract fun globalSettingDao(): GlobalSettingDao
    abstract fun scanMetricDao(): ScanMetricDao

    companion object {
        /** Migration from v1→v2: Add CampusRuleEntity */
        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS campus_rules (
                    id TEXT NOT NULL PRIMARY KEY,
                    dayOfWeek INTEGER NOT NULL,
                    startTime TEXT NOT NULL,
                    endTime TEXT NOT NULL,
                    isRestricted INTEGER NOT NULL DEFAULT 1,
                    appliesToAll INTEGER NOT NULL DEFAULT 1,
                    studyProgram TEXT,
                    academicYear TEXT,
                    priority INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        /** Migration from v2→v3: Add Permit, Holiday, CourseSchedule, GlobalSetting, ScanMetric */
        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS permits (
                    id TEXT NOT NULL PRIMARY KEY,
                    studentId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    endDate TEXT,
                    startTime TEXT,
                    endTime TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    reason TEXT,
                    isActiveLocally INTEGER NOT NULL DEFAULT 1
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS holidays (
                    id TEXT NOT NULL PRIMARY KEY,
                    date TEXT NOT NULL,
                    name TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS course_schedules (
                    id TEXT NOT NULL PRIMARY KEY,
                    studentId TEXT NOT NULL,
                    courseName TEXT NOT NULL,
                    dayOfWeek INTEGER NOT NULL,
                    startTime TEXT NOT NULL,
                    endTime TEXT NOT NULL,
                    room TEXT,
                    lecturer TEXT,
                    isActive INTEGER NOT NULL DEFAULT 1
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS global_settings (
                    key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL,
                    description TEXT
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS scan_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    studentId TEXT,
                    predictedStudentId TEXT,
                    topSimilarity REAL,
                    gap REAL,
                    confidence REAL,
                    decision TEXT,
                    detectionConfidence REAL,
                    livenessScore REAL,
                    responseTimeMs INTEGER,
                    deviceId TEXT,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
