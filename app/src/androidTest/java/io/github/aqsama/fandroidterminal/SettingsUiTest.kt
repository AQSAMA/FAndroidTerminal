// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Regression: saving one settings page must not commit drafts from another page. */
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val original = Preferences(context)
    private val oldFont = original.font
    private val oldPalette = original.palette
    private val oldKeys = original.keys
    private val oldCommands = original.snippets

    @After fun restorePreferences() = runBlocking { original.save(oldFont, oldPalette, oldKeys, oldCommands) }

    @Test fun savingKeysLeavesAppearanceDraftUnsaved() {
        runBlocking { original.save(14, "Midnight", "Esc Ctrl Tab", "") }
        rule.setContent { MaterialTheme { SettingsPage(original) } }
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithContentDescription("Larger terminal text").performClick()
        rule.onNodeWithText("Unsaved changes").assertIsDisplayed()
        rule.onNodeWithText("All settings").performClick()
        rule.onNodeWithText("Extra keyboard keys").performClick()
        rule.onNodeWithText("Home").performClick()
        rule.onNodeWithText("Save changes").performClick()
        rule.waitUntil(5000) { "Home" in original.keys }
        assertEquals(14, original.font)
        rule.onNodeWithText("All settings").performClick()
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithText("15 sp").assertIsDisplayed()
        rule.onNodeWithText("Save changes").assertIsDisplayed().performClick()
        rule.waitUntil(5000) { original.font == 15 }
    }
}
