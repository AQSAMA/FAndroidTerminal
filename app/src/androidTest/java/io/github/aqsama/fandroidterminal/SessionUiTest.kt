// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
    @get:Rule(order = 0) val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @After fun stopSessions() {
        rule.activity.startService(Intent(rule.activity, TerminalService::class.java).setAction("terminal.STOP"))
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
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
        screenshot("terminal-tabs")
        rule.onNodeWithContentDescription("Session and build information").performClick()
        rule.onNodeWithText("App permissions").assertIsDisplayed()
        screenshot("session-information")
    }
}
