package com.facegate.adminapp.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.core.data.remote.dto.StudentMonthlyStat
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    navController: NavController,
    viewModel: MonthlyReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val now = remember { YearMonth.now() }

    LaunchedEffect(Unit) { viewModel.load(month = now.monthValue, year = now.year) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekap Bulanan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.stats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${state.month}/${state.year}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Total Mahasiswa Aktif: ${state.totalStudents}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Dengan Aktivitas: ${state.stats.size}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.stats) { stat ->
                        StudentMonthlyCard(stat)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentMonthlyCard(stat: StudentMonthlyStat) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stat.name, style = MaterialTheme.typography.titleSmall)
            Text("${stat.nim} - ${stat.studyProgram}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("Keluar: ${stat.keluarCount}", style = MaterialTheme.typography.labelSmall)
                Text("Durasi: ${stat.totalDurationHours} jam", style = MaterialTheme.typography.labelSmall)
                Text("Violasi: ${stat.violationCount}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
