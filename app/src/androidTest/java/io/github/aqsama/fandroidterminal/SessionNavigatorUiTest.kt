// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionNavigatorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun secondPointerCancelsOpeningWithoutClearingTerminalInputFocus() {
        val state = SessionNavigatorState()
        rule.setContent {
            val requester = remember { FocusRequester() }
            var value by remember { mutableStateOf("") }
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp).testTag("host").onGloballyPositioned { state.openingBounds = it.boundsInRoot().let { area -> androidx.compose.ui.geometry.Rect(area.left, area.top, area.right, area.top + area.height * .8f) } }) {
                    SessionNavigator(state, { Text("Navigator") }, content = {
                        TextField(value, { value = it }, modifier = Modifier.focusRequester(requester).testTag("input"))
                    })
                }
            }
            LaunchedEffect(Unit) { requester.requestFocus() }
        }
        // Establish the fixture after window focus arrives before testing gesture retention.
        rule.onNodeWithTag("input").performClick().assertIsFocused()
        rule.onNodeWithTag("host").performTouchInput {
            down(0, Offset(width * .15f, center.y))
            moveTo(0, Offset(width * .8f, center.y), delayMillis = 200)
        }
        rule.runOnIdle { assertTrue(state.dragging); assertFalse(state.inputBlocked) }
        rule.onNodeWithTag("host").performTouchInput {
            down(1, Offset(width * .9f, center.y))
            up(1); up(0)
        }
        rule.runOnIdle { assertEquals(0f, state.target); assertFalse(state.inputBlocked) }
        rule.onNodeWithTag("input").assertIsFocused()
    }

    @Test fun contentAndDiagonalScrollNeverOpenNavigator() {
        val state = SessionNavigatorState()
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp).testTag("host").onGloballyPositioned { state.openingBounds = it.boundsInRoot().let { area -> androidx.compose.ui.geometry.Rect(area.left, area.top, area.right, area.top + area.height * .8f) } }) {
                    SessionNavigator(state, { Text("Navigator") }, content = { Text("Terminal") })
                }
            }
        }
        rule.onNodeWithTag("host").performTouchInput {
            swipe(Offset(width * .7f, center.y), Offset(width * .95f, center.y), 300)
        }
        rule.runOnIdle { assertEquals(0f, state.target); assertFalse(state.inputBlocked) }
        rule.onNodeWithTag("host").performTouchInput {
            swipe(Offset(5f, center.y), Offset(100f, center.y - 200), 300)
        }
        rule.onNodeWithTag("host").performTouchInput {
            swipe(Offset(width * .15f, height * .9f), Offset(width * .8f, height * .9f), 300)
        }
        rule.runOnIdle { assertEquals(0f, state.target); assertFalse(state.inputBlocked) }
    }

    @Test fun backClosesNavigatorBeforeDestinationBackHandler() {
        val state = SessionNavigatorState()
        var childBacks = 0
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        rule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            MaterialTheme {
                SessionNavigator(state, { Text("Navigator") }, content = {
                    BackHandler { childBacks++ }
                    Text("Destination")
                })
            }
        }
        rule.runOnIdle { state.target = 1f }
        rule.waitForIdle()
        rule.runOnIdle { dispatcher!!.onBackPressed() }
        rule.runOnIdle { assertEquals(0f, state.target); assertEquals(0, childBacks) }
    }

    @Test fun terminalSwipeOpensCancelledDragReturnsAndContentIsModal() {
        val state = SessionNavigatorState()
        var contentTaps = 0
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp).testTag("host").onGloballyPositioned { state.openingBounds = it.boundsInRoot().let { area -> androidx.compose.ui.geometry.Rect(area.left, area.top, area.right, area.top + area.height * .8f) } }) {
                    SessionNavigator(state, { Text("Navigator") }, content = { Button(onClick = { contentTaps++ }) { Text("Terminal action") } })
                }
            }
        }
        rule.onNodeWithTag("host").performTouchInput { swipe(Offset(width * .15f, center.y), Offset(width * .95f, center.y), 500) }
        rule.runOnIdle { assertEquals(1f, state.target); assertFalse(state.dragging) }
        rule.onNodeWithText("Navigator").assertIsDisplayed()
        rule.onNodeWithText("Terminal action").assertDoesNotExist()
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(width - 20f, center.y)); moveBy(Offset(-80f, 0f), 200); cancel()
        }
        rule.runOnIdle { assertEquals(1f, state.target); assertFalse(state.dragging); assertEquals(0, contentTaps) }
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(width - 20f, center.y))
            moveTo(Offset(0f, center.y), delayMillis = 400)
        }
        rule.runOnIdle { assertEquals(0f, state.target); assertTrue(state.dragging) }
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.runOnIdle { assertEquals(0f, state.target); assertFalse(state.dragging) }
    }
}
