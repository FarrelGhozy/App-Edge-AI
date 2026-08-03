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
 * Input:  [1, 3, H, W] float32, normalized (pixel - 127.5) / 128.0, RGB
 * Output: 9 tensors, 3 scales (stride 8/16/32) × (scores [N,1], boxes [N,4], landmarks [N,10])
 *   with N = (640/stride)^2 × 2 anchors: 12800, 3200, 800.
 *
 * Decode follows InsightFace reference (retinaface.py):
 *   anchor_centers = meshgrid(x, y) * stride, duplicated ×2 anchors
 *   bbox = distance2bbox(centers, preds*stride)
 *   kps  = distance2kps(centers, preds*stride)
 */
class RetinaFaceDetector(private val context: Context) : FaceDetectorProvider {

    companion object {
        private const val TAG = "RetinaFace"
        private const val INPUT_SIZE = 640 // RetinaFace-500MF default
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.5f
        private const val INPUT_NAME = "input.1"
<<<<<<< HEAD
        // 3 FPN levels × 3 heads (scores, boxes, landmarks) = 9 outputs
        private val OUTPUT_SCORES = listOf("443", "468", "493")  // [12800,1], [3200,1], [800,1]
        private val OUTPUT_BOXES = listOf("446", "471", "496")   // [12800,4], [3200,4], [800,4]
        private val OUTPUT_LANDMARKS = listOf("449", "474", "499") // [12800,10], [3200,10], [800,10]
=======
        private const val INPUT_MEAN = 127.5f
        private const val INPUT_STD = 128.0f
        private val FEAT_STRIDES = intArrayOf(8, 16, 32)
        private const val NUM_ANCHORS = 2
        private const val NUM_KPS = 5
>>>>>>> 1bc714a (fix(core,kiosk): fix ONNX pipeline build failure (#58) + duplicate Hilt bindings (#74))
    }

    private var session: OrtSession? = null
    private var ortEnv: ai.onnxruntime.OrtEnvironment? = null
    private var ready = false

    // Output tensor names ordered by scale (stride 8, 16, 32)
    private var scoreNames: List<String> = emptyList()
    private var boxNames: List<String> = emptyList()
    private var kpsNames: List<String> = emptyList()

    override fun init(): Boolean {
        if (ready) return true
        return try {
            val manager = OnnxRuntimeManager.getInstance(context)
            if (manager.state != OnnxRuntimeManager.RuntimeState.ONNX_READY) {
                if (!manager.init()) return false
            }
            session = manager.detectorSession
            ortEnv = manager.environment
            if (session == null) return false

            // Map outputs by shape: [N,1]=scores, [N,4]=boxes, [N,10]=landmarks,
                        // ordered by row count desc (12800 → stride 8, 3200 → stride 16, 800 → stride 32)
                        val outputInfo = session!!.getOutputInfo()
                        fun rowsOf(entry: Map.Entry<String, ai.onnxruntime.NodeInfo>): Long =
                            (entry.value.info as? ai.onnxruntime.TensorInfo)?.shape?.getOrElse(0) { -1L } ?: -1L
                        fun colsOf(entry: Map.Entry<String, ai.onnxruntime.NodeInfo>): Long =
                            (entry.value.info as? ai.onnxruntime.TensorInfo)?.shape?.getOrElse(1) { -1L } ?: -1L

                        scoreNames = outputInfo.entries.filter { colsOf(it) == 1L }
                            .sortedByDescending { rowsOf(it) }.map { it.key }
                        boxNames = outputInfo.entries.filter { colsOf(it) == 4L }
                            .sortedByDescending { rowsOf(it) }.map { it.key }
                        kpsNames = outputInfo.entries.filter { colsOf(it) == 10L }
                            .sortedByDescending { rowsOf(it) }.map { it.key }

            if (scoreNames.size != 3 || boxNames.size != 3 || kpsNames.size != 3) {
                Log.e(TAG, "Unexpected output layout: scores=${scoreNames.size} boxes=${boxNames.size} kps=${kpsNames.size}")
                return false
            }

            ready = true
            Log.d(TAG, "RetinaFace initialized: $ready (outputs: $scoreNames)")
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

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            // 1. Preprocess: resize + normalize to (pix-127.5)/128 + NCHW layout
            val (tensor, scaleX, scaleY) = preprocess(bitmap)
            inputTensor = tensor

            // 2. Run inference
            val inputMap = mapOf(INPUT_NAME to inputTensor)
            results = session!!.run(inputMap)

<<<<<<< HEAD
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
=======
            // 3. Parse + decode each scale (InsightFace reference)
            val candidates = mutableListOf<RetinaFaceCandidate>()
            for (scaleIdx in 0 until 3) {
                val stride = FEAT_STRIDES[scaleIdx]

                val scoreBuf = readTensorFloatBuffer(results, scoreNames[scaleIdx]) ?: continue
                val boxBuf = readTensorFloatBuffer(results, boxNames[scaleIdx]) ?: continue
                val kpsBuf = readTensorFloatBuffer(results, kpsNames[scaleIdx]) ?: continue

                val height = INPUT_SIZE / stride
                val width = INPUT_SIZE / stride
                val k = height * width
                val totalAnchors = k * NUM_ANCHORS

                if (scoreBuf.remaining() < totalAnchors) {
                    Log.w(TAG, "Scale $stride: score buffer ${scoreBuf.remaining()} < $totalAnchors")
                    continue
                }
                // DEBUG: report max score per scale
                var maxScore = -1f
                for (i in 0 until scoreBuf.remaining()) {
                    val v = scoreBuf[i]
                    if (v > maxScore) maxScore = v
                }
                Log.d(TAG, "[$stride] buffer=${scoreBuf.remaining()} maxScore=$maxScore")

                // Anchor centers: meshgrid(x, y) * stride, duplicated ×2 anchors
                // order matches insightface: np.stack([c]*2, axis=1).reshape(-1,2) → c0,c0,c1,c1,...
                val centers = FloatArray(totalAnchors * 2)
                var ci = 0
                for (row in 0 until height) {
                    for (col in 0 until width) {
                        val cx = (col * stride).toFloat()
                        val cy = (row * stride).toFloat()
                        repeat(NUM_ANCHORS) {
                            centers[ci++] = cx
                            centers[ci++] = cy
                        }
>>>>>>> 1bc714a (fix(core,kiosk): fix ONNX pipeline build failure (#58) + duplicate Hilt bindings (#74))
                    }
                }

                for (i in 0 until totalAnchors) {
                    val score = scoreBuf[i]
                    if (score < CONFIDENCE_THRESHOLD) continue

                    val cx = centers[i * 2]
                    val cy = centers[i * 2 + 1]

                    // distance2bbox: x1=cx-d0, y1=cy-d1, x2=cx+d2, y2=cy+d3 (preds scaled by stride)
                    val x1 = cx - boxBuf[i * 4 + 0] * stride
                    val y1 = cy - boxBuf[i * 4 + 1] * stride
                    val x2 = cx + boxBuf[i * 4 + 2] * stride
                    val y2 = cy + boxBuf[i * 4 + 3] * stride

                    // Scale from 640-grid coords → original bitmap coords
                    val rect = Rect(
                        (x1 * scaleX).toInt().coerceAtLeast(0),
                        (y1 * scaleY).toInt().coerceAtLeast(0),
                        (x2 * scaleX).toInt().coerceAtMost(bitmap.width),
                        (y2 * scaleY).toInt().coerceAtMost(bitmap.height)
                    )
                    if (rect.width() <= 0 || rect.height() <= 0) continue

                    // distance2kps: px = cx + d[2i], py = cy + d[2i+1] (scaled by stride)
                    val landmarks = ArrayList<Pair<Float, Float>>(NUM_KPS)
                    for (k in 0 until NUM_KPS) {
                        val px = (cx + kpsBuf[i * 10 + k * 2] * stride) * scaleX
                        val py = (cy + kpsBuf[i * 10 + k * 2 + 1] * stride) * scaleY
                        landmarks.add(px to py)
                    }

                    candidates.add(
                        RetinaFaceCandidate(
                            rect = rect,
                            confidence = score,
                            landmarks = landmarks
                        )
                    )
                }
            }

<<<<<<< HEAD
            Log.d(TAG, "After decoding: ${candidates.size} candidates pass threshold=$CONFIDENCE_THRESHOLD")
            if (candidates.isNotEmpty()) {
                val c = candidates.first()
                Log.d(TAG, "Best candidate: conf=${"%.4f".format(c.confidence)} rect=${c.rect}")
            }

            // 5. NMS
=======
            // 4. NMS across all scales
>>>>>>> 1bc714a (fix(core,kiosk): fix ONNX pipeline build failure (#58) + duplicate Hilt bindings (#74))
            val kept = nonMaxSuppression(candidates, NMS_THRESHOLD)

            // 5. Convert to FaceBox
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
        } finally {
            try { results?.close() } catch (_: Exception) {}
            try { inputTensor?.close() } catch (_: Exception) {}
        }
    }

    private fun readTensorFloatBuffer(results: OrtSession.Result, name: String): FloatBuffer? {
        return try {
            // NOTE: Result.get(String) returns Optional<OnnxValue> in ONNX Runtime 1.20
            (results.get(name).orElse(null) as? OnnxTensor)?.floatBuffer
        } catch (e: Exception) {
            null
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
                // Normalize (pixel - 127.5) / 128.0 — InsightFace convention
                inputData[idx] = (((pixel shr 16) and 0xFF) - INPUT_MEAN) / INPUT_STD       // R → channel 0
                inputData[INPUT_SIZE * INPUT_SIZE + idx] = (((pixel shr 8) and 0xFF) - INPUT_MEAN) / INPUT_STD // G → channel 1
                inputData[2 * INPUT_SIZE * INPUT_SIZE + idx] = ((pixel and 0xFF) - INPUT_MEAN) / INPUT_STD    // B → channel 2
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
