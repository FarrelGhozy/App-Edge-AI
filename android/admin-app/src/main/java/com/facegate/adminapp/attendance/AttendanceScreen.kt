package com.facegate.adminapp.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.ui.components.*
import com.facegate.adminapp.ui.theme.*
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
                title = { Text("Riwayat Absensi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                state.isLoading && state.logs.isEmpty() -> LoadingState()
                state.logs.isEmpty() -> EmptyState(
                    icon = Icons.Default.Fingerprint,
                    title = "Belum ada data absensi",
                    subtitle = "Data scan wajah akan muncul di sini"
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date filter
                    item {
                        var showDatePicker by remember { mutableStateOf(false) }
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = if (state.filterDate.isNotBlank()) {
                                try {
                                    val zdt = ZonedDateTime.parse(state.filterDate + "T00:00:00+07:00")
                                    zdt.toInstant().toEpochMilli()
                                } catch (_: Exception) { null }
                            } else null
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.filterDate,
                                onValueChange = {},
                                label = { Text("Filter Tanggal") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                readOnly = true,
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = if (state.filterDate.isNotBlank()) {
                                { IconButton(onClick = { viewModel.setFilterDate("") }) {
                                    Icon(Icons.Default.Close, "Hapus filter")
                                } }
                            } else null,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { showDatePicker = true },
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(Icons.Default.CalendarMonth, "Pilih", Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pilih")
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
                    }

                    items(state.logs) { log ->
                        AttendanceLogCard(log)
                    }

                    if (state.hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(8.dp)
                                    ) { Text("Muat Lebih Banyak") }
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
private fun AttendanceLogCard(log: com.facegate.core.data.remote.dto.AttendanceLogDto) {
    val actionColor = if (log.action == "keluar") ErrorRed else SuccessGreen
    val actionText = if (log.action == "keluar") "Keluar" else "Kembali"
    val actionIcon = if (log.action == "keluar") Icons.Default.ExitToApp else Icons.Default.Login

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = actionColor.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(actionIcon, null, tint = actionColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                StatusBadge(text = actionText, color = actionColor)
            }
            Text(
                log.timestamp.take(16).replace("T", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
