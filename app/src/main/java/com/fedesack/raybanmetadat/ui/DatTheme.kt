package com.fedesack.raybanmetadat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Palette =
    darkColorScheme(
        primary = Color(0xFFE8E8E8),
        onPrimary = Color(0xFF111111),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE8E8E8),
        surface = Color(0xFF111111),
        onSurface = Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFB5B5B5),
        error = Color(0xFFFF8A80),
    )

@Composable
fun DatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
