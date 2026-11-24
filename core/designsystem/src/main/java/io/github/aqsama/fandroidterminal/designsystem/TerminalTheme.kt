// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.designsystem

import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val WorkspacePaletteStyles: List<String> = com.materialkolor.PaletteStyle.entries.map { it.name }

/** Same pinned palette engine used by June; terminal ANSI colors remain independent. */
@Composable
fun workspaceColors(dark: Boolean, hue: Int = 168, chroma: Int = 65, black: Boolean = false, style: String = "TonalSpot", seed: Color? = null): ColorScheme =
    com.materialkolor.rememberDynamicColorScheme(
        seedColor = seed ?: Color.hsl(hue.coerceIn(0, 359).toFloat(), chroma.coerceIn(20, 90) / 100f, .5f),
        isDark = dark, isAmoled = black,
        style = com.materialkolor.PaletteStyle.entries.firstOrNull { it.name == style } ?: com.materialkolor.PaletteStyle.TonalSpot,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalTheme(dark: Boolean = isSystemInDarkTheme(), hue: Int = 168, chroma: Int = 65, black: Boolean = false, dynamic: Boolean = false, paletteStyle: String = "TonalSpot", seed: Color? = null, content: @Composable () -> Unit) {
    val target = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        val wallpaper = if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        if (black && dark) wallpaper.copy(surface = Color.Black, background = Color.Black) else wallpaper
    } else workspaceColors(dark, hue, chroma, black, paletteStyle, seed)
    val primary by animateColorAsState(target.primary, tween(300), label = "accent")
    MaterialExpressiveTheme(
        colorScheme = target.copy(primary = primary),
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)),
        typography = Typography(), content = content,
    )
}
