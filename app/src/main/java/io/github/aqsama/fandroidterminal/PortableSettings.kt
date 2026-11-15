// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import io.github.aqsama.fandroidterminal.runtime.linux.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit portable allowlist. No Android Keystore entries, live sessions or terminal output. */
internal class PortableSettings(context: Context) {
    private val store = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    companion object {
        val groups = linkedMapOf(
            "appearance" to listOf("font", "palette", "appTheme", "keepAwake", "accentHue", "accentChroma", "trueBlack", "dynamicColors", "paletteStyle"),
            "input" to listOf("keys", "snippets"),
            "startup" to listOf("startup", "launchers"),
            "defaults" to listOf("global", "debian", "ubuntu", "alpine").flatMap { listOf("profile.$it", "shell.$it", "directory.$it") },
        )
    }
    fun export(selected: Set<String>): ByteArray {
        val json = JSONObject()
        groups.filterKeys { it in selected }.values.flatten().forEach { key -> store.all[key]?.let { json.put(key, it) } }
        return json.toString().toByteArray()
    }
    fun validate(bytes: ByteArray): JSONObject {
        require(bytes.size <= 1024 * 1024)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.keys().asSequence().all { it in groups.values.flatten() })
        json.keys().forEach { key ->
            if (key !in listOf("font", "keepAwake", "accentHue", "accentChroma", "trueBlack", "dynamicColors")) require(json.get(key) is String) { "Invalid setting type: $key" }
            when (key) {
                "font" -> require(json.get(key) is Int && json.getInt(key) in 8..28)
                "accentHue" -> require(json.get(key) is Int && json.getInt(key) in 0..359)
                "accentChroma" -> require(json.get(key) is Int && json.getInt(key) in 20..90)
                "keepAwake", "trueBlack", "dynamicColors" -> require(json.get(key) is Boolean)
                "palette" -> require(json.getString(key) in listOf("Graphite", "Midnight", "Paper", "Solarized"))
                "paletteStyle" -> require(json.getString(key) in io.github.aqsama.fandroidterminal.designsystem.WorkspacePaletteStyles)
                "appTheme" -> require(json.getString(key) in listOf("System", "Light", "Dark"))
                "keys" -> require(json.getString(key).split(' ').filter { it.isNotBlank() }.let { it.size <= 16 && it.all { it in Preferences.supportedKeys } })
                "snippets" -> require(json.getString(key).length <= 16384)
                "startup" -> require(json.getString(key) in listOf("Android", "Sessions", "Environments") || runCatching { java.util.UUID.fromString(json.getString(key)) }.isSuccess)
                "launchers" -> {
                    val rows = JSONArray(json.getString(key)); require(rows.length() <= 50)
                    val ids = mutableSetOf<String>()
                    repeat(rows.length()) { index ->
                        val row = rows.getJSONObject(index); val argv = row.getJSONArray("arguments")
                        CliLauncher(row.getString("id"), row.getString("name"), row.getString("environmentId"), row.getString("directory"),
                            (0 until argv.length()).map { argv.getString(it) }).validated()
                        require(ids.add(row.getString("id")))
                    }
                }
                else -> {
                    val value = json.getString(key)
                    when {
                        key.startsWith("profile.") -> PackageMetadata.names(value)
                        else -> require(value.startsWith('/') && value.length <= 4096 && value.none(Char::isISOControl))
                    }
                }
            }
        }
        return json
    }
    fun restore(bytes: ByteArray, selected: Set<String>, identities: Map<String, String>, available: Set<String> = emptySet()) {
        val json = validate(bytes)
        if (json.has("startup")) {
            val startup = json.getString("startup")
            if (startup !in listOf("Android", "Sessions", "Environments")) json.put("startup", identities[startup] ?: startup.takeIf { it in available } ?: "Environments")
        }
        if (json.has("launchers")) {
            val old = JSONArray(json.getString("launchers")); val rows = JSONArray()
            repeat(old.length()) { index ->
                val row = old.getJSONObject(index)
                row.put("environmentId", identities[row.getString("environmentId")] ?: row.getString("environmentId"))
                row.put("id", java.util.UUID.randomUUID().toString()); rows.put(row)
            }
            json.put("launchers", rows.toString())
        }
        val editor = store.edit()
        groups.filterKeys { it in selected }.values.flatten().forEach { key ->
            if (json.has(key)) when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported preference type")
            }
        }
        check(editor.commit()) { "Cannot save restored settings; environment copies are preserved" }
    }
}
