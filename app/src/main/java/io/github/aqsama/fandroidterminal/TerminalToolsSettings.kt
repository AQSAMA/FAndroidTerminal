// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment

@Composable
internal fun TerminalToolsSettings(startup: String, setStartup: (String) -> Unit, awake: Boolean, setAwake: (Boolean) -> Unit,
    snippets: String, setSnippets: (String) -> Unit, environments: List<LinuxEnvironment>, packages: () -> Unit, defaults: () -> Unit, files: (String, String) -> Unit, startingDirectory: (String, (Result<String>) -> Unit) -> Unit) {
    val context = LocalContext.current
    val choices = listOf("Android" to stringResource(R.string.android_shell), "Sessions" to stringResource(R.string.sessions_label), "Environments" to stringResource(R.string.environments_label)) + environments.filter { it.ready }.map { it.id to it.name }
    Text(stringResource(R.string.startup_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    ConnectedGroup {
        var choosing by remember { mutableStateOf(false) }
        Box {
            ListItem(modifier = Modifier.clickable { choosing = true }, headlineContent = { Text(stringResource(R.string.startup_open)) }, supportingContent = { Text(choices.firstOrNull { it.first == startup }?.second ?: stringResource(R.string.choose_environment)) }, trailingContent = { Icon(painterResource(R.drawable.ic_chevron_down), null) })
            DropdownMenu(choosing, { choosing = false }) { choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { setStartup(id); choosing = false }) } }
        }
        ListItem(headlineContent = { Text(stringResource(R.string.keep_terminal_awake)) }, trailingContent = { Switch(awake, setAwake) })
    }
    Text(stringResource(R.string.home_directories), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    var directoryContext by rememberSaveable { mutableStateOf<String?>(null) }
    val environment = environments.firstOrNull { it.id == directoryContext && it.ready }
    var effectiveDirectory by remember(environment) { mutableStateOf<String?>(null) }
    var directoryError by remember(environment) { mutableStateOf<String?>(null) }
    val latestEnvironment by rememberUpdatedState(environment)
    var generation by remember { mutableIntStateOf(0) }
    var resolveAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(environment, resolveAttempt) {
        effectiveDirectory = null; directoryError = null
        val request = ++generation
        environment?.let { env ->
            runCatching { startingDirectory(env.id) { result ->
                if (generation == request && latestEnvironment == env) {
                    effectiveDirectory = result.getOrNull(); directoryError = result.exceptionOrNull()?.message
                }
            } }.onFailure { directoryError = it.message }
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var choosing by remember { mutableStateOf(false) }
            Box {
                TextButton({ choosing = true }) { Text(environment?.name ?: stringResource(R.string.android_shell)); Icon(painterResource(R.drawable.ic_chevron_down), null) }
                DropdownMenu(choosing, { choosing = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.android_shell)) }, onClick = { directoryContext = null; choosing = false })
                    environments.filter { it.ready }.forEach { env -> DropdownMenuItem(text = { Text(env.name) }, onClick = { directoryContext = env.id; choosing = false }) }
                }
            }
            @Composable fun PathRow(label: String, value: String) {
                ListItem(headlineContent = { Text(label, style = MaterialTheme.typography.labelMedium) }, supportingContent = { SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) } }, trailingContent = {
                    IconButton({ context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value)) }) { Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.copy_label)) }
                })
            }
            if (environment == null) {
                PathRow(stringResource(R.string.home_and_start), context.filesDir.absolutePath)
                Text(stringResource(R.string.android_home_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PathRow("HOME", "/root")
                effectiveDirectory?.let { PathRow(stringResource(R.string.guest_start_directory), it); TextButton({ resolveAttempt++ }) { Text(stringResource(R.string.refresh_label)) } }
                if (effectiveDirectory == null) {
                    if (directoryError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else { Text(directoryError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error); TextButton({ resolveAttempt++ }) { Text(stringResource(R.string.retry_label)) } }
                }
                var backing by rememberSaveable { mutableStateOf(false) }
                TextButton({ backing = !backing }) { Text(stringResource(R.string.private_storage_path)) }
                if (backing) PathRow(stringResource(R.string.guest_root_storage), java.io.File(context.filesDir, "environments/${environment.id}/rootfs").absolutePath)
                OutlinedButton(enabled = effectiveDirectory != null, onClick = { effectiveDirectory?.let { files(environment.id, it) } }) { Icon(painterResource(R.drawable.ic_files), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_folder)) }
                Text(stringResource(R.string.guest_home_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(stringResource(R.string.settings_saved_commands), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    var editIndex by rememberSaveable { mutableIntStateOf(-2) }
    var draft by rememberSaveable { mutableStateOf("") }
    var editBase by rememberSaveable { mutableStateOf("") }
    var limitError by remember { mutableStateOf(false) }
    val commands = snippets.lines().filter { it.isNotBlank() }
    ConnectedGroup {
        commands.forEachIndexed { index, command ->
            ListItem(modifier = Modifier.clickable { editIndex = index; draft = command; editBase = snippets; limitError = false }, headlineContent = { Text(command, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) }, trailingContent = {
                IconButton({ setSnippets(commands.filterIndexed { i, _ -> i != index }.joinToString("\n")) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_menu)) }
            })
        }
        ListItem(modifier = Modifier.clickable { editIndex = -1; draft = ""; editBase = snippets; limitError = false }, headlineContent = { Text(stringResource(R.string.add_saved_command)) }, leadingContent = { Icon(painterResource(R.drawable.ic_add), null) })
    }
    Text(stringResource(R.string.saved_commands_review_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (editIndex != -2) AlertDialog(onDismissRequest = { editIndex = -2 }, title = { Text(stringResource(R.string.settings_saved_commands)) }, text = {
        Column {
        if (editBase != snippets) {
            Text(stringResource(R.string.command_list_changed), color = MaterialTheme.colorScheme.error)
            TextButton({ editIndex = -1; editBase = snippets }) { Text(stringResource(R.string.command_keep_as_new)) }
        }
        OutlinedTextField(draft, { if (it.length <= 16384 && '\n' !in it && '\r' !in it) { draft = it; limitError = false } else limitError = true }, label = { Text(stringResource(R.string.command_label)) }, isError = limitError, supportingText = if (limitError) ({ Text(stringResource(R.string.saved_commands_size_limit)) }) else null,
            modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr), minLines = 2)
        }
    }, confirmButton = { TextButton(enabled = draft.isNotBlank() && editBase == snippets, onClick = {
        val updated = commands.toMutableList().apply { if (editIndex < 0) add(draft) else if (editIndex in indices) this[editIndex] = draft }.joinToString("\n")
        if (updated.length > 16384) limitError = true else { setSnippets(updated); editIndex = -2 }
    }) { Text(stringResource(R.string.save_label)) } }, dismissButton = { TextButton({ editIndex = -2 }) { Text(stringResource(R.string.cancel_label)) } })
    Text(stringResource(R.string.linux_tools), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    ConnectedGroup {
        ListItem(modifier = Modifier.clickable(onClick = packages), headlineContent = { Text(stringResource(R.string.packages_title)) }, supportingContent = { Text(stringResource(R.string.manage_installed_packages)) }, leadingContent = { Icon(painterResource(R.drawable.ic_download), null) }, trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
        ListItem(modifier = Modifier.clickable(onClick = defaults), headlineContent = { Text(stringResource(R.string.new_environment_defaults)) }, supportingContent = { Text(stringResource(R.string.defaults_scope_help)) }, leadingContent = { Icon(painterResource(R.drawable.ic_environments), null) }, trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
    }
    var keyboardHelp by rememberSaveable { mutableStateOf(false) }
    ListItem(modifier = Modifier.clickable { keyboardHelp = !keyboardHelp }, headlineContent = { Text(stringResource(R.string.settings_keyboard_clipboard)) }, trailingContent = { Icon(painterResource(if (keyboardHelp) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null) })
    if (keyboardHelp) {
        ConnectedGroup {
            listOf(R.string.commands_title to "Ctrl + Shift + P", R.string.new_session to "Ctrl + Shift + N", R.string.switch_session to "Ctrl + Shift + Tab", R.string.paste_label to "Ctrl + Shift + V").forEach { (label, shortcut) -> ListItem(headlineContent = { Text(stringResource(label)) }, supportingContent = { Text(shortcut, fontFamily = FontFamily.Monospace) }) }
        }
        Text(stringResource(R.string.settings_copy_help), style = MaterialTheme.typography.bodySmall)
    }
}
