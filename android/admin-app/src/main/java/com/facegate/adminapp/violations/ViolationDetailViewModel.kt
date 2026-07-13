package com.facegate.adminapp.violations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.ResolveViolationRequest
import com.facegate.core.data.remote.dto.ViolationDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViolationDetailState(
    val violation: ViolationDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isProcessing: Boolean = false,
    val resolveNote: String = "",
    val actionMessage: String? = null
)

@HiltViewModel
class ViolationDetailViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViolationDetailState())
    val uiState: StateFlow<ViolationDetailState> = _uiState.asStateFlow()

    fun load(violationId: String) {
        viewModelScope.launch {
            _uiState.value = ViolationDetailState(isLoading = true)
            try {
                val response = apiService.getViolations(studentId = violationId)
                if (response.isSuccessful && response.body() != null) {
                    val v = response.body()!!.data.find { it.id == violationId }
                    _uiState.value = ViolationDetailState(violation = v)
                } else {
                    _uiState.value = ViolationDetailState(error = "Pelanggaran tidak ditemukan")
                }
            } catch (e: Exception) {
                _uiState.value = ViolationDetailState(error = "Gagal terhubung")
            }
        }
    }

    fun setResolveNote(note: String) {
        _uiState.value = _uiState.value.copy(resolveNote = note)
    }

    fun resolve(violationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val request = ResolveViolationRequest(
                    resolvedNote = _uiState.value.resolveNote.ifBlank { null }
                )
                val response = apiService.resolveViolation(violationId, request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        actionMessage = "Pelanggaran diselesaikan",
                        violation = _uiState.value.violation?.copy(isResolved = true)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Gagal menyelesaikan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Gagal terhubung"
                )
            }
        }
    }
}
