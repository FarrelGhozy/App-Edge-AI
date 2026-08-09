package com.facegate.kioskscanner.permit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facegate.core.data.local.entity.PermitEntity
import com.facegate.core.data.local.entity.PermitMemberEntity
import com.facegate.core.data.local.entity.StudentEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #135: Tab "Izin" di kiosk — dua sub-layar:
 * 1. Daftar Izin (status pending/approved/rejected + tap anggota utk verifikasi scan)
 * 2. Buat Izin (form mandiri/kelompok)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitScreen(
    onVerifyTarget: (VerifyTarget) -> Unit,
    viewModel: PermitViewModel = hiltViewModel()
) {
    var subTab by remember { mutableStateOf(0) } // 0 = Daftar, 1 = Buat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        Text(
            "Izin Keluar Kampus",
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // ─── Sub-tab: Daftar / Buat ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTabButton("Daftar Izin", selected = subTab == 0, modifier = Modifier.weight(1f)) { subTab = 0 }
            SubTabButton("Buat Izin", selected = subTab == 1, modifier = Modifier.weight(1f)) { subTab = 1 }
        }

        Spacer(Modifier.height(8.dp))

        when (subTab) {
            0 -> PermitListTab(onVerifyTarget = onVerifyTarget, viewModel = viewModel)
            else -> PermitFormTab(viewModel = viewModel)
        }
    }
}

@Composable
private fun SubTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2D3142),
            contentColor = if (selected) Color.White else Color(0xFFC4C7CC)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

// ═════════════════════════════  DAFTAR IZIN  ═════════════════════════════

