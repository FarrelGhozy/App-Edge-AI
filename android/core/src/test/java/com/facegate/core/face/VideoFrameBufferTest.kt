package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VideoFrameBufferTest {

    private lateinit var buffer: VideoFrameBuffer

    @Before
    fun setup() {
        buffer = VideoFrameBuffer(maxFrames = 10, collectDurationMs = 5000L)
    }

    private fun bitmap(): Bitmap = mockk(relaxed = true)

    /** Build a real Rect with geometry via direct public-field assignment
     *  (avoids the stub Rect(int,int,int,int) constructor, whose fields stay 0
     *  in local unit tests). */
    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }

    private fun frame(bitmap: Bitmap, rect: Rect) =
        VideoFrameBuffer.FrameEntry(bitmap = bitmap, timestamp = 0L, faceRect = rect)

    @Test
    fun `consistent face boxes are all accepted`() {
        buffer.start()
        val bmp = bitmap()
        // Slight movements still overlap enough (IoU > 0.35)
        assertEquals(true, buffer.add(frame(bmp, rect(20, 20, 80, 80))))
        assertEquals(true, buffer.add(frame(bmp, rect(25, 22, 85, 82))))
        assertEquals(true, buffer.add(frame(bmp, rect(18, 25, 78, 85))))
        assertEquals(true, buffer.isCollecting())
        assertEquals(3, buffer.size())
    }

    @Test
    fun `face box jumping to different face aborts collection`() {
        buffer.start()
        val bmp1 = bitmap()
        val bmp2 = bitmap()
        assertEquals(true, buffer.add(frame(bmp1, rect(20, 20, 60, 60))))
        // Box jumps to the far corner — no overlap → IoU 0 → abort
        assertEquals(false, buffer.add(frame(bmp2, rect(70, 70, 100, 100))))
        assertFalse("collection must be aborted after a big box jump", buffer.isCollecting())
        assertEquals("frames cleared on abort", 0, buffer.size())
    }

    @Test
    fun `partial overlap below threshold aborts - different person`() {
        buffer.start()
        val bmp1 = bitmap()
        val bmp2 = bitmap()
        // Large box, then a small disparate box -> low IoU
        assertEquals(true, buffer.add(frame(bmp1, rect(5, 5, 95, 95))))
        assertEquals(false, buffer.add(frame(bmp2, rect(0, 0, 30, 30))))
        assertFalse(buffer.isCollecting())
        assertEquals(0, buffer.size())
    }
}
