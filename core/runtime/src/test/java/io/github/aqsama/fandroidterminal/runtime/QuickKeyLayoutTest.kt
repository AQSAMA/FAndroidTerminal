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
    @Test fun movesReorderAndCrossRowsWithoutLosingOrDuplicatingKeys() {
        val original = QuickKeyLayout(listOf("Esc", "Ctrl", "Alt"), listOf("Up", "Tab"))
        val reordered = original.move("Alt", 0, "Ctrl")
        assertEquals(listOf("Esc", "Alt", "Ctrl"), reordered.always)
        val moved = reordered.move("Ctrl", 1, "Tab")
        assertEquals(listOf("Up", "Ctrl", "Tab"), moved.expanded)
        assertEquals(original.tokens.filter { it != "::" }.toSet(), moved.tokens.filter { it != "::" }.toSet())
        assertEquals(moved, QuickKeyLayout.parse(moved.encode()))
        assertEquals(moved, moved.move("Ctrl", 1, "Ctrl"))
        assertEquals(listOf("Esc", "Alt", "Ctrl"), moved.move("Ctrl", 0).always)
    }
    @Test fun movesSupportEmptyRowsAndRejectOverflowForNewKeys() {
        val layout = QuickKeyLayout(listOf("Esc"), emptyList()).move("Esc", 1)
        assertTrue(layout.always.isEmpty())
        assertEquals(listOf("Esc"), layout.expanded)
        val full = QuickKeyLayout(QuickKeyLayout.supported.take(16), emptyList())
        assertEquals(16, full.move(full.always.first(), 1).always.size + 1)
        assertThrows(IllegalArgumentException::class.java) { full.move(QuickKeyLayout.supported.last(), 1) }
    }
}
