// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import org.junit.Rule
import org.junit.Test

class QuickControlsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun collapsedDockHasNoEmptyUtilityRow() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(393.dp).testTag("dock")) {
                    TerminalKeyControls(QuickKeyLayout.parse(QuickKeyLayout.DEFAULT).tokens, true,
                        remember { TerminalModifiers() }) {}
                }
            }
        }
        rule.onNodeWithTag("dock").assertHeightIsEqualTo(48.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithContentDescription(context.getString(R.string.show_extra_keys)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("dock").assertHeightIsEqualTo(96.dp)
    }
}
