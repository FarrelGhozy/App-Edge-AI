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
            _uiState.value = ViolationListState(isLoading = false)
        }
    }
}
