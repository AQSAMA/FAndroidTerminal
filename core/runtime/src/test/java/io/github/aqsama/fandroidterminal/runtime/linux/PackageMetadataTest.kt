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
    @Test fun aptDescriptionsAndSizesStayWithTheirArchitecture() {
        val rows = PackageMetadata.apt("git/stable 2.0 arm64\ngit/stable 2.0 amd64\n", "git/now 1.0 arm64 [installed]\n",
            "git - distributed version control\n", "git\tarm64\t123\ngit\tamd64\t456\n")
        assertEquals("distributed version control", rows.first().description)
        assertEquals(123L * 1024, rows.first { it.architecture == "arm64" }.installedBytes)
        assertNull(rows.first { it.architecture == "amd64" }.installedBytes)
    }
    @Test fun detailsDoNotMergeAnUnrelatedVersionOrArchitecture() {
        val row = GuestPackage("git", "1.0", "2.0", "arm64", "stable")
        val control = "Package: git\nVersion: 9.0\nArchitecture: amd64\nSize: 99\n\nPackage: git\nVersion: 2.0\nArchitecture: arm64\nInstalled-Size: 42\nSize: 1024\nDescription: Version control\n More information\n .\n Another paragraph\n"
        val enriched = PackageMetadata.aptDetails(row, control)
        assertEquals(42L * 1024, enriched.candidateInstalledBytes)
        assertEquals(1024L, enriched.downloadBytes)
        assertEquals("Version control\nMore information\n\nAnother paragraph", enriched.description)
        assertEquals(row, PackageMetadata.aptDetails(row.copy(architecture = "all"), control).copy(architecture = "arm64"))
        assertEquals("headers", PackageMetadata.apk("python3-dev-3.12.9-r0 - headers\n", "", "aarch64").single().description)
    }
    @Test fun apkInstalledDatabaseProvidesBytesAndLocalDescriptions() {
        val rows = PackageMetadata.apk("git-2.0-r0 - source control\n", "git-2.0-r0\nlocal-tool-1.0-r0\n", "aarch64", metadata = "P:git\nV:2.0-r0\nI:2048\nT:source control\n\nP:local-tool\nV:1.0-r0\nI:512\nT:Private tool\n")
        assertEquals(2048L, rows.first { it.name == "git" }.installedBytes)
        assertEquals("Private tool", rows.first { it.name == "local-tool" }.description)
    }
    @Test fun descriptionsDoNotReplaceVersionsAndSurvivePunctuation() {
        val rows = PackageMetadata.apt("git/stable 2.0 arm64\n", "local/now 1.0 arm64 [installed]\n", "git - fast - distributed version control\nlocal:arm64 - Private package\nmalformed\n")
        assertEquals("fast - distributed version control", rows.first { it.name == "git" }.description)
        assertEquals("Private package", rows.first { it.name == "local" }.description)
        assertNull(rows.first { it.name == "git" }.installed)
        val apk = PackageMetadata.apk("libfoo-2-1.0-r2 - Development headers - extra\n", "", "aarch64")
        assertEquals("Development headers - extra", apk.single().description)
        assertEquals("libfoo-2", apk.single().name)
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
        assertEquals("eof", GuestCommand.run(spec("if read value; then exit 1; else printf eof; fi"), {}).output)
        assertEquals(17, result.exitCode); assertEquals("structured", result.output)
        assertThrows(CancellationException::class.java) {
            GuestCommand.run(spec("exec sleep 30"), { throw CancellationException("cancel") })
        }
    }
}
