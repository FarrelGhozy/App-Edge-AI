package com.facegate.adminapp.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SyncState(
    val deviceId: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncResult: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val apiService: ApiService,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncState())
    val uiState: StateFlow<SyncState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val deviceId = devicePreferences.getDeviceId()
            _uiState.value = _uiState.value.copy(deviceId = deviceId)
        }
    }

    fun requestSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, isError = false)
            try {
                val deviceId = devicePreferences.getOrCreateDeviceId()
                val response = apiService.requestSync(deviceId)
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        lastSyncResult = "Sinkronisasi berhasil ($timestamp)",
                        isError = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        lastSyncResult = "Sinkronisasi gagal ($timestamp)",
                        isError = true
                    )
                }
            } catch (e: Exception) {
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    lastSyncResult = "Gagal terhubung ke server ($timestamp)",
                    isError = true
                )
            }
        }
    }
}
