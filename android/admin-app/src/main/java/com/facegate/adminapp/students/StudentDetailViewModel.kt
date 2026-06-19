package com.facegate.adminapp.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.StudentDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentDetailState(
    val student: StudentDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentDetailState())
    val uiState: StateFlow<StudentDetailState> = _uiState.asStateFlow()

    fun loadStudent(id: String) {
        viewModelScope.launch {
            _uiState.value = StudentDetailState(isLoading = true)
            try {
                val response = apiService.getStudent(id)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = StudentDetailState(student = response.body())
                } else {
                    _uiState.value = StudentDetailState(error = "Mahasiswa tidak ditemukan")
                }
            } catch (e: Exception) {
                _uiState.value = StudentDetailState(error = "Gagal terhubung")
            }
        }
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch {
            try {
                apiService.deleteStudent(id)
                _uiState.value = _uiState.value.copy(isDeleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal menghapus")
            }
        }
    }
}
