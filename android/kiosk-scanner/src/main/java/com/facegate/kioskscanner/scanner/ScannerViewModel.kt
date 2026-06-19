package com.facegate.kioskscanner.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<UIState>(UIState.Idle)
    val state: StateFlow<UIState> = _state.asStateFlow()

    sealed class UIState {
        data object Idle : UIState()
        data object Scanning : UIState()
        data class Success(
            val studentName: String,
            val actionLabel: String,
            val isViolation: Boolean = false,
            val message: String? = null
        ) : UIState()

        data class Error(
            val message: String = "Silakan hubungi admin"
        ) : UIState()
    }

    fun resetState() {
        _state.value = UIState.Idle
    }

    fun showScanning() {
        _state.value = UIState.Scanning
    }

    fun showSuccess(name: String, action: String, isViolation: Boolean = false, message: String? = null) {
        val label = when (action) {
            "keluar" -> "KELUAR ✅"
            "kembali" -> "KEMBALI ✅"
            else -> action
        }
        _state.value = UIState.Success(
            studentName = name,
            actionLabel = label,
            isViolation = isViolation,
            message = message
        )
    }

    fun showError(message: String = "Wajah tidak dikenal") {
        _state.value = UIState.Error(message)
    }
}
