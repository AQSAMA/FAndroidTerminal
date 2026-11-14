// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

/** Arguments are individual values, not shell syntax. No tokenization or interpolation occurs. */
data class CliLauncher(val id: String, val name: String, val environmentId: String, val directory: String, val arguments: List<String>) {
    fun validated(): CliLauncher {
        require(java.util.UUID.fromString(id).toString() == id)
        require(java.util.UUID.fromString(environmentId).toString() == environmentId)
        EnvironmentFiles.validateName(name)
        require(directory.startsWith('/') && directory.length <= 4096 && directory.none(Char::isISOControl))
        require(arguments.isNotEmpty() && arguments.size <= 64 && arguments.sumOf { it.length } <= 16384)
        require(arguments.first().startsWith('/') && arguments.all { '\u0000' !in it && '\n' !in it && '\r' !in it })
        return this
    }
}
