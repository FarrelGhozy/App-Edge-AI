package com.facegate.adminapp.permits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingApprovalState(
    val permits: List<PermitItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PendingApprovalViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingApprovalState())
    val uiState: StateFlow<PendingApprovalState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getPermits(status = "pending")
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.data.map { dto ->
                        PermitItem(
                            id = dto.id,
                            studentName = dto.student?.name ?: dto.studentId,
                            type = dto.type,
                            status = dto.status,
                            startDate = dto.startDate.take(10),
                            endDate = dto.endDate.take(10)
                        )
                    }
                    _uiState.value = PendingApprovalState(
                        permits = items,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Gagal memuat data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Gagal terhubung ke server"
                )
            }
        }
    }
}
