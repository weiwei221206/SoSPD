package io.github.sospd.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.sospd.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF386784), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE6F8), onPrimaryContainer = Color(0xFF10374E),
    secondary = Color(0xFF4B6579), secondaryContainer = Color(0xFFD5E9F8),
    onSecondaryContainer = Color(0xFF17394F), tertiary = Color(0xFF526B8C),
    background = Color(0xFFF7FAFE), surface = Color(0xFFF7FAFE),
    surfaceContainerLow = Color(0xFFEDF4FB), surfaceContainer = Color(0xFFDCEAF6),
    surfaceContainerHigh = Color(0xFFE6F0F9), outlineVariant = Color(0xFFC9DCEB),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA5CFF0), onPrimary = Color(0xFF09334D),
    primaryContainer = Color(0xFF244E69), onPrimaryContainer = Color(0xFFD0E7F9),
    secondary = Color(0xFFB2CCDF), secondaryContainer = Color(0xFF304E64),
    onSecondaryContainer = Color(0xFFD0E6F7),
    background = Color(0xFF101820), surface = Color(0xFF101820),
    surfaceContainerLow = Color(0xFF17232E), surfaceContainer = Color(0xFF203547),
    surfaceContainerHigh = Color(0xFF1B2D3C), outlineVariant = Color(0xFF3C5366),
)

@Composable
fun SospdTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
