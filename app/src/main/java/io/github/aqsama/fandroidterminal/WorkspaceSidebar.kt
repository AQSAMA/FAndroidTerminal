// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxImages
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
import androidx.compose.ui.semantics.selected
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
    launchers: List<io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher> = emptyList(), launchTool: (io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher) -> Unit = {},
) {
    val entries = sessions.map { session -> key(session.id) { session.listEntry() } }
    WorkspaceSidebarContent(page, busy, entries, environments, selectedId, archived, showArchived, navigate,
        { id -> sessions.firstOrNull { it.id == id }?.let(select) },
        { id, command -> sessions.firstOrNull { it.id == id }?.let { action(it, command) } }, newSession, launchers, launchTool)
}

@Composable
internal fun WorkspaceSidebarContent(
    page: String, busy: Set<String>, sessions: List<SessionListEntry>, environments: List<LinuxEnvironment>, selectedId: String?,
    archived: Boolean, showArchived: (Boolean) -> Unit, navigate: (String) -> Unit,
    select: (String) -> Unit, action: (String, String) -> Unit, newSession: (String?) -> Unit,
    launchers: List<io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher> = emptyList(), launchTool: (io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val groups = listOf(null to stringResource(R.string.android_shell)) + environments.map { it.id to it.name }
    val listed = sessions.filter { session -> session.archived == archived && (session.title.contains(query, true) || groups.firstOrNull { it.first == session.environmentId }?.second?.contains(query, true) == true) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.heightIn(min = 64.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_workspace), null, Modifier.padding(start = 8.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(if (archived) R.string.archived_sessions else R.string.sessions_label), style = MaterialTheme.typography.titleMedium)
                    if (listed.isNotEmpty()) Text(stringResource(R.string.session_count_short, listed.size),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(painterResource(R.drawable.ic_search), stringResource(R.string.find_session)) }
                IconButton(onClick = { navigate("Terminal") }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.return_to_terminal)) }
            }
            if (searching) {
                OutlinedTextField(query, { query = it }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.find_sessions_short), maxLines = 1) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (listed.isEmpty() && (query.isNotBlank() || archived)) item {
                    Text(stringResource(if (query.isNotBlank()) R.string.no_matching_sessions else R.string.no_archived_sessions),
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                groups.forEach { (id, name) ->
                    val group = listed.filter { it.environmentId == id }
                    val environment = environments.firstOrNull { it.id == id }
                    if (group.isNotEmpty() || !archived && query.isBlank() && (id == null || environment?.ready == true)) {
                        item(key = "group-$id") {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        ContextBadge(environment?.distribution, small = true)
                                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (environment != null) Text(
                                                "${LinuxImages.get(environment.distribution).name} ${environment.release}",
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        if (!archived) IconButton(onClick = { newSession(id) }, enabled = sessions.size < 12 && (id == null || id !in busy && environment?.ready == true)) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session_in, name), Modifier.size(20.dp)) }
                                    }
                                    group.forEach { session -> key(session.id) { SessionRow(session, name, selectedId == session.id, { select(session.id) }) { action(session.id, it) } } }
                                }
                            }
                        }
                    }
                }
                if (!archived && query.isBlank() && launchers.isNotEmpty()) item {
                    Text(stringResource(R.string.launchers_title), Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (!archived && query.isBlank()) items(launchers, key = { "launcher-${it.id}" }) { launcher ->
                    val available = environments.any { it.id == launcher.environmentId && it.ready && it.id !in busy }
                    TextButton(enabled = available, onClick = { launchTool(launcher) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.ic_prompt), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(launcher.name)
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val destinations = listOf("Environments", "Packages", "Launchers", "Settings")
                destinations.forEach { destination ->
                    val label = stringResource(destinationLabel(destination))
                    val active = page == destination || page in listOf("Guest settings", "Files") && destination == "Environments"
                    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    TextButton(
                        onClick = { navigate(destination) },
                        modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 60.dp)
                            .semantics { selected = active; contentDescription = label },
                        colors = ButtonDefaults.textButtonColors(contentColor = tint),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(painterResource(destinationIcon(destination)), null, Modifier.size(20.dp))
                            if (!compact) Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            val archivedCount = sessions.count { it.archived }
            if (archived || archivedCount > 0) TextButton(
                onClick = { showArchived(!archived) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(if (archived) R.drawable.ic_back else R.drawable.ic_archive), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (archived) R.string.active_sessions else R.string.archived_count, archivedCount))
            }
        }
    }
}
internal fun destinationIcon(page: String) = when (page) { "Backup" -> R.drawable.ic_storage; "Launchers" -> R.drawable.ic_prompt; "Packages" -> R.drawable.ic_download; "Terminal" -> R.drawable.ic_shell; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
internal fun destinationLabel(page: String) = when (page) { "Backup" -> R.string.backup_title; "Guest defaults" -> R.string.guest_defaults_title; "Launchers" -> R.string.launchers_title; "Packages" -> R.string.packages_title; "Terminal" -> R.string.terminal_label; "Environments" -> R.string.environments_label; "Files" -> R.string.files_label; else -> R.string.settings_label }
