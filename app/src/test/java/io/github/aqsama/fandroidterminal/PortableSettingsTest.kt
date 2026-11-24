// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/** Backups are user data: they must round-trip, reject malformed values and survive older formats. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortableSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store get() = context.getSharedPreferences("workspace", android.content.Context.MODE_PRIVATE)

    @Test fun malformedValuesAreRejected() {
        val portable = PortableSettings(context)
        listOf("{\"font\":\"14\"}", "{\"font\":14.5}", "{\"keepAwake\":\"true\"}", "{\"snippets\":14}", "{\"profile.global\":123}",
            "{\"accentHue\":360}", "{\"accentChroma\":10}", "{\"trueBlack\":\"yes\"}", "{\"bell\":\"Loud\"}", "{\"accentSource\":\"Rainbow\"}").forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) { portable.validate(json.toByteArray()) }
        }
    }

    @Test fun appearanceAndInputRoundTrip() {
        val prefs = Preferences(context).apply {
            setTerminalScheme("Nord"); setTerminalTypeface("FiraCode"); setTerminalFont(17); setAppTheme(AppTheme.Dark)
            setAccentSource(AccentSource.Custom); setAccent(275, 80); setTrueBlack(true); setBell(BellMode.Flash); setHapticKeys(false)
        }
        val portable = PortableSettings(context)
        val bytes = portable.export(setOf("appearance", "input"))
        prefs.resetAppearance(); prefs.setBell(BellMode.Off); prefs.setHapticKeys(true)
        portable.restore(bytes, setOf("appearance", "input"), emptyMap())
        prefs.reload()
        assertEquals("Nord", prefs.palette); assertEquals("FiraCode", prefs.terminalFont); assertEquals(17, prefs.font)
        assertEquals(AppTheme.Dark, prefs.appTheme); assertEquals(AccentSource.Custom, prefs.accentSource); assertEquals(275, prefs.accentHue)
        assertTrue(prefs.trueBlack); assertEquals(BellMode.Flash, prefs.bell); assertFalse(prefs.hapticKeys)
    }

    @Test fun olderBackupsStillRestore() {
        // Written before accent sources and the scheme catalogue existed.
        val legacy = "{\"palette\":\"Retired\",\"appTheme\":\"Light\",\"dynamicColors\":true,\"font\":12}".toByteArray()
        store.edit().clear().commit()
        PortableSettings(context).restore(legacy, setOf("appearance"), emptyMap())
        val prefs = Preferences(context)
        assertEquals(AccentSource.Wallpaper, prefs.accentSource); assertEquals(AppTheme.Light, prefs.appTheme)
        assertEquals(io.github.aqsama.fandroidterminal.terminal.TerminalSchemes.default.id, prefs.palette); assertEquals(12, prefs.font)
    }

    @Test fun launcherAndStartupReferencesFollowRestoredEnvironments() = runBlocking {
        val environment = UUID.randomUUID().toString()
        val launcher = CliLauncher(UUID.randomUUID().toString(), "Tool", environment, "/root", listOf("/usr/bin/tool", "literal space"))
        val preferences = Preferences(context)
        preferences.saveLaunchers(listOf(launcher)); preferences.setStartup(environment)
        val portable = PortableSettings(context); val bytes = portable.export(setOf("startup"))
        portable.restore(bytes, setOf("startup"), emptyMap(), setOf(environment))
        preferences.reload()
        assertEquals(environment, preferences.startup); assertEquals(environment, preferences.launchers.single().environmentId)
        portable.restore(bytes, setOf("startup"), emptyMap(), emptySet())
        preferences.reload()
        assertEquals("Environments", preferences.startup)
        val copy = UUID.randomUUID().toString()
        portable.restore(bytes, setOf("startup"), mapOf(environment to copy), setOf(copy))
        preferences.reload()
        assertEquals(copy, preferences.startup); assertEquals(copy, preferences.launchers.single().environmentId)
    }
}
