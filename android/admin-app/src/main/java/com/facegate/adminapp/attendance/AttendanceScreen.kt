package com.facegate.adminapp.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.core.data.remote.dto.AttendanceLogDto
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    navController: NavController,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.loadLogs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Absensi") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                var showDatePicker by remember { mutableStateOf(false) }
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = if (state.filterDate.isNotBlank()) {
                        try {
                            val zdt = ZonedDateTime.parse(state.filterDate + "T00:00:00+07:00")
                            zdt.toInstant().toEpochMilli()
                        } catch (_: Exception) { null }
                    } else null
                )

                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.filterDate,
                        onValueChange = {},
                        label = { Text("Tanggal") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.DateRange, "Pilih tanggal",
                                modifier = Modifier.clickable { showDatePicker = true })
                        }
                    )
                    if (state.filterDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { viewModel.setFilterDate("") }) {
                            Icon(Icons.Default.Close, "Hapus filter")
                        }
                    }
                }

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.of("Asia/Jakarta"))
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    viewModel.setFilterDate(date)
                                }
                                showDatePicker = false
                            }) { Text("Pilih") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                if (state.isLoading && state.logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada data absensi")
                    }
                } else {
                    LazyColumn {
                        items(state.logs) { log ->
                            AttendanceLogItem(log)
                        }
                        if (state.hasMore && state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                        if (state.hasMore && !state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text("Muat Lebih Banyak")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceLogItem(log: AttendanceLogDto) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (log.action == "keluar") Icons.Default.ExitToApp else Icons.Default.Login,
                null,
                tint = if (log.action == "keluar") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.studentName, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (log.action == "keluar") "Keluar" else "Kembali",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.action == "keluar") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                log.timestamp.take(16).replace("T", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
