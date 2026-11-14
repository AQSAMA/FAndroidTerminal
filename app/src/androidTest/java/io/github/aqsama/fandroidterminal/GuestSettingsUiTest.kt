// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuestSettingsUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val initial = LinuxEnvironment("preview", "Projects", "Ready")

    @Test fun saveAcknowledgementWaitsForCommittedEnvironment() {
        var current by mutableStateOf(initial)
        var requested: LinuxEnvironment? = null
        rule.setContent { MaterialTheme { GuestSettingsContent(current, 0, emptyMap(), {}) { requested = it } } }
        rule.onNodeWithText(context.getString(R.string.guest_dns)).performTextReplacement("8.8.8.8")
        rule.onNodeWithText(context.getString(R.string.guest_save)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.changes_saved)).assertDoesNotExist()
        rule.runOnIdle { current = requireNotNull(requested) }
        rule.onNodeWithText(context.getString(R.string.changes_saved)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.guest_save)).assertDoesNotExist()
    }

    @Test fun blockedDraftOffersRecoveryWithoutSavingOrLosingValues() {
        var saved = false
        var reviewed = false
        rule.setContent { MaterialTheme { GuestSettingsContent(initial, 2, emptyMap(), { reviewed = true }) { saved = true } } }
        rule.onNodeWithText(context.getString(R.string.guest_dns)).performTextReplacement("8.8.8.8")
        rule.onNodeWithText(context.getString(R.string.guest_save)).assertIsNotEnabled()
        rule.onAllNodesWithText(context.getString(R.string.review_sessions)).onLast().performClick()
        rule.onNodeWithText("8.8.8.8").assertExists()
        rule.runOnIdle { assertTrue(reviewed); assertFalse(saved) }
    }
}
