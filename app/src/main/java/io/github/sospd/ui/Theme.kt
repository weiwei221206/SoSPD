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
    primary = Color(0xFF3C694E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EBD5), onPrimaryContainer = Color(0xFF173B26),
    secondary = Color(0xFF725D35), secondaryContainer = Color(0xFFF2E4C1),
    tertiary = Color(0xFF426570), background = Color(0xFFF7F9F2), surface = Color(0xFFF7F9F2),
    surfaceContainerLow = Color(0xFFF0F3EA), surfaceContainer = Color(0xFFEAEEE3),
    surfaceContainerHigh = Color(0xFFE5EADF), outlineVariant = Color(0xFFD3DCCD),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFACD1AC), onPrimary = Color(0xFF163B25),
    primaryContainer = Color(0xFF2B5139), onPrimaryContainer = Color(0xFFD8EBD5),
    secondary = Color(0xFFD9C493), secondaryContainer = Color(0xFF544523),
    background = Color(0xFF111710), surface = Color(0xFF111710),
    surfaceContainerLow = Color(0xFF192018), surfaceContainer = Color(0xFF1D241C),
    surfaceContainerHigh = Color(0xFF282F26), outlineVariant = Color(0xFF424C40),
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
