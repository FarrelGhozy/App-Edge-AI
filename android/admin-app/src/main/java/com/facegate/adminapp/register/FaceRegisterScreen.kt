package com.facegate.adminapp.register

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.core.face.FaceDetectionResult
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegisterScreen(
    studentId: String,
    navController: NavController,
    viewModel: FaceRegisterViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val cameraPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted.value = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            kotlinx.coroutines.delay(2000)
            navController.previousBackStackEntry?.savedStateHandle?.set("faceRegistered", true)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrasi Wajah") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!cameraPermissionGranted.value) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Izin kamera diperlukan",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Berikan Izin")
                    }
                }
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    enabled = state.step == FaceRegisterStep.DETECTING ||
                            state.step == FaceRegisterStep.LIVENESS,
                    onFrameCaptured = { imageProxy ->
                        viewModel.onFrameCaptured(imageProxy, studentId)
                    }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    FaceOvalOverlay(state = state)
                    val det = state.detection
                    if (det != null) {
                        EyeDebugOverlay(
                            detection = det,
                            imageWidth = det.imageWidth,
                            imageHeight = det.imageHeight
                        )
                    }
                    DebugTextOverlay(detection = state.detection)
                }
            }

            when (state.step) {
                FaceRegisterStep.SUCCESS -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                state.message,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                FaceRegisterStep.ERROR -> {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                state.error ?: "Terjadi kesalahan",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFrameCaptured: (ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEnabled = remember { mutableStateOf(true) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val frameCount = remember { mutableIntStateOf(0) }

    LaunchedEffect(enabled) { isEnabled.value = enabled }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                post {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .setTargetResolution(android.util.Size(640, 480))
                            .build()
                            .also { it.surfaceProvider = surfaceProvider }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyzer ->
                                analyzer.setAnalyzer(
                                    analyzerExecutor
                                ) { imageProxy ->
                                    try {
                                        if (!isEnabled.value) return@setAnalyzer
                                        frameCount.intValue = (frameCount.intValue + 1) % 2
                                        if (frameCount.intValue != 0) return@setAnalyzer
                                        onFrameCaptured(imageProxy)
                                    } catch (e: Exception) {
                                        Log.e("FaceRegister", "error", e)
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {
                        }
                    }, mainExecutor)
                }
            }
        },
        modifier = modifier
    )
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(imageProxy)
            else -> {
                val bmp = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
                bmp
            }
        }
    } catch (e: Exception) {
        Log.e("FaceRegister", "YUV→Bitmap gagal", e)
        null
    }
}

