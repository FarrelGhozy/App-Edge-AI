package com.facegate.kioskscanner.matching

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.util.Log
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.engine.*
import com.facegate.core.face.*
import javax.inject.Inject

sealed class MatchEngineResult {
    data class Matched(
        val studentId: String,
        val studentName: String,
        val action: ToggleAction,
        val isViolation: Boolean = false,
        val violationMessage: String? = null
    ) : MatchEngineResult()

    data class Unknown(val confidence: Float) : MatchEngineResult()
    data object LivenessFailed : MatchEngineResult()
    data object NoFace : MatchEngineResult()
    data class QualityFailed(val reason: String) : MatchEngineResult()
}

/**
 * Face recognition pipeline orchestrator.
 *
 * Pipeline:
 *   Camera frame → Face detection → Quality gate → EAR blink liveness
 *     → (after 1s delay) Anti-spoofing → Face embedding → Matching → Toggle → Result
 *
 * Anti-spoofing runs ONLY on the final capture frame (not live preview)
 * to keep the real-time analyzer lightweight.
 */
class MatchEngine @Inject constructor(
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder,
    private val faceMatcher: FaceMatcher,
    private val livenessDetector: LivenessDetector,
    private val toggleEngine: ToggleEngine,
    private val violationDetector: ViolationDetector,
    private val sessionTracker: SessionTracker,
    private val studentDao: StudentDao,
    private val faceVectorDao: FaceVectorDao
) {

    companion object {
        private const val TAG = "MatchEngine"
        // Anti-spoofing threshold — auto-proceed even if spoof detection fails via legacy
        private const val SPOOF_CONFIDENCE_MIN = 0.3f
    }

    /** Synchronous face detection from raw camera image — call on analyzer thread. */
    fun detectFromImage(mediaImage: Image, rotationDegrees: Int): FaceDetectionResult? {
        val result = faceDetector.detectImage(mediaImage, rotationDegrees)
        Log.d(TAG, "detectFromImage: ${result != null}")
        return result
    }

    /**
     * Synchronous liveness check (EAR blink) — call on analyzer thread.
     * Runs on every frame during live preview.
     *
     * Full anti-spoofing (deep learning) runs later in matchAfterDetection().
     */
    fun checkLiveness(detection: FaceDetectionResult, currentTimeMs: Long): Boolean {
        return livenessDetector.checkLivenessLegacy(
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = currentTimeMs,
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )
    }

    /** Reset liveness state (call when pending capture is cancelled). */
    fun resetLiveness() {
        livenessDetector.reset()
        livenessWindowStart = 0L
    }

    /** Check if liveness window (3.5s) has expired. */
    fun isLivenessWindowExpired(currentTimeMs: Long): Boolean {
        return currentTimeMs - getLivenessWindowStart() > 3500L
    }

    /**
     * Continue pipeline after detection + EAR blink pass.
     *
     * Steps:
     *   1. Anti-spoofing (deep learning) — runs on final frame
     *   2. Face embedding (192-d/512-d)
     *   3. Match against face index
     *   4. Toggle (keluar/kembali)
     *   5. Violation check
     *   6. Session tracking
     */
    suspend fun matchAfterDetection(detection: FaceDetectionResult, bitmap: Bitmap): MatchEngineResult {
        // Reset liveness for next scan
        livenessDetector.reset()
        livenessWindowStart = 0L

        // ─── Step 1: Anti-spoofing check (deep learning) ───
        val antiSpoofResult = livenessDetector.checkLiveness(
            frameImage = bitmap,
            faceRect = detection.boundingBox,
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = System.currentTimeMillis(),
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )

        // Check for spoof attack
        if (antiSpoofResult.isSpoof && antiSpoofResult.realConfidence < SPOOF_CONFIDENCE_MIN) {
            Log.w(TAG, "Spoof detected! confidence=${antiSpoofResult.realConfidence}")
            return MatchEngineResult.QualityFailed("Deteksi wajah palsu — coba lagi")
        }
        Log.d(TAG, "Anti-spoof: real=(%.2f) | %s".format(antiSpoofResult.realConfidence, antiSpoofResult.details))

        // ─── Step 2: Face embedding ───
        val faceCrop = cropFace(bitmap, detection.boundingBox)
        val embedding = try {
            faceEmbedder.embed(faceCrop)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed", e)
            null
        } finally {
            if (faceCrop !== bitmap) faceCrop.recycle()
        }

        // ─── Step 3: Match against face index ───
        val matchResult = if (embedding != null) {
            faceMatcher.match(embedding)
        } else {
            null
        }

        val sid = matchResult?.studentId
        if (matchResult == null || !matchResult.isMatch || sid == null) {
            return MatchEngineResult.Unknown(matchResult?.confidence ?: 0f)
        }

        // ─── Step 4: Get student info ───
        val student = studentDao.getById(sid)
            ?: return MatchEngineResult.Unknown(matchResult.confidence)

        val studentInfo = StudentInfo(
            id = student.id,
            studyProgram = student.studyProgram,
            academicYear = student.academicYear
        )

        // ─── Step 5: Toggle engine ───
        val toggle = toggleEngine.determineAction(student.id)

        // ─── Step 6: Violation check ───
        val violation = violationDetector.check(toggle.action, studentInfo)

        // ─── Step 7: Session tracking ───
        if (toggle.action == ToggleAction.KELUAR) {
            sessionTracker.startSession(student.id, System.currentTimeMillis())
        } else {
            sessionTracker.endSession(student.id, System.currentTimeMillis())
        }

        return MatchEngineResult.Matched(
            studentId = student.id,
            studentName = student.name,
            action = toggle.action,
            isViolation = violation.isViolation,
            violationMessage = violation.message
        )
    }

    /**
     * Full face recognition pipeline for a single frame (Bitmap fallback).
     */
    suspend fun match(bitmap: Bitmap): MatchEngineResult {
        val detection = faceDetector.detectSync(bitmap)
            ?: return MatchEngineResult.NoFace

        if (!detection.isGoodQuality) {
            return MatchEngineResult.QualityFailed("Wajah terlalu miring — hadap lurus ke kamera")
        }

        val currentTime = System.currentTimeMillis()
        val livenessPassed = livenessDetector.checkLivenessLegacy(
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = currentTime,
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )
        if (!livenessPassed) {
            return if (currentTime - getLivenessWindowStart() > 3500L) {
                MatchEngineResult.LivenessFailed
            } else {
                MatchEngineResult.NoFace
            }
        }

        return matchAfterDetection(detection, bitmap)
    }

    private var livenessWindowStart: Long = 0L

    private fun getLivenessWindowStart(): Long {
        if (livenessWindowStart == 0L) livenessWindowStart = System.currentTimeMillis()
        return livenessWindowStart
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
}
