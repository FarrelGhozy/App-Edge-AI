package com.facegate.adminapp.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan") },
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
            ReportCard(
                icon = Icons.Default.CalendarMonth,
                title = "Rekap Harian",
                desc = "Pergerakan mahasiswa hari ini",
                onClick = { navController.navigate(Screen.DailyReport.route) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportCard(
                icon = Icons.Default.BarChart,
                title = "Rekap Bulanan",
                desc = "Statistik per program studi",
                onClick = { /* TODO: monthly report */ }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportCard(
                icon = Icons.Default.Gavel,
                title = "Laporan Pelanggaran",
                desc = "Filter tipe dan tanggal",
                onClick = { /* TODO: violation report */ }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportCard(
                icon = Icons.Default.NightsStay,
                title = "Keluar di Luar Jam Izin",
                desc = "Mahasiswa keluar di luar jam izin",
                onClick = { /* TODO: outside hours report */ }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportCard(
                icon = Icons.Default.People,
                title = "Sedang di Luar",
                desc = "Mahasiswa yang saat ini di luar kampus",
                onClick = { navController.navigate(Screen.OutsideNow.route) }
            )
        }
    }
}

@Composable
private fun ReportCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
