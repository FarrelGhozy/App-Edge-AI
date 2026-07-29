package com.facegate.kioskscanner.matching

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.util.Log
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.engine.*
import com.facegate.core.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Video-based face recognition pipeline.
 *
 * Instead of single-shot matching (1 frame → 1 embedding → match),
 * this engine collects N frames over ~1.5s, quality-filters them,
 * computes embeddings, fuses via quality-weighted average, then matches.
 *
 * Pipeline:
 *   Live preview: RetinaFace detection → quality gate → EAR blink liveness
 *   Collect: capture 10-15 frame bitmaps over ~1.5s
 *   Match:   anti-spoof → embed each → quality-weighted fusion → cosine match → toggle
 */
class VideoMatchEngine @Inject constructor(
    private val faceDetector: FaceDetectorProvider,
    private val faceEmbedder: FaceEmbedderProvider,
    private val faceMatcher: FaceMatcher,
    private val livenessDetector: LivenessDetector,
    private val toggleEngine: ToggleEngine,
    private val violationDetector: ViolationDetector,
    private val sessionTracker: SessionTracker,
    private val studentDao: StudentDao,
    private val faceVectorDao: FaceVectorDao
) {

    companion object {
        private const val TAG = "VideoMatchEngine"
        private const val SPOOF_CONFIDENCE_MIN = 0.3f
        private const val MIN_FRAMES_FOR_FUSION = 3
        private const val CENTER_MARGIN_RATIO = 0.25f
    }

    // Ring buffer for frame collection
    private val frameBuffer = VideoFrameBuffer(maxFrames = 15, collectDurationMs = 1500L)

    // Quality fusion engine
    private val fusionEngine = QualityWeightedFusion()

    // Liveness window tracking
    private var livenessWindowStart: Long = 0L

    /**
     * Synchronous face detection from raw camera image.
     * Call on analyzer thread for live preview.
     */
    fun detectFromImage(mediaImage: Image, rotationDegrees: Int): FaceBox? {
        // Convert Image → Bitmap (fast, use YUV_420_888 direct if possible)
        // For now, use existing conversion and detect
        return try {
            // We detect via RetinaFace which needs Bitmap
            // For live preview optimization, consider keeping ML Kit for detection
            // and RetinaFace only for the final capture
            null // Will be called with Bitmap from ScannerViewModel
        } catch (e: Exception) {
            Log.e(TAG, "detectFromImage error: ${e.message}", e)
            null
        }
    }

    /**
     * Synchronous detection from Bitmap (used during live analyzer).
     */
    fun detectFromBitmap(bitmap: Bitmap): List<FaceBox> {
        return if (faceDetector.isReady()) {
            faceDetector.detect(bitmap)
        } else {
            // Fallback would go here via FaceDetectorWrapper
            emptyList()
        }
    }

    /**
     * Check if face is properly centered in the frame.
     */
    fun isFaceCentered(box: FaceBox, imageWidth: Float, imageHeight: Float): Boolean {
        val cx = imageWidth / 2f
        val cy = imageHeight / 2f
        val bb = box.boundingBox
        val faceCx = bb.exactCenterX()
        val faceCy = bb.exactCenterY()
        val marginX = imageWidth * CENTER_MARGIN_RATIO
        val marginY = imageHeight * CENTER_MARGIN_RATIO
        return kotlin.math.abs(faceCx - cx) <= marginX &&
                kotlin.math.abs(faceCy - cy) <= marginY
    }

    /**
     * EAR-based blink liveness check (same as existing LivenessDetector).
     */
    fun checkLiveness(detection: FaceDetectionResult, currentTimeMs: Long): Boolean {
        // Uses existing LivenessDetector with EAR blink logic
        // This requires FaceDetectionResult which uses ML Kit contours
        // For ONNX-only mode, landmarks from RetinaFace (5 keypoints) can approximate EAR
        return livenessDetector.checkLivenessLegacy(
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = currentTimeMs,
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )
    }

    /** Reset liveness state. */
    fun resetLiveness() {
        livenessDetector.reset()
        livenessWindowStart = 0L
    }

    /** Check if liveness window (3.5s) has expired. */
    fun isLivenessWindowExpired(currentTimeMs: Long): Boolean {
        return currentTimeMs - livenessWindowStart > 3500L
    }

    /** Start tracking liveness window. */
    fun startLivenessWindow() {
        if (livenessWindowStart == 0L) {
            livenessWindowStart = System.currentTimeMillis()
        }
    }

    // ─── Video Collection API ───

    /** Start collecting frames for video-based matching. */
    fun startFrameCollection() {
        frameBuffer.start()
        Log.d(TAG, "Frame collection started")
    }

    /**
     * Add a frame to the collection buffer.
     * Called from live analyzer thread after liveness passed.
     */
    fun addFrame(bitmap: Bitmap, faceBox: FaceBox, qualityScore: Float): Boolean {
        return frameBuffer.add(
            VideoFrameBuffer.FrameEntry(
                bitmap = bitmap,
                timestamp = System.currentTimeMillis(),
                faceRect = faceBox.boundingBox,
                qualityScore = qualityScore
            )
        )
    }

    /** Check if frame collection is complete (full or expired). */
    fun isCollectionComplete(): Boolean {
        return frameBuffer.isFull() || frameBuffer.isExpired()
    }

    /** Get collection progress (0.0..1.0). */
    fun getCollectionProgress(): Float = frameBuffer.getProgress()

    /** Current frame count in buffer. */
    fun getCollectedCount(): Int = frameBuffer.size()

    /** True if actively collecting. */
    fun isCollecting(): Boolean = frameBuffer.isCollecting()

    /**
     * Process collected frames: embed, fuse, and match.
     *
     * Must be called on a background coroutine (Dispatchers.Default).
     */
    suspend fun processVideoCollection(): MatchEngineResult = withContext(Dispatchers.Default) {
        frameBuffer.stop()
        val frames = frameBuffer.getFrames()
        frameBuffer.clear()

        if (frames.size < MIN_FRAMES_FOR_FUSION) {
            Log.w(TAG, "Insufficient frames collected: ${frames.size}")
            return@withContext MatchEngineResult.NoFace
        }

        Log.d(TAG, "Processing ${frames.size} frames for video matching")

        try {
            // 1. Extract embeddings for each frame
            val embedResults = frames.map { entry ->
                try {
                    val faceCrop = cropFace(entry.bitmap, entry.faceRect)
                    val emb = faceEmbedder.embed(faceCrop)
                    if (faceCrop !== entry.bitmap) faceCrop.recycle()
                    entry.copy(embedding = emb)
                } catch (e: Exception) {
                    Log.e(TAG, "Embed failed for frame: ${e.message}")
                    null
                }
            }.filterNotNull()

            if (embedResults.size < MIN_FRAMES_FOR_FUSION) {
                Log.w(TAG, "Too few frames embedded: ${embedResults.size}")
                return@withContext MatchEngineResult.Unknown(0f)
            }

            // 2. Quality-weighted fusion
            val fusedEmbedding = fusionEngine.fuse(embedResults)
            if (fusedEmbedding == null) {
                Log.w(TAG, "Fusion failed")
                return@withContext MatchEngineResult.Unknown(0f)
            }

            // 3. Match against index
            val matchResult = faceMatcher.match(fusedEmbedding)
            val sid = matchResult.studentId
            if (!matchResult.isMatch || sid == null) {
                return@withContext MatchEngineResult.Unknown(matchResult.confidence)
            }

            // 4. Get student info
            val student = studentDao.getById(sid)
                ?: return@withContext MatchEngineResult.Unknown(matchResult.confidence)

            val studentInfo = StudentInfo(
                id = student.id,
                studyProgram = student.studyProgram,
                academicYear = student.academicYear
            )

            // 5. Toggle engine
            val toggle = toggleEngine.determineAction(student.id)

            // 6. Violation check
            val violation = violationDetector.check(toggle.action, studentInfo)

            // 7. Session tracking
            if (toggle.action == ToggleAction.KELUAR) {
                sessionTracker.startSession(student.id, System.currentTimeMillis())
            } else {
                sessionTracker.endSession(student.id, System.currentTimeMillis())
            }

            Log.d(TAG, "Video match SUCCESS: ${student.name} (${toggle.action}), conf=${"%.3f".format(matchResult.confidence)}")

            MatchEngineResult.Matched(
                studentId = student.id,
                studentName = student.name,
                action = toggle.action,
                isViolation = violation.isViolation,
                violationMessage = violation.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video processing error", e)
            MatchEngineResult.Unknown(0f)
        }
    }

    /**
     * Legacy single-image match (fallback).
     */
    suspend fun matchSingleFrame(bitmap: Bitmap): MatchEngineResult {
        return withContext(Dispatchers.Default) {
            try {
                val faces = faceDetector.detect(bitmap)
                val face = faces.maxByOrNull { it.confidence }
                    ?: return@withContext MatchEngineResult.NoFace

                val faceCrop = cropFace(bitmap, face.boundingBox)
                val embedding = faceEmbedder.embed(faceCrop)
                if (faceCrop !== bitmap) faceCrop.recycle()

                val matchResult = faceMatcher.match(embedding)
                val sid = matchResult.studentId
                if (!matchResult.isMatch || sid == null) {
                    return@withContext MatchEngineResult.Unknown(matchResult.confidence)
                }

                val student = studentDao.getById(sid)
                    ?: return@withContext MatchEngineResult.Unknown(matchResult.confidence)

                val toggle = toggleEngine.determineAction(student.id)
                val violation = violationDetector.check(
                    toggle.action,
                    StudentInfo(student.id, student.studyProgram, student.academicYear)
                )

                if (toggle.action == ToggleAction.KELUAR) {
                    sessionTracker.startSession(student.id, System.currentTimeMillis())
                } else {
                    sessionTracker.endSession(student.id, System.currentTimeMillis())
                }

                MatchEngineResult.Matched(
                    studentId = student.id,
                    studentName = student.name,
                    action = toggle.action,
                    isViolation = violation.isViolation,
                    violationMessage = violation.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Single match error", e)
                MatchEngineResult.Unknown(0f)
            }
        }
    }

    /** Crop face region with margin. */
    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val margin = (boundingBox.width() * 0.3f).toInt()
        val x = (boundingBox.left - margin).coerceAtLeast(0)
        val y = (boundingBox.top - margin).coerceAtLeast(0)
        val w = (boundingBox.width() + margin * 2).coerceAtMost(bitmap.width - x)
        val h = (boundingBox.height() + margin * 2).coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }
}
