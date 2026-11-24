// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.SessionManager
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SessionUiTest {
    @get:Rule(order = 0) val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private var previousHardwareIme: String? = null
    private fun shellSetting(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    @Before fun waitForFocusedRunningTerminal() {
        previousHardwareIme = shellSetting("settings get secure show_ime_with_hard_keyboard")
        shellSetting("settings put secure show_ime_with_hard_keyboard 1")
        rule.waitUntil(10000) {
            rule.activity.window.decorView.hasWindowFocus() && rule.onAllNodes(hasTestTag("terminal-viewport")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun stopSessions() {
        when (previousHardwareIme) {
            "0", "1" -> shellSetting("settings put secure show_ime_with_hard_keyboard $previousHardwareIme")
            "null" -> shellSetting("settings delete secure show_ime_with_hard_keyboard")
        }
        rule.activity.startService(Intent(rule.activity, TerminalService::class.java).setAction("terminal.STOP"))
    }

    private fun openInputMenu() {
        val expand = rule.activity.getString(R.string.show_extra_keys)
        if (rule.onAllNodes(hasContentDescription(expand)).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription(expand).performClick()
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.terminal_input_menu)).performClick()
    }

    private fun sessionLabel(name: String, archived: Boolean = false): String = rule.activity.getString(
        if (archived) R.string.session_restore_a11y else R.string.session_open_a11y,
        name, rule.activity.getString(R.string.android_shell),
    )

    @Test fun terminalGridExpandsToTheWholeViewportAfterClosingIme() {
        var bound: TerminalService? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) { bound = (binder as TerminalService.LocalBinder).service }
            override fun onServiceDisconnected(name: android.content.ComponentName) { bound = null }
        }
        fun imeVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        rule.runOnIdle { rule.activity.bindService(Intent(rule.activity, TerminalService::class.java), connection, android.content.Context.BIND_AUTO_CREATE) }
        try {
            rule.waitUntil(10000) { bound?.sessions?.sessions?.value?.firstOrNull()?.status?.value == SessionStatus.Running }
            val session = bound!!.sessions.sessions.value.first()
            val emulator = session.emulator
            rule.waitUntil(10000) { !imeVisible() && emulator.dimensions.heightPixels >= rule.onNodeWithTag("terminal-viewport").fetchSemanticsNode().boundsInRoot.height * .9f }
            val fullRows = emulator.dimensions.rows
            repeat(2) {
                rule.onNodeWithContentDescription(rule.activity.getString(R.string.show_keyboard)).performClick()
                rule.waitUntil(10000) { imeVisible() && emulator.dimensions.rows < fullRows }
                rule.onNodeWithContentDescription(rule.activity.getString(R.string.hide_keyboard)).performClick()
                rule.waitUntil(10000) { !imeVisible() && emulator.dimensions.rows >= fullRows && emulator.dimensions.heightPixels >= rule.onNodeWithTag("terminal-viewport").fetchSemanticsNode().boundsInRoot.height * .9f }
            }
            rule.runOnIdle { org.junit.Assert.assertSame(emulator, session.emulator) }
        } finally { rule.runOnIdle { rule.activity.unbindService(connection) } }
    }

    @Test fun keyboardReopensAfterBackAndStaysVisibleWhenAddingSession() {
        fun imeVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        val show = rule.activity.getString(R.string.show_keyboard)
        val hide = rule.activity.getString(R.string.hide_keyboard)
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription(show)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(show).performClick()
        rule.waitUntil(10000) { imeVisible() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitUntil(10000) { !imeVisible() }
        rule.onNodeWithContentDescription(show).performClick()
        rule.waitUntil(10000) { imeVisible() }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.new_session)).performClick()
        rule.waitForIdle()
        rule.waitUntil(10000) { imeVisible() }
        rule.onNodeWithContentDescription(hide).performClick()
        rule.waitUntil(10000) { !imeVisible() }
        rule.onNodeWithContentDescription(show).performClick()
        rule.waitUntil(10000) { imeVisible() }
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

    @Test fun confirmedCommandDraftRunsInTheBoundServiceSession() {
        var bound: TerminalService? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) { bound = (binder as TerminalService.LocalBinder).service }
            override fun onServiceDisconnected(name: android.content.ComponentName) { }
        }
        rule.runOnIdle { rule.activity.bindService(Intent(rule.activity, TerminalService::class.java), connection, android.content.Context.BIND_AUTO_CREATE) }
        try {
            rule.waitUntil(10000) { bound?.sessions?.sessions?.value?.firstOrNull()?.status?.value == SessionStatus.Running }
            val session = bound!!.sessions.sessions.value.first()
            val emulator = session.emulator
            openInputMenu()
            rule.onNodeWithText(rule.activity.getString(R.string.write_command)).performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.command_label)).performTextInput("printf '\\nhttps://draft-proof.invalid/%s\\n' completed")
            rule.onNodeWithText(rule.activity.getString(R.string.review_and_send)).performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.run_label)).performClick()
            rule.waitUntil(10000) { emulator.getUrls().any { it.url == "https://draft-proof.invalid/completed" } }
            rule.runOnIdle {
                org.junit.Assert.assertSame(emulator, session.emulator)
                assertEquals(SessionStatus.Running, session.status.value)
            }
        } finally { rule.runOnIdle { rule.activity.unbindService(connection) } }
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
                val archived = manager.create(environmentId = id)
                manager.archive(archived.id, true)
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

    @Test fun backPopsSettingsOneLevelAtATimeAndNeverLeavesTheTerminal() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.session_actions)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_label)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_appearance)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.terminal_color_scheme)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_extra_keys)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.onNodeWithTag("terminal-viewport").assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.runOnIdle { org.junit.Assert.assertFalse(rule.activity.isFinishing) }
    }

    @Test fun sessionsSurviveActivityRecreationAndCanBeClosed() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.new_session)).performClick()
        rule.onNodeWithText("Shell 2").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 1")).assertIsDisplayed()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).assertIsDisplayed()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).performClick()
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText("Close session…").performClick()
        rule.onNodeWithText("Close session").performClick()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()
        screenshot("terminal-tabs")
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.archive_session)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.close_session_menu)).assertIsDisplayed()
        screenshot("session-information")
        finalOutputSurvivesProcessExit()
    }
}
