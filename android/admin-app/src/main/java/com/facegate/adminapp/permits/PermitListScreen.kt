package com.facegate.adminapp.permits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.ui.components.*
import com.facegate.adminapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitListScreen(
    navController: NavController,
    viewModel: PermitListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Izin Keluar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("permit_create") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Buat Izin")
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error,
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(padding)
            )
            state.permits.isEmpty() -> EmptyState(
                icon = Icons.Default.Description,
                title = "Belum ada izin",
                subtitle = "Ketuk + untuk membuat izin baru",
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.permits) { permit ->
                    PermitCard(
                        name = permit.studentName,
                        purpose = permit.purpose ?: "-",
                        status = permit.status ?: "pending",
                        onClick = { navController.navigate("permit_detail/${permit.id}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermitCard(
    name: String,
    purpose: String,
    status: String,
    onClick: () -> Unit
) {
    val (statusColor, statusText) = when (status.lowercase()) {
        "approved" -> SuccessGreen to "Disetujui"
        "rejected" -> ErrorRed to "Ditolak"
        "expired" -> WarningOrange to "Kadaluarsa"
        else -> InfoBlue to "Pending"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (status.lowercase()) {
                            "approved" -> Icons.Default.CheckCircle
                            "rejected" -> Icons.Default.Cancel
                            else -> Icons.Default.Schedule
                        },
                        null, tint = statusColor, modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(purpose, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            StatusBadge(text = statusText, color = statusColor)
        }
    }
}
