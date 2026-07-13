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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val isSaved: Boolean = false,
    val savedStudentId: String? = null
)

@HiltViewModel
class StudentFormViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentFormState())
    val uiState: StateFlow<StudentFormState> = _uiState.asStateFlow()

    fun loadStudent(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val response = apiService.getStudent(id)
                if (response.isSuccessful && response.body() != null) {
                    val s = response.body()!!
                    _uiState.value = StudentFormState(
                        nim = s.nim, name = s.name,
                        studyProgram = s.studyProgram, academicYear = s.academicYear,
                        phone = s.phone ?: "", email = s.email ?: ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "Mahasiswa tidak ditemukan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal terhubung ke server")
            }
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
        val s = _uiState.value
        if (s.nim.isBlank() || s.name.isBlank() || s.studyProgram.isBlank() || s.academicYear.isBlank()) {
            _uiState.value = s.copy(error = "Harap isi NIM, Nama, Program Studi, dan Angkatan")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val request = CreateStudentRequest(
                    nim = s.nim,
                    name = s.name,
                    studyProgram = s.studyProgram,
                    academicYear = s.academicYear,
                    phone = s.phone.ifBlank { null },
                    email = s.email.ifBlank { null }
                )
                val response = apiService.createStudent(request)
                if (response.isSuccessful) {
                    val savedId = response.body()?.id
                    _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true, savedStudentId = savedId)
                } else {
                    val msg = parseError(response.errorBody()?.string())
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung ke server")
            }
        }
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody == null) return "Gagal menyimpan"
        return try {
            val obj = Json.decodeFromString<JsonObject>(errorBody)
            obj["error"]?.jsonPrimitive?.content ?: "Gagal menyimpan"
        } catch (_: Exception) {
            "Gagal menyimpan"
        }
    }
}
