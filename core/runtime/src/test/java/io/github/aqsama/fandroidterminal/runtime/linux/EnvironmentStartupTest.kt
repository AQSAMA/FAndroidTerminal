// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnvironmentStartupTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun displayedAndLaunchedDirectoryUseTheSameValidatedFallback() {
        val root = temp.newFolder()
        File(root, "root").mkdir(); File(root, "projects").mkdir()
        val guest = GuestFiles(root)
        val env = LinuxEnvironment("test", "Test", "Ready", startDirectory = "/root", inheritDirectory = true)
        assertEquals("/projects", EnvironmentStartup.resolve(env, guest, null, "/projects").startDirectory)
        assertEquals("/root", EnvironmentStartup.resolve(env, guest, null, "/missing").startDirectory)
        assertEquals("/root", EnvironmentStartup.resolve(env, guest, null, "/projects", "/root").startDirectory)
    }
}
