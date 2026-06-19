package com.facegate.adminapp.permits

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitFormScreen(navController: NavController) {
    var type by remember { mutableStateOf("izin_harian") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Izin") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Form izin akan diimplementasikan", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
