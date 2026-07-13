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
            Log.d("FaceDetect", "faces found=${faces.size}")
            if (faces.isEmpty()) return null
            // ML Kit returns coords in rotated space. Swap w/h when rotated 90/270.
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
            Log.d("FaceDetect", "faces found=${faces.size}")
            if (faces.isEmpty()) return null
            toResult(bitmap.width, bitmap.height, faces)
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FaceDetect", "detect error: ${e.message}", e)
            null
        }
    }

    private fun toResult(imageWidth: Int, imageHeight: Int, faces: List<Face>): FaceDetectionResult {
        val face = faces.maxByOrNull { f ->
            f.boundingBox.width() * f.boundingBox.height()
        }!!

        return FaceDetectionResult(
            imageWidth = imageWidth.toFloat(),
            imageHeight = imageHeight.toFloat(),
            boundingBox = face.boundingBox,
            leftEyeOpenProbability = face.leftEyeOpenProbability ?: 1.0f,
            rightEyeOpenProbability = face.rightEyeOpenProbability ?: 1.0f,
            leftEyeContour = extractContourPoints(face, 6),
            rightEyeContour = extractContourPoints(face, 7),
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
    val isGoodQuality: Boolean
        get() = kotlin.math.abs(headEulerAngleY) < 25f &&
                kotlin.math.abs(headEulerAngleZ) < 25f

    val hasEyeContours: Boolean
        get() = leftEyeContour.size >= 6 && rightEyeContour.size >= 6
}
