package com.facegate.adminapp.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val screen: Screen,
    val color: androidx.compose.ui.graphics.Color
)

val dashboardItems = listOf(
    DashboardItem("Mahasiswa", Icons.Default.People, Screen.Students, androidx.compose.ui.graphics.Color(0xFF1976D2)),
    DashboardItem("Absensi", Icons.Default.History, Screen.Attendance, androidx.compose.ui.graphics.Color(0xFF388E3C)),
    DashboardItem("Izin", Icons.Default.Description, Screen.Permits, androidx.compose.ui.graphics.Color(0xFFF57C00)),
    DashboardItem("Aturan", Icons.Default.Rule, Screen.Rules, androidx.compose.ui.graphics.Color(0xFF7B1FA2)),
    DashboardItem("Device", Icons.Default.Devices, Screen.Devices, androidx.compose.ui.graphics.Color(0xFF00796B)),
    DashboardItem("Pelanggaran", Icons.Default.Gavel, Screen.Violations, androidx.compose.ui.graphics.Color(0xFFC62828)),
    DashboardItem("Notifikasi", Icons.Default.Notifications, Screen.Notifications, androidx.compose.ui.graphics.Color(0xFF1565C0)),
    DashboardItem("Laporan", Icons.Default.BarChart, Screen.Reports, androidx.compose.ui.graphics.Color(0xFF4E342E)),
    DashboardItem("Pengaturan", Icons.Default.Settings, Screen.Settings, androidx.compose.ui.graphics.Color(0xFF546E7A))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FaceGate Admin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
