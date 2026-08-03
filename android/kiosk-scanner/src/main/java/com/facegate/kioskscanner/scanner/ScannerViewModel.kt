package com.facegate.kioskscanner.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.engine.ToggleAction
import com.facegate.core.face.*
import com.facegate.core.sync.SyncManager
import com.facegate.kioskscanner.matching.MatchEngineResult
import com.facegate.kioskscanner.matching.VideoMatchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/**
 * Data class for the face bounding box overlay UI state.
 * Selalu dikirim tiap frame dari live analyzer ke UI.
 */
data class FaceOverlayState(
    val faceRect: Rect? = null,           // Bounding box in original image coordinates
    val canvasRect: Rect? = null,         // Bounding box in Canvas coordinates (after transform)
    val qualityColor: Color = Color.Transparent,
    val label: String = "",
    val progress: Float = 0f,             // 0.0..1.0 selama frame collection
    val isCollecting: Boolean = false,
    val collectedCount: Int = 0,
    val userName: String? = null,          // Muncul kalau match sukses
    val actionLabel: String? = null        // "KELUAR ✅" / "KEMBALI ✅"
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val videoMatchEngine: VideoMatchEngine,
    private val attendanceLogDao: AttendanceLogDao,
    private val devicePreferences: DevicePreferences,
    private val voiceFeedback: VoiceFeedback,
    private val syncManager: SyncManager
) : ViewModel() {

    companion object {
        private const val TAG = "ScannerVM"
        private const val CENTER_MARGIN_RATIO = 0.25f
        private const val FRAME_INTERVAL_MS = 100L // Capture 1 frame setiap 100ms = 10 FPS
        private const val STEADY_DURATION_MS = 1500L // Wajib tahan pose stabil selama ini sebelum collect
    }

    private val _state = MutableStateFlow<UIState>(UIState.Idle)
    val state: StateFlow<UIState> = _state.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Face overlay state — emitted tiap frame
    private val _faceOverlay = MutableStateFlow(FaceOverlayState())
    val faceOverlay: StateFlow<FaceOverlayState> = _faceOverlay.asStateFlow()

    // Face detection state for guide overlay
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()

    private val _isFaceCentered = MutableStateFlow(false)
    val isFaceCentered: StateFlow<Boolean> = _isFaceCentered.asStateFlow()

    private val _statusMessage = MutableStateFlow("Arahkan wajah ke kamera")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Debug: expose last detection for overlay (legacy)
    private val _debugDetection = MutableStateFlow<FaceDetectionResult?>(null)
    val debugDetection: StateFlow<FaceDetectionResult?> = _debugDetection.asStateFlow()

    // Sync status
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Face-steady timer: replace EAR blink (gak bisa dengan RetinaFace 5 landmark)
    private var faceSteadyStartTime: Long = 0L
    private var lastFrameCaptureTime: Long = 0L

    // Image dimensions for transform
    private var lastImageWidth: Int = 0
    private var lastImageHeight: Int = 0

    private val _imageSize = MutableStateFlow(0 to 0)
    val imageSize: StateFlow<Pair<Int, Int>> = _imageSize.asStateFlow()

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
            resetDetectionState()
            return
        }

        val currentTime = System.currentTimeMillis()
        val bitmap = imageProxyToBitmap(imageProxy) ?: return
        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height
        _imageSize.value = lastImageWidth to lastImageHeight

        // ─── RetinaFace detection ───
        val startDetect = System.nanoTime()
        val faces = videoMatchEngine.detectFromBitmap(bitmap)
        val detectTimeMs = (System.nanoTime() - startDetect) / 1_000_000L
        Log.d(TAG, "Frame ${lastImageWidth}x$lastImageHeight: ${faces.size} faces detected in ${detectTimeMs}ms")
        val face = faces.maxByOrNull { it.confidence }

        if (face == null) {
            resetDetectionState()
            // If we were collecting frames, let the collection continue
            if (!videoMatchEngine.isCollecting()) {
                bitmap.recycle()
            }
            return
        }

        Log.d(TAG, "Best face: conf=${"%.3f".format(face.confidence)} rect=${face.boundingBox}")

        _isFaceDetected.value = true
        _debugDetection.value = null // Legacy, digantikan overlay

        // ─── Quality & centered check ───
        val centered = videoMatchEngine.isFaceCentered(face, bitmap.width.toFloat(), bitmap.height.toFloat())
        _isFaceCentered.value = centered

        val yawEstimated = estimateYaw(face) // Approx from landmarks
        val qualityOK = abs(yawEstimated) < 25f && centered

        // ─── Update overlay UI tiap frame ───
        updateOverlay(face, bitmap.width, bitmap.height, qualityOK, centered, currentTime)

        if (!qualityOK) {
            faceSteadyStartTime = 0L
            _statusMessage.value = if (!centered) "Posisikan wajah di tengah"
                                   else "Hadapkan wajah lurus ke kamera"
            if (!videoMatchEngine.isCollecting()) bitmap.recycle()
            return
        }

        // ─── Face-steady liveness ───
        // RetinaFace cuma 5 landmark → gak bisa EAR blink.
        // Solusi: wajah harus stabil di tengah + yaw <25° selama STEADY_DURATION_MS.
        // Anti-spoof deep learning tetap jalan di pipeline setelah collect.
        if (faceSteadyStartTime == 0L) {
            faceSteadyStartTime = currentTime
        }
        val steadyDuration = currentTime - faceSteadyStartTime
        if (steadyDuration < STEADY_DURATION_MS) {
            _statusMessage.value = "Tahan pose..."
            if (!videoMatchEngine.isCollecting()) bitmap.recycle()
            return
        }

        // ─── Start frame collection ───
        if (!videoMatchEngine.isCollecting()) {
            videoMatchEngine.startFrameCollection()
            _statusMessage.value = "Mengambil gambar..."
            lastFrameCaptureTime = currentTime
        }

        // ─── Collect frames ───
        if (videoMatchEngine.isCollecting()) {
            // Throttle: ambil 1 frame setiap FRAME_INTERVAL_MS
            if (currentTime - lastFrameCaptureTime >= FRAME_INTERVAL_MS) {
                val qualityScore = computeQualityScore(face)
                videoMatchEngine.addFrame(bitmap, face, qualityScore)
                lastFrameCaptureTime = currentTime

                _statusMessage.value = "Mengambil gambar... ${videoMatchEngine.getCollectedCount()}/15"
            } else {
                // Throttle skip — bitmap not stored, must recycle
                bitmap.recycle()
            }

            // Update overlay progress
            _faceOverlay.value = _faceOverlay.value.copy(
                progress = videoMatchEngine.getCollectionProgress(),
                isCollecting = true,
                collectedCount = videoMatchEngine.getCollectedCount()
            )

            // Check if collection complete
            if (videoMatchEngine.isCollectionComplete()) {
                _statusMessage.value = "Memproses..."
                _isProcessing.value = true
                processVideoFrames()
                // Don't recycle bitmap here — it's stored in buffer
                return
            }

            // If collection ongoing, don't recycle bitmap (it's stored)
            return
        }

        bitmap.recycle()
    }

    private fun resetDetectionState() {
        faceSteadyStartTime = 0L
        _isFaceDetected.value = false
        _isFaceCentered.value = false
        _statusMessage.value = "Arahkan wajah ke kamera"
        _faceOverlay.value = FaceOverlayState()
    }

    /**
     * Process collected video frames asynchronously.
     */
    private fun processVideoFrames() {
        viewModelScope.launch {
            try {
                val result = videoMatchEngine.processVideoCollection()

                withContext(Dispatchers.Main) {
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
                            launch { syncManager.syncLogsOnly() }
                            voiceFeedback.speakSuccess(result.studentName, action)
                            if (result.isViolation) {
                                result.violationMessage?.let { voiceFeedback.speakWarning(it) }
                            }
                            val label = when (action) {
                                "keluar" -> "KELUAR ✅"
                                "kembali" -> "KEMBALI ✅"
                                else -> action
                            }

                            // Update overlay with success info
                            _faceOverlay.value = _faceOverlay.value.copy(
                                userName = result.studentName,
                                actionLabel = label,
                                qualityColor = Color(0xFF4CAF50)
                            )

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
                            _state.value = UIState.Error("Wajah tidak dikenal")
                            _faceOverlay.value = _faceOverlay.value.copy(
                                qualityColor = Color(0xFFE53935)
                            )
                        }
                        is MatchEngineResult.LivenessFailed -> {
                            _state.value = UIState.Error("Kedipkan mata untuk verifikasi")
                        }
                        is MatchEngineResult.NoFace -> {
                            // Will retry on next frame
                        }
                        is MatchEngineResult.QualityFailed -> {
                            _state.value = UIState.Error(result.reason)
                        }
                    }
                    _isProcessing.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video processing error", e)
                _state.value = UIState.Error("Proses gagal, coba lagi")
                _isProcessing.value = false
            }
        }
    }

    private fun updateOverlay(face: FaceBox, imgW: Int, imgH: Int, qualityOK: Boolean, centered: Boolean, time: Long) {
        val color = when {
            !centered -> Color(0xFFFFC107)      // Kuning — belum centered
            !qualityOK -> Color(0xFFFFC107)     // Kuning — miring
            videoMatchEngine.isCollecting() -> Color(0xFF2196F3) // Biru — collecting
            else -> Color(0xFF4CAF50)           // Hijau — siap
        }

        val label = when {
            !centered -> "Posisikan di tengah"
            !qualityOK -> "Hadap lurus"
            videoMatchEngine.isCollecting() -> "Mengambil gambar..."
            else -> "Tahan pose..."
        }

        _faceOverlay.value = FaceOverlayState(
            faceRect = face.boundingBox,
            canvasRect = null, // Dihitung di UI layer
            qualityColor = color,
            label = label,
            progress = if (videoMatchEngine.isCollecting()) videoMatchEngine.getCollectionProgress() else 0f,
            isCollecting = videoMatchEngine.isCollecting(),
            collectedCount = videoMatchEngine.getCollectedCount()
        )
    }

    /**
     * Estimate yaw angle from RetinaFace landmarks (5 point).
     * If left eye and right eye x-positions are asymmetric → face is turning.
     */
    private fun estimateYaw(face: FaceBox): Float {
        val lm = face.landmarks
        if (lm.size < 4) return 0f
        val leftEye = lm[0]
        val rightEye = lm[1]
        val nose = lm[2]
        // Simple heuristic: nose deviation from center between eyes
        val eyeCenterX = (leftEye.first + rightEye.first) / 2f
        val noseOffset = (nose.first - eyeCenterX) / (rightEye.first - leftEye.first).coerceAtLeast(1f)
        return noseOffset * 45f // Approx degrees
    }

    /**
     * Compute quality score from face box characteristics.
     */
    private fun computeQualityScore(face: FaceBox): Float {
        val rect = face.boundingBox
        val faceArea = rect.width().toFloat() * rect.height().toFloat()
        val imageArea = (lastImageWidth * lastImageHeight).toFloat()
        val sizeRatio = minOf(faceArea / (imageArea * 0.3f), 1.0f)
        return 0.6f + sizeRatio * 0.4f // Base 0.6 + bonus for big face
    }

    private fun isFaceCenteredLegacy(face: FaceBox): Boolean {
        val cx = lastImageWidth / 2f
        val cy = lastImageHeight / 2f
        val bb = face.boundingBox
        val faceCx = bb.exactCenterX()
        val faceCy = bb.exactCenterY()
        val marginX = lastImageWidth * CENTER_MARGIN_RATIO
        val marginY = lastImageHeight * CENTER_MARGIN_RATIO
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
            kotlinx.coroutines.delay(3000)
            _syncStatus.value = null
        }
    }

    fun resetState() {
        _state.value = UIState.Idle
        _isProcessing.value = false
        faceSteadyStartTime = 0L
        lastFrameCaptureTime = 0L
        _isFaceDetected.value = false
        _isFaceCentered.value = false
        _statusMessage.value = "Arahkan wajah ke kamera"
        _faceOverlay.value = FaceOverlayState()
        videoMatchEngine.resetLiveness()
        videoMatchEngine.resetCollection()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "toBitmap error", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceFeedback.release()
    }
}
