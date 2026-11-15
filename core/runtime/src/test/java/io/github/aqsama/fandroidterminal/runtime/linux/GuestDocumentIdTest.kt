// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import org.junit.Assert.*
import org.junit.Test

class GuestDocumentIdTest {
    @Test fun browsableDotPathsBecomeCanonicalConfinedDocumentIds() {
        val root = java.nio.file.Files.createTempDirectory("guest-documents").toFile()
        try {
            java.io.File(root, "root").mkdir(); java.io.File(root, "tmp").mkdir()
            val guest = GuestFiles(root)
            assertEquals("/root", guest.canonicalPath("/root/./"))
            assertEquals("/tmp", guest.canonicalPath("/root/../tmp//"))
            assertEquals(listOf("one:/", "one:/tmp"), GuestDocumentId.parse("one:" + guest.canonicalPath("/root/../tmp")).ancestry())
            assertThrows(IllegalArgumentException::class.java) { guest.canonicalPath("/root/../../outside") }
        } finally { root.deleteRecursively() }
    }
    @Test fun documentBrowserCanResolveRootAndNestedAncestry() {
        assertEquals(listOf("one:/"), GuestDocumentId.parse("one:/").ancestry())
        val id = GuestDocumentId.parse("one:/root/project/read me.txt")
        assertEquals(listOf("one:/", "one:/root", "one:/root/project", id.id), id.ancestry())
        assertEquals(listOf("one:/root", "one:/root/project", id.id), id.ancestry(GuestDocumentId.parse("one:/root")))
        assertEquals(listOf(id.id), id.ancestry(id))
        listOf("two:/root", "one:/etc", "one:/root/project-other").forEach { parent ->
            assertThrows(IllegalArgumentException::class.java) { id.ancestry(GuestDocumentId.parse(parent)) }
        }
        assertThrows(IllegalArgumentException::class.java) { GuestDocumentId.parse("one:/root/../etc") }
    }
}
