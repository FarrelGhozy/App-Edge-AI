package com.facegate.adminapp.attendance

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

data class AttendanceState(
    val logs: List<AttendanceLogDto> = emptyList(),
    val isLoading: Boolean = false,
    val filterDate: String = "",
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceState())
    val uiState: StateFlow<AttendanceState> = _uiState.asStateFlow()

    fun loadLogs(page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) _uiState.value = _uiState.value.copy(error = null)
            try {
                val filterDate = _uiState.value.filterDate
                val response = if (filterDate.isNotBlank()) {
                    apiService.getAttendanceLogs(
                        page = page,
                        startDate = filterDate,
                        endDate = filterDate
                    )
                } else {
                    apiService.getAttendanceLogs(page = page)
                }
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val newLogs = if (page == 1) body.data else _uiState.value.logs + body.data
                    _uiState.value = _uiState.value.copy(
                        logs = newLogs,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasMore = page * body.pageSize < body.total,
                        page = page
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = "Gagal memuat data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false, isLoadingMore = false,
                    error = "Gagal terhubung ke server"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, page = 1)
        loadLogs(page = 1)
    }

    fun loadMore() {
        val nextPage = _uiState.value.page + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        loadLogs(page = nextPage)
    }

    fun setFilterDate(date: String) {
        _uiState.value = _uiState.value.copy(filterDate = date, page = 1, isLoading = true)
        loadLogs(page = 1)
    }
}
