// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.nio.file.Files
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LinuxLaunchTest {
    @Test fun sharedLaunchBindsBeforeGuestCommandAndPreservesHome() {
        val dir = Files.createTempDirectory("linux-launch").toFile()
        try {
            val native = File(dir, "native").apply { mkdir() }
            listOf("libproot.so", "libproot_loader.so").forEach { File(native, it).apply { writeText(""); setExecutable(true) } }
            val root = File(dir, "rootfs").apply { mkdir() }
            val shared = File(dir, "shared").apply { mkdir() }
            val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Test", startDirectory = "/shared")
            val spec = LinuxLaunch.specification(native, root, File(dir, "tmp"), environment, shared = shared)
            val bind = spec.arguments.indexOf("${shared.path}:/shared")
            assertTrue(bind > 0 && spec.arguments[bind - 1] == "-b")
            assertTrue(bind < spec.arguments.indexOf("/usr/bin/env"))
            assertTrue(spec.arguments.contains("HOME=/root"))
            assertEquals("/shared", spec.arguments[spec.arguments.indexOf("-w") + 1])
            val disabled = LinuxLaunch.specification(native, root, File(dir, "tmp"), environment.copy(startDirectory = "/root"))
            assertFalse(disabled.arguments.any { it.endsWith(":/shared") })
            assertEquals("/root", disabled.arguments[disabled.arguments.indexOf("-w") + 1])
        } finally { dir.deleteRecursively() }
    }
}
