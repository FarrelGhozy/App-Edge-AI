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
        val result = detector.checkLivenessLegacy(null, null, now, 0.9f, 0.9f)
        assertFalse("Single frame should not immediately pass", result)
    }

    @Test
    fun `blink simulated via probability drop should be detected`() {
        val now = System.currentTimeMillis()

        // Establish baseline with high prob (eyes open)
        for (i in 1..5) {
            detector.checkLivenessLegacy(null, null, now, 0.9f, 0.9f)
        }

        // Sudden drop (eyes closed)
        detector.checkLivenessLegacy(null, null, now + 1000L, 0.05f, 0.05f)

        // Back to high prob after cooldown (eyes open → blink complete)
        val result = detector.checkLivenessLegacy(null, null, now + 1500L, 0.9f, 0.9f)

        assertTrue("Blink should be detected via probability change", result)
    }

    @Test
    fun `reset should clear state`() {
        detector.checkLivenessLegacy(null, null, System.currentTimeMillis(), 0.5f, 0.5f)
        detector.reset()
        assertEquals(0, detector.getBlinkCount())
    }

    @Test
    fun `getCurrentEAR should return last value`() {
        detector.checkLivenessLegacy(null, null, System.currentTimeMillis(), 0.5f, 0.5f)
        assertTrue(detector.getCurrentEAR() > 0f)
    }

    @Test
    fun `getBlinkCount should return 0 initially`() {
        assertEquals(0, detector.getBlinkCount())
    }

    @Test
    fun `hasPassed should return false initially`() {
        assertFalse(detector.hasPassed())
    }
}
