// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import org.junit.Assert.*
import org.junit.Test
import io.github.aqsama.fandroidterminal.runtime.ShellSpec
import java.util.concurrent.CancellationException

class PackageMetadataTest {
    @Test fun aptKeepsInstalledCandidateArchitectureAndLocalOnlyPackages() {
        val rows = PackageMetadata.apt("Listing...\nbash/stable 5.3 arm64 [upgradable from: 5.2]\nlibfoo/stable 2.0 all\n", "Listing...\nbash/now 5.2 arm64 [installed]\nprivate/now 1.0 arm64 [installed,local]\n")
        val bash = rows.first { it.name == "bash" }
        assertEquals("5.2", bash.installed); assertEquals("5.3", bash.candidate)
        assertEquals("arm64", bash.architecture); assertTrue(bash.updateAvailable)
        assertNull(rows.first { it.name == "private" }.candidate)
        assertNull(rows.first { it.name == "libfoo" }.installed)
    }
    @Test fun apkSeparatesHyphenatedNamesAndVersions() {
        val rows = PackageMetadata.apk("python3-dev-3.12.9-r0 - headers\nlibfoo-2-1.0-r2 - package\n", "python3-dev-3.12.8-r0\nlocal-tool-1.0-r0\n", "aarch64\n")
        assertEquals("3.12.9-r0", rows.first { it.name == "python3-dev" }.candidate)
        assertEquals("3.12.8-r0", rows.first { it.name == "python3-dev" }.installed)
        assertEquals("1.0-r2", rows.first { it.name == "libfoo-2" }.candidate)
        assertNull(rows.first { it.name == "local-tool" }.candidate)
    }
    @Test fun packageInputRejectsOptionsShellCodeAndOverlargeSelections() {
        assertEquals(listOf("git", "curl", "zsh"), PackageMetadata.names("git, curl\nzsh git"))
        listOf("--allow-untrusted", "git;id", "$(id)", "../git", "git=99", (0..100).joinToString(" ") { "pkg$it" }).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PackageMetadata.names(value) }
        }
    }
    @Test fun copiedSelectionsCanBeReusedWithoutImportingVersionsOrContext() {
        assertEquals(listOf("git:arm64", "curl"), PackageMetadata.importNames("git:arm64\t1.0 → 2.0\tarm64\tDebian\ncurl\t— → 3.0\tAlpine"))
    }
    @Test fun commandCapturesExitAndCancelsProcess() {
        fun spec(command: String) = ShellSpec("/bin/sh", listOf("/bin/sh", "-c", command), "/tmp", mapOf("PATH" to "/usr/bin:/bin"))
        val result = GuestCommand.run(spec("printf structured; exit 17"), {})
        assertEquals(17, result.exitCode); assertEquals("structured", result.output)
        assertThrows(CancellationException::class.java) {
            GuestCommand.run(spec("exec sleep 30"), { throw CancellationException("cancel") })
        }
    }
}
