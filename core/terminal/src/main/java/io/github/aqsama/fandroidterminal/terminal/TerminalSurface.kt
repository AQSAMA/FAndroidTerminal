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
import androidx.compose.ui.platform.LocalView
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
    scheme: TerminalScheme = TerminalSchemes.default,
    fontId: String? = null,
    onInterceptKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onSelection: (org.connectbot.terminal.SelectionController) -> Unit = {},
    focusRequester: FocusRequester = androidx.compose.runtime.remember { FocusRequester() },
    showKeyboard: Boolean = true,
    onImeVisibility: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    onFontSizeChanged: (Int) -> Unit = {},
    inputEnabled: Boolean = true,
    keyboardRequest: Int = 0,
    onHyperlinkClick: (String) -> Unit = {},
) {
    val hostView = LocalView.current
    val typeface = remember(fontId) { TerminalFonts.typeface(hostView.context, fontId) }
    LaunchedEffect(emulator, scheme) { emulator.applyColorScheme(scheme.ansi, scheme.foreground, scheme.background) }
    LaunchedEffect(keyboardRequest, showKeyboard, inputEnabled) {
        if (keyboardRequest > 0 && inputEnabled) {
            if (showKeyboard) {
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
            } else {
                val imm = hostView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(hostView.windowToken, 0)
            }
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
        }.background(Color(scheme.background)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier.fillMaxSize(),
                typeface = typeface,
                focusRequester = focusRequester,
                showSoftKeyboard = showKeyboard,
                onImeVisibilityChanged = onImeVisibility,
                onTerminalTap = onTap,
                initialFontSize = liveFont.roundToInt().sp,
                onInterceptKey = onInterceptKey,
                onSelectionControllerAvailable = { controller = it; onSelection(it) },
                minFontSize = 8.sp,
                maxFontSize = 28.sp,
                backgroundColor = Color(scheme.background),
                foregroundColor = Color(scheme.foreground),
                selectionBackgroundColor = Color(scheme.selection),
                selectionForegroundColor = Color(scheme.foreground),
                keyboardEnabled = inputEnabled,
                // The measured viewport is authoritative, including after IME dismissal.
                // A retained animation/focus size must never cap the grid at keyboard height.
                resizeSuspended = false,
                modifierManager = modifiers,
                onPasteRequest = onPaste,
                onHyperlinkClick = onHyperlinkClick,
            )
        }
    }
}
