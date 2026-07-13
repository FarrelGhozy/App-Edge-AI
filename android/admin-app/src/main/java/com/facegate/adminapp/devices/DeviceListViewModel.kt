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
    val batteryLevel: Double? = null
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
            try {
                val response = apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.map { dto ->
                        DeviceItem(
                            deviceId = dto.deviceId,
                            name = dto.name,
                            isActive = dto.isActive,
                            lastPingAt = dto.lastPingAt,
                            batteryLevel = dto.batteryLevel
                        )
                    }
                    _uiState.value = _uiState.value.copy(devices = items, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
