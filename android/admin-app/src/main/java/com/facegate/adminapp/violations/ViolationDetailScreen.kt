package com.facegate.adminapp.violations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.ui.components.*
import androidx.compose.ui.graphics.Color

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = { viewModel.load(violationId) }
                )
                state.violation == null -> EmptyState(
                    icon = Icons.Default.Gavel,
                    title = "Pelanggaran tidak ditemukan"
                )
                else -> {
                    val v = state.violation!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // ── Detail Info Card ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                InfoRow("Tipe", typeLabel(v.type))
                                InfoRow("Waktu", v.timestamp.take(19).replace("T", " "))
                                if (v.description != null) {
                                    InfoRow("Keterangan", v.description!!)
                                }
                                InfoRow(
                                    "Status",
                                    if (v.isResolved) "Selesai" else "Belum"
                                )
                                if (v.resolvedAt != null) {
                                    InfoRow(
                                        "Diselesaikan",
                                        v.resolvedAt!!.take(19).replace("T", " ")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Status Badge ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            StatusBadge(
                                text = if (v.isResolved) "SELESAI" else "BELUM",
                                color = if (v.isResolved) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }

                        if (!v.isResolved) {
                            Spacer(modifier = Modifier.height(24.dp))

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
                                        "Selesaikan Pelanggaran",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = state.resolveNote,
                                        onValueChange = { viewModel.setResolveNote(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Catatan penyelesaian (opsional)") },
                                        minLines = 2,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.resolve(violationId) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !state.isProcessing,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Selesaikan Pelanggaran")
                                    }
                                }
                            }
                        }

                        if (state.actionMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    state.actionMessage!!,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
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
