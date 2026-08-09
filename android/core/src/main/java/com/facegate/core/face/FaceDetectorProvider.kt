package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Provider interface for face detection.
 * Implementations: RetinaFaceDetector (ONNX) primary, FaceDetectorWrapper (ML Kit) fallback.
 */
interface FaceDetectorProvider {
    fun init(): Boolean
    fun detect(bitmap: Bitmap): List<FaceBox>
    fun release()
    fun isReady(): Boolean
}

data class FaceBox(
    val boundingBox: Rect,
    val confidence: Float,
    val landmarks: List<Pair<Float, Float>> = emptyList() // 5 points: leftEye, rightEye, nose, leftMouth, rightMouth
)
