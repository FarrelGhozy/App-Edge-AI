package com.facegate.core.face

import android.graphics.PointF
import android.util.Log

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
    /** Blink threshold ratio (relative to running baseline EAR) */
    private val blinkRatio: Float = 0.60f  // EAR < 60% of baseline = closed

    /** Maximum time (ms) to wait for a blink */
    private val blinkWindowMs: Long = 4_000L

    /** Minimum time (ms) between blinks to avoid false double-trigger */
    private val blinkCooldownMs: Long = 300L

    /** Number of blinks required to pass liveness */
    private val requiredBlinks: Int = 1

    // Internal state
    private var blinkCount: Int = 0
    private var baselineEar: Float = 0.0f     // running max EAR (open eyes baseline)
    private var baselineFrames: Int = 0
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
            leftEyeContour.size >= 14 && rightEyeContour.size >= 14
        ) {
            val leftEAR = calculateEAR(leftEyeContour)
            val rightEAR = calculateEAR(rightEyeContour)
            val avgEAR = (leftEAR + rightEAR) / 2f
            Log.d("Liveness", "EAR left=$leftEAR right=$rightEAR avg=$avgEAR baseline=$baselineEar ratio=%.2f wasBelow=$wasBelowThreshold blink=$blinkCount".format(if (baselineEar > 0f) (avgEAR / baselineEar) else 1f))
            avgEAR
        } else {
            // Fallback: use ML Kit's built-in probability
            val avgProb = (leftEyeOpenProb + rightEyeOpenProb) / 2f
            0.10f + (avgProb * 0.25f) // Map 0→0.10, 1→0.35
        }

        lastEarValue = ear

        // Update baseline: running max of EAR (tracks open-eyes level)
        if (ear > baselineEar) {
            baselineEar = ear
            baselineFrames = 0
        } else {
            baselineFrames++
            // Decay baseline slightly if it stays high for many frames (prevents stuck high baseline)
            if (baselineFrames > 30 && baselineEar > 0.15f) {
                baselineEar *= 0.98f
            }
        }

        // Blink detection: EAR drops below ratio of baseline → rises again = 1 blink
        val dynamicThreshold = baselineEar * blinkRatio
        if (ear < dynamicThreshold && !wasBelowThreshold) {
            wasBelowThreshold = true
        } else if (ear >= dynamicThreshold && wasBelowThreshold) {
            val timeSinceLastBlink = currentTimeMs - lastBlinkTime
            if (timeSinceLastBlink > blinkCooldownMs) {
                blinkCount++
                lastBlinkTime = currentTimeMs
            }
            wasBelowThreshold = false
        }

        if (blinkCount >= requiredBlinks) {
            isPassed = true
        }

        return isPassed
    }

    /**
     * Calculate Eye Aspect Ratio for one eye using ML Kit's 16-point contour.
     *
     * ML Kit eye contour ordering (clockwise from outer corner, 16 points):
     *   [0] = outer corner
     *   [1..7] = upper eyelid (outer → inner)
     *   [8] = inner corner
     *   [9..15] = lower eyelid (inner → outer)
     *
     * EAR = average_vertical / horizontal
     *
     * Measures 3 vertical pairs (upper→lower at positions 1/4, 1/2, 3/4)
     *   and divides by eye width (outer→inner corner).
     */
    private fun calculateEAR(contour: List<PointF>): Float {
        if (contour.size < 14) return 1.0f

        val outer = contour[0]
        val inner = contour[8]
        val horizontalDist = distance(outer, inner)
        if (horizontalDist < 0.001f) return 1.0f

        // 3 vertical pairs: upper indices 2,4,6 ↔ lower 14,12,10
        val v1 = distance(contour[2], contour[14])
        val v2 = distance(contour[4], contour[12])
        val v3 = distance(contour[6], contour[10])
        val avgVertical = (v1 + v2 + v3) / 3f

        return avgVertical / horizontalDist
    }



    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Reset liveness state (call after successful match or timeout). */
    fun reset() {
        blinkCount = 0
        baselineEar = 0.0f
        baselineFrames = 0
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
