// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LinuxImagesTest {
    @Test fun legacyDebianAndDistributionSettingsRoundTrip() {
        val directory = Files.createTempDirectory("linux-records").toFile()
        try {
            val files = EnvironmentFiles(directory)
            LinuxImages.all.forEach { image ->
                val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Projects", "Ready", shell = image.shell,
                    distribution = image.distribution, release = image.version)
                files.save(environment)
                assertEquals(environment, files.load().first { it.id == environment.id })
            }
            val legacy = files.load().first { it.distribution == "debian" }
            val record = File(files.instance(legacy.id), "record.properties")
            record.writeText(record.readLines().filterNot { it.startsWith("distribution=") || it.startsWith("release=") }.joinToString("\n"))
            assertEquals(legacy, files.load().first { it.id == legacy.id })
        } finally { EnvironmentFiles.deleteTree(directory) }
    }

    @Test fun alpineBackupRestoresIdentityShellAndFiles() {
        val directory = Files.createTempDirectory("linux-backup").toFile()
        try {
            val files = EnvironmentFiles(directory)
            val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Small tools", "Ready", shell = "/bin/sh", distribution = "alpine", release = "3.22.8")
            files.save(environment)
            val root = files.root(environment.id).apply { mkdirs() }
            File(root, ".fandroid-ready").writeText("alpine-minirootfs-3.22.8-aarch64\n")
            File(root, "project.txt").writeText("preserve me")
            val bytes = ByteArrayOutputStream()
            EnvironmentBackup(files).export(environment, bytes)
            val restored = EnvironmentBackup(files).restore(bytes.toByteArray().inputStream())
            assertNotEquals(environment.id, restored.id)
            assertEquals(environment.distribution, restored.distribution)
            assertEquals(environment.release, restored.release)
            assertEquals(environment.shell, restored.shell)
            assertEquals("preserve me", File(files.root(restored.id), "project.txt").readText())
        } finally { EnvironmentFiles.deleteTree(directory) }
    }

    /** Optional real upstream archives; the ordinary CI fixtures never require network. */
    @Test fun verifiedOfficialUbuntuAndAlpineArchivesExtractWithExecutableArm64Shells() {
        val fixtures = System.getenv("FANDROID_ROOTFS_FIXTURES")
        assumeTrue("Official archive fixtures not supplied", fixtures != null)
        LinuxImages.all.filter { it.distribution != "debian" }.forEach { image ->
            val archive = File(fixtures!!, "${image.distribution}-base.tar.gz")
            assertEquals(image.bytes, archive.length())
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            assertEquals(image.sha256, digest.digest().joinToString("") { "%02x".format(it) })
            val root = Files.createTempDirectory("real-${image.distribution}").toFile()
            try {
                SafeTarExtractor().extract(archive, root, image.format)
                val shell = GuestFiles(root).resolve(image.shell)
                assertTrue(Files.isExecutable(shell))
                val header = Files.newInputStream(shell).use { it.readNBytes(20) }
                assertArrayEquals(byteArrayOf(127, 69, 76, 70), header.take(4).toByteArray())
                assertEquals(183, header[18].toInt() and 255) // ELF EM_AARCH64
                val release = Files.readString(GuestFiles(root).resolve("/etc/os-release"))
                assertTrue(release.contains(image.distribution, ignoreCase = true))
            } finally { EnvironmentFiles.deleteTree(root) }
        }
    }
}
