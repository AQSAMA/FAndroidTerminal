// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

/** Canonical, environment-bound document ancestry shared by SAF queries and grants. */
data class GuestDocumentId(val environment: String, val path: String) {
    val id get() = "$environment:$path"
    fun ancestry(parent: GuestDocumentId? = null): List<String> {
        require(parent == null || parent.environment == environment && (path == parent.path || path.startsWith(parent.path.trimEnd('/') + "/"))) { "Document is outside the selected folder" }
        val all = mutableListOf("$environment:/")
        var prefix = ""
        path.split('/').filter { it.isNotEmpty() }.forEach { prefix += "/$it"; all += "$environment:$prefix" }
        return if (parent == null) all else all.dropWhile { it != parent.id }.also { require(it.isNotEmpty()) }
    }
    companion object {
        fun parse(value: String): GuestDocumentId {
            val environment = value.substringBefore(':'); val raw = value.substringAfter(':', "")
            require(environment.matches(Regex("[a-zA-Z0-9-]+")) && raw.startsWith('/') && raw.length <= 4096 && raw.none(Char::isISOControl))
            require(raw.split('/').none { it == "." || it == ".." })
            val path = "/" + raw.split('/').filter { it.isNotEmpty() }.joinToString("/")
            return GuestDocumentId(environment, path)
        }
    }
}
