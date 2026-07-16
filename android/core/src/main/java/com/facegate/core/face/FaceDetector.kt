package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Wrapper around ML Kit Face Detection with quality assessment.
 *
 * Detects faces + extracts all landmarks and quality metrics
 * needed for registration and matching pipeline.
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

    /** Detect from raw android.media.Image (no Bitmap conversion needed). */
    fun detectImage(image: Image, rotationDegrees: Int): FaceDetectionResult? {
        if (!isInitialized) {
            Log.w("FaceDetect", "not initialized")
            return null
        }
        lastError = null
        return try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(inputImage))
            if (faces.isEmpty()) return null
            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val resultW = if (isRotated) image.height else image.width
            val resultH = if (isRotated) image.width else image.height
            toResult(resultW, resultH, faces)
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FaceDetect", "detect error: ${e.message}", e)
            null
        }
    }

    /** Detect from Bitmap (fallback). */
    fun detectSync(bitmap: Bitmap): FaceDetectionResult? {
        if (!isInitialized) {
            Log.w("FaceDetect", "not initialized")
            return null
        }
        lastError = null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(image))
            if (faces.isEmpty()) return null
            toResult(bitmap.width, bitmap.height, faces)
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FaceDetect", "detect error: ${e.message}", e)
            null
        }
    }

    private fun toResult(imageWidth: Int, imageHeight: Int, faces: List<Face>): FaceDetectionResult {
        // Pick the largest face in the frame
        val face = faces.maxByOrNull { f ->
            f.boundingBox.width() * f.boundingBox.height()
        }!!

        val sm = face.smilingProbability ?: 0f

        return FaceDetectionResult(
            imageWidth = imageWidth.toFloat(),
            imageHeight = imageHeight.toFloat(),
            boundingBox = face.boundingBox,
            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 1.0f,
            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 1.0f,
            leftEyeContour = extractContourPoints(face, 6),
            rightEyeContour = extractContourPoints(face, 7),
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ,
            headEulerAngleX = face.headEulerAngleX,
            smilingProbability = sm
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
 * Face detection result with comprehensive quality metadata.
 *
 * Quality fields help the registration pipeline reject bad captures
 * before spending compute on embedding extraction.
 */
data class FaceDetectionResult(
    val imageWidth: Float,
    val imageHeight: Float,
    val boundingBox: Rect,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val leftEyeContour: List<PointF>,
    val rightEyeContour: List<PointF>,
    val headEulerAngleY: Float,  // Yaw (left-right)
    val headEulerAngleZ: Float,  // Roll (tilt)
    val headEulerAngleX: Float = 0f,   // Pitch (up-down)
    val smilingProbability: Float = 0f // NEW
) {
    /** Quick quality gate: posture check */
    val isGoodQuality: Boolean
        get() = kotlin.math.abs(headEulerAngleY) < 25f &&
                kotlin.math.abs(headEulerAngleZ) < 25f

    val hasEyeContours: Boolean
        get() = leftEyeContour.size >= 6 && rightEyeContour.size >= 6

    /** Face is roughly centered and properly sized */
    val isWellPositioned: Boolean
        get() {
            val cx = boundingBox.exactCenterX() / imageWidth
            val cy = boundingBox.exactCenterY() / imageHeight
            val faceRatio = (boundingBox.width() * boundingBox.height()).toFloat() / (imageWidth * imageHeight)
            return cx in 0.2f..0.8f && cy in 0.2f..0.8f && faceRatio >= 0.04f
        }
}
