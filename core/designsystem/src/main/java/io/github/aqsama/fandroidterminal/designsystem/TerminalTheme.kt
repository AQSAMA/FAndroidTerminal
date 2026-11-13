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
    primary = Color(0xFFB9CBB2), onPrimary = Color(0xFF24351F),
    secondaryContainer = Color(0xFF343D34), onSecondaryContainer = Color(0xFFE0EADD),
    surfaceContainerLowest = Color(0xFF141619), surfaceContainerLow = Color(0xFF1C1F23),
    onSurfaceVariant = Color(0xFFB9BDC4), outlineVariant = Color(0xFF44484B),
    surface = Color(0xFF17191C), surfaceContainer = Color(0xFF202328),
    surfaceContainerHigh = Color(0xFF2A2E33), surfaceContainerHighest = Color(0xFF34383D), onSurface = Color(0xFFE2E3E5),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else lightColorScheme(primary = Color(0xFF486442), onPrimary = Color.White, surface = Color(0xFFFAFAF7), surfaceContainerLow = Color(0xFFF0F1EC), secondaryContainer = Color(0xFFE1E9DC)),
        content = content,
    )
}
