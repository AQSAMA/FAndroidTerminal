// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnvironmentFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun environment() = LinuxEnvironment(UUID.randomUUID().toString(), "Debian")
    @Test fun instancesAndSettingsSurviveReopening() {
        val dir = temp.newFolder()
        val store = EnvironmentFiles(dir)
        val one = environment().copy(dns = "9.9.9.9", compatibility = false)
        val two = environment()
        store.save(one); store.save(two)
        store.root(one.id).mkdirs(); store.root(two.id).mkdirs()
        File(store.root(one.id), "project").writeText("one")
        assertFalse(File(store.root(two.id), "project").exists())
        assertEquals(setOf(one, two), EnvironmentFiles(dir).load().toSet())
        assertThrows(IllegalArgumentException::class.java) { store.instance("../escape") }
    }
    @Test fun interruptedSetupIsRecoverableAndDeletionDoesNotFollowGuestSymlinks() {
        val store = EnvironmentFiles(temp.newFolder())
        val env = environment().copy(state = "Installing")
        store.save(env)
        assertTrue(store.load().single().state.startsWith("Interrupted"))
        val outside = temp.newFolder()
        val project = File(outside, "keep").apply { writeText("user data") }
        Files.createSymbolicLink(File(store.instance(env.id), "link").toPath(), outside.toPath())
        EnvironmentFiles.deleteTree(store.instance(env.id))
        assertEquals("user data", project.readText())
    }
    @Test fun deletionInterruptionKeepsRecordVisibleUntilPayloadIsGone() {
        val store = EnvironmentFiles(temp.newFolder())
        val env = environment().copy(state = "Deleting")
        store.save(env); store.root(env.id).mkdirs()
        File(store.root(env.id), "project").writeText("payload")
        assertThrows(IllegalStateException::class.java) {
            store.deleteInstance(env.id) { error("interrupted") }
        }
        assertEquals(env, EnvironmentFiles(store.directory).load().single())
        assertEquals("payload", File(store.root(env.id), "project").readText())
        store.deleteInstance(env.id)
        assertTrue(store.load().isEmpty())
        assertFalse(store.instance(env.id).exists())
    }
    @Test fun failedProbeNeverPromotesAndRetryCanComplete() {
        val store = EnvironmentFiles(temp.newFolder())
        val env = environment(); store.save(env)
        val fixture = TarFixture.build(listOf(TarFixture.Entry.File("hello", "linux".toByteArray())), ArchiveFormat.TAR_XZ)
        val installer = DebianInstaller(store, {}, {}, { it.writeBytes(fixture) })
        assertThrows(IllegalStateException::class.java) { installer.install(env, {}, { error("broken loader") }) }
        assertFalse(store.root(env.id).exists())
        assertFalse(File(store.instance(env.id), "staging").exists())
        installer.install(env, {}, { assertEquals("linux", File(it, "hello").readText()) })
        assertEquals("linux", File(store.root(env.id), "hello").readText())
        assertThrows(IllegalStateException::class.java) { installer.install(env, {}, {}) }
        assertEquals("linux", File(store.root(env.id), "hello").readText())
    }
    @Test fun cancellationDuringExtractionCleansStagingAndPreservesOtherInstance() {
        val store = EnvironmentFiles(temp.newFolder())
        val env = environment(); store.save(env)
        val other = environment(); store.save(other); store.root(other.id).mkdirs()
        File(store.root(other.id), "keep").writeText("other")
        var cancelled = false
        val installer = DebianInstaller(store, { if (cancelled) throw CancellationException() }, {}, {
            it.writeBytes(TarFixture.build(listOf(TarFixture.Entry.File("large", ByteArray(200000))), ArchiveFormat.TAR_XZ))
            cancelled = true
        })
        assertThrows(CancellationException::class.java) { installer.install(env, {}, {}) }
        assertFalse(store.root(env.id).exists())
        assertFalse(File(store.instance(env.id), "download.part").exists())
        assertFalse(File(store.instance(env.id), "staging").exists())
        assertEquals("other", File(store.root(other.id), "keep").readText())
    }
    @Test fun launchUsesPackagedLoaderAndCleansGuestEnvironment() {
        val native = temp.newFolder()
        listOf("libproot.so", "libproot_loader.so").forEach { File(native, it).apply { writeText("elf"); setExecutable(true) } }
        val spec = LinuxLaunch.specification(native, temp.newFolder(), temp.newFolder(), environment())
        assertEquals(File(native, "libproot_loader.so").path, spec.environment["PROOT_LOADER"])
        assertFalse(spec.environment.containsKey("LD_PRELOAD"))
        assertFalse(spec.environment.containsKey("LD_LIBRARY_PATH"))
        assertTrue(spec.arguments.containsAll(listOf("--kill-on-exit", "/usr/bin/env", "-i", "HOME=/root", "TMPDIR=/tmp")))
    }
}
