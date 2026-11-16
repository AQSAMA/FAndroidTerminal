// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import org.junit.Assert.*
import org.junit.Test

class PackageSearchTest {
    private fun row(name: String, description: String = "", installed: String? = null, update: Boolean = false, size: Long? = null, architecture: String = "arm64") =
        GuestPackage(name, installed, "1", architecture, "stable", update, description, installedBytes = size)

    @Test fun fishShellPrecedesHundredsOfIncidentalMatchesBeforePagination() {
        val incidental = (1..250).map { row("aaa-$it", "contains fish integration") }
        val packages = incidental + listOf(row("catfish"), row("fish-doc"), row("fish", "friendly interactive shell"), row("bash"))
        val results = PackageSearch.results(packages, " FISH ")
        assertEquals(listOf("fish", "fish-doc", "catfish"), results.take(3).map { it.name })
        assertEquals(253, results.size)
        assertEquals("fish", results.take(100).first().name)
    }

    @Test fun multipleTermsCanMatchNameAndDescriptionWithoutRequiringAPhrase() {
        assertEquals(listOf("fish"), PackageSearch.results(listOf(row("fish", "friendly interactive shell"), row("fish-doc", "documentation")), "fish shell").map { it.name })
        assertEquals(listOf("fish"), PackageSearch.results(listOf(row("fish", "friendly interactive shell")), "  shell   fish ").map { it.name })
    }

    @Test fun statusFiltersAndExplicitSortsRemainAvailable() {
        val packages = listOf(row("fish", installed = "1", update = true, size = 20), row("fish-doc", installed = "1", size = 80), row("catfish"))
        assertEquals(listOf("fish", "fish-doc"), PackageSearch.results(packages, "fish", "Installed").map { it.name })
        assertEquals(listOf("fish"), PackageSearch.results(packages, "fish", "Updates").map { it.name })
        assertEquals(listOf("fish-doc", "fish", "catfish"), PackageSearch.results(packages, "fish", sort = "ZA").map { it.name })
        assertEquals(listOf("fish-doc", "fish", "catfish"), PackageSearch.results(packages, "", sort = "Size").map { it.name })
    }

    @Test fun emptyQueryAndArchitectureTiesAreDeterministic() {
        val packages = listOf(row("zsh"), row("fish", architecture = "arm64"), row("fish", architecture = "all"))
        assertEquals(listOf("fish", "fish", "zsh"), PackageSearch.results(packages, "  ").map { it.name })
        assertEquals(listOf("all", "arm64"), PackageSearch.results(packages, "fish").map { it.architecture })
        assertTrue(PackageSearch.results(packages, "not-a-package").isEmpty())
    }
}
