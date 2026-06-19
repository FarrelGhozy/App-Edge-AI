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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    studentId: String,
    navController: NavController,
    viewModel: StudentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(studentId) { viewModel.loadStudent(studentId) }

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
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
    Divider()
}
