package com.facegate.adminapp.violations

import androidx.compose.foundation.layout.*
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
fun ViolationDetailScreen(
    violationId: String,
    navController: NavController,
    viewModel: ViolationDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(violationId) { viewModel.load(violationId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Pelanggaran") },
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
            } else if (state.violation != null) {
                val v = state.violation!!

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Tipe", modifier = Modifier.width(120.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(typeLabel(v.type))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Waktu", modifier = Modifier.width(120.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.timestamp.take(19).replace("T", " "))
                }
                val description = v.description
                if (description != null) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("Keterangan", modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(description)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Status", modifier = Modifier.width(120.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (v.isResolved) "Selesai" else "Belum")
                }
                val resolvedAt = v.resolvedAt
                if (resolvedAt != null) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("Diselesaikan", modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(resolvedAt.take(19).replace("T", " "))
                    }
                }

                if (!v.isResolved) {
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = state.resolveNote,
                        onValueChange = { viewModel.setResolveNote(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Catatan penyelesaian (opsional)") },
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.resolve(violationId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing
                    ) {
                        Text("Selesaikan Pelanggaran")
                    }
                }

                if (state.actionMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.actionMessage!!, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "keluar_tanpa_izin" -> "Keluar Tanpa Izin"
    "keluar_jam_terlarang" -> "Keluar Jam Terlarang"
    "keluar_jam_kuliah" -> "Keluar Jam Kuliah"
    "tidak_kembali" -> "Tidak Kembali"
    "melebihi_batas_izin" -> "Melebihi Batas Izin"
    else -> type
}
