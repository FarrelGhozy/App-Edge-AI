package com.facegate.adminapp.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.EmptyState
import com.facegate.adminapp.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    navController: NavController,
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.loadDevices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (state.isLoading && state.devices.isEmpty()) {
                LoadingState()
            } else if (state.devices.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.DevicesOther,
                    title = "Belum ada device terdaftar",
                    subtitle = "Tambahkan device scanner untuk memantau"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.devices) { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Device icon with status indicator
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (device.isActive) Color(0xFF4CAF50).copy(alpha = 0.1f)
                                               else Color(0xFFE53935).copy(alpha = 0.1f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Devices,
                                                null,
                                                tint = if (device.isActive) Color(0xFF4CAF50) else Color(0xFFE53935),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    // Online indicator dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (device.isActive) Color(0xFF4CAF50)
                                                else Color(0xFFE53935)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "ID: ${device.deviceId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (device.isActive) Color(0xFF4CAF50) else Color(0xFFE53935)).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        if (device.isActive) "Online" else "Offline",
                                        color = if (device.isActive) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
