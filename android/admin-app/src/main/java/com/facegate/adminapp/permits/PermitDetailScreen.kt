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
import com.facegate.core.data.remote.dto.PermitMemberDto
import com.facegate.core.util.formatWibDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #135: Detail izin mandiri/kelompok — tampilkan anggota + status verifikasi
 * scan, dan pada approval admin bisa mengoreksi waktu izin + menulis note,
 * serta menolak dengan alasan.
 */
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
                                text = when (p.type) {
                                    "izin_kelompok" -> "Izin Kelompok"
                                    "izin_mandiri" -> "Izin Mandiri"
                                    else -> "Izin"
                                },
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
                                        text = when (p.type) {
                                            "izin_kelompok" -> "Izin Kelompok"
                                            "izin_mandiri" -> "Izin Mandiri"
                                            else -> "Izin"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            InfoRow("Status", p.status.uppercase())
                            InfoRow(
                                "Jenis",
                                when (p.type) {
                                    "izin_kelompok" -> "Izin Kelompok"
                                    "izin_mandiri" -> "Izin Mandiri"
                                    else -> "Izin"
                                }
                            )
                            InfoRow("Tanggal Mulai", formatWibDate(p.startDate))
                            InfoRow("Tanggal Selesai", formatWibDate(p.endDate))

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

                            // #135: note admin + alasan tolak
                            val note = p.note
                            if (p.status == "approved" && note != null && note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow("Catatan Admin", note)
                            }
                            val rejectionReason = p.rejectionReason
                            if (p.status == "rejected" && rejectionReason != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow("Alasan Ditolak", rejectionReason)
                            }
                        }
                    }

                    // ── Anggota (mandiri: 1, kelompok: banyak) ──
                    if (p.members.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
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
                                Text(
                                    "Anggota (${p.members.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                p.members.forEach { member ->
                                    MemberStatusRow(member)
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Action Buttons (pending only) ──
                    if (p.status == "pending") {
                        var showApproveDialog by remember { mutableStateOf(false) }
                        var showRejectDialog by remember { mutableStateOf(false) }

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
                                        onClick = { showRejectDialog = true },
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
                                        onClick = { showApproveDialog = true },
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

                        // #135: dialog approve — koreksi waktu + note.
                        if (showApproveDialog) {
                            ApprovePermitDialog(
                                permit = p,
                                onDismiss = { showApproveDialog = false },
                                onConfirm = { d1, d2, t1, t2, noteText ->
                                    showApproveDialog = false
                                    viewModel.approve(
                                        permitId = permitId,
                                        startDate = d1.ifBlank { null },
                                        endDate = d2.ifBlank { null },
                                        startTime = t1.ifBlank { null },
                                        endTime = t2.ifBlank { null },
                                        note = noteText.ifBlank { null }
                                    )
                                }
                            )
                        }

                        // #135: dialog tolak — wajib alasan (dilihat santri di kiosk).
                        if (showRejectDialog) {
                            var reasonText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showRejectDialog = false },
                                title = { Text("Tolak Izin") },
                                text = {
                                    OutlinedTextField(
                                        value = reasonText,
                                        onValueChange = { reasonText = it },
                                        label = { Text("Alasan penolakan (ditampilkan ke santri)") },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showRejectDialog = false
                                            viewModel.reject(permitId, reasonText.ifBlank { null })
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Tolak")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRejectDialog = false }) {
                                        Text("Batal")
                                    }
                                }
                            )
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

/** #135: status verifikasi scan per anggota. */
@Composable
private fun MemberStatusRow(member: PermitMemberDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val student = member.student
            Text(
                student?.name ?: "Santri ${member.studentId}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (student?.nim != null) {
                Text(
                    student.nim,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val keluar = member.keluarVerifiedAt
        val kembali = member.kembaliVerifiedAt
        Column(horizontalAlignment = Alignment.End) {
            if (keluar != null) {
                Text(
                    "Keluar ${formatShortTime(keluar)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF8A65)
                )
            } else {
                Text(
                    "Belum keluar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (kembali != null) {
                Text(
                    "Kembali ${formatShortTime(kembali)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

private fun formatShortTime(iso: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val sdfOut = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        sdfOut.format(sdfIn.parse(iso) ?: Date())
    } catch (_: Exception) {
        iso
    }
}

/** #135: dialog setujui dengan koreksi waktu (opsional) + note admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovePermitDialog(
    permit: com.facegate.core.data.remote.dto.PermitDto,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    // #135: isi default dari tanggal izin (10 karakter pertama ISO = YYYY-MM-DD)
    val defaultDate = permit.startDate.take(10)
    var dateStart by remember { mutableStateOf(defaultDate) }
    var dateEnd by remember { mutableStateOf(defaultDate) }
    var timeStart by remember { mutableStateOf(permit.startTime ?: "") }
    var timeEnd by remember { mutableStateOf(permit.endTime ?: "") }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setujui Izin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Koreksi waktu jika diperlukan (kosongkan untuk memakai ajuan santri).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateStart,
                        onValueChange = { dateStart = it },
                        label = { Text("Mulai (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateEnd,
                        onValueChange = { dateEnd = it },
                        label = { Text("Sampai (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeStart,
                        onValueChange = { timeStart = it },
                        label = { Text("Jam mulai (HH:MM)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeEnd,
                        onValueChange = { timeEnd = it },
                        label = { Text("Jam selesai (HH:MM)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note/pesan ke santri (opsional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(dateStart, dateEnd, timeStart, timeEnd, noteText)
            }) {
                Text("Setujui")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
