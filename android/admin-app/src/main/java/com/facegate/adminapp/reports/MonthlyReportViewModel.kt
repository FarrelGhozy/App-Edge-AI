package com.facegate.adminapp.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.StudentMonthlyStat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonthlyReportState(
    val stats: List<StudentMonthlyStat> = emptyList(),
    val totalStudents: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyReportState())
    val uiState: StateFlow<MonthlyReportState> = _uiState.asStateFlow()

    fun load(month: Int? = null, year: Int? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getMonthlyReport(month = month, year = year)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = MonthlyReportState(
                        stats = data.stats,
                        totalStudents = data.totalStudents,
                        month = data.month,
                        year = data.year
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat laporan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung ke server")
            }
        }
    }
}
