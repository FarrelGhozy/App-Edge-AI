package com.facegate.adminapp.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.adminapp.sse.SseClient
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
    val registeredFaces: Int = 0,
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
    private val sessionManager: SessionManager,
    private val sseClient: SseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            sseClient.events.collect { event ->
                when (event.type) {
                    "scan_realtime", "dashboard_update" -> {
                        loadSummary(isAutoRefresh = true)
                    }
                }
            }
        }
        // #127: token kedaluwarsa & refresh gagal → logout otomatis (bukan
        // diam membeku di data lama). UI beralih ke layar login via isLoggedOut.
        viewModelScope.launch {
            sseClient.sessionExpired.collect {
                logout()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            sessionManager.clearSession()
            _uiState.value = _uiState.value.copy(isLoggingOut = false, isLoggedOut = true)
        }
    }

    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        sseClient.connect(viewModelScope)
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(300_000)
                loadSummary(isAutoRefresh = true)
            }
        }
    }

    fun stopAutoRefresh() {
        sseClient.disconnect()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refresh() {
        loadSummary(isRefresh = true)
    }

    fun loadSummary(isRefresh: Boolean = false, isAutoRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            val hasStaleData = current.totalStudents > 0 || current.recentScans.isNotEmpty()
            _uiState.value = current.copy(
                isLoading = !isRefresh && !isAutoRefresh && !hasStaleData,
                isRefreshing = isRefresh,
                // #95: JANGAN reset error untuk refresh — hindari jeda di mana
                // layar berkedip antar pemuatan.
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
                        registeredFaces = data.registeredFaces,
                        recentScans = data.recentScans,
                        isLoading = false,
                        isRefreshing = false
                    )
                } else {
                    // #95: kalau sudah punya data, refresh gagal TIDAK menghapus
                    // dashboard — cukup tandai error sebagai banner.
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (hasStaleData) "Gagal memperbarui data" else "Gagal memuat data"
                    )
                }
            } catch (e: Exception) {
                // #95: jaringan drop saat auto-refresh sesaat → jangan tutup
                // dashboard; pertahankan data lama + tampilkan pesan ringan.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (hasStaleData) "Gagal terhubung ke server" else "Gagal terhubung ke server"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseClient.disconnect()
    }
}
