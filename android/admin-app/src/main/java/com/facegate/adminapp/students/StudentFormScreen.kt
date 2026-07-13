package com.facegate.adminapp.students

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    studentId: String?,
    navController: NavController,
    viewModel: StudentFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isEdit = studentId != null

    LaunchedEffect(studentId) {
        if (studentId != null) viewModel.loadStudent(studentId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            if (state.savedStudentId != null) {
                navController.navigate(Screen.StudentDetail.createRoute(state.savedStudentId!!)) {
                    popUpTo(Screen.Students.route)
                }
            } else {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Mahasiswa" else "Tambah Mahasiswa") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.nim,
                onValueChange = { viewModel.updateField("nim", it) },
                label = { Text("NIM") },
                enabled = !isEdit,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateField("name", it) },
                label = { Text("Nama") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.studyProgram,
                onValueChange = { viewModel.updateField("studyProgram", it) },
                label = { Text("Program Studi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.academicYear,
                onValueChange = { viewModel.updateField("academicYear", it) },
                label = { Text("Angkatan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.phone,
                onValueChange = { viewModel.updateField("phone", it) },
                label = { Text("No. HP (opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = { viewModel.updateField("email", it) },
                label = { Text("Email (opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEdit) "Simpan" else "Tambah")
                }
            }
        }
    }
}
