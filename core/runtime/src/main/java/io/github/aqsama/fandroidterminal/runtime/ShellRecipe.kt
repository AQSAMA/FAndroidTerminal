// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

/** Script text is authored code. Inputs travel as positional arguments, never as shell source. */
data class ShellRecipe(val id: String, val name: String, val script: String, val inputs: List<String> = emptyList()) {
    fun validated(): ShellRecipe {
        require(id.isNotBlank() && id.length <= 80)
        require(name.isNotBlank() && name.length <= 60 && name.none(Char::isISOControl))
        require(script.isNotBlank() && script.toByteArray(Charsets.UTF_8).size <= 4096)
        require(script.none { it.isISOControl() && it != '\n' && it != '\t' })
        require(inputs.size <= 3 && inputs.all { it.isNotBlank() && it.length <= 32 && it.none(Char::isISOControl) })
        return this
    }

    fun command(values: List<String>): String {
        validated()
        require(values.size == inputs.size && values.all { it.length <= 512 && it.none(Char::isISOControl) })
        return buildString {
            append("sh -c ")
            append(shellQuote(script))
            append(" recipe")
            values.forEach { value ->
                append(' ')
                append(shellQuote(value))
            }
        }.also { require(it.toByteArray(Charsets.UTF_8).size <= 8192) }
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
