package com.facegate.core.face

import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

class LivenessDetector {
    private val earThreshold = 0.2f
    private val blinkWindowMs = 3000L
    private var lastBlinkTime = 0L
    private var consecutiveBlinks = 0
    private val requiredBlinks = 2

    fun checkLiveness(
        result: FaceDetectorResult,
        currentTimeMs: Long
    ): Boolean {
        val detections = result.detections()
        if (detections.isEmpty()) return false

        val detection = detections[0]
        val landmarks = detection.boundingBox()

        val ear = calculateEAR(detection)
        val isBlink = ear < earThreshold

        if (isBlink) {
            if (currentTimeMs - lastBlinkTime > 200) {
                consecutiveBlinks++
                lastBlinkTime = currentTimeMs
            }
        }

        val withinWindow = (currentTimeMs - lastBlinkTime) <= blinkWindowMs
        return withinWindow && consecutiveBlinks >= requiredBlinks
    }

    private fun calculateEAR(detection: com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult.Detection): Float {
        val landmarks = detection.boundingBox()
        val h = landmarks.height().toFloat()
        val w = landmarks.width().toFloat()
        return if (w > 0) h / w else 1f
    }

    fun reset() {
        consecutiveBlinks = 0
        lastBlinkTime = 0L
    }
}
