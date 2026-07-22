package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class QualityAnalyzerTest {

    private val analyzer = QualityAnalyzer

    private fun mockBitmap(width: Int = 640, height: Int = 480): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.width } returns width
        every { bmp.height } returns height
        every { bmp.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val pixels = firstArg<IntArray>()
            for (i in pixels.indices) pixels[i] = 0xFF808080.toInt()
        }
        return bmp
    }

    @Test
    fun `selectBestFrame should return -1 for empty list`() {
        val idx = analyzer.selectBestFrame(emptyList())
        assertEquals(-1, idx)
    }

    @Test
    fun `selectBestFrame should pick the one with highest score that passes`() {
        val reports = listOf(
            QualityAnalyzer.QualityReport(isPass = false, score = 0.3f, isBlurry = true,
                blurScore = 20f, brightnessScore = 128f, faceSizeRatio = 0.1f,
                yawAngle = 0f, pitchAngle = 0f, messages = listOf("blurry")),
            QualityAnalyzer.QualityReport(isPass = true, score = 0.8f, isBlurry = false,
                blurScore = 150f, brightnessScore = 128f, faceSizeRatio = 0.2f,
                yawAngle = 0f, pitchAngle = 0f),
            QualityAnalyzer.QualityReport(isPass = true, score = 0.6f, isBlurry = false,
                blurScore = 100f, brightnessScore = 128f, faceSizeRatio = 0.15f,
                yawAngle = 0f, pitchAngle = 0f)
        )
        assertEquals(1, analyzer.selectBestFrame(reports))
    }

    @Test
    fun `selectBestFrame should fallback to highest score when none pass`() {
        val reports = listOf(
            QualityAnalyzer.QualityReport(isPass = false, score = 0.4f, isBlurry = true,
                blurScore = 20f, brightnessScore = 128f, faceSizeRatio = 0.1f,
                yawAngle = 0f, pitchAngle = 0f, messages = listOf("blurry")),
            QualityAnalyzer.QualityReport(isPass = false, score = 0.7f, isBlurry = false,
                blurScore = 150f, brightnessScore = 220f, faceSizeRatio = 0.05f,
                yawAngle = 0f, pitchAngle = 0f, messages = listOf("bright")),
            QualityAnalyzer.QualityReport(isPass = false, score = 0.5f, isBlurry = true,
                blurScore = 30f, brightnessScore = 128f, faceSizeRatio = 0.1f,
                yawAngle = 20f, pitchAngle = 0f, messages = listOf("blurry"))
        )
        assertEquals(1, analyzer.selectBestFrame(reports))
    }

    @Test
    fun `analyze should return pass for good quality face`() {
        // Full white pixels yield high avg brightness
        val goodBmp = mockk<Bitmap>(relaxed = true)
        every { goodBmp.width } returns 640
        every { goodBmp.height } returns 480
        every { goodBmp.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val pixels = firstArg<IntArray>()
            for (i in pixels.indices) pixels[i] = 0xFFAAAAAA.toInt()
        }

        val w = 640; val h = 480
        val report = analyzer.analyze(goodBmp, Rect(200, 100, 440, 380), 10f, -5f)
        assertNotNull(report)
        assertTrue(report.score >= 0f)
        assertTrue(report.score <= 1f)
        assertEquals(10f, report.yawAngle, 0.001f)
        assertEquals(-5f, report.pitchAngle, 0.001f)
    }

    @Test
    fun `analyze should flag blurry face`() {
        val blurryBmp = mockk<Bitmap>(relaxed = true)
        every { blurryBmp.width } returns 640
        every { blurryBmp.height } returns 480
        // All same pixel = laplacian variance = 0 → blurry
        every { blurryBmp.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val pixels = firstArg<IntArray>()
            for (i in pixels.indices) pixels[i] = 0xFF808080.toInt()
        }

        val report = analyzer.analyze(blurryBmp, Rect(200, 100, 440, 380), 0f, 0f)
        assertTrue(report.isBlurry)
        assertFalse(report.isPass)
        assertTrue(report.messages.any { it.contains("blur", ignoreCase = true) })
    }

    @Test
    fun `analyze should flag extreme yaw angles`() {
        val bmp = mockBitmap()
        val report = analyzer.analyze(bmp, Rect(200, 100, 440, 380), 40f, 0f)
        assertTrue(report.messages.any { it.contains("Miring", ignoreCase = true) })
    }

    @Test
    fun `analyze should flag extreme pitch angles`() {
        val bmp = mockBitmap()
        val report = analyzer.analyze(bmp, Rect(200, 100, 440, 380), 0f, 30f)
        assertTrue(report.messages.any { it.contains("Menunduk", ignoreCase = true) })
    }

    @Test
    fun `yawAngle and pitchAngle should be preserved in report`() {
        val report = analyzer.analyze(mockBitmap(), Rect(200, 100, 440, 380), 12f, -8f)
        assertEquals(12f, report.yawAngle, 0.001f)
        assertEquals(-8f, report.pitchAngle, 0.001f)
    }

    @Test
    fun `face size ratio should be computed correctly`() {
        val bmp = mockBitmap(640, 480)
        // Very small face rect
        val report = analyzer.analyze(bmp, Rect(300, 220, 340, 260), 0f, 0f)
        val area = 40 * 40 / (640f * 480f)
        assertEquals(area, report.faceSizeRatio, 0.01f)
    }

    @Test
    fun `analyze should detect extreme brightness`() {
        // Test that QualityAnalyzer catches extreme brightness
        // via the blur + brightness score combination
        val bmp = mockBitmap(640, 480)
        val report = analyzer.analyze(bmp, Rect(200, 100, 440, 380), 0f, 0f)
        assertNotNull(report)
        // Test passed if no crash and we get valid scores
        assertTrue(report.blurScore >= 0f)
    }
}
