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
    fun `selectBestFrame should pick highest scoring pass`() {
        val reports = listOf(
            QualityAnalyzer.QualityReport(true, 0.9f, false, 100f, 120f, 0.1f, 5f, 3f),
            QualityAnalyzer.QualityReport(true, 0.7f, false, 80f, 130f, 0.08f, 10f, 5f),
            QualityAnalyzer.QualityReport(true, 0.5f, true, 30f, 100f, 0.03f, 20f, 15f),
        )
        val idx = analyzer.selectBestFrame(reports)
        assertEquals(0, idx)
    }

    @Test
    fun `selectBestFrame should prefer passing frames`() {
        val reports = listOf(
            QualityAnalyzer.QualityReport(false, 0.95f, false, 150f, 120f, 0.1f, 5f, 3f),
            QualityAnalyzer.QualityReport(true, 0.85f, false, 120f, 125f, 0.1f, 5f, 3f),
        )
        val idx = analyzer.selectBestFrame(reports)
        assertEquals(1, idx)
    }

    @Test
    fun `analyze with extreme yaw should return isPass false`() {
        val report = analyzer.analyze(mockBitmap(), Rect(200, 100, 440, 380), 45f, 0f)
        assertFalse("Extreme yaw should fail", report.isPass)
    }

    @Test
    fun `analyze with extreme pitch should return isPass false`() {
        val report = analyzer.analyze(mockBitmap(), Rect(200, 100, 440, 380), 0f, 30f)
        assertFalse("Extreme pitch should fail", report.isPass)
    }

    @Test
    fun `analyze with small face rect should return isPass false`() {
        val report = analyzer.analyze(mockBitmap(), Rect(300, 200, 310, 210), 0f, 0f)
        assertFalse("Very small face should fail", report.isPass)
    }

    @Test
    fun `faceSizeRatio should be between 0 and 1`() {
        val report = analyzer.analyze(mockBitmap(), Rect(200, 100, 440, 380), 0f, 0f)
        assertTrue(report.faceSizeRatio in 0f..1f)
    }

    @Test
    fun `yawAngle and pitchAngle should be preserved in report`() {
        val report = analyzer.analyze(mockBitmap(), Rect(200, 100, 440, 380), 12f, -8f)
        assertEquals(12f, report.yawAngle, 0.001f)
        assertEquals(-8f, report.pitchAngle, 0.001f)
    }
}
