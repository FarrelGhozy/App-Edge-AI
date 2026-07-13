package com.facegate.adminapp.permits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreatePermitRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermitFormState(
    val students: List<StudentBrief> = emptyList(),
    val selectedStudentId: String? = null,
    val type: String = "izin_harian",
    val startDate: String = "",
    val endDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PermitFormViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermitFormState())
    val uiState: StateFlow<PermitFormState> = _uiState.asStateFlow()

    fun loadStudents() {
        viewModelScope.launch {
            try {
                val response = apiService.getStudents(page = 1, pageSize = 200)
                if (response.isSuccessful && response.body() != null) {
                    val briefs = response.body()!!.data.map { dto ->
                        StudentBrief(id = dto.id, nim = dto.nim, name = dto.name)
                    }
                    _uiState.value = _uiState.value.copy(students = briefs)
                }
            } catch (_: Exception) {}
        }
    }

    fun setStudent(id: String) {
        _uiState.value = _uiState.value.copy(selectedStudentId = id)
    }

    fun setType(type: String) {
        _uiState.value = _uiState.value.copy(type = type)
    }

    fun setStartDate(date: String) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun setEndDate(date: String) {
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun setStartTime(time: String) {
        _uiState.value = _uiState.value.copy(startTime = time)
    }

    fun setEndTime(time: String) {
        _uiState.value = _uiState.value.copy(endTime = time)
    }

    fun setReason(reason: String) {
        _uiState.value = _uiState.value.copy(reason = reason)
    }

    fun submit() {
        val s = _uiState.value
        if (s.selectedStudentId == null) {
            _uiState.value = s.copy(error = "Pilih mahasiswa")
            return
        }
        if (s.startDate.isBlank() || s.endDate.isBlank()) {
            _uiState.value = s.copy(error = "Isi tanggal")
            return
        }

        viewModelScope.launch {
            _uiState.value = s.copy(isSubmitting = true, error = null)
            try {
                val request = CreatePermitRequest(
                    studentId = s.selectedStudentId!!,
                    type = s.type,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    startTime = s.startTime.ifBlank { null },
                    endTime = s.endTime.ifBlank { null },
                    reason = s.reason.ifBlank { null }
                )
                val response = apiService.createPermit(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "Gagal menyimpan izin"
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
