// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Command input owns a window that resizes around the IME, with context outside scrolling content. */
@Composable
internal fun CommandSurface(title: String, identity: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.TopCenter) {
            val showHeading = maxHeight >= 320.dp
            Surface(Modifier.widthIn(max = 640.dp).fillMaxSize().semantics { paneTitle = title }, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.heightIn(min = if (showHeading) 56.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (showHeading) Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(identity, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_command_surface)) }
                    }
                    content()
                }
            }
        }
    }
}

private val shellExamples = listOf(
    R.string.workspace_command_pwd to "pwd",
    R.string.workspace_command_files to "ls -la",
    R.string.workspace_command_identity to "id",
    R.string.workspace_command_disk to "df -h .",
)

/** Commands remain reviewable before the selected process receives input. */
@Composable
internal fun CommandBrowser(identity: String, commands: List<String>, enabled: Boolean, dismiss: () -> Unit,
    write: () -> Unit, manage: () -> Unit, run: (String, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchLabel = stringResource(R.string.workspace_find_command)
    val saved = commands.filter { it.contains(query, ignoreCase = true) }
    val examples = shellExamples.map { (label, command) -> stringResource(label) to command }
        .filter { (label, command) -> command !in commands && (label.contains(query, true) || command.contains(query, true)) }
    CommandSurface(stringResource(R.string.commands_title), identity, dismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = write, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.write_command))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = manage) { Text(stringResource(R.string.manage_saved_commands)) }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(searchLabel) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search))
            } },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel })
        if (!enabled) Text(stringResource(R.string.snippets_running_required), Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (saved.isNotEmpty()) {
                item { CommandSection(R.string.saved_commands_label) }
                items(saved) { command -> CommandResult(command, null, enabled) { run(command, true) } }
            }
            if (examples.isNotEmpty()) {
                item { CommandSection(R.string.workspace_shell_examples) }
                items(examples, key = { it.second }) { (label, command) ->
                    CommandResult(command, label, enabled) { run(command, false) }
                }
            }
            if (saved.isEmpty() && examples.isEmpty()) item {
                Text(stringResource(R.string.no_matching_actions), Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CommandSection(label: Int) {
    Text(stringResource(label), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CommandResult(command: String, label: String?, enabled: Boolean, review: () -> Unit) {
    val commandText: @Composable () -> Unit = {
        Text(command, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(onClick = review, enabled = enabled, shape = MaterialTheme.shapes.small) {
        ListItem(
            headlineContent = if (label == null) commandText else ({ Text(label) }),
            supportingContent = if (label == null) null else commandText,
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp)) },
        )
    }
}
