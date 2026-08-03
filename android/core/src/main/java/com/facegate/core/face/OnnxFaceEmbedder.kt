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
 * Output: [1, 512] float32 — already L2-normalized, ready for cosine similarity
 */
class OnnxFaceEmbedder(private val context: Context) : FaceEmbedderProvider {

    companion object {
        private const val TAG = "OnnxEmbedder"
<<<<<<< HEAD
        val INPUT_SIZE = 112
        val EMBEDDING_DIM = 512
        private const val INPUT_NAME = "input.1"
        private const val OUTPUT_NAME = "516"
=======
>>>>>>> 1bc714a (fix(core,kiosk): fix ONNX pipeline build failure (#58) + duplicate Hilt bindings (#74))
    }

    override val inputSize: Int = 112
    override val embeddingDim: Int = 512

    private var session: OrtSession? = null
    private var ortEnv: ai.onnxruntime.OrtEnvironment? = null
    private var ready = false
    // Resolved at init from the actual session (InsightFace models expose symbol-like
    // input/output names, e.g. input "input.1", output "516"). Never hard-code.
    private var inputName: String? = null
    private var outputName: String? = null

    override fun init(): Boolean {
        if (ready) return true
        return try {
            val manager = OnnxRuntimeManager.getInstance(context)
            if (manager.state != OnnxRuntimeManager.RuntimeState.ONNX_READY) {
                if (!manager.init()) return false
            }
            session = manager.embedderSession
            ortEnv = manager.environment
            if (session == null) return false

            // Resolve input name — the model's only input.
            val inputNames = session!!.inputNames
            inputName = inputNames.firstOrNull()
            if (inputName == null) {
                Log.e(TAG, "No input found on embedder session")
                return false
            }

            // Resolve output name — pick the tensor that matches [?, 512] or the only output.
            val outputInfo = session!!.getOutputInfo()
            val info = outputInfo.entries.first { (_, n) ->
                (n.info as? ai.onnxruntime.TensorInfo)?.let {
                    // shape like [1, 512] or [-1, 512]
                    it.shape.getOrElse(1) { 0L } == 512L
                } ?: false
            }
            outputName = info.key

            ready = outputName != null
            Log.d(TAG, "ONNX Embedder initialized: $ready (input=$inputName output=$outputName)")
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
        val inName = inputName ?: throw IllegalStateException("Embedder input name not resolved")
        val outName = outputName ?: throw IllegalStateException("Embedder output name not resolved")

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            inputTensor = preprocess(faceCrop)

            val inputMap = mapOf(inName to inputTensor)
            results = session!!.run(inputMap)

<<<<<<< HEAD
            val output = (results.get(OUTPUT_NAME).orElse(null)?.value as? Array<FloatArray>)?.get(0)
                ?: throw IllegalStateException("Unexpected ONNX output format")
=======
            val tensor = results.get(outName).orElse(null) as? OnnxTensor
                ?: throw IllegalStateException("Unexpected ONNX output type for '$outName'")
            val buffer: FloatBuffer = tensor.floatBuffer
            val raw = FloatArray(buffer.remaining())
            buffer.get(raw)
>>>>>>> 1bc714a (fix(core,kiosk): fix ONNX pipeline build failure (#58) + duplicate Hilt bindings (#74))

            // InsightFace w600k_mbf does NOT L2-normalize by itself — the Python
            // pipeline normalizes explicitly after inference. Normalize here so
            // embeddings are unit vectors (required for cosine similarity matching).
            return l2Normalize(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Embed error: ${e.message}", e)
            throw e
        } finally {
            try { results?.close() } catch (_: Exception) {}
            try { inputTensor?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Preprocess face crop bitmap → ONNX tensor (NCHW, normalized [0,1]).
     * Layout: NCHW (channel-first) — different from TFLite's NHWC.
     */
    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // NCHW: [1, 3, H, W]
        val inputData = FloatArray(1 * 3 * inputSize * inputSize)
        var idx = 0
        for (row in 0 until inputSize) {
            for (col in 0 until inputSize) {
                val pixel = pixels[row * inputSize + col]
                // InsightFace preprocessing: (pix - 127.5) / 128 (same as detector)
                val r = (((pixel shr 16) and 0xFF) - 127.5f) / 128.0f
                val g = (((pixel shr 8) and 0xFF) - 127.5f) / 128.0f
                val b = ((pixel and 0xFF) - 127.5f) / 128.0f
                inputData[idx] = r
                inputData[inputSize * inputSize + idx] = g
                inputData[2 * inputSize * inputSize + idx] = b
                idx++
            }
        }
        resized.recycle()

        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        return OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(inputData), shape)
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vec) sumSq += v * v
        val norm = kotlin.math.sqrt(sumSq)
        if (norm < 1e-8f) return vec
        for (i in vec.indices) vec[i] = (vec[i] / norm).toFloat()
        return vec
    }

    override fun release() {
        ready = false
    }

    override fun isReady(): Boolean = ready
}