package com.facegate.adminapp.students

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
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
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error)
                state.student != null -> {
                    val s = state.student!!
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        DetailRow("NIM", s.nim)
                        DetailRow("Nama", s.name)
                        DetailRow("Program Studi", s.studyProgram)
                        DetailRow("Angkatan", s.academicYear)
                        DetailRow("No. HP", s.phone ?: "-")
                        DetailRow("Email", s.email ?: "-")
                        DetailRow("Status", if (s.isActive) "Aktif" else "Nonaktif")

                        Spacer(modifier = Modifier.height(16.dp))

                        FaceRegistrationCard(
                            isRegistered = state.faceRegistered,
                            updatedAt = state.faceUpdatedAt,
                            isDeletingFace = state.isDeletingFace,
                            onRegister = {
                                navController.navigate(Screen.FaceRegister.createRoute(studentId))
                            },
                            onDelete = { viewModel.showDeleteFaceConfirm() }
                        )
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRegistered)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
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
                            style = MaterialTheme.typography.titleSmall,
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
                        style = MaterialTheme.typography.titleSmall,
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

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
    HorizontalDivider()
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
