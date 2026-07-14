package com.facegate.adminapp.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.DeviceDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceDetailState(
    val device: DeviceDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDetailState())
    val uiState: StateFlow<DeviceDetailState> = _uiState.asStateFlow()

    fun loadDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = DeviceDetailState(isLoading = true)
            try {
                val response = apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val device = response.body()!!.find { it.deviceId == deviceId }
                    _uiState.value = DeviceDetailState(
                        device = device,
                        isLoading = false,
                        error = if (device == null) "Device tidak ditemukan" else null
                    )
                } else {
                    _uiState.value = DeviceDetailState(error = "Gagal memuat data device")
                }
            } catch (e: Exception) {
                _uiState.value = DeviceDetailState(error = "Gagal terhubung ke server")
            }
        }
    }
}
