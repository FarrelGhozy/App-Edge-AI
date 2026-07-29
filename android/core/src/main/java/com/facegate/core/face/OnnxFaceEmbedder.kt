package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * MBF@WebFace600K face embedding via ONNX Runtime.
 *
 * Model: w600k_mbf.onnx from InsightFace buffalo_sc pack.
 * Input:  [1, 3, 112, 112] float32 normalized [0,1]
 * Output: [1, 192] float32 — already L2-normalized, ready for cosine similarity
 */
class OnnxFaceEmbedder(private val context: Context) : FaceEmbedderProvider {

    companion object {
        private const val TAG = "OnnxEmbedder"
        override val INPUT_SIZE = 112
        override val EMBEDDING_DIM = 192
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "output"
    }

    private var session: OrtSession? = null
    private var ortEnv: ai.onnxruntime.OrtEnvironment? = null
    private var ready = false

    override val embeddingDim: Int get() = EMBEDDING_DIM
    override val inputSize: Int get() = INPUT_SIZE

    override fun init(): Boolean {
        if (ready) return true
        return try {
            val manager = OnnxRuntimeManager.getInstance(context)
            if (manager.state != OnnxRuntimeManager.RuntimeState.ONNX_READY) {
                if (!manager.init()) return false
            }
            session = manager.embedderSession
            ortEnv = manager.environment
            ready = session != null
            Log.d(TAG, "ONNX Embedder initialized: $ready")
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            ready = false
            false
        }
    }

    override fun embed(faceCrop: Bitmap): FloatArray {
        if (!ready) {
            if (!init()) throw IllegalStateException("OnnxFaceEmbedder not initialized")
        }

        try {
            val inputTensor = preprocess(faceCrop)

            val inputMap = mapOf(INPUT_NAME to inputTensor)
            val results = session!!.run(inputMap)

            val output = (results.get(OUTPUT_NAME)?.value as? Array<FloatArray>)?.get(0)
                ?: throw IllegalStateException("Unexpected ONNX output format")

            // Model output is already L2-normalized — no need to normalize again
            return output
        } catch (e: Exception) {
            Log.e(TAG, "Embed error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Preprocess face crop bitmap → ONNX tensor (NCHW, normalized [0,1]).
     *
     * InsightFace MBF model expects:
     * - Input: [1, 3, 112, 112] float32
     * - Channel order: RGB
     * - Normalization: pixel / 255.0 (range [0, 1])
     * - Layout: NCHW (channel-first)
     *
     * ⚠️ This is DIFFERENT from TFLite FaceEmbedder which uses NHWC layout.
     */
    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // NCHW layout: [1, 3, H, W] → float array
        val inputData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        var idx = 0
        for (row in 0 until INPUT_SIZE) {
            for (col in 0 until INPUT_SIZE) {
                val pixel = pixels[row * INPUT_SIZE + col]
                // Normalize [0,1]: pixel / 255.0
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                // NCHW: all R's first, then all G's, then all B's
                inputData[idx] = r
                inputData[INPUT_SIZE * INPUT_SIZE + idx] = g
                inputData[2 * INPUT_SIZE * INPUT_SIZE + idx] = b
                idx++
            }
        }

        resized.recycle()

        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val buffer = FloatBuffer.wrap(inputData)
        return OnnxTensor.createTensor(ortEnv!!, buffer, shape)
    }

    override fun release() {
        // Session managed by OnnxRuntimeManager
        ready = false
    }

    override fun isReady(): Boolean = ready
}
