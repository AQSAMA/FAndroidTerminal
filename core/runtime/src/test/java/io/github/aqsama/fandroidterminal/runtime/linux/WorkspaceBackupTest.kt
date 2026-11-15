// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CancellationException

class WorkspaceBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private fun environment(files: EnvironmentFiles): LinuxEnvironment {
        val env = LinuxEnvironment(UUID.randomUUID().toString(), "Alpine", state = "Ready", shell = "/bin/sh", distribution = "alpine", release = "3.22.8")
        files.save(env)
        files.root(env.id).mkdirs()
        File(files.root(env.id), ".fandroid-ready").writeText("ready")
        File(files.root(env.id), "root").mkdir()
        File(files.root(env.id), "root/.profile").writeText("export EDITOR=vi")
        return env
    }
    @Test fun encryptedRoundTripPreservesFullFilesAndMapsNewIdentity() {
        val files = EnvironmentFiles(temp.newFolder()); val env = environment(files)
        File(files.root(env.id), "large").writeBytes(ByteArray(150_000) { (it % 251).toByte() })
        val backup = WorkspaceBackup(files, temp.newFolder())
        val bytes = ByteArrayOutputStream()
        backup.export("settings".toByteArray(), listOf(env), bytes, "a password".toCharArray())
        assertFalse(bytes.toString("ISO-8859-1").contains("export EDITOR"))
        val result = backup.restore(bytes.toByteArray().inputStream(), "a password".toCharArray())
        assertEquals("settings", result.settings.toString(Charsets.UTF_8))
        val id = result.identities.getValue(env.id); assertNotEquals(env.id, id)
        assertEquals("export EDITOR=vi", File(files.root(id), "root/.profile").readText())
        assertEquals("alpine", files.load().first { it.id == id }.distribution)
        assertArrayEquals(File(files.root(env.id), "large").readBytes(), File(files.root(id), "large").readBytes())
    }
    @Test fun wrongPasswordAndTamperingNeverPromoteEnvironments() {
        val files = EnvironmentFiles(temp.newFolder()); val env = environment(files); val cache = temp.newFolder()
        val backup = WorkspaceBackup(files, cache)
        val out = ByteArrayOutputStream(); backup.export(byteArrayOf(), listOf(env), out, "password1".toCharArray())
        assertThrows(Exception::class.java) { backup.restore(out.toByteArray().inputStream(), "password2".toCharArray()) }
        val changed = out.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { backup.restore(changed.inputStream(), "password1".toCharArray()) }
        assertEquals(listOf(env), files.load()); assertTrue(cache.listFiles()!!.isEmpty())
    }
    @Test fun unencryptedIntegrityAndSettingsValidationPrecedePromotion() {
        val files = EnvironmentFiles(temp.newFolder()); val env = environment(files); val backup = WorkspaceBackup(files, temp.newFolder())
        val out = ByteArrayOutputStream(); backup.export("settings".toByteArray(), listOf(env), out)
        assertThrows(IllegalArgumentException::class.java) {
            backup.restore(out.toByteArray().inputStream(), validateSettings = { throw IllegalArgumentException("Invalid settings") })
        }
        val changed = out.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { backup.restore(changed.inputStream()) }
        assertEquals(listOf(env), files.load())
        val settingsOnly = backup.restore(out.toByteArray().inputStream(), restoreEnvironments = false)
        assertTrue(settingsOnly.identities.isEmpty()); assertEquals(listOf(env), files.load())
    }
    @Test fun authenticatedPreviewCanRestoreOneSelectedEnvironment() {
        val files = EnvironmentFiles(temp.newFolder()); val first = environment(files); val second = environment(files)
        val backup = WorkspaceBackup(files, temp.newFolder()); val out = ByteArrayOutputStream()
        backup.export("settings".toByteArray(), listOf(first, second), out, "password1".toCharArray())
        backup.prepare(out.toByteArray().inputStream(), "password1".toCharArray()).use { plan ->
            assertEquals(setOf(first.id, second.id), plan.environments.map { it.originalId }.toSet())
            val restored = plan.restore(setOf(second.id))
            assertEquals(setOf(second.id), restored.identities.keys)
            assertEquals(3, files.load().size)
        }
    }
    @Test fun cancellationRemovesStagingAndLeavesOriginalUntouched() {
        val files = EnvironmentFiles(temp.newFolder()); val env = environment(files); val cache = temp.newFolder()
        val out = ByteArrayOutputStream(); WorkspaceBackup(files, cache).export(byteArrayOf(), listOf(env), out)
        val backup = WorkspaceBackup(files, cache, { throw CancellationException() })
        assertThrows(CancellationException::class.java) { backup.restore(out.toByteArray().inputStream()) }
        assertTrue(cache.listFiles()!!.isEmpty()); assertEquals(listOf(env), files.load())
    }
}
