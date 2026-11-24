// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.selection.SelectionContainer
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
import io.github.aqsama.fandroidterminal.sessions.GuestSettingsFailure
import io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import kotlinx.coroutines.launch

@Composable
internal fun GuestSettingsPage(service: TerminalService, id: String, packages: () -> Unit = {}, files: () -> Unit = {}, manageSessions: () -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = environments.firstOrNull { it.id == id } ?: return
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val count = sessions.count { it.environmentId == id }
    var shells by remember(id) { mutableStateOf(emptyList<String>()) }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var discoveryError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = packages) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.packages_title)) }
            OutlinedButton(onClick = files) { Icon(painterResource(R.drawable.ic_files), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.files_label)) }
        }
        discoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.weight(1f)) {
            GuestSettingsContent(environment, count, progress, manageSessions, shells, {
                var found = emptyList<String>()
                try {
                    service.environments.fileOperation(id, service.sessions, "Discovering shells…", { failure ->
                        discoveryError = failure?.message
                        if (failure == null) shells = found
                    }) { guest ->
                        found = (runCatching { guest.read("/etc/shells").lines() }.getOrDefault(emptyList()) + listOf("/bin/sh", "/bin/bash", "/bin/zsh", "/usr/bin/fish"))
                            .map { it.trim() }.filter { it.startsWith('/') }.distinct().take(100)
                            .filter { runCatching { java.nio.file.Files.isExecutable(guest.resolve(it)) }.getOrDefault(false) }
                    }
                } catch (e: Exception) { discoveryError = e.message ?: resources.getString(R.string.check_values) }
            }) { value, completed -> service.environments.saveSettings(value, service.sessions, completed) }
        }
    }
}

@Composable
internal fun GuestSettingsContent(environment: LinuxEnvironment, count: Int,
    progress: Map<String, String>, manageSessions: () -> Unit, shells: List<String> = emptyList(), discover: (() -> Unit)? = null, save: (LinuxEnvironment, (Exception?) -> Unit) -> Unit) {
    val id = environment.id
    var dns by rememberSaveable(id) { mutableStateOf(environment.dns) }
    var compatibility by rememberSaveable(id) { mutableStateOf(environment.compatibility) }
    var inheritShell by rememberSaveable(id) { mutableStateOf(environment.inheritShell) }
    var inheritDirectory by rememberSaveable(id) { mutableStateOf(environment.inheritDirectory) }
    var directory by rememberSaveable(id) { mutableStateOf(environment.startDirectory) }
    var shell by rememberSaveable(id) { mutableStateOf(environment.shell) }
    var variables by rememberSaveable(id) { mutableStateOf(environment.variables) }
    var message by remember { mutableStateOf<String?>(null) }
    var dnsError by remember { mutableStateOf<String?>(null) }
    var directoryError by remember { mutableStateOf<String?>(null) }
    var shellError by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable(id) { mutableStateOf(environment.variables.isNotBlank()) }
    val dirty = inheritShell != environment.inheritShell || inheritDirectory != environment.inheritDirectory || dns != environment.dns || compatibility != environment.compatibility || directory != environment.startDirectory || shell != environment.shell || variables != environment.variables
    val saving = progress[id] == "Saving settings…"
    val resources = androidx.compose.ui.platform.LocalResources.current
    Page(environment.name, stringResource(R.string.guest_settings_title), showTitle = false) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContextBadge(environment.distribution)
                        Column { Text("${io.github.aqsama.fandroidterminal.runtime.linux.LinuxImages.get(environment.distribution).name} ${environment.release}", style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.guest_context_badge), style = MaterialTheme.typography.labelMedium, color = contextColor(environment.distribution)) }
                    }
                    Text("${environment.shell}  •  ${environment.startDirectory}", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
                }
            }
            if (count > 0) ConnectedGroup {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.review_sessions)) },
                    supportingContent = { Text(stringResource(R.string.env_access_lock, count)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_shell), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) },
                    modifier = Modifier.clickable(onClick = manageSessions))
            }
            Text(stringResource(R.string.guest_network), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it; message = null; dnsError = null }, label = { Text(stringResource(R.string.guest_dns)) }, isError = dnsError != null, supportingText = dnsError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_start), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(directory, { directory = it; message = null; directoryError = null }, label = { Text(stringResource(R.string.guest_folder)) }, isError = directoryError != null, supportingText = directoryError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Row(Modifier.fillMaxWidth().toggleable(inheritShell, role = Role.Checkbox) { inheritShell = it; message = null }) {
                Checkbox(inheritShell, null); Text(stringResource(R.string.inherit_guest_shell))
            }
            Row(Modifier.fillMaxWidth().toggleable(inheritDirectory, role = Role.Checkbox) { inheritDirectory = it; message = null }) {
                Checkbox(inheritDirectory, null); Text(stringResource(R.string.inherit_guest_directory))
            }
            discover?.let { action -> TextButton(enabled = count == 0 && progress.isEmpty(), onClick = action) { Text(stringResource(R.string.discover_shells)) } }
            shells.forEach { candidate ->
                TextButton(onClick = { shell = candidate; shellError = null; message = null }) { Text(candidate) }
            }
            OutlinedTextField(shell, { shell = it; message = null; shellError = null }, label = { Text(stringResource(R.string.guest_shell)) }, isError = shellError != null, supportingText = shellError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
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
            AnimatedVisibility(advanced) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Text(java.io.File(androidx.compose.ui.platform.LocalContext.current.filesDir, "environments/$id/rootfs").path, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
            OutlinedTextField(variables, { variables = it; message = null }, label = { Text(stringResource(R.string.guest_variables)) }, supportingText = { Text(stringResource(R.string.guest_variables_help), style = MaterialTheme.typography.bodyMedium) }, minLines = 3, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            } }
            Spacer(Modifier.height(16.dp))
        }
        SettingsSaveArea(dirty, saving, message?.takeIf { it != "Changes saved" }, message == "Changes saved", stringResource(R.string.guest_save),
            enabled = count == 0 && progress.isEmpty(), reviewSessions = if (count > 0) manageSessions else null) {
            message = null
            dnsError = runCatching { EnvironmentFiles.validateDns(dns) }.exceptionOrNull()?.message
            directoryError = if (!directory.startsWith('/') || directory.length > 4096 || directory.any(Char::isISOControl)) resources.getString(R.string.absolute_guest_path_required) else null
            shellError = if (!shell.startsWith('/') || shell.length > 4096 || shell.any(Char::isISOControl)) resources.getString(R.string.absolute_guest_path_required) else null
            val completed: (Exception?) -> Unit = { failure ->
                when ((failure as? GuestSettingsFailure)?.field) {
                    GuestSettingsFailure.Field.StartingFolder -> directoryError = failure?.message
                    GuestSettingsFailure.Field.ShellProgram -> shellError = failure?.message
                    null -> Unit
                }
                message = if (failure == null) "Changes saved" else if (failure is GuestSettingsFailure) resources.getString(R.string.check_values) else failure.message ?: resources.getString(R.string.check_values)
            }
            if (dnsError == null && directoryError == null && shellError == null) {
                runCatching { save(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables, inheritShell = inheritShell, inheritDirectory = inheritDirectory), completed) }.onFailure { completed(it as? Exception ?: IllegalStateException(it)) }
            } else message = resources.getString(R.string.check_values)
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

