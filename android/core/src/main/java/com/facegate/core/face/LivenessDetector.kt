package com.facegate.core.face

import android.graphics.PointF

/**
 * Liveness detection using Eye Aspect Ratio (EAR).
 *
 * EAR = ratio of vertical to horizontal eye landmark distances.
 * A blink is detected when EAR drops below threshold then rises again.
 *
 * Requires at least 1 natural blink within a 3-second window.
 * Based on Soukupová & Čech (2016) — Real-Time Eye Blink Detection using Facial Landmarks.
 */
class LivenessDetector {
    /** Minimum EAR value to consider eye as "open" */
    var earThreshold: Float = 0.20f

    /** Maximum time (ms) to wait for a blink */
    private val blinkWindowMs: Long = 3_000L

    /** Minimum time (ms) between blinks to avoid false double-trigger */
    private val blinkCooldownMs: Long = 300L

    /** Number of blinks required to pass liveness */
    private val requiredBlinks: Int = 1

    // Internal state
    private var blinkCount: Int = 0
    private var lastEarValue: Float = 1.0f
    private var wasBelowThreshold: Boolean = false
    private var lastBlinkTime: Long = 0L
    private var windowStartTime: Long = 0L
    private var isPassed: Boolean = false

    /**
     * Check liveness based on eye contour points from face detection.
     *
     * @param leftEyeContour  contour points around the left eye (from ML Kit)
     * @param rightEyeContour contour points around the right eye (from ML Kit)
     * @param currentTimeMs   current time in milliseconds
     * @param leftEyeOpenProb ML Kit's built-in eye open probability (0-1), fallback
     * @param rightEyeOpenProb ML Kit's built-in right eye open probability (0-1), fallback
     * @return true if liveness check passed (enough blinks detected)
     */
    fun checkLiveness(
        leftEyeContour: List<PointF>?,
        rightEyeContour: List<PointF>?,
        currentTimeMs: Long,
        leftEyeOpenProb: Float = 1.0f,
        rightEyeOpenProb: Float = 1.0f
    ): Boolean {
        if (isPassed) return true

        // Initialize window on first call
        if (windowStartTime == 0L) {
            windowStartTime = currentTimeMs
        }

        // Timeout: if window expired, reset and try again
        if (currentTimeMs - windowStartTime > blinkWindowMs) {
            if (blinkCount >= requiredBlinks) {
                isPassed = true
                return true
            }
            reset()
            windowStartTime = currentTimeMs
        }

        // Calculate EAR from eye contours (primary method)
        val ear = if (leftEyeContour != null && rightEyeContour != null &&
            leftEyeContour.size >= 6 && rightEyeContour.size >= 6
        ) {
            val leftEAR = calculateEAR(sampleContourPoints(leftEyeContour))
            val rightEAR = calculateEAR(sampleContourPoints(rightEyeContour))
            (leftEAR + rightEAR) / 2f
        } else {
            // Fallback: use ML Kit's built-in probability
            val avgProb = (leftEyeOpenProb + rightEyeOpenProb) / 2f
            0.10f + (avgProb * 0.25f) // Map 0→0.10, 1→0.35
        }

        // Blink detection: EAR drops below threshold → rises again = 1 blink
        if (ear < earThreshold && !wasBelowThreshold) {
            wasBelowThreshold = true
        } else if (ear >= earThreshold && wasBelowThreshold) {
            val timeSinceLastBlink = currentTimeMs - lastBlinkTime
            if (timeSinceLastBlink > blinkCooldownMs) {
                blinkCount++
                lastBlinkTime = currentTimeMs
            }
            wasBelowThreshold = false
        }

        lastEarValue = ear

        if (blinkCount >= requiredBlinks) {
            isPassed = true
        }

        return isPassed
    }

    /**
     * Calculate Eye Aspect Ratio for one eye.
     *
     * EAR = (|p2-p6| + |p3-p5|) / (2 * |p1-p4|)
     *
     * Uses 6 evenly-spaced sample points from the contour array.
     */
    private fun calculateEAR(points: List<PointF>): Float {
        // p1=left corner, p4=right corner, p2=upper-left, p6=upper-right
        // p3=lower-left, p5=lower-right
        val p1 = points[0]  // left corner
        val p2 = points[1]  // upper-left
        val p3 = points[2]  // lower-left
        val p4 = points[3]  // right corner
        val p5 = points[4]  // lower-right
        val p6 = points[5]  // upper-right

        val vertical1 = distance(p2, p6)
        val vertical2 = distance(p3, p5)
        val horizontal = distance(p1, p4)

        if (horizontal < 0.001f) return 1.0f

        return (vertical1 + vertical2) / (2.0f * horizontal)
    }

    /** Sample N evenly-spaced points from the full contour list. */
    private fun sampleContourPoints(contour: List<PointF>, sampleCount: Int = 6): List<PointF> {
        if (contour.size <= sampleCount) return contour.toList()
        val step = (contour.size - 1).toFloat() / (sampleCount - 1)
        return (0 until sampleCount).map { i ->
            contour[(i * step).toInt().coerceIn(0, contour.size - 1)]
        }
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Reset liveness state (call after successful match or timeout). */
    fun reset() {
        blinkCount = 0
        lastEarValue = 1.0f
        wasBelowThreshold = false
        lastBlinkTime = 0L
        windowStartTime = 0L
        isPassed = false
    }

    fun getCurrentEAR(): Float = lastEarValue
    fun getBlinkCount(): Int = blinkCount
    fun hasPassed(): Boolean = isPassed
}
