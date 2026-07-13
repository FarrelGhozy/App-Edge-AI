package com.facegate.adminapp.permits

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitDetailScreen(
    permitId: String,
    navController: NavController,
    viewModel: PermitDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(permitId) { viewModel.load(permitId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Izin") },
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
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            } else if (state.permit != null) {
                val p = state.permit!!

                DetailRow("Status", p.status.uppercase())
                DetailRow("Jenis", if (p.type == "izin_harian") "Izin Harian" else "Pengajuan Izin")
                DetailRow("Tanggal Mulai", p.startDate.take(10))
                DetailRow("Tanggal Selesai", p.endDate.take(10))

                val startTime = p.startTime
                if (startTime != null) {
                    DetailRow("Jam Mulai", startTime)
                }
                val endTime = p.endTime
                if (endTime != null) {
                    DetailRow("Jam Selesai", endTime)
                }
                val reason = p.reason
                if (reason != null) {
                    DetailRow("Alasan", reason)
                }

                if (p.status == "pending") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.reject(permitId) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isProcessing
                        ) {
                            Text("Tolak")
                        }
                        Button(
                            onClick = { viewModel.approve(permitId) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isProcessing
                        ) {
                            Text("Setujui")
                        }
                    }
                }

                if (state.actionMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.actionMessage!!,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            modifier = Modifier.width(140.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
