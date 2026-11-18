// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.terminal.terminalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioSettings(
    preferences: Preferences,
    installed: List<LinuxEnvironment>,
    detail: (String) -> Unit,
    backup: () -> Unit,
    defaults: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun update(save: suspend () -> Unit) {
        if (saving) return
        saving = true
        error = false
        scope.launch {
            try { save() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    val starts = listOf(
        "Home" to stringResource(R.string.studio_home),
        "Android" to stringResource(R.string.android_shell),
        "Environments" to stringResource(R.string.studio_linux),
        "Sessions" to stringResource(R.string.sessions_label),
    ) + installed.filter { it.ready }.map { it.id to it.name }
    val themes = listOf(
        "Dark" to stringResource(R.string.theme_dark),
        "Light" to stringResource(R.string.theme_light),
        "System" to stringResource(R.string.theme_system),
    )
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { StudioHeading(stringResource(R.string.settings_label)) }
        if (error) item {
            Text(stringResource(R.string.studio_save_failed), color = MaterialTheme.colorScheme.error)
        }
        item {
            val (foreground, background) = terminalColors(preferences.palette)
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color(background), contentColor = Color(foreground)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.studio_console_sample), style = MaterialTheme.typography.labelMedium)
                    Text("$ ls\nnotes  src  README.md", fontSize = preferences.font.sp,
                        style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_text_size), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FilledTonalIconButton({ preferences.setTerminalFont(preferences.font - 1) }, enabled = !saving && preferences.font > 8) {
                    Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.smaller_terminal_text))
                }
                Text(stringResource(R.string.font_size_value, preferences.font), Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelLarge)
                FilledTonalIconButton({ preferences.setTerminalFont(preferences.font + 1) }, enabled = !saving && preferences.font < 28) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.larger_terminal_text))
                }
            }
        }
        item {
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Graphite", "Midnight", "Paper", "Solarized").forEach { palette ->
                    FilterChip(selected = preferences.palette == palette, enabled = !saving, onClick = {
                        update { preferences.saveAppearance(preferences.font, palette, preferences.appTheme, preferences.accentHue,
                            preferences.accentChroma, preferences.trueBlack, preferences.dynamicColors) }
                    }, label = { Text(palette) })
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(selected = preferences.appTheme == value, enabled = !saving,
                        onClick = { update { preferences.saveBehavior(value, preferences.keepAwake, preferences.startup) } },
                        shape = SegmentedButtonDefaults.itemShape(index, themes.size), label = { Text(label) })
                }
            }
        }
        item {
            var choosing by remember { mutableStateOf(false) }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    Box {
                        StudioSetting(stringResource(R.string.startup_open), R.drawable.ic_shell,
                            starts.firstOrNull { it.first == preferences.startup }?.second ?: stringResource(R.string.startup_unavailable)) {
                            choosing = !saving
                        }
                        DropdownMenu(choosing, { choosing = false }) {
                            starts.forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    choosing = false
                                    update { preferences.saveBehavior(preferences.appTheme, preferences.keepAwake, value) }
                                })
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 60.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_prompt), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.keep_screen_awake_short), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        val keepAwakeLabel = stringResource(R.string.keep_screen_awake_short)
                        Switch(preferences.keepAwake,
                            { value -> update { preferences.saveBehavior(preferences.appTheme, value, preferences.startup) } },
                            enabled = !saving, modifier = Modifier.semantics { contentDescription = keepAwakeLabel })
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.studio_controls), style = MaterialTheme.typography.titleLarge)
            StudioSetting(stringResource(R.string.settings_extra_keys), R.drawable.ic_keyboard) { detail("Extra keyboard keys") }
            StudioSetting(stringResource(R.string.studio_theme_controls), R.drawable.ic_palette) { detail("Appearance") }
            StudioSetting(stringResource(R.string.working_mode), R.drawable.ic_shield) { detail("Working mode") }
        }
        item {
            Text(stringResource(R.string.studio_data), style = MaterialTheme.typography.titleLarge)
            StudioSetting(stringResource(R.string.backup_title), R.drawable.ic_storage, click = backup)
            StudioSetting(stringResource(R.string.guest_defaults_title), R.drawable.ic_environments, click = defaults)
            StudioSetting(stringResource(R.string.terminal_tools), R.drawable.ic_tools) { detail("Terminal & tools") }
            StudioSetting(stringResource(R.string.settings_about), R.drawable.ic_info, BuildConfig.VERSION_NAME) { detail("About this app") }
        }
    }
}

@Composable
private fun StudioSetting(title: String, icon: Int, detail: String? = null, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = click)
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(17.dp))
    }
}
