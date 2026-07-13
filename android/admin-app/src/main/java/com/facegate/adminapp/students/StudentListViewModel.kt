package com.facegate.adminapp.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.StudentDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentListState(
    val students: List<StudentDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1
)

@HiltViewModel
class StudentListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentListState())
    val uiState: StateFlow<StudentListState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadStudents(page: Int = 1) {
        viewModelScope.launch {
            try {
                val response = apiService.getStudents(
                    page = page,
                    search = _uiState.value.searchQuery.ifBlank { null }
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val newStudents = if (page == 1) body.data else _uiState.value.students + body.data
                    _uiState.value = _uiState.value.copy(
                        students = newStudents,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasMore = page * body.pageSize < body.total,
                        page = page,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = "Gagal memuat data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = "Gagal terhubung"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, page = 1, error = null)
        loadStudents(page = 1)
    }

    fun loadMore() {
        val nextPage = _uiState.value.page + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        loadStudents(page = nextPage)
    }

    fun onSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(page = 1, isLoading = true, error = null)
            loadStudents(page = 1)
        }
    }
}
