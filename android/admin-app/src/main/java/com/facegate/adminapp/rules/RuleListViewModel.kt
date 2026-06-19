package com.facegate.adminapp.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CampusRuleDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleListState(
    val rules: List<CampusRuleDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RuleListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleListState())
    val uiState: StateFlow<RuleListState> = _uiState.asStateFlow()

    fun loadRules() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getRules()
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(rules = response.body()!!, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung")
            }
        }
    }
}
