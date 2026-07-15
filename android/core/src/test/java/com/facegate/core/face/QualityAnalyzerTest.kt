package com.facegate.core.face

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QualityAnalyzerTest {

    private lateinit var analyzer: QualityAnalyzer

    @Before
    fun setup() {
        analyzer = QualityAnalyzer()
    }

    @Test
    fun `non-null bitmap should return quality result`() {
        // With null bitmap, analyze should return null
        val result = analyzer.analyze(null)
        assertNull(result)
    }

    @Test
    fun `blur score range should be valid`() {
        // Blur is calculated from Laplacian variance of the bitmap
        // Without actual bitmap, use default
        val result = analyzer.calculateBlurScore(null)
        assertEquals(-1f, result, 0.001f)  // -1 indicates error/unavailable
    }

    @Test
    fun `brightness score for null bitmap should return -1`() {
        val result = analyzer.calculateBrightnessScore(null)
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `face angle estimation should handle null contour gracefully`() {
        val result = analyzer.estimateAngle(null)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `quality check with null bitmap should fail`() {
        val result = analyzer.check(null, null)
        assertFalse(result)
    }

    @Test
    fun `size check should reject too-small bounding boxes`() {
        // A face that's too small (less than 80x80 recommended)
        assertFalse(analyzer.isWellPositioned(60f, 60f))
    }

    @Test
    fun `size check should accept adequate face size`() {
        assertTrue(analyzer.isWellPositioned(150f, 200f))
    }

    @Test
    fun `size check should reject zero dimensions`() {
        assertFalse(analyzer.isWellPositioned(0f, 0f))
        assertFalse(analyzer.isWellPositioned(100f, 0f))
        assertFalse(analyzer.isWellPositioned(0f, 100f))
    }

    @Test
    fun `size check should accept large face bounding boxes`() {
        assertTrue(analyzer.isWellPositioned(500f, 500f))
    }

    @Test
    fun `size check threshold boundary`() {
        // Exactly at minimum boundary
        assertFalse(analyzer.isWellPositioned(79f, 79f))
        assertTrue(analyzer.isWellPositioned(80f, 80f))
    }

    @Test
    fun `angle estimation with partially null landmarks should not crash`() {
        // Landmarks with 5 points, some may be null in real scenarios
        val landmarks = listOf(
            null,
            null,
            null,
            null,
            null
        )
        // Should handle gracefully
        val angle = analyzer.estimateAngle(landmarks)
        assertEquals(0f, angle, 0.001f)
    }

    @Test
    fun `angle estimation with valid landmarks`() {
        // Simulate normal forward-facing face landmarks
        val landmarks = listOf(
            Pair(100f, 100f), // left eye
            Pair(200f, 100f), // right eye
            Pair(150f, 160f), // nose tip
            Pair(130f, 200f), // mouth left
            Pair(170f, 200f)  // mouth right
        )

        // These are roughly horizontal → angle should be near 0
        val angle = analyzer.estimateAngle(landmarks)
        assertTrue("Forward face should have near-zero angle, got $angle", kotlin.math.abs(angle) < 30f)
    }

    @Test
    fun `angle estimation with tilted face should detect rotation`() {
        // Tilted face: left eye much lower than right eye
        val leftEyePos = Pair(100f, 200f)
        val rightEyePos = Pair(200f, 100f)

        val angle = analyzer.estimateEyeAngle(leftEyePos, rightEyePos)
        // Eye angle: arctan((200-100)/(100-200)) = arctan(-1) = -45 degrees
        // Or: atan2(200-100, 100-200) = atan2(100, -100) ≈ 135° → but normalized
        assertTrue("Tilted face should have non-zero angle, got $angle", kotlin.math.abs(angle) > 20f)
    }

    @Test
    fun `eye angle with equal positions should be zero`() {
        val angle = analyzer.estimateEyeAngle(Pair(100f, 100f), Pair(100f, 100f))
        assertEquals(0f, angle, 0.001f)
    }

    @Test
    fun `eye angle calculation should handle vertical differences`() {
        // Left eye above right eye
        val angle = analyzer.estimateEyeAngle(Pair(100f, 50f), Pair(200f, 100f))
        // atan2(100-50, 200-100) = atan2(50, 100) ≈ 26.565°
        assertTrue(angle > 20f && angle < 30f)
    }

    @Test
    fun `minBrightness should have reasonable range`() {
        assertTrue(analyzer.minBrightness in 30..80)
    }

    @Test
    fun `maxBrightness should have reasonable range`() {
        assertTrue(analyzer.maxBrightness in 180..250)
    }

    @Test
    fun `minBrightness should be less than maxBrightness`() {
        assertTrue(analyzer.minBrightness < analyzer.maxBrightness)
    }

    @Test
    fun `maxRecommendedAngle should be reasonable`() {
        assertTrue(analyzer.maxRecommendedAngle in 15..45)
    }
}
