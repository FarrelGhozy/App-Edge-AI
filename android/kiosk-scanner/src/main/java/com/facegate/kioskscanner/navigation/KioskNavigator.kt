package com.facegate.kioskscanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.facegate.kioskscanner.registration.RegistrationScreen
import com.facegate.kioskscanner.scanner.ScannerScreen

enum class KioskScreen {
    SCANNER,
    REGISTRATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskNavigator() {
    var currentScreen by remember { mutableStateOf(KioskScreen.SCANNER) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    label = { Text("Scanner") },
                    selected = currentScreen == KioskScreen.SCANNER,
                    onClick = { currentScreen = KioskScreen.SCANNER }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    label = { Text("Registrasi") },
                    selected = currentScreen == KioskScreen.REGISTRATION,
                    onClick = { currentScreen = KioskScreen.REGISTRATION }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                KioskScreen.SCANNER -> ScannerScreen()
                KioskScreen.REGISTRATION -> RegistrationScreen(
                    onBack = { currentScreen = KioskScreen.SCANNER }
                )
            }
        }
    }
}
