package com.facegate.adminapp.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.core.data.remote.dto.AttendanceLogDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    navController: NavController,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.filterDate,
                    onValueChange = { viewModel.setFilterDate(it) },
                    label = { Text("Tanggal") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            if (state.isLoading) {
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
