package com.facegate.kioskscanner.permit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facegate.core.data.local.entity.PermitEntity
import com.facegate.core.data.local.entity.PermitMemberEntity
import com.facegate.core.data.local.entity.StudentEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    var showTimeFromPicker by remember { mutableStateOf(false) }
    var showTimeToPicker by remember { mutableStateOf(false) }

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
                KioskDateField(
                    value = dateFrom,
                    label = "Tanggal mulai (DD-MM-YYYY)",
                    modifier = Modifier.weight(1f),
                    onValueChange = { dateFrom = it },
                    onPick = { showDateFromPicker = true }
                )
                KioskDateField(
                    value = dateTo,
                    label = "Sampai (DD-MM-YYYY)",
                    modifier = Modifier.weight(1f),
                    onValueChange = { dateTo = it },
                    onPick = { showDateToPicker = true }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KioskTimeField(
                    value = timeFrom,
                    label = "Jam mulai (HH:MM, opsional)",
                    modifier = Modifier.weight(1f),
                    onValueChange = { timeFrom = it },
                    onPick = { showTimeFromPicker = true }
                )
                KioskTimeField(
                    value = timeTo,
                    label = "Jam selesai (HH:MM, opsional)",
                    modifier = Modifier.weight(1f),
                    onValueChange = { timeTo = it },
                    onPick = { showTimeToPicker = true }
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
                    val startIso = ddmmyyyyToIso(dateFrom) ?: return@Button
                    val endIso = ddmmyyyyToIso(dateTo) ?: return@Button
                    viewModel.submitPermit(
                        memberIds = selected.map { it.id },
                        startDate = startIso,
                        endDate = endIso,
                        startTime = timeFrom.ifBlank { null },
                        endTime = timeTo.ifBlank { null },
                        reason = reason.ifBlank { null }
                    )
                },
                enabled = selected.isNotEmpty() && ddmmyyyyToIso(dateFrom) != null && ddmmyyyyToIso(dateTo) != null &&
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

    // ── Date Pickers (ikon kalender) ──
    if (showDateFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ddmmyyyyToMillis(dateFrom)
        )
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateFrom = millisToDdmmyyyy(it) }
                    showDateFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateFromPicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDateToPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ddmmyyyyToMillis(dateTo)
        )
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateTo = millisToDdmmyyyy(it) }
                    showDateToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateToPicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ── Time pickers (ikon jam) ──
    if (showTimeFromPicker) {
        val (h, m) = splitHm(timeFrom)
        val pickerState = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimeFromPicker = false },
            title = { Text("Pilih Jam Mulai") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    timeFrom = formatHm(pickerState.hour, pickerState.minute)
                    showTimeFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeFromPicker = false }) { Text("Batal") }
            }
        )
    }

    if (showTimeToPicker) {
        val (h, m) = splitHm(timeTo)
        val pickerState = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimeToPicker = false },
            title = { Text("Pilih Jam Selesai") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    timeTo = formatHm(pickerState.hour, pickerState.minute)
                    showTimeToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeToPicker = false }) { Text("Batal") }
            }
        )
    }
}

// ═════════════════════════════  HELPERS  ═════════════════════════════

/**
 * Field tanggal kiosk: ketik manual dgn mask otomatis (DD-MM-YYYY),
 * atau tekan ikon kalender utk memilih via DatePickerDialog.
 */
@Composable
private fun KioskDateField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onPick: () -> Unit
) {
    val invalid = value.isNotBlank() && ddmmyyyyToMillis(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(maskDdMmYyyy(it)) },
        modifier = modifier,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "Pilih tanggal",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        supportingText = if (invalid) {
            { Text("Tanggal tidak valid", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        } else null,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = darkColors()
    )
}

/**
 * Field jam kiosk: ketik "1430" → otomatis "14:30" (tanpa mengetik ":"),
 * atau tekan ikon jam utk memilih TimePicker.
 */
@Composable
private fun KioskTimeField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onPick: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(maskHhMm(it)) },
        modifier = modifier,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = "Pilih jam",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = darkColors()
    )
}

/** Auto-mask input ketik → DD-MM-YYYY: 08072026 → 08-07-2026. */
private fun maskDdMmYyyy(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(8)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('-')
            append(c)
        }
    }
}

/** Auto-mask jam → HH:MM: 1430 → 14:30 (tanpa mengetik ':'). */
private fun maskHhMm(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(4)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2) append(':')
            append(c)
        }
    }
}

/** "DD-MM-YYYY" → epoch millis (UTC tengah malam, konsisten dgn DatePicker). */
private fun ddmmyyyyToMillis(s: String): Long? = try {
    LocalDate.parse(s, DDMYY_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}

/** "DD-MM-YYYY" → "YYYY-MM-DD" (format yang diterima server). */
private fun ddmmyyyyToIso(s: String): String? = try {
    LocalDate.parse(s, DDMYY_FORMAT).toString()
} catch (_: Exception) {
    null
}

private fun millisToDdmmyyyy(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DDMYY_FORMAT)

/** "HH:MM" (atau parsial) → (jam, menit). */
private fun splitHm(s: String): Pair<Int, Int> {
    val parts = s.split(':')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun formatHm(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private val DDMYY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy")

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