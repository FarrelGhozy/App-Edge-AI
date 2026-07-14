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

    /** Synchronous face detection from raw camera image — call on analyzer thread. */
    fun detectFromImage(mediaImage: Image, rotationDegrees: Int): FaceDetectionResult? {
        val result = faceDetector.detectImage(mediaImage, rotationDegrees)
        Log.d("MatchEngine", "detectFromImage: ${result != null}")
        return result
    }

    /** Synchronous liveness check — call on analyzer thread. */
    fun checkLiveness(detection: FaceDetectionResult, currentTimeMs: Long): Boolean {
        return livenessDetector.checkLiveness(
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
     * Continue pipeline after detection + liveness pass.
     *
     * Steps: embed → match → toggle → violation → session
     */
    suspend fun matchAfterDetection(detection: FaceDetectionResult, bitmap: Bitmap): MatchEngineResult {
        // Reset liveness for next scan
        livenessDetector.reset()
        livenessWindowStart = 0L

        // Step 4: Face embedding (128-d vector via MobileFaceNet)
        val faceCrop = cropFace(bitmap, detection.boundingBox)
        val embedding = try {
            faceEmbedder.embed(faceCrop)
        } catch (e: Exception) {
            null
        } finally {
            if (faceCrop !== bitmap) faceCrop.recycle()
        }

        // Step 5: Match against face index
        val matchResult = if (embedding != null) {
            faceMatcher.match(embedding)
        } else {
            null
        }

        val sid = matchResult?.studentId
        if (matchResult == null || !matchResult.isMatch || sid == null) {
            return MatchEngineResult.Unknown(matchResult?.confidence ?: 0f)
        }

        // Step 6: Get student info
        val student = studentDao.getById(sid)
            ?: return MatchEngineResult.Unknown(matchResult.confidence)

        val studentInfo = StudentInfo(
            id = student.id,
            studyProgram = student.studyProgram,
            academicYear = student.academicYear
        )

        // Step 7: Toggle engine — determine keluar/kembali
        val toggle = toggleEngine.determineAction(student.id)

        // Step 8: Violation check
        val violation = violationDetector.check(toggle.action, studentInfo)

        // Step 9: Session tracking
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
        val livenessPassed = livenessDetector.checkLiveness(
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
