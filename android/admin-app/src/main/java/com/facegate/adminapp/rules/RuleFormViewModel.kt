package com.facegate.adminapp.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.RuleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleFormState(
    val dayOfWeek: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
    val isRestricted: Boolean = true,
    val studyProgram: String = "",
    val academicYear: String = "",
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * Auto-format input jam saat mengetik: hanya digit diambil, sisipkan ":" setelah
 * 2 digit, clamp jam [0,23] & menit [0,59] agar selalu valid.
 * Contoh: "1200" -> "12:00", "1" -> "1", "12" -> "12", "123" -> "12:3", "2559" -> "23:59".
 */
object TimeFormatter {
    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun format(raw: String): String {
        if (raw.isEmpty()) return ""
        fun pad(v: Int) = v.toString().padStart(2, '0')

        // Input yang sudah memakai ":" (paste "08:30") → parse per bagian.
        if (raw.contains(":")) {
            val hPart = raw.substringBefore(":").filter { it.isDigit() }.take(2)
            val mPart = raw.substringAfter(":", "").filter { it.isDigit() }.take(2)
            val hour = hPart.toIntOrNull()?.coerceIn(0, 23) ?: return ""
            if (mPart.isEmpty()) return hour.toString()
            val minute = mPart.toIntOrNull()?.coerceIn(0, 59) ?: 0
            return "${pad(hour)}:${pad(minute)}"
        }

        // Ketik digit berurutan: sisipkan ":" setelah 2 digit, clamp nilai.
        val digits = raw.filter { it.isDigit() }.take(4)
        if (digits.isEmpty()) return ""

        // Ambil maksimal 2 digit pertama sebagai jam. Bila 2 digit itu > 23
        // dan user masih mengetik (3 digit, mis. "915" → jam "9" menit "15"),
        // perlakukan sebagai jam 1 digit + menit 2 digit. Untuk 4 digit penuh
        // (HHMM) atau < 3 digit, pakai 2 digit pertama + clamp ke 23:59.
        val twoHour = digits.take(2).toIntOrNull() ?: 0
        val (hour, minute) = if (twoHour > 23 && digits.length == 3) {
            (digits.take(1).toIntOrNull() ?: 0) to (digits.drop(1).take(2).toIntOrNull() ?: 0)
        } else {
            twoHour.coerceIn(0, 23) to (digits.drop(2).take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0)
        }

        return when {
            digits.length == 1 -> hour.coerceAtMost(9).toString()
            digits.length == 2 -> pad(hour)
            digits.length == 3 -> "${pad(hour)}:${minute.toString().take(1)}"
            else -> "${pad(hour)}:${pad(minute)}"
        }
    }

    fun isValid(value: String): Boolean = TIME_REGEX.matches(value)
}

@HiltViewModel
class RuleFormViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleFormState())
    val uiState: StateFlow<RuleFormState> = _uiState.asStateFlow()

    fun load(ruleId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val response = apiService.getRules()
                if (response.isSuccessful && response.body() != null) {
                    val rule = response.body()!!.find { it.id == ruleId }
                    if (rule != null) {
                        _uiState.value = RuleFormState(
                            dayOfWeek = rule.dayOfWeek,
                            startTime = rule.startTime,
                            endTime = rule.endTime,
                            isRestricted = rule.isRestricted,
                            studyProgram = rule.studyProgram ?: "",
                            academicYear = rule.academicYear ?: ""
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(error = "Aturan tidak ditemukan")
                    }
                } else {
                    _uiState.value = _uiState.value.copy(error = "Gagal memuat aturan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal terhubung ke server")
            }
        }
    }

    fun setDayOfWeek(day: Int) { _uiState.value = _uiState.value.copy(dayOfWeek = day) }
    fun setStartTime(t: String) { _uiState.value = _uiState.value.copy(startTime = TimeFormatter.format(t)) }
    fun setEndTime(t: String) { _uiState.value = _uiState.value.copy(endTime = TimeFormatter.format(t)) }
    fun setRestricted(r: Boolean) { _uiState.value = _uiState.value.copy(isRestricted = r) }
    fun setStudyProgram(s: String) { _uiState.value = _uiState.value.copy(studyProgram = s) }
    fun setAcademicYear(a: String) { _uiState.value = _uiState.value.copy(academicYear = a) }

    fun submit(ruleId: String? = null) {
        val s = _uiState.value
        if (s.startTime.isBlank() || s.endTime.isBlank()) {
            _uiState.value = s.copy(error = "Isi jam mulai dan jam selesai")
            return
        }
        if (!TimeFormatter.isValid(s.startTime) || !TimeFormatter.isValid(s.endTime)) {
            _uiState.value = s.copy(error = "Format jam harus HH:mm (contoh: 08:30)")
            return
        }

        _uiState.value = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                // #137: appliesToAll HARUS dikirim eksplisit. Backend default true,
                // jadi tanpa field ini rule dengan filter prodi/angkatan tetap
                // berlaku untuk SEMUA santri.
                val studyProgram = s.studyProgram.trim().ifBlank { null }
                val academicYear = s.academicYear.trim().ifBlank { null }
                val appliesToAll = studyProgram == null && academicYear == null

                // #137: pakai RuleRequest (@Serializable) — Map<String, Any> tidak
                // bisa diserialisasi kotlinx → createRule/updateRule selalu gagal
                // dengan "Gagal terhubung ke server" (exception sebelum request).
                val body = RuleRequest(
                    dayOfWeek = s.dayOfWeek,
                    startTime = s.startTime.trim(),
                    endTime = s.endTime.trim(),
                    isRestricted = s.isRestricted,
                    appliesToAll = appliesToAll,
                    studyProgram = studyProgram,
                    academicYear = academicYear
                )
                val response = if (ruleId != null) {
                    apiService.updateRule(ruleId, body)
                } else {
                    apiService.createRule(body)
                }
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "Gagal menyimpan aturan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = "Gagal terhubung ke server"
                )
            }
        }
    }
}
