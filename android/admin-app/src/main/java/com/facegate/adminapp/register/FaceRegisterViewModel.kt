package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.BatchUploadFacesRequest
import com.facegate.core.data.remote.dto.PoseVectorEntry
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
import kotlin.math.abs
import javax.inject.Inject

// ──────────────────────────────────────────────
// Angle positions to cover during registration
// ──────────────────────────────────────────────
enum class CapturePose(
    val displayName: String,
    val guideText: String,
    val targetYaw: Float,     // target yaw (degrees)
    val targetPitch: Float,   // target pitch (degrees)
    val tolerance: Float      // acceptance range around target
) {
    CENTER("Lurus", "Hadapkan wajah lurus ke kamera", 0f, 0f, 22f),
    LEFT("Kiri", "Miringkan kepala ke kiri", -30f, 0f, 12f),
    RIGHT("Kanan", "Miringkan kepala ke kanan", 30f, 0f, 12f),
    UP("Atas", "Tengadahkan kepala ke atas", 0f, 18f, 12f),
    DOWN("Bawah", "Tundukkan kepala ke bawah", 0f, -18f, 12f)
}

data class CapturedFrameData(
    val bitmap: Bitmap,
    val faceRect: Rect,
    val qualityReport: QualityReport,
    val pose: CapturePose
)

enum class FaceRegisterStep {
    DETECTING,
    POSITIONING,    // guiding user to a pose
    CAPTURING,      // collecting frame for current pose
    CONFIRM,        // showing captured face preview before next pose
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

    // Multi-pose progress
    val currentPose: CapturePose = CapturePose.CENTER,
    val capturedPoses: Set<CapturePose> = emptySet(),
    val totalFramesCollected: Int = 0,
    val framesRequired: Int = 5,

