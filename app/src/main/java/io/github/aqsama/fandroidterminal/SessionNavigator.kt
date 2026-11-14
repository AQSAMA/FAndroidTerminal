// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal class SessionNavigatorState {
    var target by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var visible by mutableStateOf(false)
    suspend fun open() { target = 1f }
    suspend fun close() { target = 0f }
}

/** Layer transforms never change the measured terminal grid during navigation. */
@Composable
internal fun SessionNavigator(state: SessionNavigatorState, sidebar: @Composable () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent {
        if ((state.target > 0f || state.dragging) && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            state.target = 0f; state.dragging = false; true
        } else false
    }) {
        val width = minOf(320.dp, maxWidth * .82f)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val settled by animateFloatAsState(state.target, if (state.dragging) snap() else spring(dampingRatio = .9f, stiffness = 550f), label = "Session navigator")
        val progress = if (state.dragging) state.target else settled
        val visible = state.dragging || progress > .001f || state.target > 0f
        val currentProgress by rememberUpdatedState(progress)
        SideEffect { state.visible = visible }
        val inputSink = remember { FocusRequester() }
        LaunchedEffect(visible) { if (visible) inputSink.requestFocus() }
        fun dragModifier() = Modifier.pointerInput(widthPx, direction) {
            var start = 0f
            detectHorizontalDragGestures(
                onDragStart = { start = state.target; state.target = currentProgress; state.dragging = true },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    state.target = (state.target + direction * amount / widthPx).coerceIn(0f, 1f)
                },
                onDragEnd = { state.target = if (state.target >= .5f) 1f else 0f; state.dragging = false },
                onDragCancel = { state.target = if (start >= .5f) 1f else 0f; state.dragging = false },
            )
        }
        if (visible) Box(Modifier.width(width).fillMaxHeight().graphicsLayer {
            translationX = direction * (progress - 1f) * widthPx
        }.then(dragModifier())) { sidebar() }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = direction * widthPx * progress
            scaleX = 1f - .05f * progress
            scaleY = 1f - .05f * progress
            shape = RoundedCornerShape((20f * progress).dp)
            clip = progress > 0f
        }) {
            Box(if (visible) Modifier.clearAndSetSemantics {}.onPreviewKeyEvent { true } else Modifier) { content() }
            if (visible) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .18f * progress))
                .clickable { state.target = 0f }.then(dragModifier()))
        }
        // Move IME and hardware focus off the terminal without dismissing an open IME.
        // This field discards input; it never stores shell text or sends it to a process.
        if (visible) BasicTextField("", {}, keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            modifier = Modifier.size(1.dp).focusRequester(inputSink).clearAndSetSemantics {})
        // Only the start edge opens the navigator. Terminal scroll/selection owns the rest.
        Box(Modifier.width(20.dp).fillMaxHeight().padding(top = 56.dp, bottom = 56.dp).then(dragModifier()))
        // Register after destination content so Back dismisses this modal first.
        BackHandler(visible) { state.dragging = false; state.target = 0f }
    }
}
