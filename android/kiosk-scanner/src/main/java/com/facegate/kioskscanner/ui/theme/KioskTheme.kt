package com.facegate.kioskscanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Kiosk Scanner Theme — dark, immersive for face scanning
private val KioskDarkScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003734),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1A1C23),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2D3142),
    onSurfaceVariant = Color(0xFFC4C7CC),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9199)
)

@Composable
fun KioskTheme(
    darkTheme: Boolean = true, // Force dark for kiosk
    content: @Composable () -> Unit
) {
    val colorScheme = KioskDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KioskTypography,
        shapes = KioskShapes,
        content = content
    )
}

private val KioskShapes = shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val KioskTypography = Typography(
    headlineLarge = androidx.compose.material3.Typography().headlineLarge,
    headlineMedium = androidx.compose.material3.Typography().headlineMedium,
    titleLarge = androidx.compose.material3.Typography().titleLarge,
    titleMedium = androidx.compose.material3.Typography().titleMedium,
    bodyLarge = androidx.compose.material3.Typography().bodyLarge,
    bodyMedium = androidx.compose.material3.Typography().bodyMedium,
    bodySmall = androidx.compose.material3.Typography().bodySmall,
)
