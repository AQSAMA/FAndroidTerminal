// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class TerminalInputDockUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun expandedKeysExposeAllArrowsAndSurviveRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(96.dp)) {
                    TerminalKeyControls(listOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right"),
                        true, true, remember { TerminalModifiers() }, null, remember { FocusRequester() }, {}, {}, {}, {}) {}
                }
            }
        }
        rule.onNodeWithContentDescription("Left").assertIsNotDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.show_extra_keys)).performClick()
        listOf("Up", "Down", "Left", "Right").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithContentDescription(context.getString(R.string.hide_extra_keys)).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Left").assertIsNotDisplayed()
    }
}
