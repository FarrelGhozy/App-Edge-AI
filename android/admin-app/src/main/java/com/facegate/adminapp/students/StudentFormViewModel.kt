package com.facegate.adminapp.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateStudentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentFormState(
    val nim: String = "",
    val name: String = "",
    val studyProgram: String = "",
    val academicYear: String = "",
    val phone: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class StudentFormViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentFormState())
    val uiState: StateFlow<StudentFormState> = _uiState.asStateFlow()

    fun loadStudent(id: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getStudent(id)
                if (response.isSuccessful && response.body() != null) {
                    val s = response.body()!!
                    _uiState.value = StudentFormState(
                        nim = s.nim, name = s.name,
                        studyProgram = s.studyProgram, academicYear = s.academicYear,
                        phone = s.phone ?: "", email = s.email ?: ""
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateField(field: String, value: String) {
        _uiState.value = when (field) {
            "nim" -> _uiState.value.copy(nim = value)
            "name" -> _uiState.value.copy(name = value)
            "studyProgram" -> _uiState.value.copy(studyProgram = value)
            "academicYear" -> _uiState.value.copy(academicYear = value)
            "phone" -> _uiState.value.copy(phone = value)
            "email" -> _uiState.value.copy(email = value)
            else -> _uiState.value
        }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val request = CreateStudentRequest(
                    nim = _uiState.value.nim,
                    name = _uiState.value.name,
                    studyProgram = _uiState.value.studyProgram,
                    academicYear = _uiState.value.academicYear,
                    phone = _uiState.value.phone.ifBlank { null },
                    email = _uiState.value.email.ifBlank { null }
                )
                val response = apiService.createStudent(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal menyimpan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung")
            }
        }
    }
}
