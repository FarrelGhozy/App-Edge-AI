package com.facegate.adminapp.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.*
import com.facegate.adminapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSummary()
        viewModel.startAutoRefresh()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopAutoRefresh() }
    }

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Dashboard.route) { inclusive = true }
            }
        }
    }

    // Logout confirm dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = { Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Keluar") },
            text = { Text("Yakin ingin keluar dari aplikasi?") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FaceGate", fontWeight = FontWeight.Bold)
                        Text("Dashboard", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding)
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Stat Cards ──
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Total Santri",
                                value = formatNumber(state.totalStudents),
                                icon = Icons.Default.People,
                                containerColor = InfoBlue,
                                iconTint = InfoBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Di Luar",
                                value = formatNumber(state.currentlyOutside),
                                icon = Icons.Default.ExitToApp,
                                containerColor = WarningOrange,
                                iconTint = WarningOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Pelanggaran Hari Ini",
                                value = formatNumber(state.violationsToday),
                                icon = Icons.Default.Gavel,
                                containerColor = ErrorRed,
                                iconTint = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Terdaftar",
                                value = formatNumber(state.totalStudents),
                                icon = Icons.Default.Face,
                                containerColor = SuccessGreen,
                                iconTint = SuccessGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── Menu Grid ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Menu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MenuCard(
                                title = "Santri",
                                subtitle = "Kelola data santri",
                                icon = Icons.Default.People,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { navController.navigate(Screen.Students.route) },
                                modifier = Modifier.weight(1f)
                            )
                            MenuCard(
                                title = "Absensi",
                                subtitle = "Riwayat scan wajah",
                                icon = Icons.Default.Fingerprint,
                                accentColor = Teal40,
                                onClick = { navController.navigate(Screen.Attendance.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MenuCard(
                                title = "Izin",
                                subtitle = "Kelola izin keluar",
                                icon = Icons.Default.Description,
                                accentColor = Amber40,
                                onClick = { navController.navigate(Screen.Permits.route) },
                                modifier = Modifier.weight(1f)
                            )
                            MenuCard(
                                title = "Status Toggle",
                                subtitle = "Santri di luar",
                                icon = Icons.Default.SwitchAccount,
                                accentColor = WarningOrange,
                                onClick = { navController.navigate(Screen.ToggleStatus.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MenuCard(
                                title = "Pelanggaran",
                                subtitle = "Data pelanggaran",
                                icon = Icons.Default.Gavel,
                                accentColor = ErrorRed,
                                onClick = { navController.navigate(Screen.Violations.route) },
                                modifier = Modifier.weight(1f)
                            )
                            MenuCard(
                                title = "Pengaturan",
                                subtitle = "Konfigurasi aplikasi",
                                icon = Icons.Default.Settings,
                                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { navController.navigate(Screen.Settings.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── Recent Scans ──
                    if (state.recentScans.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Scan Terakhir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(state.recentScans.take(5)) { log ->
                            RecentScanCard(log)
                        }

                        if (state.recentScans.size > 5) {
                            item {
                                TextButton(
                                    onClick = { navController.navigate(Screen.Attendance.route) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Lihat Semua")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // bottom spacer
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecentScanCard(log: com.facegate.core.data.remote.dto.AttendanceLogDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (log.action == "keluar") ErrorLight else SuccessLight,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (log.action == "keluar") Icons.Default.ExitToApp else Icons.Default.Login,
                        null,
                        tint = if (log.action == "keluar") ErrorRed else SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.studentName, style = MaterialTheme.typography.titleSmall)
                StatusBadge(
                    text = if (log.action == "keluar") "Keluar" else "Kembali",
                    color = if (log.action == "keluar") ErrorRed else SuccessGreen
                )
            }
            Text(
                log.timestamp.take(16).replace("T", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNumber(n: Int): String {
    return when {
        n >= 1000 -> "${n / 1000}k"
        else -> n.toString()
    }
}
