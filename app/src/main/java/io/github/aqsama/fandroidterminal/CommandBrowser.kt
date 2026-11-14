// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/** Saved shell commands only; navigation, session management and clipboard actions have their own homes. */
@Composable
internal fun CommandBrowser(identity: String, commands: List<String>, enabled: Boolean, dismiss: () -> Unit,
    write: () -> Unit, manage: () -> Unit, run: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.find_saved_command)
    val results = commands.filter { it.contains(query, ignoreCase = true) }
    CommandSurface(stringResource(R.string.commands_title), identity, dismiss) {
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.find_saved_command)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_commands), null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = searchLabel })
        LaunchedEffect(Unit) { focus.requestFocus() }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = write, enabled = enabled) { Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.write_command)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = manage) { Text(stringResource(R.string.manage_saved_commands)) }
                }
                if (!enabled) Text(stringResource(R.string.snippets_running_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (results.isEmpty()) item {
                Text(stringResource(if (query.isBlank() && commands.isEmpty()) R.string.saved_commands_empty else R.string.no_matching_actions), Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(results) { command ->
                ListItem(modifier = Modifier.clickable(enabled = enabled) { run(command) },
                    headlineContent = { Text(command, maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_terminal), null) })
                HorizontalDivider()
            }
        }
    }
}
