package com.facegate.adminapp.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceLogDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val totalStudents: Int = 0,
    val currentlyOutside: Int = 0,
    val violationsToday: Int = 0,
    val recentScans: List<AttendanceLogDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getDashboardSummary()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = DashboardState(
                        totalStudents = data.totalStudents,
                        currentlyOutside = data.currentlyOutside,
                        violationsToday = data.violationsToday,
                        recentScans = data.recentScans
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat data")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung ke server")
            }
        }
    }
}
