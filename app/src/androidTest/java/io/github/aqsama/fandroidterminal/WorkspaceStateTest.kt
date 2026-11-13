// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import org.junit.Assert.*
import org.junit.Test

/** Navigation state must stay isolated without creating a terminal or touching a rootfs. */
class WorkspaceStateTest {
    @Test fun commandDraftsRemainSeparateAndClosedSessionsReleaseThem() {
        val state = WorkspaceState()
        state.command("one").text = "printf first"
        state.command("two").text = "printf second"
        assertEquals("printf first", state.command("one").text)
        state.retainSessions(setOf("two"))
        assertEquals("", state.command("one").text)
        assertEquals("printf second", state.command("two").text)
    }

    @Test fun returningToAnEnvironmentRetainsItsDirectoryAndUnsavedFile() {
        val state = WorkspaceState()
        state.draft("one").apply { directory = "/root/project"; path = "/root/project/note"; baseline = "old"; text = "unsaved" }
        state.draft("two").directory = "/tmp"
        assertEquals("/root/project", state.draft("one").directory)
        assertEquals("unsaved", state.draft("one").text)
        assertTrue(state.draft("one").dirty)
        assertNull(state.draft("two").path)
    }
}
