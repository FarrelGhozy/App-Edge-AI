package com.facegate.core.engine

import com.facegate.core.data.local.dao.AttendanceLogDao
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class ToggleAction {
    KELUAR,
    KEMBALI
}

data class ToggleResult(
    val action: ToggleAction,
    val lastAction: String?,
    val sessionDurationMs: Long? = null
)

class ToggleEngine @Inject constructor(
    private val attendanceLogDao: AttendanceLogDao
) {
    suspend fun determineAction(studentId: String): ToggleResult {
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.of("Asia/Jakarta"))
            .toInstant()
            .toEpochMilli()

        val latestLog = attendanceLogDao.getLatestByStudentIdSince(studentId, todayStart)

        return if (latestLog == null) {
            ToggleResult(ToggleAction.KELUAR, null)
        } else if (latestLog.action == "keluar") {
            ToggleResult(ToggleAction.KEMBALI, "keluar")
        } else {
            ToggleResult(ToggleAction.KELUAR, "kembali")
        }
    }
}
