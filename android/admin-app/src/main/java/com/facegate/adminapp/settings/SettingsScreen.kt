package com.facegate.adminapp.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadSettings() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!state.isEditMode) {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                    } else {
                        IconButton(onClick = { viewModel.saveSettings() }) {
                            Icon(Icons.Default.Check, "Simpan")
                        }
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Close, "Batal")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.isSaving -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn {
                        items(state.settings.keys.toList()) { key ->
                            val value = if (state.isEditMode) {
                                state.editingValues[key].orEmpty()
                            } else {
                                state.settings[key].orEmpty()
                            }
                            if (state.isEditMode) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { viewModel.updateValue(key, it) },
                                    label = { Text(key) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            } else {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(key, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(value, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
