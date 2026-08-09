package com.facegate.kioskscanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.facegate.kioskscanner.permit.PermitScreen
import com.facegate.kioskscanner.permit.VerifyTarget
import com.facegate.kioskscanner.scanner.ScannerScreen

/**
 * #135: navigator kiosk — 2 tab (Scanner | Izin) + layar verifikasi izin
 * full-screen saat anggota izin ditap di tab Izin.
 */
@Composable
fun KioskNavigator() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var verifyTarget by remember { mutableStateOf<VerifyTarget?>(null) }

    // #135: mode verifikasi — layar scanner penuh dengan target anggota izin.
    val target = verifyTarget
    if (target != null) {
        ScannerScreen(
            verifyTarget = target,
            onVerifyDone = {
                verifyTarget = null
                tab = 0
            }
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF0D1117),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF16181D)) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Scanner",
                            tint = if (tab == 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    },
                    label = { Text("Scanner", color = if (tab == 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = "Izin",
                            tint = if (tab == 1) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    },
                    label = { Text("Izin", color = if (tab == 1) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                1 -> PermitScreen(onVerifyTarget = { verifyTarget = it })
                else -> ScannerScreen()
            }
        }
    }
}