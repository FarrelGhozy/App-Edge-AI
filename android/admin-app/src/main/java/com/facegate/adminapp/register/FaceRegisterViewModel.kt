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
    val isSuccess: Boolean = false,
    val detection: FaceDetectionResult? = null
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

    fun onFrameCaptured(imageProxy: ImageProxy, studentId: String) {
        if (isProcessing) {
            Log.d("FaceRegVM", "skip — isProcessing")
            return
        }

        val currentStep = _state.value.step
        if (currentStep != FaceRegisterStep.DETECTING && currentStep != FaceRegisterStep.LIVENESS) {
            Log.d("FaceRegVM", "skip — step=$currentStep")
            return
        }

        isProcessing = true
        Log.d("FaceRegVM", "mulai process frame step=$currentStep")

        // Run detection synchronously on analyzer thread (background).
        // The ImageProxy will be closed by the caller after this returns.
        val mediaImage = imageProxy.image
        val rotation = imageProxy.imageInfo.rotationDegrees
        Log.d("FaceRegVM", "imgProxy w=${imageProxy.width} h=${imageProxy.height} rot=$rotation rawImg=${mediaImage?.width}x${mediaImage?.height}")
        val detection: FaceDetectionResult?
        if (mediaImage != null) {
            detection = faceDetector.detectImage(mediaImage, rotation)
            Log.d("FaceRegVM", "detectImage: result=${detection != null}, error=${faceDetector.getLastError()}")
        } else {
            detection = null
            Log.d("FaceRegVM", "mediaImage null")
        }

        if (detection == null) {
            isProcessing = false
            _state.value = _state.value.copy(
                step = FaceRegisterStep.DETECTING,
                message = "Tidak ada wajah terdeteksi",
                detection = null
            )
            return
        }

        val bb = detection.boundingBox
        Log.d("FaceRegVM", "face found: headY=${detection.headEulerAngleY} headZ=${detection.headEulerAngleZ} quality=${detection.isGoodQuality} contourL=${detection.leftEyeContour.size} contourR=${detection.rightEyeContour.size} eyeL=${detection.leftEyeOpenProbability} eyeR=${detection.rightEyeOpenProbability} bb=${bb.left},${bb.top},${bb.right},${bb.bottom} imgW=${detection.imageWidth.toInt()} imgH=${detection.imageHeight.toInt()}")

        if (!detection.isGoodQuality) {
            isProcessing = false
            _state.value = _state.value.copy(
                step = FaceRegisterStep.DETECTING,
                message = "Hadapkan wajah lurus ke kamera",
                detection = detection
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
            isProcessing = false
            val blinkCount = livenessDetector.getBlinkCount()
            _state.value = _state.value.copy(
                step = FaceRegisterStep.LIVENESS,
                message = "Kedipkan mata ($blinkCount/1)",
                detection = detection
            )
            return
        }

        // Take bitmap while ImageProxy is still valid
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            isProcessing = false
            _state.value = _state.value.copy(
                step = FaceRegisterStep.ERROR,
                error = "Gagal konversi gambar"
            )
            return
        }

        // Liveness passed — launch coroutine for heavy work
        viewModelScope.launch {
            try {
                processAfterLiveness(detection, bitmap, studentId)
            } catch (e: Exception) {
                Log.e("FaceRegVM", "exception", e)
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.ERROR,
                    error = "Terjadi kesalahan: ${e.message}"
                )
            }
        }
    }

    private suspend fun processAfterLiveness(detection: FaceDetectionResult, bitmap: Bitmap, studentId: String) {
        _state.value = _state.value.copy(
            step = FaceRegisterStep.EMBEDDING,
            message = "Memproses wajah...",
            detection = null
        )

        val faceCrop = cropFace(bitmap, detection.boundingBox)
        Log.d("FaceRegVM", "crop: ${faceCrop.width}x${faceCrop.height} bb=${detection.boundingBox.toShortString()} bitmap=${bitmap.width}x${bitmap.height} rotatedImg=${detection.imageWidth}x${detection.imageHeight}")
        capturedBitmap = faceCrop
        bitmap.recycle()

        Log.d("FaceRegVM", "starting embed... modelReady=${faceEmbedder.isReady()}")
        val embedding = try {
            withContext(Dispatchers.Default) {
                faceEmbedder.embed(faceCrop)
            }
        } catch (e: Exception) {
            Log.e("FaceRegVM", "embed error: ${e.message}", e)
            faceCrop.recycle()
            capturedBitmap = null
            _state.value = _state.value.copy(
                step = FaceRegisterStep.ERROR,
                error = "Gagal memproses embedding: ${e.message}"
            )
            return
        }

        if (faceCrop !== bitmap) faceCrop.recycle()

        _state.value = _state.value.copy(
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
            _state.value = _state.value.copy(
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

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e("FaceRegVM", "toBitmap error", e)
            null
        }
    }
}
