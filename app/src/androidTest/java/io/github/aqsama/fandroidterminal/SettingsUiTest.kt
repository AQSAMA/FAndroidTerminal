// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextContains
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

    @Test fun savedPreferencesRefreshCleanEditorsButPreserveConflictingDrafts() {
        runBlocking { original.save(14, "Midnight", "Esc Ctrl Tab", "") }
        rule.setContent { MaterialTheme { SettingsPage(original, requestedSection = "Extra keyboard keys") } }
        runBlocking { original.save(16, "Solarized", "Esc Ctrl Tab Home", "") }
        rule.waitForIdle()
        rule.onNodeWithText("Home").performClick() // Select it; removing is an explicit draft.
        rule.onNodeWithText(context.getString(R.string.remove_key)).performClick()
        runBlocking { original.save(16, "Solarized", "Esc Ctrl Tab Home End", "") }
        rule.onNodeWithText(context.getString(R.string.settings_restore_conflict)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.settings_use_current)).performClick()
        rule.onNodeWithText("End").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.save_changes)).assertDoesNotExist()
    }

    @Test fun guestDefaultDraftSurvivesSwitchingDistributionTabs() {
        rule.setContent { MaterialTheme { GuestDefaultsScreen(io.github.aqsama.fandroidterminal.sessions.GuestDefaults(context)) } }
        rule.onNodeWithText("Debian").performClick()
        rule.onNodeWithText(context.getString(R.string.default_guest_shell)).performTextInput("/bin/draft-shell")
        rule.onNodeWithText("Ubuntu").performClick()
        rule.onNodeWithText("Debian").performClick()
        rule.onNodeWithText(context.getString(R.string.default_guest_shell)).assertTextContains("draft-shell", substring = true)
    }

    @Test fun modifierCanMoveBetweenTermuxRowsAndPersist() {
        runBlocking { original.save(14, "Midnight", "Esc / - Home Up End PgUp Tab Ctrl Alt Left Down Right PgDn", "") }
        rule.setContent { MaterialTheme { SettingsPage(original, requestedSection = "Extra keyboard keys") } }
        rule.onNodeWithText("Ctrl").performScrollTo().performClick()
        rule.onNodeWithText("PgUp").performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.save_changes)).performClick()
        rule.waitUntil(5000) { original.keys == "Esc / - Home Up End Ctrl PgUp Tab Alt Left Down Right PgDn" }
        assertEquals(original.keys, Preferences(context).keys)
    }

    @Test fun tapDestinationReordersKeysAndSavePersistsTheirRuntimeOrder() {
        runBlocking { original.save(14, "Midnight", "Esc Ctrl Alt Tab Up Down", "") }
        rule.setContent { MaterialTheme { SettingsPage(original, requestedSection = "Extra keyboard keys") } }
        rule.onNodeWithText("Tab").performClick()
        rule.onNodeWithText("Down").performClick()
        rule.onNodeWithText(context.getString(R.string.save_changes)).performClick()
        rule.waitUntil(5000) { original.keys == "Esc Ctrl Alt Up Down Tab" }
        assertEquals("Esc Ctrl Alt Up Down Tab", Preferences(context).keys)
    }

    @Test fun oversizedSavedCommandsPreservePreviousDraftAndShowReason() {
        runBlocking { original.save(14, "Midnight", "Esc Ctrl Tab", "printf preserved") }
        rule.setContent { MaterialTheme { SettingsPage(original) } }
        rule.onNodeWithText(context.getString(R.string.settings_saved_commands)).performClick()
        rule.onNodeWithText(context.getString(R.string.settings_commands)).performTextInput("x".repeat(16385))
        rule.onNodeWithText(context.getString(R.string.saved_commands_size_limit)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.settings_commands)).assertTextContains("printf preserved")
        assertEquals("printf preserved", original.snippets)
    }

    @Test fun savingKeysLeavesAppearanceDraftUnsaved() {
        runBlocking { original.save(14, "Midnight", "Esc Ctrl Tab", "") }
        rule.setContent { MaterialTheme { SettingsPage(original) } }
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithContentDescription("Larger terminal text").performScrollTo().performClick()
        rule.onNodeWithText("Unsaved changes").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.back_label)).performClick()
        rule.onNodeWithText("Extra keyboard keys").performClick()
        rule.onNodeWithText("Home").performClick()
        rule.onNodeWithText("Save changes").performClick()
        rule.waitUntil(5000) { "Home" in original.keys }
        assertEquals(14, original.font)
        rule.onNodeWithContentDescription(context.getString(R.string.back_label)).performClick()
        rule.onNodeWithText("Appearance").performClick()
        rule.onNodeWithText("15 sp").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Save changes").assertIsDisplayed().performClick()
        rule.waitUntil(5000) { original.font == 15 }
    }
}
