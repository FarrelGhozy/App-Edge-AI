package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Face embedding extractor using a TFLite model.
 *
 * Supports both:
 *   - **MobileFaceNet** (192-d, 112×112 input) — lightweight, default
 *   - **ArcFace / FaceNet512** (512-d, 112×112 or 160×160 input) — higher accuracy
 *
 * The model config is controlled by `init(modelName, embeddingDim, inputSize)`.
 * To switch to a 512-d model, just change the parameters:
 *
 * ```kotlin
 * faceEmbedder.init("arcface_512.tflite", embeddingDim = 512, inputSize = 112)
 * ```
 *
 * Normalization: [-1, 1] after centering around 127.5
 * Output: L2-normalized embedding vector
 */
class FaceEmbedder(private val context: Context) {
    companion object {
        private const val TAG = "FaceEmbedder"
        private const val DEFAULT_MODEL = "mobilefacenet.tflite"
        private const val DEFAULT_DIM = 192
        private const val DEFAULT_INPUT_SIZE = 112
    }

    private var interpreter: Interpreter? = null
    private var embeddingDim: Int = DEFAULT_DIM
    private var inputSize: Int = DEFAULT_INPUT_SIZE
    private var initError: String? = null

    /**
     * Initialize the TFLite interpreter.
     *
     * @param modelName    TFLite file in assets (default: mobilefacenet.tflite)
     * @param embeddingDim Output dimension: 128, 192, 512 (default: 192)
     * @param inputSize    Model input image size (default: 112)
     */
    fun init(
        modelName: String = DEFAULT_MODEL,
        embeddingDim: Int = DEFAULT_DIM,
        inputSize: Int = DEFAULT_INPUT_SIZE
    ): Boolean {
        if (interpreter != null) return true
        return try {
            val modelBuffer = loadModelFile(modelName)
            interpreter = Interpreter(modelBuffer)
            this.embeddingDim = embeddingDim
            this.inputSize = inputSize
            initError = null
            Log.d(TAG, "Embedder initialized: model=$modelName dim=$embeddingDim input=$inputSize")
            true
        } catch (e: Exception) {
            interpreter = null
            initError = "Gagal load model $modelName: ${e.message}"
            Log.e(TAG, initError!!, e)
            false
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val aFd = context.assets.openFd(modelName)
        val input = FileInputStream(aFd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, aFd.startOffset, aFd.declaredLength)
    }

    /**
     * Extract face embedding from a face-cropped Bitmap.
     *
     * @param bitmap Face crop (will be resized to inputSize × inputSize)
     * @return L2-normalized float array of size embeddingDim
     */
    fun embed(bitmap: Bitmap): FloatArray {
        if (interpreter == null) {
            val ok = init()
            if (!ok || interpreter == null)
                throw IllegalStateException(initError ?: "FaceEmbedder belum diinisialisasi")
        }

        val inputBuffer = preprocess(bitmap)
        val outputBuffer = Array(1) { FloatArray(embeddingDim) }
        interpreter!!.run(inputBuffer, outputBuffer)
        return l2Normalize(outputBuffer[0])
    }

    /**
     * Batch embed multiple face crops into a single matrix.
     * Each row = 1 embedding vector.
     */
    fun embedBatch(bitmaps: List<Bitmap>): Array<FloatArray> {
        if (interpreter == null) {
            val ok = init()
            if (!ok || interpreter == null)
                throw IllegalStateException(initError ?: "FaceEmbedder belum diinisialisasi")
        }
        return bitmaps.map { embed(it) }.toTypedArray()
    }

    /**
     * Average multiple embeddings into one template.
     * Useful for multi-frame registration: embed 3 frames → average → store.
     */
    fun averageEmbeddings(embeddings: Array<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(embeddingDim)
        val result = FloatArray(embeddingDim)
        for (emb in embeddings) {
            for (i in result.indices) result[i] += emb[i]
        }
        for (i in result.indices) result[i] /= embeddings.size
        return l2Normalize(result)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // [-1, 1] normalization around 127.5
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
            val b = (pixel and 0xFF) / 127.5f - 1.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        resized.recycle()
        return buffer
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    fun isReady(): Boolean = interpreter != null
    fun getEmbeddingDim(): Int = embeddingDim

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
