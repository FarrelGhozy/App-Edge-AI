package com.facegate.adminapp.permits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitListScreen(
    navController: NavController,
    viewModel: PermitListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.loadPermits() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Izin") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.PermitForm.route) }) {
                        Icon(Icons.Default.Add, "Tambah")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filterStatus == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("Semua") }
                    )
                    FilterChip(
                        selected = state.filterStatus == "pending",
                        onClick = { viewModel.setFilter("pending") },
                        label = { Text("Pending") }
                    )
                    FilterChip(
                        selected = state.filterStatus == "approved",
                        onClick = { viewModel.setFilter("approved") },
                        label = { Text("Disetujui") }
                    )
                    FilterChip(
                        selected = state.filterStatus == "rejected",
                        onClick = { viewModel.setFilter("rejected") },
                        label = { Text("Ditolak") }
                    )
                }

                if (state.isLoading && state.permits.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.permits.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada izin")
                    }
                } else {
                    LazyColumn {
                        items(state.permits) { permit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { navController.navigate(Screen.PermitDetail.createRoute(permit.id)) }
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(permit.studentName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (permit.type == "izin_harian") "Izin Harian" else "Pengajuan Izin",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    val statusColor = when (permit.status) {
                                        "approved" -> Color(0xFF4CAF50)
                                        "rejected" -> Color(0xFFE53935)
                                        else -> Color(0xFFFFA000)
                                    }
                                    Text(
                                        permit.status.uppercase(),
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        if (state.hasMore && state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                        if (state.hasMore && !state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
