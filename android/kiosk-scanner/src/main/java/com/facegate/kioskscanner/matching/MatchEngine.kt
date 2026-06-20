package com.facegate.kioskscanner.matching

import android.graphics.Bitmap
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
    suspend fun match(bitmap: Bitmap): MatchEngineResult {
        val detection = faceDetector.detect(bitmap)
            ?: return MatchEngineResult.NoFace

        val livenessPassed = livenessDetector.checkLiveness(
            System.currentTimeMillis()
        )
        if (!livenessPassed) {
            return MatchEngineResult.LivenessFailed
        }

        val embedding = faceEmbedder.embed(bitmap)
        val matchResult = faceMatcher.match(embedding)

        val sid = matchResult.studentId
        if (!matchResult.isMatch || sid == null) {
            return MatchEngineResult.Unknown(matchResult.confidence)
        }

        val student = studentDao.getById(sid)
            ?: return MatchEngineResult.Unknown(matchResult.confidence)

        val studentInfo = StudentInfo(
            id = student.id,
            studyProgram = student.studyProgram,
            academicYear = student.academicYear
        )

        val toggle = toggleEngine.determineAction(student.id)
        val violation = violationDetector.check(toggle.action, studentInfo)

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
}
