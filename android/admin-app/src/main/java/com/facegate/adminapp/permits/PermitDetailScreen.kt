package com.facegate.adminapp.permits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.ui.components.*

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
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.load(permitId) }
                )
                state.permit != null -> {
                    val p = state.permit!!

                    // ── Status Badge + Type ──
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = when (p.status) {
                                    "approved" -> "DISETUJUI"
                                    "rejected" -> "DITOLAK"
                                    else -> "PENDING"
                                },
                                color = when (p.status) {
                                    "approved" -> Color(0xFF4CAF50)
                                    "rejected" -> Color(0xFFE53935)
                                    else -> Color(0xFFFFA726)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusBadge(
                                text = if (p.type == "izin_harian") "Izin Harian" else "Pengajuan Izin",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ── Detail Card ──
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (p.status) {
                                        "approved" -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                                        "rejected" -> Color(0xFFE53935).copy(alpha = 0.12f)
                                        else -> Color(0xFFFFA726).copy(alpha = 0.12f)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            when (p.status) {
                                                "approved" -> Icons.Default.CheckCircle
                                                "rejected" -> Icons.Default.Cancel
                                                else -> Icons.Default.HourglassEmpty
                                            },
                                            null,
                                            tint = when (p.status) {
                                                "approved" -> Color(0xFF4CAF50)
                                                "rejected" -> Color(0xFFE53935)
                                                else -> Color(0xFFFFA726)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = when (p.status) {
                                            "approved" -> "Izin Disetujui"
                                            "rejected" -> "Izin Ditolak"
                                            else -> "Menunggu Persetujuan"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (p.type == "izin_harian") "Izin Harian" else "Pengajuan Izin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Info rows
                            InfoRow("Status", p.status.uppercase())
                            InfoRow("Jenis", if (p.type == "izin_harian") "Izin Harian" else "Pengajuan Izin")
                            InfoRow("Tanggal Mulai", p.startDate.take(10))
                            InfoRow("Tanggal Selesai", p.endDate.take(10))

                            val startTime = p.startTime
                            if (startTime != null) {
                                InfoRow("Jam Mulai", startTime)
                            }
                            val endTime = p.endTime
                            if (endTime != null) {
                                InfoRow("Jam Selesai", endTime)
                            }
                            val reason = p.reason
                            if (reason != null) {
                                InfoRow("Alasan", reason)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Action Buttons ──
                    if (p.status == "pending") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Aksi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.reject(permitId) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !state.isProcessing,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        if (state.isProcessing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text("Tolak")
                                    }
                                    Button(
                                        onClick = { viewModel.approve(permitId) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !state.isProcessing
                                    ) {
                                        if (state.isProcessing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text("Setujui")
                                    }
                                }
                            }
                        }
                    }

                    // ── Action Message ──
                    if (state.actionMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.actionMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
