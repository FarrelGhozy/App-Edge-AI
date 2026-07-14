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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.facegate.kioskscanner.scanner.ScannerViewModel.UIState
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val isFaceCentered by viewModel.isFaceCentered.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
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

        // Guide box overlay (kotak panduan di tengah)
        FaceGuideOverlay(
            isDetected = isFaceDetected,
            isCentered = isFaceCentered,
            statusMessage = statusMessage,
            state = state
        )

        // Result overlay (success/error)
        ResultOverlay(state = state)

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
                            Log.e("Scanner", "Camera binding failed: ${e.message}")
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

// ── Face Guide Overlay (kotak panduan di tengah) ──

@Composable
private fun FaceGuideOverlay(
    isDetected: Boolean,
    isCentered: Boolean,
    statusMessage: String,
    state: UIState
) {
    val guideColor = when {
        state is UIState.Success -> Color(0xFF4CAF50)
        state is UIState.Error -> Color(0xFFE53935)
        isDetected && isCentered -> Color(0xFF2196F3)
        isDetected -> Color(0xFFFFC107)
        else -> Color.White
    }
    val guideWidthRatio = 0.55f
    val guideHeightRatio = 0.45f

    Box(modifier = Modifier.fillMaxSize()) {
        // Dark overlay with cutout + guide border
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gw = size.width * guideWidthRatio
            val gh = size.height * guideHeightRatio
            val gl = (size.width - gw) / 2f
            val gt = (size.height - gh) / 2f

            val radius = CornerRadius(16.dp.toPx())
            val path = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        rect = Rect(gl, gt, gl + gw, gt + gh),
                        cornerRadius = radius
                    )
                )
            }
            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.45f))
            }

            drawRoundRect(
                color = guideColor,
                topLeft = Offset(gl, gt),
                size = Size(gw, gh),
                cornerRadius = radius,
                style = Stroke(width = 3.dp.toPx())
            )

            // Corner accents
            val cornerLen = 30.dp.toPx()
            val strokeW = 4.dp.toPx()
            // Top-left
            drawLine(guideColor, Offset(gl, gt + cornerLen), Offset(gl, gt), strokeW)
            drawLine(guideColor, Offset(gl, gt), Offset(gl + cornerLen, gt), strokeW)
            // Top-right
            drawLine(guideColor, Offset(gl + gw - cornerLen, gt), Offset(gl + gw, gt), strokeW)
            drawLine(guideColor, Offset(gl + gw, gt), Offset(gl + gw, gt + cornerLen), strokeW)
            // Bottom-left
            drawLine(guideColor, Offset(gl, gt + gh - cornerLen), Offset(gl, gt + gh), strokeW)
            drawLine(guideColor, Offset(gl, gt + gh), Offset(gl + cornerLen, gt + gh), strokeW)
            // Bottom-right
            drawLine(guideColor, Offset(gl + gw - cornerLen, gt + gh), Offset(gl + gw, gt + gh), strokeW)
            drawLine(guideColor, Offset(gl + gw, gt + gh - cornerLen), Offset(gl + gw, gt + gh), strokeW)
        }

        // Status message above guide
        if (statusMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    text = statusMessage,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
