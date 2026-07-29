package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * RetinaFace-500MF face detection via ONNX Runtime.
 *
 * Model: det_500m.onnx from InsightFace buffalo_sc pack.
 * Input:  [1, 3, H, W] float32 normalized [0,1]
 * Output:
 *   - bboxes:  [N, 4]  (x1, y1, x2, y2) on grid scale
 *   - scores:   [N, 1]  face confidence
 *   - landmarks:[N, 10] (5 keypoints × 2)
 */
class RetinaFaceDetector(private val context: Context) : FaceDetectorProvider {

    companion object {
        private const val TAG = "RetinaFace"
        private const val INPUT_SIZE = 640 // RetinaFace-500MF default
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.5f
        private const val INPUT_NAME = "input"
        private const val OUTPUT_BOXES = "output"
        private const val OUTPUT_SCORES = "output_1"
        private const val OUTPUT_LANDMARKS = "output_2"
    }

    private var session: OrtSession? = null
    private var ortEnv: ai.onnxruntime.OrtEnvironment? = null
    private var ready = false

    override fun init(): Boolean {
        if (ready) return true
        return try {
            val manager = OnnxRuntimeManager.getInstance(context)
            if (manager.state != OnnxRuntimeManager.RuntimeState.ONNX_READY) {
                if (!manager.init()) return false
            }
            session = manager.detectorSession
            ortEnv = manager.environment
            ready = session != null
            Log.d(TAG, "RetinaFace initialized: $ready")
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            ready = false
            false
        }
    }

    override fun detect(bitmap: Bitmap): List<FaceBox> {
        if (!ready) {
            if (!init()) return emptyList()
        }

        try {
            // 1. Preprocess: resize + normalize to [0,1] + NCHW layout
            val (inputTensor, scaleX, scaleY) = preprocess(bitmap)

            // 2. Run inference
            val inputMap = mapOf(INPUT_NAME to inputTensor)
            val results = session!!.run(inputMap)

            // 3. Parse output
            val boxes = (results.get(OUTPUT_BOXES)?.value as? Array<FloatArray>)?.toList() ?: emptyList()
            val scores = (results.get(OUTPUT_SCORES)?.value as? Array<FloatArray>)?.toList() ?: emptyList()
            val landmarks = (results.get(OUTPUT_LANDMARKS)?.value as? Array<FloatArray>)?.toList() ?: emptyList()

            // 4. Filter by confidence threshold
            val candidates = mutableListOf<RetinaFaceCandidate>()
            for (i in scores.indices) {
                val score = scores[i][0]
                if (score < CONFIDENCE_THRESHOLD) continue

                // Scale boxes from grid to original image coords
                val box = boxes[i]
                val x1 = box[0] * scaleX
                val y1 = box[1] * scaleY
                val x2 = box[2] * scaleX
                val y2 = box[3] * scaleY

                val landmark = if (i < landmarks.size) landmarks[i] else null
                val lmPoints = if (landmark != null) {
                    (0 until 5).map { idx ->
                        (landmark[idx * 2] * scaleX) to (landmark[idx * 2 + 1] * scaleY)
                    }
                } else emptyList()

                candidates.add(
                    RetinaFaceCandidate(
                        rect = Rect(
                            x1.toInt().coerceAtLeast(0),
                            y1.toInt().coerceAtLeast(0),
                            x2.toInt().coerceAtMost(bitmap.width),
                            y2.toInt().coerceAtMost(bitmap.height)
                        ),
                        confidence = score,
                        landmarks = lmPoints
                    )
                )
            }

            // 5. NMS
            val kept = nonMaxSuppression(candidates, NMS_THRESHOLD)

            // 6. Convert to FaceBox
            return kept.map { cand ->
                FaceBox(
                    boundingBox = cand.rect,
                    confidence = cand.confidence,
                    landmarks = cand.landmarks.map { Pair(it.first, it.second) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detect error: ${e.message}", e)
            return emptyList()
        }
    }

    private data class RetinaFaceCandidate(
        val rect: Rect,
        val confidence: Float,
        val landmarks: List<Pair<Float, Float>>
    )

    /**
     * Preprocess Bitmap → ONNX input tensor in NCHW format.
     * Returns: (tensor, scaleX, scaleY) where scaleX/Y maps grid coords → original image coords.
     */
    private fun preprocess(bitmap: Bitmap): Triple<OnnxTensor, Float, Float> {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val scaleX = bitmap.width.toFloat() / INPUT_SIZE
        val scaleY = bitmap.height.toFloat() / INPUT_SIZE

        // NCHW layout: [1, 3, H, W]
        val inputData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        var idx = 0
        for (row in 0 until INPUT_SIZE) {
            for (col in 0 until INPUT_SIZE) {
                val pixel = pixels[row * INPUT_SIZE + col]
                // Normalize [0,1]
                inputData[idx] = ((pixel shr 16) and 0xFF) / 255.0f      // R → channel 0
                inputData[INPUT_SIZE * INPUT_SIZE + idx] = ((pixel shr 8) and 0xFF) / 255.0f // G → channel 1
                inputData[2 * INPUT_SIZE * INPUT_SIZE + idx] = (pixel and 0xFF) / 255.0f    // B → channel 2
                idx++
            }
        }

        resized.recycle()

        val buffer = FloatBuffer.wrap(inputData)
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv!!, buffer, shape)

        return Triple(tensor, scaleX, scaleY)
    }

    /**
     * Non-Maximum Suppression: filter overlapping boxes, keep highest confidence.
     */
    private fun nonMaxSuppression(
        candidates: List<RetinaFaceCandidate>,
        iouThreshold: Float
    ): List<RetinaFaceCandidate> {
        val sorted = candidates.sortedByDescending { it.confidence }
        val result = mutableListOf<RetinaFaceCandidate>()

        for (candidate in sorted) {
            var suppressed = false
            for (kept in result) {
                val iou = computeIoU(candidate.rect, kept.rect)
                if (iou > iouThreshold) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) {
                result.add(candidate)
            }
        }

        return result
    }

    private fun computeIoU(a: Rect, b: Rect): Float {
        val intersectLeft = maxOf(a.left, b.left)
        val intersectTop = maxOf(a.top, b.top)
        val intersectRight = minOf(a.right, b.right)
        val intersectBottom = minOf(a.bottom, b.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) return 0f

        val intersectArea = (intersectRight - intersectLeft).toLong() * (intersectBottom - intersectTop).toLong()
        val areaA = a.width().toLong() * a.height().toLong()
        val areaB = b.width().toLong() * b.height().toLong()
        val unionArea = areaA + areaB - intersectArea

        return if (unionArea > 0) intersectArea.toFloat() / unionArea.toFloat() else 0f
    }

    override fun release() {
        // Session managed by OnnxRuntimeManager
        ready = false
    }

    override fun isReady(): Boolean = ready
}
