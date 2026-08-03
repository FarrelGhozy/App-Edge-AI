package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test: validates the ONNX pipeline on a real device/emulator.
 *
 * Ground truth: det_500m.onnx run on lena.jpg (512x512) via Python reference
 * (insightface decode) → box ≈ (204, 186, 357, 392) @ conf 0.81.
 */
@RunWith(AndroidJUnit4::class)
class OnnxPipelineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var lena: Bitmap

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        lena = BitmapFactory.decodeStream(context.assets.open("lena.jpg"))
        assertNotNull("lena.jpg must exist in androidTest assets", lena)
    }

    @Test
    fun `ONNX Runtime loads both models`() {
        val manager = OnnxRuntimeManager.getInstance(context)
        val ok = manager.init()
        assertTrue("ONNX Runtime init failed: ${manager.getInitError()}", ok)
        assertEquals(OnnxRuntimeManager.RuntimeState.ONNX_READY, manager.state)
    }

    @Test
    fun `RetinaFace detects Lena face matching python reference`() {
        val detector = RetinaFaceDetector(context)
        assertTrue("RetinaFace init failed", detector.init())
        assertTrue("RetinaFace not ready", detector.isReady())

        val faces = detector.detect(lena)
        assertFalse("Expected at least 1 detection on lena.jpg", faces.isEmpty())

        val best = faces.maxByOrNull { it.confidence }!!
        println("Detected: conf=${best.confidence} box=${best.boundingBox}")

        // Python reference: box=(204,186,357,392) conf=0.81 (original 512x512 coords)
        assertTrue("confidence too low: ${best.confidence}", best.confidence > 0.5f)
        val box = best.boundingBox
        assertEquals("left", 204f, box.left.toFloat(), 60f)
        assertEquals("top", 186f, box.top.toFloat(), 60f)
        assertEquals("right", 357f, box.right.toFloat(), 60f)
        assertEquals("bottom", 392f, box.bottom.toFloat(), 60f)
    }

    @Test
    fun `ONNX embedder produces 512-dim embedding`() {
        val embedder = OnnxFaceEmbedder(context)
        assertTrue("ONNX embedder init failed", embedder.init())
        assertEquals(512, embedder.embeddingDim)

        // Crop a face region from lena (python reference box)
        val crop = Bitmap.createBitmap(
            lena,
            190, 175, 180, 230
        )
        val embedding = embedder.embed(crop)
        assertEquals("embedding dim must be 512", 512, embedding.size)

        // L2 norm should be ~1.0 (model outputs normalized embeddings)
        var sumSq = 0.0
        for (v in embedding) sumSq += v * v
        val norm = kotlin.math.sqrt(sumSq)
        println("Embedding norm=$norm (first5=${embedding.take(5).joinToString()})")
        assertTrue("L2 norm should be ~1.0, got $norm", kotlin.math.abs(norm - 1.0) < 0.05)
    }
}
