// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import org.junit.Assert.*
import org.junit.Test

class QuickKeyLayoutTest {
    @Test fun legacyLayoutsPreserveEveryKeyAndMigrateWithoutConfusingPipeKey() {
        val old = "Esc Ctrl Alt Tab Up Down Left Right Home End PgUp PgDn Insert Delete / |"
        val layout = QuickKeyLayout.parse(old)
        assertEquals(listOf("Esc", "Ctrl", "Alt"), layout.always)
        assertEquals(old.split(' '), layout.always + layout.expanded)
        assertEquals(layout, QuickKeyLayout.parse(layout.encode()))
    }
    @Test fun deletingAllAlwaysKeysKeepsExpandedMembershipAndRoundTrips() {
        val layout = QuickKeyLayout(emptyList(), listOf("Tab", "Up", "|"))
        assertEquals(layout, QuickKeyLayout.parse(layout.encode()))
        assertEquals(QuickKeyLayout(emptyList(), emptyList()), QuickKeyLayout.parse("::"))
        assertThrows(IllegalArgumentException::class.java) { QuickKeyLayout.parse("Esc :: :: Tab") }
    }
}
