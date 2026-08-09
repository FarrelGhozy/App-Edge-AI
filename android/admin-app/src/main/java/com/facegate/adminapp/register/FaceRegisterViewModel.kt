package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.BatchUploadFacesRequest
import com.facegate.core.data.remote.dto.PoseVectorEntry
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceCropUtils
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedderProvider
import com.facegate.core.face.LivenessDetector
import com.facegate.core.face.QualityAnalyzer
import com.facegate.core.face.QualityAnalyzer.QualityReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ──────────────────────────────────────────────
// Registrasi wajah — video 10 detik (1 arah depan)
// (#132/#135): ganti alur multi-pose
// (CENTER/LEFT/RIGHT/UP/DOWN) dengan merekam frame
// depan selama 10 detik, lalu memilih 5–10 frame
// berkualitas terbaik sebagai patokan muka.
// ──────────────────────────────────────────────

data class CapturedFrameData(
    val bitmap: Bitmap,
    val faceRect: Rect,
    val qualityReport: QualityReport,
    val capturedAt: Long
)

enum class FaceRegisterStep {
    DETECTING,
    RECORDING,      // merekam frame depan (countdown 10 detik)
    SELECTING,      // memilih frame terbaik
    PREVIEW,        // menampilkan frame terpilih utk konfirmasi
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

    // Video recording progress
    val recordingProgress: Float = 0f,
    val recordingSeconds: Int = 10,

    // Selected frames
    val selectedFrames: List<CapturedFrameData> = emptyList(),
    val framesRequired: Int = 5,   // min frames
    val framesMax: Int = 10,       // max frames

    // Quality feedback for live display
    val currentQualityScore: Float = 0f,
    val qualityMessages: List<String> = emptyList(),
    val currentYaw: Float = 0f,
    val currentPitch: Float = 0f
)

