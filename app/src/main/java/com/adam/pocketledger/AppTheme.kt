package com.adam.pocketledger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LedgerGreen = Color(0xFF0F766E)
private val LightColors = lightColorScheme(
    primary = LedgerGreen, secondary = Color(0xFF2563EB),
    surface = Color(0xFFF8FAFC), background = Color(0xFFF8FAFC)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4), secondary = Color(0xFF93C5FD),
    surface = Color(0xFF111827), background = Color(0xFF0B1220)
)

@Composable
fun PocketLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
