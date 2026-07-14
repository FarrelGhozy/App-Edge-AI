package com.facegate.adminapp.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.core.data.remote.dto.AttendanceLogDto

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val screen: Screen,
    val color: Color
)

val dashboardItems = listOf(
    DashboardItem("Mahasiswa", Icons.Default.People, Screen.Students, Color(0xFF1976D2)),
    DashboardItem("Absensi", Icons.Default.History, Screen.Attendance, Color(0xFF388E3C)),
    DashboardItem("Izin", Icons.Default.Description, Screen.Permits, Color(0xFFF57C00)),
    DashboardItem("Persetujuan", Icons.Default.Approval, Screen.PendingApproval, Color(0xFFE91E63)),
    DashboardItem("Aturan", Icons.Default.Rule, Screen.Rules, Color(0xFF7B1FA2)),
    DashboardItem("Device", Icons.Default.Devices, Screen.Devices, Color(0xFF00796B)),
    DashboardItem("Pelanggaran", Icons.Default.Gavel, Screen.Violations, Color(0xFFC62828)),
    DashboardItem("Status", Icons.Default.Logout, Screen.ToggleStatus, Color(0xFFFF5722)),
    DashboardItem("Hari Libur", Icons.Default.CalendarMonth, Screen.Holidays, Color(0xFF4CAF50)),
    DashboardItem("Notifikasi", Icons.Default.Notifications, Screen.Notifications, Color(0xFF1565C0)),
    DashboardItem("Sinkronisasi", Icons.Default.CloudSync, Screen.Sync, Color(0xFF0097A7)),
    DashboardItem("Laporan", Icons.Default.BarChart, Screen.Reports, Color(0xFF4E342E)),
    DashboardItem("Pengaturan", Icons.Default.Settings, Screen.Settings, Color(0xFF546E7A))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSummary()
    }

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FaceGate Admin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                .padding(16.dp)
        ) {
            StatCards(state)
            Spacer(modifier = Modifier.height(16.dp))
            RecentScansSection(state.recentScans)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Menu", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            val rows = dashboardItems.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { item ->
                        DashboardCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(item.screen.route) }
                        )
                    }
                    if (row.size < 3) {
                        Spacer(modifier = Modifier.weight((3 - row.size).toFloat()))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StatCards(state: DashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isLoading && state.totalStudents == 0) {
            repeat(3) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Memuat...", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            StatCard(
                title = "Mahasiswa",
                value = state.totalStudents.toString(),
                icon = Icons.Default.People,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Di Luar",
                value = state.currentlyOutside.toString(),
                icon = Icons.Default.ExitToApp,
                color = Color(0xFFF57C00),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Violasi Hari Ini",
                value = state.violationsToday.toString(),
                icon = Icons.Default.Gavel,
                color = Color(0xFFC62828),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, title, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RecentScansSection(scans: List<AttendanceLogDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Scan Terakhir",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (scans.isEmpty()) {
                Text(
                    "Belum ada data scan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                scans.forEach { scan ->
                    RecentScanItem(scan)
                    if (scan != scans.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentScanItem(scan: AttendanceLogDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scan.studentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTimestamp(scan.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val (label, color) = if (scan.action == "keluar") {
            "Keluar" to Color(0xFFE65100)
        } else {
            "Kembali" to Color(0xFF2E7D32)
        }
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatTimestamp(iso: String): String {
    return try {
        val parts = iso.split("T")
        if (parts.size >= 2) {
            val date = parts[0]
            val time = parts[1].substringBefore(".").take(5)
            "$date $time"
        } else {
            iso.take(16)
        }
    } catch (_: Exception) {
        iso.take(16)
    }
}

@Composable
fun DashboardCard(
    item: DashboardItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = item.color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(item.icon, item.title, tint = item.color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = item.color)
        }
    }
}
