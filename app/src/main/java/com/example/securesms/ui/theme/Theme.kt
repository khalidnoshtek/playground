package com.example.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B74DE),
    secondary = Color(0xFF0F766E),
    background = Color(0xFFF8FAFC),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
)

@Composable
fun SecureSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
