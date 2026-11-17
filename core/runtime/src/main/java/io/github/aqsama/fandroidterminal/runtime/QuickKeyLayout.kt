// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

/** Two ordered rows. The separator preserves row membership across editing and backups. */
data class QuickKeyLayout(val always: List<String>, val expanded: List<String>) {
    val tokens get() = always + SEPARATOR + expanded
    fun encode() = tokens.joinToString(" ")
    companion object {
        const val DEFAULT = "Esc Ctrl Alt Left Down Up Right :: Tab / - Home End PgUp PgDn"
        val supported = setOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right", "Home", "End", "PgUp", "PgDn", "Insert", "Delete", "/", "-", "|", "~")
        // Use a dedicated token: the pipe character is itself a terminal key.
        const val SEPARATOR = "::"
        fun parse(value: String): QuickKeyLayout {
            val keys = value.split(' ').filter { it.isNotBlank() }
            require(keys.count { it == SEPARATOR } <= 1 && keys.count { it != SEPARATOR } <= 16)
            require(keys.all { it == SEPARATOR || it in supported })
            val split = keys.indexOf(SEPARATOR)
            if (split >= 0) return QuickKeyLayout(keys.take(split).distinct(), keys.drop(split + 1).distinct().filterNot { it in keys.take(split) })
            val count = if (keys.take(3).toSet() == setOf("Esc", "Ctrl", "Alt")) 3 else minOf(7, keys.size)
            return QuickKeyLayout(keys.take(count).distinct(), keys.drop(count).distinct().filterNot { it in keys.take(count) })
        }
    }
}
