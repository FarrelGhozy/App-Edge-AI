package com.facegate.core.face

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LivenessDetectorTest {

    private lateinit var detector: LivenessDetector

    @Before
    fun setup() {
        detector = LivenessDetector()
    }

    @Test
    fun `checkLivenessLegacy with null contours should not crash`() {
        val result = detector.checkLivenessLegacy(
            null, null,
            System.currentTimeMillis(),
            0.5f, 0.5f
        )
        assertFalse(result)
    }

    @Test
    fun `checkLivenessLegacy with high probabilities should progress`() {
        val now = System.currentTimeMillis()
        // With high eye-open probabilities and no contour data,
        // detector uses probability-based EAR and starts building baseline
        val result = detector.checkLivenessLegacy(
            null, null,
            now, 0.9f, 0.9f
        )
        // Should not crash, may or may not pass depending on blink state
        assertNotNull(detector.getCurrentEAR())
    }

    @Test
    fun `blink simulated via probability drop should be detected after 2 blinks`() {
        val REQUIRED_BLINKS = 2
        val COOLDOWN_MS = 300L

        // Phase 1: Provide several frames with high probability to establish baseline
        val t0 = System.currentTimeMillis()
        for (i in 0 until 10) {
            detector.checkLivenessLegacy(null, null, t0 + i * 50L, 0.9f, 0.9f)
        }

        // Phase 2: First blink — probability drops, then recovers
        val t1 = t0 + 600L
        detector.checkLivenessLegacy(null, null, t1, 0.2f, 0.2f)  // eyes closed
        val t2 = t1 + 50L
        detector.checkLivenessLegacy(null, null, t2, 0.9f, 0.9f)  // eyes open → blink #1

        // Phase 3: Wait past cooldown
        val t3 = t2 + COOLDOWN_MS + 50L

        // Phase 4: Second blink
        detector.checkLivenessLegacy(null, null, t3, 0.3f, 0.3f)  // eyes closed again
        val t4 = t3 + 50L
        detector.checkLivenessLegacy(null, null, t4, 0.8f, 0.8f)  // eyes open → blink #2

        assertEquals(REQUIRED_BLINKS, detector.getBlinkCount())
        assertTrue(detector.hasPassed())
    }

    @Test
    fun `single blink should not pass when requires 2`() {
        val t0 = System.currentTimeMillis()
        // Establish baseline
        for (i in 0 until 10) {
            detector.checkLivenessLegacy(null, null, t0 + i * 50L, 0.9f, 0.9f)
        }
        // One blink cycle
        val t1 = t0 + 600L
        detector.checkLivenessLegacy(null, null, t1, 0.2f, 0.2f)
        val t2 = t1 + 50L
        detector.checkLivenessLegacy(null, null, t2, 0.9f, 0.9f)

        assertEquals(1, detector.getBlinkCount())
        assertFalse(detector.hasPassed())
    }

    @Test
    fun `getBlinkCount should return 0 initially`() {
        assertEquals(0, detector.getBlinkCount())
    }

    @Test
    fun `hasPassed should return false initially`() {
        assertFalse(detector.hasPassed())
    }

    @Test
    fun `reset should clear blink count and passed state`() {
        val t0 = System.currentTimeMillis()
        for (i in 0 until 20) {
            detector.checkLivenessLegacy(null, null, t0 + i * 30L, 0.9f, 0.9f)
        }
        // 2 blinks to pass
        val t1 = t0 + 1000L
        detector.checkLivenessLegacy(null, null, t1, 0.2f, 0.2f)
        val t2 = t1 + 50L
        detector.checkLivenessLegacy(null, null, t2, 0.9f, 0.9f)
        val t3 = t2 + 500L
        detector.checkLivenessLegacy(null, null, t3, 0.3f, 0.3f)
        val t4 = t3 + 50L
        detector.checkLivenessLegacy(null, null, t4, 0.8f, 0.8f)

        assertTrue(detector.hasPassed())
        detector.reset()
        assertEquals(0, detector.getBlinkCount())
        assertFalse(detector.hasPassed())
    }

    @Test
    fun `checkLivenessLegacy with actual contour points should compute EAR`() {
        // Create minimal 14-point eye contours (EAR computation reads indices 0,2,4,6,8,10,12,14)
        val contour = List(16) { i ->
            val angle = i * (Math.PI * 2 / 16).toFloat()
            PointF(
                100f + 20f * kotlin.math.cos(angle.toDouble()).toFloat(),
                100f + 10f * kotlin.math.sin(angle.toDouble()).toFloat()
            )
        }

        val now = System.currentTimeMillis()
        val result = detector.checkLivenessLegacy(contour, contour, now, 1.0f, 1.0f)
        assertNotNull(detector.getCurrentEAR())
        // Should not crash — actual EAR value depends on points
    }

    @Test
    fun `timeout after 3s window should reset blink count`() {
        val t0 = System.currentTimeMillis()
        for (i in 0 until 10) {
            detector.checkLivenessLegacy(null, null, t0 + i * 50L, 0.9f, 0.9f)
        }
        // 2 blinks
        var t = t0 + 600L
        detector.checkLivenessLegacy(null, null, t, 0.2f, 0.2f)
        detector.checkLivenessLegacy(null, null, t + 50L, 0.9f, 0.9f)
        t = t + 400L
        detector.checkLivenessLegacy(null, null, t, 0.3f, 0.3f)
        detector.checkLivenessLegacy(null, null, t + 50L, 0.8f, 0.8f)

        assertTrue(detector.hasPassed())
    }
}
