// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AndroidShellTest {
    @Test fun systemShellHasAnExplicitCleanEnvironment() {
        val root = Files.createTempDirectory("shell-spec").toFile()
        try {
            val home = root.resolve("home").apply { mkdir() }
            val cache = root.resolve("cache").apply { mkdir() }
            val spec = AndroidShell.specification(home, cache)
            assertEquals("/system/bin/sh", spec.executable)
            assertEquals(listOf("/system/bin/sh", "-i"), spec.arguments)
            assertEquals(home.absolutePath, spec.cwd)
            assertFalse(spec.environment.containsKey("LD_PRELOAD"))
            assertFalse(spec.environment.containsKey("LD_LIBRARY_PATH"))
            assertFalse(spec.environment.values.any { it.contains("/data/data/com.termux") })
            assertEquals("xterm-256color", spec.environment["TERM"])
        } finally { root.deleteRecursively() }
    }
}
