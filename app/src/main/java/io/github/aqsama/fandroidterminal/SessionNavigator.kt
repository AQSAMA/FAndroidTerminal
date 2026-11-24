// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal class SessionNavigatorState {
    var openingBounds by mutableStateOf<Rect?>(null)
    var target by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var inputBlocked by mutableStateOf(false)
    suspend fun open() { target = 1f }
    suspend fun close() { target = 0f }
}

/** Horizontal navigation is claimed in Initial, before the terminal can consume it.
 * Opening starts only in the terminal viewport’s leading half, outside its controls. */
@Composable
internal fun SessionNavigator(state: SessionNavigatorState, sidebar: @Composable () -> Unit,
    gesturesEnabled: Boolean = true, content: @Composable () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { originInRoot = it.positionInRoot() }.onPreviewKeyEvent {
        if (state.visible && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            state.target = 0f; state.dragging = false; true
        } else false
    }) {
        val width = minOf(344.dp, maxWidth * .86f)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val settled by animateFloatAsState(state.target, if (state.dragging) snap() else spring(dampingRatio = .92f, stiffness = 480f), label = "Session navigator")
        val progress = if (state.dragging) state.target else settled
        val currentProgress by rememberUpdatedState(progress)
        val enabled by rememberUpdatedState(gesturesEnabled)
        val visible = state.dragging || progress > .001f || state.target > 0f
        // A partial opening may still turn into a two-finger pinch. Do not disturb
        // terminal focus or its InputConnection until the opening is committed.
        // Once open, keep input blocked throughout a closing/cancelled drag.
        val inputBlocked = visible && if (state.dragging) state.inputBlocked else state.target >= .5f
        SideEffect { state.visible = visible; state.inputBlocked = inputBlocked }
        LaunchedEffect(inputBlocked) { if (inputBlocked) { focus.clearFocus(force = true); keyboard?.hide() } }
        val bounds by rememberUpdatedState(state.openingBounds)
        val navigationGesture = Modifier.pointerInput(widthPx, direction) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val origin = state.target
                var previous = down.position
                var dx = 0f; var dy = 0f; var claimed = false; var released = false
                try {
                    val point = down.position + originInRoot
                    val inTerminal = bounds?.let { area ->
                        area.contains(point) && if (direction > 0f) point.x < area.center.x else point.x > area.center.x
                    } == true
                    // While open, drags that start on the sidebar belong to it (row swipes, lists);
                    // closing by drag starts on the dimmed content instead.
                    val onSidebar = if (direction > 0f) down.position.x < widthPx else down.position.x > size.width - widthPx
                    if ((enabled && inTerminal) || (origin > 0f && !onSidebar)) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // Never turn a two-finger resize into session navigation.
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val movement = change.position - previous
                            previous = change.position
                            dx += movement.x; dy += movement.y
                            if (!claimed) {
                                if (abs(dy) > viewConfiguration.touchSlop) break
                                if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 2f && (origin > 0f || dx * direction > 0f)) {
                                    state.target = currentProgress
                                    state.dragging = true; claimed = true
                                    state.target = (state.target + dx * direction / widthPx).coerceIn(0f, 1f)
                                }
                            } else state.target = (state.target + movement.x * direction / widthPx).coerceIn(0f, 1f)
                            if (claimed) change.consume()
                            if (!change.pressed) { released = true; break }
                        }
                    }
                } finally {
                    if (claimed) {
                        state.target = if (!released) { if (origin >= .5f) 1f else 0f } else if (state.target >= if (origin == 0f) .25f else .4f) 1f else 0f
                        state.dragging = false
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().then(navigationGesture)) {
            if (visible) Box(Modifier.width(width).fillMaxHeight().graphicsLayer {
                translationX = direction * (progress - 1f) * widthPx
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                clip = true
            }) { sidebar() }
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = direction * widthPx * progress
                // Keep terminal glyphs at native scale throughout navigation.
                shape = RoundedCornerShape((20f * progress).dp); clip = progress > 0f
            }) {
                Box(if (visible) Modifier.clearAndSetSemantics {}.onPreviewKeyEvent { true } else Modifier) { content() }
                if (visible) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .3f * progress))
                    .clickable { state.target = 0f })
            }
        }
        BackHandler(visible) { state.dragging = false; state.target = 0f }
    }
}
