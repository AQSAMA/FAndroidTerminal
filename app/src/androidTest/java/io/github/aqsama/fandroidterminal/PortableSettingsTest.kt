// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.util.UUID

class PortableSettingsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = context.getSharedPreferences("workspace", android.content.Context.MODE_PRIVATE)
    private val previous = store.all.toMap()

    @After fun restore() {
        val editor = store.edit().clear()
        previous.forEach { (key, value) -> when (value) {
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        } }
        check(editor.commit())
    }
    @Test fun legacyKeyMigrationRunsOnceAndThenHonorsAnEditedLegacyLayout() = runBlocking {
        val old = "Esc Ctrl Alt :: Tab / - Home Up End PgUp Left Down Right PgDn"
        check(store.edit().remove("keyRowMigrationV2").putString("keys", old).commit())
        val preferences = Preferences(context)
        assertEquals(io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.DEFAULT, preferences.keys)
        preferences.save(preferences.font, preferences.palette, old, preferences.snippets)
        preferences.reload()
        assertEquals(old, preferences.keys)
        assertEquals(old, Preferences(context).keys)
    }
    @Test fun malformedFontTypeCannotBeCommitted() {
        val portable = PortableSettings(context)
        listOf("{\"font\":\"14\"}", "{\"font\":14.5}", "{\"keepAwake\":\"true\"}", "{\"snippets\":14}", "{\"profile.global\":123}").forEach { json ->
            assertThrows(IllegalArgumentException::class.java) { portable.validate(json.toByteArray()) }
        }
    }
    @Test fun customizedThemeSurvivesPortableRestoreAndReload() = runBlocking {
        val prefs = Preferences(context)
        prefs.saveAppearance(16, "Solarized", "Dark", 275, 90, true, false, "Expressive")
        val portable = PortableSettings(context)
        val bytes = portable.export(setOf("appearance"))
        prefs.saveAppearance(14, "Graphite", "Light", 168, 30, false, true)
        portable.restore(bytes, setOf("appearance"), emptyMap())
        prefs.reload()
        assertEquals("Expressive", prefs.paletteStyle); assertEquals(275, prefs.accentHue); assertEquals(90, prefs.accentChroma)
        assertTrue(prefs.trueBlack); assertFalse(prefs.dynamicColors)
        assertEquals("Dark", prefs.appTheme); assertEquals("Solarized", prefs.palette)
        listOf("{\"accentHue\":360}", "{\"accentChroma\":10}", "{\"trueBlack\":\"yes\"}").forEach {
            assertThrows(IllegalArgumentException::class.java) { portable.validate(it.toByteArray()) }
        }
    }
    @Test fun settingsOnlyRestorePreservesSameDeviceAndUnavailableLauncherReferences() = runBlocking {
        val environment = UUID.randomUUID().toString()
        val launcher = CliLauncher(UUID.randomUUID().toString(), "Tool", environment, "/root", listOf("/usr/bin/tool", "literal space"))
        val preferences = Preferences(context)
        preferences.saveLaunchers(listOf(launcher)); preferences.saveBehavior("System", false, environment)
        val portable = PortableSettings(context); val bytes = portable.export(setOf("startup"))
        portable.restore(bytes, setOf("startup"), emptyMap(), setOf(environment))
        preferences.reload()
        assertEquals(environment, preferences.startup); assertEquals(environment, preferences.launchers.single().environmentId)
        portable.restore(bytes, setOf("startup"), emptyMap(), emptySet())
        preferences.reload()
        assertEquals("Environments", preferences.startup); assertEquals(environment, preferences.launchers.single().environmentId)
        val copy = UUID.randomUUID().toString()
        portable.restore(bytes, setOf("startup"), mapOf(environment to copy), setOf(copy))
        preferences.reload()
        assertEquals(copy, preferences.startup); assertEquals(copy, preferences.launchers.single().environmentId)
    }
}
