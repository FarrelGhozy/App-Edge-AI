package com.facegate.adminapp.violations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViolationItem(
    val id: String,
    val studentName: String,
    val type: String,
    val isResolved: Boolean,
    val timestamp: String = ""
)

data class ViolationListState(
    val violations: List<ViolationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
    val searchQuery: String = "",
    val fromDate: String? = null,
    val toDate: String? = null,
    val deleteMessage: String? = null,
    val deleteError: String? = null
)

@HiltViewModel
class ViolationListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViolationListState())
    val uiState: StateFlow<ViolationListState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun load(page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) _uiState.value = _uiState.value.copy(error = null)
            val s = _uiState.value
            try {
                val response = apiService.getViolations(
                    page = page,
                    search = s.searchQuery.ifBlank { null },
                    from = s.fromDate,
                    to = s.toDate
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val items = body.data.map { dto ->
                        ViolationItem(
                            id = dto.id,
                            studentName = dto.studentName,
                            type = dto.type,
                            isResolved = dto.isResolved,
                            timestamp = dto.timestamp
                        )
                    }
                    val newViolations = if (page == 1) items else _uiState.value.violations + items
                    _uiState.value = _uiState.value.copy(
                        violations = newViolations,
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
        load(page = 1)
    }

    fun loadMore() {
        val nextPage = _uiState.value.page + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        load(page = nextPage)
    }

    fun onSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(page = 1, isLoading = true, error = null)
            load(page = 1)
        }
    }

    fun setDateRange(from: String?, to: String?) {
        _uiState.value = _uiState.value.copy(
            fromDate = from,
            toDate = to,
            page = 1,
            isLoading = true,
            error = null
        )
        load(page = 1)
    }

    fun clearDateRange() {
        setDateRange(null, null)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(deleteMessage = null, deleteError = null)
    }

    fun deleteViolation(id: String) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteViolation(id)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        violations = _uiState.value.violations.filterNot { it.id == id },
                        deleteMessage = "Pelanggaran dihapus"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(deleteError = "Gagal menghapus pelanggaran")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(deleteError = "Gagal terhubung ke server")
            }
        }
    }
}
