package com.facegate.adminapp.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceItem(
    val deviceId: String,
    val name: String,
    val isActive: Boolean,
    val lastPingAt: String? = null,
    val batteryLevel: Float? = null
)

data class DeviceListState(
    val devices: List<DeviceItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceListState())
    val uiState: StateFlow<DeviceListState> = _uiState.asStateFlow()

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = _uiState.value.copy(
                devices = listOf(
                    DeviceItem("DEV-001", "Kiosk Lobby", true, "2026-06-19 23:00", 0.85f),
                    DeviceItem("DEV-002", "Kiosk Lab", false, "2026-06-18 10:00", 0.12f)
                ),
                isLoading = false
            )
        }
    }
}
