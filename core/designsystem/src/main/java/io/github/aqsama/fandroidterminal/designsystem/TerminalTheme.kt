// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Night = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFF141218), surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930), onSurface = Color(0xFFE6E1E5),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else lightColorScheme(),
        content = content,
    )
}
