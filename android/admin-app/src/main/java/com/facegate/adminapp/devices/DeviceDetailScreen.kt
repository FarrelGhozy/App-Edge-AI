package com.facegate.adminapp.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun DeviceDetailScreen(
    deviceId: String,
    navController: NavController,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(deviceId) { viewModel.loadDevice(deviceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.device?.name ?: "Detail Device") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                state.device != null -> {
                    val d = state.device!!
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                DeviceDetailRow("Nama", d.name)
                                DeviceDetailRow("Device ID", d.deviceId)
                                DeviceDetailRow("Lokasi", d.location ?: "-")
                                DeviceDetailRow("Status", if (d.isActive) "Aktif" else "Nonaktif")
                                DeviceDetailRow("Baterai",
                                    d.batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "-")
                                DeviceDetailRow("Last Ping", d.lastPingAt?.take(19)?.replace("T", " ") ?: "-")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
    HorizontalDivider()
}
