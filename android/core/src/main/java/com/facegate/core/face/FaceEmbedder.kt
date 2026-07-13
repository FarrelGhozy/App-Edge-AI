package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FaceEmbedder(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 112
    private val embeddingDim = 128

    fun init(modelName: String = "mobilefacenet.tflite") {
        if (interpreter != null) return
        try {
            val modelBuffer = loadModelFile(modelName)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            interpreter = null
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun embed(bitmap: Bitmap): FloatArray {
        if (interpreter == null) init()
        val inputBuffer = preprocessBitmap(bitmap)
        val outputBuffer = Array(1) { FloatArray(embeddingDim) }
        interpreter?.run(inputBuffer, outputBuffer)
        return normalize(outputBuffer[0])
    }

    fun isReady(): Boolean = interpreter != null

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
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

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
