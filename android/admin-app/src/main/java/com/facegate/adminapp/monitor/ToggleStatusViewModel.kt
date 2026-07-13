package com.facegate.adminapp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.OutsideStudent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToggleStatusState(
    val count: Int = 0,
    val students: List<OutsideStudent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ToggleStatusViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToggleStatusState())
    val uiState: StateFlow<ToggleStatusState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = ToggleStatusState(isLoading = true)
            try {
                val response = apiService.getOutsideNow()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _uiState.value = ToggleStatusState(
                        count = body.count,
                        students = body.students
                    )
                } else {
                    _uiState.value = ToggleStatusState(error = "Gagal memuat data")
                }
            } catch (e: Exception) {
                _uiState.value = ToggleStatusState(error = "Gagal terhubung")
            }
        }
    }
}
