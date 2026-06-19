package com.facegate.adminapp.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.facegate.adminapp.students.StudentListScreen
import com.facegate.adminapp.permits.PermitListScreen
import com.facegate.adminapp.rules.RuleListScreen

data class DashboardTab(
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val tabs = remember {
        listOf(
            DashboardTab("Students", Icons.Default.People),
            DashboardTab("Attendance", Icons.Default.History),
            DashboardTab("Permits", Icons.Default.Description),
            DashboardTab("Rules", Icons.Default.Rule)
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FaceGate Admin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> StudentListScreen()
                1 -> Text("Attendance")
                2 -> PermitListScreen()
                3 -> RuleListScreen()
            }
        }
    }
}
