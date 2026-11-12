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
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF0B3265),
    secondaryContainer = Color(0xFF334863), onSecondaryContainer = Color(0xFFD7E3F7),
    surface = Color(0xFF10141B), surfaceContainer = Color(0xFF1B2029),
    surfaceContainerHigh = Color(0xFF252C37), onSurface = Color(0xFFE1E5EE),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else lightColorScheme(primary = Color(0xFF265EA4), onPrimary = Color.White, surface = Color(0xFFF8F9FD), surfaceContainerLow = Color(0xFFEEF2F9), secondaryContainer = Color(0xFFDCE6F7)),
        content = content,
    )
}
