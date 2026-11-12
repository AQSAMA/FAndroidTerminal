// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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

    @Test fun swipeArchiveCanBeRestoredAndSwipeCloseCanBeCancelled() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Select session Shell 1").performTouchInput { swipeRight() }
        rule.onNodeWithText("Archived sessions").performClick()
        rule.onNodeWithContentDescription("Restore and open Shell 1").performClick()
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Select session Shell 1").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithContentDescription("Select session Shell 1").assertIsDisplayed()
    }

    @Test fun archivingKeepsTheSameProcessAndTerminalUntilRestored() {
        lateinit var manager: SessionManager
        lateinit var session: TerminalSession
        rule.runOnIdle { manager = SessionManager(rule.activity.applicationContext); session = manager.create() }
        try {
            rule.waitUntil(10000) { session.status.value == SessionStatus.Running }
            val emulator = session.emulator
            rule.runOnIdle {
                session.emulator.pasteText("sleep 1; printf '\\nhttps://archive-proof.invalid/survived\\n'\r")
                manager.archive(session.id, true)
            }
            rule.waitUntil(10000) { session.emulator.getUrls().any { it.url == "https://archive-proof.invalid/survived" } }
            rule.activityRule.scenario.recreate()
            rule.runOnIdle {
                org.junit.Assert.assertTrue(session.archived)
                org.junit.Assert.assertSame(emulator, manager.sessions.value.single().emulator)
                assertEquals(SessionStatus.Running, session.status.value)
                manager.archive(session.id, false)
                org.junit.Assert.assertFalse(session.archived)
                org.junit.Assert.assertSame(emulator, session.emulator)
            }
        } finally { rule.runOnIdle { manager.close() } }
    }

    @Test fun liveGuestPreventsFileAccessBeforeAParentCanBeSwapped() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
        val root = java.nio.file.Files.createTempDirectory(rule.activity.cacheDir.toPath(), "guest-access-")
        val parent = java.nio.file.Files.createDirectory(root.resolve("parent"))
        java.nio.file.Files.write(parent.resolve("note"), "preserve".toByteArray())
        val outside = java.nio.file.Files.createDirectory(root.resolve("outside"))
        java.nio.file.Files.write(outside.resolve("note"), "outside".toByteArray())
        val id = java.util.UUID.randomUUID().toString()
        lateinit var manager: SessionManager
        var invoked = false
        try {
            rule.runOnIdle {
                manager = SessionManager(rule.activity.applicationContext)
                manager.create(environmentId = id)
                val environments = io.github.aqsama.fandroidterminal.sessions.EnvironmentManager(rule.activity.applicationContext, scope)
                val result = runCatching {
                    environments.fileOperation(id, manager, "Read") {
                        invoked = true
                        java.nio.file.Files.move(parent, root.resolve("old-parent"))
                        java.nio.file.Files.createSymbolicLink(parent, outside)
                        it.read("/parent/note")
                    }
                }
                org.junit.Assert.assertTrue(result.exceptionOrNull()?.message?.contains("Close") == true)
                org.junit.Assert.assertFalse(invoked)
                assertEquals("preserve", parent.resolve("note").toFile().readText())
                manager.close()
            }
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.deleteTree(root.toFile())
        }
    }

    @Test fun sessionsSurviveActivityRecreationAndCanBeClosed() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText("New session").performClick()
        rule.onNodeWithText("Shell 2").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Switch session").performClick()
        rule.onNodeWithContentDescription("Select session Shell 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Select session Shell 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Select session Shell 2").performClick()
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText("Close session…").performClick()
        rule.onNodeWithText("Close session").performClick()
        rule.onNodeWithText("Android shell").assertIsDisplayed()
        screenshot("terminal-tabs")
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText("Android shell · app permissions").assertIsDisplayed()
        screenshot("session-information")
        finalOutputSurvivesProcessExit()
    }
}
