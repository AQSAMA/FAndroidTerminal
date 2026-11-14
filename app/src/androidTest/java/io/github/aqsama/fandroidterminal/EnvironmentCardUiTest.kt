// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnvironmentCardUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val environment = LinuxEnvironment("00000000-0000-0000-0000-000000000001", "Ready workspace", "Ready")

    @Test fun anotherOperationBlocksManagementButAllowsOpeningThisReadyEnvironment() {
        var opened = false
        rule.setContent {
            MaterialTheme {
                EnvironmentCard(environment, count = 0, stage = null, managementBusy = true,
                    open = { opened = true }, files = {}, settings = {}, action = {}, cancel = {})
            }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.env_manage, environment.name)).assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.open_terminal)).assertIsEnabled().performClick()
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test fun longOperationPathsKeepCancelReachableAndHaveFullDetails() {
        val stage = "Backing up " + "deep/".repeat(100) + "notes.txt"
        var cancelled = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).heightIn(max = 320.dp)) {
                    EnvironmentCard(environment, count = 0, stage = stage, managementBusy = true,
                        open = {}, files = {}, settings = {}, action = {}, cancel = { cancelled = true })
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.cancel_label)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.operation_details)).performClick()
        rule.onNode(hasText(stage) and hasAnyAncestor(isDialog())).assertExists()
        rule.onNodeWithText(context.getString(R.string.close_label)).performClick()
        rule.onNodeWithText(context.getString(R.string.cancel_label)).performClick()
        rule.runOnIdle { assertTrue(cancelled) }
    }
}
