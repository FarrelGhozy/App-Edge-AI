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
        private const val INPUT_NAME = "input.1"
        // 3 FPN levels × 3 heads (scores, boxes, landmarks) = 9 outputs
        private val OUTPUT_SCORES = listOf("443", "468", "493")  // [12800,1], [3200,1], [800,1]
        private val OUTPUT_BOXES = listOf("446", "471", "496")   // [12800,4], [3200,4], [800,4]
        private val OUTPUT_LANDMARKS = listOf("449", "474", "499") // [12800,10], [3200,10], [800,10]
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

            // 3. Parse all 9 outputs (3 FPN levels)
            val allScores = mutableListOf<FloatArray>()
            val allBoxes = mutableListOf<FloatArray>()
            val allLandmarks = mutableListOf<FloatArray>()
            for (name in OUTPUT_SCORES) {
                results.get(name).orElse(null)?.let { v ->
                    Log.d(TAG, "Output $name type=${v.value?.javaClass?.name} value=${v.value?.javaClass?.componentType()}")
                    // Try 3D first, then 2D
                    val arr3d = v.value as? Array<Array<FloatArray>>
                    if (arr3d != null) {
                        arr3d.forEach { batch -> batch.forEach { row -> allScores.add(row) } }
                    } else {
                        val arr2d = v.value as? Array<FloatArray>
                        if (arr2d != null) {
                            arr2d.forEach { allScores.add(it) }
                        }
                    }
                }
            }
            for (name in OUTPUT_BOXES) {
                results.get(name).orElse(null)?.let { v ->
                    val arr3d = v.value as? Array<Array<FloatArray>>
                    if (arr3d != null) {
                        arr3d.forEach { batch -> batch.forEach { row -> allBoxes.add(row) } }
                    } else {
                        val arr2d = v.value as? Array<FloatArray>
                        if (arr2d != null) {
                            arr2d.forEach { allBoxes.add(it) }
                        }
                    }
                }
            }
            for (name in OUTPUT_LANDMARKS) {
                results.get(name).orElse(null)?.let { v ->
                    val arr3d = v.value as? Array<Array<FloatArray>>
                    if (arr3d != null) {
                        arr3d.forEach { batch -> batch.forEach { row -> allLandmarks.add(row) } }
                    } else {
                        val arr2d = v.value as? Array<FloatArray>
                        if (arr2d != null) {
                            arr2d.forEach { allLandmarks.add(it) }
                        }
                    }
                }
            }

            val numDetections = minOf(allScores.size, allBoxes.size, allLandmarks.size)
            Log.d(TAG, "Detect: $numDetections raw candidates (scores=${allScores.size}, boxes=${allBoxes.size}, lm=${allLandmarks.size})")

            // 4. Generate/retrieve prior anchors
            val anchors = getPriorAnchors()
            if (anchors.size != numDetections) {
                Log.w(TAG, "Anchor count mismatch: ${anchors.size} anchors vs $numDetections detections")
            }

            // 5. Decode anchor-relative box deltas → image coordinates, filter by threshold
            val candidates = mutableListOf<RetinaFaceCandidate>()
            val numToProcess = minOf(anchors.size, numDetections)
            for (i in 0 until numToProcess) {
                val score = allScores[i][0]
                if (score < CONFIDENCE_THRESHOLD) continue

                val anchor = anchors[i]
                val box = allBoxes[i]
                // Decode: cx = anchor_cx + dx * anchor_w, cy = anchor_cy + dy * anchor_h
                //         w = anchor_w * exp(dw), h = anchor_h * exp(dh)
                val dx = box[0]; val dy = box[1]; val dw = box[2]; val dh = box[3]
                // RetinaFace variance: [0.1, 0.2] — baked into the training targets
                val variance0 = 0.1f; val variance1 = 0.2f
                val cx = anchor.cx + dx * variance0 * anchor.w
                val cy = anchor.cy + dy * variance0 * anchor.h
                val w = anchor.w * kotlin.math.exp(dw * variance1)
                val h = anchor.h * kotlin.math.exp(dh * variance1)
                // Convert to x1,y1,x2,y2 in 640×640 input coords → scale to original bitmap coords
                val x1 = (cx - w / 2f) * scaleX
                val y1 = (cy - h / 2f) * scaleY
                val x2 = (cx + w / 2f) * scaleX
                val y2 = (cy + h / 2f) * scaleY

                val landmark = if (i < allLandmarks.size) allLandmarks[i] else null
                val lmPoints = if (landmark != null) {
                    (0 until 5).map { idx ->
                        // Landmarks also use variance[0]
                        val lx = (anchor.cx + landmark[idx * 2] * variance0 * anchor.w) * scaleX
                        val ly = (anchor.cy + landmark[idx * 2 + 1] * variance0 * anchor.h) * scaleY
                        lx to ly
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

            Log.d(TAG, "After decoding: ${candidates.size} candidates pass threshold=$CONFIDENCE_THRESHOLD")
            if (candidates.isNotEmpty()) {
                val c = candidates.first()
                Log.d(TAG, "Best candidate: conf=${"%.4f".format(c.confidence)} rect=${c.rect}")
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

    // RetinaFace-500MF anchor config (from InsightFace buffalo_sc)
    // 3 FPN levels, 2 square anchors per position
    // Stride 8:  anchors [16, 32], feature 80×80 → 12800
    // Stride 16: anchors [64, 128], feature 40×40 → 3200
    // Stride 32: anchors [256, 512], feature 20×20 → 800
    private val FPN_STRIDES = intArrayOf(8, 16, 32)
    private val FPN_MIN_SIZES = arrayOf(
        intArrayOf(16, 32),
        intArrayOf(64, 128),
        intArrayOf(256, 512)
    )
    // Prior anchors: pre-generated list of (cx, cy, w, h) for all 16800 positions
    private var priorAnchors: List<Anchor>? = null

    private data class Anchor(val cx: Float, val cy: Float, val w: Float, val h: Float)

    /** Generate all prior anchors once (cached). */
    private fun getPriorAnchors(): List<Anchor> {
        priorAnchors?.let { return it }
        val anchors = mutableListOf<Anchor>()
        for (level in FPN_STRIDES.indices) {
            val stride = FPN_STRIDES[level]
            val minSizes = FPN_MIN_SIZES[level]
            val featureSize = INPUT_SIZE / stride
            for (i in 0 until featureSize) {
                for (j in 0 until featureSize) {
                    val cx = (j + 0.5f) * stride
                    val cy = (i + 0.5f) * stride
                    for (size in minSizes) {
                        anchors.add(Anchor(cx, cy, size.toFloat(), size.toFloat()))
                    }
                }
            }
        }
        priorAnchors = anchors
        Log.d(TAG, "Generated ${anchors.size} prior anchors")
        return anchors
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
