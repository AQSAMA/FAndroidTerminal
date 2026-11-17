// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux
import java.nio.file.Files
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RootAccessTest {
    @Test fun pendingRootCleanupBlocksOnlyTargetedEnvironmentsAndWholeWorkspaceOperations() {
        val temp = Files.createTempDirectory("root-admission").toFile()
        try {
            val files = EnvironmentFiles(temp)
            val busy = UUID.randomUUID().toString(); val idle = UUID.randomUUID().toString()
            val marker = File(files.instance(busy).apply { mkdir() }, "root-access.pending").apply { writeText("") }
            files.requireAppAccess(emptySet()) // settings-only restore or new-instance import
            files.requireAppAccess(listOf(idle))
            assertThrows(IllegalStateException::class.java) { files.requireAppAccess(listOf(busy)) }
            assertThrows(IllegalStateException::class.java) { files.requireAppAccess(listOf(busy, idle)) }
            marker.delete()
            files.requireAppAccess(listOf(busy, idle))
        } finally { temp.deleteRecursively() }
    }
}
