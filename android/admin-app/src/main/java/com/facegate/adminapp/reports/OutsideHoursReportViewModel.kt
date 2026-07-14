package com.facegate.adminapp.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.OutsidePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OutsideHoursState(
    val periods: List<OutsidePeriod> = emptyList(),
    val totalOutside: Int = 0,
    val date: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OutsideHoursReportViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutsideHoursState())
    val uiState: StateFlow<OutsideHoursState> = _uiState.asStateFlow()

    init { load() }

    fun setDate(v: String) { _uiState.value = _uiState.value.copy(date = v) }

    fun load() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = apiService.getOutsideHoursReport(date = s.date)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    _uiState.value = OutsideHoursState(
                        periods = data.periods,
                        totalOutside = data.totalOutside,
                        date = data.date
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung")
            }
        }
    }
}
