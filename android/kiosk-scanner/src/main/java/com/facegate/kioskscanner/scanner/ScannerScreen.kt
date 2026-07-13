package com.facegate.kioskscanner.scanner

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewWithAnalysis(
            modifier = Modifier.fillMaxSize(),
            enabled = state is UIState.Idle && !isProcessing,
            onFrameCaptured = { bitmap ->
                viewModel.onFrameCaptured(bitmap)
            }
        )

        ResultOverlay(state = state)

        if (state is UIState.Idle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
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
    }

    AutoDismissEffect(state = state, onDismiss = { viewModel.resetState() })
}

@Composable
fun CameraPreviewWithAnalysis(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFrameCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEnabled = remember { mutableStateOf(true) }

    LaunchedEffect(enabled) { isEnabled.value = enabled }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                post {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .setTargetResolution(Size(640, 480))
                            .build()
                            .also { it.surfaceProvider = surfaceProvider }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyzer ->
                                analyzer.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx)
                                ) { imageProxy ->
                                    if (isEnabled.value) {
                                        val bitmap = imageProxy.toBitmap()
                                        if (bitmap != null) {
                                            onFrameCaptured(bitmap)
                                        }
                                    }
                                    imageProxy.close()
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
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        },
        modifier = modifier
    )
}

private fun ImageProxy.toBitmap(): Bitmap? {
    return try {
        when (format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap()
            else -> {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(planes[0].buffer)
                bitmap
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun ImageProxy.yuv420ToBitmap(): Bitmap {
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

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (row in 0 until height) {
        val yRowOff = row * yRowStride
        val uvRow = row / 2
        val uRowOff = uvRow * uRowStride
        val vRowOff = uvRow * vRowStride

        for (col in 0 until width) {
            val y = yData[yRowOff + col * yPixelStride].toInt() and 0xFF
            val uvCol = col / 2
            val u = (uData[uRowOff + uvCol * uPixelStride].toInt() and 0xFF) - 128
            val v = (vData[vRowOff + uvCol * vPixelStride].toInt() and 0xFF) - 128

            val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
            val g = (y - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
            val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

            pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
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

@Composable
fun AutoDismissEffect(state: UIState, onDismiss: () -> Unit) {
    LaunchedEffect(state) {
        if (state !is UIState.Idle) {
            delay(3000)
            onDismiss()
        }
    }
}
