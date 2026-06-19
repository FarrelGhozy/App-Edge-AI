package com.facegate.adminapp.rules

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
fun RuleFormScreen(
    ruleId: String?,
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId != null) "Edit Aturan" else "Tambah Aturan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Text(
            "Form aturan akan diimplementasikan",
            modifier = Modifier.padding(padding).padding(16.dp)
        )
    }
}
