package com.facegate.adminapp.permits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.PermitDto
import com.facegate.core.data.remote.dto.UpdatePermitStatusRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermitDetailState(
    val permit: PermitDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isProcessing: Boolean = false,
    val actionMessage: String? = null
)

@HiltViewModel
class PermitDetailViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermitDetailState())
    val uiState: StateFlow<PermitDetailState> = _uiState.asStateFlow()

    fun load(permitId: String) {
        viewModelScope.launch {
            _uiState.value = PermitDetailState(isLoading = true)
            try {
                val response = apiService.getPermit(permitId)
                if (response.isSuccessful && response.body()?.data != null) {
                    _uiState.value = PermitDetailState(permit = response.body()!!.data)
                } else {
                    _uiState.value = PermitDetailState(error = "Izin tidak ditemukan")
                }
            } catch (e: Exception) {
                _uiState.value = PermitDetailState(error = "Gagal terhubung")
            }
        }
    }

    fun approve(permitId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val request = UpdatePermitStatusRequest(status = "approved", adminId = "admin")
                val response = apiService.updatePermitStatus(permitId, request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        actionMessage = "Izin disetujui",
                        permit = _uiState.value.permit?.copy(status = "approved")
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Gagal menyetujui"
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

    fun reject(permitId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val request = UpdatePermitStatusRequest(status = "rejected", adminId = "admin")
                val response = apiService.updatePermitStatus(permitId, request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        actionMessage = "Izin ditolak",
                        permit = _uiState.value.permit?.copy(status = "rejected")
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Gagal menolak"
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
