package com.facegate.adminapp.holiday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.HolidayDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HolidayListState(
    val holidays: List<HolidayDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HolidayListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HolidayListState())
    val uiState: StateFlow<HolidayListState> = _uiState.asStateFlow()

    fun loadHolidays() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getHolidays()
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        holidays = response.body()!!,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Gagal memuat data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Gagal terhubung ke server"
                )
            }
        }
    }

    fun deleteHoliday(id: String) {
        viewModelScope.launch {
            try {
                apiService.deleteHoliday(id)
                loadHolidays()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal menghapus hari libur")
            }
        }
    }
}
