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

    /**
     * #135: setujui izin — admin bisa mengoreksi tanggal/jam serta menulis
     * note/pesan yang akan dilihat kiosk (field opsional, null = biarkan asli).
     */
    fun approve(
        permitId: String,
        startDate: String? = null,
        endDate: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        note: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val request = UpdatePermitStatusRequest(
                    status = "approved",
                    note = note,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime
                )
                val response = apiService.updatePermitStatus(permitId, request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        actionMessage = "Izin disetujui",
                        permit = _uiState.value.permit?.copy(status = "approved", note = note)
                    )
                } else {
                    val serverError = response.body()?.error
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = serverError ?: "Gagal menyetujui"
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

    /** #135: tolak izin dengan alasan (rejectionReason ditampilkan di kiosk). */
    fun reject(permitId: String, reason: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val request = UpdatePermitStatusRequest(
                    status = "rejected",
                    rejectionReason = reason
                )
                val response = apiService.updatePermitStatus(permitId, request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        actionMessage = "Izin ditolak",
                        permit = _uiState.value.permit?.copy(
                            status = "rejected",
                            rejectionReason = reason
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = response.body()?.error ?: "Gagal menolak"
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
