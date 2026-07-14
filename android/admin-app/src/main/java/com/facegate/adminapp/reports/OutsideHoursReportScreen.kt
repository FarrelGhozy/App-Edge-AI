package com.facegate.adminapp.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutsideHoursReportScreen(
    navController: NavController,
    viewModel: OutsideHoursReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keluar di Luar Jam Izin") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.date,
                    onValueChange = { viewModel.setDate(it) },
                    label = { Text("Tanggal") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                FilledTonalButton(onClick = { viewModel.load() }) { Text("Cari") }
            }

            Text("Total: ${state.totalOutside} periode",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall)

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.periods.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada data")
                }
            } else {
                LazyColumn {
                    items(state.periods) { p ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(p.studentName, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    Text("Keluar: ${p.keluarTime.take(19).replace("T", " ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f))
                    p.kembaliTime?.let { kembali ->
                        Text("Kembali: ${kembali.take(19).replace("T", " ")}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                    }
                                }
                                if (p.durationMinutes != null) {
                                    Text("Durasi: ${p.durationMinutes} menit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Belum kembali",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
