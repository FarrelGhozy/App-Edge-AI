package com.facegate.adminapp.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.MenuCard

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
            Text(
                "Pilih Jenis Laporan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MenuCard(
                        title = "Rekap Harian",
                        subtitle = "Pergerakan mahasiswa hari ini",
                        icon = Icons.Default.CalendarMonth,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { navController.navigate(Screen.DailyReport.route) },
                        modifier = Modifier.weight(1f)
                    )
                    MenuCard(
                        title = "Rekap Bulanan",
                        subtitle = "Statistik per program studi",
                        icon = Icons.Default.BarChart,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { navController.navigate(Screen.MonthlyReport.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MenuCard(
                        title = "Laporan Pelanggaran",
                        subtitle = "Filter tipe dan tanggal",
                        icon = Icons.Default.Gavel,
                        accentColor = MaterialTheme.colorScheme.error,
                        onClick = { navController.navigate(Screen.ViolationReport.route) },
                        modifier = Modifier.weight(1f)
                    )
                    MenuCard(
                        title = "Keluar di Luar Jam Izin",
                        subtitle = "Mahasiswa keluar di luar jam izin",
                        icon = Icons.Default.NightsStay,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = { navController.navigate(Screen.OutsideHoursReport.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                MenuCard(
                    title = "Sedang di Luar",
                    subtitle = "Mahasiswa yang saat ini di luar kampus",
                    icon = Icons.Default.People,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigate(Screen.OutsideNow.route) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
