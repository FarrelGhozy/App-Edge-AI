package com.facegate.kioskscanner.scanner

import android.util.Log
import android.util.Size as AndroidSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.facegate.core.face.FaceDetectionResult
import com.facegate.kioskscanner.scanner.ScannerViewModel.UIState
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val debugDetection by viewModel.debugDetection.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewWithAnalysis(
            modifier = Modifier.fillMaxSize(),
            enabled = state is UIState.Idle && !isProcessing,
            onFrameCaptured = { imageProxy ->
                viewModel.onFrameCaptured(imageProxy)
            }
        )

        // Debug overlay: bounding box + eye landmarks
        EyeDebugOverlay(
            detection = debugDetection,
            imageWidth = debugDetection?.imageWidth ?: 640f,
            imageHeight = debugDetection?.imageHeight ?: 480f
        )

        // Debug text: NO FACE / FACE OK
        DebugTextOverlay(detection = debugDetection)

        // Result overlay (success/error)
        ResultOverlay(state = state)

        // "Arahkan wajah" hint
        if (state is UIState.Idle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Arahkan wajah ke kamera",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Sync button (top-right)
        IconButton(
            onClick = { viewModel.syncNow() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .size(48.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Pull data dari server",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Sync status text
        if (syncStatus != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 100.dp, end = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = syncStatus!!,
                    color = if (syncStatus!!.startsWith("OK")) Color(0xFF4CAF50) else Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }

    AutoDismissEffect(state = state, onDismiss = { viewModel.resetState() })
}

@Composable
fun CameraPreviewWithAnalysis(
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
                            .setTargetResolution(AndroidSize(640, 480))
                            .build()
                            .also { it.surfaceProvider = surfaceProvider }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(AndroidSize(320, 240))
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
                                        Log.e("Scanner", "error", e)
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
                        } catch (e: Exception) {
                            // Camera binding failed
                        }
                    }, mainExecutor)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ResultOverlay(state: UIState) {
    when (state) {
        is UIState.Idle -> {}
        is UIState.Success -> {
            val bgColor = if (state.isViolation) Color(0xFFFFA000) else Color(0xFF4CAF50)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.studentName,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.actionLabel,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.message != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
        is UIState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE53935).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Wajah Tidak Dikenal",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// ── Debug overlays (sama seperti FaceRegisterScreen) ──

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
        String.format("EAR L=%.2f R=%.2f blink=%d q=%.2f", leftEAR, rightEAR,
            detection.leftEyeContour.size, detection.headEulerAngleY)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
        val imgW = imageWidth * scale
        val imgH = imageHeight * scale
        val offX = (size.width - imgW) / 2f
        val offY = (size.height - imgH) / 2f

        fun mx(x: Float) = (imageWidth - x) * scale + offX
        fun my(y: Float) = y * scale + offY
        fun pt(p: android.graphics.PointF) = Offset(mx(p.x), my(p.y))

        // Bounding box
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

        // Eye contour landmarks
        drawEyePoints(detection.leftEyeContour, ::pt)
        drawEyePoints(detection.rightEyeContour, ::pt)

        // EAR text
        drawContext.canvas.nativeCanvas.drawText(
            earText,
            20f,
            size.height - 20f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
                setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyePoints(
    contour: List<android.graphics.PointF>,
    pt: (android.graphics.PointF) -> Offset
) {
    if (contour.size < 14) return
    // Key landmarks
    val keyPts = listOf(
        contour[0] to Color.Red,
        contour[2] to Color.Green,
        contour[4] to Color.Green,
        contour[6] to Color.Green,
        contour[8] to Color.Yellow,
        contour[10] to Color.Cyan,
        contour[12] to Color.Cyan,
        contour[14] to Color.Cyan,
    )
    for ((p, c) in keyPts) {
        drawCircle(c, radius = 3.dp.toPx(), center = pt(p))
    }
    // Eye width line
    drawLine(Color.White, pt(contour[0]), pt(contour[8]), strokeWidth = 1.dp.toPx())
    // Vertical pairs
    drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[2]), pt(contour[14]), strokeWidth = 1.dp.toPx())
    drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[4]), pt(contour[12]), strokeWidth = 1.dp.toPx())
    drawLine(Color.Green.copy(alpha = 0.5f), pt(contour[6]), pt(contour[10]), strokeWidth = 1.dp.toPx())
}

@Composable
private fun DebugTextOverlay(detection: FaceDetectionResult?) {
    val text = remember(detection) {
        if (detection != null) {
            "FACE OK  pts=${detection.leftEyeContour.size},${detection.rightEyeContour.size}  yaw=${"%.1f".format(detection.headEulerAngleY)}"
        } else {
            "NO FACE"
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            color = Color.Red,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        )
    }
}

@Composable
fun AutoDismissEffect(state: UIState, onDismiss: () -> Unit) {
    LaunchedEffect(state) {
        if (state !is UIState.Idle) {
            delay(3000)
            onDismiss()
        }
    }
}

private fun calculateDebugEAR(contour: List<android.graphics.PointF>): Float {
    if (contour.size < 14) return 0f
    val outer = contour[0]; val inner = contour[8]
    val h = kotlin.math.sqrt((outer.x - inner.x) * (outer.x - inner.x) + (outer.y - inner.y) * (outer.y - inner.y))
    if (h < 0.001f) return 0f
    fun d(i: Int, j: Int) = kotlin.math.sqrt(
        (contour[i].x - contour[j].x) * (contour[i].x - contour[j].x) +
        (contour[i].y - contour[j].y) * (contour[i].y - contour[j].y)
    )
    val avgV = (d(2, 14) + d(4, 12) + d(6, 10)) / 3f
    return avgV / h
}
