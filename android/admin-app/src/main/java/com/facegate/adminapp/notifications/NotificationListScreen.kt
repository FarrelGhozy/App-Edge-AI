package com.facegate.adminapp.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    navController: NavController,
    viewModel: NotificationListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifikasi") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    var showConfirm by remember { mutableStateOf(false) }
                    TextButton(onClick = { showConfirm = true }) {
                        Text("Baca Semua")
                    }
                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            title = { Text("Baca Semua Notifikasi") },
                            text = { Text("Tandai semua notifikasi sebagai sudah dibaca?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showConfirm = false
                                    viewModel.markAllRead()
                                }) { Text("Ya") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirm = false }) { Text("Batal") }
                            }
                        )
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
            when {
                state.isLoading && state.notifications.isEmpty() -> LoadingState()
                state.notifications.isEmpty() -> EmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = "Tidak ada notifikasi"
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.notifications) { notif ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.isRead)
                                        MaterialTheme.colorScheme.surface
                                    else
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (notif.isRead) 0.dp else 2.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Unread indicator dot
                                    if (!notif.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp, end = 10.dp)
                                                .size(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(8.dp)
                                            ) {}
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(18.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            notif.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            notif.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!notif.isRead) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(
                                            modifier = Modifier.align(Alignment.Top),
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (state.hasMore && state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                        if (state.hasMore && !state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(16.dp),
                                        shape = RoundedCornerShape(8.dp)
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
