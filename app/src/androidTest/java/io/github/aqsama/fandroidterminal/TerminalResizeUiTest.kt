// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.terminal.TerminalSurface
import org.connectbot.terminal.TerminalEmulatorFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TerminalResizeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pinchCommitsFontAndResizesSameEmulatorWithoutSendingInput() {
        val input = mutableListOf<Byte>()
        val emulator = TerminalEmulatorFactory.create(onKeyboardInput = { input.addAll(it.toList()) })
        var saved = 14
        rule.setContent {
            var font by remember { mutableIntStateOf(14) }
            MaterialTheme {
                TerminalSurface(emulator, remember { TerminalModifiers() }, Modifier.size(320.dp, 400.dp).testTag("terminal"),
                    onPaste = {}, fontSize = font, showKeyboard = false,
                    onFontSizeChanged = { saved = it; font = it })
            }
        }
        try {
            rule.waitUntil(10000) { emulator.dimensions.columns != 80 }
            val before = emulator.dimensions.columns
            rule.onNodeWithTag("terminal").performTouchInput {
                val x = center.x; val y = center.y
                down(0, Offset(x - 30, y)); down(1, Offset(x + 30, y))
                repeat(10) { i ->
                    moveTo(0, Offset(x - 30 - i * 6, y), delayMillis = 20)
                    moveTo(1, Offset(x + 30 + i * 6, y), delayMillis = 20)
                }
                up(0); up(1)
            }
            rule.waitUntil(10000) { emulator.dimensions.columns < before }
            rule.runOnIdle { assertTrue(saved in 15..28); assertTrue(input.isEmpty()) }
        } finally { rule.runOnIdle { emulator.close() } }
    }

    @Test fun quickFontChangePersistsAndKeepsOtherPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = Preferences(context)
        val before = preferences.font
        val palette = preferences.palette
        val keys = preferences.keys
        try {
            rule.runOnIdle { preferences.setTerminalFont(100) }
            assertEquals(28, Preferences(context).font)
            rule.runOnIdle { preferences.setTerminalFont(-5) }
            assertEquals(8, Preferences(context).font)
            assertEquals(palette, Preferences(context).palette)
            assertEquals(keys, Preferences(context).keys)
        } finally { rule.runOnIdle { preferences.setTerminalFont(before) } }
    }
}
