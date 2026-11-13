// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator

/** App RTL does not mirror the terminal's cell grid or reinterpret escape sequences. */
@Composable
fun TerminalSurface(
    emulator: TerminalEmulator,
    modifiers: ModifierManager,
    modifier: Modifier = Modifier,
    onPaste: () -> Unit,
    fontSize: Int = 14,
    palette: String = "Midnight",
    onInterceptKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onSelection: (org.connectbot.terminal.SelectionController) -> Unit = {},
    focusRequester: FocusRequester = androidx.compose.runtime.remember { FocusRequester() },
    showKeyboard: Boolean = true,
    onImeVisibility: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
) {
    val colors = terminalColors(palette)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Insets are outside the measured terminal: grid dimensions, hit tests and IME
        // coordinates use the same reduced viewport; never shift drawn cells alone.
        Box(modifier.background(Color(colors.second)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier.fillMaxSize(),
                focusRequester = focusRequester,
                showSoftKeyboard = showKeyboard,
                onImeVisibilityChanged = onImeVisibility,
                onTerminalTap = onTap,
                initialFontSize = fontSize.sp,
                onInterceptKey = onInterceptKey,
                onSelectionControllerAvailable = onSelection,
                minFontSize = 8.sp,
                maxFontSize = 28.sp,
                backgroundColor = Color(colors.second),
                foregroundColor = Color(colors.first),
                selectionBackgroundColor = Color(0xFF4F378B),
                selectionForegroundColor = Color.White,
                keyboardEnabled = true,
                modifierManager = modifiers,
                onPasteRequest = onPaste,
            )
        }
    }
}

/** Foreground/background shared by the emulator and its complete padded surface. */
fun terminalColors(palette: String): Pair<Int, Int> = when (palette) {
    "Paper" -> 0xFF202124.toInt() to 0xFFFAFAFA.toInt()
    "Solarized" -> 0xFF93A1A1.toInt() to 0xFF002B36.toInt()
    "Graphite" -> 0xFFE2E1E6.toInt() to 0xFF1E1E24.toInt()
    else -> 0xFFE6E1E5.toInt() to 0xFF141218.toInt()
}
