package com.facegate.kioskscanner.registration

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Camera setup
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasTakenPhoto by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(android.view.Surface.ROTATION_0)
                .build()
            imageCapture.value = imageCaptureUseCase

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCaptureUseCase
                )
            } catch (e: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }

    // Handle success dialog
    if (state.successMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSuccess() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) },
            title = { Text("Registrasi Berhasil", fontWeight = FontWeight.Bold) },
            text = { Text(state.successMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSuccess(); onBack() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            "📝 Registrasi Santri Baru",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ambil foto wajah dan isi data santri",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // Camera preview / captured photo
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (state.capturedFace != null) {
                    // Show captured face
                    Image(
                        bitmap = state.capturedFace!!.asImageBitmap(),
                        contentDescription = "Captured Face",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    // Retake button overlay
                    IconButton(
                        onClick = { hasTakenPhoto = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Text("✕", color = Color.White)
                    }
                } else {
                    // Camera preview
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleX = -1f // Mirror for front camera
                                post {
                                    imageCapture.value?.let { capture ->
                                        // Take photo on tap
                                        setOnClickListener {
                                            capture.takePicture(
                                                ContextCompat.getMainExecutor(ctx),
                                                object : ImageCapture.OnImageCapturedCallback() {
                                                    override fun onCaptureSuccess(image: ImageProxy) {
                                                        val bitmap = imageProxyToBitmap(image)
                                                        image.close()
                                                        if (bitmap != null) {
                                                            val rotated = rotateBitmap(bitmap, 270f)
                                                            viewModel.captureFace(rotated)
                                                            hasTakenPhoto = true
                                                        }
                                                    }

                                                    override fun onError(e: ImageCaptureException) { }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Camera placeholder + capture hint
                    if (!hasTakenPhoto) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Tap untuk ambil foto", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // Face captured indicator
        if (state.faceVector != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("✅ Wajah terdeteksi (${state.faceVector!!.size} dimensi)",
                        fontSize = 13.sp, color = Color(0xFF2E7D32))
                }
            }
        }

        if (state.isCapturing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("Memproses wajah...", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))

        // Error message
        if (state.error != null) {
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    state.error!!,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFC62828),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Form fields
        OutlinedTextField(
            value = state.nim,
            onValueChange = viewModel::updateNim,
            label = { Text("NIM/NIS") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSubmitting
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            label = { Text("Nama Lengkap") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSubmitting,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.studyProgram,
                onValueChange = viewModel::updateStudyProgram,
                label = { Text("Program Studi") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !state.isSubmitting
            )
            OutlinedTextField(
                value = state.academicYear,
                onValueChange = viewModel::updateAcademicYear,
                label = { Text("Tahun Ajaran") },
                singleLine = true,
                placeholder = { Text("2024/2025") },
                modifier = Modifier.weight(1f),
                enabled = !state.isSubmitting
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::updatePhone,
                label = { Text("No. HP (opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                enabled = !state.isSubmitting
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::updateEmail,
                label = { Text("Email (opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f),
                enabled = !state.isSubmitting
            )
        }

        Spacer(Modifier.height(24.dp))

        // Submit button
        Button(
            onClick = { viewModel.submitRegistration() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !state.isSubmitting && state.faceVector != null && state.nim.isNotBlank() && state.name.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isSubmitting) "Mendaftarkan..." else "✅ Daftarkan Santri", fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Helper functions ───

/** Convert ImageProxy to Bitmap (YUV_420_888 → ARGB_8888) */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
    return bitmap
}

/** Rotate bitmap by degrees */
private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
