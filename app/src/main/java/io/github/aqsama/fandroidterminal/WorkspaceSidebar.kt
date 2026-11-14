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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

/** One primary session navigator. Search and archives are secondary; destinations do not interrupt the list. */
@Composable
internal fun WorkspaceSidebar(
    page: String, busy: Set<String>, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, selectedId: String?,
    archived: Boolean, showArchived: (Boolean) -> Unit, navigate: (String) -> Unit,
    select: (TerminalSession) -> Unit, action: (TerminalSession, String) -> Unit, newSession: (String?) -> Unit,
) {
    val entries = sessions.map { session -> key(session.id) { session.listEntry() } }
    WorkspaceSidebarContent(page, busy, entries, environments, selectedId, archived, showArchived, navigate,
        { id -> sessions.firstOrNull { it.id == id }?.let(select) },
        { id, command -> sessions.firstOrNull { it.id == id }?.let { action(it, command) } }, newSession)
}

@Composable
internal fun WorkspaceSidebarContent(
    page: String, busy: Set<String>, sessions: List<SessionListEntry>, environments: List<LinuxEnvironment>, selectedId: String?,
    archived: Boolean, showArchived: (Boolean) -> Unit, navigate: (String) -> Unit,
    select: (String) -> Unit, action: (String, String) -> Unit, newSession: (String?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val groups = listOf(null to stringResource(R.string.android_shell)) + environments.map { it.id to it.name }
    val listed = sessions.filter { session -> session.archived == archived && (session.title.contains(query, true) || groups.firstOrNull { it.first == session.environmentId }?.second?.contains(query, true) == true) }
    Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp)) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (archived) R.string.archived_sessions else R.string.sessions_label), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(painterResource(R.drawable.ic_search), stringResource(R.string.find_session)) }
            IconButton(onClick = { navigate("Terminal") }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.return_to_terminal)) }
        }
        if (searching) {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.find_sessions_short), maxLines = 1) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        if (archived) {
            TextButton(onClick = { showArchived(false) }) { Icon(painterResource(R.drawable.ic_back), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.active_sessions)) }
            Text(stringResource(R.string.archive_explanation), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (listed.isEmpty()) item { Text(stringResource(if (query.isNotBlank()) R.string.no_matching_sessions else if (archived) R.string.no_archived_sessions else R.string.no_visible_sessions), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            groups.forEach { (id, name) ->
                val group = listed.filter { it.environmentId == id }
                if (group.isNotEmpty() || !archived && query.isBlank() && (id == null || environments.any { it.id == id && it.ready })) {
                    item(key = "group-$id") {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (id == null) Text(stringResource(R.string.device_context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!archived) IconButton(onClick = { newSession(id) }, enabled = sessions.size < 12 && (id == null || id !in busy && environments.any { it.id == id && it.ready })) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session_in, name), Modifier.size(20.dp)) }
                        }
                    }
                    items(group, key = { it.id }) { session -> SessionRow(session, name, selectedId == session.id, { select(session.id) }) { action(session.id, it) } }
                }
            }
        }
        HorizontalDivider()
        if (!archived) NavigationDrawerItem(
            label = { Text(stringResource(R.string.archived_count, sessions.count { it.archived }), style = MaterialTheme.typography.bodyMedium) },
            icon = { Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(20.dp)) },
            selected = false, onClick = { showArchived(true) }, modifier = Modifier.heightIn(min = 48.dp))
        listOf("Environments", "Settings").forEach { destination ->
            val label = stringResource(destinationLabel(destination))
            NavigationDrawerItem(label = { Text(label, style = MaterialTheme.typography.bodyMedium) }, icon = { Icon(painterResource(destinationIcon(destination)), null, Modifier.size(20.dp)) },
                selected = page == destination || page in listOf("Guest settings", "Files") && destination == "Environments", onClick = { navigate(destination) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label })
        }
        Spacer(Modifier.height(8.dp))
    }
}
internal fun destinationIcon(page: String) = when (page) { "Terminal" -> R.drawable.ic_terminal; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
internal fun destinationLabel(page: String) = when (page) { "Terminal" -> R.string.terminal_label; "Environments" -> R.string.environments_label; "Files" -> R.string.files_label; else -> R.string.settings_label }
