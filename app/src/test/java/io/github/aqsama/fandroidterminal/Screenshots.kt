// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.aqsama.fandroidterminal.designsystem.TerminalTheme
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.terminal.TerminalSchemes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app chrome on the JVM for design review (no emulator, no device):
 *   ./gradlew :app:testDebugUnitTest --tests '*Screenshots*' -Proborazzi.record
 * PNGs land in build/screenshots. The native terminal cannot run on the JVM, so its viewport
 * shows [TerminalPreview] in the same scheme and font.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class Screenshots {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val debian = LinuxEnvironment("d1", "Debian Work", state = "Ready", distribution = "debian", release = "13.7")
    private val ubuntu = LinuxEnvironment("u1", "Ubuntu Lab", state = "Ready", distribution = "ubuntu", release = "24.04")
    private val alpine = LinuxEnvironment("a1", "Alpine Mini", state = "Ready", distribution = "alpine", release = "3.22")
    private val sessions = listOf(
        SessionListEntry("s1", "Shell 1", null, false, SessionStatus.Running),
        SessionListEntry("s2", "Terminal 1", "d1", false, SessionStatus.Running),
        SessionListEntry("s3", "opencode", "d1", false, SessionStatus.Running),
        SessionListEntry("s4", "Terminal 1", "u1", false, SessionStatus.Exited(0)),
        SessionListEntry("s5", "build log", "d1", true, SessionStatus.Running),
    )

    private fun preferences(scheme: String = "Graphite") = Preferences(context).apply {
        setTerminalScheme(scheme); setTerminalTypeface("JetBrainsMono"); setTerminalFont(14); setAccentSource(AccentSource.Terminal)
    }

    private fun shot(name: String, dark: Boolean = true, rtl: Boolean = false, scheme: String = "Graphite", content: @Composable (Preferences) -> Unit) {
        val prefs = preferences(scheme)
        captureRoboImage("${System.getProperty("roborazzi.output.dir") ?: "build/screenshots"}/${name}.png") {
            val seed = Color(TerminalSchemes.get(prefs.palette).ansi[4])
            TerminalTheme(dark, seed = seed) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content(prefs) }
                }
            }
        }
    }

    private val noActions = WorkspaceActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {})

    @Composable
    private fun Workspace(prefs: Preferences, expanded: Boolean = false, selected: String = "s2") {
        val names = mapOf(null to "Android", "d1" to "Debian Work", "u1" to "Ubuntu Lab")
        val dist = mapOf(null to null, "d1" to "debian", "u1" to "ubuntu")
        val tabs = sessions.filterNot { it.archived }.map { WorkspaceTab(it, names[it.environmentId]!!, dist[it.environmentId], if (it.environmentId == null) "Android · App UID" else "${names[it.environmentId]} · PRoot") }
        WorkspaceChrome(tabs.first { it.entry.id == selected }, tabs, wide = false, canOpen = true, readyEnvironments = listOf(debian, ubuntu),
            showLinuxHint = false, progress = null, actions = noActions) {
            Column(Modifier.fillMaxSize()) {
                val scheme = TerminalSchemes.get(prefs.palette)
                TerminalPreview(scheme, prefs.terminalFont, prefs.font, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp))
                TerminalKeyControls(QuickKeyLayout.parse(prefs.keys), true, remember { TerminalModifiers() }, null,
                    DockActions({}, {}, {}, {}, {}, toggleKeyboard = {}, imeVisible = false), initiallyExpanded = expanded)
            }
        }
    }

    @Test fun workspace() = shot("01-workspace-dark") { Workspace(it) }
    @Test fun workspaceExpanded() = shot("02-workspace-expanded-dracula", scheme = "Dracula") { Workspace(it, expanded = true) }
    @Test fun workspaceLight() = shot("03-workspace-light", dark = false, scheme = "GitHubLight") { Workspace(it, selected = "s1") }
    @Config(sdk = [35], qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun workspaceRtl() = shot("04-workspace-rtl", rtl = true, scheme = "TokyoNight") { Workspace(it) }
    @Test fun drawer() = shot("05-drawer") {
        val alpineBusy = alpine.copy(state = "Downloading Linux image…")
        val drawerSessions = listOf(
            SessionListEntry("s1", "Shell 1", null, false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.Android),
            SessionListEntry("s6", "adb-bridge", null, false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.Shizuku),
            SessionListEntry("s2", "Terminal 1", "d1", false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s3", "opencode", "d1", false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s4", "Terminal 1", "u1", false, SessionStatus.Exited(0), io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s7", "kernel-dbg", "u1", false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.Chroot),
            SessionListEntry("s5", "build log", "d1", true, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
        )
        val drawerLaunchers = listOf(
            io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher("l1", "OpenCode", "d1", "/root", listOf("/usr/local/bin/opencode")),
            io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher("l2", "htop", "d1", "/root", listOf("/usr/bin/htop")),
        )
        Surface(Modifier.fillMaxHeight().width(340.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            WorkspaceSidebarContent(
                screen = Screen.Terminal,
                busy = setOf("a1"),
                sessions = drawerSessions,
                environments = listOf(debian, ubuntu, alpineBusy),
                selectedId = "s2",
                navigate = {},
                select = {},
                action = { _, _ -> },
                newSession = {},
                launchers = drawerLaunchers,
                archivedInitiallyExpanded = true,
            )
        }
    }
    @Config(sdk = [35], qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun drawerRtl() = shot("05b-drawer-rtl", rtl = true) {
        val alpineBusy = alpine.copy(state = "جارٍ تنزيل صورة Linux…")
        val drawerSessions = listOf(
            SessionListEntry("s1", "Shell 1", null, false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.Root),
            SessionListEntry("s2", "Terminal 1", "d1", false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s3", "opencode", "d1", false, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s4", "Terminal 1", "u1", false, SessionStatus.Exited(0), io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
            SessionListEntry("s5", "build log", "d1", true, SessionStatus.Running, io.github.aqsama.fandroidterminal.runtime.ExecutionMode.PRoot),
        )
        val drawerLaunchers = listOf(
            io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher("l1", "OpenCode", "d1", "/root", listOf("/usr/local/bin/opencode")),
        )
        Surface(Modifier.fillMaxHeight().width(340.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            WorkspaceSidebarContent(
                screen = Screen.Terminal,
                busy = setOf("a1"),
                sessions = drawerSessions,
                environments = listOf(debian, ubuntu, alpineBusy),
                selectedId = "s2",
                navigate = {},
                select = {},
                action = { _, _ -> },
                newSession = {},
                launchers = drawerLaunchers,
            )
        }
    }
    @Test fun settingsHome() = shot("06-settings-home") { prefs -> Column { ScreenBar("Settings", R.drawable.ic_settings, {}); SettingsHome(prefs, 3) {} } }
    @Test fun appearance() = shot("07-appearance", scheme = "Catppuccin") { prefs -> Column { ScreenBar("Appearance", R.drawable.ic_palette, {}); AppearanceSettings(prefs) } }
    @Test fun appearanceLight() = shot("08-appearance-light", dark = false, scheme = "Paper") { prefs -> Column { ScreenBar("Appearance", R.drawable.ic_palette, {}); AppearanceSettings(prefs) } }
    @Test fun keyboard() = shot("09-keyboard") { prefs -> Column { ScreenBar("Quick keys", R.drawable.ic_keyboard, {}); KeyboardSettings(prefs) } }
    @Test fun terminalBehavior() = shot("10-terminal-settings") { prefs -> Column { ScreenBar("Terminal", R.drawable.ic_terminal, {}); TerminalBehaviorSettings(prefs, listOf(debian)) } }
    @Test fun environments() = shot("11-environments") {
        Column {
            ScreenBar("Environments", R.drawable.ic_environments, {})
            EnvironmentList(listOf(debian, ubuntu, alpine), mapOf(null to 1, "d1" to 2, "u1" to 1), 1, mapOf("a1" to "Downloading Alpine…"), true,
                dismissError = {}, install = {}, restore = {}, restoreDetails = {}, open = {}, files = {}, settings = {}, manageSessions = {}, action = { _, _ -> }, cancel = {})
        }
    }
    @Config(sdk = [35], qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun settingsRtl() = shot("12-settings-rtl", rtl = true) { prefs -> Column { ScreenBar(androidx.compose.ui.res.stringResource(R.string.settings_label), R.drawable.ic_settings, {}); SettingsHome(prefs, 3) {} } }
}
