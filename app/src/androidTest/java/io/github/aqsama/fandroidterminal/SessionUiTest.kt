// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.SessionManager
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import org.junit.Assert.assertEquals
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
        // UTP uninstalls the target after testing and removes its app-owned files.
        // A shell-created Download file survives teardown long enough for CI to pull it.
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/FAndroidTerminal-$name.png"
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun finalOutputSurvivesProcessExit() {
        lateinit var manager: SessionManager
        lateinit var session: TerminalSession
        rule.runOnIdle {
            manager = SessionManager(rule.activity.applicationContext)
            session = manager.create()
        }
        try {
            rule.waitUntil(10_000) { session.status.value == SessionStatus.Running }
            rule.runOnIdle {
                session.emulator.pasteText(
                    "i=0; while [ \$i -lt 2000 ]; do printf 'drain-output-line\\n'; i=\$((i+1)); done; " +
                    "printf '\\nhttps://final-output.invalid/complete\\n'; exit 23\r"
                )
            }
            rule.waitUntil(15_000) { session.status.value is SessionStatus.Exited }
            assertEquals(23, (session.status.value as SessionStatus.Exited).code)
            rule.waitUntil(5_000) {
                session.emulator.getUrls().any { it.url == "https://final-output.invalid/complete" }
            }
        } finally { rule.runOnIdle { manager.close() } }
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
        finalOutputSurvivesProcessExit()
    }
}
