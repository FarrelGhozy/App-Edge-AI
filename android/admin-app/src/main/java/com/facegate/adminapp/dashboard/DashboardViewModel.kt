package com.facegate.adminapp.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.SessionManager
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.AttendanceLogDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val totalStudents: Int = 0,
    val currentlyOutside: Int = 0,
    val violationsToday: Int = 0,
    val recentScans: List<AttendanceLogDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            sessionManager.clearSession()
            _uiState.value = _uiState.value.copy(isLoggingOut = false, isLoggedOut = true)
        }
    }

    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                loadSummary(isAutoRefresh = true)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refresh() {
        loadSummary(isRefresh = true)
    }

    fun loadSummary(isRefresh: Boolean = false, isAutoRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isLoading = !isRefresh && !isAutoRefresh && current.totalStudents == 0,
                isRefreshing = isRefresh,
                error = null
            )
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
                    _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false, error = "Gagal memuat data")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false, error = "Gagal terhubung ke server")
            }
        }
    }
}
