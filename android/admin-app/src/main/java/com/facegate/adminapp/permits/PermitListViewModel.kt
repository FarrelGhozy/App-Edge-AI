package com.facegate.adminapp.permits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.PermitDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermitListState(
    val permits: List<PermitItem> = emptyList(),
    val isLoading: Boolean = false,
    val filterStatus: String? = null,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null
)

data class PermitItem(
    val id: String,
    val studentName: String,
    val type: String,
    val status: String,
    val startDate: String = "",
    val endDate: String = ""
)

@HiltViewModel
class PermitListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermitListState())
    val uiState: StateFlow<PermitListState> = _uiState.asStateFlow()

    fun loadPermits(page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) _uiState.value = _uiState.value.copy(error = null)
            try {
                val response = apiService.getPermits(
                    page = page,
                    status = _uiState.value.filterStatus
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val items = body.data.map { dto ->
                        PermitItem(
                            id = dto.id,
                            studentName = dto.student?.name ?: dto.studentId,
                            type = dto.type,
                            status = dto.status,
                            startDate = dto.startDate.take(10),
                            endDate = dto.endDate.take(10)
                        )
                    }
                    val newPermits = if (page == 1) items else _uiState.value.permits + items
                    _uiState.value = _uiState.value.copy(
                        permits = newPermits,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasMore = page * body.pageSize < body.total,
                        page = page
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = "Gagal memuat izin"
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
        loadPermits(page = 1)
    }

    fun loadMore() {
        val nextPage = _uiState.value.page + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        loadPermits(page = nextPage)
    }

    fun setFilter(status: String?) {
        _uiState.value = _uiState.value.copy(filterStatus = status, page = 1, isLoading = true)
        loadPermits(page = 1)
    }
}
