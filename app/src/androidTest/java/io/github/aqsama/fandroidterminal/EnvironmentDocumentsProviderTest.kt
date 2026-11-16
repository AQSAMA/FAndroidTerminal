// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import org.junit.Assert.*
import org.junit.Test

/** SAF tree grants must never include sibling paths, other environments or traversal. */
class EnvironmentDocumentsProviderTest {
    private val provider = EnvironmentDocumentsProvider()
    @Test fun grantsStayWithinTheChosenDirectoryAndEnvironment() {
        assertTrue(provider.isChildDocument("one:/root", "one:/root/project/file.txt"))
        assertFalse(provider.isChildDocument("one:/root", "one:/root-other/file.txt"))
        assertFalse(provider.isChildDocument("one:/root", "two:/root/file.txt"))
        assertFalse(provider.isChildDocument("one:/root", "one:/etc/passwd"))
        assertFalse(provider.isChildDocument("one:/root", "one:/root"))
        assertThrows(IllegalArgumentException::class.java) { provider.isChildDocument("one:/root", "one:/root/../etc/passwd") }
    }
    @Test fun externalWritesAreRejectedBeforeAccessingStorage() {
        listOf("w", "rw", "rwt", "wa").forEach { mode ->
            assertThrows(java.io.FileNotFoundException::class.java) { provider.openDocument("one:/root/file", mode, null) }
        }
    }
}
