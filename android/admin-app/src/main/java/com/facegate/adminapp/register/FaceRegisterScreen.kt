package com.facegate.adminapp.register

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.util.concurrent.Executors
import android.util.Log

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
                    onFrameCaptured = { bitmap ->
                        viewModel.onFrameCaptured(bitmap, studentId)
                    }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    FaceOvalOverlay(state = state)
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
    onFrameCaptured: (Bitmap) -> Unit
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
                                    if (!isEnabled.value) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    frameCount.intValue = (frameCount.intValue + 1) % 2
                                    if (frameCount.intValue != 0) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val bitmap = imageProxy.toBitmap()
                                    if (bitmap != null) {
                                        onFrameCaptured(bitmap)
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
                        } catch (_: Exception) {
                        }
                    }, mainExecutor)
                }
            }
        },
        modifier = modifier
    )
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


