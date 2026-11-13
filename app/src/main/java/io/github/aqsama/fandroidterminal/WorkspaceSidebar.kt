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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

/** Primary cross-environment session navigator. Quick tabs stay scoped to one environment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceSidebar(
    page: String, busy: Set<String>, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, selectedId: String?,
    archived: Boolean, showArchived: (Boolean) -> Unit, navigate: (String) -> Unit,
    select: (TerminalSession) -> Unit, action: (TerminalSession, String) -> Unit, newSession: (String?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = listOf(null to stringResource(R.string.android_shell)) + environments.map { it.id to it.name }
    val visibleCount = sessions.count { !it.archived }
    val listed = sessions.filter { session -> session.archived == archived && (session.title.contains(query, true) || groups.firstOrNull { it.first == session.environmentId }?.second?.contains(query, true) == true) }
    LazyColumn(Modifier.widthIn(max = 304.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigate("Terminal") }) { Icon(painterResource(R.drawable.ic_terminal), stringResource(R.string.return_to_terminal), tint = MaterialTheme.colorScheme.primary) }
            Text(stringResource(R.string.workspaces_label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { navigate("Settings") }) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_label)) }
        }
        // Destinations live above the working contexts, not between sessions.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Files", "Environments").forEach { destination ->
                val label = stringResource(destinationLabel(destination))
                val current = page == destination || page == "Guest settings" && destination == "Environments"
                FilterChip(current, { navigate(destination) }, modifier = Modifier.weight(1f), label = { Text(label, maxLines = 2, style = MaterialTheme.typography.labelMedium) }, leadingIcon = { Icon(painterResource(destinationIcon(destination)), null, Modifier.size(18.dp)) })
            }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.find_session)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } }, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!archived, { showArchived(false) }, label = { Text(stringResource(R.string.visible_count, visibleCount)) })
            FilterChip(archived, { showArchived(true) }, label = { Text(stringResource(R.string.archived_count, sessions.size - visibleCount)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(16.dp)) })
        }
        if (archived) Text(stringResource(R.string.archive_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), style = MaterialTheme.typography.bodySmall)
        } }
            if (listed.isEmpty()) item { Text(stringResource(if (query.isNotBlank()) R.string.no_matching_sessions else if (archived) R.string.no_archived_sessions else if (sessions.any { it.archived }) R.string.no_visible_sessions else R.string.open_terminal_hint), Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            groups.forEach { (id, name) ->
                val group = listed.filter { it.environmentId == id }
                if (group.isNotEmpty() || !archived && query.isBlank()) {
                    item(key = "group-$id") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(if (id == null) R.drawable.ic_terminal else R.drawable.ic_environments), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(name, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(group.size.toString(), style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { newSession(id) }, enabled = sessions.size < 12 && !archived && (id == null || id !in busy && environments.any { it.id == id && it.ready })) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session_in, name)) }
                        }
                    }
                    items(group, key = { it.id }) { session -> SessionRow(session, name, selectedId == session.id, { select(session) }) { action(session, it) } }
                }
            }
        item { Text(stringResource(R.string.swipe_session_hint), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
internal fun destinationIcon(page: String) = when (page) { "Terminal" -> R.drawable.ic_terminal; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
internal fun destinationLabel(page: String) = when (page) { "Terminal" -> R.string.terminal_label; "Environments" -> R.string.environments_label; "Files" -> R.string.files_label; else -> R.string.settings_label }
