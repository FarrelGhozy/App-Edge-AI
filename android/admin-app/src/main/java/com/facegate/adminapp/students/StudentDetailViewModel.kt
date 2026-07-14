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
    val isDeleted: Boolean = false,
    val faceRegistered: Boolean = false,
    val faceUpdatedAt: String? = null,
    val showDeleteConfirm: Boolean = false,
    val showDeleteFaceConfirm: Boolean = false,
    val isDeletingFace: Boolean = false,
    val faceDeleteError: String? = null
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
                    val student = response.body()!!
                    val faceVectors = student.faceVectors
                    _uiState.value = StudentDetailState(
                        student = student,
                        faceRegistered = student.faceRegistered,
                        faceUpdatedAt = faceVectors?.firstOrNull()?.updatedAt
                    )
                } else {
                    _uiState.value = StudentDetailState(error = "Mahasiswa tidak ditemukan")
                }
            } catch (e: Exception) {
                _uiState.value = StudentDetailState(error = "Gagal terhubung")
            }
        }
    }

    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
            try {
                apiService.deleteStudent(id)
                _uiState.value = _uiState.value.copy(isDeleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal menghapus")
            }
        }
    }

    fun showDeleteFaceConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteFaceConfirm = true)
    }

    fun hideDeleteFaceConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteFaceConfirm = false)
    }

    fun deleteFace(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showDeleteFaceConfirm = false,
                isDeletingFace = true,
                faceDeleteError = null
            )
            try {
                apiService.deleteFace(id)
                _uiState.value = _uiState.value.copy(
                    isDeletingFace = false,
                    faceRegistered = false,
                    faceUpdatedAt = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeletingFace = false,
                    faceDeleteError = "Gagal menghapus wajah"
                )
            }
        }
    }

    fun onFaceRegistered() {
        _uiState.value = _uiState.value.copy(faceRegistered = true)
    }

    fun clearFaceDeleteError() {
        _uiState.value = _uiState.value.copy(faceDeleteError = null)
    }
}
