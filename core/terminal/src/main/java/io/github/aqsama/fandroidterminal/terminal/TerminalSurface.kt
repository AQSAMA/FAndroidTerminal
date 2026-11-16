// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
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
@OptIn(ExperimentalLayoutApi::class)
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
    onFontSizeChanged: (Int) -> Unit = {},
    inputEnabled: Boolean = true,
    keyboardRequest: Int = 0,
) {
    val colors = terminalColors(palette)
    val hostView = LocalView.current
    LaunchedEffect(keyboardRequest, showKeyboard, inputEnabled) {
        if (keyboardRequest > 0 && showKeyboard && inputEnabled) {
            focusRequester.requestFocus()
            withFrameNanos { }
            // Compose's keyboard controller targets its own editor. This terminal uses
            // an Android input View; request the served editor even if intent stayed
            // true while Android Back dismissed the IME.
            val editor = hostView.rootView.findFocus()
            if (editor != null && editor.isAttachedToWindow && editor.hasWindowFocus() &&
                editor.onCheckIsTextEditor()) {
                val imm = editor.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
    val density = LocalDensity.current
    val imeAnimating = WindowInsets.imeAnimationSource.getBottom(density) !=
        WindowInsets.imeAnimationTarget.getBottom(density)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var settlingViewport by remember { mutableStateOf(false) }
    LaunchedEffect(imeAnimating, windowFocused) {
        if (imeAnimating || !windowFocused) settlingViewport = true
        else if (settlingViewport) {
            // Commit final insets before the emulator receives its one final size.
            withFrameNanos { }
            settlingViewport = false
        }
    }
    var liveFont by remember(emulator) { mutableFloatStateOf(fontSize.toFloat()) }
    LaunchedEffect(fontSize) { liveFont = fontSize.toFloat() }
    val saveFont by rememberUpdatedState(onFontSizeChanged)
    var controller by remember(emulator) { mutableStateOf<org.connectbot.terminal.SelectionController?>(null) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Insets are outside the measured terminal: grid dimensions, hit tests and IME
        // coordinates use the same reduced viewport; never shift drawn cells alone.
        Box(modifier.pointerInput(emulator) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var resizing = false
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            if (!resizing) controller?.clearSelection()
                            resizing = true
                            liveFont = (liveFont * event.calculateZoom()).coerceIn(8f, 28f)
                        }
                        if (resizing) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally { if (resizing) saveFont(liveFont.roundToInt()) }
            }
        }.background(Color(colors.second)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier.fillMaxSize(),
                focusRequester = focusRequester,
                showSoftKeyboard = showKeyboard,
                onImeVisibilityChanged = onImeVisibility,
                onTerminalTap = onTap,
                initialFontSize = liveFont.roundToInt().sp,
                onInterceptKey = onInterceptKey,
                onSelectionControllerAvailable = { controller = it; onSelection(it) },
                minFontSize = 8.sp,
                maxFontSize = 28.sp,
                backgroundColor = Color(colors.second),
                foregroundColor = Color(colors.first),
                selectionBackgroundColor = Color(if (palette == "Paper") 0xFFE5E5E5 else 0xFF2A2A2A),
                selectionForegroundColor = Color(colors.first),
                keyboardEnabled = inputEnabled,
                resizeSuspended = imeAnimating || !windowFocused || settlingViewport,
                modifierManager = modifiers,
                onPasteRequest = onPaste,
            )
        }
    }
}

/** Foreground/background shared by the emulator and its complete padded surface. */
fun terminalColors(palette: String): Pair<Int, Int> = when (palette) {
    "Paper" -> 0xFF111111.toInt() to 0xFFFFFFFF.toInt()
    "Solarized" -> 0xFFB6DAD6.toInt() to 0xFF0B252C.toInt()
    "Graphite" -> 0xFFF7F7F7.toInt() to 0xFF141414.toInt()
    else -> 0xFFF7F7F7.toInt() to 0xFF111111.toInt()
}

/** Terax Default ANSI palette data (Apache-2.0), see THIRD_PARTY_NOTICES.md.
 * Source: crynta/terax-ai f7bee29d8690aa0afe37ed8b4c94daeb5b0211ad,
 * src/styles/globals.css (:root / .dark terminal-ansi-*). Native mapping is original. */
fun terminalAnsiColors(palette: String): IntArray {
    val rgb = if (palette == "Paper") intArrayOf(
        0x111111, 0xC0392F, 0x2F7D4F, 0xA37413, 0x2F6BB5, 0x8A4FB5, 0x227D85, 0xCFCFCF,
        0x656565, 0xD94F42, 0x3D9C64, 0xBF8A1F, 0x3F82D4, 0xA065CF, 0x2B98A2, 0x111111,
    ) else intArrayOf(
        0x3F3F3F, 0xE35D5D, 0x6EC48F, 0xE0B34D, 0x6EA8E8, 0xB98CE0, 0x5FC6CC, 0xD8D8D8,
        0x6E6E6E, 0xF27F7F, 0x85D9A4, 0xEEC469, 0x8DBCF2, 0xCBA5EE, 0x7BDBE0, 0xF7F7F7,
    )
    return rgb.map { it or 0xFF000000.toInt() }.toIntArray()
}
