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
    val isLoading: Boolean = false
)

@HiltViewModel
class NotificationListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationListState())
    val uiState: StateFlow<NotificationListState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getNotifications()
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.data.map { dto ->
                        NotificationItem(
                            id = dto.id,
                            title = dto.title,
                            message = dto.message,
                            isRead = dto.isRead,
                            createdAt = dto.createdAt
                        )
                    }
                    _uiState.value = _uiState.value.copy(notifications = items, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                apiService.markAllNotificationsRead()
                load()
            } catch (_: Exception) {}
        }
    }
}
