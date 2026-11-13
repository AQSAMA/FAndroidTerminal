// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommandDraftUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rejectedOversizedPasteShowsErrorAndPreservesDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val draft = CommandDraft().apply { text = "printf preserved" }
        rule.setContent { MaterialTheme { CommandDraftSheet(draft, "Shell 1 — Android shell", true, {}, {}) } }
        rule.onNodeWithText(context.getString(R.string.command_label)).performTextInput("x".repeat(8193))
        rule.onNodeWithText(context.getString(R.string.command_too_large)).assertExists()
        rule.runOnIdle { assertEquals("printf preserved", draft.text) }
        rule.onNodeWithText(context.getString(R.string.command_label)).performTextClearance()
        rule.onNodeWithText(context.getString(R.string.command_too_large)).assertDoesNotExist()
    }
}
