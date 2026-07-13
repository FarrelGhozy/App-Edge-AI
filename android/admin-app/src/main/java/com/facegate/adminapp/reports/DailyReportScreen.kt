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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    navController: NavController,
    viewModel: DailyReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadToday() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekap Harian") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                // Summary cards
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("Keluar", "${state.keluarCount}", Color(0xFF1976D2), Modifier.weight(1f))
                    StatCard("Kembali", "${state.kembaliCount}", Color(0xFF388E3C), Modifier.weight(1f))
                    StatCard("Di Luar", "${state.stillOutsideCount}", Color(0xFFE53935), Modifier.weight(1f))
                }

                if (state.logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada scan hari ini")
                    }
                } else {
                    LazyColumn {
                        items(state.logs) { log ->
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(log.studentName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            log.timestamp.take(19).replace("T", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val actionColor = if (log.action == "keluar") Color(0xFFE53935) else Color(0xFF4CAF50)
                                    val actionLabel = if (log.action == "keluar") "KELUAR" else "KEMBALI"
                                    Text(
                                        actionLabel,
                                        color = actionColor,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
