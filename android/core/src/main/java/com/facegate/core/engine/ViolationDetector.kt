package com.facegate.core.engine

import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ViolationCheckResult(
    val isViolation: Boolean,
    val violationType: String? = null,
    val message: String? = null
)

class ViolationDetector @Inject constructor(
    private val campusRuleDao: CampusRuleDao
) {
    suspend fun check(action: ToggleAction, student: StudentInfo): ViolationCheckResult {
        return check(action, student, java.time.ZonedDateTime.now(ZoneId.of("Asia/Jakarta")))
    }

    /** Overload testable — waktu di-inject untuk unit test (#64). */
    internal suspend fun check(
        action: ToggleAction,
        student: StudentInfo,
        now: java.time.ZonedDateTime
    ): ViolationCheckResult {
        if (action != ToggleAction.KELUAR) {
            return ViolationCheckResult(false)
        }

        val dayOfWeek = now.dayOfWeek.value % 7
        val currentTime = now.toLocalTime()

        val rules = campusRuleDao.getByDay(dayOfWeek)

        for (rule in rules) {
            if (!rule.isRestricted) continue

            if (!rule.appliesToAll) {
                if (rule.studyProgram != null && rule.studyProgram != student.studyProgram) continue
                if (rule.academicYear != null && rule.academicYear != student.academicYear) continue
            }

            val ruleStart = LocalTime.parse(rule.startTime)
            val ruleEnd = LocalTime.parse(rule.endTime)

            // #64: rule bisa overnight (22:00–05:00). Kondisi
            // `start <= t < end` mustahil true untuk rentang lintas-midnight:
            // rule normal aktif jika start <= t < end; rule overnight aktif
            // jika t >= start ATAU t < end.
            val overnight = ruleEnd.isBefore(ruleStart)
            val isInRestrictedWindow = if (overnight) {
                !currentTime.isBefore(ruleStart) || currentTime.isBefore(ruleEnd)
            } else {
                !currentTime.isBefore(ruleStart) && currentTime.isBefore(ruleEnd)
            }

            if (isInRestrictedWindow) {
                return ViolationCheckResult(
                    isViolation = true,
                    violationType = "restricted_hours",
                    message = "Keluar pada jam terlarang (${rule.startTime}-${rule.endTime})"
                )
            }
        }

        return ViolationCheckResult(false)
    }
}

data class StudentInfo(
    val id: String,
    val studyProgram: String,
    val academicYear: String
)
