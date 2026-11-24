// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import org.junit.Assert.*
import org.junit.Test

class TerminalModifiersTest {
    @Test fun singleTapArmsOneKeyAndDoubleTapLocksUntilTappedAgain() {
        val modifiers = TerminalModifiers()
        modifiers.tap("Ctrl"); assertEquals(4, modifiers.mask())
        modifiers.clearTransients(); assertEquals(0, modifiers.mask())
        modifiers.tap("Alt"); modifiers.tap("Alt")
        repeat(3) { modifiers.clearTransients(); assertEquals(2, modifiers.mask()) }
        modifiers.tap("Alt"); assertEquals(0, modifiers.mask())
    }
}
