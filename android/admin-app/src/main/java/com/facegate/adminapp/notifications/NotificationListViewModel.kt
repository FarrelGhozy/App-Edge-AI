package com.facegate.adminapp.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String = ""
)

data class NotificationListState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null
)

@HiltViewModel
class NotificationListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationListState())
    val uiState: StateFlow<NotificationListState> = _uiState.asStateFlow()

    fun load(page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) _uiState.value = _uiState.value.copy(error = null)
            try {
                val response = apiService.getNotifications(page = page)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val items = body.data.map { dto ->
                        NotificationItem(
                            id = dto.id,
                            title = dto.title,
                            message = dto.message,
                            isRead = dto.isRead,
                            createdAt = dto.createdAt
                        )
                    }
                    val newNotifications = if (page == 1) items else _uiState.value.notifications + items
                    _uiState.value = _uiState.value.copy(
                        notifications = newNotifications,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasMore = page * body.pageSize < body.total,
                        page = page
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = "Gagal memuat notifikasi"
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

    fun markAllRead() {
        viewModelScope.launch {
            try {
                apiService.markAllNotificationsRead()
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Gagal menandai semua sebagai dibaca")
            }
        }
    }
}
