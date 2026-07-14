package com.facegate.adminapp.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleFormScreen(
    ruleId: String?,
    navController: NavController,
    viewModel: RuleFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(ruleId) { if (ruleId != null) viewModel.load(ruleId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId != null) "Edit Aturan" else "Tambah Aturan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val dayNames = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
            Text("Hari", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = dayNames.getOrElse(state.dayOfWeek) { "?" },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    dayNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.setDayOfWeek(index)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Jam Mulai", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.startTime,
                onValueChange = { viewModel.setStartTime(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("HH:mm") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Jam Selesai", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.endTime,
                onValueChange = { viewModel.setEndTime(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("HH:mm") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isRestricted,
                    onCheckedChange = { viewModel.setRestricted(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Jam terlarang keluar")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Program Studi (kosongkan untuk semua)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.studyProgram,
                onValueChange = { viewModel.setStudyProgram(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Contoh: TI") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Angkatan (kosongkan untuk semua)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.academicYear,
                onValueChange = { viewModel.setAcademicYear(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Contoh: 2024") },
                singleLine = true
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            if (state.isSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Aturan disimpan", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Kembali") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submit(ruleId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Simpan")
            }
        }
    }
}
