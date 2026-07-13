package com.facegate.adminapp.violations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViolationItem(
    val id: String,
    val studentName: String,
    val type: String,
    val isResolved: Boolean,
    val timestamp: String = ""
)

data class ViolationListState(
    val violations: List<ViolationItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ViolationListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViolationListState())
    val uiState: StateFlow<ViolationListState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getViolations()
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.data.map { dto ->
                        ViolationItem(
                            id = dto.id,
                            studentName = dto.studentId,
                            type = dto.type,
                            isResolved = dto.isResolved,
                            timestamp = dto.timestamp
                        )
                    }
                    _uiState.value = _uiState.value.copy(violations = items, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