@HiltViewModel
class FaceRegisterViewModel @Inject constructor(
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedderProvider,
    private val livenessDetector: LivenessDetector,
    private val apiService: ApiService
) : ViewModel() {

    companion object {
        private const val TAG = "FaceRegVM"
        private const val RECORDING_DURATION_MS = 10_000L   // 10 detik (#132)
        private const val QUALITY_INTERVAL_MS = 150L        // throttle capture
        private const val MIN_FRAMES = 5                    // #132: 5-10 frame
        private const val MAX_FRAMES = 10
        private const val DEDUP_MIN_GAP_MS = 700L           // jarak temporal antar frame terpilih
    }

    private val _state = MutableStateFlow(FaceRegisterState(
        framesRequired = MIN_FRAMES,
        framesMax = MAX_FRAMES
    ))
    val state: StateFlow<FaceRegisterState> = _state.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // Buffer frame selama 10 detik
    private val frameBuffer = mutableListOf<CapturedFrameData>()
    private var isProcessing = false
    private var recordingStartTime = 0L
    private var lastCaptureTime = 0L
    private var recordJob: Job? = null
    private var studentId: String = ""

    init {
        faceDetector.init()
        faceEmbedder.init()
    }

    fun setStudentId(id: String) {
        studentId = id
    }

    fun onFrameCaptured(imageProxy: ImageProxy, studentIdParam: String?) {
        if (isProcessing) return
        isProcessing = true
        try {
            onFrameCapturedInternal(imageProxy, studentIdParam)
        } catch (e: Exception) {
            // Satu frame error tidak boleh mengunci isProcessing selamanya —
            // kalau tidak, frame berikutnya semua di-drop → frameBuffer < MIN_FRAMES.
            Log.e(TAG, "onFrameCaptured error", e)
        } finally {
            isProcessing = false
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun onFrameCapturedInternal(imageProxy: ImageProxy, studentIdParam: String?) {
        this.studentId = studentIdParam ?: this.studentId

        val currentStep = _state.value.step
        if (currentStep != FaceRegisterStep.DETECTING &&
            currentStep != FaceRegisterStep.RECORDING
        ) return

        val mediaImage = imageProxy.image
        val rotation = imageProxy.imageInfo.rotationDegrees

        val detection: FaceDetectionResult? = if (mediaImage != null) {
            faceDetector.detectImage(mediaImage, rotation)
        } else null

        if (detection == null) {
            if (currentStep == FaceRegisterStep.DETECTING) {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.DETECTING,
                    message = "Tidak ada wajah terdeteksi",
                    detection = null,
                    currentYaw = 0f,
                    currentPitch = 0f
                )
            }
            return
        }

        val yaw = detection.headEulerAngleY
        val pitch = detection.headEulerAngleX

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            return
        }

        // Quality analysis
        val quality = QualityAnalyzer.analyze(
            bitmap = bitmap,
            faceRect = detection.boundingBox,
            yawAngle = yaw,
            pitchAngle = pitch
        )

        // Live feedback
        _state.value = _state.value.copy(
            detection = detection,
            currentYaw = yaw,
            currentPitch = pitch,
            currentQualityScore = quality.score,
            qualityMessages = quality.messages
        )

        // ─── DETECTING: wajah lurus & berkualitas → mulai rekam ───
        if (currentStep == FaceRegisterStep.DETECTING) {
            if (quality.isPass) {
                startRecording()
            } else {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.DETECTING,
                    message = quality.messages.firstOrNull() ?: "Hadapkan wajah lurus ke kamera"
                )
            }
            bitmap.recycle()
            return
        }

        // ─── RECORDING: kumpulkan frame yang lolos kualitas ───
        if (currentStep == FaceRegisterStep.RECORDING) {
            val now = System.currentTimeMillis()
            // #139: frame yang in-flight setelah countdown 10 detik selesai harus
            // DIBUANG — jangan menambah frame & jangan menyentuh state. Kalau
            // tidak, frame ini bisa menimpa step PREVIEW → rekam "lanjut terus".
            if (now - recordingStartTime > RECORDING_DURATION_MS) {
                bitmap.recycle()
                return
            }
            if (now - lastCaptureTime < QUALITY_INTERVAL_MS) {
                bitmap.recycle()
                return // throttle
            }

            if (quality.isPass && !quality.isBlurry) {
                frameBuffer.add(CapturedFrameData(
                    bitmap = bitmap,
                    faceRect = detection.boundingBox,
                    qualityReport = quality,
                    capturedAt = now
                ))
                lastCaptureTime = now
                val progress = ((now - recordingStartTime).toFloat() / RECORDING_DURATION_MS).coerceIn(0f, 1f)
                // #139: JANGAN set `step = RECORDING` di sini — jalur frame boleh
                // hanya update progress/message. Menimpa step dari thread analyzer
                // bisa memulihkan RECORDING setelah finishRecording() → countdown
                // sudah mati → frame terkumpul tanpa henti.
                _state.value = _state.value.copy(
                    recordingProgress = progress,
                    message = "Rekam... (${frameBuffer.size} frame)"
                )
                Log.d(TAG, "Frame ${frameBuffer.size} score=${"%.3f".format(quality.score)}")
            } else {
                bitmap.recycle()
            }
        }
    }

    /** Mulai window rekam 10 detik — job countdown otomatis. */
    private fun startRecording() {
        frameBuffer.clear()
        recordingStartTime = System.currentTimeMillis()
        lastCaptureTime = 0L
        _state.value = _state.value.copy(
            step = FaceRegisterStep.RECORDING,
            message = "Hadap lurus ke kamera, jangan bergerak...",
            recordingProgress = 0f,
            detection = null
        )

        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < RECORDING_DURATION_MS) {
                delay(100)
                val progress = ((System.currentTimeMillis() - start).toFloat() / RECORDING_DURATION_MS).coerceIn(0f, 1f)
                _state.value = _state.value.copy(recordingProgress = progress)
            }
            finishRecording()
        }
    }

    /** 10 detik selesai → pilih frame terbaik. */
    private fun finishRecording() {
        if (frameBuffer.size < MIN_FRAMES) {
            // Terlalu sedikit frame layak → STOP (jangan kembali ke DETECTING:
            // kalau wajah masih terlihat, DETECTING langsung auto-start rekam
            // lagi → loop rekam 10 detik tanpa henti). Tampilkan error dgn
            // tombol "Coba Lagi" supaya user aksi manual.
            val collected = frameBuffer.size
            recycleBuffer()
            _state.value = _state.value.copy(
                step = FaceRegisterStep.ERROR,
                error = "Frame terlalu sedikit ($collected/$MIN_FRAMES minimum). Pastikan pencahayaan cukup dan wajah tidak bergerak selama 10 detik.",
                recordingProgress = 0f
            )
            return
        }

        _state.value = _state.value.copy(
            step = FaceRegisterStep.SELECTING,
            message = "Memilih frame terbaik..."
        )

        viewModelScope.launch {
            val selected = withContext(Dispatchers.Default) { selectBestFrames() }
            if (selected.size < MIN_FRAMES) {
                recycleBuffer()
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.ERROR,
                    error = "Tidak cukup frame berkualitas setelah seleksi, coba lagi dengan pencahayaan cukup",
                    recordingProgress = 0f
                )
                return@launch
            }

            _state.value = _state.value.copy(
                step = FaceRegisterStep.PREVIEW,
                selectedFrames = selected,
                message = "${selected.size} frame terpilih sebagai patokan muka"
            )
        }
    }

    /**
     * Seleksi frame: skor kualitas tertinggi + dedup temporal.
     * (#132) Sortir turun skor → ambil frame terbaik dengan jarak
     * waktu ≥ DEDUP_MIN_GAP_MS agar frame terpilih tidak identik
     * beruntun → cap MAX_FRAMES.
     */
    private fun selectBestFrames(): List<CapturedFrameData> {
        val sorted = frameBuffer.sortedByDescending { it.qualityReport.score }
        val picked = mutableListOf<CapturedFrameData>()
        for (frame in sorted) {
            if (picked.size >= MAX_FRAMES) break
            val gapOk = picked.none { abs(it.capturedAt - frame.capturedAt) < DEDUP_MIN_GAP_MS }
            if (gapOk) picked.add(frame)
        }
        // Urutkan hasil akhir berdasarkan waktu (natural order video)
        return picked.sortedBy { it.capturedAt }
    }

    /** User menekan "Simpan" pada preview → embed + upload. */
    fun confirmRecording() {
        proceedToEmbedding()
    }

    /** User menekan "Ulangi" pada preview → rekam lagi. */
    fun retryRecording() {
        recycleBuffer()
        _state.value = _state.value.copy(
            step = FaceRegisterStep.DETECTING,
            selectedFrames = emptyList(),
            message = "Arahkan wajah ke dalam oval",
            recordingProgress = 0f
        )
    }

    private fun proceedToEmbedding() {
        val selected = _state.value.selectedFrames
        if (selected.isEmpty()) return
        isProcessing = false

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.EMBEDDING,
                    message = "Memproses ${selected.size} frame wajah...",
                    detection = null
                )

                // ─── Embed tiap frame terpilih → vektor FRONT_1..FRONT_N (#132) ───
                val vectors = withContext(Dispatchers.Default) {
                    selected.mapIndexedNotNull { index, data ->
                        val faceCrop = cropFace(data.bitmap, data.faceRect)
                        val emb = faceEmbedder.embed(faceCrop)
                        if (faceCrop !== data.bitmap) faceCrop.recycle()
                        PoseVectorEntry(
                            pose = "FRONT_${index + 1}",
                            vector = emb.toList()
                        )
                    }
                }

                // Cleanup semua bitmap
                _previewBitmap.value?.recycle()
                _previewBitmap.value = null
                recycleBuffer()

                if (vectors.isEmpty()) {
                    _state.value = _state.value.copy(
                        step = FaceRegisterStep.ERROR,
                        error = "Tidak ada frame yang valid"
                    )
                    return@launch
                }

                _state.value = _state.value.copy(
                    step = FaceRegisterStep.UPLOADING,
                    message = "Mengunggah data wajah..."
                )

                val batchRequest = BatchUploadFacesRequest(vectors = vectors)
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
                recycleBuffer()
                _state.value = _state.value.copy(
                    step = FaceRegisterStep.ERROR,
                    error = "Terjadi kesalahan: ${e.message}"
                )
            }
        }
    }

    private fun recycleBuffer() {
        frameBuffer.forEach { it.bitmap.recycle() }
        frameBuffer.clear()
    }

    fun reset() {
        recordJob?.cancel()
        livenessDetector.reset()
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        recycleBuffer()
        isProcessing = false
        recordingStartTime = 0L
        lastCaptureTime = 0L
        _state.value = FaceRegisterState(
            framesRequired = MIN_FRAMES,
            framesMax = MAX_FRAMES
        )
    }

    override fun onCleared() {
        super.onCleared()
        recordJob?.cancel()
        _previewBitmap.value?.recycle()
        _previewBitmap.value = null
        recycleBuffer()
    }

    /** Crop wajah dengan bentuk kotak + margin — wajib square supaya resize
     *  112×112 di embedder tidak men-distorsi wajah (InsightFace convention). */
    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        return FaceCropUtils.cropSquare(bitmap, boundingBox)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            // ImageProxy.toBitmap() TIDAK menerapkan rotationDegrees → hasilnya
            // frame sensor yang belum diputar. Padahal detection.boundingBox dari
            // ML Kit adalah dalam frame upright (sudah diputar). Tanpa rotasi ini
            // cropFace() memakai koordinat yang salah → embedding sampah → kiosk
            // selalu menampilkan "Wajah tidak dikenal" (fix #130/#ca68c1d).
            val raw = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation == 0) {
                raw
            } else {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rotated !== raw) raw.recycle()
                rotated
            }
        } catch (e: Exception) {
            Log.e(TAG, "toBitmap error", e)
            null
        }
    }
}
