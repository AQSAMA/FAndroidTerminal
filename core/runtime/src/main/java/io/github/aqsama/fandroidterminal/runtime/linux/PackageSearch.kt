// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

/** Relevance is applied before the UI's page limit, so exact names cannot be buried. */
object PackageSearch {
    fun results(packages: List<GuestPackage>, query: String, filter: String = "All", sort: String = "Relevance"): List<GuestPackage> {
        val text = query.trim()
        val terms = text.split(Regex("\\s+")).filter(String::isNotEmpty)
        val matching = packages.filter { row ->
            terms.all { row.name.contains(it, ignoreCase = true) || row.description.contains(it, ignoreCase = true) } &&
                when (filter) { "Installed" -> row.installed != null; "Updates" -> row.updateAvailable; else -> true }
        }
        val alphabetical = compareBy<GuestPackage> { it.name }.thenBy { it.architecture }
        val order = when (sort) {
            "ZA" -> compareByDescending<GuestPackage> { it.name }.thenBy { it.architecture }
            "Size" -> compareByDescending<GuestPackage> { it.installedBytes ?: -1L }.then(alphabetical)
            "AZ" -> alphabetical
            else -> compareBy<GuestPackage> { row ->
                when {
                    text.isEmpty() -> 0
                    row.name.equals(text, ignoreCase = true) -> 0
                    row.name.startsWith(text, ignoreCase = true) -> 1
                    terms.all { row.name.contains(it, ignoreCase = true) } -> 2
                    terms.any { row.name.contains(it, ignoreCase = true) } -> 3
                    else -> 4
                }
            }.then(alphabetical)
        }
        return matching.sortedWith(order)
    }
}
