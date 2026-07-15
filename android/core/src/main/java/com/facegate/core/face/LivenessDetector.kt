package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log

/**
 * Liveness detection: Hybrid approach combining:
 * 1. **Deep learning anti-spoofing** (primary) — MiniFASNet detects photo/video/screen attacks
 * 2. **Eye Aspect Ratio (EAR)** (fallback) — blink detection for extra assurance
 *
 * Pipeline:
 *   Anti-spoof check → if spoof detected → REJECT immediately
 *   If real → EAR blink (fast, lightweight) → PASS after 1 natural blink
 *
 * Configuration:
 * - Spoof confidence threshold: 0.5 (below = treat as spoof)
 * - EAR blink required: 1 natural blink in 4-second window
 * - If anti-spoof model unavailable → fallback to EAR-only mode
 */
class LivenessDetector(
    private val antiSpoofDetector: AntiSpoofDetector? = null
) {
    companion object {
        private const val TAG = "LivenessDetector"

        // Anti-spoof thresholds
        private const val SPOOF_CONFIDENCE_THRESHOLD = 0.5f

        // EAR blink detection
        private const val BLINK_RATIO = 0.60f
        private const val BLINK_WINDOW_MS = 4_000L
        private const val BLINK_COOLDOWN_MS = 300L
        private const val REQUIRED_BLINKS = 1
    }

    // ─── EAR blink state ───
    private var blinkCount = 0
    private var baselineEar = 0.0f
    private var baselineFrames = 0
    private var lastEarValue = 1.0f
    private var wasBelowThreshold = false
    private var lastBlinkTime = 0L
    private var windowStartTime = 0L
    private var earPassed = false

    // ─── Anti-spoof state ───
    private var lastSpoofResult: AntiSpoofDetector.SpoofResult? = null
    private var antiSpoofPassed = false

    /**
     * Run full liveness check: anti-spoofing (DL) + EAR blink.
     *
     * @param frameImage      Full camera frame (for anti-spoof crop)
     * @param faceRect        Face bounding box (for anti-spoof crop)
     * @param leftEyeContour  ML Kit left eye contour points
     * @param rightEyeContour ML Kit right eye contour points
     * @param currentTimeMs   Current timestamp for blink windowing
     * @param leftEyeOpenProb ML Kit eye-open probability (fallback)
     * @param rightEyeOpenProb ML Kit eye-open probability (fallback)
     * @return LivenessResult with pass/fail + details
     */
    suspend fun checkLiveness(
        frameImage: Bitmap,
        faceRect: Rect,
        leftEyeContour: List<PointF>?,
        rightEyeContour: List<PointF>?,
        currentTimeMs: Long,
        leftEyeOpenProb: Float = 1.0f,
        rightEyeOpenProb: Float = 1.0f
    ): LivenessResult {
        val detailMessages = mutableListOf<String>()

        // ─── 1. Anti-spoofing (DL) ───
        if (!antiSpoofPassed && antiSpoofDetector != null) {
            val spoofResult = antiSpoofDetector.detectSpoof(frameImage, faceRect)
            lastSpoofResult = spoofResult

            if (!spoofResult.isSpoof && spoofResult.realConfidence >= SPOOF_CONFIDENCE_THRESHOLD) {
                antiSpoofPassed = true
                detailMessages.add("anti-spoof: real (%.2f)".format(spoofResult.realConfidence))
            } else {
                detailMessages.add("anti-spoof: SPOOF (%.2f)".format(spoofResult.realConfidence))
                // Early reject — definitely a spoof
                return LivenessResult(
                    passed = false,
                    isSpoof = true,
                    realConfidence = spoofResult.realConfidence,
                    blinkCount = blinkCount,
                    details = "Spoof detected! Confidence: %.2f".format(spoofResult.realConfidence)
                )
            }
        }

        // ─── 2. EAR blink (secondary check) ───
        val earOk = checkEarBlink(
            leftEyeContour, rightEyeContour,
            currentTimeMs, leftEyeOpenProb, rightEyeOpenProb
        )
        if (earOk) {
            detailMessages.add("blink: $blinkCount/$REQUIRED_BLINKS")
        } else {
            detailMessages.add("blink: waiting...")
        }

        val allPassed = (antiSpoofPassed || antiSpoofDetector == null) &&
                (earPassed || antiSpoofDetector != null)

        return LivenessResult(
            passed = allPassed,
            isSpoof = !antiSpoofPassed && antiSpoofDetector != null,
            realConfidence = lastSpoofResult?.realConfidence ?: 0f,
            blinkCount = blinkCount,
            details = detailMessages.joinToString(" | ")
        )
    }

    /**
     * Legacy synchronous check liveness (EAR-only) for backward compatibility.
     * Uses same EAR logic without anti-spoofing.
     */
    fun checkLivenessLegacy(
        leftEyeContour: List<PointF>?,
        rightEyeContour: List<PointF>?,
        currentTimeMs: Long,
        leftEyeOpenProb: Float = 1.0f,
        rightEyeOpenProb: Float = 1.0f
    ): Boolean {
        return checkEarBlink(
            leftEyeContour, rightEyeContour,
            currentTimeMs, leftEyeOpenProb, rightEyeOpenProb
        )
    }

    private fun checkEarBlink(
        leftEyeContour: List<PointF>?,
        rightEyeContour: List<PointF>?,
        currentTimeMs: Long,
        leftEyeOpenProb: Float,
        rightEyeOpenProb: Float
    ): Boolean {
        if (earPassed) return true

        if (windowStartTime == 0L) {
            windowStartTime = currentTimeMs
        }

        // Timeout: window expired
        if (currentTimeMs - windowStartTime > BLINK_WINDOW_MS) {
            if (blinkCount >= REQUIRED_BLINKS) {
                earPassed = true
                return true
            }
            resetEar()
            windowStartTime = currentTimeMs
        }

        val ear = if (leftEyeContour != null && rightEyeContour != null &&
            leftEyeContour.size >= 14 && rightEyeContour.size >= 14
        ) {
            (calculateEAR(leftEyeContour) + calculateEAR(rightEyeContour)) / 2f
        } else {
            0.10f + ((leftEyeOpenProb + rightEyeOpenProb) / 2f) * 0.25f
        }

        lastEarValue = ear

        // Running baseline (max observed)
        if (ear > baselineEar) {
            baselineEar = ear
            baselineFrames = 0
        } else {
            baselineFrames++
            if (baselineFrames > 30 && baselineEar > 0.15f) {
                baselineEar *= 0.98f
            }
        }

        // Blink: falls below threshold then rises again
        val threshold = baselineEar * BLINK_RATIO
        if (ear < threshold && !wasBelowThreshold) {
            wasBelowThreshold = true
        } else if (ear >= threshold && wasBelowThreshold) {
            if (currentTimeMs - lastBlinkTime > BLINK_COOLDOWN_MS) {
                blinkCount++
                lastBlinkTime = currentTimeMs
            }
            wasBelowThreshold = false
        }

        if (blinkCount >= REQUIRED_BLINKS) {
            earPassed = true
        }
        return earPassed
    }

    private fun calculateEAR(contour: List<PointF>): Float {
        if (contour.size < 14) return 1.0f
        val outer = contour[0]
        val inner = contour[8]
        val hDist = distance(outer, inner)
        if (hDist < 0.001f) return 1.0f
        val v1 = distance(contour[2], contour[14])
        val v2 = distance(contour[4], contour[12])
        val v3 = distance(contour[6], contour[10])
        return ((v1 + v2 + v3) / 3f) / hDist
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun reset() {
        resetEar()
        antiSpoofPassed = false
        lastSpoofResult = null
    }

    private fun resetEar() {
        blinkCount = 0
        baselineEar = 0.0f
        baselineFrames = 0
        lastEarValue = 1.0f
        wasBelowThreshold = false
        lastBlinkTime = 0L
        windowStartTime = 0L
        earPassed = false
    }

    fun getCurrentEAR(): Float = lastEarValue
    fun getBlinkCount(): Int = blinkCount
    fun hasPassed(): Boolean = earPassed && (antiSpoofPassed || antiSpoofDetector == null)
}

data class LivenessResult(
    val passed: Boolean,
    val isSpoof: Boolean = false,
    val realConfidence: Float = 0f,
    val blinkCount: Int = 0,
    val details: String = ""
)
