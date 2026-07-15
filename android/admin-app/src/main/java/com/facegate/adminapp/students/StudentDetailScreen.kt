package com.facegate.adminapp.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    studentId: String,
    navController: NavController,
    viewModel: StudentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(studentId) { viewModel.loadStudent(studentId) }

    val currentEntry = navController.currentBackStackEntry
    val faceRegisteredResult = currentEntry?.savedStateHandle?.getStateFlow("faceRegistered", false)
    val faceResult by faceRegisteredResult?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(faceResult) {
        if (faceResult) {
            viewModel.onFaceRegistered()
            currentEntry?.savedStateHandle?.set("faceRegistered", false)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.faceDeleteError) {
        state.faceDeleteError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFaceDeleteError()
        }
    }

    // Dialog konfirmasi hapus mahasiswa
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Hapus Mahasiswa") },
            text = {
                Text("Yakin ingin menghapus ${state.student?.name ?: "mahasiswa ini"}? " +
                     "Semua data absensi, izin, pelanggaran, dan wajah akan ikut terhapus.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteStudent(studentId) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog konfirmasi hapus wajah
    if (state.showDeleteFaceConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteFaceConfirm() },
            icon = { Icon(Icons.Default.Face, null) },
            title = { Text("Hapus Wajah") },
            text = { Text("Yakin ingin menghapus data wajah ${state.student?.name ?: ""}?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFace(studentId) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteFaceConfirm() }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.student?.name ?: "Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.student != null) {
                        IconButton(onClick = {
                            navController.navigate(Screen.StudentForm.createRoute(studentId))
                        }) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                        IconButton(onClick = { viewModel.showDeleteConfirm() }) {
                            Icon(Icons.Default.Delete, "Hapus")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.loadStudent(studentId) }
                )
                state.student != null -> {
                    val s = state.student!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ── Student Info Card ──
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            s.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            s.nim,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Info rows
                                InfoRow("Program Studi", s.studyProgram)
                                InfoRow("Angkatan", s.academicYear)
                                InfoRow("No. HP", s.phone ?: "-")
                                InfoRow("Email", s.email ?: "-")
                                InfoRow(
                                    "Status",
                                    if (s.isActive) "Aktif" else "Nonaktif"
                                )
                            }
                        }

                        // ── Status Badge ──
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            StatusBadge(
                                text = if (s.isActive) "AKTIF" else "NONAKTIF",
                                color = if (s.isActive) Color(0xFF4CAF50) else Color(0xFFE53935)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── Face Registration Card ──
                        FaceRegistrationCard(
                            isRegistered = state.faceRegistered,
                            updatedAt = state.faceUpdatedAt,
                            isDeletingFace = state.isDeletingFace,
                            onRegister = {
                                navController.navigate(Screen.FaceRegister.createRoute(studentId))
                            },
                            onDelete = { viewModel.showDeleteFaceConfirm() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            if (state.isDeleted) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
fun FaceRegistrationCard(
    isRegistered: Boolean,
    updatedAt: String? = null,
    isDeletingFace: Boolean = false,
    onRegister: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRegistered)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (isRegistered) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wajah Terdaftar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (updatedAt != null) {
                            Text(
                                text = "Terakhir diperbarui: ${formatDateTime(updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRegister,
                        modifier = Modifier.weight(1f),
                        enabled = !isDeletingFace
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rekam Ulang")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        enabled = !isDeletingFace,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isDeletingFace) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Hapus Wajah")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wajah Belum Terdaftar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Ketuk untuk merekam wajah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
                FilledTonalButton(
                    onClick = onRegister,
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Default.CameraAlt, "Registrasi Wajah", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rekam")
                }
            }
        }
    }
}

private fun formatDateTime(isoString: String): String {
    return try {
        val zdt = ZonedDateTime.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
        zdt.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}
