package com.facegate.adminapp.rules

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.facegate.adminapp.navigation.Screen
import com.facegate.adminapp.ui.components.*
import com.facegate.core.data.remote.dto.CampusRuleDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    navController: NavController,
    viewModel: RuleListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    var ruleToDelete by remember { mutableStateOf<CampusRuleDto?>(null) }

    LaunchedEffect(Unit) { viewModel.loadRules() }

    // Konfirmasi hapus
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus aturan?") },
            text = {
                Text(
                    "${dayNames.getOrElse(rule.dayOfWeek) { "?" }} " +
                        "${rule.startTime} - ${rule.endTime}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule)
                        ruleToDelete = null
                    }
                ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aturan Jam") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.RuleForm.createRoute()) }) {
                        Icon(Icons.Default.Add, "Tambah")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, "Settings")
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
                state.isLoading && state.rules.isEmpty() -> LoadingState()
                state.error != null && state.rules.isEmpty() -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.loadRules() }
                )
                state.rules.isEmpty() -> EmptyState(
                    icon = Icons.Default.Schedule,
                    title = "Belum ada aturan",
                    subtitle = "Ketuk + untuk menambahkan aturan jam baru"
                )
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.rules) { rule ->
                            RuleItem(
                                rule = rule,
                                onClick = {
                                    navController.navigate(Screen.RuleForm.createRoute(rule.id))
                                },
                                onDelete = { ruleToDelete = rule }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val dayNames = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")

@Composable
fun RuleItem(
    rule: CampusRuleDto,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (rule.isRestricted)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = if (rule.isRestricted)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dayNames.getOrElse(rule.dayOfWeek) { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${rule.startTime} - ${rule.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!rule.appliesToAll) {
                    val scope = listOfNotNull(rule.studyProgram, rule.academicYear).joinToString(" · ")
                    if (scope.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Khusus: $scope",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (rule.isRestricted) {
                StatusBadge(
                    text = "Terbatas",
                    color = Color(0xFFE53935)
                )
            } else {
                StatusBadge(
                    text = "Bebas",
                    color = Color(0xFF4CAF50)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "Hapus",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
