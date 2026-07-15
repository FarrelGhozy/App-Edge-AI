package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.UploadFaceRequest
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
import com.facegate.core.face.QualityAnalyzer
import com.facegate.core.face.QualityAnalyzer.QualityReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FaceRegisterStep {
    DETECTING,
    COLLECTING,      // NEW: collecting N good frames
    EMBEDDING,
    UPLOADING,
    SUCCESS,
    ERROR
}

data class FaceRegisterState(
    val step: FaceRegisterStep = FaceRegisterStep.DETECTING,
    val message: String = "Arahkan wajah ke dalam oval",
    val error: String? = null,
    val isUploading: Boolean = false,
    val isSuccess: Boolean = false,
    val detection: FaceDetectionResult? = null,
    // Multi-frame progress
    val framesCollected: Int = 0,
    val framesRequired: Int = 3,
    val currentQualityScore: Float = 0f,
    val qualityMessages: List<String> = emptyList()
)

@HiltViewModel
class FaceRegisterViewModel @Inject constructor(
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder,
    private val livenessDetector: LivenessDetector,
    private val apiService: ApiService
) : ViewModel() {

    companion object {
        private const val TAG = "FaceRegVM"
        private const val REQUIRED_FRAMES = 3  // Number of quality frames to collect
        private const val COLLECT_TIMEOUT_MS = 12_000L  // Max time to collect all frames
        private const val QUALITY_INTERVAL_MS = 150L    // Min gap between frame captures
    }

    private val _state = MutableStateFlow(FaceRegisterState(
        framesRequired = REQUIRED_FRAMES
    ))
    val state: StateFlow<FaceRegisterState> = _state.asStateFlow()

    // Collected frames for multi-frame averaging
    private val collectedBitmaps = mutableListOf<Bitmap>()
    private val collectedRects = mutableListOf<Rect>()
    private var isProcessing = false
    private var collectStartTime = 0L
    private var lastCaptureTime = 0L
    private var studentId: String = ""

    init {
        faceDetector.init()
        faceEmbedder.init()
    }

    fun setStudentId(id: String) {
        studentId = id
    }

    fun onFrameCaptured(imageProxy: ImageProxy, studentId: String?) {
        if (isProcessing) return
        this.studentId = studentId ?: this.studentId

        val currentStep = _state.value.step
        if (currentStep != FaceRegisterStep.DETECTING &&
            currentStep != FaceRegisterStep.COLLECTING
        ) return

        isProcessing = true

        val mediaImage = imageProxy.image
        val rotation = imageProxy.imageInfo.rotationDegrees

        val detection: FaceDetectionResult? = if (mediaImage != null) {
            faceDetector.detectImage(mediaImage, rotation)
        } else null

        if (detection == null) {
            isProcessing = false
            _state.value = _state.value.copy(
                step = FaceRegisterStep.DETECTING,
                message = "Tidak ada wajah terdeteksi",
                detection = null
            )
            return
        }

        // Quick quality gate: posture check
        if (!detection.isGoodQuality) {
            isProcessing = false
            _state.value = _state.value.copy(
                step = FaceRegisterStep.DETECTING,
                message = "Hadapkan wajah lurus ke kamera",
                detection = detection
            )
            return
        }

        // ─── Still in DETECTING → start collecting frames ───
        if (currentStep == FaceRegisterStep.DETECTING) {
            transitionToCollecting()
        }

        // ─── COLLECTING frame ───
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < QUALITY_INTERVAL_MS) {
            isProcessing = false
            return // Throttle captures
        }

        // Check timeout
        if (now - collectStartTime > COLLECT_TIMEOUT_MS) {
            if (collectedBitmaps.isNotEmpty()) {
                // We have at least some frames — proceed with what we have
                Log.d(TAG, "Collect timeout with ${collectedBitmaps.size}/${REQUIRED_FRAMES} frames — proceeding")
                proceedToEmbedding()
            } else {
                reset()
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.DETECTING,
                    message = "Waktu habis, coba lagi"
                )
            }
            isProcessing = false
            return
        }

        // Convert frame to bitmap for quality analysis
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            isProcessing = false
            return
        }

        // Quality analysis on the face region
        val quality = QualityAnalyzer.analyze(
            bitmap = bitmap,
            faceRect = detection.boundingBox,
            yawAngle = detection.headEulerAngleY,
            pitchAngle = detection.headEulerAngleZ
        )

        lastCaptureTime = now

        if (quality.isPass) {
            collectedBitmaps.add(bitmap)
            collectedRects.add(detection.boundingBox)
            _state.value = _state.value.copy(
                step = FaceRegisterStep.COLLECTING,
                framesCollected = collectedBitmaps.size,
                message = "Kualitas baik (${collectedBitmaps.size}/$REQUIRED_FRAMES)",
                detection = detection,
                currentQualityScore = quality.score,
                qualityMessages = emptyList()
            )
            Log.d(TAG, "Frame ${collectedBitmaps.size}/$REQUIRED_FRAMES collected (score=${"%.3f".format(quality.score)})")

            if (collectedBitmaps.size >= REQUIRED_FRAMES) {
                proceedToEmbedding()
            }
        } else {
            // Show quality feedback but don't store
            _state.value = _state.value.copy(
                step = FaceRegisterStep.COLLECTING,
                message = quality.messages.firstOrNull()
                    ?: "Perbaiki posisi wajah (${collectedBitmaps.size}/$REQUIRED_FRAMES)",
                detection = detection,
                currentQualityScore = quality.score,
                qualityMessages = quality.messages
            )
            bitmap.recycle()
            Log.d(TAG, "Frame rejected: ${quality.messages}")
        }

        isProcessing = false
    }

    private fun transitionToCollecting() {
        collectStartTime = System.currentTimeMillis()
        lastCaptureTime = 0L
        collectedBitmaps.clear()
        collectedRects.clear()
        _state.value = _state.value.copy(
            step = FaceRegisterStep.COLLECTING,
            message = "Ambil 3 frame berkualitas... (0/$REQUIRED_FRAMES)",
            detection = null
        )
    }

    private fun proceedToEmbedding() {
        val frames = collectedBitmaps.toList() // snapshot
        isProcessing = false

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.EMBEDDING,
                    message = "Memproses ${frames.size} frame wajah...",
                    detection = null
                )

                // Embed each frame
                val embeddings = withContext(Dispatchers.Default) {
                    frames.map { bitmap ->
                        faceEmbedder.embed(bitmap)
                    }.toTypedArray()
                }

                // Average embeddings for robust template
                val averaged = faceEmbedder.averageEmbeddings(embeddings)

                // Cleanup bitmaps
                frames.forEach { it.recycle() }
                collectedBitmaps.clear()
                collectedRects.clear()

                _state.value = _state.value.copy(
                    step = FaceRegisterStep.UPLOADING,
                    message = "Mengunggah data wajah..."
                )

                // Upload
                val vector = averaged.toList()
                val request = UploadFaceRequest(vector = vector)
                val response = withContext(Dispatchers.IO) {
                    apiService.uploadFace(studentId, request)
                }

                if (response.isSuccessful) {
                    livenessDetector.reset()
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.SUCCESS,
                        message = "Registrasi wajah berhasil!",
                        isSuccess = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.ERROR,
                        error = "Gagal mengunggah: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                frames.forEach { it.recycle() }
                collectedBitmaps.clear()
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.ERROR,
                    error = "Terjadi kesalahan: ${e.message}"
                )
            }
        }
    }

    fun reset() {
        livenessDetector.reset()
        collectedBitmaps.forEach { it.recycle() }
        collectedBitmaps.clear()
        collectedRects.clear()
        isProcessing = false
        collectStartTime = 0L
        lastCaptureTime = 0L
        _state.value = FaceRegisterState(framesRequired = REQUIRED_FRAMES)
    }

    override fun onCleared() {
        super.onCleared()
        collectedBitmaps.forEach { it.recycle() }
        collectedBitmaps.clear()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "toBitmap error", e)
            null
        }
    }
}
