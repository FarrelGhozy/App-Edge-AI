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
                val response = apiService.getDevice(deviceId)
                if (response.isSuccessful && response.body()?.data != null) {
                    _uiState.value = DeviceDetailState(
                        device = response.body()!!.data,
                        isLoading = false
                    )
                } else if (response.code() == 404) {
                    _uiState.value = DeviceDetailState(error = "Device tidak ditemukan")
                } else {
                    _uiState.value = DeviceDetailState(error = "Gagal memuat data device")
                }
            } catch (e: Exception) {
                _uiState.value = DeviceDetailState(error = "Gagal terhubung ke server")
            }
        }
    }
}
