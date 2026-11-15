// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.nio.file.Files

/** One resolution rule for launch and displayed startup paths. Run with guest-file admission. */
object EnvironmentStartup {
    fun resolve(environment: LinuxEnvironment, guest: GuestFiles, inheritedShell: String?, inheritedDirectory: String?, directory: String? = null): LinuxEnvironment = environment.copy(
        shell = inheritedShell?.takeIf { runCatching { Files.isExecutable(guest.resolve(it)) }.getOrDefault(false) } ?: environment.shell,
        startDirectory = directory ?: inheritedDirectory?.takeIf { runCatching { Files.isDirectory(guest.resolve(it)) }.getOrDefault(false) } ?: environment.startDirectory,
    )
}
