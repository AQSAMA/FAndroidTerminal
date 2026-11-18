// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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
            rule.activity.window.decorView.hasWindowFocus() && rule.onAllNodes(
                hasContentDescription(rule.activity.getString(R.string.status_running))
            ).fetchSemanticsNodes().isNotEmpty()
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
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.terminal_input_menu)).performClick()
    }

    private fun newAndroidSession() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.new_session)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.device_shell_summary)).performClick()
    }

    private fun sessionLabel(name: String, archived: Boolean = false): String = rule.activity.getString(
        if (archived) R.string.session_restore_a11y else R.string.session_open_a11y,
        name, rule.activity.getString(R.string.android_shell),
    )

    private fun sessionMenuAction(name: String, label: Int) {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.session_actions_a11y, name, rule.activity.getString(R.string.android_shell))).performClick()
        rule.onNodeWithText(rule.activity.getString(label)).performClick()
    }

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
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.new_session))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitForIdle()
        rule.waitUntil(10000) { imeVisible() }
        rule.onNodeWithContentDescription(hide).performClick()
        rule.waitUntil(10000) { !imeVisible() }
        rule.onNodeWithContentDescription(show).performClick()
        rule.waitUntil(10000) { imeVisible() }
    }

    @Test fun backReturnsThroughSettingsAndDoesNotFinishTerminalActivity() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.session_actions)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_label)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_appearance)).performClick()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(rule.activity.getString(R.string.settings_extra_keys)).performScrollTo().assertIsDisplayed()
        repeat(3) { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() } }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.session_actions)).assertIsDisplayed()
        org.junit.Assert.assertFalse(rule.activity.isFinishing)
    }

    @Test fun commandSearchFocusesAndClearsQuery() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.commands_title)).performClick()
        rule.onNodeWithText("pwd").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performClick().assertIsFocused().performTextInput("no-match-unique-query")
        rule.onNodeWithText(rule.activity.getString(R.string.no_matching_actions)).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.clear_search)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.write_command)).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.dismiss_command_surface)).assertIsDisplayed()
    }

    @Test fun toolbarBackMatchesSettingsHierarchy() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back_label)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.terminal_tools)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back_label)).performClick()
        rule.onNodeWithContentDescription("Session actions").assertIsDisplayed()
    }

    @Test fun settingsDraftSurvivesTerminalNavigationAndRecreation() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val before = Preferences(rule.activity).font
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").performClick()
        val increase = before < 28
        rule.onNodeWithContentDescription(if (increase) "Larger terminal text" else "Smaller terminal text").performScrollTo().performClick()
        val draft = before + if (increase) 1 else -1
        // The newly visible save footer shrinks the scrollable editor after this tap.
        rule.onNodeWithText("$draft sp").assertExists().performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back_label)).performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back_label)).performClick()
        rule.onNodeWithContentDescription("Session actions").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithText("$draft sp").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Unsaved changes").assertIsDisplayed()
        rule.runOnIdle { assertEquals(before, Preferences(rule.activity).font) }
    }

    @Test fun archiveUndoRestoresSelectedServiceSessionAcrossRecreation() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        newAndroidSession()
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).assertIsSelected()
        sessionMenuAction("Shell 2", R.string.archive_session)
        rule.onNodeWithText(rule.activity.getString(R.string.undo_label)).performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).assertIsSelected()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Open sidebar")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).assertIsSelected()
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

    @Test fun commandDraftSurvivesClosingAndRecreationWithoutRunning() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        fun openDraft() {
            openInputMenu()
            rule.onNodeWithText(rule.activity.getString(R.string.write_command)).performClick()
        }
        openDraft()
        val command = "printf draft-no-run"
        rule.onNodeWithText(rule.activity.getString(R.string.command_label)).performTextInput(command)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.dismiss_command_surface)).performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription(rule.activity.getString(R.string.terminal_input_menu)) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        openDraft()
        rule.onNodeWithText(command).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.review_and_send)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel_label)).performClick()
        openDraft()
        rule.onNodeWithText(command).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.dismiss_command_surface)).performClick()
        openInputMenu()
        rule.onNodeWithText(rule.activity.getString(R.string.select_visible_text)).performClick()
        openInputMenu()
        rule.onNodeWithText(rule.activity.getString(R.string.close_selection)).assertIsDisplayed()
        rule.onNode(hasContentDescription(rule.activity.getString(R.string.show_keyboard)) or hasContentDescription(rule.activity.getString(R.string.hide_keyboard))).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.copy_selection)).performClick()
        rule.runOnIdle {
            val clip = rule.activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip
            org.junit.Assert.assertFalse(clip?.getItemAt(0)?.coerceToText(rule.activity)?.contains("draft-no-run") == true)
        }
    }

    @Test fun newSessionChoicesAndQuickSwitcherKeepTheActiveIdentity() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.new_session)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.device_shell_summary)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.workspace_root_help)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.workspace_advanced_modes)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.workspace_shizuku_help)).assertExists()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.dismiss_panel)).performClick()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()

        newAndroidSession()
        rule.onNodeWithText("Shell 2").performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.workspace_current_session)).assertIsDisplayed()
        rule.onNodeWithText("Shell 1").performClick()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()
    }

    @Test fun shellExampleOpensReviewForTheActiveTerminal() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.commands_title)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.workspace_command_pwd)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.run_command)).assertIsDisplayed()
        rule.onNodeWithText("pwd").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.session_identity, "Shell 1", rule.activity.getString(R.string.android_shell))).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel_label)).performClick()
        rule.onNodeWithText("Shell 1").assertIsDisplayed()
    }

    @Test fun multilinePasteConfirmationIdentifiesItsEnvironment() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { rule.activity.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("test", "echo first\necho second")) }
        openInputMenu()
        rule.onNodeWithText(rule.activity.getString(R.string.paste_label)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.session_identity, "Shell 1", rule.activity.getString(R.string.android_shell))).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel_label)).performClick()
    }

    @Test fun menuArchiveCanBeRestoredAndCloseCanBeCancelled() {
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        sessionMenuAction("Shell 1", R.string.archive_session)
        rule.onNodeWithText(rule.activity.getString(R.string.archived_count, 1)).performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 1", archived = true)).performClick()
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        sessionMenuAction("Shell 1", R.string.close_session_menu)
        rule.onNodeWithText(rule.activity.getString(R.string.close_named_session, "Shell 1", rule.activity.getString(R.string.android_shell))).assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 1")).assertIsDisplayed()
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

    @Test fun sessionsSurviveActivityRecreationAndCanBeClosed() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Session actions") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        newAndroidSession()
        rule.onNodeWithText("Shell 2").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription(sessionLabel("Shell 1")).assertIsDisplayed()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).assertIsDisplayed()
        rule.onNodeWithContentDescription(sessionLabel("Shell 2")).performClick()
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText("Close session…").performClick()
        rule.onNodeWithText("Close session").performClick()
        rule.onNodeWithText("Android shell", substring = true).assertIsDisplayed()
        screenshot("terminal-tabs")
        rule.onNodeWithContentDescription("Session actions").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.archive_session)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.close_session_menu)).assertIsDisplayed()
        screenshot("session-information")
        finalOutputSurvivesProcessExit()
    }
}
