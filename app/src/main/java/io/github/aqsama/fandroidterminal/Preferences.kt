// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Preferences(context: Context) {
    private val store = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    var font by mutableStateOf(store.getInt("font", 14)); private set
    var palette by mutableStateOf(store.getString("palette", "Midnight")!!); private set
    var keys by mutableStateOf(store.getString("keys", "Esc Ctrl Alt Tab Up Down Left Right")!!); private set
    var snippets by mutableStateOf(store.getString("snippets", "")!!); private set
    var recent by mutableStateOf(store.getString("recent", "/root\n/")!!); private set
    suspend fun save(font: Int, palette: String, keys: String, snippets: String) {
        require(font in 8..28 && palette in listOf("Midnight", "Paper", "Solarized"))
        require(keys.split(' ').filter { it.isNotBlank() }.let { it.size <= 16 && it.all { k -> k in supportedKeys } }) { "Choose up to 16 supported keys, separated by spaces" }
        require(snippets.length <= 16384) { "Saved commands exceed 16 KiB" }
        withContext(NonCancellable) {
        withContext(Dispatchers.IO) { check(store.edit().putInt("font", font).putString("palette", palette).putString("keys", keys).putString("snippets", snippets).commit()) { "Cannot save preferences" } }
        this.font = font; this.palette = palette; this.keys = keys; this.snippets = snippets
        }
    }
    fun visit(path: String) {
        recent = (listOf(path) + recent.lines()).distinct().take(8).joinToString("\n")
        store.edit().putString("recent", recent).apply()
    }
    companion object { val supportedKeys = setOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right", "Home", "End", "PgUp", "PgDn", "Insert", "Delete") }
}
