package com.facegate.adminapp.permits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                title = {
                    Text("Buat Izin", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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
            // ── Section: Data Mahasiswa ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Mahasiswa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Mahasiswa",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StudentDropdown(
                        students = state.students,
                        selectedStudentId = state.selectedStudentId,
                        onStudentSelected = { viewModel.setStudent(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section: Jenis Izin ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Jenis Izin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = state.type == "izin_harian",
                            onClick = { viewModel.setType("izin_harian") },
                            label = { Text("Izin Harian") },
                            leadingIcon = if (state.type == "izin_harian") {
                                { Icon(Icons.Default.Checklist, null, Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = state.type == "pengajuan_izin",
                            onClick = { viewModel.setType("pengajuan_izin") },
                            label = { Text("Pengajuan Izin") },
                            leadingIcon = if (state.type == "pengajuan_izin") {
                                { Icon(Icons.Default.Checklist, null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section: Waktu Izin ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Waktu Izin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Start date
                    Text(
                        "Tanggal Mulai",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.startDate,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("YYYY-MM-DD") },
                            leadingIcon = { Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showStartDatePicker = true })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // End date
                    Text(
                        "Tanggal Selesai",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.endDate,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("YYYY-MM-DD") },
                            leadingIcon = { Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showEndDatePicker = true })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start time
                    Text(
                        "Jam Mulai (opsional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.startTime,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("HH:mm") },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showStartTimePicker = true })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // End time
                    Text(
                        "Jam Selesai (opsional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.endTime,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("HH:mm") },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showEndTimePicker = true })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section: Alasan ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alasan",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.reason,
                        onValueChange = { viewModel.setReason(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Alasan izin") },
                        leadingIcon = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Error ──
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = state.error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Submit Button ──
            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Izin", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Success ──
            if (state.isSuccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Izin berhasil dibuat",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Kembali")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Date Pickers ──
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

    // ── Time Pickers ──
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
            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp)
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
