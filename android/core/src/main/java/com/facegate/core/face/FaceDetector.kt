package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Unified face detector — YOLOv8 Face (primary) + ML Kit (fallback).
 *
 * Pipeline:
 *   1. YOLOv8 Face TFLite (~98% akurasi) — jika model yolov8n_face.tflite ada di assets/
 *   2. ML Kit FaceDetection (~95% akurasi) — fallback jika YOLO gagal/tidak tersedia
 */
class FaceDetectorWrapper(private val context: Context? = null) {
    private val yoloDetector: YoloV8FaceDetector? by lazy {
        context?.let { YoloV8FaceDetector(it) }
    }

    private val mlKitDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private var isInitialized = false
    private var lastError: String? = null

    val yoloAvailable: Boolean get() = yoloDetector?.isAvailable() == true

    fun init() {
        isInitialized = true
        lastError = null
        Log.i("FaceDetect", if (yoloAvailable) "YOLOv8 Face + ML Kit fallback" else "ML Kit only")
    }

    /** Detect from CameraX Image. */
    fun detectImage(image: Image, rotationDegrees: Int): FaceDetectionResult? {
        if (!isInitialized) return null
        lastError = null

        // 1) YOLOv8
        if (yoloAvailable) {
            try {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    val dets = runBlocking { yoloDetector?.detect(bitmap) }
                    if (!dets.isNullOrEmpty()) {
                        val best = dets.maxByOrNull { it.confidence }!!
                        val isRot = rotationDegrees == 90 || rotationDegrees == 270
                        return yoloToResult(
                            if (isRot) image.height.toFloat() else image.width.toFloat(),
                            if (isRot) image.width.toFloat() else image.height.toFloat(),
                            best
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("FaceDetect", "YOLO fail → ML Kit: ${e.message}")
            }
        }

        // 2) ML Kit fallback
        return try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            val faces = com.google.android.gms.tasks.Tasks.await(mlKitDetector.process(inputImage))
            if (faces.isEmpty()) return null
            val isRot = rotationDegrees == 90 || rotationDegrees == 270
            toResult(
                if (isRot) image.height.toFloat() else image.width.toFloat(),
                if (isRot) image.width.toFloat() else image.height.toFloat(),
                faces
            )
        } catch (e: Exception) {
            lastError = e.message; null
        }
    }

    /** Detect from Bitmap. */
    fun detectSync(bitmap: Bitmap): FaceDetectionResult? {
        if (!isInitialized) return null
        lastError = null

        if (yoloAvailable) {
            try {
                val dets = runBlocking { yoloDetector?.detect(bitmap) }
                if (!dets.isNullOrEmpty()) {
                    return yoloToResult(bitmap.width.toFloat(), bitmap.height.toFloat(),
                        dets.maxByOrNull { it.confidence }!!)
                }
            } catch (_: Exception) { }
        }

        return try {
            val faces = com.google.android.gms.tasks.Tasks.await(
                mlKitDetector.process(InputImage.fromBitmap(bitmap, 0)))
            if (faces.isEmpty()) null else toResult(bitmap.width.toFloat(), bitmap.height.toFloat(), faces)
        } catch (e: Exception) { lastError = e.message; null }
    }

    fun release() { isInitialized = false; yoloDetector?.release(); mlKitDetector.close() }
    fun getLastError(): String? = lastError

    // ─── Internal helpers ───

    private fun yoloToResult(w: Float, h: Float, d: YoloV8FaceDetector.YoloDetection): FaceDetectionResult {
        val b = d.bbox
        val r = Rect(b.left.toInt().coerceAtLeast(0), b.top.toInt().coerceAtLeast(0),
                     b.right.toInt().coerceAtMost(w.toInt()), b.bottom.toInt().coerceAtMost(h.toInt()))
        val (leftEye, rightEye) = yoloToEyeContours(d)
        return FaceDetectionResult(w, h, r, 1f, 1f, leftEye, rightEye,
            0f, 0f, 0f, 0f, d.confidence, "yolov8")
    }

    private fun yoloToEyeContours(d: YoloV8FaceDetector.YoloDetection): Pair<List<PointF>, List<PointF>> {
        if (d.landmarks.size < 2) return Pair(emptyList(), emptyList())
        val le = d.landmarks[0]; val re = d.landmarks[1]; val s = 8f
        fun circle(cx: Float, cy: Float): List<PointF> = listOf(
            PointF(cx - s, cy), PointF(cx - s / 2, cy - s / 2), PointF(cx, cy - s),
            PointF(cx + s, cy), PointF(cx + s / 2, cy + s / 2), PointF(cx, cy + s))
        return circle(le.x, le.y) to circle(re.x, re.y)
    }

    private fun toResult(w: Float, h: Float, faces: List<Face>): FaceDetectionResult {
        val f = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
        return FaceDetectionResult(w, h, f.boundingBox,
            f.leftEyeOpenProbability ?: 1f, f.rightEyeOpenProbability ?: 1f,
            extract(f, 6), extract(f, 7),
            f.headEulerAngleY, f.headEulerAngleZ, f.headEulerAngleX,
            f.smilingProbability ?: 0f, 1f, "mlkit")
    }

    private fun extract(face: Face, t: Int): List<PointF> =
        face.getContour(t)?.points?.map { PointF(it.x, it.y) } ?: emptyList()

    /** Convert YUV_420_888 Image to ARGB_8888 Bitmap (proper YUV→RGB). */
    private fun imageToBitmap(img: Image): Bitmap? = try {
        val planes = img.planes
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer
        val yStride = planes[0].rowStride
        val uStride = planes[1].rowStride
        val vStride = planes[2].rowStride
        val uPixelStride = planes[1].pixelStride
        val vPixelStride = planes[2].pixelStride

        val w = img.width; val h = img.height
        val pixels = IntArray(w * h)
        val yRow = ByteArray(yStride)
        val uRow = ByteArray(uStride)
        val vRow = ByteArray(vStride)

        for (row in 0 until h) {
            yBuf.position(row * yStride)
            yBuf.get(yRow, 0, yStride)
            val uRowIdx = (row / 2) * uStride
            val vRowIdx = (row / 2) * vStride
            uBuf.position(uRowIdx); uBuf.get(uRow, 0, uStride)
            vBuf.position(vRowIdx); vBuf.get(vRow, 0, vStride)

            for (col in 0 until w) {
                val y = yRow[col].toInt() and 0xFF
                val uIdx = (col / 2) * uPixelStride
                val vIdx = (col / 2) * vPixelStride
                val u = (uRow[uIdx].toInt() and 0xFF) - 128
                val v = (vRow[vIdx].toInt() and 0xFF) - 128

                val r = (y + 1.402f * v).coerceIn(0f, 255f).toInt()
                val g = (y - 0.344f * u - 0.714f * v).coerceIn(0f, 255f).toInt()
                val b = (y + 1.772f * u).coerceIn(0f, 255f).toInt()
                pixels[row * w + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) { null }
}

/** Face detection result with comprehensive quality metadata. */
data class FaceDetectionResult(
    val imageWidth: Float,
    val imageHeight: Float,
    val boundingBox: Rect,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val leftEyeContour: List<PointF>,
    val rightEyeContour: List<PointF>,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val headEulerAngleX: Float = 0f,
    val smilingProbability: Float = 0f,
    val detectionConfidence: Float = 1f,        // YOLO/ML Kit confidence
    val detectorSource: String = "mlkit"          // "yolov8" or "mlkit"
) {
    val isGoodQuality: Boolean
        get() = kotlin.math.abs(headEulerAngleY) < 25f && kotlin.math.abs(headEulerAngleZ) < 25f
    val hasEyeContours: Boolean get() = leftEyeContour.size >= 6 && rightEyeContour.size >= 6
    val isWellPositioned: Boolean get() {
        val cx = boundingBox.exactCenterX() / imageWidth
        val cy = boundingBox.exactCenterY() / imageHeight
        val r = boundingBox.width().toFloat() * boundingBox.height().toFloat() / (imageWidth * imageHeight)
        return cx in 0.2f..0.8f && cy in 0.2f..0.8f && r >= 0.04f
    }
}

private fun <T> runBlocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }
