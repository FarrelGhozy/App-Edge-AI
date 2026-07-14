package com.facegate.adminapp.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.ViolationDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ViolationReportState(
    val violations: List<ViolationDto> = emptyList(),
    val total: Int = 0,
    val from: String = LocalDate.now().withDayOfMonth(1).toString(),
    val to: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ViolationReportViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViolationReportState())
    val uiState: StateFlow<ViolationReportState> = _uiState.asStateFlow()

    init { load() }

    fun setFrom(v: String) { _uiState.value = _uiState.value.copy(from = v) }
    fun setTo(v: String) { _uiState.value = _uiState.value.copy(to = v) }

    fun load() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = apiService.getViolationReport(from = s.from, to = s.to)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        violations = data.violations,
                        total = data.total,
                        isLoading = false
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
