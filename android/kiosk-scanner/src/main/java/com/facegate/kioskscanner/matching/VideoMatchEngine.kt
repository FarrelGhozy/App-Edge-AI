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
 *   Live preview: RetinaFace detection → quality gate → face-steady liveness (1.5s)
 *   Collect: capture 10-15 frame bitmaps over ~1.5s
 *   Match:   anti-spoof → embed each → quality-weighted fusion → cosine match → toggle
 */
class VideoMatchEngine @Inject constructor(
    private val faceDetector: FaceDetectorProvider,
    private val faceEmbedder: FaceEmbedderProvider,
    @javax.inject.Named("video") private val faceMatcher: FaceMatcher,
    private val livenessDetector: LivenessDetector,
    private val antiSpoofDetector: AntiSpoofDetector,
    private val toggleEngine: ToggleEngine,
    private val violationDetector: ViolationDetector,
    private val sessionTracker: SessionTracker,
    private val studentDao: StudentDao,
    private val faceVectorDao: FaceVectorDao
) {

    companion object {
        private const val TAG = "VideoMatchEngine"
        private const val SPOOF_CONFIDENCE_MIN = 0.3f
        private const val SPOOF_REJECTS_REQUIRED = 1
        private const val MIN_FRAMES_FOR_FUSION = 3
        private const val CENTER_MARGIN_RATIO = 0.25f
    }

    // Ring buffer for frame collection
    private val frameBuffer = VideoFrameBuffer(maxFrames = 15, collectDurationMs = 1500L)

    // Quality fusion engine
    private val fusionEngine = QualityWeightedFusion()

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
        if (!faceDetector.isReady()) {
            if (!faceDetector.init()) return emptyList()
        }
        return faceDetector.detect(bitmap)
    }

    /**
     * #101: expose error inference terakhir dari detector (RetinaFace).
     * Null = tidak ada error; non-null = inference gagal (bukan "tidak ada wajah").
     */
    fun lastDetectError(): String? {
        return (faceDetector as? RetinaFaceDetector)?.lastDetectError
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
        // TODO(EAR-RetinaFace): Jalur ini masih memakai ML Kit contours (FaceDetectionResult).
        // Pipeline aktif (ONNX-only) tidak punya eye contours — EAR blink TIDAK berjalan di sini;
        // anti-spoof MiniFASNet (VideoMatchEngine line ~199) adalah liveness defense utama.
        // Rencana: hitung EAR dari 5 landmark RetinaFace (kiri/kanan mata = landmark idx 0,1;
        // hidung = 2; mulut = 3,4) — perlu rasio eksperimental karena landmark mata
        // RetinaFace adalah titik sudut, bukan kontur penuh seperti ML Kit.
        // Sementara ini kiosk mengandalkan SPOOF_REJECTS_REQUIRED=1 di VideoMatchEngine.
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

    /** Abort & reset any in-flight frame collection (issue #69: kiosk must be
     *  reusable immediately after a scan without restarting; issue #80: abort
     *  when the face disappears or jumps). Recycles stored bitmaps to avoid
     *  leaks — the caller transfers ownership once frames are added. */
    fun resetCollection() {
        frameBuffer.getFrames().forEach { entry ->
            if (!entry.bitmap.isRecycled) entry.bitmap.recycle()
        }
        frameBuffer.stop()
        frameBuffer.clear()
    }

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
            // 0. Anti-spoof gate (issue #59): run DL anti-spoof on a sample of the
            //    collected frames BEFORE embedding. RetinaFace has only 5 landmarks
            //    (no eye contour) so EAR blink is not available — the MiniFASNet
            //    anti-spoof model is the primary liveness defense. A static photo /
            //    print / screen replay must be rejected here.
            var spoofRejections = 0
            val antiSpoofStep = maxOf(1, frames.size / 2) // sample ~half the frames
            for (i in frames.indices step antiSpoofStep) {
                val frame = frames[i]
                val rect = frame.faceRect ?: continue
                val spoof = antiSpoofDetector.detectSpoof(frame.bitmap, rect)
                if (spoof.isSpoof) {
                    spoofRejections++
                    // Early exit on first confirmed spoof.
                    if (spoofRejections >= SPOOF_REJECTS_REQUIRED) {
                        Log.w(TAG, "Liveness FAILED: anti-spoof detected attack (real=%.2f)".format(spoof.realConfidence))
                        livenessDetector.reset()
                        return@withContext MatchEngineResult.LivenessFailed
                    }
                }
            }
            if (spoofRejections > 0) {
                Log.w(TAG, "Anti-spoof flagged $spoofRejections frame(s) but below reject threshold")
            }

            // 1. Extract embeddings for each frame
            val embedResults = frames.mapNotNull { entry ->
                try {
                    val faceRect = entry.faceRect ?: return@mapNotNull null
                    val faceCrop = cropFace(entry.bitmap, faceRect)
                    val emb = faceEmbedder.embed(faceCrop)
                    if (faceCrop !== entry.bitmap) faceCrop.recycle()
                    entry.copy(embedding = emb)
                } catch (e: Exception) {
                    Log.e(TAG, "Embed failed for frame: ${e.message}")
                    null
                }
            }

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
                violationMessage = violation.message,
                confidence = matchResult.confidence,
                // #91: decision level dipetakan ke UX (CONFIDENT/MEDIUM/WEAK)
                decision = matchResult.decision
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
                    violationMessage = violation.message,
                    confidence = matchResult.confidence
                )
            } catch (e: Exception) {
                Log.e(TAG, "Single match error", e)
                MatchEngineResult.Unknown(0f)
            }
        }
    }

    /** Crop wajah dengan bentuk kotak + margin — wajib square supaya resize
     *  112×112 di embedder tidak men-distorsi wajah (InsightFace convention). */
    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        return FaceCropUtils.cropSquare(bitmap, boundingBox)
    }
}
