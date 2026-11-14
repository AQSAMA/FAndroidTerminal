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
        rule.onNodeWithContentDescription(context.getString(R.string.key_arrow_left)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.show_extra_keys)).performClick()
        listOf(R.string.key_arrow_up, R.string.key_arrow_down, R.string.key_arrow_left, R.string.key_arrow_right).forEach { rule.onNodeWithContentDescription(context.getString(it)).assertIsDisplayed() }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithContentDescription(context.getString(R.string.hide_extra_keys)).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.key_arrow_left)).assertDoesNotExist()
    }
}
