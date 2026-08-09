package com.facegate.kioskscanner.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.PermitDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.data.local.entity.PermitVerificationEntity
import com.facegate.core.engine.ToggleAction
import com.facegate.core.face.*
import com.facegate.core.sync.SyncManager
import com.facegate.kioskscanner.matching.MatchEngineResult
import com.facegate.kioskscanner.matching.VideoMatchEngine
import com.facegate.kioskscanner.permit.PermitMemberPhase
import com.facegate.kioskscanner.permit.VerifyTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val syncManager: SyncManager,
    private val faceVectorDao: com.facegate.core.data.local.dao.FaceVectorDao,
    private val permitDao: PermitDao
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

    // #130: kunci re-init kamera — increment utk me-rebind CameraX setelah
    // error (mis. bindToLifecycle gagal) tanpa restart activity.
    private val _cameraRetry = MutableStateFlow(0)
    val cameraRetry: StateFlow<Int> = _cameraRetry.asStateFlow()

    // #135: target verifikasi izin. Non-null = scanner berjalan mode verifikasi
    // (scan harus cocok dgn orang ini; tanpa toggle/session/attendance log).
    private val _verifyTarget = MutableStateFlow<VerifyTarget?>(null)
    val verifyTarget: StateFlow<VerifyTarget?> = _verifyTarget.asStateFlow()
    val isVerificationMode: Boolean get() = _verifyTarget.value != null

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
            val message: String? = null,
            // #91: decision level CONFIDENT/MEDIUM/WEAK untuk UX
            val decisionLabel: String? = null
        ) : UIState()
        data class Error(
            val message: String = "Silakan hubungi admin"
        ) : UIState()
    }

    @OptIn(ExperimentalGetImage::class)
    fun onFrameCaptured(imageProxy: ImageProxy) {
            // #129: imageProxy (image buffer kamera) HARUS selalu di-close pada SEMUA
            // path (success, error, throttle-skip, dsb.). Kalau tidak, buffer kamera
            // bocor tiap frame → OOM di kiosk yang berjalan nonstop. try/finally
            // menjamin imageProxy.close() dieksekusi apapun hasilnya.
            try {
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

                // #101: error inference (output shape berubah / sesi rusak) tidak
                // boleh diam-diam jadi "tidak ada wajah" — feedback ke operator.
                val detectError = videoMatchEngine.lastDetectError()
                if (detectError != null && _state.value is UIState.Idle) {
                    _state.value = UIState.Error("Deteksi wajah bermasalah: $detectError")
                    bitmap.recycle()
                    return
                }

                if (face == null) {
                    resetDetectionState()
                    // Issue #80: wajah hilang di tengah koleksi → ABORT. Melanjutkan
                    // akan memfusi frame orang berikutnya yang masuk.
                    if (videoMatchEngine.isCollecting()) {
                        videoMatchEngine.resetCollection()
                        _statusMessage.value = "Arahkan wajah ke kamera"
                        _faceOverlay.value = _faceOverlay.value.copy(
                            isCollecting = false,
                            progress = 0f,
                            collectedCount = 0
                        )
                        faceSteadyStartTime = 0L
                    }
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
                // RetinaFace cuma 5 landmark → tak bisa EAR blink. Solusi: wajah
                // harus stabil di tengah + yaw <25° selama STEADY_DURATION_MS.
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
                        val added = videoMatchEngine.addFrame(bitmap, face, qualityScore)
                        lastFrameCaptureTime = currentTime

                        if (!added) {
                            // Frame rejected (box melompat / beda orang → buffer
                            // abort; issue #80). Reset steady timer agar scan
                            // restart dgn wajah yang konsisten.
                            faceSteadyStartTime = 0L
                            _statusMessage.value = "Arahkan wajah ke kamera"
                            _faceOverlay.value = _faceOverlay.value.copy(
                                isCollecting = false,
                                progress = 0f,
                                collectedCount = 0
                            )
                            bitmap.recycle()
                            return
                        }

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
            } finally {
                // #129: release image buffer kamera — menjamin tidak bocor meski
                // ada early-return di atas.
                imageProxy.close()
            }
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
                val target = _verifyTarget.value
                val result = videoMatchEngine.processVideoCollection(verifyStudentId = target?.studentId)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is MatchEngineResult.Matched -> {
                            if (target != null) {
                                // #135: mode verifikasi izin — queue scan keluar/
                                // kembali utk anggota izin, bukan attendance log.
                                handleVerificationSuccess(target, result)
                            } else {
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
                                    confidenceScore = result.confidence,
                                    isViolation = result.isViolation,
                                    violationType = if (result.isViolation) result.violationMessage else null,
                                    deviceId = deviceId,
                                    // #119: idempotency key unik per log offline —
                                    // retry sync tidak membuat duplikat di server.
                                    clientId = java.util.UUID.randomUUID().toString()
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
                                    message = result.violationMessage,
                                    decisionLabel = when (result.decision) {
                                        com.facegate.core.face.MatchDecision.CONFIDENT -> "Terverifikasi ✅"
                                        com.facegate.core.face.MatchDecision.MEDIUM -> "Cocok ⚠️"
                                        com.facegate.core.face.MatchDecision.WEAK -> "Cocok lemah ❗"
                                        else -> null
                                    }
                                )
                                _statusMessage.value = ""
                            }
                        }
                        is MatchEngineResult.WrongPerson -> {
                            // #135: orang cocok BUKAN yang diverifikasi.
                            voiceFeedback.speakError()
                            _state.value = UIState.Error("Bukan ${target?.studentName ?: "dia"} — terdeteksi ${result.matchedName}")
                            _faceOverlay.value = _faceOverlay.value.copy(
                                qualityColor = Color(0xFFE53935)
                            )
                        }
                        is MatchEngineResult.Unknown -> {
                            voiceFeedback.speakError()
                            _state.value = UIState.Error("Wajah tidak dikenal")
                            _faceOverlay.value = _faceOverlay.value.copy(
                                qualityColor = Color(0xFFE53935)
                            )
                        }
                        is MatchEngineResult.LivenessFailed -> {
                            // #130: pipeline pakai anti-spoof DL + face-steady, BUKAN
                            // EAR blink — instruksi "kedipkan mata" menyesatkan.
                            _state.value = UIState.Error("Verifikasi gagal — hadapkan wajah, jangan tutupi")
                        }
                        is MatchEngineResult.NoFace -> {
                            // #104: jangan biarkan status "Memproses..." menggantung —
                            // reset ke panduan default supaya frame berikutnya bisa scan lagi.
                            _statusMessage.value = "Arahkan wajah ke kamera"
                            faceSteadyStartTime = 0L
                        }
                        is MatchEngineResult.QualityFailed -> {
                            _state.value = UIState.Error(result.reason)
                        }
                    }
                    _isProcessing.value = false
                }
            } catch (e: CancellationException) {
                // #105: cancellation harus di-propagasi, bukan ditelan sebagai error UI.
                _isProcessing.value = false
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video processing error", e)
                _state.value = UIState.Error("Proses gagal, coba lagi")
                _isProcessing.value = false
            }
        }
    }

    /**
     * #135: Verifikasi izin sukses — simpan antrean ke Room (upload via sync)
     * lalu tampilkan overlay sesuai fasa (KELUAR / KEMBALI).
     */
    private fun handleVerificationSuccess(target: VerifyTarget, result: MatchEngineResult.Matched) {
        val phase = target.phase
        val label = when (phase) {
            PermitMemberPhase.KELUAR -> "KELUAR ✅"
            PermitMemberPhase.KEMBALI -> "KEMBALI ✅"
            PermitMemberPhase.SELESAI -> "SELESAI ✅"
        }
        val verb = when (phase) {
            PermitMemberPhase.KELUAR -> "keluar"
            PermitMemberPhase.KEMBALI -> "kembali"
            PermitMemberPhase.SELESAI -> "selesai"
        }

        viewModelScope.launch {
            val deviceId = devicePreferences.getDeviceId()
            permitDao.insertVerification(
                PermitVerificationEntity(
                    permitId = target.permitId,
                    studentId = target.studentId,
                    studentName = target.studentName,
                    confidenceScore = result.confidence,
                    timestamp = System.currentTimeMillis(),
                    clientId = java.util.UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    isSynced = false
                )
            )
            // Optimistic update — server konfirmasi saat sync upload.
            val now = System.currentTimeMillis()
            permitDao.updateVerification(
                permitId = target.permitId,
                studentId = target.studentId,
                keluarAt = if (phase == PermitMemberPhase.KELUAR) now else null,
                kembaliAt = if (phase == PermitMemberPhase.KEMBALI) now else null
            )
        }

        voiceFeedback.speakSuccess(target.studentName, verb)

        _faceOverlay.value = _faceOverlay.value.copy(
            userName = target.studentName,
            actionLabel = label,
            qualityColor = Color(0xFF4CAF50)
        )
        _state.value = UIState.Success(
            studentName = target.studentName,
            actionLabel = label,
            decisionLabel = result.decision?.let {
                when (it) {
                    com.facegate.core.face.MatchDecision.CONFIDENT -> "Terverifikasi ✅"
                    com.facegate.core.face.MatchDecision.MEDIUM -> "Cocok ⚠️"
                    com.facegate.core.face.MatchDecision.WEAK -> "Cocok lemah ❗"
                    com.facegate.core.face.MatchDecision.NO_MATCH -> null
                }
            }
        )
        _statusMessage.value = ""
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
                    // #137: "wajah baru" (delta) vs "total tersimpan" — user sering
                    // bingung melihat 0 saat vektor sudah pernah di-download.
                    val total = faceVectorDao.count()
                    _syncStatus.value = "OK: ${result.facesDownloaded} wajah baru, ${total} tersimpan, ${result.rulesDownloaded} aturan"
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

    /**
     * Hapus total data lokal kiosk (santri, wajah, aturan, log) lalu pull ulang
     * semua dari server. Log offline di-upload dulu supaya tidak hilang.
     */
    fun wipeAndResync() {
        viewModelScope.launch {
            _syncStatus.value = "Menghapus data lokal..."
            try {
                val deviceId = devicePreferences.getDeviceId() ?: "unknown"
                val result = syncManager.wipeAndResync(deviceId)
                if (result.success) {
                    val total = faceVectorDao.count()
                    _syncStatus.value = "OK: ${result.facesDownloaded} wajah dimuat ulang, ${total} tersimpan, ${result.rulesDownloaded} aturan"
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

    // #130: kamera gagal di-bind (bindToLifecycle error) → set UIState.Error
    // supaya operator melihat pesan + tombol "Coba Lagi" (bukan layar hitam).
    fun onCameraError(message: String) {
        _state.value = UIState.Error("Kamera gagal: $message")
    }

// #130: retry kamera — increment key re-init supaya AndroidView di
    // ScannerScreen me-rebind CameraX (unbindAll + bindToLifecycle ulang).
    fun retryCamera() {
        _cameraRetry.value++
        resetState()
    }

    /** #135: mulai mode verifikasi izin untuk target tertentu. */
    fun startVerification(target: VerifyTarget) {
        _verifyTarget.value = target
        resetState()
        _statusMessage.value = "Scan wajah ${target.studentName}"
    }

    /** #135: keluar dari mode verifikasi (kembali ke daftar izin). */
    fun stopVerification() {
        _verifyTarget.value = null
        resetState()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            // #130: buat bitmap sejajar arah PreviewView. ImageProxy.toBitmap()
            // versi ini tidak terima rotationDegrees → decode lalu rotasi manual
            // dgn Matrix sesuai orientasi sensor. Tanpa ini bbox overlay meleset
            // pada rotasi sensor (mis. portrait 90°).
            val raw = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation == 0) {
                raw
            } else {
                val matrix = android.graphics.Matrix()
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

    override fun onCleared() {
        super.onCleared()
        // NOTE: voiceFeedback is @Singleton app-wide and must NOT be released
        // here — onCleared fires on every activity recreate / config change,
        // which would shut down the shared TTS engine permanently (issue #79).
        // Its lifecycle belongs to the Application scope (KioskInitializer),
        // i.e. it lives until the process dies.
    }
}
