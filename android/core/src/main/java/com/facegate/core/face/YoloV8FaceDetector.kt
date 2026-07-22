package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * YOLOv8 Face detector — primary face detector untuk FaceGate.
 *
 * Model: yolov8n-face.tflite (quantized int8/f16, ~6-8 MB)
 * Sumber: https://huggingface.co/arnabdhar/YOLOv8-Face-Detection
 *         atau export dari ultralytics: yolo export model=yolov8n-face.pt format=tflite
 *
 * Input:  [1, 640, 640, 3] — RGB, normalized [0, 1]
 * Output: [1, 16, 8400]   — 8400 anchors × 16 values per anchor
 *                           [cx, cy, w, h, conf, x1, y1, x2, y2, l1x, l1y, l2x, l2y, l3x, l3y, cls]
 *
 * Akurasi: 98-99% (vs ML Kit 95-97%)
 * Latency: ~15-25ms per frame (vs ML Kit ~5-10ms)
 */
class YoloV8FaceDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private var isLoaded = false

    data class YoloDetection(
        val bbox: RectF,
        val confidence: Float,
        val landmarks: List<PointF> = emptyList()
    )

    companion object {
        private const val TAG = "YoloV8Face"
        private const val MODEL_FILE = "yolov8n_face.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.60f  // YOLO raw confidence
        private const val NMS_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 10

        // Output indices
        private const val IDX_CX = 0
        private const val IDX_CY = 1
        private const val IDX_W = 2
        private const val IDX_H = 3
        private const val IDX_CONF = 4
        private const val IDX_L1X = 5
        private const val IDX_L1Y = 6
        private const val IDX_L2X = 7
        private const val IDX_L2Y = 8
        private const val IDX_L3X = 9
        private const val IDX_L3Y = 10
        private const val IDX_L4X = 11
        private const val IDX_L4Y = 12
        private const val IDX_L5X = 13
        private const val IDX_L5Y = 14
        private const val IDX_CLS = 15
    }

    init {
        try {
            val modelBuffer = loadModelFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 4
                useXNNPACK = true
            }
            interpreter = Interpreter(modelBuffer, options)
            isLoaded = true
            Log.d(TAG, "YOLOv8 Face model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "YOLOv8 Face model not found: ${e.message}. Will fallback to ML Kit.")
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded && interpreter != null

    /**
     * Detect faces in a bitmap frame.
     * @return list of YoloDetection with bbox, confidence, landmarks
     */
    suspend fun detect(bitmap: Bitmap): List<YoloDetection> = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext emptyList()

        val startMs = System.currentTimeMillis()
        val inputBuffer = preprocess(bitmap)

        // Output shape: [1, 16, 8400]
        val output = Array(1) { Array(16) { FloatArray(8400) } }
        interpreter?.run(inputBuffer, output)

        val rawDetections = output[0]
        val boxes = mutableListOf<YoloDetection>()

        // Parse 8400 anchors
        for (i in 0 until 8400) {
            val conf = sigmoid(rawDetections[IDX_CONF][i])
            if (conf < CONFIDENCE_THRESHOLD) continue

            // Decode bbox (center format → corner format)
            val cx = rawDetections[IDX_CX][i]
            val cy = rawDetections[IDX_CY][i]
            val w = rawDetections[IDX_W][i]
            val h = rawDetections[IDX_H][i]

            // Scale from [0,1] to image coords (640x640 model input)
            val x1 = (cx - w / 2f) * INPUT_SIZE
            val y1 = (cy - h / 2f) * INPUT_SIZE
            val x2 = (cx + w / 2f) * INPUT_SIZE
            val y2 = (cy + h / 2f) * INPUT_SIZE

            // Scale to original bitmap size
            val scaleX = bitmap.width.toFloat() / INPUT_SIZE
            val scaleY = bitmap.height.toFloat() / INPUT_SIZE

            val landmarks = mutableListOf<PointF>()
            for (li in 0 until 5) {
                val lx = rawDetections[IDX_L1X + li * 2][i] * bitmap.width
                val ly = rawDetections[IDX_L1Y + li * 2][i] * bitmap.height
                landmarks.add(PointF(lx, ly))
            }

            boxes.add(
                YoloDetection(
                    bbox = RectF(
                        x1 * scaleX, y1 * scaleY,
                        x2 * scaleX, y2 * scaleY
                    ),
                    confidence = conf,
                    landmarks = landmarks
                )
            )
        }

        // Apply NMS
        val kept = nonMaxSuppression(boxes, NMS_THRESHOLD, MAX_DETECTIONS)
        val elapsed = System.currentTimeMillis() - startMs
        Log.d(TAG, "Detected ${kept.size} faces in ${elapsed}ms (${boxes.size} raw)")
        return@withContext kept
    }

    /**
     * Convert YOLO bbox to Android Rect
     */
    fun toRect(detection: YoloDetection): Rect {
        val b = detection.bbox
        return Rect(
            b.left.toInt().coerceAtLeast(0),
            b.top.toInt().coerceAtLeast(0),
            b.right.toInt().coerceAtMost(10000),
            b.bottom.toInt().coerceAtMost(10000)
        )
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat() / 255f)  // R
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat() / 255f)   // G
            buffer.putFloat((pixel and 0xFF).toFloat() / 255f)            // B
        }
        buffer.rewind()
        if (resized !== bitmap) resized.recycle()
        return buffer
    }

    private fun nonMaxSuppression(
        detections: List<YoloDetection>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<YoloDetection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val result = mutableListOf<YoloDetection>()

        for (det in sorted) {
            var suppressed = false
            for (kept in result) {
                if (computeIou(det.bbox, kept.bbox) > iouThreshold) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) {
                result.add(det)
                if (result.size >= maxDetections) break
            }
        }
        return result
    }

    private fun computeIou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        if (interLeft >= interRight || interTop >= interBottom) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val aFd = context.assets.openFd(modelName)
        val input = FileInputStream(aFd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, aFd.startOffset, aFd.declaredLength)
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        isLoaded = false
    }
}