@Composable
private fun PermitListTab(
    onVerifyTarget: (VerifyTarget) -> Unit,
    viewModel: PermitViewModel
) {
    val permitList by viewModel.permitList.collectAsState(initial = emptyList())
    val unsyncedCount by viewModel.unsyncedRequestCount.collectAsState(initial = 0)

    Column(modifier = Modifier.fillMaxSize()) {
        if (unsyncedCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A3400))
            ) {
                Text(
                    "⚠️ $unsyncedCount pengajuan belum terkirim — menunggu koneksi internet",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (permitList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Belum ada izin.\nTekan \"Buat Izin\" untuk mengajukan.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(permitList, key = { it.permit.id }) { item ->
                PermitCard(
                    item = item,
                    onVerify = { member, phase ->
                        onVerifyTarget(
                            VerifyTarget(
                                permitId = item.permit.id,
                                studentId = member.studentId,
                                studentName = member.name,
                                phase = phase,
                                permitNote = item.permit.note
                            )
                        )
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun PermitCard(
    item: PermitListItem,
    onVerify: (PermitMemberEntity, PermitMemberPhase) -> Unit,
    viewModel: PermitViewModel
) {
    val permit = item.permit
    val (badgeColor, badgeText) = when (permit.status) {
        "approved" -> Color(0xFF2E7D32) to "DISETUJUI"
        "rejected" -> Color(0xFFB71C1C) to "DITOLAK"
        else -> Color(0xFFF9A825) to "MENUNGGU"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C23))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (permit.type == "izin_kelompok")
                            "Izin Kelompok (${item.members.size})"
                        else "Izin Mandiri",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDateRange(permit.startDate, permit.endDate, permit.startTime, permit.endTime),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(badgeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            val reason = permit.reason
            if (reason != null && reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Keperluan: $reason",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            val note = permit.note
            if (permit.status == "approved" && note != null && note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "📝 $note",
                    color = Color(0xFF90CAF9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (permit.status == "rejected" && permit.rejectionReason != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alasan: ${permit.rejectionReason}",
                    color = Color(0xFFEF9A9A),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Anggota", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            item.members.forEach { member ->
                MemberRow(
                    member = member,
                    enabled = permit.status == "approved",
                    onClick = { m, phase -> onVerify(m, phase) }
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: PermitMemberEntity,
    enabled: Boolean,
    onClick: (PermitMemberEntity, PermitMemberPhase) -> Unit
) {
    val phase = memberPhase(member)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                if (enabled) Color(0xFF232733) else Color(0xFF17191F),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) {
                if (phase != PermitMemberPhase.SELESAI) onClick(member, phase)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = Color(0xFF90CAF9),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(member.nim, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        when {
            member.kembaliVerifiedAt != null -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sudah kembali", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            member.keluarVerifiedAt != null -> Text("KELUAR ✓", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else -> Text("Belum keluar", color = Color(0xFFC4C7CC), fontSize = 11.sp)
        }
    }
}

// ═════════════════════════════  BUAT IZIN  ═════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermitFormTab(viewModel: PermitViewModel) {
    val scope = rememberCoroutineScope()
    val submitState by viewModel.submitState.collectAsState()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var selected by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var reason by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var timeFrom by remember { mutableStateOf("") }
    var timeTo by remember { mutableStateOf("") }

    // Reset banner hasil submit yang sudah tampil lama
    LaunchedEffect(submitState) {
        if (submitState is PermitSubmitState.Success) {
            kotlinx.coroutines.delay(6000)
            viewModel.resetSubmitState()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // ── Pencarian mahasiswa ──
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    scope.launch { results = viewModel.searchStudents(q) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cari nama atau NIM (mis. 2023...)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = darkColors()
            )
        }

        if (query.isNotBlank()) {
            items(results) { student ->
                val already = selected.any { it.id == student.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (already) Color(0xFF1B3A34) else Color(0xFF232733), RoundedCornerShape(10.dp))
                        .clickable {
                            if (!already) {
                                if (student.id !in selected.map { it.id }) {
                                    selected = selected + student
                                }
                                query = ""
                                results = emptyList()
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(student.nim, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    if (already) Text("Dipilih", color = Color(0xFF81C784), fontSize = 11.sp)
                }
            }
        }

        // ─── Anggota terpilih ───
        if (selected.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D23))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (selected.size == 1) "Izin Mandiri (1 orang)"
                            else "Izin Kelompok (${selected.size} orang)",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        selected.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.name + " — " + s.nim, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selected = selected.filter { it.id != s.id } }) {
                                    Text("Hapus", color = Color(0xFFEF9A9A), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Keperluan izin (mis. pulang kampung, acara keluarga)") },
                colors = darkColors()
            )
        }

        item {
            // ─── Tanggal & jam ───
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateFrom, onValueChange = { dateFrom = it }, modifier = Modifier.weight(1f),
                    label = { Text("Tanggal mulai (YYYY-MM-DD)") }, colors = darkColors()
                )
                OutlinedTextField(
                    value = dateTo, onValueChange = { dateTo = it }, modifier = Modifier.weight(1f),
                    label = { Text("Sampai (YYYY-MM-DD)") }, colors = darkColors()
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timeFrom, onValueChange = { timeFrom = it }, modifier = Modifier.weight(1f),
                    label = { Text("Jam mulai (HH:MM, opsional)") }, colors = darkColors()
                )
                OutlinedTextField(
                    value = timeTo, onValueChange = { timeTo = it }, modifier = Modifier.weight(1f),
                    label = { Text("Jam selesai (HH:MM, opsional)") }, colors = darkColors()
                )
            }
        }

        item {
            when (val st = submitState) {
                is PermitSubmitState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (st.queuedOffline) Color(0xFF4E3400) else Color(0xFF1A3A2A)
                        )
                    ) {
                        Text(st.message, modifier = Modifier.padding(12.dp), color = Color.White, fontSize = 13.sp)
                    }
                }
                else -> {}
            }
        }

        item {
            Button(
                onClick = {
                    if (selected.isEmpty()) return@Button
                    if (dateFrom.isBlank() || dateTo.isBlank()) return@Button
                    viewModel.submitPermit(
                        memberIds = selected.map { it.id },
                        startDate = dateFrom,
                        endDate = dateTo,
                        startTime = timeFrom.ifBlank { null },
                        endTime = timeTo.ifBlank { null },
                        reason = reason.ifBlank { null }
                    )
                },
                enabled = selected.isNotEmpty() && dateFrom.isNotBlank() && dateTo.isNotBlank() &&
                          if (submitState is PermitSubmitState.Sending) false else true,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (submitState is PermitSubmitState.Sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Ajukan Izin", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ═════════════════════════════  HELPERS  ═════════════════════════════

fun memberPhase(member: PermitMemberEntity): PermitMemberPhase {
    return when {
        member.keluarVerifiedAt == null -> PermitMemberPhase.KELUAR
        member.kembaliVerifiedAt == null -> PermitMemberPhase.KEMBALI
        else -> PermitMemberPhase.SELESAI
    }
}

private fun formatDateRange(start: Long, end: Long, sTime: String?, eTime: String?): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    val dateRange = if (start == end) sdf.format(Date(start)) else "${sdf.format(Date(start))} – ${sdf.format(Date(end))}"
    val time = if (!sTime.isNullOrBlank() || !eTime.isNullOrBlank()) " · ${sTime ?: "?"}–${eTime ?: "?"}" else ""
    return dateRange + time
}

@Composable
private fun darkColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0xFF4A4E5C),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
)