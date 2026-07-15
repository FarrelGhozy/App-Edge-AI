package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analyzes face image quality before registration/matching.
 *
 * Checks performed:
 *   1. **Blur** — Laplacian variance (below threshold = blurry)
 *   2. **Brightness** — Average pixel intensity (too dark/bright = reject)
 *   3. **Face size** — Face bounding box relative to image size (too small = reject)
 *   4. **Face angle** — Head pose Yaw/Z (from FaceDetectionResult)
 *   5. **Overall quality** — Composite score [0..1]
 */
object QualityAnalyzer {

    data class QualityReport(
        val isPass: Boolean,
        val score: Float,          // composite 0..1
        val isBlurry: Boolean,
        val blurScore: Float,      // higher = sharper
        val brightnessScore: Float, // 0..1, ideal ~0.5
        val faceSizeRatio: Float,   // face bbox area / image area
        val yawAngle: Float,
        val pitchAngle: Float,
        val messages: List<String> = emptyList()
    )

    private const val TAG = "QualityAnalyzer"

    // Thresholds (configurable)
    private const val MIN_LAPLACIAN_VARIANCE = 80f    // below this = blurry
    private const val MIN_BRIGHTNESS = 40f             // [0, 255]
    private const val MAX_BRIGHTNESS = 215f
    private const val MIN_FACE_SIZE_RATIO = 0.05f      // at least 5% of image
    private const val MAX_YAW_ANGLE = 25f               // degrees
    private const val MAX_PITCH_ANGLE = 20f

    // Weighting for composite score
    private const val W_BLUR = 0.25f
    private const val W_BRIGHTNESS = 0.25f
    private const val W_FACE_SIZE = 0.25f
    private const val W_ANGLE = 0.25f

    /**
     * Analyze quality of a detected face in the camera frame.
     *
     * @param bitmap      The full camera bitmap (ARGB_8888)
     * @param faceRect    Face bounding box in bitmap coordinates
     * @param yawAngle    Head rotation Y (from FaceDetectionResult)
     * @param pitchAngle  Head rotation Z (from FaceDetectionResult)
     * @return QualityReport with all metrics
     */
    fun analyze(
        bitmap: Bitmap,
        faceRect: Rect,
        yawAngle: Float,
        pitchAngle: Float
    ): QualityReport {
        val messages = mutableListOf<String>()

        // 1. Blur detection — Laplacian variance
        val blurScore = computeLaplacianVariance(bitmap, faceRect)
        val isBlurry = blurScore < MIN_LAPLACIAN_VARIANCE
        if (isBlurry) messages.add("Wajah blur (${"%.0f".format(blurScore)} < $MIN_LAPLACIAN_VARIANCE)")

        // 2. Brightness — average pixel intensity
        val brightness = computeAverageBrightness(bitmap, faceRect)
        val brightnessIsBad = brightness < MIN_BRIGHTNESS || brightness > MAX_BRIGHTNESS
        if (brightness < MIN_BRIGHTNESS) messages.add("Terlalu gelap (${"%.0f".format(brightness)})")
        if (brightness > MAX_BRIGHTNESS) messages.add("Terlalu terang (${"%.0f".format(brightness)})")

        // 3. Face size ratio
        val faceArea = faceRect.width() * faceRect.height()
        val imageArea = bitmap.width * bitmap.height
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()
        if (faceRatio < MIN_FACE_SIZE_RATIO) messages.add("Wajah terlalu kecil (${"%.1f".format(faceRatio * 100)}%)")

        // 4. Angle check
        val yawOk = abs(yawAngle) <= MAX_YAW_ANGLE
        val pitchOk = abs(pitchAngle) <= MAX_PITCH_ANGLE
        if (!yawOk) messages.add("Miring (yaw=${"%.0f".format(yawAngle)}°)")
        if (!pitchOk) messages.add("Menunduk/mendongak (pitch=${"%.0f".format(pitchAngle)}°)")

        // 5. Composite score (0..1, higher = better)
        val blurNorm = (blurScore / 200f).coerceIn(0f, 1f)
        val brightnessNorm = 1f - abs(brightness - 127.5f) / 127.5f
        val faceSizeNorm = (faceRatio / 0.25f).coerceIn(0f, 1f)
        val angleNorm = 1f - (
            abs(yawAngle).coerceAtMost(MAX_YAW_ANGLE) / MAX_YAW_ANGLE * 0.5f +
            abs(pitchAngle).coerceAtMost(MAX_PITCH_ANGLE) / MAX_PITCH_ANGLE * 0.5f
        )

        val composite = blurNorm * W_BLUR +
                brightnessNorm * W_BRIGHTNESS +
                faceSizeNorm * W_FACE_SIZE +
                angleNorm * W_ANGLE

        val isPass = !isBlurry && !brightnessIsBad &&
                faceRatio >= MIN_FACE_SIZE_RATIO &&
                yawOk && pitchOk

        return QualityReport(
            isPass = isPass,
            score = composite,
            isBlurry = isBlurry,
            blurScore = blurScore,
            brightnessScore = brightness,
            faceSizeRatio = faceRatio,
            yawAngle = yawAngle,
            pitchAngle = pitchAngle,
            messages = messages
        )
    }

    /**
     * Laplacian variance — a well-known blur metric.
     * Higher = sharper. Below ~80-100 = blurry face.
     */
    private fun computeLaplacianVariance(bitmap: Bitmap, faceRect: Rect): Float {
        // Sample from face ROI (or fallback to center crop)
        val x = faceRect.left.coerceAtLeast(0)
        val y = faceRect.top.coerceAtLeast(0)
        val w = faceRect.width().coerceAtMost(bitmap.width - x)
        val h = faceRect.height().coerceAtMost(bitmap.height - y)
        if (w <= 3 || h <= 3) return 0f

        // Simple Laplacian approximation using 3×3 kernel
        // L = [0 -1  0; -1  4  -1; 0  -1  0]
        var sumSq = 0f
        var count = 0

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x, y, w, h)

        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val idx = row * w + col
                val center = brightness(pixels[idx])
                val top = brightness(pixels[idx - w])
                val bottom = brightness(pixels[idx + w])
                val left = brightness(pixels[idx - 1])
                val right = brightness(pixels[idx + 1])
                val laplacian = abs(4f * center - top - bottom - left - right)
                sumSq += laplacian * laplacian
                count++
            }
        }

        return if (count > 0) sumSq / count else 0f
    }

    private fun computeAverageBrightness(bitmap: Bitmap, faceRect: Rect): Float {
        val x = faceRect.left.coerceAtLeast(0)
        val y = faceRect.top.coerceAtLeast(0)
        val w = faceRect.width().coerceAtMost(bitmap.width - x)
        val h = faceRect.height().coerceAtMost(bitmap.height - y)
        if (w <= 0 || h <= 0) return 127f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x, y, w, h)

        var sum = 0f
        for (p in pixels) {
            sum += brightness(p)
        }
        return sum / pixels.size
    }

    private fun brightness(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Picks the best frame from a list of quality reports.
     * Returns the index of the frame with highest composite score that passes all checks.
     */
    fun selectBestFrame(reports: List<QualityReport>): Int {
        var bestIdx = -1
        var bestScore = -1f
        for ((i, r) in reports.withIndex()) {
            if (r.isPass && r.score > bestScore) {
                bestScore = r.score
                bestIdx = i
            }
        }
        // Fallback: take highest score even if not all checks pass
        if (bestIdx < 0) {
            for ((i, r) in reports.withIndex()) {
                if (r.score > bestScore) {
                    bestScore = r.score
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }
}
