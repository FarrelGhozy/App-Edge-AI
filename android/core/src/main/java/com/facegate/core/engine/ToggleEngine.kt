package com.facegate.core.engine

import com.facegate.core.data.local.dao.AttendanceLogDao
import java.time.LocalDate
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

/**
 * Toggle engine — determine apakah scan adalah KELUAR atau KEMBALI.
 * 
 * Fix: mencari LAST log SECARA GLOBAL (tidak filter hari ini) untuk handle
 * cross-midnight session (KELUAR jam 23:50 WIB, KEMBALI jam 00:10 WIB besok).
 */
class ToggleEngine @Inject constructor(
    private val attendanceLogDao: AttendanceLogDao
) {
    suspend fun determineAction(studentId: String): ToggleResult {
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.of("Asia/Jakarta"))
            .toInstant()
            .toEpochMilli()

        // Cari log terakhir GLOBAL (tanpa filter tanggal)
        // untuk handle cross-midnight: jika KELUAR jam 23:50 tadi malam,
        // maka scan jam 00:10 besok harus jadi KEMBALI, bukan KELUAR baru
        val latestLogGlobal = attendanceLogDao.getLatestByStudentId(studentId)

        if (latestLogGlobal == null) {
            return ToggleResult(ToggleAction.KELUAR, null)
        }

        // Jika log terakhir dari hari ini, gunakan toggle normal
        if (latestLogGlobal.timestamp >= todayStart) {
            return if (latestLogGlobal.action == "keluar") {
                ToggleResult(ToggleAction.KEMBALI, "keluar")
            } else {
                ToggleResult(ToggleAction.KELUAR, "kembali")
            }
        }

        // Jika log terakhir dari KEMARIN dan action = "keluar",
        // berarti student belum kembali → scan ini = KEMBALI
        if (latestLogGlobal.action == "keluar") {
            return ToggleResult(ToggleAction.KEMBALI, "keluar")
        }

        // Default: scan pertama hari ini = KELUAR
        return ToggleResult(ToggleAction.KELUAR, null)
    }
}
