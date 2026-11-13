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
internal fun SettingsPage(preferences: Preferences) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var font by rememberSaveable { mutableIntStateOf(preferences.font) }
    var keys by rememberSaveable { mutableStateOf(preferences.keys.split(' ').filter { it.isNotBlank() }) }
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
    Page(section ?: "Settings", if (section == null) "Make the terminal feel right for you." else "") {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (section == null) {
                Text("Terminal & input", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConnectedGroup {
                    SettingLink(R.drawable.ic_palette, "Appearance", "${preferences.font} sp text · ${preferences.palette} colors") { section = "Appearance"; result = "" }
                    SettingLink(R.drawable.ic_keyboard, "Extra keyboard keys", "Choose the keys above your keyboard") { section = "Extra keyboard keys"; result = "" }
                    SettingLink(R.drawable.ic_commands, "Saved commands", "Keep commands handy. You choose when to run them.") { section = "Saved commands"; result = "" }
                }
                Text("Help & information", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConnectedGroup {
                    SettingLink(R.drawable.ic_help, "Keyboard & clipboard", "Shortcuts, selection and safe pasting") { section = "Keyboard & clipboard" }
                    SettingLink(R.drawable.ic_info, "About this app", "Version, license and terminal capabilities") { section = "About this app" }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_environments), null, tint = MaterialTheme.colorScheme.primary)
                        Text("Linux settings live in Environments → Settings. Each environment has its own DNS, shell and starting directory.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                TextButton(onClick = { section = null }) { Icon(painterResource(R.drawable.ic_back), null); Spacer(Modifier.width(8.dp)); Text("All settings") }
                when (section) {
                    "Appearance" -> {
                        Text("Text size", style = MaterialTheme.typography.titleMedium)
                        Text("Change readability without restarting your terminal.", style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                FilledTonalIconButton(onClick = { font = (font - 1).coerceAtLeast(8); result = "" }, enabled = font > 8) { Icon(painterResource(R.drawable.ic_minus), "Smaller terminal text") }
                                Text("$font sp", style = MaterialTheme.typography.headlineSmall)
                                FilledTonalIconButton(onClick = { font = (font + 1).coerceAtMost(28); result = "" }, enabled = font < 28) { Icon(painterResource(R.drawable.ic_add), "Larger terminal text") }
                            }
                        }
                        Text("Terminal colors", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Midnight", "Paper", "Solarized").forEach { name -> FilterChip(selected = palette == name, onClick = { palette = name; result = "" }, label = { Text(name) }, leadingIcon = if (palette == name) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }) else null) }
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = if (palette == "Paper") Color(0xFFFAFAFA) else if (palette == "Solarized") Color(0xFF002B36) else Color(0xFF141218)) {
                            Text("~/projects $ ls\nnotes  src  README.md\n~/projects $ ▋", Modifier.fillMaxWidth().padding(20.dp), fontFamily = FontFamily.Monospace, fontSize = font.sp, color = if (palette == "Paper") Color(0xFF202124) else if (palette == "Solarized") Color(0xFF93A1A1) else Color(0xFFE6E1E5))
                        }
                        Text("Preview text only. Your running terminal keeps its processes and history.", style = MaterialTheme.typography.bodySmall)
                    }
                    "Extra keyboard keys" -> {
                        Text("Your key row", style = MaterialTheme.typography.titleMedium)
                        Text("These keys sit above the software keyboard. Tap a key below to add or remove it. Keys appear in the order you select them.")
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { if (keys.isEmpty()) Text("No extra keys selected") else keys.forEach { Text(it, style = MaterialTheme.typography.labelLarge) } }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Preferences.supportedKeys.forEach { name -> FilterChip(name in keys, { keys = if (name in keys) keys - name else keys + name; result = "" }, label = { Text(name) }) } }
                        Text("Ctrl and Alt modify the next key, then release automatically.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { keys = listOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right"); result = "" }) { Text("Reset to default keys") }
                    }
                    "Saved commands" -> {
                        Text("One command per line. Open Tools from the toolbar, choose a command, then confirm Run.")
                        OutlinedTextField(snippets, { if (it.length <= 16384) { snippets = it; result = "" } }, label = { Text("Commands") }, placeholder = { Text("git status\npython3 --version") }, minLines = 5, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                        Text("Commands are stored on this device. Avoid saving passwords or tokens.", style = MaterialTheme.typography.bodySmall)
                    }
                    "Keyboard & clipboard" -> {
                        Text("Hardware keyboard", style = MaterialTheme.typography.titleMedium)
                        ConnectedGroup {
                            listOf("Open Tools" to "Ctrl + Shift + P", "New session" to "Ctrl + Shift + N", "Previous session" to "Ctrl + Shift + Tab", "Paste" to "Ctrl + Shift + V").forEach { (label, shortcut) -> ListItem(headlineContent = { Text(label) }, supportingContent = { Text(shortcut, fontFamily = FontFamily.Monospace) }) }
                        }
                        Text("These shortcuts work while the terminal has focus. Tap the terminal to resume typing.")
                        Text("Copy & paste", style = MaterialTheme.typography.titleMedium)
                        Text("Long press terminal text to select and copy. Tools also has Select visible text and Copy selection.\n\nPasting multiple lines always asks for confirmation because a newline can run a command.")
                    }
                    "About this app" -> {
                        Text("FAndroidTerminal", style = MaterialTheme.typography.titleLarge)
                        Text("${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_COMMIT.take(12)}\nGPL-3.0-only")
                        Text("Terminal capabilities", style = MaterialTheme.typography.titleMedium)
                        Text("Scrollback keeps the engine’s built-in history limit. A configurable limit is not exposed by this engine.\n\nTerminal programs control the cursor’s shape and blinking. A separate app override is not available.\n\nOpen source licenses are available from Tools.")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (editable) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (saving) "Saving…" else if (dirty) "Unsaved changes" else result.ifEmpty { "Up to date" }, style = MaterialTheme.typography.labelMedium,
                    color = if (result.isNotEmpty() && result != "Changes saved" && !dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled = dirty && !saving, onClick = {
                    val selectedSection = section
                    val newFont = if (selectedSection == "Appearance") font else preferences.font
                    val newPalette = if (selectedSection == "Appearance") palette else preferences.palette
                    val newKeys = if (selectedSection == "Extra keyboard keys") keys.joinToString(" ") else preferences.keys
                    val newSnippets = if (selectedSection == "Saved commands") snippets else preferences.snippets
                    saving = true
                    scope.launch { try { preferences.save(newFont, newPalette, newKeys, newSnippets); result = "Changes saved" } catch (e: Exception) { result = e.message ?: "Could not save settings" } finally { saving = false } }
                }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save changes") }
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
    var message by remember { mutableStateOf("") }
    Page(environment.name, "Linux environment settings") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(if (count > 0) stringResource(R.string.guest_settings_blocked) else "Changes apply the next time you open a session. Android shell settings stay separate.", Modifier.padding(16.dp)) }
            if (count > 0) OutlinedButton(onClick = manageSessions, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_owned_sessions)) }
            Text("Network", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it }, label = { Text("DNS server") }, supportingText = { Text("Resolves website names inside Linux. Use an IPv4 address, for example 1.1.1.1.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("When a terminal opens", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(directory, { directory = it }, label = { Text("Starting folder") }, supportingText = { Text("A folder inside this environment, for example /root or /root/projects.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(shell, { shell = it }, label = { Text("Shell program") }, supportingText = { Text("The command interpreter to start. Usually /bin/bash; use an installed executable.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Compatibility", style = MaterialTheme.typography.titleMedium)
            ListItem(headlineContent = { Text("Compatibility mode") }, supportingContent = { Text("Recommended. Helps Linux start on phones that restrict tracing. Disable only to troubleshoot or compare performance.") }, leadingContent = { Icon(painterResource(R.drawable.ic_shield), null) }, trailingContent = { Switch(compatibility, onCheckedChange = null) }, modifier = Modifier.toggleable(value = compatibility, role = Role.Switch, onValueChange = { compatibility = it }))
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(variables, { variables = it }, label = { Text("Environment variables") }, supportingText = { Text("Optional. One NAME=value per line, such as EDITOR=nano. Loader and core shell variables are reserved.") }, minLines = 3, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
            Button(enabled = count == 0 && progress.isEmpty(), onClick = { message = runCatching { service.environments.saveSettings(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables), service.sessions); "Saving… See operation status." }.getOrElse { it.message ?: "Check the values above" } }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save environment settings") }
            if (message.isNotEmpty()) Text(message)
            Spacer(Modifier.height(16.dp))
        }
    }
}
