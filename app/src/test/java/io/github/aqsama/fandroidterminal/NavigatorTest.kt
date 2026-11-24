// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import org.junit.Assert.*
import org.junit.Test

class NavigatorTest {
    @Test fun backStackSurvivesSavedStateAndNeverPopsTheTerminal() {
        val nav = Navigator(listOf(Screen.Terminal))
        nav.push(Screen.Settings); nav.push(Screen.Appearance); nav.push(Screen.Packages("d1", "fish shell"))
        val restored = Navigator(nav.stack.map { Screen.decode(it.encode()) })
        assertEquals(nav.stack, restored.stack)
        assertTrue(restored.pop()); assertTrue(restored.pop()); assertTrue(restored.pop())
        assertFalse(restored.pop()); assertEquals(Screen.Terminal, restored.current)
    }

    @Test fun deepStacksKeepTheTerminalAsRoot() {
        val nav = Navigator(listOf(Screen.Terminal))
        repeat(30) { nav.push(Screen.Files("e$it")) }
        assertEquals(Screen.Terminal, nav.stack.first()); assertTrue(nav.stack.size <= 16)
    }

    @Test fun reopeningAScreenReturnsToItInsteadOfLooping() {
        val nav = Navigator(listOf(Screen.Terminal))
        nav.push(Screen.Settings); nav.push(Screen.Keyboard); nav.push(Screen.Settings)
        assertEquals(listOf(Screen.Terminal, Screen.Settings), nav.stack)
    }
}
