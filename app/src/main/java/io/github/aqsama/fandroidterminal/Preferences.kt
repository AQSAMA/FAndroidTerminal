// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Preferences(context: Context) {
    private val store = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    var launchers by mutableStateOf(readLaunchers()); private set
    private fun readLaunchers(): List<io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher> = runCatching {
        val rows = org.json.JSONArray(store.getString("launchers", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i); val argv = row.getJSONArray("arguments")
            io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher(row.getString("id"), row.getString("name"), row.getString("environmentId"),
                row.getString("directory"), (0 until argv.length()).map { argv.getString(it) }).validated()
        }
    }.getOrDefault(emptyList())
    suspend fun saveLaunchers(value: List<io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher>) {
        require(value.size <= 50 && value.map { it.id }.distinct().size == value.size)
        val json = org.json.JSONArray().apply { value.forEach { item ->
            item.validated()
            put(org.json.JSONObject().put("id", item.id).put("name", item.name).put("environmentId", item.environmentId)
                .put("directory", item.directory).put("arguments", org.json.JSONArray(item.arguments)))
        } }.toString()
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { check(store.edit().putString("launchers", json).commit()) }
            launchers = value
        }
    }
    var linuxHintDismissed by mutableStateOf(store.getBoolean("linuxHintDismissed", false)); private set
    fun dismissLinuxHint() { linuxHintDismissed = true; store.edit().putBoolean("linuxHintDismissed", true).apply() }
    var font by mutableStateOf(store.getInt("font", 14)); private set
    fun setTerminalFont(value: Int) {
        font = value.coerceIn(8, 28)
        store.edit().putInt("font", font).apply()
    }
    var appTheme by mutableStateOf(store.getString("appTheme", "System")!!); private set
    var keepAwake by mutableStateOf(store.getBoolean("keepAwake", false)); private set
    var startup by mutableStateOf(store.getString("startup", "Android")!!); private set
    suspend fun saveBehavior(theme: String, awake: Boolean, destination: String) {
        require(theme in listOf("System", "Light", "Dark"))
        require(destination in listOf("Android", "Environments", "Sessions") || runCatching { java.util.UUID.fromString(destination) }.isSuccess)
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                check(store.edit().putString("appTheme", theme).putBoolean("keepAwake", awake).putString("startup", destination).commit())
            }
            appTheme = theme; keepAwake = awake; startup = destination
        }
    }
    var palette by mutableStateOf(store.getString("palette", "Graphite")!!); private set
    var keys by mutableStateOf(store.getString("keys", "Esc Ctrl Alt Tab Up Down Left Right")!!); private set
    var snippets by mutableStateOf(store.getString("snippets", "")!!); private set
    var recent by mutableStateOf(store.getString("recent", "/root\n/")!!); private set
    suspend fun save(font: Int, palette: String, keys: String, snippets: String) {
        require(font in 8..28 && palette in listOf("Graphite", "Midnight", "Paper", "Solarized"))
        require(keys.split(' ').filter { it.isNotBlank() }.let { it.size <= 16 && it.all { k -> k in supportedKeys } }) { "Choose up to 16 supported keys, separated by spaces" }
        require(snippets.length <= 16384) { "Saved commands exceed 16 KiB" }
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                check(store.edit().putInt("font", font).putString("palette", palette)
                    .putString("keys", keys).putString("snippets", snippets).commit()) { "Cannot save preferences" }
            }
            this@Preferences.font = font
            this@Preferences.palette = palette
            this@Preferences.keys = keys
            this@Preferences.snippets = snippets
        }
    }
    fun reload() {
        font = store.getInt("font", 14); palette = store.getString("palette", "Graphite")!!
        keys = store.getString("keys", "Esc Ctrl Alt Tab Up Down Left Right")!!; snippets = store.getString("snippets", "")!!
        appTheme = store.getString("appTheme", "System")!!; keepAwake = store.getBoolean("keepAwake", false)
        startup = store.getString("startup", "Android")!!; launchers = readLaunchers()
    }
    fun visit(path: String) {
        recent = (listOf(path) + recent.lines()).distinct().take(8).joinToString("\n")
        store.edit().putString("recent", recent).apply()
    }
    companion object { val supportedKeys = setOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right", "Home", "End", "PgUp", "PgDn", "Insert", "Delete", "/", "-", "|", "~") }
}
