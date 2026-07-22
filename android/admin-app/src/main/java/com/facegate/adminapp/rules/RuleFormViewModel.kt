package com.facegate.adminapp.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
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
                val response = apiService.getRule(ruleId)
                if (response.isSuccessful && response.body()?.data != null) {
                    val rule = response.body()!!.data!!
                    _uiState.value = RuleFormState(
                        dayOfWeek = rule.dayOfWeek,
                        startTime = rule.startTime,
                        endTime = rule.endTime,
                        isRestricted = rule.isRestricted,
                        studyProgram = rule.studyProgram ?: "",
                        academicYear = rule.academicYear ?: ""
                    )
                } else if (response.code() == 404) {
                    _uiState.value = _uiState.value.copy(error = "Aturan tidak ditemukan")
                } else {
                    _uiState.value = _uiState.value.copy(error = "Gagal memuat aturan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal terhubung ke server")
            }
        }
    }

    fun setDayOfWeek(day: Int) { _uiState.value = _uiState.value.copy(dayOfWeek = day) }
    fun setStartTime(t: String) { _uiState.value = _uiState.value.copy(startTime = t) }
    fun setEndTime(t: String) { _uiState.value = _uiState.value.copy(endTime = t) }
    fun setRestricted(r: Boolean) { _uiState.value = _uiState.value.copy(isRestricted = r) }
    fun setStudyProgram(s: String) { _uiState.value = _uiState.value.copy(studyProgram = s) }
    fun setAcademicYear(a: String) { _uiState.value = _uiState.value.copy(academicYear = a) }

    fun submit(ruleId: String? = null) {
        val s = _uiState.value
        if (s.startTime.isBlank() || s.endTime.isBlank()) {
            _uiState.value = s.copy(error = "Isi jam mulai dan jam selesai")
            return
        }

        _uiState.value = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val body = mapOf<String, Any>(
                    "dayOfWeek" to s.dayOfWeek,
                    "startTime" to s.startTime.trim(),
                    "endTime" to s.endTime.trim(),
                    "isRestricted" to s.isRestricted,
                    "studyProgram" to (s.studyProgram.ifBlank { "" }),
                    "academicYear" to (s.academicYear.ifBlank { "" })
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
