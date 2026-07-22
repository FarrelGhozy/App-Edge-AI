package com.facegate.core.engine

import com.facegate.core.data.local.dao.AttendanceLogDao
import javax.inject.Inject

data class SessionInfo(
    val studentId: String,
    val keluarTime: Long,
    val durationMs: Long? = null
)

class SessionTracker @Inject constructor(
    private val attendanceLogDao: AttendanceLogDao
) {
    private val activeSessions = mutableMapOf<String, Long>()

    fun startSession(studentId: String, timestamp: Long) {
        activeSessions[studentId] = timestamp
    }

    fun endSession(studentId: String, timestamp: Long): Long? {
        val startTime = activeSessions.remove(studentId) ?: return null
        return timestamp - startTime
    }

    suspend fun getTotalDurationToday(studentId: String): Long {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.of("Asia/Jakarta"))
            .toInstant()
            .toEpochMilli()

        val logs = attendanceLogDao.getByStudentIdSince(studentId, todayStart)
        var totalDuration = 0L
        var keluarTime: Long? = null

        for (log in logs.sortedBy { it.timestamp }) {
            if (log.action == "keluar") {
                keluarTime = log.timestamp
            } else if (log.action == "kembali" && keluarTime != null) {
                totalDuration += log.timestamp - keluarTime
                keluarTime = null
            }
        }

        if (keluarTime != null) {
            totalDuration += System.currentTimeMillis() - keluarTime
        }

        return totalDuration
    }

    fun clear() {
        activeSessions.clear()
    }
}
