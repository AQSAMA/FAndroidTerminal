// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import kotlinx.coroutines.launch

@Composable
private fun SettingLink(icon: Int, title: String, description: String, action: () -> Unit) {
    ListItem(modifier = Modifier.clickable(onClick = action), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(painterResource(icon), null, Modifier.padding(10.dp).size(22.dp)) } },
        headlineContent = { Text(title) }, supportingContent = { Text(description) }, trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(preferences: Preferences, licenses: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var font by rememberSaveable { mutableIntStateOf(preferences.font) }
    var keys by rememberSaveable { mutableStateOf(preferences.keys.split(' ').filter { it.isNotBlank() }) }
    var snippetsLimitError by remember { mutableStateOf(false) }
    var snippets by rememberSaveable { mutableStateOf(preferences.snippets) }
    var palette by rememberSaveable { mutableStateOf(preferences.palette) }
    var result by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val editable = section in listOf("Appearance", "Extra keyboard keys", "Saved commands")
    val dirty = when (section) {
        "Appearance" -> font != preferences.font || palette != preferences.palette
        "Extra keyboard keys" -> keys.joinToString(" ") != preferences.keys
        "Saved commands" -> snippets != preferences.snippets
        else -> false
    }
    BackHandler(section != null) { section = null }
    Page(when (section) { "Appearance" -> stringResource(R.string.settings_appearance); "Extra keyboard keys" -> stringResource(R.string.settings_extra_keys); "Saved commands" -> stringResource(R.string.settings_saved_commands); "Keyboard & clipboard" -> stringResource(R.string.settings_keyboard_clipboard); "About this app" -> stringResource(R.string.settings_about); else -> stringResource(R.string.settings_label) }, if (section == null) stringResource(R.string.settings_intro) else "", showTitle = section != null) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (section == null) {
                Text(stringResource(R.string.settings_terminal_input), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConnectedGroup {
                    SettingLink(R.drawable.ic_palette, stringResource(R.string.settings_appearance), stringResource(R.string.appearance_summary, preferences.font, preferences.palette)) { section = "Appearance"; result = "" }
                    SettingLink(R.drawable.ic_keyboard, stringResource(R.string.settings_extra_keys), stringResource(R.string.settings_keys_description)) { section = "Extra keyboard keys"; result = "" }
                    SettingLink(R.drawable.ic_commands, stringResource(R.string.settings_saved_commands), stringResource(R.string.settings_commands_description)) { section = "Saved commands"; result = "" }
                }
                Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConnectedGroup {
                    SettingLink(R.drawable.ic_help, stringResource(R.string.settings_keyboard_clipboard), stringResource(R.string.settings_keyboard_description)) { section = "Keyboard & clipboard" }
                    SettingLink(R.drawable.ic_info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_description)) { section = "About this app" }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_environments), null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.settings_linux_location), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                TextButton(onClick = { section = null }) { Icon(painterResource(R.drawable.ic_back), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.all_settings)) }
                when (section) {
                    "Appearance" -> {
                        Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_text_size_help), style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                FilledTonalIconButton(onClick = { font = (font - 1).coerceAtLeast(8); result = "" }, enabled = font > 8) { Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.smaller_terminal_text)) }
                                Text("$font sp", style = MaterialTheme.typography.headlineSmall)
                                FilledTonalIconButton(onClick = { font = (font + 1).coerceAtMost(28); result = "" }, enabled = font < 28) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.larger_terminal_text)) }
                            }
                        }
                        Text(stringResource(R.string.settings_terminal_colors), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Graphite", "Midnight", "Paper", "Solarized").forEach { name -> FilterChip(selected = palette == name, onClick = { palette = name; result = "" }, label = { Text(name) }, leadingIcon = if (palette == name) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }) else null) }
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).second)) {
                            Text("~/projects $ ls\nnotes  src  README.md\n~/projects $ ▋", Modifier.fillMaxWidth().padding(20.dp), fontFamily = FontFamily.Monospace, fontSize = font.sp, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).first))
                        }
                        Text(stringResource(R.string.settings_preview_help), style = MaterialTheme.typography.bodySmall)
                    }
                    "Extra keyboard keys" -> {
                        Text(stringResource(R.string.settings_key_row), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_key_row_help))
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { if (keys.isEmpty()) Text(stringResource(R.string.settings_no_keys)) else keys.forEach { Text(it, style = MaterialTheme.typography.labelLarge) } }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Preferences.supportedKeys.forEach { name -> FilterChip(name in keys, { keys = if (name in keys) keys - name else keys + name; result = "" }, label = { Text(name) }) } }
                        Text(stringResource(R.string.settings_modifier_help), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { keys = listOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right"); result = "" }) { Text(stringResource(R.string.settings_reset_keys)) }
                    }
                    "Saved commands" -> {
                        Text(stringResource(R.string.settings_command_help))
                        OutlinedTextField(snippets, { snippetsLimitError = it.length > 16384; if (!snippetsLimitError) { snippets = it; result = "" } }, isError = snippetsLimitError, supportingText = if (snippetsLimitError) ({ Text(stringResource(R.string.saved_commands_size_limit)) }) else null, label = { Text(stringResource(R.string.settings_commands)) }, placeholder = { Text("git status\npython3 --version") }, minLines = 5, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                        Text(stringResource(R.string.settings_command_privacy), style = MaterialTheme.typography.bodySmall)
                    }
                    "Keyboard & clipboard" -> {
                        Text(stringResource(R.string.settings_hardware_keyboard), style = MaterialTheme.typography.titleMedium)
                        ConnectedGroup {
                            listOf(R.string.command_launcher to "Ctrl + Shift + P", R.string.new_session to "Ctrl + Shift + N", R.string.switch_session to "Ctrl + Shift + Tab", R.string.paste_label to "Ctrl + Shift + V").forEach { (label, shortcut) -> ListItem(headlineContent = { Text(stringResource(label)) }, supportingContent = { Text(shortcut, fontFamily = FontFamily.Monospace) }) }
                        }
                        Text(stringResource(R.string.settings_shortcut_focus))
                        Text(stringResource(R.string.settings_copy_paste), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_copy_help))
                    }
                    "About this app" -> {
                        Text("FAndroidTerminal", style = MaterialTheme.typography.titleLarge)
                        Text("${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_COMMIT.take(12)}\nGPL-3.0-only")
                        Text(stringResource(R.string.settings_capabilities), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_capabilities_help))
                        OutlinedButton(onClick = licenses) { Icon(painterResource(R.drawable.ic_info), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.licenses_label)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (editable) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (saving) stringResource(R.string.saving_settings) else if (dirty) stringResource(R.string.unsaved_changes) else if (result == "Changes saved") stringResource(R.string.changes_saved) else result.ifEmpty { stringResource(R.string.up_to_date) }, style = MaterialTheme.typography.labelMedium,
                    color = if (result.isNotEmpty() && result != "Changes saved" && !dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled = dirty && !saving, onClick = {
                    val selectedSection = section
                    val newFont = if (selectedSection == "Appearance") font else preferences.font
                    val newPalette = if (selectedSection == "Appearance") palette else preferences.palette
                    val newKeys = if (selectedSection == "Extra keyboard keys") keys.joinToString(" ") else preferences.keys
                    val newSnippets = if (selectedSection == "Saved commands") snippets else preferences.snippets
                    saving = true
                    scope.launch { try { preferences.save(newFont, newPalette, newKeys, newSnippets); result = "Changes saved" } catch (e: Exception) { result = e.message ?: "Could not save settings" } finally { saving = false } }
                }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save_changes)) }
                if (result.isNotEmpty() && result != "Changes saved") Text(result, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun GuestSettingsPage(service: TerminalService, id: String, manageSessions: () -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = environments.firstOrNull { it.id == id } ?: return
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val count = sessions.count { it.environmentId == id }
    var dns by rememberSaveable(id) { mutableStateOf(environment.dns) }
    var compatibility by rememberSaveable(id) { mutableStateOf(environment.compatibility) }
    var directory by rememberSaveable(id) { mutableStateOf(environment.startDirectory) }
    var shell by rememberSaveable(id) { mutableStateOf(environment.shell) }
    var variables by rememberSaveable(id) { mutableStateOf(environment.variables) }
    var message by remember { mutableStateOf<String?>(null) }
    val dirty = dns != environment.dns || compatibility != environment.compatibility || directory != environment.startDirectory || shell != environment.shell || variables != environment.variables
    val saving = progress[id] == "Saving settings…"
    val resources = androidx.compose.ui.platform.LocalResources.current
    Page(environment.name, stringResource(R.string.guest_settings_title)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(if (count > 0) stringResource(R.string.guest_settings_blocked) else stringResource(R.string.guest_settings_apply), Modifier.padding(16.dp)) }
            if (count > 0) OutlinedButton(onClick = manageSessions, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_owned_sessions)) }
            Text(stringResource(R.string.guest_network), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it }, label = { Text(stringResource(R.string.guest_dns)) }, supportingText = { Text(stringResource(R.string.guest_dns_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_start), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(directory, { directory = it }, label = { Text(stringResource(R.string.guest_folder)) }, supportingText = { Text(stringResource(R.string.guest_folder_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            OutlinedTextField(shell, { shell = it }, label = { Text(stringResource(R.string.guest_shell)) }, supportingText = { Text(stringResource(R.string.guest_shell_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_compatibility), style = MaterialTheme.typography.titleMedium)
            ListItem(headlineContent = { Text(stringResource(R.string.guest_compatibility_mode)) }, supportingContent = { Text(stringResource(R.string.guest_compatibility_help)) }, leadingContent = { Icon(painterResource(R.drawable.ic_shield), null) }, trailingContent = { Switch(compatibility, onCheckedChange = null) }, modifier = Modifier.toggleable(value = compatibility, role = Role.Switch, onValueChange = { compatibility = it }))
            Text(stringResource(R.string.guest_advanced), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(variables, { variables = it }, label = { Text(stringResource(R.string.guest_variables)) }, supportingText = { Text(stringResource(R.string.guest_variables_help)) }, minLines = 3, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            Text(stringResource(if (saving) R.string.saving_settings else if (dirty) R.string.unsaved_changes else R.string.up_to_date), style = MaterialTheme.typography.labelMedium)
            Button(enabled = dirty && count == 0 && progress.isEmpty(), onClick = {
                message = null
                runCatching { service.environments.saveSettings(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables), service.sessions) }
                    .onFailure { message = it.message ?: resources.getString(R.string.check_values) }
            }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.guest_save)) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
