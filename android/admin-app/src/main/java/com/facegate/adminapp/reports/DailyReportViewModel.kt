package com.facegate.adminapp.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.DailyReportLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DailyReportState(
    val keluarCount: Int = 0,
    val kembaliCount: Int = 0,
    val stillOutsideCount: Int = 0,
    val logs: List<DailyReportLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyReportState())
    val uiState: StateFlow<DailyReportState> = _uiState.asStateFlow()

    fun loadToday() {
        load(LocalDate.now().toString())
    }

    fun load(date: String) {
        viewModelScope.launch {
            _uiState.value = DailyReportState(isLoading = true)
            try {
                val response = apiService.getDailyReport(date)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _uiState.value = DailyReportState(
                        keluarCount = body.keluarCount,
                        kembaliCount = body.kembaliCount,
                        stillOutsideCount = body.stillOutsideCount,
                        logs = body.logs
                    )
                } else {
                    _uiState.value = DailyReportState(error = "Gagal memuat laporan")
                }
            } catch (e: Exception) {
                _uiState.value = DailyReportState(error = "Gagal terhubung")
            }
        }
    }
}
