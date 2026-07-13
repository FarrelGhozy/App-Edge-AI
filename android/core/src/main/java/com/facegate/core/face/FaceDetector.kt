package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Real face detection using Google ML Kit (bundles MediaPipe internally).
 * No manual TFLite model needed — works via Gradle dependency.
 *
 * Returns bounding box + eye contour points for EAR liveness calculation.
 */
class FaceDetectorWrapper {
    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private var isInitialized = false
    private var lastError: String? = null

    fun init() {
        isInitialized = true
        lastError = null
    }

    /** Detect the largest face. Suspend version for coroutine usage. */
    suspend fun detect(bitmap: Bitmap): FaceDetectionResult? {
        if (!isInitialized) return null
        lastError = null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(image))
            if (faces.isEmpty()) return null
            toResult(bitmap, faces)
        } catch (e: Exception) {
            lastError = e.message
            null
        }
    }

    /** Detect the largest face. Sync version for CameraX analyzer. */
    fun detectSync(bitmap: Bitmap): FaceDetectionResult? {
        if (!isInitialized) return null
        lastError = null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(image))
            if (faces.isEmpty()) return null
            toResult(bitmap, faces)
        } catch (e: Exception) {
            lastError = e.message
            null
        }
    }

    private fun toResult(bitmap: Bitmap, faces: List<Face>): FaceDetectionResult {
        // Take the largest face (closest to camera)
        val face = faces.maxByOrNull { f ->
            f.boundingBox.width() * f.boundingBox.height()
        }!!

        return FaceDetectionResult(
            imageWidth = bitmap.width.toFloat(),
            imageHeight = bitmap.height.toFloat(),
            boundingBox = face.boundingBox,
            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 1.0f,
            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 1.0f,
            leftEyeContour = extractContourPoints(face, 6 /* CONTOUR_LEFT_EYE */),
            rightEyeContour = extractContourPoints(face, 7 /* CONTOUR_RIGHT_EYE */),
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ
        )
    }

    private fun extractContourPoints(face: Face, contourType: Int): List<PointF> {
        val contour = face.getContour(contourType) ?: return emptyList()
        return contour.points.map { PointF(it.x, it.y) }
    }

    fun release() {
        isInitialized = false
        detector.close()
    }

    fun getLastError(): String? = lastError
}

/**
 * Face detection result with bounding box, eye contours, and quality metrics.
 */
data class FaceDetectionResult(
    val imageWidth: Float,
    val imageHeight: Float,
    val boundingBox: Rect,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val leftEyeContour: List<PointF>,
    val rightEyeContour: List<PointF>,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float
) {
    /** Check if face is in a good position for embedding (not too tilted). */
    val isGoodQuality: Boolean
        get() = kotlin.math.abs(headEulerAngleY) < 25f &&
                kotlin.math.abs(headEulerAngleZ) < 25f

    val hasEyeContours: Boolean
        get() = leftEyeContour.size >= 6 && rightEyeContour.size >= 6
}
