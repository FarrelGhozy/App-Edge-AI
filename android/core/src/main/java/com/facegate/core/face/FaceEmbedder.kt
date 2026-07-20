package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Face embedding extractor using a TFLite model.
 * Supports both quantized (INT8) and float models automatically.
 *
 * Normalization: pixel / 255.0 ([0, 1] range) — standard for face recognition models.
 * Previously used [-1, 1] range which caused poor accuracy.
 *
 * To switch model: faceEmbedder.init("other.tflite", embeddingDim = N, inputSize = N)
 */
class FaceEmbedder(private val context: Context) {
    companion object {
        private const val TAG = "FaceEmbedder"
        private const val DEFAULT_MODEL = "mobilefacenet.tflite"
        private const val DEFAULT_DIM = 192
        private const val DEFAULT_INPUT_SIZE = 112
        private const val NORMALIZE_01 = true
    }

    private var isNormalized01: Boolean = true

    private var interpreter: Interpreter? = null
    private var embeddingDim: Int = DEFAULT_DIM
    private var inputSize: Int = DEFAULT_INPUT_SIZE
    private var isInputQuantized: Boolean = false
    private var isOutputQuantized: Boolean = false
    private var initError: String? = null

    /**
     * Initialize the TFLite interpreter.
     *
     * @param modelName         TFLite file in assets (default: mobilefacenet.tflite)
     * @param embeddingDim      Output dimension: 192 (MobileFaceNet) or 512 (ArcFace)
     * @param inputSize         Model input image size (default: 112)
     * @param normalize01       If true, normalize pixel to [0,1] (pixel/255.0).
     *                          If false, normalize to [-1,1] (pixel/127.5-1.0).
     */
    fun init(
        modelName: String = DEFAULT_MODEL,
        embeddingDim: Int = DEFAULT_DIM,
        inputSize: Int = DEFAULT_INPUT_SIZE,
        normalize01: Boolean = true
    ): Boolean {
        if (interpreter != null) return true
        return try {
            val modelBuffer = loadModelFile(modelName)
            interpreter = Interpreter(modelBuffer)
            this.embeddingDim = embeddingDim
            this.inputSize = inputSize

            val inputType = interpreter!!.getInputTensor(0).dataType()
            isInputQuantized = inputType == DataType.UINT8
            val outputType = interpreter!!.getOutputTensor(0).dataType()
            isOutputQuantized = outputType == DataType.UINT8
            isNormalized01 = normalize01

            initError = null
            Log.d(TAG, "Embedder initialized: model=$modelName dim=$embeddingDim input=$inputSize normalize01=$normalize01 inputQuantized=$isInputQuantized outputQuantized=$isOutputQuantized")
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

        if (isOutputQuantized) {
            val outputBuffer = Array(1) { ByteArray(embeddingDim) }
            interpreter!!.run(inputBuffer, outputBuffer)
            val quant = interpreter!!.getOutputTensor(0).quantizationParams()
            val zeroPoint = quant.zeroPoint.toFloat()
            val scale = quant.scale
            val raw = FloatArray(embeddingDim)
            for (i in 0 until embeddingDim) {
                val uint8 = outputBuffer[0][i].toInt() and 0xFF
                raw[i] = (uint8 - zeroPoint) * scale
            }
            return l2Normalize(raw)
        } else {
            val outputBuffer = Array(1) { FloatArray(embeddingDim) }
            interpreter!!.run(inputBuffer, outputBuffer)
            return l2Normalize(outputBuffer[0])
        }
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
     * Useful for multi-frame registration: embed 5 frames → centroid → store.
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

    /**
     * Preprocess Bitmap into ByteBuffer matching model input type.
     * - Float models: normalized to [0, 1] (pixel/255) or [-1, 1] based on isNormalized01
     * - Quantized models: raw uint8 [0, 255]
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        if (isInputQuantized) {
            val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3)
            for (pixel in pixels) {
                buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
                buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
                buffer.put((pixel and 0xFF).toByte())           // B
            }
            resized.recycle()
            return buffer
        } else {
            val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val divisor = if (isNormalized01) 255.0f else 127.5f
            val offset = if (isNormalized01) 0.0f else -1.0f
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / divisor + offset
                val g = ((pixel shr 8) and 0xFF) / divisor + offset
                val b = (pixel and 0xFF) / divisor + offset
                buffer.putFloat(r)
                buffer.putFloat(g)
                buffer.putFloat(b)
            }
            resized.recycle()
            return buffer
        }
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
    fun isModelQuantized(): Boolean = isInputQuantized

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
