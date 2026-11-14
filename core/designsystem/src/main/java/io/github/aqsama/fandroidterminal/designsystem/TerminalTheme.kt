// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = darkColorScheme(
    primary = Color(0xFF9BC7FF), onPrimary = Color(0xFF072C55), primaryContainer = Color(0xFF24466E), onPrimaryContainer = Color(0xFFDFECFF),
    secondary = Color(0xFF84DDCD), onSecondary = Color(0xFF00382F), secondaryContainer = Color(0xFF233D50), onSecondaryContainer = Color(0xFFE0F3FF),
    tertiary = Color(0xFFD3BAFF), tertiaryContainer = Color(0xFF40305C), onTertiaryContainer = Color(0xFFF0E5FF),
    surface = Color(0xFF0C121C), surfaceContainerLowest = Color(0xFF080D15), surfaceContainerLow = Color(0xFF131E2D),
    surfaceContainer = Color(0xFF1A283A), surfaceContainerHigh = Color(0xFF223247), surfaceContainerHighest = Color(0xFF2E4057),
    onSurface = Color(0xFFF0F4FC), onSurfaceVariant = Color(0xFFB9C9DE), outline = Color(0xFF7E94AD), outlineVariant = Color(0xFF3B506B),
)
private val Day = lightColorScheme(
    primary = Color(0xFF245D9C), onPrimary = Color.White, primaryContainer = Color(0xFFD8E8FF), onPrimaryContainer = Color(0xFF123B69),
    secondaryContainer = Color(0xFFD9E9F4), onSecondaryContainer = Color(0xFF183C52),
    surface = Color(0xFFF3F7FD), surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEAF0F8),
    surfaceContainer = Color(0xFFE1EAF5), surfaceContainerHigh = Color(0xFFD6E2F0),
    onSurface = Color(0xFF14253B), onSurfaceVariant = Color(0xFF425972), outline = Color(0xFF687E97), outlineVariant = Color(0xFFB4C6DC),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else Day,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
        typography = base.copy(titleLarge = base.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold), labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)),
        content = content,
    )
}
