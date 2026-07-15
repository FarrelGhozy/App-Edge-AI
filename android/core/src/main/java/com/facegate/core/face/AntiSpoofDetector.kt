package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import kotlin.math.exp

/**
 * Anti-spoofing detection using MiniFASNet (Silent-Face-Anti-Spoofing).
 *
 * Uses ensemble of 2 models (scale 2.7 and scale 4.0) for robust
 * liveness detection against:
 *   - Printed photo attacks
 *   - Video replay attacks
 *   - Screen/cut-out attacks
 *
 * Reference: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
 * Ported from: shubham0204/OnDevice-Face-Recognition-Android/FaceSpoofDetector
 *
 * Model size: ~12 MB total (2× 5-6MB TFLite)
 * Inference time: ~15-25ms per face on modern CPU
 */
class AntiSpoofDetector(context: Context) {

    data class SpoofResult(
        val isSpoof: Boolean,       // true if attack/spoof detected
        val realConfidence: Float,  // 0-1, higher = more likely real
        val inferenceMs: Long
    )

    companion object {
        private const val TAG = "AntiSpoofDetector"
        private const val SCALE_1 = 2.7f
        private const val SCALE_2 = 4.0f
        private const val INPUT_DIM = 80
        private const val OUTPUT_DIM = 3
        private const val REAL_LABEL_INDEX = 1  // Model output at index 1 = "real face"
    }

    private val interpreter1: Interpreter
    private val interpreter2: Interpreter
    private val imageProcessor = ImageProcessor.Builder()
        .add(CastOp(DataType.FLOAT32))
        .build()

    init {
        val options = Interpreter.Options().apply {
            numThreads = 4
            useXNNPACK = true
        }

        interpreter1 = Interpreter(
            FileUtil.loadMappedFile(context, "anti_spoof_2_7.tflite"),
            options
        )
        interpreter2 = Interpreter(
            FileUtil.loadMappedFile(context, "anti_spoof_4_0.tflite"),
            options
        )
        Log.d(TAG, "Anti-spoof models loaded (2.7 + 4.0)")
    }

    /**
     * Detect if the face in the given crop is a real person or a spoof attack.
     *
     * @param frameImage Full camera frame bitmap
     * @param faceRect   Bounding box of the detected face within frameImage
     * @return SpoofResult with classification and confidence
     */
    suspend fun detectSpoof(
        frameImage: Bitmap,
        faceRect: Rect
    ): SpoofResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()

        // Crop at two scales and convert RGB → BGR (model expects BGR)
        val crop1 = cropAndToBgr(frameImage, faceRect, SCALE_1)
        val crop2 = cropAndToBgr(frameImage, faceRect, SCALE_2)

        // Run inference on both models
        val input1 = imageProcessor.process(TensorImage.fromBitmap(crop1)).buffer
        val input2 = imageProcessor.process(TensorImage.fromBitmap(crop2)).buffer
        val output1 = Array(1) { FloatArray(OUTPUT_DIM) }
        val output2 = Array(1) { FloatArray(OUTPUT_DIM) }

        interpreter1.run(input1, output1)
        interpreter2.run(input2, output2)

        // Softmax ensemble: average both model outputs
        val scores1 = softmax(output1[0])
        val scores2 = softmax(output2[0])
        val ensemble = scores1.zip(scores2) { a, b -> (a + b) / 2f }

        val predictedLabel = ensemble.indexOf(ensemble.maxOrNull()!!)
        val isSpoof = predictedLabel != REAL_LABEL_INDEX
        val confidence = ensemble[predictedLabel]

        val elapsed = System.currentTimeMillis() - startMs
        Log.d(TAG, "Spoof result: isSpoof=$isSpoof confidence=%.3f realScore=%.3f time=%dms"
            .format(confidence, ensemble[REAL_LABEL_INDEX], elapsed))

        SpoofResult(
            isSpoof = isSpoof,
            realConfidence = ensemble[REAL_LABEL_INDEX],
            inferenceMs = elapsed
        )
    }

    private fun cropAndToBgr(
        orig: Bitmap,
        bbox: Rect,
        scale: Float
    ): Bitmap {
        val scaledBox = scaledBoundingBox(
            orig.width, orig.height, bbox, scale
        )
        val cropped = Bitmap.createBitmap(
            orig,
            scaledBox.left, scaledBox.top,
            scaledBox.width(), scaledBox.height()
        )
        val resized = Bitmap.createScaledBitmap(cropped, INPUT_DIM, INPUT_DIM, true)
        cropped.recycle()

        // Convert RGB → BGR (model was trained on BGR)
        for (x in 0 until resized.width) {
            for (y in 0 until resized.height) {
                val pixel = resized.getPixel(x, y)
                resized.setPixel(x, y, Color.rgb(
                    Color.blue(pixel),   // R ← B
                    Color.green(pixel),  // G stays
                    Color.red(pixel)     // B ← R
                ))
            }
        }
        return resized
    }

    private fun scaledBoundingBox(
        srcW: Int, srcH: Int,
        box: Rect, scale: Float
    ): Rect {
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val w = box.width()
        val h = box.height()
        val s = floatArrayOf(
            (srcH - 1f) / h,
            (srcW - 1f) / w,
            scale
        ).min()
        val newW = w * s
        val newH = h * s

        var l = (cx - newW / 2f).toInt()
        var t = (cy - newH / 2f).toInt()
        var r = (cx + newW / 2f).toInt()
        var b = (cy + newH / 2f).toInt()

        // Clamp to image bounds
        if (l < 0) { r -= l; l = 0 }
        if (t < 0) { b -= t; t = 0 }
        if (r > srcW) { l -= (r - srcW); r = srcW }
        if (b > srcH) { t -= (b - srcH); b = srcH }

        return Rect(l.coerceAtLeast(0), t.coerceAtLeast(0),
            r.coerceAtMost(srcW), b.coerceAtMost(srcH))
    }

    private fun softmax(x: FloatArray): FloatArray {
        val exps = x.map { exp(it.toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    fun release() {
        interpreter1.close()
        interpreter2.close()
        Log.d(TAG, "Anti-spoof models released")
    }
}
