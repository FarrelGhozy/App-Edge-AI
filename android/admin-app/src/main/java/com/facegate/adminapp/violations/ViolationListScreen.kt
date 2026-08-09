package com.facegate.adminapp.violations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViolationListScreen(
    navController: NavController,
    viewModel: ViolationListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    var violationToDelete by remember { mutableStateOf<ViolationItem?>(null) }
    var showFromPicker by rememberSaveable { mutableStateOf(false) }
    var showToPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleteMessage, state.deleteError) {
        val message = state.deleteMessage ?: state.deleteError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedback()
    }

    val fromPickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.fromDate?.let { dateToMillis(it) }
    )
    val toPickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.toDate?.let { dateToMillis(it) }
    )

    val isFilterActive = state.fromDate != null || state.toDate != null

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pelanggaran") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Search Bar ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    shadowElevation = 2.dp
                ) {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearch(it) },
                        placeholder = {
                            Text(
                                "Cari Nama atau NIM santri...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                if (state.searchQuery.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { viewModel.onSearch("") }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Clear, "Hapus", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // ── Filter Tanggal ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateFilterField(
                        label = "Dari",
                        date = state.fromDate,
                        modifier = Modifier.weight(1f),
                        onPick = { showFromPicker = true },
                        onClear = { viewModel.setDateRange(null, state.toDate) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DateFilterField(
                        label = "Sampai",
                        date = state.toDate,
                        modifier = Modifier.weight(1f),
                        onPick = { showToPicker = true },
                        onClear = { viewModel.setDateRange(state.fromDate, null) }
                    )
                    if (isFilterActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.clearDateRange() }) {
                            Icon(
                                Icons.Default.FilterAltOff,
                                "Reset filter",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ── Result count ──
                if ((state.searchQuery.isNotBlank() || isFilterActive) && state.violations.isNotEmpty()) {
                    Text(
                        "${state.violations.size} pelanggaran ditemukan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                // ── Content ──
                when {
                    state.isLoading && state.violations.isEmpty() -> LoadingState(modifier = Modifier.fillMaxSize())
                    state.error != null && state.violations.isEmpty() -> ErrorState(
                        message = state.error!!,
                        onRetry = { viewModel.load() }
                    )
                    state.violations.isEmpty() -> EmptyState(
                        icon = Icons.Default.Gavel,
                        title = if (state.searchQuery.isNotBlank() || isFilterActive)
                            "Tidak ada hasil"
                        else
                            "Belum ada pelanggaran",
                        subtitle = if (state.searchQuery.isNotBlank() || isFilterActive)
                            "Coba ubah kata kunci atau rentang tanggal"
                        else
                            "Data pelanggaran akan muncul di sini ketika tercatat"
                    )
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(state.violations) { v ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clickable {
                                            navController.navigate(Screen.ViolationDetail.createRoute(v.id))
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (v.isResolved)
                                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Gavel,
                                                    null,
                                                    tint = if (v.isResolved)
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                v.studentName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                v.type,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (v.timestamp.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    formatViolationDate(v.timestamp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (v.isResolved) {
                                            StatusBadge(
                                                text = "Selesai",
                                                color = Color(0xFF4CAF50)
                                            )
                                        } else {
                                            StatusBadge(
                                                text = "Baru",
                                                color = Color(0xFFFFA726)
                                            )
                                        }
                                        IconButton(onClick = { violationToDelete = v }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                "Hapus pelanggaran",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.hasMore && state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                    }
                                }
                            }

                            if (state.hasMore && !state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.loadMore() },
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Text("Muat Lebih Banyak")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDateRange(millisToDate(millis), state.toDate)
                    }
                    showFromPicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = fromPickerState)
        }
    }

    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDateRange(state.fromDate, millisToDate(millis))
                    }
                    showToPicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = toPickerState)
        }
    }

    violationToDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { violationToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus pelanggaran?") },
            text = {
                Text(
                    "${v.studentName} — ${v.type}\n" +
                        if (v.timestamp.isNotBlank()) formatViolationDate(v.timestamp) else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteViolation(v.id)
                        violationToDelete = null
                    }
                ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { violationToDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun DateFilterField(
    label: String,
    date: String?,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = date ?: "",
        onValueChange = {},
        label = { Text(label) },
        placeholder = { Text("Tgl") },
        modifier = modifier,
        singleLine = true,
        readOnly = true,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (date != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Hapus", modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(onClick = onPick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

private fun dateToMillis(date: String): Long? = try {
    ZonedDateTime.parse(date + "T00:00:00+07:00").toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}

private fun millisToDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.of("Asia/Jakarta"))
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

private val violationDateFormatter = DateTimeFormatter
    .ofPattern("dd MMM yyyy, HH:mm", Locale("id", "ID"))

private fun formatViolationDate(timestamp: String): String = try {
    Instant.parse(timestamp)
        .atZone(ZoneId.of("Asia/Jakarta"))
        .format(violationDateFormatter)
} catch (_: Exception) {
    timestamp
}
