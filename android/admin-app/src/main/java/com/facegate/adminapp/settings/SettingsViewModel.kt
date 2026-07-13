package com.facegate.adminapp.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val settings: Map<String, String> = emptyMap(),
    val editingValues: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getSettings()
                if (response.isSuccessful && response.body() != null) {
                    val settings = response.body()!!
                    _uiState.value = SettingsState(settings = settings, editingValues = settings)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat pengaturan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal terhubung ke server")
            }
        }
    }

    fun toggleEditMode() {
        val current = _uiState.value
        if (current.isEditMode) {
            _uiState.value = current.copy(isEditMode = false, editingValues = current.settings)
        } else {
            _uiState.value = current.copy(isEditMode = true)
        }
    }

    fun updateValue(key: String, value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            editingValues = current.editingValues + (key to value)
        )
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val response = apiService.updateSettings(_uiState.value.editingValues)
                if (response.isSuccessful) {
                    val settings = _uiState.value.editingValues
                    _uiState.value = _uiState.value.copy(
                        settings = settings,
                        isSaving = false,
                        isEditMode = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = "Gagal menyimpan pengaturan")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Gagal terhubung ke server")
            }
        }
    }
}
