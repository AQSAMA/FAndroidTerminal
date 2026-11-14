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
import androidx.compose.ui.draw.clip
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
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import kotlinx.coroutines.launch

@Composable
private fun SettingLink(icon: Int, title: String, description: String, action: () -> Unit) {
    ListItem(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = action), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) }, supportingContent = { Text(description) }, trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(preferences: Preferences, requestedSection: String? = null, requestHandled: () -> Unit = {}, environments: () -> Unit = {}, licenses: () -> Unit = {}, exit: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf(requestedSection) }
    LaunchedEffect(requestedSection) { if (requestedSection != null) { section = requestedSection; requestHandled() } }
    var font by rememberSaveable { mutableIntStateOf(preferences.font) }
    var keys by rememberSaveable { mutableStateOf(preferences.keys.split(' ').filter { it.isNotBlank() }) }
    var snippetsLimitError by remember { mutableStateOf(false) }
    var snippets by rememberSaveable { mutableStateOf(preferences.snippets) }
    var palette by rememberSaveable { mutableStateOf(preferences.palette) }
    var result by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appearanceDirty = font != preferences.font || palette != preferences.palette
    val keysDirty = keys.joinToString(" ") != preferences.keys
    val commandsDirty = snippets != preferences.snippets
    val editable = section in listOf("Appearance", "Extra keyboard keys", "Saved commands")
    val dirty = when (section) {
        "Appearance" -> appearanceDirty
        "Extra keyboard keys" -> keysDirty
        "Saved commands" -> commandsDirty
        else -> false
    }
    fun back() { if (section != null) section = null else exit() }
    BackHandler(section != null) { back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_label)) }
            Text(when (section) { "Appearance" -> stringResource(R.string.settings_appearance); "Extra keyboard keys" -> stringResource(R.string.settings_extra_keys); "Saved commands" -> stringResource(R.string.settings_saved_commands); "Keyboard & clipboard" -> stringResource(R.string.settings_keyboard_clipboard); "About this app" -> stringResource(R.string.settings_about); else -> stringResource(R.string.settings_label) },
                Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
        }
        Page("", showTitle = false) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (section == null) {
                    if (appearanceDirty || keysDirty || commandsDirty) Text(stringResource(R.string.settings_drafts_kept), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_terminal_input), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ConnectedGroup {
                        SettingLink(R.drawable.ic_palette, stringResource(R.string.settings_appearance), (if (appearanceDirty) stringResource(R.string.unsaved_changes) else stringResource(R.string.appearance_summary, preferences.font, preferences.palette))) { section = "Appearance"; result = "" }
                        SettingLink(R.drawable.ic_keyboard, stringResource(R.string.settings_extra_keys), (if (keysDirty) stringResource(R.string.unsaved_changes) else stringResource(R.string.settings_key_count, preferences.keys.split(' ').count { it.isNotBlank() }))) { section = "Extra keyboard keys"; result = "" }
                        SettingLink(R.drawable.ic_commands, stringResource(R.string.settings_saved_commands), (if (commandsDirty) stringResource(R.string.unsaved_changes) else stringResource(R.string.saved_command_count, preferences.snippets.lines().count { it.isNotBlank() }))) { section = "Saved commands"; result = "" }
                    }
                    Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ConnectedGroup {
                        SettingLink(R.drawable.ic_help, stringResource(R.string.settings_keyboard_clipboard), stringResource(R.string.settings_keyboard_description)) { section = "Keyboard & clipboard" }
                        SettingLink(R.drawable.ic_info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_description)) { section = "About this app" }
                    }
                    Text(stringResource(R.string.linux_environments), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ConnectedGroup {
                        SettingLink(R.drawable.ic_environments, stringResource(R.string.guest_settings_title), stringResource(R.string.settings_environment_description), environments)
                    }
                    Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT.take(12)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                } else {
                    when (section) {
                        "Appearance" -> {
                            AppearanceSettings(font, palette, { font = it; result = "" }, { palette = it; result = "" })
                        }
                        "Extra keyboard keys" -> {
                            val previewKeys = listOf("Ctrl", "Alt", "Esc").filter { it in keys } + keys.filter { it !in listOf("Ctrl", "Alt", "Esc") }
                            Text(stringResource(R.string.settings_key_row), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.settings_key_row_help))
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { if (keys.isEmpty()) Text(stringResource(R.string.settings_no_keys)) else previewKeys.forEach { Text(it, style = MaterialTheme.typography.labelLarge) } }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Preferences.supportedKeys.forEach { name -> FilterChip(name in keys, { keys = if (name in keys) keys - name else keys + name; result = "" }, label = { Text(name) }, leadingIcon = if (name in keys) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }) else null) } }
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
                                listOf(R.string.focus_mode to "Ctrl + Shift + F", R.string.commands_title to "Ctrl + Shift + P", R.string.new_session to "Ctrl + Shift + N", R.string.switch_session to "Ctrl + Shift + Tab", R.string.paste_label to "Ctrl + Shift + V").forEach { (label, shortcut) -> ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(label)) }, supportingContent = { Text(shortcut, fontFamily = FontFamily.Monospace) }) }
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
            if (editable) SettingsSaveArea(dirty, saving, result.takeIf { it.isNotEmpty() && it != "Changes saved" }, result == "Changes saved", stringResource(R.string.save_changes)) {
                val selectedSection = section
                val newFont = if (selectedSection == "Appearance") font else preferences.font
                val newPalette = if (selectedSection == "Appearance") palette else preferences.palette
                val newKeys = if (selectedSection == "Extra keyboard keys") keys.joinToString(" ") else preferences.keys
                val newSnippets = if (selectedSection == "Saved commands") snippets else preferences.snippets
                saving = true
                scope.launch { try { preferences.save(newFont, newPalette, newKeys, newSnippets); result = "Changes saved" } catch (e: Exception) { result = e.message ?: "Could not save settings" } finally { saving = false } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun GuestSettingsPage(service: TerminalService, id: String, manageSessions: () -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = environments.firstOrNull { it.id == id } ?: return
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val count = sessions.count { it.environmentId == id }
    GuestSettingsContent(environment, count, progress, manageSessions) { value -> service.environments.saveSettings(value, service.sessions) }
}

@Composable
internal fun GuestSettingsContent(environment: LinuxEnvironment, count: Int,
    progress: Map<String, String>, manageSessions: () -> Unit, save: (LinuxEnvironment) -> Unit) {
    val id = environment.id
    var dns by rememberSaveable(id) { mutableStateOf(environment.dns) }
    var compatibility by rememberSaveable(id) { mutableStateOf(environment.compatibility) }
    var directory by rememberSaveable(id) { mutableStateOf(environment.startDirectory) }
    var shell by rememberSaveable(id) { mutableStateOf(environment.shell) }
    var variables by rememberSaveable(id) { mutableStateOf(environment.variables) }
    var message by remember { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable(id) { mutableStateOf(false) }
    var advanced by rememberSaveable(id) { mutableStateOf(environment.variables.isNotBlank()) }
    val dirty = dns != environment.dns || compatibility != environment.compatibility || directory != environment.startDirectory || shell != environment.shell || variables != environment.variables
    val saving = progress[id] == "Saving settings…"
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(environment) { if (submitted && !dirty) { message = "Changes saved"; submitted = false } }
    Page(environment.name, stringResource(R.string.guest_settings_title)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (count > 0) ConnectedGroup {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.review_sessions)) },
                    supportingContent = { Text(stringResource(R.string.env_access_lock, count)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_terminal), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) },
                    modifier = Modifier.clickable(onClick = manageSessions))
            } else Text(stringResource(R.string.guest_settings_apply), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.guest_network), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it; message = null }, label = { Text(stringResource(R.string.guest_dns)) }, supportingText = { Text(stringResource(R.string.guest_dns_help), style = MaterialTheme.typography.bodyMedium) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_start), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(directory, { directory = it; message = null }, label = { Text(stringResource(R.string.guest_folder)) }, supportingText = { Text(stringResource(R.string.guest_folder_help), style = MaterialTheme.typography.bodyMedium) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            OutlinedTextField(shell, { shell = it; message = null }, label = { Text(stringResource(R.string.guest_shell)) }, supportingText = { Text(stringResource(R.string.guest_shell_help), style = MaterialTheme.typography.bodyMedium) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_compatibility), style = MaterialTheme.typography.titleMedium)
            ConnectedGroup {
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.guest_compatibility_mode)) }, supportingContent = { Text(stringResource(R.string.guest_compatibility_help)) }, leadingContent = { Icon(painterResource(R.drawable.ic_shield), null) }, trailingContent = { Switch(compatibility, onCheckedChange = null) }, modifier = Modifier.toggleable(value = compatibility, role = Role.Switch, onValueChange = { compatibility = it; message = null }))
            }
            ConnectedGroup {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.guest_advanced)) },
                    supportingContent = { Text(stringResource(R.string.guest_variables)) },
                    trailingContent = { Icon(painterResource(if (advanced) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null) },
                    modifier = Modifier.clickable { advanced = !advanced })
            }
            if (advanced) OutlinedTextField(variables, { variables = it; message = null }, label = { Text(stringResource(R.string.guest_variables)) }, supportingText = { Text(stringResource(R.string.guest_variables_help), style = MaterialTheme.typography.bodyMedium) }, minLines = 3, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            Spacer(Modifier.height(16.dp))
        }
        SettingsSaveArea(dirty, saving, message?.takeIf { it != "Changes saved" }, message == "Changes saved", stringResource(R.string.guest_save),
            enabled = count == 0 && progress.isEmpty(), reviewSessions = if (count > 0) manageSessions else null) {
            message = null; submitted = true
            runCatching { save(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables)) }
                .onFailure { message = it.message ?: resources.getString(R.string.check_values); submitted = false }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Shared edit feedback keeps app and environment settings consistent, including blocked saves. */