    // Quality feedback for live display
    val currentQualityScore: Float = 0f,
    val qualityMessages: List<String> = emptyList(),
    val currentYaw: Float = 0f,
    val currentPitch: Float = 0f
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
        private const val MAX_FRAMES_PER_POSE = 2       // collect up to 2 per pose
        private const val PER_POSE_TIMEOUT_MS = 15_000L // max time per pose
        private const val QUALITY_INTERVAL_MS = 150L
        private const val TRANSITION_DELAY_MS = 600L    // delay before next pose
    }

    private val _state = MutableStateFlow(FaceRegisterState(
        framesRequired = CapturePose.entries.size // = 5
    ))
    val state: StateFlow<FaceRegisterState> = _state.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // Per-pose frame storage
    private val poseQueues = mutableMapOf<CapturePose, MutableList<CapturedFrameData>>()
    private var isProcessing = false
    private var poseStartTime = 0L
    private var lastCaptureTime = 0L
    private var lastPoseCaptureCount = 0
    private var isTransitionScheduled = false
    private var studentId: String = ""

    // Ordered list of poses to go through
    private val poseOrder = listOf(
        CapturePose.CENTER,
        CapturePose.LEFT,
        CapturePose.RIGHT,
        CapturePose.UP,
        CapturePose.DOWN
    )

    init {
        faceDetector.init()
        faceEmbedder.init()
        // Initialize empty queues for all poses
        for (pose in CapturePose.entries) {
            poseQueues[pose] = mutableListOf()
        }
    }

    fun setStudentId(id: String) {
        studentId = id
    }

    fun onFrameCaptured(imageProxy: ImageProxy, studentIdParam: String?) {
        if (isProcessing) return
        this.studentId = studentIdParam ?: this.studentId
        if (isTransitionScheduled) return

        val currentStep = _state.value.step
        if (currentStep != FaceRegisterStep.DETECTING &&
            currentStep != FaceRegisterStep.POSITIONING &&
            currentStep != FaceRegisterStep.CAPTURING
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
                detection = null,
                currentYaw = 0f,
                currentPitch = 0f
            )
            return
        }

        val yaw = detection.headEulerAngleY
        val pitch = detection.headEulerAngleX

        // ─── Determine which pose the user is closest to ───
        val matchedPose = findClosestPose(yaw, pitch)

        // Convert frame to bitmap for quality analysis & embedding
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            isProcessing = false
            return
        }

        // Quality analysis
        val quality = QualityAnalyzer.analyze(
            bitmap = bitmap,
            faceRect = detection.boundingBox,
            yawAngle = yaw,
            pitchAngle = pitch
        )

        // ─── Update state with live feedback ───
        val currentPose = _state.value.currentPose

        _state.value = _state.value.copy(
            detection = detection,
            currentYaw = yaw,
            currentPitch = pitch,
            currentQualityScore = quality.score,
            qualityMessages = quality.messages
        )

        // ─── Phase: DETECTING → start first pose when face detected ───
        if (currentStep == FaceRegisterStep.DETECTING) {
            if (quality.isPass) {
                startNextPose()
            } else {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.DETECTING,
                    message = quality.messages.firstOrNull()
                        ?: "Hadapkan wajah lurus ke kamera"
                )
            }
            bitmap.recycle()
            isProcessing = false
            return
        }

        // ─── Check timeout for current pose ───
        val now = System.currentTimeMillis()
        if (now - poseStartTime > PER_POSE_TIMEOUT_MS) {
            if (poseQueues[currentPose]!!.isNotEmpty()) {
                // We have frames — proceed even if incomplete set
                Log.d(TAG, "Pose ${currentPose.name} timeout with ${poseQueues[currentPose]!!.size} frames")
                moveToNextPose()
            } else {
                // No frames at all — reset to detecting
                reset()
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.DETECTING,
                    message = "Waktu habis, coba lagi"
                )
            }
            bitmap.recycle()
            isProcessing = false
            return
        }

        // ─── Phase: POSITIONING — wait for user to match pose ───
        if (currentStep == FaceRegisterStep.POSITIONING) {
            val isInPose = matchedPose == currentPose && abs(yaw - currentPose.targetYaw) < currentPose.tolerance &&
                    abs(pitch - currentPose.targetPitch) < currentPose.tolerance

            if (isInPose && quality.isPass && !quality.isBlurry) {
                // Entered the correct pose — switch to CAPTURING
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.CAPTURING,
                    message = "Pertahankan posisi... (${poseQueues[currentPose]!!.size + 1}/$MAX_FRAMES_PER_POSE)"
                )
            } else {
                // Guide user toward correct pose
                val guide = buildPoseGuidance(currentPose, yaw, pitch)
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.POSITIONING,
                    message = guide,
                    currentPose = currentPose
                )
            }
            bitmap.recycle()
            isProcessing = false
            return
        }

        // ─── Phase: CAPTURING — collect frames for current pose ───
        if (currentStep == FaceRegisterStep.CAPTURING) {
            val currentQueue = poseQueues[currentPose]!!
            val isInPose = matchedPose == currentPose && abs(yaw - currentPose.targetYaw) < currentPose.tolerance &&
                    abs(pitch - currentPose.targetPitch) < currentPose.tolerance

            if (!isInPose) {
                // User moved out of pose — warn them
                val guide = buildPoseGuidance(currentPose, yaw, pitch)
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.POSITIONING,
                    message = guide
                )
                bitmap.recycle()
                isProcessing = false
                return
            }

            if (now - lastCaptureTime < QUALITY_INTERVAL_MS) {
                bitmap.recycle()
                isProcessing = false
                return // throttle
            }

            if (quality.isPass && currentQueue.size < MAX_FRAMES_PER_POSE) {
                currentQueue.add(CapturedFrameData(
                    bitmap = bitmap,
                    faceRect = detection.boundingBox,
                    qualityReport = quality,
                    pose = currentPose
                ))
                lastCaptureTime = System.currentTimeMillis()
                val capturedPoses = _state.value.capturedPoses + currentPose
                val totalFrames = poseQueues.values.sumOf { it.size }

                _state.value = _state.value.copy(
                    step = FaceRegisterStep.CAPTURING,
                    capturedPoses = capturedPoses,
                    totalFramesCollected = totalFrames,
                    message = "Pertahankan posisi... (${currentQueue.size}/$MAX_FRAMES_PER_POSE)"
                )

                Log.d(TAG, "Frame captured for ${currentPose.name}: ${currentQueue.size}/$MAX_FRAMES_PER_POSE (score=${"%.3f".format(quality.score)})")

                if (currentQueue.size >= MAX_FRAMES_PER_POSE) {
                    // This pose is done — show preview before next
                    val best = currentQueue.maxByOrNull { it.qualityReport.score }
                    val previewBmp = best?.let {
                        val faceRect = it.faceRect
                        try {
                            val crop = Bitmap.createBitmap(
                                it.bitmap,
                                faceRect.left.coerceAtLeast(0),
                                faceRect.top.coerceAtLeast(0),
                                faceRect.width().coerceAtMost(it.bitmap.width - faceRect.left.coerceAtLeast(0)),
                                faceRect.height().coerceAtMost(it.bitmap.height - faceRect.top.coerceAtLeast(0))
                            )
                            if (rotation != 0) {
                                val mat = Matrix().apply { postRotate(rotation.toFloat()) }
                                Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, mat, true)
                            } else crop
                        } catch (e: Exception) { null }
                    }
                    _previewBitmap.value = previewBmp

                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.CONFIRM,
                        message = "Pose ${currentPose.displayName} selesai!",
                        currentQualityScore = 0f
                    )
                }
            } else {
                // Frame didn't pass quality — show feedback but stay in CAPTURING
                _state.value = _state.value.copy(
                    message = quality.messages.firstOrNull()
                        ?: "Tunggu... (${currentQueue.size}/$MAX_FRAMES_PER_POSE)"
                )
                bitmap.recycle()
            }

            isProcessing = false
            return
        }

        bitmap.recycle()
        isProcessing = false
    }

    private fun findClosestPose(yaw: Float, pitch: Float): CapturePose {
        return CapturePose.entries.minByOrNull { pose ->
            val dy = (yaw - pose.targetYaw) / pose.tolerance
            val dp = (pitch - pose.targetPitch) / pose.tolerance
            dy * dy + dp * dp
        } ?: CapturePose.CENTER
    }

    private fun buildPoseGuidance(target: CapturePose, yaw: Float, pitch: Float): String {
        val dyaw = target.targetYaw - yaw
        val dpitch = target.targetPitch - pitch
        return when {
            abs(dyaw) > 15 && dyaw > 0 -> "Miringkan kepala ke kiri"
            abs(dyaw) > 15 && dyaw < 0 -> "Miringkan kepala ke kanan"
            dpitch > 10 -> "Tundukkan kepala"
            dpitch < -10 -> "Tengadahkan kepala"
            abs(dyaw) > 8 -> if (dyaw > 0) "Sedikit ke kiri" else "Sedikit ke kanan"
            abs(dpitch) > 6 -> if (dpitch > 0) "Sedikit tunduk" else "Sedikit tengadah"
            else -> target.guideText
        }
    }

    private fun startNextPose() {
        poseStartTime = System.currentTimeMillis()
        val firstPose = poseOrder.first()
        _state.value = _state.value.copy(
            step = FaceRegisterStep.POSITIONING,
            currentPose = firstPose,
            message = firstPose.guideText
        )
    }

    private fun moveToNextPose() {
        val currentPose = _state.value.currentPose
        val currentIdx = poseOrder.indexOf(currentPose)
        val nextIdx = currentIdx + 1

        if (nextIdx >= poseOrder.size) {
            // All poses done — process
            proceedToEmbedding()
            return
        }

        val nextPose = poseOrder[nextIdx]
        isTransitionScheduled = true
        _state.value = _state.value.copy(
            step = FaceRegisterStep.POSITIONING,
            currentPose = nextPose,
            message = "Bagus! Sekarang: ${nextPose.guideText}",
            currentQualityScore = 0f
        )

        // Brief delay so user can see success before next instruction
        viewModelScope.launch {
            delay(TRANSITION_DELAY_MS)
            poseStartTime = System.currentTimeMillis()
            isTransitionScheduled = false
        }
    }

    private fun proceedToEmbedding() {
        isProcessing = false

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.EMBEDDING,
                    message = "Memproses ${_state.value.framesRequired} frame wajah...",
                    detection = null
                )

                // ─── Select best 1 frame per pose (total = 5) ───
                val selectedFrames = mutableListOf<CapturedFrameData>()
                for (pose in poseOrder) {
                    val queue = poseQueues[pose]!!
                    if (queue.isEmpty()) continue
                    // Pick the frame with highest quality score for this pose
                    val best = queue.maxByOrNull { it.qualityReport.score }!!
                    selectedFrames.add(best)
                }

                if (selectedFrames.isEmpty()) {
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.ERROR,
                        error = "Tidak ada frame yang valid"
                    )
                    return@launch
                }

                // Take top N up to framesRequired
                val sortedFrames = selectedFrames.sortedByDescending { it.qualityReport.score }
                val finalFrames = sortedFrames.take(_state.value.framesRequired)

                // ─── Embed each selected frame (crop face first, consistent with kiosk) ───
                val embeddings = withContext(Dispatchers.Default) {
                    finalFrames.map { data ->
                        val faceCrop = cropFace(data.bitmap, data.faceRect)
                        val emb = faceEmbedder.embed(faceCrop)
                        if (faceCrop !== data.bitmap) faceCrop.recycle()
                        emb
                    }.toTypedArray()
                }

                // Cleanup all bitmaps
                _previewBitmap.value?.recycle()
                _previewBitmap.value = null
                poseQueues.values.forEach { queue ->
                    queue.forEach { it.bitmap.recycle() }
                    queue.clear()
                }

                _state.value = _state.value.copy(
                    step = FaceRegisterStep.UPLOADING,
                    message = "Mengunggah data wajah..."
                )

                // ─── Build per-pose vector list ───
                val poseVectors = finalFrames.mapIndexed { index, frameData ->
                    PoseVectorEntry(
                        pose = frameData.pose.name,
                        vector = embeddings[index].toList()
                    )
                }

                // ─── Upload all 5 pose vectors in one batch ───
                val batchRequest = BatchUploadFacesRequest(vectors = poseVectors)
                val response = withContext(Dispatchers.IO) {
                    apiService.uploadFaces(studentId, batchRequest)
                }

                if (response.isSuccessful) {
                    livenessDetector.reset()
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.SUCCESS,
                        message = "Registrasi wajah berhasil!",
                        isSuccess = true
                    )
                } else {
                    val errBody = try {
                        response.errorBody()?.string()
                    } catch (_: Exception) { null }
                    Log.e(TAG, "Upload gagal ${response.code()}: $errBody")
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.ERROR,
                        error = errBody ?: "Gagal mengunggah: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                poseQueues.values.forEach { queue ->
                    queue.forEach { it.bitmap.recycle() }
                    queue.clear()
                }
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.ERROR,
                    error = "Terjadi kesalahan: ${e.message}"
                )
            }
        }
    }

    fun confirmPose() {
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        moveToNextPose()
    }

    fun retryPose() {
        val currentPose = _state.value.currentPose
        poseQueues[currentPose]?.clear()
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        poseStartTime = System.currentTimeMillis()
        _state.value = _state.value.copy(
            step = FaceRegisterStep.POSITIONING,
            message = "Ulangi: ${currentPose.guideText}",
            capturedPoses = _state.value.capturedPoses - currentPose,
            currentQualityScore = 0f
        )
    }

    fun skipPose() {
        val currentStep = _state.value.step
        val currentPose = _state.value.currentPose
        Log.d(TAG, "User skipped pose ${currentPose.name}")

        // If still in DETECTING, start the pose flow first
        if (currentStep == FaceRegisterStep.DETECTING) {
            startNextPose()
            // Skip again after starting (moves to next pose)
            val newPose = _state.value.currentPose
            val newIdx = poseOrder.indexOf(newPose)
            val nextIdx = newIdx + 1
            if (nextIdx < poseOrder.size) {
                _state.value = _state.value.copy(
                    currentPose = poseOrder[nextIdx],
                    message = poseOrder[nextIdx].guideText
                )
            }
            return
        }
        moveToNextPose()
    }

    fun reset() {
        livenessDetector.reset()
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        poseQueues.values.forEach { queue ->
            queue.forEach { it.bitmap.recycle() }
            queue.clear()
        }
        isProcessing = false
        isTransitionScheduled = false
        poseStartTime = 0L
        lastCaptureTime = 0L
        _state.value = FaceRegisterState(framesRequired = CapturePose.entries.size)
    }

    override fun onCleared() {
        super.onCleared()
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        poseQueues.values.forEach { queue ->
            queue.forEach { it.bitmap.recycle() }
            queue.clear()
        }
    }

    /** Crop face region from bitmap using bounding box, with margin. */
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
            Log.e(TAG, "toBitmap error", e)
            null
        }
    }
}