private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
    val planes = imageProxy.planes
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yRowStride = yPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vRowStride = vPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    val vPixelStride = vPlane.pixelStride

    val yData = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
    val uData = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.get(it) }
    val vData = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }

    val w = imageProxy.width
    val h = imageProxy.height
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)

    for (row in 0 until h) {
        val yRowOff = row * yRowStride
        val uvRow = row / 2
        val uRowOff = uvRow * uRowStride
        val vRowOff = uvRow * vRowStride

        for (col in 0 until w) {
            val y = yData[yRowOff + col * yPixelStride].toInt() and 0xFF
            val uvCol = col / 2
            val u = (uData[uRowOff + uvCol * uPixelStride].toInt() and 0xFF) - 128
            val v = (vData[vRowOff + uvCol * vPixelStride].toInt() and 0xFF) - 128

            val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
            val g = (y - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
            val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

            pixels[row * w + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    return bitmap
}

@Composable
private fun EyeDebugOverlay(
    detection: FaceDetectionResult?,
    imageWidth: Float,
    imageHeight: Float
) {
    if (detection == null) return

    val earText = remember(detection) {
        val leftEAR = calculateDebugEAR(detection.leftEyeContour)
        val rightEAR = calculateDebugEAR(detection.rightEyeContour)
        String.format("EAR L=%.2f R=%.2f", leftEAR, rightEAR)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        Log.d("EyeDebug", "canvas ${size.width.toInt()}x${size.height.toInt()} img ${imageWidth.toInt()}x${imageHeight.toInt()}")
        // FILL_CENTER: image fills canvas, centered, possibly cropped
        val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
        val imgW = imageWidth * scale
        val imgH = imageHeight * scale
        val offX = (size.width - imgW) / 2f
        val offY = (size.height - imgH) / 2f

        // Map from ML Kit rotated space → canvas space (mirror x for front camera preview)
        fun mx(x: Float) = (imageWidth - x) * scale + offX
        fun my(y: Float) = y * scale + offY

        // Helper: convert PointF to Offset
        fun pt(p: android.graphics.PointF) = Offset(mx(p.x), my(p.y))

        // Draw bounding box (mirror-safe: handle swapped left/right after mirror)
        val bb = detection.boundingBox
        val x1 = mx(bb.left.toFloat())
        val x2 = mx(bb.right.toFloat())
        val y1 = my(bb.top.toFloat())
        val y2 = my(bb.bottom.toFloat())
        drawRect(
            color = Color.Yellow,
            topLeft = Offset(minOf(x1, x2), minOf(y1, y2)),
            size = Size(abs(x2 - x1), abs(y2 - y1)),
            style = Stroke(width = 2.dp.toPx())
        )

        // Helper to draw EAR keypoints for one eye
        fun drawEARPoints(contour: List<android.graphics.PointF>) {
            if (contour.size < 14) return
            val pts = listOf(
                contour[0] to Color.Red,   // outer corner
                contour[2] to Color.Green,  // upper-outer
                contour[4] to Color.Green,  // upper-center
                contour[6] to Color.Green,  // upper-inner
                contour[8] to Color.Yellow, // inner corner
                contour[10] to Color.Cyan,  // lower-inner
                contour[12] to Color.Cyan,  // lower-center
                contour[14] to Color.Cyan,  // lower-outer
            )
            for ((p, c) in pts) {
                drawCircle(c, radius = 3.dp.toPx(), center = pt(p))
            }
            // Eye width
            drawLine(Color.White, pt(contour[0]), pt(contour[8]), strokeWidth = 1.dp.toPx())
            // Vertical pairs
            drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[2]), pt(contour[14]), strokeWidth = 1.dp.toPx())
            drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[4]), pt(contour[12]), strokeWidth = 1.dp.toPx())
            drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[6]), pt(contour[10]), strokeWidth = 1.dp.toPx())
        }

        drawEARPoints(detection.leftEyeContour)
        drawEARPoints(detection.rightEyeContour)

        // Draw all contour points (small dots)
        for (pt in detection.leftEyeContour) {
            drawCircle(Color.Cyan.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = pt(pt))
        }
        for (pt in detection.rightEyeContour) {
            drawCircle(Color.Magenta.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = pt(pt))
        }

        // EAR text
        drawContext.canvas.nativeCanvas.drawText(
            earText,
            20f,
            size.height - 20f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                isAntiAlias = true
            }
        )
    }
}

@Composable
private fun DebugTextOverlay(detection: FaceDetectionResult?) {
    val text = remember(detection) {
        if (detection != null) {
            String.format("FACE OK pts=%d,%d EAR: pending",
                detection.leftEyeContour.size, detection.rightEyeContour.size)
        } else {
            "NO FACE"
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            color = Color.Red,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

private fun calculateDebugEAR(contour: List<android.graphics.PointF>): Float {
    if (contour.size < 14) return 0f
    val outer = contour[0]; val inner = contour[8]
    val h = kotlin.math.sqrt((outer.x-inner.x)*(outer.x-inner.x) + (outer.y-inner.y)*(outer.y-inner.y))
    if (h < 0.001f) return 0f
    fun d(i: Int, j: Int) = kotlin.math.sqrt((contour[i].x-contour[j].x)*(contour[i].x-contour[j].x) + (contour[i].y-contour[j].y)*(contour[i].y-contour[j].y))
    val avgV = (d(2,14) + d(4,12) + d(6,10)) / 3f
    return avgV / h
}

@Composable
private fun FaceOvalOverlay(state: FaceRegisterState) {
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    val strokeColor = when (state.step) {
        FaceRegisterStep.DETECTING -> Color.White
        FaceRegisterStep.LIVENESS -> Color(0xFFFFC107)
        FaceRegisterStep.EMBEDDING -> Color(0xFF2196F3)
        FaceRegisterStep.UPLOADING -> Color(0xFF2196F3)
        FaceRegisterStep.SUCCESS -> Color(0xFF4CAF50)
        FaceRegisterStep.ERROR -> Color(0xFFE53935)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size
            val ovalWidth = canvasSize.width * 0.75f
            val ovalHeight = canvasSize.height * 0.50f
            val ovalTop = (canvasSize.height - ovalHeight) / 2
            val ovalLeft = (canvasSize.width - ovalWidth) / 2

            val path = Path().apply {
                addRect(Rect(0f, 0f, canvasSize.width, canvasSize.height))
                addOval(
                    Rect(ovalLeft, ovalTop, ovalLeft + ovalWidth, ovalTop + ovalHeight)
                )
            }

            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(overlayColor)
            }

            drawOval(
                color = strokeColor,
                topLeft = Offset(ovalLeft, ovalTop),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = state.message,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}


