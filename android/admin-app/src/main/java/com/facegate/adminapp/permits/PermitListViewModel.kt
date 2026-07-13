package com.facegate.adminapp.permits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.PermitDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermitListState(
    val permits: List<PermitItem> = emptyList(),
    val isLoading: Boolean = false,
    val filterStatus: String? = null
)

data class PermitItem(
    val id: String,
    val studentName: String,
    val type: String,
    val status: String,
    val startDate: String = "",
    val endDate: String = ""
)

@HiltViewModel
class PermitListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermitListState())
    val uiState: StateFlow<PermitListState> = _uiState.asStateFlow()

    fun loadPermits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getPermits(
                    status = _uiState.value.filterStatus
                )
                if (response.isSuccessful && response.body() != null) {
                    val permits = response.body()!!.data.map { dto ->
                        PermitItem(
                            id = dto.id,
                            studentName = dto.student?.name ?: dto.studentId,
                            type = dto.type,
                            status = dto.status,
                            startDate = dto.startDate.take(10),
                            endDate = dto.endDate.take(10)
                        )
                    }
                    _uiState.value = _uiState.value.copy(permits = permits, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setFilter(status: String?) {
        _uiState.value = _uiState.value.copy(filterStatus = status)
        loadPermits()
    }
}
