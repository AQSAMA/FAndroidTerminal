// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Terax Default palette data (Apache-2.0), revision f7bee29d8690aa0afe37ed8b4c94daeb5b0211ad.
// Mapped to original native Material surface roles; see THIRD_PARTY_NOTICES.md.
// Reference: references/terax/src/styles/globals.css (:root and .dark). Original Compose code.
private val Night = darkColorScheme(
    primary = Color(0xFFF7F7F7), onPrimary = Color(0xFF141414), primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color(0xFFF7F7F7),
    secondary = Color(0xFFCBCBCB), onSecondary = Color(0xFF141414), secondaryContainer = Color(0xFF2A2A2A), onSecondaryContainer = Color(0xFFF7F7F7),
    tertiary = Color(0xFFCBCBCB), tertiaryContainer = Color(0xFF2A2A2A), onTertiaryContainer = Color(0xFFF7F7F7),
    surface = Color(0xFF141414), surfaceContainerLowest = Color(0xFF111111), surfaceContainerLow = Color(0xFF1D1D1D),
    surfaceContainer = Color(0xFF242424), surfaceContainerHigh = Color(0xFF2A2A2A), surfaceContainerHighest = Color(0xFF333333),
    onSurface = Color(0xFFF7F7F7), onSurfaceVariant = Color(0xFFA3A3A3), outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF303030),
    error = Color(0xFFE35D5D),
)
private val Day = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White, primaryContainer = Color(0xFFE5E5E5), onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF656565), onSecondary = Color.White, secondaryContainer = Color(0xFFE5E5E5), onSecondaryContainer = Color(0xFF111111),
    surface = Color.White, surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3F3),
    surfaceContainer = Color(0xFFEDEDED), surfaceContainerHigh = Color(0xFFE5E5E5), surfaceContainerHighest = Color(0xFFDDDDDD),
    onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF656565), outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFE8E8E8),
    error = Color(0xFFC0392F),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark) Night else Day,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = Typography(),
        content = content,
    )
}
