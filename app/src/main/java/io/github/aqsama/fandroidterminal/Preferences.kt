// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import androidx.compose.runtime.*
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import io.github.aqsama.fandroidterminal.terminal.TerminalFonts
import io.github.aqsama.fandroidterminal.terminal.TerminalSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class AppTheme { System, Light, Dark }

/** What a terminal BEL (\a) does in the visible session. */
enum class BellMode { Off, Vibrate, Flash }

/** Where the app chrome takes its accent from. [Terminal] keeps the app and terminal one visual system. */
enum class AccentSource { Terminal, Custom, Wallpaper }

/**
 * App preferences. Every setter applies and persists immediately: settings screens edit live
 * values instead of drafts, so there is no save button, no conflict merging and no stale copy.
 */
class Preferences(context: Context) {
    private val store = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) = store.edit().apply(block).apply()

    // Appearance
    var appTheme by mutableStateOf(enumOr(store.getString("appTheme", null), AppTheme.System)); private set
    var accentSource by mutableStateOf(readAccentSource()); private set
    var accentHue by mutableIntStateOf(store.getInt("accentHue", 168)); private set
    var accentChroma by mutableIntStateOf(store.getInt("accentChroma", 65)); private set
    var paletteStyle by mutableStateOf(store.getString("paletteStyle", "TonalSpot")!!); private set
    var trueBlack by mutableStateOf(store.getBoolean("trueBlack", false)); private set
    var palette by mutableStateOf(TerminalSchemes.get(store.getString("palette", null)).id); private set
    var terminalFont by mutableStateOf(TerminalFonts.get(store.getString("terminalFont", null)).id); private set
    var font by mutableIntStateOf(store.getInt("font", 14).coerceIn(8, 28)); private set

    @JvmName("updateAppTheme") fun setAppTheme(value: AppTheme) { appTheme = value; edit { putString("appTheme", value.name) } }
    @JvmName("updateAccentSource") fun setAccentSource(value: AccentSource) { accentSource = value; edit { putString("accentSource", value.name).putBoolean("dynamicColors", value == AccentSource.Wallpaper) } }
    @JvmName("updateAccent") fun setAccent(hue: Int = accentHue, chroma: Int = accentChroma) {
        accentHue = hue.coerceIn(0, 359); accentChroma = chroma.coerceIn(20, 90)
        edit { putInt("accentHue", accentHue).putInt("accentChroma", accentChroma) }
    }
    @JvmName("updatePaletteStyle") fun setPaletteStyle(value: String) { require(value in io.github.aqsama.fandroidterminal.designsystem.WorkspacePaletteStyles); paletteStyle = value; edit { putString("paletteStyle", value) } }
    @JvmName("updateTrueBlack") fun setTrueBlack(value: Boolean) { trueBlack = value; edit { putBoolean("trueBlack", value) } }
    @JvmName("updateTerminalScheme") fun setTerminalScheme(id: String) { palette = TerminalSchemes.get(id).id; edit { putString("palette", palette) } }
    @JvmName("updateTerminalTypeface") fun setTerminalTypeface(id: String) { terminalFont = TerminalFonts.get(id).id; edit { putString("terminalFont", terminalFont) } }
    @JvmName("updateTerminalFont") fun setTerminalFont(value: Int) { font = value.coerceIn(8, 28); edit { putInt("font", font) } }

    // Terminal behaviour
    var keepAwake by mutableStateOf(store.getBoolean("keepAwake", false)); private set
    var hapticKeys by mutableStateOf(store.getBoolean("hapticKeys", true)); private set
    var confirmMultilinePaste by mutableStateOf(store.getBoolean("confirmPaste", true)); private set
    var bell by mutableStateOf(enumOr(store.getString("bell", null), BellMode.Vibrate)); private set
    var startup by mutableStateOf(store.getString("startup", "Android")!!); private set
    var keys by mutableStateOf(readKeys()); private set
    var snippets by mutableStateOf(store.getString("snippets", "")!!); private set

    @JvmName("updateKeepAwake") fun setKeepAwake(value: Boolean) { keepAwake = value; edit { putBoolean("keepAwake", value) } }
    @JvmName("updateHapticKeys") fun setHapticKeys(value: Boolean) { hapticKeys = value; edit { putBoolean("hapticKeys", value) } }
    @JvmName("updateConfirmMultilinePaste") fun setConfirmMultilinePaste(value: Boolean) { confirmMultilinePaste = value; edit { putBoolean("confirmPaste", value) } }
    @JvmName("updateBell") fun setBell(value: BellMode) { bell = value; edit { putString("bell", value.name) } }
    @JvmName("updateStartup") fun setStartup(value: String) {
        require(value in listOf("Android", "Environments", "Sessions") || runCatching { java.util.UUID.fromString(value) }.isSuccess)
        startup = value; edit { putString("startup", value) }
    }
    @JvmName("updateKeys") fun setKeys(value: QuickKeyLayout) { keys = value.encode(); edit { putString("keys", keys) } }
    @JvmName("updateSnippets") fun setSnippets(value: String) { require(value.length <= 16384) { "Saved commands exceed 16 KiB" }; snippets = value; edit { putString("snippets", value) } }
    val snippetList get() = snippets.lines().filter { it.isNotBlank() }

    // Workspace
    var launchers by mutableStateOf(readLaunchers()); private set
    var linuxHintDismissed by mutableStateOf(store.getBoolean("linuxHintDismissed", false)); private set
    var recent by mutableStateOf(store.getString("recent", "/root\n/")!!); private set
    fun dismissLinuxHint() { linuxHintDismissed = true; edit { putBoolean("linuxHintDismissed", true) } }
    fun visit(path: String) {
        recent = (listOf(path) + recent.lines()).distinct().take(8).joinToString("\n")
        edit { putString("recent", recent) }
    }
    suspend fun saveLaunchers(value: List<CliLauncher>) {
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

    fun resetAppearance() {
        setAppTheme(AppTheme.System); setAccentSource(AccentSource.Terminal); setAccent(168, 65)
        setPaletteStyle("TonalSpot"); setTrueBlack(false); setTerminalScheme(TerminalSchemes.default.id)
        setTerminalTypeface(TerminalFonts.default.id); setTerminalFont(14)
    }

    /** Re-reads everything after a portable-settings restore wrote the store directly. */
    fun reload() {
        appTheme = enumOr(store.getString("appTheme", null), AppTheme.System); accentSource = readAccentSource()
        accentHue = store.getInt("accentHue", 168); accentChroma = store.getInt("accentChroma", 65)
        paletteStyle = store.getString("paletteStyle", "TonalSpot")!!; trueBlack = store.getBoolean("trueBlack", false)
        palette = TerminalSchemes.get(store.getString("palette", null)).id
        terminalFont = TerminalFonts.get(store.getString("terminalFont", null)).id
        font = store.getInt("font", 14).coerceIn(8, 28)
        keepAwake = store.getBoolean("keepAwake", false); hapticKeys = store.getBoolean("hapticKeys", true)
        confirmMultilinePaste = store.getBoolean("confirmPaste", true); bell = enumOr(store.getString("bell", null), BellMode.Vibrate)
        startup = store.getString("startup", "Android")!!; keys = readKeys(); snippets = store.getString("snippets", "")!!
        launchers = readLaunchers()
    }

    private fun readAccentSource(): AccentSource = store.getString("accentSource", null)?.let { enumOr(it, AccentSource.Terminal) }
        ?: if (store.getBoolean("dynamicColors", false)) AccentSource.Wallpaper
        else if (store.contains("accentHue")) AccentSource.Custom else AccentSource.Terminal

    private fun readKeys(): String {
        val value = store.getString("keys", QuickKeyLayout.DEFAULT)!!
        return runCatching { QuickKeyLayout.parse(value).encode() }.getOrDefault(QuickKeyLayout.DEFAULT)
    }

    private fun readLaunchers(): List<CliLauncher> = runCatching {
        val rows = org.json.JSONArray(store.getString("launchers", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i); val argv = row.getJSONArray("arguments")
            CliLauncher(row.getString("id"), row.getString("name"), row.getString("environmentId"),
                row.getString("directory"), (0 until argv.length()).map { argv.getString(it) }).validated()
        }
    }.getOrDefault(emptyList())
}

private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
