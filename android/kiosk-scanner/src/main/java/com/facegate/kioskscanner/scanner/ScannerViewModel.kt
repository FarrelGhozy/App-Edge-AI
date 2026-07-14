package com.facegate.kioskscanner.scanner

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.engine.ToggleAction
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.FaceMatcher
import com.facegate.core.face.LivenessDetector
import com.facegate.core.sync.SyncManager
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
import kotlin.math.abs

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val matchEngine: MatchEngine,
    private val attendanceLogDao: AttendanceLogDao,
    private val devicePreferences: DevicePreferences,
    private val voiceFeedback: VoiceFeedback,
    private val syncManager: SyncManager
) : ViewModel() {

    companion object {
        private const val CENTER_MARGIN_RATIO = 0.25f
        private const val DELAY_BEFORE_CAPTURE_MS = 1000L
    }

    private val _state = MutableStateFlow<UIState>(UIState.Idle)
    val state: StateFlow<UIState> = _state.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Face detection state for guide overlay
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()

    private val _isFaceCentered = MutableStateFlow(false)
    val isFaceCentered: StateFlow<Boolean> = _isFaceCentered.asStateFlow()

    private val _statusMessage = MutableStateFlow("Arahkan wajah ke kamera")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Debug: expose last detection for overlay
    private val _debugDetection = MutableStateFlow<FaceDetectionResult?>(null)
    val debugDetection: StateFlow<FaceDetectionResult?> = _debugDetection.asStateFlow()

    // Sync status
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Delayed capture — 1s wait after liveness passes
    private var pendingCaptureAt: Long = 0L

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

    fun onFrameCaptured(imageProxy: ImageProxy) {
        if (_state.value is UIState.Success || _state.value is UIState.Error) return
        if (_isProcessing.value) return

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.d("ScannerVM", "mediaImage null")
            pendingCaptureAt = 0L
            _isFaceDetected.value = false
            _isFaceCentered.value = false
            _statusMessage.value = "Arahkan wajah ke kamera"
            _debugDetection.value = null
            return
        }

        val detection = matchEngine.detectFromImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        _debugDetection.value = detection

        if (detection == null) {
            Log.d("ScannerVM", "no face detected")
            pendingCaptureAt = 0L
            _isFaceDetected.value = false
            _isFaceCentered.value = false
            _statusMessage.value = "Arahkan wajah ke kamera"
            return
        }

        _isFaceDetected.value = true

        if (!detection.isGoodQuality) {
            _isFaceCentered.value = false
            pendingCaptureAt = 0L
            _statusMessage.value = "Hadapkan wajah lurus ke kamera"
            _debugDetection.value = detection
            return
        }

        // Center-position check: wajah harus di tengah frame
        val centered = isFaceCentered(detection)
        _isFaceCentered.value = centered
        if (!centered) {
            pendingCaptureAt = 0L
            _statusMessage.value = "Posisikan wajah di tengah kotak"
            _debugDetection.value = detection
            return
        }

        val currentTime = System.currentTimeMillis()

        // Liveness check (blink detection)
        val livenessPassed = matchEngine.checkLiveness(detection, currentTime)
        if (!livenessPassed) {
            val timedOut = matchEngine.isLivenessWindowExpired(currentTime)
            _statusMessage.value = if (timedOut) {
                "Kedipkan mata untuk verifikasi"
            } else {
                "Kedipkan mata"
            }
            _debugDetection.value = detection
            return
        }

        // If this is the first frame after liveness passed, start 1s delay
        if (pendingCaptureAt == 0L) {
            pendingCaptureAt = currentTime + DELAY_BEFORE_CAPTURE_MS
            _statusMessage.value = "Tahan pose..."
            _debugDetection.value = detection
            return
        }

        // Wait for delay to elapse (wajah harus tetap di tengah)
        if (currentTime < pendingCaptureAt) {
            _statusMessage.value = "Tahan pose..."
            _debugDetection.value = detection
            return
        }

        // ─── CAPTURE TIME ───
        pendingCaptureAt = 0L
        _isProcessing.value = true
        _statusMessage.value = "Memproses..."

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            _isProcessing.value = false
            return
        }

        viewModelScope.launch {
            try {
                val result = matchEngine.matchAfterDetection(detection, bitmap)

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
                        _statusMessage.value = ""
                    }
                    is MatchEngineResult.Unknown -> {
                        voiceFeedback.speakError()
                        _state.value = UIState.Error("Wajah tidak dikenal (${String.format("%.0f", result.confidence * 100)}% mirip)")
                    }
                    is MatchEngineResult.LivenessFailed -> {
                        _state.value = UIState.Error("Kedipkan mata untuk verifikasi")
                    }
                    is MatchEngineResult.NoFace -> {}
                    is MatchEngineResult.QualityFailed -> {
                        _state.value = UIState.Error(result.reason)
                    }
                }
            } finally {
                _isProcessing.value = false
                bitmap.recycle()
            }
        }
    }

    private fun isFaceCentered(detection: FaceDetectionResult): Boolean {
        val cx = detection.imageWidth / 2f
        val cy = detection.imageHeight / 2f
        val bb = detection.boundingBox
        val faceCx = bb.exactCenterX()
        val faceCy = bb.exactCenterY()
        val marginX = detection.imageWidth * CENTER_MARGIN_RATIO
        val marginY = detection.imageHeight * CENTER_MARGIN_RATIO
        return abs(faceCx - cx) <= marginX && abs(faceCy - cy) <= marginY
    }

    /** Manual pull data from server */
    fun syncNow() {
        viewModelScope.launch {
            _syncStatus.value = "Menarik data..."
            try {
                val deviceId = devicePreferences.getDeviceId() ?: "unknown"
                val result = syncManager.syncAll(deviceId)
                if (result.success) {
                    _syncStatus.value = "OK: ${result.facesDownloaded} wajah, ${result.rulesDownloaded} aturan"
                } else {
                    _syncStatus.value = "Gagal: ${result.error ?: "unknown"}"
                }
            } catch (e: Exception) {
                _syncStatus.value = "Gagal: ${e.message}"
            }
            // Auto-hide status after 3 seconds
            kotlinx.coroutines.delay(3000)
            _syncStatus.value = null
        }
    }

    fun resetState() {
        _state.value = UIState.Idle
        pendingCaptureAt = 0L
        _isFaceDetected.value = false
        _isFaceCentered.value = false
        _statusMessage.value = "Arahkan wajah ke kamera"
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e("ScannerVM", "toBitmap error", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceFeedback.release()
    }
}
