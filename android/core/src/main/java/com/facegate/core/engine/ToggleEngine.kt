package com.facegate.core.engine

import com.facegate.core.data.local.dao.AttendanceLogDao
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
        // #117: jangan pakai start-of-day — log "kembali" jam 23:50 lalu
        // "keluar" jam 00:10 (lintas tengah malam) tidak akan ditemukan karena
        // tengah hari berganti. Pakai window 24 jam terakhir agar state benar
        // melewati batas hari.
        val since = System.currentTimeMillis() - WINDOW_MS

        val latestLog = attendanceLogDao.getLatestByStudentIdSince(studentId, since)

        return if (latestLog == null) {
            ToggleResult(ToggleAction.KELUAR, null)
        } else if (latestLog.action == "keluar") {
            ToggleResult(ToggleAction.KEMBALI, "keluar")
        } else {
            ToggleResult(ToggleAction.KELUAR, "kembali")
        }
    }

    private companion object {
        const val WINDOW_MS = 24L * 60 * 60 * 1000 // 24 jam
    }
}
