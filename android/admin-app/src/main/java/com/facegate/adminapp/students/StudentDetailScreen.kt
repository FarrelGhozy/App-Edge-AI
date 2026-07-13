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
                        IconButton(onClick = { viewModel.deleteStudent(studentId) }) {
                            Icon(Icons.Default.Delete, "Hapus")
                        }
                    }
                }
            )
        }
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
                            onClick = {
                                navController.navigate(Screen.FaceRegister.createRoute(studentId))
                            }
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
fun FaceRegistrationCard(isRegistered: Boolean, updatedAt: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRegistered)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = if (isRegistered) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRegistered) "Wajah Terdaftar" else "Wajah Belum Terdaftar",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isRegistered) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onErrorContainer
                )
                if (isRegistered && updatedAt != null) {
                    Text(
                        text = "Terakhir diperbarui: ${formatDateTime(updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = if (isRegistered) "Ketuk untuk merekam ulang"
                               else "Ketuk untuk merekam wajah",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRegistered) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                               else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Registrasi Wajah",
                tint = if (isRegistered) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onErrorContainer
            )
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
    Divider()
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
