package com.dispatcher.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Concept B — Freight Dispatcher Pro tokens (docs/PHASE-4-UI-SPEC.md)
val Navy900 = Color(0xFF15233B)
val Navy800 = Color(0xFF1B2A4A)
val Steel100 = Color(0xFFE9EDF2)
val Steel200 = Color(0xFFDDE4EC)
val Border = Color(0xFFC3CDD9)
val Orange500 = Color(0xFFF97316)
val OrangeDim = Color(0xFFFDE8D8)
val Green700 = Color(0xFF15803D)
val Amber600 = Color(0xFFC2700C)
val Red700 = Color(0xFFB91C1C)
val Blue700 = Color(0xFF1D4ED8)

private val LightScheme = lightColorScheme(
    primary = Orange500,
    onPrimary = Color.White,
    primaryContainer = OrangeDim,
    secondary = Navy800,
    background = Steel100,
    surface = Color.White,
    onSurface = Navy900,
    surfaceVariant = Steel200,
    outline = Border,
    error = Red700,
)

private val DarkScheme = darkColorScheme(
    primary = Orange500,
    onPrimary = Color.White,
    secondary = Color(0xFF9FB0D0),
    background = Color(0xFF10182A),
    surface = Color(0xFF16223A),
    onSurface = Color(0xFFE3EAF4),
    outline = Color(0xFF28415F),
    error = Color(0xFFF87171),
)

@Composable
fun DispatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
