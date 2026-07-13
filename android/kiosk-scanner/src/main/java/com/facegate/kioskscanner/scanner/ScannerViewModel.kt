package com.facegate.kioskscanner.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.engine.ToggleAction
import com.facegate.kioskscanner.matching.MatchEngine
import com.facegate.kioskscanner.matching.MatchEngineResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val matchEngine: MatchEngine,
    private val attendanceLogDao: AttendanceLogDao,
    private val devicePreferences: DevicePreferences,
    private val voiceFeedback: VoiceFeedback
) : ViewModel() {

    private val _state = MutableStateFlow<UIState>(UIState.Idle)
    val state: StateFlow<UIState> = _state.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    sealed class UIState {
        data object Idle : UIState()
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

    fun onFrameCaptured(bitmap: Bitmap) {
        if (_state.value is UIState.Success || _state.value is UIState.Error) return
        if (_isProcessing.value) return

        _isProcessing.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                matchEngine.match(bitmap)
            }

            when (result) {
                is MatchEngineResult.Matched -> {
                    val action = when (result.action) {
                        ToggleAction.KELUAR -> "keluar"
                        ToggleAction.KEMBALI -> "kembali"
                    }
                    val deviceId = devicePreferences.getDeviceId()
                    val log = AttendanceLogEntity(
                        studentId = result.studentId,
                        studentName = result.studentName,
                        action = action,
                        timestamp = System.currentTimeMillis(),
                        confidenceScore = 1.0f,
                        isViolation = result.isViolation,
                        violationType = if (result.isViolation) result.violationMessage else null,
                        deviceId = deviceId
                    )
                    attendanceLogDao.insert(log)
                    voiceFeedback.speakSuccess(result.studentName, action)
                    if (result.isViolation) {
                        result.violationMessage?.let { voiceFeedback.speakWarning(it) }
                    }
                    val label = when (action) {
                        "keluar" -> "KELUAR ✅"
                        "kembali" -> "KEMBALI ✅"
                        else -> action
                    }
                    _state.value = UIState.Success(
                        studentName = result.studentName,
                        actionLabel = label,
                        isViolation = result.isViolation,
                        message = result.violationMessage
                    )
                }
                is MatchEngineResult.Unknown -> {
                    voiceFeedback.speakError()
                    _state.value = UIState.Error("Wajah tidak dikenal (${String.format("%.0f", result.confidence * 100)}% mirip)")
                }
                is MatchEngineResult.LivenessFailed -> {
                    _state.value = UIState.Error("Kedipkan mata untuk verifikasi")
                }
                is MatchEngineResult.NoFace -> {
                    // silent — stay Idle, no state change
                }
                is MatchEngineResult.QualityFailed -> {
                    _state.value = UIState.Error(result.reason)
                }
            }

            _isProcessing.value = false
        }
    }

    fun resetState() {
        _state.value = UIState.Idle
    }
}
