package com.facegate.adminapp.permits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitFormScreen(
    navController: NavController,
    viewModel: PermitFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadStudents() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Izin") },
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
            // Student selection
            Text("Mahasiswa", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            StudentDropdown(
                students = state.students,
                selectedStudentId = state.selectedStudentId,
                onStudentSelected = { viewModel.setStudent(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type selection
            Text("Jenis Izin", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == "izin_harian",
                    onClick = { viewModel.setType("izin_harian") },
                    label = { Text("Izin Harian") }
                )
                FilterChip(
                    selected = state.type == "pengajuan_izin",
                    onClick = { viewModel.setType("pengajuan_izin") },
                    label = { Text("Pengajuan Izin") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start date
            Text("Tanggal Mulai", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.startDate,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, "Pilih tanggal") }
                )
                Box(modifier = Modifier.matchParentSize().clickable { showStartDatePicker = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // End date
            Text("Tanggal Selesai", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.endDate,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, "Pilih tanggal") }
                )
                Box(modifier = Modifier.matchParentSize().clickable { showEndDatePicker = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start time
            Text("Jam Mulai (opsional)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.startTime,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, "Pilih jam") }
                )
                Box(modifier = Modifier.matchParentSize().clickable { showStartTimePicker = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // End time
            Text("Jam Selesai (opsional)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.endTime,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, "Pilih jam") }
                )
                Box(modifier = Modifier.matchParentSize().clickable { showEndTimePicker = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reason
            Text("Alasan", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.reason,
                onValueChange = { viewModel.setReason(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Alasan izin") },
                minLines = 2,
                maxLines = 4
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Simpan")
            }

            if (state.isSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Izin berhasil dibuat", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Kembali")
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate.takeIf { it.isNotBlank() }
                ?.let { parseYmdToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setStartDate(formatMillisToYmd(it)) }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.endDate.takeIf { it.isNotBlank() }
                ?.let { parseYmdToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setEndDate(formatMillisToYmd(it)) }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.startTime.takeIf { it.isNotBlank() }
                ?.let { it.substringBefore(":").toIntOrNull() } ?: 0,
            initialMinute = state.startTime.takeIf { it.isNotBlank() }
                ?.let { it.substringAfter(":").toIntOrNull() } ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Pilih Jam Mulai") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStartTime(
                        "${timePickerState.hour.toString().padStart(2, '0')}:${timePickerState.minute.toString().padStart(2, '0')}"
                    )
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Batal") }
            }
        )
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.endTime.takeIf { it.isNotBlank() }
                ?.let { it.substringBefore(":").toIntOrNull() } ?: 0,
            initialMinute = state.endTime.takeIf { it.isNotBlank() }
                ?.let { it.substringAfter(":").toIntOrNull() } ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Pilih Jam Selesai") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEndTime(
                        "${timePickerState.hour.toString().padStart(2, '0')}:${timePickerState.minute.toString().padStart(2, '0')}"
                    )
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Batal") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDropdown(
    students: List<StudentBrief>,
    selectedStudentId: String?,
    onStudentSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = students.find { it.id == selectedStudentId }?.let {
        "${it.name} (${it.nim})"
    } ?: "Pilih mahasiswa"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            students.forEach { student ->
                DropdownMenuItem(
                    text = { Text("${student.name} (${student.nim})") },
                    onClick = {
                        onStudentSelected(student.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

data class StudentBrief(val id: String, val nim: String, val name: String)

private fun formatMillisToYmd(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun parseYmdToMillis(ymd: String): Long? {
    return try {
        val parts = ymd.split("-")
        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) { null }
}
