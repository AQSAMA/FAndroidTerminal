// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Terminal(
            terminalEmulator = emulator,
            modifier = modifier,
            initialFontSize = fontSize.sp,
            onInterceptKey = onInterceptKey,
            onSelectionControllerAvailable = onSelection,
            minFontSize = 8.sp,
            maxFontSize = 28.sp,
            backgroundColor = if (palette == "Paper") Color(0xFFFAFAFA) else if (palette == "Solarized") Color(0xFF002B36) else Color(0xFF141218),
            foregroundColor = if (palette == "Paper") Color(0xFF202124) else if (palette == "Solarized") Color(0xFF93A1A1) else Color(0xFFE6E1E5),
            selectionBackgroundColor = Color(0xFF4F378B),
            selectionForegroundColor = Color.White,
            keyboardEnabled = true,
            modifierManager = modifiers,
            onPasteRequest = onPaste,
        )
    }
}
