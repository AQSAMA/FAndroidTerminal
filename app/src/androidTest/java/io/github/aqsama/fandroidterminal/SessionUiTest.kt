// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SessionUiTest {
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun stopSessions() {
        rule.activity.startService(Intent(rule.activity, TerminalService::class.java).setAction("terminal.STOP"))
    }

    @Test fun sessionsSurviveActivityRecreationAndCanBeClosed() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Shell 1")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("New session").performClick()
        rule.onNodeWithText("Shell 2").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()
        rule.onNodeWithText("Shell 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close Shell 2").performClick()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Session and build information").performClick()
        rule.onNodeWithText("App permissions").assertIsDisplayed()
    }
}
