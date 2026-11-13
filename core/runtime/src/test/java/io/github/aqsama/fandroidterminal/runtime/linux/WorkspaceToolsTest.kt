// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CancellationException

class WorkspaceToolsTest {
    private fun fixture(block: (EnvironmentFiles, LinuxEnvironment) -> Unit) {
        val temp = Files.createTempDirectory("workspace-test").toFile()
        try {
            val files = EnvironmentFiles(temp)
            val env = LinuxEnvironment(UUID.randomUUID().toString(), "Work", "Ready", startDirectory = "/root", variables = "EDITOR=vi")
            files.root(env.id).mkdirs(); File(files.root(env.id), "root").mkdir(); File(files.root(env.id), ".fandroid-ready").writeText("ready")
            files.save(env); block(files, env)
        } finally { EnvironmentFiles.deleteTree(temp) }
    }
    @Test fun guestAbsoluteAndRelativeLinksNeverUseHostRoot() = fixture { files, env ->
        val root = files.root(env.id); File(root, "root/project").mkdir(); File(root, "root/project/note").writeText("guest")
        Files.createSymbolicLink(File(root, "absolute").toPath(), java.nio.file.Paths.get("/root/project"))
        Files.createSymbolicLink(File(root, "root/relative").toPath(), java.nio.file.Paths.get("project"))
        val guest = GuestFiles(root)
        assertEquals("guest", guest.read("/absolute/note")); assertEquals("guest", guest.read("/root/relative/note"))
        assertTrue(runCatching { guest.resolve("/../../data") }.isFailure)
        Files.createSymbolicLink(File(root, "loop").toPath(), java.nio.file.Paths.get("/loop"))
        assertTrue(runCatching { guest.resolve("/loop") }.isFailure)
        guest.delete("/absolute"); assertTrue(File(root, "root/project/note").exists())
    }
    @Test fun copyPreservesLinksAndCancelledWritePreservesOriginal() = fixture { files, env ->
        val root = files.root(env.id); File(root, "root/source").writeText("old")
        Files.createSymbolicLink(File(root, "root/link").toPath(), java.nio.file.Paths.get("/root/source"))
        GuestFiles(root).transfer("/root/link", "/root/copy", false)
        assertEquals("/root/source", Files.readSymbolicLink(File(root, "root/copy").toPath()).toString())
        var checks = 0
        val guest = GuestFiles(root) { if (++checks > 3) throw CancellationException() }
        assertTrue(runCatching { guest.write("/root/source", ByteArray(200000).inputStream(), true) }.isFailure)
        assertEquals("old", File(root, "root/source").readText())
        assertFalse(File(root, "root").list()!!.any { it.endsWith(".partial") })
    }
    @Test fun independentRestorePreservesSettingsModesAndLinks() = fixture { files, env ->
        val root = files.root(env.id); val script = File(root, "root/run").apply { writeText("#!/bin/sh\n"); setExecutable(true, false) }
        Files.createSymbolicLink(File(root, "root/link").toPath(), java.nio.file.Paths.get("/root/run"))
        val bytes = ByteArrayOutputStream(); EnvironmentBackup(files).export(env, bytes)
        val restored = EnvironmentBackup(files).restore(bytes.toByteArray().inputStream())
        assertNotEquals(env.id, restored.id); assertEquals(env.variables, files.load().first { it.id == restored.id }.variables)
        assertEquals(Files.getPosixFilePermissions(script.toPath()), Files.getPosixFilePermissions(File(files.root(restored.id), "root/run").toPath()))
        assertEquals("/root/run", Files.readSymbolicLink(File(files.root(restored.id), "root/link").toPath()).toString())
        File(files.root(restored.id), "root/run").writeText("changed"); assertEquals("#!/bin/sh\n", script.readText())
    }
    @Test fun failedAndCancelledRestoresLeaveExistingEnvironmentUntouched() = fixture { files, env ->
        val bytes = ByteArrayOutputStream(); EnvironmentBackup(files).export(env, bytes)
        assertTrue(runCatching { EnvironmentBackup(files).restore(bytes.toByteArray().dropLast(10).toByteArray().inputStream()) }.isFailure)
        assertTrue(runCatching { EnvironmentBackup(files, { throw CancellationException() }).restore(bytes.toByteArray().inputStream()) }.isFailure)
        assertEquals(listOf(env), files.load()); assertEquals(1, files.directory.listFiles()!!.size)
        assertTrue(File(files.root(env.id), ".fandroid-ready").exists())
    }
    @Test fun malformedPathsAndOversizedArchiveEntriesAreRejected() = fixture { files, env ->
        fun archive(path: String, size: Long): ByteArray {
            val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
            out.writeUTF("FANDROID-BACKUP-1"); out.writeUTF("Bad"); out.writeUTF("1.1.1.1"); out.writeBoolean(true); out.writeUTF("/root"); out.writeUTF("/bin/bash"); out.writeUTF("")
            out.writeByte(1); out.writeUTF(path); out.writeInt(0); out.writeLong(size); out.flush(); return bytes.toByteArray()
        }
        for (path in listOf("../escape", "/absolute", "root/../../escape")) assertTrue(runCatching { EnvironmentBackup(files).restore(archive(path, 0).inputStream()) }.isFailure)
        assertTrue(runCatching { EnvironmentBackup(files).restore(archive("huge", Long.MAX_VALUE).inputStream()) }.isFailure)
        assertEquals(listOf(env), files.load())
    }
    @Test fun guestSettingsPersistWithoutHostLoaderVariables() = fixture { files, env ->
        val updated = env.copy(dns = "9.9.9.9", compatibility = false, shell = "/bin/sh").validated()
        files.save(updated); assertEquals(updated, files.load().single())
        assertTrue(runCatching { env.copy(variables = "LD_PRELOAD=bad").validated() }.isFailure)
        assertTrue(runCatching { env.copy(startDirectory = "relative").validated() }.isFailure)
    }
    @Test fun prootHardlinkChainsAreRelocatedAndSurviveSourceDeletion() = fixture { files, env ->
        val root = files.root(env.id).toPath()
        val backing = root.resolve("root/.l2s.program0001.0002")
        Files.write(backing, "independent".toByteArray())
        val intermediate = root.resolve("root/.l2s.program0001")
        Files.createSymbolicLink(intermediate, backing)
        Files.createSymbolicLink(root.resolve("root/first"), intermediate)
        Files.createSymbolicLink(root.resolve("root/second"), intermediate)
        assertEquals("independent", GuestFiles(root.toFile()).read("/root/first"))
        val bytes = ByteArrayOutputStream(); EnvironmentBackup(files).export(env, bytes)
        val restored = EnvironmentBackup(files).restore(bytes.toByteArray().inputStream())
        EnvironmentFiles.deleteTree(root.toFile())
        val guest = GuestFiles(files.root(restored.id))
        assertEquals("independent", guest.read("/root/first"))
        guest.write("/root/first", "updated".byteInputStream(), true)
        assertEquals("updated", guest.read("/root/second"))
        assertTrue(Files.readSymbolicLink(File(files.root(restored.id), "root/first").toPath()).startsWith(files.root(restored.id).toPath()))
    }
    @Test fun restartRecoversOnlyMarkedIncompleteRestores() = fixture { files, env ->
        val abandoned = files.instance(UUID.randomUUID().toString()).apply { mkdir() }
        File(abandoned, "restore.pending").writeText("pending")
        File(abandoned, "restore-staging").mkdir()
        File(abandoned, "restore-staging/partial").writeText("partial")
        val unrelated = files.instance(UUID.randomUUID().toString()).apply { mkdir() }
        File(unrelated, "user-data").writeText("preserve")
        File(files.instance(env.id), "restore.pending").writeText("pending")
        files.recoverRestores()
        assertFalse(abandoned.exists()); assertTrue(File(unrelated, "user-data").exists())
        assertEquals(listOf(env), files.load()); assertTrue(files.root(env.id).isDirectory)
    }
    @Test fun sixtyCharacterEnvironmentNameRestores() = fixture { files, env ->
        val named = env.copy(name = "n".repeat(60)); files.save(named)
        val bytes = ByteArrayOutputStream(); EnvironmentBackup(files).export(named, bytes)
        val restored = EnvironmentBackup(files).restore(bytes.toByteArray().inputStream())
        assertEquals(60, restored.name.length)
    }

}
