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
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Terminal(
            terminalEmulator = emulator,
            modifier = modifier,
            initialFontSize = 14.sp,
            minFontSize = 8.sp,
            maxFontSize = 28.sp,
            backgroundColor = Color(0xFF141218),
            foregroundColor = Color(0xFFE6E1E5),
            selectionBackgroundColor = Color(0xFF4F378B),
            selectionForegroundColor = Color.White,
            keyboardEnabled = true,
            modifierManager = modifiers,
            onPasteRequest = onPaste,
        )
    }
}
