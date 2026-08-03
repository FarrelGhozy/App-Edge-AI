package com.facegate.adminapp.permits

import androidx.lifecycle.SavedStateHandle
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
    private val apiService: ApiService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // #106: pulihkan isian form setelah process death / rotasi.
        PermitFormState(
            type = savedStateHandle["type"] ?: "izin_harian",
            selectedStudentId = savedStateHandle["studentId"],
            startDate = savedStateHandle["startDate"] ?: "",
            endDate = savedStateHandle["endDate"] ?: "",
            startTime = savedStateHandle["startTime"] ?: "",
            endTime = savedStateHandle["endTime"] ?: "",
            reason = savedStateHandle["reason"] ?: ""
        )
    )
    val uiState: StateFlow<PermitFormState> = _uiState.asStateFlow()

    fun loadStudents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                // #98: loop semua halaman — santri >200 pertama harus muncul
                // di dropdown (sebelumnya permit tak bisa dibuat utk mereka).
                val all = mutableListOf<StudentBrief>()
                var page = 1
                while (true) {
                    val response = apiService.getStudents(page = page, pageSize = 200)
                    if (!response.isSuccessful || response.body() == null) {
                        _uiState.value = _uiState.value.copy(error = "Gagal memuat data mahasiswa")
                        return@launch
                    }
                    val body = response.body()!!
                    all += body.data.map { dto ->
                        StudentBrief(id = dto.id, nim = dto.nim, name = dto.name)
                    }
                    // pageSize*totalPages cukup; hentikan saat halaman penuh habis
                    if (body.data.isEmpty() || page * body.pageSize >= body.total) break
                    page++
                }
                _uiState.value = _uiState.value.copy(students = all)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal terhubung ke server")
            }
        }
    }

    fun setStudent(id: String) {
        savedStateHandle["studentId"] = id
        _uiState.value = _uiState.value.copy(selectedStudentId = id)
    }

    fun setType(type: String) {
        savedStateHandle["type"] = type
        _uiState.value = _uiState.value.copy(type = type)
    }

    fun setStartDate(date: String) {
        savedStateHandle["startDate"] = date
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun setEndDate(date: String) {
        savedStateHandle["endDate"] = date
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun setStartTime(time: String) {
        savedStateHandle["startTime"] = time
        _uiState.value = _uiState.value.copy(startTime = time)
    }

    fun setEndTime(time: String) {
        savedStateHandle["endTime"] = time
        _uiState.value = _uiState.value.copy(endTime = time)
    }

    fun setReason(reason: String) {
        savedStateHandle["reason"] = reason
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
