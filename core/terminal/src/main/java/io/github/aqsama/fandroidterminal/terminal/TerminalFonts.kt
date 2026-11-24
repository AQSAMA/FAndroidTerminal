// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.terminal

import android.content.Context
import android.graphics.Typeface

/** Bundled OFL monospace fonts (see THIRD_PARTY_NOTICES.md) plus the platform monospace face. */
data class TerminalFont(val id: String, val name: String, val resource: Int?)

object TerminalFonts {
    val all = listOf(
        TerminalFont("JetBrainsMono", "JetBrains Mono", R.font.jetbrains_mono),
        TerminalFont("FiraCode", "Fira Code", R.font.fira_code),
        TerminalFont("IBMPlexMono", "IBM Plex Mono", R.font.ibm_plex_mono),
        TerminalFont("System", "System mono", null),
    )
    val default = all.first()
    fun get(id: String?) = all.firstOrNull { it.id == id } ?: default

    private val cache = HashMap<String, Typeface>()
    fun typeface(context: Context, id: String?): Typeface = synchronized(cache) {
        val font = get(id)
        cache.getOrPut(font.id) {
            font.resource?.let { runCatching { context.resources.getFont(it) }.getOrNull() } ?: Typeface.MONOSPACE
        }
    }
}
