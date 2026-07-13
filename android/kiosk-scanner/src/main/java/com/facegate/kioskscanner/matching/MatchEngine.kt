package com.facegate.kioskscanner.matching

import android.graphics.Bitmap
import android.graphics.Rect
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

    /**
     * Full face recognition pipeline for a single frame.
     *
     * Pipeline: detect → quality check → liveness → embed → match → toggle → violation
     */
    suspend fun match(bitmap: Bitmap): MatchEngineResult {
        // Step 1: Face detection via ML Kit
        val detection = faceDetector.detect(bitmap)
            ?: return MatchEngineResult.NoFace

        // Step 2: Quality check — reject if face is too tilted
        if (!detection.isGoodQuality) {
            return MatchEngineResult.QualityFailed("Wajah terlalu miring — hadap lurus ke kamera")
        }

        // Step 3: Liveness detection — requires natural blink
        val currentTime = System.currentTimeMillis()
        val livenessPassed = livenessDetector.checkLiveness(
            leftEyeContour = detection.leftEyeContour,
            rightEyeContour = detection.rightEyeContour,
            currentTimeMs = currentTime,
            leftEyeOpenProb = detection.leftEyeOpenProbability,
            rightEyeOpenProb = detection.rightEyeOpenProbability
        )
        if (!livenessPassed) {
            // Not failed — still collecting blinks. Return NoFace to keep scanning silently.
            // Only return LivenessFailed after window timeout (handled internally).
            return if (currentTime - getLivenessWindowStart() > 3500L) {
                MatchEngineResult.LivenessFailed
            } else {
                MatchEngineResult.NoFace // silently keep scanning
            }
        }

        // Reset liveness for next scan
        livenessDetector.reset()

        // Step 4: Face embedding (128-d vector via MobileFaceNet)
        val faceCrop = cropFace(bitmap, detection.boundingBox)
        val embedding = try {
            faceEmbedder.embed(faceCrop)
        } catch (e: Exception) {
            // Embedding failed (e.g. model not found) — fall through
            null
        } finally {
            if (faceCrop !== bitmap) faceCrop.recycle()
        }

        // Step 5: Match against face index
        val matchResult = if (embedding != null) {
            faceMatcher.match(embedding)
        } else {
            // No embedding — skip match (model not available)
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
