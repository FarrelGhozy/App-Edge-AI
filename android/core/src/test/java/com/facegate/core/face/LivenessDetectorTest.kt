package com.facegate.core.face

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
    fun `EAR with fully open eyes should be above blink threshold`() {
        // Wide open eye (horizontal >> vertical)
        val leftEye = listOf(
            Pair(0f, 10f),    // top-left
            Pair(10f, 0f),    // top
            Pair(20f, 10f),   // top-right
            Pair(20f, 20f),   // bottom-right
            Pair(10f, 30f),   // bottom
            Pair(0f, 20f),    // bottom-left
        )
        val rightEye = leftEye
        val ear = detector.calculateEAR(leftEye.map { it.first to it.second }, rightEye.map { it.first to it.second })
        // EAR for this shape ≈ 1.0 (horizontal distance ~20, vertical ~20 → EAR ≈ 20/20 = 1.0)
        assertTrue("EAR should be high for open eyes, got $ear", ear > 0.3f)
        assertTrue(detector.isEyeOpen(ear))
    }

    @Test
    fun `EAR with closed eyes should be below blink threshold`() {
        // Closed eye (horizontal >> vertical, narrow gap)
        val eye = listOf(
            Pair(0f, 10f),   // top-left
            Pair(10f, 9f),   // top (barely below)
            Pair(20f, 10f),  // top-right
            Pair(20f, 11f),  // bottom-right
            Pair(10f, 12f),  // bottom
            Pair(0f, 11f),   // bottom-left
        )
        val ear = detector.calculateEAR(eye.map { it.first to it.second }, eye.map { it.first to it.second })
        assertTrue("EAR should be low for closed eyes, got $ear", ear < 0.2f)
        assertFalse(detector.isEyeOpen(ear))
    }

    @Test
    fun `asymmetrical EAR should still be calculated`() {
        // Left eye open, right eye closed (blinking one eye)
        val openEye = listOf(
            Pair(0f, 10f), Pair(10f, 0f), Pair(20f, 10f),
            Pair(20f, 20f), Pair(10f, 30f), Pair(0f, 20f),
        )
        val closedEye = listOf(
            Pair(0f, 10f), Pair(10f, 9f), Pair(20f, 10f),
            Pair(20f, 11f), Pair(10f, 12f), Pair(0f, 11f),
        )

        // EAR = average of left and right EAR
        // Left: ≈1.0, Right: ≈0.1 → Average ≈0.55
        val ear = detector.calculateEAR(
            openEye.map { it.first to it.second },
            closedEye.map { it.first to it.second }
        )
        assertTrue("Asymmetrical EAR should be moderate, got $ear", ear > 0.2f && ear < 0.8f)
    }

    @Test
    fun `EAR should be symmetric for identical left-right eyes`() {
        val eye = listOf(
            Pair(0f, 10f), Pair(10f, 0f), Pair(20f, 10f),
            Pair(20f, 20f), Pair(10f, 30f), Pair(0f, 20f),
        )
        val ear = detector.calculateEAR(eye.map { it.first to it.second }, eye.map { it.first to it.second })
        val repeatEar = detector.calculateEAR(eye.map { it.first to it.second }, eye.map { it.first to it.second })
        assertEquals(ear, repeatEar, 0.001f)
    }

    @Test
    fun `blink detection should work after multiple frames`() {
        // Start with open eyes
        val openEye = listOf(
            Pair(0f, 10f), Pair(10f, 0f), Pair(20f, 10f),
            Pair(20f, 20f), Pair(10f, 30f), Pair(0f, 20f),
        )

        // Frame 1: open
        var result = detector.checkLivenessLegacy(
            openEye.map { it.first to it.second },
            openEye.map { it.first to it.second },
            System.currentTimeMillis(),
            0.5f, 0.5f
        )
        // Initially no blink detected (need to learn baseline)
        assertTrue(result)

        // Simulate 5 more open frames to establish baseline
        for (i in 2..6) {
            result = detector.checkLivenessLegacy(
                openEye.map { it.first to it.second },
                openEye.map { it.first to it.second },
                System.currentTimeMillis(),
                0.5f, 0.5f
            )
        }

        // Now send closed eye frame (blink event)
        val closedEye = listOf(
            Pair(0f, 10f), Pair(10f, 9f), Pair(20f, 10f),
            Pair(20f, 11f), Pair(10f, 12f), Pair(0f, 11f),
        )

        // First closed frame → should still pass (blink partial)
        result = detector.checkLivenessLegacy(
            closedEye.map { it.first to it.second },
            closedEye.map { it.first to it.second },
            System.currentTimeMillis(),
            0.05f, 0.05f
        )
        assertTrue("A blink should be treated as liveness", result)
    }

    @Test
    fun `multiple consecutive closed frames should fail`() {
        val closedEye = listOf(
            Pair(0f, 10f), Pair(10f, 9f), Pair(20f, 10f),
            Pair(20f, 11f), Pair(10f, 12f), Pair(0f, 11f),
        )

        // 10 consecutive frames with eyes closed
        var result = true
        for (i in 1..10) {
            result = detector.checkLivenessLegacy(
                closedEye.map { it.first to it.second },
                closedEye.map { it.first to it.second },
                System.currentTimeMillis(),
                0.01f, 0.01f
            )
        }
        assertFalse("Continuously closed eyes should fail liveness", result)
    }

    @Test
    fun `minimal EAR with near-zero values should not crash`() {
        val tinyEye = listOf(
            Pair(0f, 0.01f), Pair(0.01f, 0f), Pair(0.02f, 0.01f),
            Pair(0.02f, 0.02f), Pair(0.01f, 0.03f), Pair(0f, 0.02f),
        )
        val ear = detector.calculateEAR(tinyEye.map { it.first to it.second }, tinyEye.map { it.first to it.second })
        assertFalse("Near-zero coordinates should produce small EAR", ear.isNaN())
        assertFalse(detector.isEyeOpen(ear))
    }

    @Test
    fun `default mode should be HYBRID`() {
        assertEquals(LivenessDetector.Mode.HYBRID, detector.currentMode)
    }

    @Test
    fun `switching mode should update currentMode`() {
        detector.switchMode(LivenessDetector.Mode.EAR_ONLY)
        assertEquals(LivenessDetector.Mode.EAR_ONLY, detector.currentMode)

        detector.switchMode(LivenessDetector.Mode.ANTI_SPOOF_ONLY)
        assertEquals(LivenessDetector.Mode.ANTI_SPOOF_ONLY, detector.currentMode)
    }

    @Test
    fun `extreme coordinates should not cause NaN`() {
        val largeCoord = 10000f
        val bigEye = listOf(
            Pair(largeCoord, largeCoord + 10f),
            Pair(largeCoord + 10f, largeCoord),
            Pair(largeCoord + 20f, largeCoord + 10f),
            Pair(largeCoord + 20f, largeCoord + 20f),
            Pair(largeCoord + 10f, largeCoord + 30f),
            Pair(largeCoord, largeCoord + 20f),
        )
        val ear = detector.calculateEAR(bigEye.map { it.first to it.second }, bigEye.map { it.first to it.second })
        assertFalse("Large coords should not produce NaN", ear.isNaN())
    }
}