@Composable
internal fun SettingsSaveArea(dirty: Boolean, saving: Boolean, error: String?, saved: Boolean, label: String,
    enabled: Boolean = true, reviewSessions: (() -> Unit)? = null, save: () -> Unit) {
    if (dirty || saving || error != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (saving) R.string.saving_settings else R.string.unsaved_changes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(enabled = dirty && !saving && enabled, onClick = save, modifier = Modifier.fillMaxWidth()) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(label)
            }
            reviewSessions?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_sessions)) } }
        }
    } else if (saved) Text(stringResource(R.string.changes_saved), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(font: Int, palette: String, setFont: (Int) -> Unit, setPalette: (String) -> Unit) {
    val sizing: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_text_size_help), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { setFont((font - 1).coerceAtLeast(8)) }, enabled = font > 8) { Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.smaller_terminal_text)) }
                    Text("$font sp", style = MaterialTheme.typography.headlineSmall)
                    FilledTonalIconButton(onClick = { setFont((font + 1).coerceAtMost(28)) }, enabled = font < 28) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.larger_terminal_text)) }
                }
            }
        }
    }
    val colors: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_terminal_colors), style = MaterialTheme.typography.titleMedium)
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Graphite", "Midnight", "Paper", "Solarized").forEach { name ->
                    val sample = io.github.aqsama.fandroidterminal.terminal.terminalColors(name)
                    FilterChip(modifier = Modifier.weight(1f), selected = palette == name, onClick = { setPalette(name) }, label = { Text(name) },
                        leadingIcon = { Surface(shape = RoundedCornerShape(4.dp), color = Color(sample.second), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Text(if (palette == name) "✓" else "Aa", Modifier.padding(3.dp), color = Color(sample.first), style = MaterialTheme.typography.labelSmall)
                        } })
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).second)) {
                Text("~/projects $ ls\nnotes  src  README.md", Modifier.fillMaxWidth().padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = font.sp, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).first))
            }
            Text(stringResource(R.string.settings_preview_help), style = MaterialTheme.typography.bodySmall)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(0.4f)) { sizing() }
            Box(Modifier.weight(0.6f)) { colors() }
        } else Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { sizing(); colors() }
    }
}
