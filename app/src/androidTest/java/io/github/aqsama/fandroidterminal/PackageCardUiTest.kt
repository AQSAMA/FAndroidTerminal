// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.linux.GuestPackage
import org.junit.Rule
import org.junit.Test

class PackageCardUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun cachedDetailsExpandEvenWhenGuestOperationsAreBlocked() {
        rule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(false) }
                PackageCard(GuestPackage("git", "1.0", "2.0", "arm64", "stable", description = "Version control"),
                    selected = false, compact = false, expanded = expanded, canLoad = false,
                    select = {}, expand = { expanded = !expanded })
            }
        }
        rule.onNodeWithText("git").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.package_versions, "1.0", "2.0")).assertIsDisplayed()
    }
}
