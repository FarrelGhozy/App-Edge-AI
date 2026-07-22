package com.facegate.core.engine

import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId
import javax.inject.Inject

data class ViolationCheckResult(
    val isViolation: Boolean,
    val violationType: String? = null,
    val message: String? = null
)

data class StudentInfo(
    val id: String,
    val studyProgram: String,
    val academicYear: String
)

/**
 * Rule engine untuk memvalidasi scan KELUAR.
 * Memeriksa: permit aktif, hari libur, jam operasional, restricted hours, jadwal kuliah.
 */
class ViolationDetector @Inject constructor(
    private val campusRuleDao: CampusRuleDao,
    private val attendanceLogDao: AttendanceLogDao
) {
    suspend fun check(action: ToggleAction, student: StudentInfo): ViolationCheckResult {
        // Only check on KELUAR actions
        if (action != ToggleAction.KELUAR) {
            return ViolationCheckResult(false)
        }

        val now = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
        val todayStart = now.toLocalDate().atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
        val dayOfWeek = now.dayOfWeek.value % 7  // Sunday=0
        val currentTime = now.toLocalTime()

        // 1. Cek apakah hari ini hari libur
        // (Holiday check dilakukan di service layer — fallback ke tidak ada libur)
        
        // 2. Cek jam operasional (06:00 - 21:00 default)
        val operationalStart = LocalTime.parse("06:00")
        val operationalEnd = LocalTime.parse("21:00")
        if (currentTime.isBefore(operationalStart) || currentTime.isAfter(operationalEnd)) {
            return ViolationCheckResult(
                isViolation = true,
                violationType = "outside_operational_hours",
                message = "Keluar di luar jam operasional (${operationalStart}-${operationalEnd})"
            )
        }

        // 3. Cek jika ada izin aktif hari ini
        val hasPermit = checkActivePermitToday(student.id, todayStart)
        if (hasPermit) {
            return ViolationCheckResult(false)
        }

        // 4. Cek restricted hours rules
        val rules = campusRuleDao.getByDay(dayOfWeek)
        for (rule in rules) {
            if (!rule.isRestricted) continue

            // Check if rule applies to this student
            if (!rule.appliesToAll) {
                if (rule.studyProgram != null && rule.studyProgram != student.studyProgram) continue
                if (rule.academicYear != null && rule.academicYear != student.academicYear) continue
            }

            val ruleStart = LocalTime.parse(rule.startTime)
            val ruleEnd = LocalTime.parse(rule.endTime)

            if (!currentTime.isBefore(ruleStart) && currentTime.isBefore(ruleEnd)) {
                return ViolationCheckResult(
                    isViolation = true,
                    violationType = "restricted_hours",
                    message = "Keluar pada jam terlarang (${rule.startTime}-${rule.endTime})"
                )
            }
        }

        // 5. Cek jadwal kuliah — jika ada kelas, dianggap violation
        val hasSchedule = checkActiveCourseSchedule(student.id, dayOfWeek, currentTime)
        if (hasSchedule) {
            return ViolationCheckResult(
                isViolation = true,
                violationType = "class_hours",
                message = "Ada jadwal kuliah pada jam ini"
            )
        }

        return ViolationCheckResult(false)
    }

    private suspend fun checkActivePermitToday(studentId: String, todayStartMs: Long): Boolean {
        // Query attendance logs for today — look for any permit-related skip
        // In full implementation, this would check a Room PermitEntity table
        // For now, we check if there's a recent scan suggesting permitted leave
        val todayLog = attendanceLogDao.getLatestByStudentIdSince(studentId, todayStartMs)
        // If student has no KELUAR log today, they haven't left yet — no permit check needed
        if (todayLog == null) return false
        // Skip permit check — extended logic would query permits from Room
        return false
    }

    private suspend fun checkActiveCourseSchedule(studentId: String, dayOfWeek: Int, currentTime: LocalTime): Boolean {
        // Would check Room CourseScheduleEntity table
        // For now, no schedule data in Room — return false
        return false
    }
}
