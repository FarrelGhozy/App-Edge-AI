package com.facegate.kioskscanner.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val faceOverlay by viewModel.faceOverlay.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val isFaceCentered by viewModel.isFaceCentered.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val imageSize by viewModel.imageSize.collectAsState()

    // Auto-dismiss after success/error so the kiosk returns to scan mode
    // without manual intervention (issue #69). 3s for success, 4s for error.
    LaunchedEffect(state) {
        when (state) {
            is ScannerViewModel.UIState.Success -> {
                delay(3000)
                viewModel.resetState()
            }
            is ScannerViewModel.UIState.Error -> {
                delay(4000)
                viewModel.resetState()
            }
            else -> {}
        }
    }

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

    // #92: deny-forever (Android 11+: "Don't ask again") — launcher langsung
    // return denied tanpa dialog. Satu-satunya jalan keluar: buka app settings.
    fun openAppSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Canvas size for coordinate transform
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Is front camera? Affects coordinate mirroring
    val isFrontCamera = true // default kiosk pakai front camera

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!cameraPermissionGranted.value) {
            // Permission denied — #92: kalau sudah di-deny forever (launcher tak
            // akan menampilkan dialog lagi), tawarkan "Buka Pengaturan" sebagai
            // satu-satunya jalan keluar; kalau masih bisa diminta ulang, tombol
            // "Berikan Izin" tetap pakai launcher.
            val activity = context as? android.app.Activity
            // #92: deny-forever — launcher tak tampilkan dialog jika sudah
            // diblokir permanen; sebaliknya anggap masih bisa diminta ulang.
            val permanentlyDenied = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.CAMERA
                    )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Izin kamera diperlukan", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (permanentlyDenied) {
                        "Kamera diblokir permanen. Buka Pengaturan untuk mengizinkan."
                    } else {
                        "Izinkan akses kamera untuk memindai wajah."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
                Spacer(Modifier.height(16.dp))
                if (permanentlyDenied) {
                    Button(onClick = { openAppSettings() }) {
                        Text("Buka Pengaturan")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Coba Lagi", color = Color.White.copy(alpha = 0.8f))
                    }
                } else {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Berikan Izin")
                    }
                }
            }
        } else {
            // ─── CameraX Preview ───
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            // #93: guard lama (`!isProcessing && state !is Success/Error`) adalah
                            // stale closure dari komposisi pertama (AndroidView factory jalan sekali)
                            // dan salah tipe — dihapus. Guard sebenarnya ada di dalam
                            // ScannerViewModel.onFrameCaptured yang memakai _state/_isProcessing
                            // internal (stateflow), jadi aman dari double-processing.
                            viewModel.onFrameCaptured(imageProxy)
                            imageProxy.close()
                        }
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                ctx as androidx.lifecycle.LifecycleOwner,
                                cameraSelector,
                                preview,
                                analyzer
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // ─── Face Bounding Box Overlay ───
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        canvasWidth = size.width.toFloat()
                        canvasHeight = size.height.toFloat()
                    }
            ) {
                val overlay = faceOverlay
                val rect = overlay.faceRect ?: return@Canvas

                // Transform image coords → canvas coords
                val imgW = if (imageSize.first > 0) imageSize.first.toFloat() else 640f
                val imgH = if (imageSize.second > 0) imageSize.second.toFloat() else 480f
                val scaleX = size.width / imgW
                val scaleY = size.height / imgH

                val canvasRect = if (isFrontCamera) {
                    // Mirror X for front camera
                    Rect(
                        ((imgW - rect.right) * scaleX).toInt(),
                        (rect.top * scaleY).toInt(),
                        ((imgW - rect.left) * scaleX).toInt(),
                        (rect.bottom * scaleY).toInt()
                    )
                } else {
                    Rect(
                        (rect.left * scaleX).toInt(),
                        (rect.top * scaleY).toInt(),
                        (rect.right * scaleX).toInt(),
                        (rect.bottom * scaleY).toInt()
                    )
                }

                val color = overlay.qualityColor
                val alpha = if (color == Color.Transparent) 0f else 1f

                // Draw bounding box
                drawRect(
                    color = color,
                    topLeft = Offset(canvasRect.left.toFloat(), canvasRect.top.toFloat()),
                    size = Size(canvasRect.width().toFloat(), canvasRect.height().toFloat()),
                    style = Stroke(width = 4.dp.toPx() * alpha)
                )

                // Draw corner accents (more visible)
                val cornerLen = 30.dp.toPx()
                val corners = listOf(
                    // Top-left
                    canvasRect.left.toFloat() to canvasRect.top.toFloat(),
                    // Top-right
                    canvasRect.right.toFloat() to canvasRect.top.toFloat(),
                    // Bottom-left
                    canvasRect.left.toFloat() to canvasRect.bottom.toFloat(),
                    // Bottom-right
                    canvasRect.right.toFloat() to canvasRect.bottom.toFloat()
                )
                // Simplified: just draw the box with thicker stroke for now
            }

            // ─── Status & Guidance Text ───
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress indicator selama video collection
                if (faceOverlay.isCollecting) {
                    LinearProgressIndicator(
                        progress = { faceOverlay.progress },
                        modifier = Modifier
                            .width(250.dp)
                            .height(6.dp)
                            .padding(bottom = 8.dp),
                        color = Color(0xFF2196F3),
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                    Text(
                        "${faceOverlay.collectedCount} / 15",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Status message
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Sync status
                val syncStatusText = syncStatus
                if (syncStatusText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        syncStatusText,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            // ─── Success / Error Overlay ───
            when (val s = state) {
                is ScannerViewModel.UIState.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                s.studentName,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                s.actionLabel,
                                color = Color(0xFF81C784),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (s.isViolation && s.message != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.message,
                                    color = Color(0xFFFFCDD2),
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            // Manual reset for gate operators (issue #69) — the
                            // overlay also auto-dismisses after 3s.
                            Button(
                                onClick = { viewModel.resetState() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                Text("Scan Berikutnya", color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
                is ScannerViewModel.UIState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCCB71C1C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                s.message,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.resetState() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                Text("Coba Lagi", color = Color(0xFFB71C1C))
                            }
                        }
                    }
                }
                else -> {}
            }

            // ─── Reset button (top-right corner) ───
            if (state is ScannerViewModel.UIState.Error || state is ScannerViewModel.UIState.Success) {
                // Handled above
            } else {
                IconButton(
                    onClick = { viewModel.syncNow() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    Text("↻", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}
