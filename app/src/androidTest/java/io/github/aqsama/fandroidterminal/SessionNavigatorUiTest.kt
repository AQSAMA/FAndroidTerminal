// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionNavigatorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun backClosesNavigatorBeforeDestinationBackHandler() {
        val state = SessionNavigatorState()
        var childBacks = 0
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        rule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            MaterialTheme {
                SessionNavigator(state, { Text("Navigator") }, {
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

    @Test fun edgeOpensCancelledDragReturnsAndContentIsModal() {
        val state = SessionNavigatorState()
        var contentTaps = 0
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp).testTag("host")) {
                    SessionNavigator(state, { Text("Navigator") }, { Button(onClick = { contentTaps++ }) { Text("Terminal action") } })
                }
            }
        }
        rule.onNodeWithTag("host").performTouchInput { swipe(Offset(2f, center.y), Offset(width * .8f, center.y), 500) }
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
