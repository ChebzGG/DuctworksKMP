package com.cheezo.ductworks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.cheezo.ductworks.ui.CadScreen

private val CadLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1D4ED8),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFE2E8F0)
)

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = CadLightColorScheme) {
        CadScreen()
    }
}