// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** Typed destinations. The terminal is always the root; everything else is pushed above it. */
sealed interface Screen {
    data object Terminal : Screen
    data object Environments : Screen
    data class Environment(val id: String) : Screen
    data class Files(val id: String) : Screen
    data class Packages(val environmentId: String? = null, val query: String = "") : Screen
    data object Launchers : Screen
    data object Backup : Screen
    data object Settings : Screen
    data object Appearance : Screen
    data object Keyboard : Screen
    data object TerminalBehavior : Screen
    data object Access : Screen
    data object GuestDefaults : Screen
    data object About : Screen

    /** Compact string form for saved instance state. Explicit names survive R8 renaming. */
    fun encode(): String = when (this) {
        is Environment -> "Environment\u0000$id"
        is Files -> "Files\u0000$id"
        is Packages -> "Packages\u0000${environmentId.orEmpty()}\u0000$query"
        Terminal -> "Terminal"; Environments -> "Environments"; Launchers -> "Launchers"; Backup -> "Backup"
        Settings -> "Settings"; Appearance -> "Appearance"; Keyboard -> "Keyboard"; TerminalBehavior -> "TerminalBehavior"
        Access -> "Access"; GuestDefaults -> "GuestDefaults"; About -> "About"
    }

    companion object {
        fun decode(value: String): Screen {
            val parts = value.split('\u0000')
            return when (parts[0]) {
                "Environment" -> Environment(parts[1])
                "Files" -> Files(parts[1])
                "Packages" -> Packages(parts[1].ifEmpty { null }, parts.getOrElse(2) { "" })
                "Environments" -> Environments; "Launchers" -> Launchers; "Backup" -> Backup; "Settings" -> Settings
                "Appearance" -> Appearance; "Keyboard" -> Keyboard; "TerminalBehavior" -> TerminalBehavior
                "Access" -> Access; "GuestDefaults" -> GuestDefaults; "About" -> About
                else -> Terminal
            }
        }
    }
}

/** Back pops one destination; it never leaves the app from a pushed screen. */
@Stable
class Navigator(initial: List<Screen>) {
    var stack by mutableStateOf(initial.ifEmpty { listOf(Screen.Terminal) }); private set
    val current: Screen get() = stack.last()
    val canGoBack get() = stack.size > 1

    fun push(screen: Screen) {
        if (screen == Screen.Terminal) { stack = listOf(Screen.Terminal); return }
        if (current == screen) return
        // Re-opening a destination already in the stack returns to it instead of growing a loop.
        val existing = stack.indexOf(screen)
        stack = if (existing > 0) stack.take(existing + 1) else listOf(Screen.Terminal) + (stack.drop(1) + screen).takeLast(15)
    }
    /** Replaces the top destination, e.g. switching settings sections from the rail on tablets. */
    fun replace(screen: Screen) { stack = if (stack.size > 1) stack.dropLast(1) + screen else stack + screen }
    fun pop(): Boolean { if (!canGoBack) return false; stack = stack.dropLast(1); return true }
    fun home() { stack = listOf(Screen.Terminal) }

    companion object {
        val saver = Saver<Navigator, List<String>>(save = { nav -> nav.stack.map { it.encode() } }, restore = { saved -> Navigator(saved.map(Screen::decode)) })
    }
}

@Composable
fun rememberNavigator(start: Screen = Screen.Terminal): Navigator =
    rememberSaveable(saver = Navigator.saver) { Navigator(if (start == Screen.Terminal) listOf(start) else listOf(Screen.Terminal, start)) }

internal val Screen.isSettings get() = this in listOf(Screen.Settings, Screen.Appearance, Screen.Keyboard, Screen.TerminalBehavior, Screen.Access, Screen.GuestDefaults, Screen.About)
