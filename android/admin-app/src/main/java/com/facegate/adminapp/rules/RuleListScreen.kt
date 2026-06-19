package com.facegate.adminapp.rules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RuleListScreen(
    viewModel: RuleListViewModel = hiltViewModel()
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rules list placeholder", style = MaterialTheme.typography.bodyLarge)
    }
}
