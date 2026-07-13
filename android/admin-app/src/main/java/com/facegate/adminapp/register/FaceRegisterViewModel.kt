package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.UploadFaceRequest
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FaceRegisterStep {
    DETECTING,
    LIVENESS,
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
    val isSuccess: Boolean = false
)

@HiltViewModel
class FaceRegisterViewModel @Inject constructor(
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder,
    private val livenessDetector: LivenessDetector,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(FaceRegisterState())
    val state: StateFlow<FaceRegisterState> = _state.asStateFlow()

    private var capturedBitmap: Bitmap? = null
    private var isProcessing = false

    init {
        faceDetector.init()
        faceEmbedder.init()
    }

    fun onFrameCaptured(bitmap: Bitmap, studentId: String) {
        if (isProcessing) return

        val currentStep = _state.value.step
        if (currentStep != FaceRegisterStep.DETECTING && currentStep != FaceRegisterStep.LIVENESS) return

        isProcessing = true

        viewModelScope.launch {
            try {
                processFrame(bitmap, studentId)
            } catch (e: Exception) {
                _state.value = FaceRegisterState(
                    step = FaceRegisterStep.ERROR,
                    error = "Terjadi kesalahan: ${e.message}"
                )
            } finally {
                isProcessing = false
            }
        }
    }

    private suspend fun processFrame(bitmap: Bitmap, studentId: String) {
        val detection = withContext(Dispatchers.Default) {
            faceDetector.detectSync(bitmap)
        }

        if (detection == null) {
            _state.value = FaceRegisterState(
                step = FaceRegisterStep.DETECTING,
                message = "Tidak ada wajah terdeteksi"
            )
            return
        }

        if (!detection.isGoodQuality) {
            _state.value = FaceRegisterState(
                step = FaceRegisterStep.DETECTING,
                message = "Hadapkan wajah lurus ke kamera"
            )
            return
        }

        val currentTime = System.currentTimeMillis()
        val livenessPassed = livenessDetector.checkLiveness(
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = currentTime,
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )

        if (!livenessPassed) {
            val blinkCount = livenessDetector.getBlinkCount()
            _state.value = FaceRegisterState(
                step = FaceRegisterStep.LIVENESS,
                message = "Kedipkan mata ($blinkCount/1)"
            )
            return
        }

        _state.value = FaceRegisterState(
            step = FaceRegisterStep.EMBEDDING,
            message = "Memproses wajah..."
        )

        val faceCrop = cropFace(bitmap, detection.boundingBox)
        capturedBitmap = faceCrop

        val embedding = try {
            withContext(Dispatchers.Default) {
                faceEmbedder.embed(faceCrop)
            }
        } catch (e: Exception) {
            faceCrop.recycle()
            capturedBitmap = null
            _state.value = FaceRegisterState(
                step = FaceRegisterStep.ERROR,
                error = "Gagal memproses embedding"
            )
            return
        }

        if (faceCrop !== bitmap) faceCrop.recycle()

        _state.value = FaceRegisterState(
            step = FaceRegisterStep.UPLOADING,
            message = "Mengunggah data wajah..."
        )

        try {
            val vector = embedding.toList()
            val request = UploadFaceRequest(vector = vector)
            val response = withContext(Dispatchers.IO) {
                apiService.uploadFace(studentId, request)
            }

            if (response.isSuccessful) {
                livenessDetector.reset()
                _state.value = FaceRegisterState(
                    step = FaceRegisterStep.SUCCESS,
                    message = "Registrasi wajah berhasil!",
                    isSuccess = true
                )
            } else {
                _state.value = FaceRegisterState(
                    step = FaceRegisterStep.ERROR,
                    error = "Gagal mengunggah: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            _state.value = FaceRegisterState(
                step = FaceRegisterStep.ERROR,
                error = "Gagal terhubung ke server"
            )
        }
    }

    fun reset() {
        livenessDetector.reset()
        capturedBitmap?.recycle()
        capturedBitmap = null
        isProcessing = false
        _state.value = FaceRegisterState()
    }

    override fun onCleared() {
        super.onCleared()
        capturedBitmap?.recycle()
        capturedBitmap = null
    }

    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val margin = (boundingBox.width() * 0.3f).toInt()
        val x = (boundingBox.left - margin).coerceAtLeast(0)
        val y = (boundingBox.top - margin).coerceAtLeast(0)
        val w = (boundingBox.width() + margin * 2).coerceAtMost(bitmap.width - x)
        val h = (boundingBox.height() + margin * 2).coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }
}
